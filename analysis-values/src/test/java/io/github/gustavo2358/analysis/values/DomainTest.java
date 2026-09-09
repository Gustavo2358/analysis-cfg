package io.github.gustavo2358.analysis.values;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DomainTest {
    @Test void missingStrongUpdateAndJoinHaveIndependentExpected() {
        var w=new ValuesWork(); var u=PossibleValuesState.reached();
        assertTrue(u.value(0,w).open()); assertEquals(0,u.explicitBindings());
        assertFalse(PossibleValuesState.unreachable().isReached());
        var a=u.assign(0,Candidates.singleton(0,w),w);
        var b=a.assign(0,Candidates.singleton(1,w),w);
        assertArrayEquals(new int[]{1},b.value(0,w).ordinals());
        assertFalse(b.value(0,w).open());
        assertArrayEquals(new int[]{0},a.value(0,w).ordinals());
        var join=a.join(b,w); assertArrayEquals(new int[]{0,1},join.value(0,w).ordinals());
        assertFalse(join.value(0,w).open());
        var missing=a.join(u,w); assertTrue(missing.value(0,w).open());
        assertArrayEquals(new int[]{0},missing.value(0,w).ordinals());
        assertSame(a,PossibleValuesState.unreachable().join(a,w));
        assertSame(u,u.join(u,w));
    }
    @Test void finiteLatticeLawsAndAssignmentMonotonicity() {
        var w=new ValuesWork(); var states=new ArrayList<PossibleValuesState>();
        states.add(PossibleValuesState.unreachable());
        for(int x=0;x<7;x++) for(int y=0;y<7;y++) {
            var s=PossibleValuesState.reached();
            for(int k=0;k<2;k++) {
                int choice=k==0?x:y;
                if(choice!=0) {
                    int mask=(choice+1)/2;
                    var c=mask==1?Candidates.singleton(0,w):mask==2?Candidates.singleton(1,w):Candidates.singleton(0,w).join(Candidates.singleton(1,w),w);
                    if(choice%2==0)c=c.withOpen(w);
                    s=s.assign(k,c,w);
                }
            }
            states.add(s);
        }
        for(var a:states) for(var b:states) {
            assertTrue(a.join(a,w).equivalent(a,w),"idempotence");
            assertTrue(a.join(b,w).equivalent(b.join(a,w),w),"commutativity");
            assertTrue(leq(a,a.join(b,w),w),"upper bound");
            if(leq(a,b,w)) assertTrue(leq(a.assign(0,Candidates.singleton(1,w),w),b.assign(0,Candidates.singleton(1,w),w),w),"assignment monotonicity");
            for(var c:states) assertTrue(a.join(b,w).join(c,w).equivalent(a.join(b.join(c,w),w),w),"associativity");
        }
    }
    private static boolean leq(PossibleValuesState a,PossibleValuesState b,ValuesWork w) {
        if(!a.isReached()) return true; if(!b.isReached()) return false;
        for(int k=0;k<2;k++) {
            var x=a.value(k,w);var y=b.value(k,w);
            if(x.open()&&!y.open())return false;
            for(int n:x.ordinals()) if(Arrays.binarySearch(y.ordinals(),n)<0)return false;
        }
        return true;
    }
    @Test void persistentUpdatesShareAndRetainNoHistory() {
        var w=new ValuesWork();var s=PossibleValuesState.reached();
        for(int i=0;i<10000;i++)s=s.assign(i,Candidates.singleton(i,w),w);
        var before=s;long allocations=w.stateNodes;
        s=s.assign(5000,Candidates.singleton(10001,w),w);
        assertTrue(w.stateNodes-allocations<64,"bounded update must not clone state");
        assertEquals(10000,s.explicitBindings());
        assertArrayEquals(new int[]{5000},before.value(5000,w).ordinals());
        assertSame(before.value(5001,w),s.value(5001,w));
        assertTrue(RetentionAudit.shared(before.root,s.root)>9900,"unchanged subtrees shared");
        assertEquals(10000,RetentionAudit.nodeCount(s.root));
        assertTrue(RetentionAudit.valid(s.root));
    }
    @Test void allFiniteCandidatesSurviveAndUnionConverges() {
        for(int n:new int[]{8,9,100,1000,2000,4000,10000}) {
            var w=new ValuesWork();var c=Candidates.singleton(0,w);
            for(int i=1;i<n;i++)c=c.join(Candidates.singleton(i,w),w);
            assertEquals(n,c.size(),"all finite candidates preserved");assertFalse(c.open());
            assertSame(c,c.join(c,w),"finite fixed point");
            for(int i=0;i<n;i++)assertEquals(i,c.ordinals()[i]);
        }
    }
}
