package io.github.gustavo2358.analysis.values;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.analysis.query.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.values.ValuesFixtures.*;
import static io.github.gustavo2358.analysis.values.ValuesTest.*;

/** Independent symbolic recomposition and finite concrete machine, neither calls product transfer/join. */
class ValueOracleTest {
    private record Abstract(Set<String> values,boolean unknown,boolean reached) { }
    private static Abstract[] recompute(String[] writes,int[][] targets) {
        int n=writes.length;var result=new Abstract[n];Arrays.fill(result,new Abstract(Set.of(),false,false));
        boolean changed;
        do {
            changed=false;
            for(int node=0;node<n;node++) {
                boolean reached=node==0,unknown=node==0;var values=new TreeSet<String>();
                for(int pred=0;pred<n;pred++)for(int edge:targets[pred])if(edge==node&&result[pred].reached()) {
                    reached=true;unknown|=result[pred].unknown();values.addAll(result[pred].values());
                }
                if(reached&&writes[node]!=null){values.clear();values.add(writes[node]);unknown=false;}
                var next=new Abstract(Set.copyOf(values),unknown,reached);
                if(!next.equals(result[node])){result[node]=next;changed=true;}
            }
        }while(changed);
        return result;
    }
    private record Machine(int node,String value) { }
    private static List<Set<String>> concrete(String[] writes,int[][] targets) {
        var observed=new ArrayList<Set<String>>();for(int i=0;i<writes.length;i++)observed.add(new TreeSet<>());
        var pending=new ArrayDeque<Machine>();var seen=new HashSet<Machine>();
        for(String initial:List.of("A","B","UNMODELED_INITIAL"))pending.add(new Machine(0,initial));
        while(!pending.isEmpty()) {
            var machine=pending.removeFirst();if(!seen.add(machine))continue;
            String value=writes[machine.node()]==null?machine.value():writes[machine.node()];
            observed.get(machine.node()).add(value);
            for(int destination:targets[machine.node()])pending.add(new Machine(destination,value));
        }
        return observed;
    }
    @Test void generatedRealAirMatchesIndependentRecompositionAndConcreteOracle() {
        int graphs=48,points=0;
        for(int seed=27001;seed<27001+graphs;seed++) {
            var random=new Random(seed);int n=2+random.nextInt(6);var writes=new String[n];var targets=new int[n][];
            for(int i=0;i<n;i++) {
                int choice=random.nextInt(3);writes[i]=choice==0?null:choice==1?"A":"B";
                int arity=random.nextInt(3);targets[i]=new int[arity];for(int j=0;j<arity;j++)targets[i][j]=random.nextInt(n);
            }
            var p=graph(writes,targets,1,false,false);var run=execute(p);var slow=recompute(writes,targets);var concrete=concrete(writes,targets);
            for(int node=0;node<n;node++) {
                points++;var observed=fact(run,before(p,node,0));
                if(!slow[node].reached()) {assertEquals(ValueFact.Reachability.UNREACHABLE_IN_MODEL,observed.reachability());assertTrue(concrete.get(node).isEmpty());continue;}
                assertEquals(slow[node].values(),new HashSet<>(observed.candidates().stream().map(Values.TextValue::value).toList()),"independent recomposition candidates");
                assertEquals(slow[node].unknown(),observed.modelValueRemainder(),"independent unknown path");
                var candidates=new HashSet<>(observed.candidates().stream().map(Values.TextValue::value).toList());
                for(String value:concrete.get(node))assertTrue(observed.modelValueRemainder()||candidates.contains(value),"finite concrete inclusion");
                if(!slow[node].unknown())assertEquals(concrete.get(node),candidates,"minimum exact precision");
            }
        }
        System.out.println("W3_CORPUS {\"graphs\":"+graphs+",\"points\":"+points+",\"seedBase\":27001,\"independentOracles\":2}");
    }
    @Test void concreteAndPrecisionOraclesRejectSharedWrongOrAlwaysUnknownAnswers() {
        String[] writes={"A"};int[][] targets={{}};
        var behaviors=concrete(writes,targets).getFirst();assertEquals(Set.of("A"),behaviors);
        var wrong=recompute(new String[]{"B"},targets)[0];
        assertThrows(AssertionError.class,()->assertTrue(wrong.unknown()||wrong.values().containsAll(behaviors)),"shared wrong transfer rejected");
        var alwaysUnknown=new Abstract(Set.of(),true,true);
        assertTrue(alwaysUnknown.unknown()||alwaysUnknown.values().containsAll(behaviors));
        assertThrows(AssertionError.class,()->assertEquals(behaviors,alwaysUnknown.values()),"soundness alone does not prove precision");
    }
}
