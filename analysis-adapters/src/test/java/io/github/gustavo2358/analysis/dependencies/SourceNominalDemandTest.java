package io.github.gustavo2358.analysis.dependencies;

import io.github.gustavo2358.analysis.adapters.*;
import io.github.gustavo2358.analysis.dependencies.source.*;
import io.github.gustavo2358.analysis.solver.*;
import java.io.ByteArrayInputStream;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static io.github.gustavo2358.analysis.dependencies.source.QualifiedSourceDependencies.*;
import static org.junit.jupiter.api.Assertions.*;

final class SourceNominalDemandTest {
    @TempDir Path directory;
    private static final QualifiedSourceJson JSON=new QualifiedSourceJson();
    private static AnalysisResources resources(){return new AnalysisResources(new AnalysisResources.Limits(1048576,16384,0,256L*1024*1024,2,Long.MAX_VALUE,0));}
    private static void zero(AnalysisResources resources){for(var pool:AnalysisResources.Pool.values())assertEquals(0,resources.used(pool),pool.toString());}
    private static SourceAdmissionSnapshot admit(byte[] wire,PageStore pages,AnalysisResources resources)throws Exception {
        return SourceAdmissionSnapshot.admit(JSON.decodeSnapshot(new ByteArrayInputStream(wire),pages,resources),pages,resources);
    }
    private static Set<String> closure(SourceNominalDemand demand,NominalValues facts,Set<String> requested) {
        demand.begin();
        for(int i=0;i<facts.symbols().size();i++)if(requested.contains(facts.symbols().get(i).node()))demand.request(i);
        demand.finish();var result=new HashSet<String>();
        for(int i=0;i<facts.symbols().size();i++)if(demand.contains(i))result.add(facts.symbols().get(i).node());
        return result;
    }
    /** Independent set fixpoint, deliberately not the graph implementation. */
    private static Set<String> oracle(NominalValues facts,Set<String> requested) {
        var result=new HashSet<>(requested);boolean changed;
        do {
            changed=false;
            for(var assignment:facts.assignments())if(result.contains(assignment.target()))changed|=result.addAll(reads(assignment.source()));
            for(var condition:facts.conditions()) {
                var read=new HashSet<String>();var pending=new ArrayDeque<NominalValues.Predicate>();pending.add(condition.predicate());
                while(!pending.isEmpty()){var predicate=pending.removeFirst();for(var term:predicate.terms())read.addAll(reads(term));pending.addAll(predicate.children());}
                if(!Collections.disjoint(read,result))changed|=result.addAll(read);
            }
        }while(changed);return result;
    }
    private static Set<String> reads(NominalValues.Term term) {
        var result=new HashSet<String>();var seen=Collections.newSetFromMap(new IdentityHashMap<NominalValues.Term,Boolean>());var pending=new ArrayDeque<NominalValues.Term>();pending.add(term);
        while(!pending.isEmpty()){var next=pending.removeFirst();if(!seen.add(next))continue;if(next.kind().equals("READ"))result.add(next.value());pending.addAll(next.arguments());}return result;
    }
    private static NominalValues.Term read(int i){return new NominalValues.Term("READ","v"+i);}
    private static NominalValues facts(int n,List<NominalValues.Assignment> writes,List<NominalValues.Condition> conditions) {
        var symbols=new ArrayList<NominalValues.Symbol>();for(int i=0;i<n;i++)symbols.add(new NominalValues.Symbol("v"+i,8));
        return new NominalValues("NOMINAL_TEXT_SOURCE_V4",symbols,writes,conditions,List.of());
    }
    static byte[] wire(NominalValues facts,int units)throws Exception {
        var location=new Location("synthetic-demand.cbl",1,0,1,1);var provenance=new Provenance(location,location,List.of(),true);var evidence=new ArrayList<UnitEvidence>();
        for(int u=0;u<units;u++) {
            var id=new UnitId("synthetic-demand",List.of(u),u==0?"Aa":"BB");var statements=new ArrayList<Statement>();var owners=new LinkedHashSet<String>();
            facts.assignments().forEach(a->owners.add(a.statement()));facts.conditions().forEach(c->owners.add(c.statement()));
            for(var owner:owners)statements.add(new Statement(new StatementId(id,owner),provenance));
            var declarations=facts.symbols().stream().map(s->new NominalValueEvidence.Declaration(s.node(),provenance)).toList();
            var nominal=new NominalValueEvidence(facts,declarations,List.of(),List.of(),List.of());
            evidence.add(new UnitEvidence(id,false,statements,List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),Optional.of(nominal),List.of()));
        }
        return JSON.encode(new QualifiedSourceDependencies("qualified-source-dependencies","1.6.0","synthetic-demand",new Document("cobol-semantic-product","2.67.0","0".repeat(64)),List.of(),evidence));
    }
    @Test void everyExistingNominalLiteralMatchesResidentAndIndependentClosure()throws Exception {
        int units=0;var nominalLiterals=new TreeSet<String>();
        try(var paths=Files.walk(Path.of("src/test/resources"))) {
            for(var path:paths.filter(p->p.toString().endsWith(".source.json")).sorted().toList()) {
                byte[] wire=Files.readAllBytes(path);var legacy=JSON.decode(wire);var resources=resources();
                try(var pages=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.INDEX);var admission=admit(wire,pages,resources)) {
                    for(int u=0;u<legacy.units().size();u++)if(legacy.units().get(u).nominalValues().isPresent()) {
                        var facts=legacy.units().get(u).nominalValues().orElseThrow().facts();long baseline=pages.statistics().livePages();
                        try(var demand=SourceNominalDemand.prepare(admission,u,pages,resources)) {
                            assertEquals(facts.symbols().size(),demand.symbolCount());var resident=new NominalDemandIndex(facts);
                            for(var symbol:facts.symbols()) {
                                var seeds=Set.of(symbol.node());var expected=oracle(facts,seeds);assertEquals(expected,resident.closure(seeds).symbols());assertEquals(expected,closure(demand,facts,seeds),path+"/"+symbol.node());
                            }
                            assertEquals(Set.of(),closure(demand,facts,Set.of()));units++;nominalLiterals.add(path.getFileName().toString());
                        }assertEquals(baseline,pages.statistics().livePages());
                    }
                }zero(resources);
            }
        }assertEquals(Set.of("unknown-branch-kill.source.json","unknown-copy-kill.source.json","unknown-branch-query.source.json","reentry-values.source.json"),nominalLiterals);
        assertEquals(4,units,"exact independently inspected nominal literal inventory");System.out.println("PAGED_SOURCE_NOMINAL_DEMAND_LITERAL_PARITY units="+units+" CLI=NOT_RUN values=NOT_RUN");
    }
    @Test void assignmentConditionSharedDagKeepsDirectionAndFullSymbolIdentity()throws Exception {
        var shared=new NominalValues.Term("CHOICE","",List.of(read(0),read(1)));var facts=facts(4,List.of(new NominalValues.Assignment("s","v2",shared)),List.of(new NominalValues.Condition("c",new NominalValues.Predicate("EQ",List.of(shared,read(3)),List.of()))));
        byte[] wire=wire(facts,2);var resources=resources();
        try(var pages=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.INDEX);var admission=admit(wire,pages,resources);
            var first=SourceNominalDemand.prepare(admission,0,pages,resources);var second=SourceNominalDemand.prepare(admission,1,pages,resources)) {
            assertEquals(Set.of("v0","v1","v3"),closure(first,facts,Set.of("v0")));assertEquals(Set.of("v0","v1","v2","v3"),closure(first,facts,Set.of("v2")));
            assertEquals(Set.of(),closure(second,facts,Set.of()));assertEquals(Set.of("v0","v1","v3"),closure(second,facts,Set.of("v3")));
            assertEquals(Set.of("v0","v1","v2","v3"),closure(first,facts,Set.of("v2")));assertEquals(Set.of(),closure(first,facts,Set.of()));
        }zero(resources);
    }
    @Test void permutationsCyclesAndAllOrAlternativesMatchIndependentSetFixpoint()throws Exception {
        var random=new Random(810734);
        for(int sample=0;sample<12;sample++) {
            int n=3+random.nextInt(20);var writes=new ArrayList<NominalValues.Assignment>();var conditions=new ArrayList<NominalValues.Condition>();
            for(int i=0;i<n;i++) {
                writes.add(new NominalValues.Assignment("s"+i,"v"+random.nextInt(n),new NominalValues.Term("CHOICE","",List.of(read(random.nextInt(n)),read(random.nextInt(n))))));
                conditions.add(new NominalValues.Condition("c"+i,new NominalValues.Predicate("EQ",List.of(read(random.nextInt(n)),read(random.nextInt(n))),List.of())));
            }
            var facts=facts(n,writes,conditions);var seeds=Set.of("v"+random.nextInt(n));var expected=oracle(facts,seeds);
            for(int permutation=0;permutation<2;permutation++) {
                var resources=resources();byte[] wire=wire(facts(n,writes,conditions),1);
                try(var pages=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.INDEX);var admission=admit(wire,pages,resources);var demand=SourceNominalDemand.prepare(admission,0,pages,resources)) {
                    assertEquals(expected,closure(demand,facts,seeds));assertTrue(demand.nodeVisits()<=demand.nodeCount());assertTrue(demand.edgeVisits()<=demand.edgeCount());
                }zero(resources);Collections.shuffle(writes,random);Collections.shuffle(conditions,random);
            }
        }
    }
    @Test void reverseChainAndWideConditionSpillWithFixedControlsAndLinearIncidence()throws Exception {
        long fixed=-1;
        for(int n:new int[]{1,4,16,64,256,1024}) {
            var writes=new ArrayList<NominalValues.Assignment>();for(int i=n-1;i>=0;i--)writes.add(new NominalValues.Assignment("s"+i,"v"+i,read(i+1)));
            var facts=facts(n+1,writes,List.of());byte[] wire=wire(facts,1);var resources=resources();
            try(var pages=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.INDEX);var admission=admit(wire,pages,resources)) {
                long before=resources.heapUsed(),baseline=pages.statistics().livePages();
                try(var demand=SourceNominalDemand.prepare(admission,0,pages,resources)) {
                    long delta=resources.heapUsed()-before;if(fixed<0)fixed=delta;assertEquals(fixed,delta);
                    assertEquals(oracle(facts,Set.of("v0")),closure(demand,facts,Set.of("v0")));assertEquals(2L*n+1,demand.nodeVisits());assertEquals(2L*n,demand.edgeVisits());assertEquals(2L*n,demand.edgeCount());
                    assertEquals(Set.of("v"+n),closure(demand,facts,Set.of("v"+n)));assertEquals(1,demand.nodeVisits());assertEquals(0,demand.edgeVisits());
                }assertEquals(baseline,pages.statistics().livePages());
            }zero(resources);
        }
        int n=1024;var terms=new ArrayList<NominalValues.Term>();for(int i=0;i<n;i++)terms.add(read(i));var choice=new NominalValues.Term("CHOICE","",terms);
        var facts=facts(n,List.of(),List.of(new NominalValues.Condition("c",new NominalValues.Predicate("EQ",List.of(choice,new NominalValues.Term("LITERAL","X")),List.of()))));var resources=resources();
        try(var pages=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.INDEX);var admission=admit(wire(facts,1),pages,resources);var demand=SourceNominalDemand.prepare(admission,0,pages,resources)) {
            assertEquals(n,closure(demand,facts,Set.of("v0")).size());assertEquals(4L*n+4,demand.edgeCount());assertEquals(demand.edgeCount(),demand.edgeVisits());
        }zero(resources);
    }
    @Test void foreignContextPreallocationDenialAndInvalidQueryNeverStealParent()throws Exception {
        var facts=facts(2,List.of(new NominalValues.Assignment("s","v0",read(1))),List.of());var resources=resources();var foreign=resources();
        try(var pages=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.INDEX);var other=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.INDEX);var admission=admit(wire(facts,1),pages,resources)) {
            long baseline=pages.statistics().livePages();
            assertThrows(IllegalArgumentException.class,()->SourceNominalDemand.prepare(admission,0,other,resources));assertThrows(IllegalArgumentException.class,()->SourceNominalDemand.prepare(admission,0,pages,foreign));
            try(var pressure=resources.reserve(AnalysisResources.Pool.RESIDENT,resources.limits().heapBytes()-resources.heapUsed()-1023,AnalysisResources.Phase.INDEX)) {
                assertNotNull(pressure);assertThrows(AnalysisResources.Exhausted.class,()->SourceNominalDemand.prepare(admission,0,pages,resources));assertEquals(baseline,pages.statistics().livePages());
            }
            try(var demand=SourceNominalDemand.prepare(admission,0,pages,resources)) {
                demand.begin();assertThrows(IndexOutOfBoundsException.class,()->demand.request(2));assertThrows(IllegalStateException.class,demand::begin);
            }assertEquals(baseline,pages.statistics().livePages());assertEquals(1,admission.unitCount());
        }zero(resources);zero(foreign);
    }
    @Test void parentStorageExpiryRetiresDemandBeforeAnySubsequentQuery()throws Exception {
        var resources=resources();var facts=facts(2,List.of(new NominalValues.Assignment("s","v0",read(1))),List.of());
        try(var pages=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.INDEX)) {
            var admission=admit(wire(facts,1),pages,resources);var demand=SourceNominalDemand.prepare(admission,0,pages,resources);assertEquals(2,closure(demand,facts,Set.of("v0")).size());
            admission.snapshot().close();assertEquals(0,pages.statistics().livePages());assertThrows(IllegalStateException.class,demand::symbolCount);demand.close();admission.close();long page=pages.allocate();pages.release(page);
        }zero(resources);
    }
    @Test void nativeCommonOwnerExpiryRetiresDemandWithoutAQuery()throws Exception {
        var resources=new AnalysisResources(new AnalysisResources.Limits(64L*1024*1024,16L*1024*1024,0,256L*1024*1024,8,Long.MAX_VALUE,0));
        var facts=facts(2,List.of(new NominalValues.Assignment("s","v0",read(1))),List.of());
        try(var input=new DataflowAirReader().readSnapshot(Path.of("src/test/resources/cp6/dynamic-x8.air.json"),resources);var pages=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.INDEX)) {
            var program=new SnapshotProgram(input.checked(),input.newIdentityStorage(),input.newOrderStorage(),resources);
            try {
                var execution=AnalysisResources.executionFor(program);
                try(var admission=admit(wire(facts,1),pages,execution);var demand=SourceNominalDemand.prepare(admission,0,pages,execution)) {
                    assertEquals(Set.of("v0","v1"),closure(demand,facts,Set.of("v0")));program.close();assertEquals(0,pages.statistics().livePages());zero(execution);
                    assertThrows(IllegalStateException.class,demand::symbolCount);assertThrows(IllegalStateException.class,admission::snapshot);
                }
            }finally{program.close();}
        }zero(resources);
    }
    @Test void primitiveConstructionAndQueryFaultsPreservePrimaryAndBorrowedBackend()throws Exception {
        var facts=facts(3,List.of(new NominalValues.Assignment("s","v0",new NominalValues.Term("CHOICE","",List.of(read(1),read(2))))),List.of());byte[] wire=wire(facts,1);
        for(int operation=0;operation<4;operation++) {
            var resources=resources();
            try(var pages=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.INDEX)) {
                var primary=new PageStore.Failure(PageStore.Reason.IO,"independent nominal demand page fault");var port=new FaultPages(pages,primary);
                try(var admission=admit(wire,port,resources)) {
                    long baseline=pages.statistics().livePages();
                    if(operation<3){port.arm(operation);assertSame(primary,assertThrows(PageStore.Failure.class,()->SourceNominalDemand.prepare(admission,0,port,resources)));}
                    else try(var demand=SourceNominalDemand.prepare(admission,0,port,resources)) {
                        demand.begin();demand.request(0);port.arm(1);assertSame(primary,assertThrows(PageStore.Failure.class,demand::finish));assertThrows(IllegalStateException.class,demand::symbolCount);
                    }
                    try{admission.snapshot();assertEquals(baseline,pages.statistics().livePages());}catch(IllegalStateException expired){assertEquals(0,pages.statistics().livePages());}
                    assertFalse(port.closed);
                }assertEquals(0,pages.statistics().livePages());long borrowed=pages.allocate();pages.release(borrowed);
            }zero(resources);
        }
    }
    @Test void fullCollidingNamesCanonicalReferencesAndQueryBoundariesAreExact()throws Exception {
        assertEquals("Aa".hashCode(),"BB".hashCode());
        var facts=new NominalValues("NOMINAL_TEXT_SOURCE_V4",List.of(new NominalValues.Symbol("Aa",8),new NominalValues.Symbol("BB",8),new NominalValues.Symbol("Aa/B\u0000🚀",8)),
            List.of(new NominalValues.Assignment("s","Aa",new NominalValues.Term("READ","Aa/B\u0000🚀"))),List.of(),List.of());var resources=resources();
        try(var pages=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.INDEX);var admission=admit(wire(facts,1),pages,resources)) {
            long baseline=pages.statistics().livePages();
            try(var demand=SourceNominalDemand.prepare(admission,0,pages,resources)) {
                assertEquals(Set.of("BB"),closure(demand,facts,Set.of("BB")));assertEquals(Set.of("Aa","Aa/B\u0000🚀"),closure(demand,facts,Set.of("Aa")));
                var source=admission.snapshot();long nominal=source.field(admission.unit(0),SourceShape.UNIT_EVIDENCE,12),model=source.field(nominal,SourceShape.NOMINAL_VALUE_EVIDENCE,0),symbols=source.field(model,SourceShape.NOMINAL_VALUES,1);
                long name=source.field(source.element(symbols,SourceShape.SYMBOL,0),SourceShape.SYMBOL,0);demand.begin();demand.requestReference(name);demand.requestReference(name);demand.finish();
                assertTrue(demand.containsReference(name));assertEquals(3,demand.nodeVisits());assertEquals(2,demand.edgeVisits());
                demand.begin();assertThrows(IllegalStateException.class,()->demand.contains(0));assertThrows(IllegalStateException.class,demand::begin);
            }assertEquals(baseline,pages.statistics().livePages());assertEquals(1,admission.unitCount());
        }zero(resources);
    }
    private static final class FaultPages implements PageStore {
        private final PageStore delegate;private final Failure primary;private int operation=-1;private boolean closed;
        FaultPages(PageStore delegate,Failure primary){this.delegate=delegate;this.primary=primary;}
        void arm(int operation){this.operation=operation;}
        private void fail(int candidate){if(candidate==operation){operation=-1;throw primary;}}
        @Override public int pageBytes(){return delegate.pageBytes();}
        @Override public long allocate(){fail(0);return delegate.allocate();}
        @Override public void read(long p,int offset,byte[] bytes,int at,int count){fail(1);delegate.read(p,offset,bytes,at,count);}
        @Override public void write(long p,int offset,byte[] bytes,int at,int count){fail(2);delegate.write(p,offset,bytes,at,count);}
        @Override public void release(long p){delegate.release(p);}
        @Override public void readForCleanup(long p,int offset,byte[] bytes,int at,int count){delegate.readForCleanup(p,offset,bytes,at,count);}
        @Override public void releaseForCleanup(long p){delegate.releaseForCleanup(p);}
        @Override public void flush(){delegate.flush();}
        @Override public Statistics statistics(){return delegate.statistics();}
        @Override public void close(){closed=true;delegate.close();}
    }
}
