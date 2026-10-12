package io.github.gustavo2358.analysis.values;

import io.github.gustavo2358.analysis.solver.DomainWork;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.values.ValuesFixtures.*;
import static io.github.gustavo2358.analysis.values.RegionalValuesTest.*;

class StateFingerprintTest {
    @Test void scalarKeysAreCongruentAcrossMapShapesAndIncludeSupportsAndUnknownness() {
        var random=new Random(17841);var fingerprints=new HashSet<Long>();
        for(int round=0;round<100;round++) {
            var work=new ValuesWork();var ordered=PossibleValuesState.reached();var keys=new ArrayList<Integer>();
            var candidates=new ArrayList<Candidates>();
            for(int i=0;i<32;i++) {
                keys.add(i);var value=Candidates.singleton(random.nextInt(1000),work).supportedBy(round*32+i,work);
                if(random.nextBoolean())value=value.withOpen(work);candidates.add(value);ordered=ordered.initialize(i,value,work);
            }
            Collections.shuffle(keys,random);var permuted=PossibleValuesState.reached();
            for(int key:keys)permuted=permuted.initialize(key,candidates.get(key),work);
            assertTrue(ordered.equivalent(permuted,work));assertEquals(ordered.fingerprint(),permuted.fingerprint());
            fingerprints.add(ordered.fingerprint());
            var otherSupport=candidates.getFirst().supportedBy(999999,work);
            var changed=ordered.initialize(0,otherSupport,work);
            assertFalse(ordered.equivalent(changed,work));assertNotEquals(ordered.fingerprint(),changed.fingerprint());
        }
        assertEquals(100,fingerprints.size());assertNotEquals(PossibleValuesState.reached().fingerprint(),PossibleValuesState.unreachable().fingerprint());
    }
    @Test void segmentKeysIgnoreAvlShapeAndAgreeWithValueEquality() {
        var first=new io.github.gustavo2358.analysis.storage.SegmentMap<String>();
        var second=new io.github.gustavo2358.analysis.storage.SegmentMap<String>();
        var keys=new ArrayList<Integer>();for(int i=0;i<100;i++){keys.add(i);first=first.put(i,"value-"+i);}
        Collections.shuffle(keys,new Random(47281));for(int key:keys)second=second.put(key,"value-"+key);
        assertEquals(first.fingerprint(),second.fingerprint());
        for(int key:keys)assertEquals(first.get(key),second.get(key));
        assertNotEquals(first.fingerprint(),second.put(19,"changed").fingerprint());
        assertEquals(new io.github.gustavo2358.analysis.storage.SegmentMap<String>().put(1,"Aa").fingerprint(),new io.github.gustavo2358.analysis.storage.SegmentMap<String>().put(1,"BB").fingerprint(),"hash collisions are legal, not equivalence");
    }
    @Test void regionalFingerprintsAgreeWithRealEngineEquivalenceInRepeatedExecution() {
        var p=regional(List.of(returning(U,"s0",List.of(assign(U,"write",WHOLE,"ABCDEFGH"),assign(U,"overwrite",PREFIX,"WXYZ")))));
        var owner=session(p);var analysis=RegionalValuesAnalysis.prepare(owner,StorageAnalysisMode.EXPERIMENTAL_PHYSICAL).analysis().orElseThrow();
        var engine=analysis.new Engine();var first=io.github.gustavo2358.analysis.solver.DataflowSolver.solve(owner,engine);var second=io.github.gustavo2358.analysis.solver.DataflowSolver.solve(owner,engine);
        var context=owner.contexts().iterator().next();
        for(var node:List.of(context.entryNode(),owner.index().sequence(new io.github.gustavo2358.air.model.Ids.LabelId(U,"s0")),context.normalExit())) {
            var a=first.in(context,node);var b=second.in(context,node);
            assertNotNull(a);assertTrue(engine.equivalent(a,b,new DomainWork()));
            assertEquals(engine.stateFingerprint(a),engine.stateFingerprint(b));
        }
    }
}
