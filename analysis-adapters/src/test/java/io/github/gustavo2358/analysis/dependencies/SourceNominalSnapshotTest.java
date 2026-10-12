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

final class SourceNominalSnapshotTest {
    @TempDir Path directory;
    private static final QualifiedSourceJson JSON=new QualifiedSourceJson();
    private static final UnitId UNIT=new UnitId("synthetic-values",List.of(0),"SAMPLE");
    private static final Location LOCATION=new Location("synthetic-values.cbl",1,0,1,10);
    private static final Provenance PROVENANCE=new Provenance(LOCATION,LOCATION,List.of(new Include("main.cbl","copy","copy.cpy",1),new Include("main.cbl","copy","copy.cpy",1)),true);
    private static AnalysisResources resources(){return new AnalysisResources(new AnalysisResources.Limits(64L*1024*1024,16L*1024*1024,0,256L*1024*1024,8,Long.MAX_VALUE,0));}
    private static void zero(AnalysisResources resources){for(var pool:AnalysisResources.Pool.values())assertEquals(0,resources.used(pool),pool.toString());}
    static byte[] wire(UnitEvidence unit)throws Exception{return JSON.encode(new QualifiedSourceDependencies("qualified-source-dependencies","1.6.0","synthetic-values",new Document("cobol-semantic-product","2.67.0","0".repeat(64)),List.of(),List.of(unit)));}
    private static SourceAdmissionSnapshot admit(byte[] wire,PageStore pages,AnalysisResources resources)throws Exception{return SourceAdmissionSnapshot.admit(JSON.decodeSnapshot(new ByteArrayInputStream(wire),pages,resources),pages,resources);}
    private static NominalValues.Term read(String name){return new NominalValues.Term("READ",name);}
    private static NominalValues.Term literal(String text){return new NominalValues.Term("LITERAL",text);}
    private static NominalValues.Term unary(String kind,NominalValues.Term term){return new NominalValues.Term(kind,"",List.of(term));}
    static UnitEvidence fixture(List<NominalValues.Assignment> writes,List<NominalValues.Condition> conditions,List<NominalValueEvidence.Branch> branches,String model,int extent,boolean table) {
        var statements=new ArrayList<Statement>();var nodes=new ArrayList<Node>();var edges=new ArrayList<Derivation>();
        for(int i=0;i<3;i++) {
            statements.add(new Statement(new StatementId(UNIT,"s"+i),PROVENANCE));nodes.add(new Node("n"+i,"ROOT","s"+i,new Support("ENTRY_UNKNOWN",List.of(),List.of(),"NONE")));
            edges.add(new Derivation("d"+i,i==0?List.of():List.of("n"+(i-1)),"n"+i,List.of(),i==0?"PRIMARY_ENTRY":"FLOW",List.of("p"),List.of()));
        }
        var statement=statements.getLast().id();var occurrence=new Occurrence(statement,"COBOL","CALL","PROGRAM",CallNameInterpreter.PROFILE,"COMPUTED",List.of(new Operand(new OperandId(statement,"o"),PROVENANCE)),List.of(),true,List.of("n2"));
        var symbols=List.of(new NominalValues.Symbol("P",model.equals("P")?extent:8,model.equals("P")),new NominalValues.Symbol("Q",model.equals("Q")?extent:8,model.equals("Q")));
        var tables=table?List.of(new NominalValues.TableField("P",List.of(new NominalValues.Initial("Q","PROGA001"),new NominalValues.Initial("Q","PROGB001")))):List.<NominalValues.TableField>of();
        var facts=new NominalValues("NOMINAL_TEXT_SOURCE_V4",symbols,writes,conditions,List.of(new NominalValues.Query("s2","P")),tables);
        var seeds=table?List.<NominalValueEvidence.Seed>of():List.of(new NominalValueEvidence.Seed("P","SELF0001","DECLARATIVE_POSSIBILITY",PROVENANCE),new NominalValueEvidence.Seed("Q","OLD00001","DECLARATIVE_POSSIBILITY",PROVENANCE));
        var source=new NominalValueEvidence(facts,List.of(new NominalValueEvidence.Declaration("P",PROVENANCE),new NominalValueEvidence.Declaration("Q",PROVENANCE)),seeds,branches,List.of(new NominalValueEvidence.Uncertainty("missing/Aa","MISSING_COPY",PROVENANCE),new NominalValueEvidence.Uncertainty("missing/BB","UNLOCATED_INPUT",PROVENANCE)));
        return new UnitEvidence(UNIT,true,statements,List.of(occurrence),List.of(),nodes,edges,List.of(),List.of(),List.of(),List.of(new Proof("p","LOCAL_GRAMMAR","synthetic-values",PROVENANCE,List.of())),List.of(),Optional.of(source));
    }
    private static UnitEvidence fixture(List<NominalValues.Assignment> writes){return fixture(writes,List.of(),List.of(),"",8,false);}
    private static String text(SourceSnapshot source,long reference){if(reference==0)return "";try(var projection=source.project(reference)){return projection.value();}}
    private static Location location(SourceSnapshot source,long reference) {
        return new Location(text(source,source.field(reference,SourceShape.LOCATION,0)),source.integer(source.field(reference,SourceShape.LOCATION,1)),source.integer(source.field(reference,SourceShape.LOCATION,2)),source.integer(source.field(reference,SourceShape.LOCATION,3)),source.integer(source.field(reference,SourceShape.LOCATION,4)));
    }
    /** Test-only complete projection; no such DTO reconstruction is allowed in production. */
    static Provenance provenance(SourceSnapshot source,long reference) {
        long chain=source.field(reference,SourceShape.PROVENANCE,2);var includes=new ArrayList<Include>();
        for(long i=0,n=source.size(chain,SourceShape.INCLUDE);i<n;i++){long row=source.element(chain,SourceShape.INCLUDE,i);includes.add(new Include(text(source,source.field(row,SourceShape.INCLUDE,0)),text(source,source.field(row,SourceShape.INCLUDE,1)),text(source,source.field(row,SourceShape.INCLUDE,2)),source.integer(source.field(row,SourceShape.INCLUDE,3))));}
        return new Provenance(location(source,source.field(reference,SourceShape.PROVENANCE,0)),location(source,source.field(reference,SourceShape.PROVENANCE,1)),includes,source.bool(source.field(reference,SourceShape.PROVENANCE,3)));
    }
    private static List<SourceValuesProvider.Candidate> projection(SourceNominalSnapshot result,SourceAdmissionSnapshot admission,long query) {
        long value=result.value(query);if(value==0)return List.of();var values=result.values();var source=admission.snapshot();var out=new ArrayList<SourceValuesProvider.Candidate>();
        long uncertain=source.field(result.nominalEvidence(),SourceShape.NOMINAL_VALUE_EVIDENCE,4);var uncertainties=new ArrayList<NominalValueEvidence.Uncertainty>();
        for(long i=0,n=source.size(uncertain,SourceShape.UNCERTAINTY);i<n;i++){long row=source.element(uncertain,SourceShape.UNCERTAINTY,i);uncertainties.add(new NominalValueEvidence.Uncertainty(text(source,source.field(row,SourceShape.UNCERTAINTY,0)),text(source,source.field(row,SourceShape.UNCERTAINTY,1)),provenance(source,source.field(row,SourceShape.UNCERTAINTY,2))));}
        try(var candidates=values.candidates(value)) {
            while(candidates.advance()) {
                var atoms=new ArrayList<Long>();try(var proofs=values.supports(candidates.value())){while(proofs.advance())atoms.add(proofs.key());}
                atoms.sort(Comparator.comparingInt((Long atom)->result.proofKind(atom)).thenComparing(atom->text(source,result.proofOwner(atom))).thenComparing(atom->text(source,result.proofDetail(atom))));
                var evidence=new LinkedHashSet<SourceValuesProvider.Evidence>();
                for(long atom:atoms)evidence.add(new SourceValuesProvider.Evidence(result.proofKind(atom)==2?"ASSIGNMENT":"DECLARATION_VALUE",text(source,result.proofKind(atom)==1?result.proofDetail(atom):result.proofOwner(atom)),provenance(source,result.proofProvenance(atom))));
                var assumptions=new ArrayList<>(List.of("NOMINAL_DECLARATIONS_PRESERVE_MEANING","NO_UNMODELED_STORAGE_INTERFERENCE"));
                if(result.affected(query))assumptions.add("UNKNOWN_CONTROL_CAN_COMPLETE");if((values.flags(value)&4)!=0||result.summary(query))assumptions.add("TABLE_INDEX_NOT_REFINED");if((values.flags(value)&2)!=0)assumptions.add("SYNTHETIC_MODEL_IS_NOT_KILL_PROOF");
                if(evidence.stream().anyMatch(e->e.kind().equals("DECLARATION_VALUE")))assumptions.add("DECLARATIVE_INITIAL_VALUES_APPLY");
                out.add(new SourceValuesProvider.Candidate(values.text(candidates.key()),new SourceValuesProvider.Support(SourceValuesProvider.PROFILE,List.copyOf(evidence),assumptions,uncertainties)));
            }
        }out.sort(Comparator.comparing(SourceValuesProvider.Candidate::rawValue));return List.copyOf(out);
    }
    private void compare(UnitEvidence unit,List<String> expected)throws Exception {
        var resident=new SourceValuesProvider(unit,Set.of("s2"));assertEquals(expected,resident.candidates("s2").stream().map(SourceValuesProvider.Candidate::rawValue).toList(),"independent named scenario oracle");var resources=resources();
        try(var pages=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.DOMAIN);var admission=admit(wire(unit),pages,resources);var control=SourceControlSnapshot.prepare(admission,pages,resources)) {
            long baseline=pages.statistics().livePages();
            try(var result=SourceNominalSnapshot.prepare(admission,0,q->true,control,pages,resources)){assertEquals(resident.candidates("s2"),projection(result,admission,0));assertEquals(1,result.activeQueries());assertEquals(resident.workItems(),result.workItems());}
            assertEquals(baseline,pages.statistics().livePages());assertEquals(1,admission.unitCount());
        }zero(resources);
    }
    @Test void completeOverwriteSnapshotModelTableChoiceAndTransformLawsMatchIndependentValuesAndResidentSupports()throws Exception {
        compare(fixture(List.of(new NominalValues.Assignment("s0","P",literal("REAL0001")))),List.of("REAL0001"));
        compare(fixture(List.of(new NominalValues.Assignment("s0","P",read("Q")),new NominalValues.Assignment("s1","Q",literal("NEW00001")))),List.of("OLD00001"));
        compare(fixture(List.of(new NominalValues.Assignment("s0","P",read("Q")),new NominalValues.Assignment("s0","Q",read("P")))),List.of("OLD00001"));
        compare(fixture(List.of(new NominalValues.Assignment("s0","P",unary("UPPER_ASCII",unary("TRIM_SPACES",literal("  progb001  ")))))),List.of("PROGB001"));
        compare(fixture(List.of(new NominalValues.Assignment("s0","P",unary("TRIM_SPACES",literal(" \tA\t "))))),List.of("\tA\t     "));
        compare(fixture(List.of(new NominalValues.Assignment("s0","P",unary("UPPER_ASCII",literal("éprog001"))))),List.of());
        compare(fixture(List.of(new NominalValues.Assignment("s0","P",literal("PROGA001")),new NominalValues.Assignment("s1","P",new NominalValues.Term("UNKNOWN",""))),List.of(),List.of(),"P",1,false),List.of("P","PROGA001"));
        compare(fixture(List.of(new NominalValues.Assignment("s0","P",read("Q"))),List.of(),List.of(),"Q",1,false),List.of("SELF0001"));
        compare(fixture(List.of(new NominalValues.Assignment("s0","P",literal("PROGC001"))),List.of(),List.of(),"",8,true),List.of("PROGA001","PROGB001","PROGC001"));
        compare(fixture(List.of(),List.of(),List.of(),"Q",1,true),List.of());
        compare(fixture(List.of(new NominalValues.Assignment("s0","P",new NominalValues.Term("CHOICE","",List.of(literal("PROGC001"),literal("PROGD001"))))),List.of(),List.of(),"",8,true),List.of("PROGA001","PROGB001","PROGC001","PROGD001"));
    }
    @Test void conditionalEqualityFigurativeNestedLogicAndModelTableFiltersRemainExact()throws Exception {
        var equality=new NominalValues.Condition("s1",new NominalValues.Predicate("EQ",List.of(read("P"),read("Q")),List.of()));
        compare(fixture(List.of(new NominalValues.Assignment("s0","P",read("Q"))),List.of(equality),List.of(new NominalValueEvidence.Branch("d2",false)),"",8,false),List.of());
        compare(fixture(List.of(new NominalValues.Assignment("s0","P",read("Q"))),List.of(equality),List.of(new NominalValueEvidence.Branch("d2",true)),"",8,false),List.of("OLD00001"));
        var low=new NominalValues.Condition("s0",new NominalValues.Predicate("EQ",List.of(read("P"),new NominalValues.Term("LOW_VALUES","")),List.of()));
        compare(fixture(List.of(),List.of(low),List.of(new NominalValueEvidence.Branch("d1",true)),"",8,false),List.of());
        for(boolean branch:List.of(false,true)) {
            var predicate=new NominalValues.Condition("s1",new NominalValues.Predicate("EQ",List.of(read("P"),literal("PROGA001")),List.of()));
            compare(fixture(List.of(),List.of(predicate),List.of(new NominalValueEvidence.Branch("d2",branch)),"",8,true),List.of("PROGA001","PROGB001"));
            compare(fixture(List.of(new NominalValues.Assignment("s0","P",read("Q"))),List.of(equality),List.of(new NominalValueEvidence.Branch("d2",branch)),"Q",1,false),List.of("SELF0001"));
        }
        var eq=equality.predicate();var nested=new NominalValues.Condition("s1",new NominalValues.Predicate("AND",List.of(),List.of(eq,new NominalValues.Predicate("OR",List.of(),List.of(eq,new NominalValues.Predicate("NOT",List.of(),List.of(eq)))))));
        compare(fixture(List.of(new NominalValues.Assignment("s0","P",read("Q"))),List.of(nested),List.of(new NominalValueEvidence.Branch("d2",true)),"",8,false),List.of("OLD00001"));
        var choice=new NominalValues.Term("CHOICE","",List.of(literal("PROGA001"),literal("PROGB001")));
        var rhs=new NominalValues.Term("CHOICE","",List.of(literal("PROGA001"),literal("NOOTHER1")));
        var nondeterministicEq=new NominalValues.Predicate("EQ",List.of(read("P"),rhs),List.of());
        var nondeterministic=new NominalValues.Condition("s1",new NominalValues.Predicate("AND",List.of(),List.of(nondeterministicEq,nondeterministicEq)));
        compare(fixture(List.of(new NominalValues.Assignment("s0","P",choice)),List.of(nondeterministic),List.of(new NominalValueEvidence.Branch("d2",true)),"",8,false),List.of("PROGA001"));
        compare(fixture(List.of(new NominalValues.Assignment("s0","P",choice)),List.of(nondeterministic),List.of(new NominalValueEvidence.Branch("d2",false)),"",8,false),List.of("PROGA001","PROGB001"));
    }
    @Test void everyExistingNominalLiteralPreservesCompleteOrderedEvidenceAssumptionsUncertaintiesAndQueries()throws Exception {
        int units=0;
        try(var paths=Files.walk(Path.of("src/test/resources"))) {for(var path:paths.filter(p->p.toString().endsWith(".source.json")).sorted().toList()) {
            byte[] wire=Files.readAllBytes(path);var model=JSON.decode(wire);var resources=resources();
            try(var pages=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.DOMAIN);var admission=admit(wire,pages,resources);var control=SourceControlSnapshot.prepare(admission,pages,resources)) {
                for(int u=0;u<model.units().size();u++)if(model.units().get(u).nominalValues().isPresent()) {
                    var unit=model.units().get(u);var queries=unit.nominalValues().orElseThrow().facts().queries();var requested=new HashSet<String>();queries.forEach(q->requested.add(q.statement()));var resident=new SourceValuesProvider(unit,requested);
                    try(var result=SourceNominalSnapshot.prepare(admission,u,q->true,control,pages,resources)){for(int q=0;q<queries.size();q++)assertEquals(resident.candidates(queries.get(q).statement()),projection(result,admission,q),path+"/"+q);}units++;
                }
            }zero(resources);
        }}
        assertEquals(4,units);
    }
    @Test void noRequestedQueryAndUnobservedTailNeverAllocatePrivateIndexesOrChangeSupports()throws Exception {
        var unit=fixture(List.of(new NominalValues.Assignment("s0","P",read("Q"))));var nodes=new ArrayList<>(unit.nodes());var edges=new ArrayList<>(unit.derivations());
        for(int i=0;i<1024;i++){nodes.add(new Node("tail"+i,"ROOT","s0",unit.nodes().getFirst().support()));edges.add(new Derivation("tail-link"+i,List.of(i==0?"n2":"tail"+(i-1)),"tail"+i,List.of(),"FLOW",List.of("p"),List.of()));}
        var larger=new UnitEvidence(unit.unit(),unit.controlAvailable(),unit.statements(),unit.occurrences(),unit.targets(),nodes,edges,unit.selections(),unit.events(),unit.guards(),unit.proofs(),unit.frontiers(),unit.nominalValues());var expected=new SourceValuesProvider(unit,Set.of("s2"));var resources=resources();
        try(var pages=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.DOMAIN);var admission=admit(wire(larger),pages,resources);var control=SourceControlSnapshot.prepare(admission,pages,resources)) {
            long baseline=pages.statistics().livePages();
            try(var result=SourceNominalSnapshot.prepare(admission,0,q->false,control,pages,resources)){assertEquals(0,result.activeQueries());assertEquals(0,result.workItems());assertEquals(0,result.retainedMetadata());assertEquals(List.of(),projection(result,admission,0));}
            assertEquals(baseline,pages.statistics().livePages());
            try(var result=SourceNominalSnapshot.prepare(admission,0,q->true,control,pages,resources)){assertEquals(expected.candidates("s2"),projection(result,admission,0));assertEquals(expected.workItems(),result.workItems());}
            assertEquals(baseline,pages.statistics().livePages());
        }zero(resources);
    }
    private static UnitEvidence changed(UnitEvidence old,List<Statement> statements,List<Occurrence> occurrences,List<Node> nodes,List<Derivation> edges,List<Proof> proofs,NominalValueEvidence nominal) {
        return new UnitEvidence(old.unit(),old.controlAvailable(),statements,occurrences,old.targets(),nodes,edges,old.selections(),old.events(),old.guards(),proofs,old.frontiers(),Optional.of(nominal));
    }
    @Test void callerPresenceAndUnusedVersusUsedUnknownControlKeepDistinctNominalPremises()throws Exception {
        var unit=fixture(List.of(new NominalValues.Assignment("s0","P",literal("REAL0001"))));var nodes=new ArrayList<>(unit.nodes());nodes.add(new Node("caller","ROOT","s0",unit.nodes().getFirst().support()));
        var edges=new ArrayList<>(unit.derivations());var last=edges.getLast();edges.set(2,new Derivation(last.id(),last.source(),last.destination(),List.of("caller"),last.authority(),last.proofs(),last.selection()));
        // Every SOURCE node must be statically grounded. Make the caller's
        // certificate valid, then refute its nominal branch independently.
        edges.add(new Derivation("caller-link",List.of("n0"),"caller",List.of(),"FLOW",List.of("p"),List.of()));
        var old=unit.nominalValues().orElseThrow();var f=old.facts();var predicate=new NominalValues.Condition("s0",new NominalValues.Predicate("EQ",List.of(read("P"),literal("SELF0001")),List.of()));
        var facts=new NominalValues(f.authority(),f.symbols(),f.assignments(),List.of(predicate),f.queries(),f.tableFields());
        var refuted=new NominalValueEvidence(facts,old.declarations(),old.seeds(),List.of(new NominalValueEvidence.Branch("caller-link",false)),old.uncertainties());
        compare(changed(unit,unit.statements(),unit.occurrences(),nodes,edges,unit.proofs(),refuted),List.of());
        var enabled=new NominalValueEvidence(facts,old.declarations(),old.seeds(),List.of(new NominalValueEvidence.Branch("caller-link",true)),old.uncertainties());
        compare(changed(unit,unit.statements(),unit.occurrences(),nodes,edges,unit.proofs(),enabled),List.of("REAL0001"));
        var unused=new ArrayList<>(unit.proofs());unused.add(new Proof("unused","CONTROL_POSSIBILITY","synthetic-hypothesis",PROVENANCE,List.of()));
        compare(changed(unit,unit.statements(),unit.occurrences(),unit.nodes(),unit.derivations(),unused,unit.nominalValues().orElseThrow()),List.of("REAL0001"));
        var used=List.of(new Proof("p","CONTROL_POSSIBILITY","synthetic-hypothesis",PROVENANCE,List.of()));
        var hypothetical=changed(unit,unit.statements(),unit.occurrences(),unit.nodes(),unit.derivations(),used,unit.nominalValues().orElseThrow());compare(hypothetical,List.of("REAL0001"));
        assertTrue(new SourceValuesProvider(hypothetical,Set.of("s2")).candidates("s2").getFirst().support().assumptions().contains("UNKNOWN_CONTROL_CAN_COMPLETE"));
    }
    @Test void fullProofTuplesWithSeparatorsNeverAliasAndRetainCompleteOrderedProvenance()throws Exception {
        var base=fixture(List.of());var statements=List.of(new Statement(new StatementId(UNIT,"stmt/a"),PROVENANCE),new Statement(new StatementId(UNIT,"stmt"),PROVENANCE),base.statements().getLast());
        var nodes=List.of(new Node("n0","ROOT","stmt/a",base.nodes().getFirst().support()),new Node("n1","ROOT","stmt",base.nodes().getFirst().support()),base.nodes().getLast());
        var facts=new NominalValues("NOMINAL_TEXT_SOURCE_V4",List.of(new NominalValues.Symbol("P",8),new NominalValues.Symbol("a/P",8)),List.of(new NominalValues.Assignment("stmt/a","P",literal("REAL0001")),new NominalValues.Assignment("stmt","a/P",read("P"))),List.of(),List.of(new NominalValues.Query("s2","a/P")));
        var old=base.nominalValues().orElseThrow();var nominal=new NominalValueEvidence(facts,List.of(new NominalValueEvidence.Declaration("P",PROVENANCE),new NominalValueEvidence.Declaration("a/P",PROVENANCE)),List.of(),List.of(),old.uncertainties());
        compare(changed(base,statements,base.occurrences(),nodes,base.derivations(),base.proofs(),nominal),List.of("REAL0001"));
        var expected=new SourceValuesProvider(changed(base,statements,base.occurrences(),nodes,base.derivations(),base.proofs(),nominal),Set.of("s2")).candidates("s2").getFirst().support().evidence();
        assertEquals(List.of("stmt","stmt/a"),expected.stream().map(SourceValuesProvider.Evidence::reference).toList());assertTrue(expected.stream().allMatch(e->e.provenance().includeChain().size()==2));
    }
    @Test void growingProofHistoryUsesFixedResidentControlsAndRetiresStateRootsBeforeReady()throws Exception {
        long fixed=-1;
        for(int n:new int[]{1,4,16,64,256}) {
            var base=fixture(List.of());var statements=new ArrayList<Statement>();var nodes=new ArrayList<Node>();var edges=new ArrayList<Derivation>();var writes=new ArrayList<NominalValues.Assignment>();
            for(int i=0;i<=n;i++){statements.add(new Statement(new StatementId(UNIT,"s"+i),PROVENANCE));nodes.add(new Node("n"+i,"ROOT","s"+i,base.nodes().getFirst().support()));edges.add(new Derivation("d"+i,i==0?List.of():List.of("n"+(i-1)),"n"+i,List.of(),i==0?"PRIMARY_ENTRY":"FLOW",List.of("p"),List.of()));if(i<n)writes.add(new NominalValues.Assignment("s"+i,"P",read("P")));}
            var statement=statements.getLast().id();var occurrence=new Occurrence(statement,"COBOL","CALL","PROGRAM",CallNameInterpreter.PROFILE,"COMPUTED",List.of(new Operand(new OperandId(statement,"o"),PROVENANCE)),List.of(),true,List.of("n"+n));var old=base.nominalValues().orElseThrow();
            var facts=new NominalValues("NOMINAL_TEXT_SOURCE_V4",old.facts().symbols(),writes,List.of(),List.of(new NominalValues.Query("s"+n,"P")));var nominal=new NominalValueEvidence(facts,old.declarations(),old.seeds(),List.of(),old.uncertainties());var unit=changed(base,statements,List.of(occurrence),nodes,edges,base.proofs(),nominal);var resources=resources();
            try(var pages=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.DOMAIN);var admission=admit(wire(unit),pages,resources);var control=SourceControlSnapshot.prepare(admission,pages,resources)) {
                long before=resources.heapUsed(),baseline=pages.statistics().livePages();
                try(var result=SourceNominalSnapshot.prepare(admission,0,q->true,control,pages,resources)) {
                    long delta=resources.heapUsed()-before;if(fixed<0)fixed=delta;assertEquals(fixed,delta);var actual=projection(result,admission,0);assertEquals(1,actual.size());assertEquals("SELF0001",actual.getFirst().rawValue());assertEquals(n+1,actual.getFirst().support().evidence().size());assertEquals(n+1,result.workItems());
                    assertEquals(new SourceValuesProvider(unit,Set.of("s"+n)).candidates("s"+n),actual);assertTrue(result.values().records()<32L*(n+1),"unobserved state roots survived ready teardown");
                }assertEquals(baseline,pages.statistics().livePages());
            }zero(resources);
        }
    }
    @Test void foreignOwnersDenialAndCallbackFailuresPreserveParentsAndOriginalPrimary()throws Exception {
        var resources=resources();var foreign=resources();byte[] wire=wire(fixture(List.of()));
        try(var pages=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.DOMAIN);var other=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.DOMAIN);var admission=admit(wire,pages,resources);var otherAdmission=admit(wire,pages,resources);
            var control=SourceControlSnapshot.prepare(admission,pages,resources);var otherControl=SourceControlSnapshot.prepare(otherAdmission,pages,resources)) {
            long baseline=pages.statistics().livePages();assertThrows(IllegalArgumentException.class,()->SourceNominalSnapshot.prepare(admission,0,q->true,control,other,resources));assertThrows(IllegalArgumentException.class,()->SourceNominalSnapshot.prepare(admission,0,q->true,control,pages,foreign));assertThrows(IllegalArgumentException.class,()->SourceNominalSnapshot.prepare(admission,0,q->true,otherControl,pages,resources));
            for(int available:new int[]{4095,4096+1023,4096+3071})try(var pressure=resources.reserve(AnalysisResources.Pool.RESIDENT,resources.limits().heapBytes()-resources.heapUsed()-available,AnalysisResources.Phase.DOMAIN)) {assertNotNull(pressure);var denial=assertThrows(AnalysisResources.Exhausted.class,()->SourceNominalSnapshot.prepare(admission,0,q->true,control,pages,resources));assertEquals(AnalysisResources.Resource.HEAP,denial.resource());assertEquals(baseline,pages.statistics().livePages());}
            var primary=new IllegalStateException("independent requested-query callback failure");assertSame(primary,assertThrows(IllegalStateException.class,()->SourceNominalSnapshot.prepare(admission,0,q->{throw primary;},control,pages,resources)));assertEquals(baseline,pages.statistics().livePages());
            try(var result=SourceNominalSnapshot.prepare(admission,0,q->true,control,pages,resources)){assertThrows(IndexOutOfBoundsException.class,()->result.value(-1));assertThrows(IllegalStateException.class,result::activeQueries);}
            assertEquals(baseline,pages.statistics().livePages());assertEquals(1,admission.unitCount());assertEquals(1,otherAdmission.unitCount());
        }zero(resources);zero(foreign);
    }
    @Test void nativeAndSourceOwnerExpiryRetirePreparedValuesBeforeAnyOtherQuery()throws Exception {
        var resources=resources();byte[] wire=wire(fixture(List.of(new NominalValues.Assignment("s0","P",read("Q")))));
        try(var input=new DataflowAirReader().readSnapshot(Path.of("src/test/resources/cp6/dynamic-x8.air.json"),resources);var pages=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.DOMAIN)) {
            var program=new SnapshotProgram(input.checked(),input.newIdentityStorage(),input.newOrderStorage(),resources);
            try {var execution=AnalysisResources.executionFor(program);try(var admission=admit(wire,pages,execution);var control=SourceControlSnapshot.prepare(admission,pages,execution);var result=SourceNominalSnapshot.prepare(admission,0,q->true,control,pages,execution)) {
                assertFalse(projection(result,admission,0).isEmpty());program.close();assertEquals(0,pages.statistics().livePages());zero(execution);assertThrows(IllegalStateException.class,result::activeQueries);
            }}finally{program.close();}
        }zero(resources);
        resources=resources();
        try(var pages=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.DOMAIN);var admission=admit(wire,pages,resources);var control=SourceControlSnapshot.prepare(admission,pages,resources);var result=SourceNominalSnapshot.prepare(admission,0,q->true,control,pages,resources)) {
            admission.snapshot().close();assertEquals(0,pages.statistics().livePages());assertThrows(IllegalStateException.class,result::activeQueries);
        }zero(resources);
    }
    @Test void eachPrimitivePrepareAndResultFaultCannotPublishPartialValuesOrCloseBorrowedBackend()throws Exception {
        byte[] wire=wire(fixture(List.of(new NominalValues.Assignment("s0","P",read("Q")))));
        for(int operation=0;operation<4;operation++) {
            var resources=resources();
            try(var pages=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.DOMAIN)) {
                var primary=new PageStore.Failure(PageStore.Reason.IO,"independent nominal result page fault");var port=new FaultPages(pages,primary);
                try(var admission=admit(wire,port,resources);var control=SourceControlSnapshot.prepare(admission,port,resources)) {
                    long baseline=pages.statistics().livePages();
                    if(operation<3){port.arm(operation);assertSame(primary,assertThrows(PageStore.Failure.class,()->SourceNominalSnapshot.prepare(admission,0,q->true,control,port,resources)));}
                    else try(var result=SourceNominalSnapshot.prepare(admission,0,q->true,control,port,resources)){port.arm(1);assertSame(primary,assertThrows(PageStore.Failure.class,()->result.value(0)));assertThrows(IllegalStateException.class,result::activeQueries);}
                    try{admission.snapshot();assertEquals(baseline,pages.statistics().livePages());}catch(IllegalStateException expired){assertEquals(0,pages.statistics().livePages());}assertFalse(port.closed);
                }assertEquals(0,pages.statistics().livePages());long borrowed=pages.allocate();pages.release(borrowed);
            }zero(resources);
        }
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
