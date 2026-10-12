package io.github.gustavo2358.analysis.structure;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.analysis.cfg.domain.*;
import io.github.gustavo2358.analysis.solver.*;
import java.math.BigInteger;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.structure.LocalControlTest.*;

class ActivationOracleTest {
    static AnalysisSession generated(int seed) {
        var random=new Random(seed);var list=new ArrayList<Sequence>();int count=12;
        for(int i=0;i<count;i++) {
            String name="n"+i,a="n"+random.nextInt(count),b="n"+random.nextInt(count);
            list.add(switch(i) {
                case 0,1,2,3 -> seq(name,new Operations.LocalInvoke(h(name),label(a),List.of(i%2==0?A:B),label(b),fallback(),
                    Optional.of(new Operations.ReentryGuard("guard-"+random.nextInt(3),label("n"+random.nextInt(count)))),
                    List.of(new Operations.ResumeRoute("route",label("n"+random.nextInt(count))))));
                case 4,5 -> StructuralFixtures.branch(U,name,a,b);
                case 6 -> seq(name,new Operations.LocalResume(h(name),fallback(),random.nextBoolean()?Optional.empty():Optional.of("route")));
                case 7 -> boundary(name,random.nextBoolean()?A:B,a);
                case 8 -> unwind(name,BigInteger.valueOf(random.nextInt(4)),a);
                case 9 -> seq(name,new Operations.LocalUnwind(h(name),BigInteger.ZERO,label(a),fallback(),true));
                case 10 -> jump(name,a);
                default -> ret(name);
            });
        }
        return session(list,"n0","n1");
    }
    static AnalysisDefinition<Integer> definition(AnalysisSession session,int seed,Direction direction) {
        return new AnalysisDefinition<>() {
            public Direction direction(){return direction;}
            public Integer bottom(){return 0;}
            public Iterable<Boundary<Integer>> boundaries(AnalysisSession ignored){return session.contexts().stream().map(c->new Boundary<>(c,c.entryNode(),c.entry().id().localId().equals("n0")?1:2)).toList();}
            public Join<Integer> joinInto(Integer a,Integer b,DomainWork work){return new Join<>(a|b,(a|b)!=a);}
            public boolean equivalent(Integer a,Integer b,DomainWork work){return a.equals(b);}
            public Integer transferBlock(AnalysisPoint point,Integer state,DomainWork work) {
                if(!(point.node().source() instanceof CfgNode.SequenceNode node))return state;
                int n=Integer.parseInt(node.label().localId().substring(1));
                int keep=15^((seed+n)%3==0?1:0);int gen=(seed+n)%4==0?2:0;
                int result=(state&keep)|gen;
                // Monotone, finite and deliberately non-distributive.
                if((result&3)==3)result|=4;
                return result;
            }
            public Integer transferEdge(AnalysisPoint point,CfgTransition edge,Integer state,DomainWork work){return (state&4)!=0&&edge.kind()==CfgTransition.Kind.LOCAL?state|8:state;}
        };
    }
    @Test void generatedGuardsPortsRoutesAndUnwindsMatchExplicitStacks() {checkGenerated(120);}
    static void checkGenerated(int count) {
        for(var direction:Direction.values())for(int seed=1;seed<=count;seed++) {
            var session=generated(seed);var definition=definition(session,seed,direction);
            var expected=ExplicitActivationOracle.solve(session,definition);var actual=DataflowSolver.solve(session,definition);
            for(var context:session.contexts()) {
                var nodes=new HashSet<ProgramIndex.Node>();for(var state:expected)if(state.context()==context)nodes.add(state.node());
                for(var node:nodes)for(boolean atIn:new boolean[]{true,false}) {
                    var old=expected.stream().filter(s->s.context()==context&&s.node()==node).map(s->atIn?s.in():s.out()).toList();
                    assertEquals(new HashSet<>(old),new HashSet<>(actual.states(context,node,atIn)),"direction="+direction+" seed="+seed+" node="+node.source().id()+" in="+atIn);
                }
                for(var sequence:session.index().unit(context.entry().id().unit()).sequences()) {
                    var node=session.index().sequence(sequence.label());assertEquals(nodes.contains(node),actual.contains(context,node),"structural reachability seed="+seed);
                }
            }
        }
    }
    public static void main(String[] args){int count=args.length==0?120:Integer.parseInt(args[0]);checkGenerated(count);System.out.println("ACTIVATION_ORACLE=PASS "+(count*2)+" analyses, two entries, forward and backward, exact IN/OUT roots");}
}
