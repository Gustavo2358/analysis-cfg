package io.github.gustavo2358.analysis.dependencies;

import java.util.*;
import static io.github.gustavo2358.analysis.dependencies.source.QualifiedSourceDependencies.*;

/** Boolean Horn certificate oracle independent of the indexing/worklist implementation. */
public final class SourceCertificateTest {
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
