package io.github.gustavo2358.analysis.dependencies;

import io.github.gustavo2358.analysis.adapters.*;
import io.github.gustavo2358.analysis.dependencies.source.*;
import io.github.gustavo2358.analysis.solver.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.io.ByteArrayInputStream;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

final class SourceControlSnapshotTest {
    @TempDir Path directory;
    private static final ObjectMapper JSON=new ObjectMapper();
    private static AnalysisResources resources(){return new AnalysisResources(new AnalysisResources.Limits(1048576,16384,0,128L*1024*1024,2,Long.MAX_VALUE,0));}
    private static void zero(AnalysisResources resources){for(var pool:AnalysisResources.Pool.values())assertEquals(0,resources.used(pool));}
    private static byte[] literal()throws Exception{return Files.readAllBytes(Path.of("src/test/resources/source-possibility/unknown-native.source.json"));}
    private static SourceAdmissionSnapshot admit(byte[] wire,PageStore pages,AnalysisResources resources)throws Exception {
        return SourceAdmissionSnapshot.admit(new QualifiedSourceJson().decodeSnapshot(new ByteArrayInputStream(wire),pages,resources),pages,resources);
    }
    private static void compare(byte[] wire,SourceControlSnapshot control)throws Exception {
        var legacy=new QualifiedSourceJson().decode(wire);
        for(int i=0;i<legacy.units().size();i++) {
            var unit=legacy.units().get(i);var affected=SourceControlEvidence.affected(unit);var assumed=SourceControlEvidence.assumedOnly(unit);
            var hypothetical=SourceControlEvidence.hypotheticalProofs(unit.proofs()).ids();
            for(int j=0;j<unit.nodes().size();j++) {
                assertEquals(affected.contains(unit.nodes().get(j).id()),control.affected(i,j),"affected "+i+"/"+j);
                assertEquals(assumed.contains(unit.nodes().get(j).id()),control.assumedOnly(i,j),"assumed-only "+i+"/"+j);
            }
            for(int j=0;j<unit.proofs().size();j++)assertEquals(hypothetical.contains(unit.proofs().get(j).id()),control.hypothetical(i,j),"proof "+i+"/"+j);
        }
    }
    @Test void allExistingLiteralsPreserveTheThreeDistinctControlFacts()throws Exception {
        int count=0;
        try(var paths=Files.walk(Path.of("src/test/resources"))) {
            for(var path:paths.filter(p->p.toString().endsWith(".source.json")).sorted().toList()) {
                byte[] wire=Files.readAllBytes(path);if(!JSON.readTree(wire).path("schema").asText().equals("qualified-source-dependencies"))continue;
                var resources=resources();
                try(var pages=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.INDEX);
                    var admission=admit(wire,pages,resources)) {
                    long before=pages.statistics().livePages();
                    try(var control=SourceControlSnapshot.prepare(admission,pages,resources)){compare(wire,control);}
                    assertEquals(before,pages.statistics().livePages());assertTrue(admission.unitCount()>0);
                }zero(resources);count++;
            }
        }
        assertEquals(53,count);System.out.println("PAGED_SOURCE_CONTROL_LITERALS fullAffectedAssumedOnlyProofParity="+count+" CLI=NOT_RUN");
    }
    private static ObjectNode proof(ObjectNode origin,String id,String kind,String dependency) {
        var proof=JSON.createObjectNode();proof.put("id",id);proof.put("kind",kind);proof.put("rule","independent-control-oracle");
        proof.set("provenance",origin.deepCopy());proof.set("dependencies",dependency==null?JSON.createArrayNode():JSON.createArrayNode().add(dependency));return proof;
    }
    private static void derivation(ArrayNode rows,String id,int source,int caller,int destination,String proof) {
        var row=JSON.createObjectNode();row.put("id",id);row.put("destination","n"+destination);row.put("authority",source<0?"PRIMARY_ENTRY":"SYNTHETIC_TRANSFER");
        row.set("source",source<0?JSON.createArrayNode():JSON.createArrayNode().add("n"+source));row.set("callerPremise",caller<0?JSON.createArrayNode():JSON.createArrayNode().add("n"+caller));
        row.set("proofs",JSON.createArrayNode().add(proof));row.set("selection",JSON.createArrayNode());rows.add(row);
    }
    private static ObjectNode graph(int chain,boolean seed)throws Exception {
        var root=(ObjectNode)JSON.readTree(literal());root.put("version","1.6.0");
        var template=(ObjectNode)root.path("units").get(0);var origin=(ObjectNode)template.path("proofs").get(0).path("provenance");var units=JSON.createArrayNode();
        for(int u=0;u<2;u++) {
            var unit=template.deepCopy();var unitId=(ObjectNode)unit.path("unit");unitId.put("canonicalProgramName","INDEPENDENT-"+u);unitId.set("structuralPath",JSON.createArrayNode().add(u));
            unit.put("controlAvailable",true);unit.remove("nominalValues");
            for(String field:new String[]{"statements","occurrences","targets","selections","events","guards","frontiers","nativeFiles"})unit.set(field,JSON.createArrayNode());
            var proofs=JSON.createArrayNode();String rootProof;
            if(chain==0) {
                proofs.add(proof(origin,"unused",u==0&&seed?"CONTROL_POSSIBILITY":"LOCAL_GRAMMAR",null));
                proofs.add(proof(origin,"link","LOCAL_GRAMMAR","BB"));
                proofs.add(proof(origin,"BB",u==0&&seed?"CONTROL_POSSIBILITY":"LOCAL_GRAMMAR",null));rootProof="link";
            }else {
                for(int i=chain-1;i>=0;i--)proofs.add(proof(origin,"p"+i,i==0&&u==0&&seed?"CONTROL_POSSIBILITY":"LOCAL_GRAMMAR",i==0?null:"p"+(i-1)));
                rootProof="p"+(chain-1);
            }
            proofs.add(proof(origin,"Aa","LOCAL_GRAMMAR",null));unit.set("proofs",proofs);
            var nodes=JSON.createArrayNode();
            for(int i=0;i<6;i++) {
                var node=JSON.createObjectNode();node.put("id","n"+i);node.put("context","SYNTHETIC");node.put("location","l"+i);
                var support=JSON.createObjectNode();support.put("kind","ENTRY_UNKNOWN");support.put("cause","NONE");support.set("target",JSON.createArrayNode());support.set("activation",JSON.createArrayNode());node.set("support",support);nodes.add(node);
            }
            unit.set("nodes",nodes);var derivations=JSON.createArrayNode();
            derivation(derivations,"root0",-1,-1,0,"Aa");derivation(derivations,"root1",-1,-1,1,rootProof);
            derivation(derivations,"both",0,1,2,"Aa");derivation(derivations,"affected-alternative",2,-1,3,"Aa");
            derivation(derivations,"grounded-alternative",0,-1,3,"Aa");derivation(derivations,"same-premise",0,0,4,"Aa");
            derivation(derivations,"cycle-forward",3,-1,5,"Aa");derivation(derivations,"cycle-back",5,-1,3,"Aa");unit.set("derivations",derivations);units.add(unit);
        }
        root.set("units",units);new QualifiedSourceJson().decode(JSON.writeValueAsBytes(root));return root;
    }
    @Test void independentAndOrGroundedCycleAndSharedUnitIdsRemainExact()throws Exception {
        byte[] wire=JSON.writeValueAsBytes(graph(0,true));var resources=resources();
        try(var pages=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.INDEX);
            var admission=admit(wire,pages,resources);var control=SourceControlSnapshot.prepare(admission,pages,resources)) {
            compare(wire,control);assertEquals(10,control.retainedEntries());
            for(int i=0;i<6;i++) {
                assertEquals(Set.of(1,2,3,5).contains(i),control.affected(0,i));assertEquals(Set.of(1,2).contains(i),control.assumedOnly(0,i));
                assertFalse(control.affected(1,i));assertFalse(control.assumedOnly(1,i));
            }
            assertTrue(control.hypothetical(0,0));assertTrue(control.hypothetical(0,1));assertTrue(control.hypothetical(0,2));assertFalse(control.hypothetical(0,3));
        }zero(resources);
    }
    @Test void reverseProofChainsSpillWithoutRetainingNoSeedIndexes()throws Exception {
        long fixed=-1;
        for(int count:new int[]{1,4,16,64,256,1024,4096})for(boolean seed:new boolean[]{false,true}) {
            byte[] wire=JSON.writeValueAsBytes(graph(count,seed));var resources=resources();
            try(var pages=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.INDEX);
                var admission=admit(wire,pages,resources)) {
                long before=resources.heapUsed(),reads=pages.statistics().bytesRead();
                try(var control=SourceControlSnapshot.prepare(admission,pages,resources)) {
                    compare(wire,control);assertEquals(seed?count+7:0,control.retainedEntries());
                    long heap=resources.heapUsed()-before;if(fixed<0)fixed=heap;assertEquals(fixed,heap);
                    System.out.println("PAGED_SOURCE_CONTROL_GEOMETRY count="+count+" seeded="+seed+" retained="+control.retainedEntries()+" fixedResidentDelta="+heap+" reads="+(pages.statistics().bytesRead()-reads)+" temporaryPeak="+resources.peak(AnalysisResources.Pool.TEMPORARY));
                }
            }zero(resources);
        }
    }
    @Test void closingConsumerBorrowsAndParentExpiryRetiresEveryIssuedConsumerImmediately()throws Exception {
        var resources=resources();
        try(var pages=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.INDEX)) {
            var admission=admit(literal(),pages,resources);long before=pages.statistics().livePages();
            var first=SourceControlSnapshot.prepare(admission,pages,resources);var middle=SourceControlSnapshot.prepare(admission,pages,resources);var last=SourceControlSnapshot.prepare(admission,pages,resources);
            middle.close();assertTrue(admission.unitCount()>0);assertTrue(first.retainedEntries()>0);assertTrue(last.retainedEntries()>0);
            first.close();last.close();assertEquals(before,pages.statistics().livePages());
            first=SourceControlSnapshot.prepare(admission,pages,resources);last=SourceControlSnapshot.prepare(admission,pages,resources);
            admission.snapshot().close();assertEquals(0,pages.statistics().livePages());
            assertThrows(IllegalStateException.class,first::retainedEntries);assertThrows(IllegalStateException.class,last::retainedEntries);
            first.close();last.close();admission.close();long borrowed=pages.allocate();pages.release(borrowed);
        }zero(resources);
    }
    @Test void nativeCommonOwnerExpiryRetiresConsumerWithoutAnotherQuery()throws Exception {
        var resources=new AnalysisResources(new AnalysisResources.Limits(64L*1024*1024,16L*1024*1024,0,256L*1024*1024,8,Long.MAX_VALUE,0));
        try(var input=new DataflowAirReader().readSnapshot(Path.of("src/test/resources/cp6/dynamic-x8.air.json"),resources);
            var pages=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.INDEX)) {
            var program=new SnapshotProgram(input.checked(),input.newIdentityStorage(),input.newOrderStorage(),resources);
            try {
                var execution=AnalysisResources.executionFor(program);
                try(var admission=admit(literal(),pages,execution);var control=SourceControlSnapshot.prepare(admission,pages,execution)) {
                    assertTrue(control.retainedEntries()>0);program.close();assertEquals(0,pages.statistics().livePages());zero(execution);
                    assertThrows(IllegalStateException.class,control::retainedEntries);assertThrows(IllegalStateException.class,admission::snapshot);
                }
            }finally{program.close();}
        }zero(resources);
    }
    @Test void foreignContextAndFundedControlDenialDoNotStealAdmission()throws Exception {
        var resources=resources();var foreign=resources();
        try(var pages=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.INDEX);
            var other=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.INDEX);
            var admission=admit(literal(),pages,resources)) {
            long before=pages.statistics().livePages();
            assertThrows(IllegalArgumentException.class,()->SourceControlSnapshot.prepare(admission,other,resources));
            assertThrows(IllegalArgumentException.class,()->SourceControlSnapshot.prepare(admission,pages,foreign));
            for(int remaining:new int[]{1023,1024+511,1024+512+3071}) {
                try(var pressure=resources.reserve(AnalysisResources.Pool.RESIDENT,resources.limits().heapBytes()-resources.heapUsed()-remaining,AnalysisResources.Phase.INDEX)) {
                    assertNotNull(pressure);var failure=assertThrows(AnalysisResources.Exhausted.class,()->SourceControlSnapshot.prepare(admission,pages,resources));
                    assertEquals(AnalysisResources.Resource.HEAP,failure.resource());assertEquals(before,pages.statistics().livePages());assertTrue(admission.unitCount()>0);
                }
            }
            try(var control=SourceControlSnapshot.prepare(admission,pages,resources)){assertTrue(control.retainedEntries()>0);}
        }zero(resources);zero(foreign);
    }
    @Test void everyPrimitiveFaultPreservesPrimaryAndRetiresDerivedOwnership()throws Exception {
        for(int operation:new int[]{0,1,2,3}) {
            var resources=resources();
            try(var pages=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.INDEX)) {
                var failure=new PageStore.Failure(PageStore.Reason.IO,"independent paged SOURCE control fault");var port=new FaultPages(pages,failure);
                try(var admission=admit(literal(),port,resources)) {
                    long before=pages.statistics().livePages();
                    if(operation<3) {port.arm(operation);assertSame(failure,assertThrows(PageStore.Failure.class,()->SourceControlSnapshot.prepare(admission,port,resources)));}
                    else {
                        try(var control=SourceControlSnapshot.prepare(admission,port,resources)) {
                            port.arm(1);assertSame(failure,assertThrows(PageStore.Failure.class,()->control.affected(0,0)));
                            assertThrows(IllegalStateException.class,control::retainedEntries);
                        }
                    }
                    try{admission.snapshot();assertEquals(before,pages.statistics().livePages());}catch(IllegalStateException expired){assertEquals(0,pages.statistics().livePages());}
                    assertFalse(port.closed);
                }
                assertEquals(0,pages.statistics().livePages());long borrowed=pages.allocate();pages.release(borrowed);
            }zero(resources);
        }
    }
    @Test void invalidOrdinalsAbortOnlyTheConsumerNotItsCertificate()throws Exception {
        var resources=resources();
        try(var pages=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.INDEX);
            var admission=admit(literal(),pages,resources)) {
            long before=pages.statistics().livePages();
            for(int mode=0;mode<3;mode++) {
                try(var control=SourceControlSnapshot.prepare(admission,pages,resources)) {
                    if(mode==0)assertThrows(IndexOutOfBoundsException.class,()->control.affected(-1,0));
                    if(mode==1)assertThrows(IndexOutOfBoundsException.class,()->control.assumedOnly(0,-1));
                    if(mode==2)assertThrows(IndexOutOfBoundsException.class,()->control.hypothetical(0,Long.MAX_VALUE));
                    assertThrows(IllegalStateException.class,control::retainedEntries);assertTrue(admission.unitCount()>0);assertEquals(before,pages.statistics().livePages());
                }
            }
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
