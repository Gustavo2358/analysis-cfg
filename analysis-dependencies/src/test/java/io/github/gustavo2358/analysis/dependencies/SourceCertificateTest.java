package io.github.gustavo2358.analysis.dependencies;

import java.util.*;
import static io.github.gustavo2358.analysis.dependencies.source.QualifiedSourceDependencies.*;

/** Boolean Horn certificate oracle independent of the indexing/worklist implementation. */
public final class SourceCertificateTest {
    static void smallAndLargeOwnership() {
        for(int count:new int[]{1,2048,2049,6000}) {
            var expectedNodes=new ArrayList<Node>();var expectedSteps=new ArrayList<Derivation>();
            var builder=new io.github.gustavo2358.analysis.dependencies.source.SourceInventories.Builder();
            for(int i=0;i<count;i++) {
                var support=new Support("ENTRY_UNKNOWN",List.of(),List.of(),"NONE");
                var node=new Node((i%2==0?"Aa":"BB")+i,"context-"+(i%3),"statement-"+i,support);
                var step=new Derivation("d"+i,i==0?List.of():List.of(expectedNodes.get(i-1).id()),node.id(),i<2?List.of():List.of(expectedNodes.get(i-2).id()),i==0?"PRIMARY_ENTRY":"edge",List.of("Aa","BB"),List.of());
                expectedNodes.add(node);expectedSteps.add(step);builder.addNode(node);builder.addDerivation(step);
            }
            var snapshot=builder.build();
            if(!snapshot.nodes().equals(expectedNodes)||!snapshot.derivations().equals(expectedSteps))throw new AssertionError("complete row alternatives changed");
            boolean small=2L*count<=4096;
            if(small!=(snapshot.nodes().get(0)==snapshot.nodes().get(0))||small!=(snapshot.derivations().get(0)==snapshot.derivations().get(0)))throw new AssertionError("bounded immutable row representation not owned");
            if(count>1&&snapshot.nodes().get(0).support()!=snapshot.nodes().get(1).support())throw new AssertionError("large support payload dictionary lost sharing");
            if(count>1&&snapshot.derivations().get(0).proofs()!=snapshot.derivations().get(1).proofs())throw new AssertionError("proof payload dictionary lost sharing");
            expectedNodes.clear();expectedSteps.clear();
            if(snapshot.nodes().size()!=count||snapshot.derivations().size()!=count)throw new AssertionError("caller list aliases escaped");
            try {snapshot.nodes().clear();throw new AssertionError("mutable rows");}catch(UnsupportedOperationException expected){}
            try {builder.addNode(snapshot.nodes().get(0));throw new AssertionError("frozen builder reused");}catch(IllegalStateException expected){}
        }
    }
    @org.junit.jupiter.api.Test void boundedSnapshotOwnership(){smallAndLargeOwnership();}
    private static final UnitId UNIT=new UnitId("c",List.of(0),"P");
    private static final Support SUPPORT=new Support("ENTRY_UNKNOWN",List.of(),List.of(),"NONE");
    private static final Location LOCATION=new Location("fixture",1,0,1,1);
    private static final Proof PROOF=new Proof("p","LOCAL_GRAMMAR","test",new Provenance(LOCATION,LOCATION,List.of(),true),List.of());
    private static UnitEvidence certificate(List<Node> nodes,List<Derivation> steps){return new UnitEvidence(UNIT,true,List.of(),List.of(),List.of(),nodes,steps,List.of(),List.of(),List.of(),List.of(PROOF),List.of());}
    private static Node node(String id){return new Node(id,"ROOT","statement",SUPPORT);}
    private static Derivation step(String id,String source,String destination,String caller){return new Derivation(id,source==null?List.of():List.of(source),destination,caller==null?List.of():List.of(caller),source==null?"PRIMARY_ENTRY":"edge",List.of("p"),List.of());}
    private static void rejected(Runnable operation){try{operation.run();throw new AssertionError("invalid certificate admitted");}catch(IllegalArgumentException expected){}}
    @org.junit.jupiter.api.Test void groundingPreservesAndOrCyclesAndHashCollisions() {
        String[] args=new String[0];
        var originalNodes=new ArrayList<>(List.of(node("Aa"),node("BB"),new Node("caller","OTHER_CONTEXT","statement",SUPPORT)));
        var originalSteps=new ArrayList<>(List.of(step("r",null,"Aa",null),step("b","Aa","BB",null),step("j","BB","caller","Aa")));
        var builder=new io.github.gustavo2358.analysis.dependencies.source.SourceInventories.Builder();
        originalNodes.forEach(builder::addNode);originalSteps.forEach(builder::addDerivation);
        var owned=builder.build();
        if(!owned.nodes().equals(originalNodes)||!owned.derivations().equals(originalSteps))throw new AssertionError("lossless tuple expansion");
        originalNodes.clear();originalSteps.clear();
        if(owned.nodes().size()!=3||owned.derivations().size()!=3)throw new AssertionError("builder aliases escaped");
        try{owned.nodes().clear();throw new AssertionError("mutable owned nodes");}catch(UnsupportedOperationException expected){}
        try{builder.addNode(node("late"));throw new AssertionError("builder reused");}catch(IllegalStateException expected){}
        certificate(owned.nodes(),owned.derivations());
        // Aa and BB deliberately collide under String.hashCode.
        var nodes=List.of(node("Aa"),node("BB"),node("join"));
        certificate(nodes,List.of(step("root",null,"Aa",null),step("b","Aa","BB",null),step("j","Aa","join","BB"),step("cycle","join","Aa",null)));
        rejected(()->certificate(nodes,List.of(step("root",null,"Aa",null),step("j","Aa","join","BB"),step("cycle","join","BB",null))));
        certificate(List.of(node("Aa"),node("BB")),List.of(step("root",null,"Aa",null),step("same","Aa","BB","Aa")));
        rejected(()->certificate(List.of(node("Aa"),node("Aa")),List.of(step("root",null,"Aa",null))));
        rejected(()->certificate(List.of(node("Aa")),List.of(step("missing","BB","Aa",null))));
        var collisionNodes=new ArrayList<Node>();var collisionSteps=new ArrayList<Derivation>();String previous=null;
        for(int i=0;i<256;i++){var key=new StringBuilder();for(int bit=0;bit<8;bit++)key.append((i&(1<<bit))==0?"Aa":"BB");String id=key.toString();collisionNodes.add(node(id));collisionSteps.add(step("collision"+i,previous,id,null));previous=id;}
        certificate(collisionNodes,collisionSteps);
        int size=args.length==0?1000:Integer.parseInt(args[0]);
        var largeNodes=new ArrayList<Node>(size);var steps=new ArrayList<Derivation>(size);
        for(int i=0;i<size;i++){String id="n"+i;largeNodes.add(node(id));steps.add(step("d"+i,i==0?null:"n"+(i-1),id,null));}
        var result=certificate(largeNodes,steps);
        if(result.nodes().size()!=size||result.derivations().size()!=size)throw new AssertionError("certificate facts lost");
    }
}
