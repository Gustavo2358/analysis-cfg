package io.github.gustavo2358.analysis.values;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Linear depth, not combinatorial width. Also runnable one operation per JVM. */
class FactorizedDepthTest {
    @Test void deepUpdatePreservesEveryUnchangedComponent() {assertDoesNotThrow(()->probe("update",8192));}
    @Test void deepRestrictionPreservesTheSingleTuple() {assertDoesNotThrow(()->probe("restrict",8192));}
    @Test void deepUnionKeepsBothSingletonsAndCanonicality() {assertDoesNotThrow(()->probe("union",8192));}
    @Test void deepProjectionKeepsOnlyTheSelectedComponent() {assertDoesNotThrow(()->probe("project",8192));}
    @Test void deepEnumerationReturnsTheOneCompleteTuple() {assertDoesNotThrow(()->probe("selections",8192));}

    public static void main(String[] args) {probe(args[0],Integer.parseInt(args[1]));}
    static void require(boolean value,String reason) {if(!value)throw new AssertionError(reason);}
    static void probe(String operation,int depth) {
        var domain=new FactorizedAlternatives<Integer>();var initial=new TreeMap<Integer,Integer>();
        for(int i=0;i<depth;i++)initial.put(i,0);
        var state=domain.singleton(initial);
        var changed=new TreeMap<>(initial);changed.put(depth-1,1);
        var other=domain.singleton(changed);FactorizedAlternatives.Node<Integer> result;
        switch(operation) {
            case "update" -> {
                result=domain.update(state,Map.of(depth-1,old->1));
                require(result==other,"local image must be canonical changed singleton");
            }
            case "restrict" -> {
                result=domain.restrict(state,Map.of(depth-1,0));
                require(result==state,"matching restriction preserves canonical singleton");
                require(domain.restrict(state,Map.of(depth-1,9))==null,"mismatch must be empty");
            }
            case "union" -> {
                result=domain.union(state,other);
                require(result==domain.union(other,state),"union commutativity/canonicality");
                require(result==domain.union(result,state),"union absorption");
                // Inspect iteratively so a union RED cannot be attributed to selections.
                var cursor=result;int seen=0;
                while(!cursor.terminal()) {
                    require(cursor.level==seen,"ordered level");
                    require(cursor.edges.keySet().equals(seen==depth-1?Set.of(0,1):Set.of(0)),"union labels");
                    cursor=cursor.edges.get(0);seen++;
                }
                require(seen==depth,"union lost a component");
            }
            case "project" -> {
                result=domain.project(state,Set.of(depth-1));
                require(result==domain.singleton(new TreeMap<>(Map.of(depth-1,0))),"projected singleton");
                require(domain.project(state,Set.of())==domain.terminal,"empty projection is the empty tuple");
            }
            case "selections" -> {
                result=state;require(domain.selections(state).equals(List.of(initial)),"exactly one complete tuple");
            }
            default -> throw new IllegalArgumentException(operation);
        }
        var size=FactorizedAlternatives.size(List.of(result));
        require(size.nodes()==(operation.equals("project")?1:depth),"linear node count");
        require(size.alternatives()==(operation.equals("project")?1:depth+(operation.equals("union")?1:0)),"linear edge count");
        System.out.println("EP_R2_DEPTH operation="+operation+" depth="+depth+" nodes="+size.nodes()+" alternatives="+size.alternatives()+" PASS");
    }
}
