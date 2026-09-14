package io.github.gustavo2358.analysis.storage;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.query.*;
import io.github.gustavo2358.analysis.rd.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static io.github.gustavo2358.analysis.storage.StorageFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

/** Test-only per-octet semantics. Neither oracle uses production partition/transfer/join/worklist. */
class RegionalDefinitionOracleTest {
    record Block(String name,int start,int length,String event,List<String> successors) { }
    record Concrete(String block,List<String> writers) { }
    static List<String> unknown(){return List.of("ENTRY","ENTRY","ENTRY","ENTRY","ENTRY","ENTRY","ENTRY","ENTRY");}
    static List<Set<String>> empty(){var result=new ArrayList<Set<String>>();for(int i=0;i<8;i++)result.add(new TreeSet<>());return result;}
    static List<Set<String>> initial(){var result=empty();result.forEach(s->s.add("ENTRY"));return result;}
    static List<Set<String>> cloneFacts(List<Set<String>> facts){var result=empty();for(int i=0;i<8;i++)result.get(i).addAll(facts.get(i));return result;}
    static boolean union(List<Set<String>> into,List<Set<String>> from){boolean changed=false;for(int i=0;i<8;i++)changed|=into.get(i).addAll(from.get(i));return changed;}
    static List<Set<String>> writeAbstract(Block block,List<Set<String>> before) {
        var after=cloneFacts(before);for(int i=block.start;i<block.start+block.length;i++){after.get(i).clear();after.get(i).add(block.event);}return after;
    }
    static Map<String,List<Set<String>>> recomposition(List<Block> blocks) {
        var out=new HashMap<String,List<Set<String>>>();boolean changed;
        do {
            changed=false;
            for(var block:blocks) {
                var input=empty();boolean reached=block.name.equals("n0");if(reached)union(input,initial());
                for(var predecessor:blocks)if(predecessor.successors.contains(block.name)&&out.containsKey(predecessor.name)) {reached=true;union(input,out.get(predecessor.name));}
                if(!reached)continue;var next=writeAbstract(block,input);if(!next.equals(out.get(block.name))){out.put(block.name,next);changed=true;}
            }
        } while(changed); // Finite node × octet × program-definition powerset.
        return out;
    }
    static Map<String,List<Set<String>>> concrete(List<Block> blocks,long[] visitedCount) {
        var index=new HashMap<String,Block>();blocks.forEach(b->index.put(b.name,b));
        var pending=new ArrayDeque<Concrete>();pending.add(new Concrete("n0",unknown()));
        var seen=new HashSet<Concrete>();var out=new HashMap<String,List<Set<String>>>();
        while(!pending.isEmpty()) {
            var state=pending.remove();if(!seen.add(state))continue;visitedCount[0]++;
            var block=index.get(state.block);var after=new ArrayList<>(state.writers);
            // A concrete byte has exactly one current writer. Branch enumerates both unknown booleans.
            for(int i=block.start;i<block.start+block.length;i++)after.set(i,block.event);
            var observed=out.computeIfAbsent(block.name,ignored->empty());for(int i=0;i<8;i++)observed.get(i).add(after.get(i));
            for(var successor:block.successors)pending.add(new Concrete(successor,List.copyOf(after)));
        }
        return out;
    }
    static Publication air(List<Block> blocks) {
        var sequences=new ArrayList<Sequence>();
        for(var block:blocks) {
            List<Instruction> instructions=block.length==0?List.of():List.of(assign(block.event,block.start==0?"left":"right",65,66,67,68));
            Terminator terminator;
            if(block.successors.isEmpty())terminator=new Operations.Return(header("end-"+block.name),List.of());
            else if(block.successors.size()==1)terminator=new Operations.Jump(header("end-"+block.name),new LabelId(U,block.successors.getFirst()));
            else {
                var predicate=new Expressions.Unknown(operand("end-"+block.name,"condition",Operand.Role.PREDICATE),Types.known(Types.Builtin.BOOL),List.of(),Scopes.NoMemory.INSTANCE,UNKNOWN);
                terminator=new Operations.Branch(header("end-"+block.name),predicate,new LabelId(U,block.successors.get(0)),new LabelId(U,block.successors.get(1)));
            }
            sequences.add(new Sequence(new LabelId(U,block.name),instructions,terminator,O));
        }
        return publication(List.of(region("r",8L,Memory.Lifetime.ACTIVATION)),List.of(view("all","r",0,8),view("left","r",0,4),view("right","r",4,4)),sequences,List.of());
    }
    static List<Set<String>> expand(DefinitionFact fact) {
        var result=empty();for(var contribution:fact.definitions())for(var location:contribution.contributedRanges()) {
            var range=location.location().range().orElseThrow();String event=contribution.definition().operation().map(OperationId::localId).orElse("ENTRY");
            for(int i=range.start().intValueExact();i<range.end().orElseThrow().intValueExact();i++)result.get(i).add(event);
        }
        return result;
    }
    @Test void generatedBranchesLoopsAndOrphansMatchConcreteAndRecomposition() {
        long[] visited={0};int observations=0;
        for(int seed=0;seed<48;seed++) {
            var random=new Random(73001+seed);var blocks=new ArrayList<Block>();
            for(int i=0;i<4;i++) {
                int start=random.nextBoolean()?0:4;int length=random.nextInt(3)==0?0:4;
                blocks.add(new Block("n"+i,start,length,"d"+i,List.of("n"+random.nextInt(5),"n"+random.nextInt(5))));
            }
            blocks.add(new Block("n4",0,0,"unused",List.of()));
            var expected=recomposition(blocks);var executionOracle=concrete(blocks,visited);assertEquals(expected,executionOracle,"two independent oracles seed="+seed);
            var p=air(blocks);var run=new ReachingDefinitions(new StatementEffects(new StorageIndex(session(p)))).execute();
            var queries=blocks.stream().map(b->new PointQuery<>(ProgramPoint.before(new EntryId(U,"main"),new OperationId(U,"end-"+b.name)),object("all"))).toList();
            var batch=run.observe(queries);assertEquals(ObservationBatch.Status.COMPLETE,batch.status());
            for(var observation:batch.observations()) {
                observations++;assertEquals(ObservationBatch.QueryStatus.VALUE,observation.status());
                String block=observation.query().point().operation().localId().substring(4);
                var wanted=expected.get(block);var actual=observation.value();
                if(wanted==null){assertEquals(DefinitionFact.Reachability.UNREACHABLE_IN_MODEL,actual.reachability());assertNull(actual.unknownRemainder());assertTrue(actual.definitions().isEmpty());}
                else {
                    assertEquals(wanted,expand(actual),"program="+seed+" block="+block);
                    assertEquals(wanted.stream().anyMatch(s->s.contains("ENTRY")),actual.unknownRemainder());
                }
            }
            assertEquals(0,run.dataflow().metrics().predecessorContributionReads());
        }
        assertEquals(240,observations);assertTrue(visited[0]>48);
        System.out.println("ST_RD_ORACLE graphs=48 observations="+observations+" concreteStates="+visited[0]+" independentOracles=2");
    }
    @Test void independentOracleRejectsWholeKillAndMayAsMustMutants() {
        var full=initial();for(int i=0;i<8;i++){full.get(i).clear();full.get(i).add("d1");}
        var correct=writeAbstract(new Block("n",0,4,"d2",List.of()),full);
        var wholeKill=empty();wholeKill.forEach(s->s.add("d2"));assertNotEquals(correct,wholeKill);
        var may=cloneFacts(full);for(int i=0;i<4;i++)may.get(i).add("d2");assertNotEquals(may,correct);
    }
}
