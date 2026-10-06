package io.github.gustavo2358.analysis.structure;

import io.github.gustavo2358.air.model.*;
import java.math.BigInteger;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.structure.LocalControlTest.*;
import static io.github.gustavo2358.analysis.structure.GuardedLocalControlTest.guarded;

class ActivationSupportTest {
    private static ActivationControl.Frame frame(AnalysisSession session,ActivationControl control,String name) {
        return control.frame(session.index().sequence(label(name)).source().id());
    }
    @Test void ancestorsExcludeSiblingAndOrphanFramesBeforeAnySymbolicExecution() {
        var session=session(List.of(guarded("main","inner","sibling","main","done"),
            guarded("inner","leaf","return-main","inner","done"),
            guarded("leaf","return-leaf","return-inner","leaf","done"),
            guarded("sibling","return-sibling","done","sibling","done"),
            guarded("orphan","return-orphan","done","orphan","done"),
            resume("return-main"),resume("return-inner"),resume("return-leaf"),resume("return-sibling"),resume("return-orphan"),ret("done")),"main");
        var control=new ActivationControl(session,session.contexts().iterator().next());var possible=control.possibleAncestors();
        var main=frame(session,control,"main");var inner=frame(session,control,"inner");var leaf=frame(session,control,"leaf");
        var ancestors=possible.get(leaf);assertEquals(3,ancestors.cardinality());
        for(var f:List.of(main,inner,leaf))assertTrue(ancestors.get(f.variable()));
        assertFalse(ancestors.get(frame(session,control,"sibling").variable()));
        assertFalse(ancestors.get(frame(session,control,"orphan").variable()));
        assertTrue(possible.get(null).isEmpty());
    }
    @Test void positiveUnwindIncludesCallsAtTheDestinationUnderRemainingFrames() {
        var session=session(List.of(guarded("main","inner","done","main","done"),
            guarded("inner","unwind","bad","inner","done"),unwind("unwind",BigInteger.ONE,"after-unwind"),
            guarded("after-unwind","finish-helper","finish-main","helper","bad"),
            resume("finish-helper"),resume("finish-main"),ret("done"),ret("bad")),"main");
        var control=new ActivationControl(session,session.contexts().iterator().next());var possible=control.possibleAncestors();
        var helper=frame(session,control,"after-unwind");
        assertTrue(possible.get(helper).get(frame(session,control,"main").variable()));
        assertTrue(possible.get(helper).get(helper.variable()));
    }
    @Test void completeUnwindRestartsAtRootWithoutKeepingAbandonedAncestors() {
        var all=seq("discard",new Operations.LocalUnwind(h("discard"),BigInteger.ZERO,label("restart"),fallback(),true));
        var session=session(List.of(guarded("main","inner","done","main","done"),
            guarded("inner","discard","bad","inner","done"),all,
            guarded("restart","finish-helper","done","helper","bad"),resume("finish-helper"),ret("done"),ret("bad")),"main");
        var control=new ActivationControl(session,session.contexts().iterator().next());var possible=control.possibleAncestors();
        var helper=frame(session,control,"restart");var keys=possible.get(helper);
        assertEquals(1,keys.cardinality());assertTrue(keys.get(helper.variable()));
        assertFalse(keys.get(frame(session,control,"main").variable()));
        assertFalse(keys.get(frame(session,control,"inner").variable()));
    }
}
