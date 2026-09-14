package io.github.gustavo2358.analysis.values;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.values.ValuesFixtures.*;
import static io.github.gustavo2358.analysis.values.RegionalValuesTest.*;
import static io.github.gustavo2358.analysis.values.RegionalCompositionTest.*;
import static io.github.gustavo2358.analysis.values.StorageValueObservationTest.query;

/** Collecting semantics over individual bytes and static producer coordinates.
 * It never uses ByteImage, intervals, joins, the solver or its transfer functions.
 * Visited concrete states terminate cycles; there is no iteration/candidate cutoff.
 */
class RegionalConcreteOracleTest {
    record Byte(char value,String producer,int base,int offset) {}
    record MemoryState(List<Byte> bytes) { MemoryState { bytes=List.copyOf(bytes); } }
    record Action(String id,int destination,int start,String literal,int source,int sourceStart,int length) {
        static Action write(String id,int base,int start,String text) {return new Action(id,base,start,text,-1,0,text.length());}
        static Action copy(String id,int from,int start,int to,int dest,int length) {return new Action(id,to,dest,null,from,start,length);}
        static Action unknown(String id,int base,int start,int length) {return new Action(id,base,start,null,-1,0,length);}
        MemoryState concrete(MemoryState before) {
            var out=new ArrayList<>(before.bytes());
            for(int i=0;i<length;i++) out.set(8*destination+start+i,literal!=null
                ?new Byte(literal.charAt(i),id,destination,start+i)
                :source>=0?before.bytes().get(8*source+sourceStart+i):new Byte('?',"",-1,-1));
            return new MemoryState(out);
        }
        Instruction air() {
            if(source>=0)return RegionalCompositionTest.copy(id,source==0?R:Y,sourceStart,destination==0?R:Y,start,length);
            if(literal!=null)return RegionalTransferTest.slice(id,destination==0?R:Y,start,literal);
            assertEquals(0,destination);assertEquals(0,start);assertEquals(4,length);
            return RegionalTransferTest.havoc(id,PREFIX);
        }
    }
    record Node(List<Action> actions,List<Integer> successors) {}
    record Visit(int node,MemoryState memory) {}
    static Node node(List<Integer> edges,Action... actions){return new Node(List.of(actions),edges);}
    static MemoryState unknown() {return new MemoryState(Collections.nCopies(16,new Byte('?',"",-1,-1)));}
    static List<Set<MemoryState>> collect(List<Node> graph) {
        var after=new ArrayList<Set<MemoryState>>();for(var ignored:graph)after.add(new HashSet<>());
        var work=new ArrayDeque<Visit>();var visited=new HashSet<Visit>();work.add(new Visit(0,unknown()));
        while(!work.isEmpty()) {
            var visit=work.removeFirst();if(!visited.add(visit))continue;
            var n=graph.get(visit.node());var state=visit.memory();for(var action:n.actions())state=action.concrete(state);
            after.get(visit.node()).add(state);for(int next:n.successors())work.addLast(new Visit(next,state));
        }
        return after;
    }
    static Publication publication(List<Node> graph) {
        var sequences=new ArrayList<Sequence>();
        for(int i=0;i<graph.size();i++) {
            var n=graph.get(i);var edges=n.successors();var control=edges.isEmpty()?returning(U,"s"+i,List.of()):edges.size()==1
                ?jump(U,"s"+i,"s"+edges.getFirst()):branch(U,"s"+i,"s"+edges.getFirst(),"s"+edges.getLast());
            sequences.add(new Sequence(control.label(),n.actions().stream().map(Action::air).toList(),control.terminator(),origin(P)));
        }
        return RegionalTransferTest.uncertainty(twoBases(sequences));
    }
    static int octet(char c) {
        if(c>='A'&&c<='I')return 0xc1+c-'A';if(c>='J'&&c<='R')return 0xd1+c-'J';
        if(c>='S'&&c<='Z')return 0xe2+c-'S';if(c>='0'&&c<='9')return 0xf0+c-'0';
        throw new AssertionError("oracle alphabet: "+c);
    }
    static String byteKey(Byte b) {return b.value()=='?'?"?":octet(b.value())+":"+b.producer()+":"+b.base()+":"+b.offset();}
    static void check(String label,List<Node> graph) {
        var expected=collect(graph);var p=publication(graph);var execution=run(p);
        for(int n=0;n<graph.size();n++)for(int base=0;base<2;base++) {
            var operation=p.units().getFirst().sequences().get(n).terminator().header().id().localId();
            var fact=execution.observeStorage(List.of(query(operation,base==0?R:Y,0,8))).observations().getFirst().value();
            String where=label+" node "+n+" base "+base;
            if(expected.get(n).isEmpty()) {assertEquals(ValueFact.Reachability.UNREACHABLE_IN_MODEL,fact.reachability(),where);assertNull(fact.candidates());continue;}
            var texts=new TreeSet<String>();var supports=new TreeMap<String,Set<String>>();var alternatives=new HashSet<List<String>>();boolean open=false;
            for(var state:expected.get(n)) {
                var bytes=state.bytes().subList(8*base,8*base+8);alternatives.add(bytes.stream().map(RegionalConcreteOracleTest::byteKey).toList());
                if(bytes.stream().anyMatch(b->b.value()=='?')) {open=true;continue;}
                var text=new StringBuilder();for(var b:bytes)text.append(b.value());texts.add(text.toString());
                var producers=supports.computeIfAbsent(text.toString(),k->new TreeSet<>());for(var b:bytes)producers.add(b.producer());
            }
            assertEquals(List.copyOf(texts),fact.candidates().stream().map(Values.TextValue::value).toList(),where);assertEquals(open,fact.modelValueRemainder(),where);
            var actualSupports=new TreeMap<String,Set<String>>();for(var s:fact.candidateSupports()) {
                var producers=new TreeSet<String>();for(var producer:s.producers())producers.add(producer.evidence().localId());actualSupports.put(s.candidate().value(),producers);
            }
            assertEquals(supports,actualSupports,where+" support union");
            var actualAlternatives=new HashSet<List<String>>();
            for(var alternative:fact.alternatives()) {
                var bytes=new ArrayList<>(Collections.nCopies(8,"?"));
                for(var fragment:alternative.fragments())if(fragment.bytes().isPresent()) {
                    var producer=fragment.producer().orElseThrow();var at=fragment.location().location().range().orElseThrow().start().intValueExact();
                    var from=producer.contributedRange().location();var offset=from.range().orElseThrow().start().intValueExact();int source=from.base().id().equals(R)?0:1;
                    var octets=fragment.bytes().orElseThrow().octets();
                    for(int i=0;i<octets.size();i++)bytes.set(at+i,octets.get(i)+":"+producer.definition().operation().orElseThrow().localId()+":"+source+":"+(offset+i));
                }
                actualAlternatives.add(List.copyOf(bytes));
            }
            assertEquals(alternatives,actualAlternatives,where+" surviving byte/producer coordinates");
        }
    }
    @Test void finiteBranchGraphsCompareEveryByteCandidateAndProducer() {
        var random=new Random(503041);for(int trial=0;trial<24;trial++) {
            var graph=new ArrayList<Node>();
            graph.add(node(List.of(1,2),Action.write("initial-x",0,0,"ABCDEFGH"),Action.write("initial-y",1,0,"12345678")));
            for(int n=1;n<7;n++) {
                int base=random.nextInt(2),start=4*random.nextInt(2);String id="write-"+n;
                var action=random.nextBoolean()?Action.write(id,base,start,random.nextBoolean()?"WXYZ":"ABCD")
                    :Action.copy(id,1-base,4*random.nextInt(2),base,start,4);
                graph.add(node(n==6?List.of():n==5?List.of(6):List.of(n+1,n+2),action));
            }
            check("branch-"+trial,graph);
        }
    }
    @Test void zeroOneAndManyIterationsIncludeOverlappingCopiesAndUnknownRepair() {
        for(int shift=0;shift<2;shift++)for(int mandatory=0;mandatory<2;mandatory++) {
            var action=Action.copy("shift",0,shift,0,1-shift,7);
            var graph=List.of(node(List.of(mandatory==0?1:2),Action.write("initial",0,0,"ABCDEFGH"),Action.write("y",1,0,"12345678")),
                node(List.of(2,4)),node(List.of(3),action),node(List.of(1),Action.copy("capture",0,0,1,0,8)),node(List.of()));
            check("overlap-"+shift+"-"+mandatory,graph);
        }
        check("unknown-repair",List.of(node(List.of(1),Action.write("initial",0,0,"ABCDEFGH")),node(List.of(2,4)),
            node(List.of(3),Action.unknown("erase",0,0,4)),node(List.of(1),Action.write("repair",0,0,"WXYZ")),node(List.of())));
    }
    @Test void supportOnlyLoopChangesReachTheExitAndTwoExplicitResumesStaySeparate() {
        check("supports",List.of(node(List.of(1),Action.write("first",0,0,"ABCDEFGH")),node(List.of(2,3)),
            node(List.of(1),Action.write("second",0,0,"ABCDEFGH")),node(List.of())));
        // AIR already has distinct, explicit continuation contexts here. This does
        // not assert support for LocalInvoke/LocalResume or flatten source returns.
        check("two-contexts",List.of(node(List.of(1),Action.write("first",0,0,"AAAABBBB")),
            node(List.of(2),Action.write("body-context-1",0,0,"WXYZ")),
            node(List.of(3),Action.copy("snapshot",0,0,1,0,8),Action.write("second",0,0,"CCCCDDDD")),
            node(List.of(4),Action.write("body-context-2",0,0,"WXYZ")),node(List.of())));
    }

    @Test void measuredFragmentCandidateAndReplayGrowthHasNoSemanticCutoff() {
        for(int fragments:List.of(1,2,4,8)) {
            var actions=new ArrayList<Action>();for(int i=0;i<fragments;i++)actions.add(Action.write("piece-"+i,0,8*i/fragments,"A".repeat(8/fragments)));
            var p=publication(List.of(new Node(actions,List.of())));var execution=run(p);var q=query("return-s0",R,0,8);
            var batch=execution.observeStorage(List.of(q,q,query("piece-0",R,0,8)));
            var fact=batch.observations().stream().filter(o->o.query().equals(q)).findFirst().orElseThrow().value();
            assertEquals(fragments,fact.alternatives().getFirst().fragments().size());assertEquals(1,fact.candidates().size());
            assertEquals(1,batch.metrics().sequencesReplayed());assertEquals(fragments,batch.metrics().operationsReplayed());
            assertEquals(2,batch.metrics().uniqueQueries());
            System.out.println("W5_COST fragments="+fragments+" prepare="+execution.preparationMetrics()+" solve="+execution.solveMetrics()+" replay="+batch.metrics());
        }
        for(int count:List.of(2,4,8,16)) {
            var graph=new ArrayList<Node>();
            for(int i=0;i<count-1;i++)graph.add(node(List.of(i+1,count-1+i)));
            for(int i=0;i<count;i++)graph.add(node(List.of(2*count-1),Action.write("producer-"+i,0,0,String.valueOf((char)('A'+i)).repeat(8))));
            // The last decision's first edge must reach the last leaf; all other
            // first edges advance to the next decision.
            graph.set(count-2,node(List.of(2*count-2,2*count-3)));graph.add(node(List.of()));
            var p=publication(graph);var execution=run(p);var q=query("return-s"+(2*count-1),R,0,8);
            var batch=execution.observeStorage(List.of(q));var fact=batch.observations().getFirst().value();
            assertEquals(count,fact.candidates().size());assertEquals(count,fact.alternatives().size());
            assertFalse(fact.modelValueRemainder());
            System.out.println("W5_COST candidates="+count+" alternatives="+fact.alternatives().size()+" solve="+execution.solveMetrics()+" solver="+execution.dataflow().metrics());
        }
        for(int count:List.of(1,4,16,64)) {
            var actions=new ArrayList<Action>();actions.add(Action.write("initial",0,0,"ABCDEFGH"));
            for(int i=0;i<count;i++)actions.add(Action.copy("capture-"+i,i%2,0,1-i%2,0,8));
            var execution=run(publication(List.of(new Node(actions,List.of()))));var batch=execution.observeStorage(List.of(query("return-s0",count%2==0?R:Y,0,8)));
            var fact=batch.observations().getFirst().value();assertEquals(count,fact.alternatives().getFirst().fragments().getFirst().captures().size());
            assertEquals(List.of(new Values.TextValue("ABCDEFGH")),fact.candidates());
            System.out.println("W5_COST captures="+count+" solve="+execution.solveMetrics()+" replay="+batch.metrics());
        }
    }
}
