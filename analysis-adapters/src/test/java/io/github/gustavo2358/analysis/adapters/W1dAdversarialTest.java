package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.application.*;
import io.github.gustavo2358.analysis.cfg.application.CfgBuildResult;
import io.github.gustavo2358.analysis.dependencies.*;
import io.github.gustavo2358.analysis.plan.*;
import io.github.gustavo2358.analysis.query.*;
import io.github.gustavo2358.analysis.values.*;
import java.util.*;
import java.io.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.adapters.W1dBoundaryTest.*;
import static io.github.gustavo2358.analysis.adapters.W1dEffectsTest.*;

final class W1dAdversarialTest {
    static Publication target(Publication p,Interactions.Target target) {
        var i=invoke(p);var next=new Operations.Invoke(i.header(),i.action(),target,i.arguments(),i.results(),i.signature(),i.effectOperands(),i.effectBound(),i.outcomes(),i.contract());
        return sequences(p,p.units().getFirst().sequences().stream().map(s->s.terminator()==i?new Sequence(s.label(),s.instructions(),next,s.origin()):s).toList(),p.units().getFirst().entries());
    }
    static Publication orphan(Publication p) {
        var u=p.units().getFirst();var e=u.entries().getFirst();var normal=((Control.Normal)invoke(p).outcomes().known().getFirst()).label();
        return sequences(p,u.sequences(),List.of(new Entries.Entry(e.id(),Optional.of(normal),e.signature(),e.state(),e.origin())));
    }
    static byte[] wire(DependencyResult result) throws IOException {var out=new ByteArrayOutputStream();new DependencyJson().write(result,out);return out.toByteArray();}
    @Test void orphanLiteralAndComputedEmitSiteWithoutExecutableEdge() throws Exception {
        for(String name:List.of("literal","dynamic-x8")) {
            var r=new DependencyAnalysis().prepare(orphan(input(name)));assertEquals(1,r.sites().size());var s=r.sites().getFirst();
            assertEquals(DependencySiteFact.Reachability.UNREACHABLE_IN_MODEL,s.reachability());assertEquals(DependencySiteFact.TargetStatus.UNREACHABLE_IN_MODEL,s.targetStatus());
            assertTrue(s.candidates().isEmpty());assertTrue(s.rawCandidates().isEmpty());assertTrue(r.edges().isEmpty());assertNull(s.modelValueRemainder());
        }
    }
    @Test void wrongNamespaceAndCategoryAreNotProgramDependencies() throws Exception {
        var p=input("literal");var t=(Interactions.LiteralTarget)invoke(p).target();
        for(var target:List.of(new Interactions.LiteralTarget("file",t.namespace(),t.name(),t.namePolicy(),t.origin()),new Interactions.LiteralTarget(t.category(),"other.program",t.name(),t.namePolicy(),t.origin()))) {
            var r=new DependencyAnalysis().prepare(target(p,target));assertTrue(r.sites().isEmpty());assertTrue(r.edges().isEmpty());assertEquals(0L,r.metrics().get("possibleValuesRuns"));
        }
    }
    @Test void noncanonicalLiteralPreservesRawAndOpensInterpretation() throws Exception {
        var p=input("literal");var t=(Interactions.LiteralTarget)invoke(p).target();
        var r=new DependencyAnalysis().prepare(target(p,new Interactions.LiteralTarget(t.category(),t.namespace()," ProGa ",t.namePolicy(),t.origin())));
        var s=r.sites().getFirst();assertEquals(" ProGa ",s.rawCandidates().getFirst().rawValue());assertTrue(s.candidates().isEmpty());assertTrue(s.interpretationUnknownRemainder());assertTrue(r.edges().isEmpty());
    }
    @Test void unsupportedComputedExpressionIsDiagnosticNotInventedLiteral() throws Exception {
        var p=input("dynamic-x8");var t=(Interactions.ComputedTarget)invoke(p).target();
        var read=(Expressions.Read)t.name();
        var inner=new Expressions.Read(new Operand.Header(new OperandId(new OperationOwner(invoke(p).header().id()),"inner-read"),Operand.Role.VALUE_READ,read.header().origin()),read.place());
        var expression=new Expressions.TrimRight(t.name().header(),inner," ");
        var r=new DependencyAnalysis().prepare(target(p,new Interactions.ComputedTarget(t.category(),t.namespace(),expression,t.namePolicy(),t.origin())));
        assertEquals(DependencySiteFact.TargetStatus.UNSUPPORTED_TARGET_EXPRESSION,r.sites().getFirst().targetStatus());assertTrue(r.edges().isEmpty());assertEquals(0L,r.metrics().get("possibleValuesRuns"));
    }
    @Test void finiteNonNormalOutcomesAreExplicitlyRefused() throws Exception {
        var p=input("literal");var i=invoke(p);var u=p.units().getFirst();
        for(var extra:List.<Control.InvocationAlternative>of(Control.Diverge.INSTANCE,Control.HaltAlternative.INSTANCE,new Control.AnyException(Control.Propagate.INSTANCE),new Control.Exceptional("error",new Control.Handler(((Control.Normal)i.outcomes().known().getFirst()).label())))) {
            var next=replace(i,i.effectBound(),new Control.InvocationOutcomes(List.of(i.outcomes().known().getFirst(),extra),i.outcomes().remainder()));
            var changed=sequences(p,u.sequences().stream().map(s->s.terminator()==i?new Sequence(s.label(),s.instructions(),next,s.origin()):s).toList(),u.entries());
            assertEquals(CfgBuildResult.Status.UNSUPPORTED_INPUT,build(changed).status());assertTrue(build(changed).graph().isEmpty());
        }
    }
    @Test void explicitContinuationSurvivesPhysicalPermutationAndDecoy() throws Exception {
        var p=input("dynamic-x8");var u=p.units().getFirst();var all=new ArrayList<>(u.sequences());Collections.reverse(all);
        assertArrayEquals(wire(new DependencyAnalysis().prepare(p)),wire(new DependencyAnalysis().prepare(sequences(p,all,u.entries()))));
        var i=invoke(p);var label=new LabelId(u.id(),"000-physical-decoy");
        var h=new Operations.Header(new OperationId(u.id(),"decoy-return"),i.header().origin(),i.header().coverage(),i.header().precision(),i.header().uncertainties());
        all.add(new Sequence(label,List.of(),new Operations.Return(h,List.of()),i.header().origin()));
        var changed=sequences(p,all,u.entries());var cfg=build(changed);assertEquals(CfgBuildResult.Status.CFG_BUILT,cfg.status());
        var index=session(changed).index();var call=index.sequence(index.site(i.header().id()).sequence().label());
        var graph=cfg.graph().orElseThrow();var node=graph.nodes().stream().filter(n->n instanceof io.github.gustavo2358.analysis.cfg.domain.CfgNode.SequenceNode s&&s.source().terminator()==i).findFirst().orElseThrow();
        var edge=graph.transitions().stream().filter(e->e.from().equals(node.id())).findFirst().orElseThrow();
        var dest=graph.nodes().stream().filter(n->n.id().equals(edge.to())).findFirst().orElseThrow();
        assertEquals(((Control.Normal)i.outcomes().known().getFirst()).label(),((io.github.gustavo2358.analysis.cfg.domain.CfgNode.SequenceNode)dest).source().label());
        assertNotNull(call);
    }
    @Test void sharedAndRepeatedQueriesExecuteOneValuesRun() throws Exception {
        var p=input("dynamic-x8");var s=session(p);
        var registrations=new ArrayList<>(CallDependencyPlan.select(s,"first",true));registrations.addAll(CallDependencyPlan.select(s,"second",false));
        try(var execution=new PlanningExecution(s,new AnalysisRegistry(List.of(new PossibleValuesProvider(),new ReachabilityProvider())))) {
            var result=execution.execute("sharing",execution.plan(registrations));assertEquals(PreparedAnalysisResult.PreparationStatus.COMPLETE,result.preparationStatus());
            assertEquals(1,result.analyses().stream().filter(a->a.key().implementation().equals("PossibleValues")).count());
            assertEquals(2L,result.metrics().get("analysis").get("analysisRuns")); // BFS plus one actual values execution.
            assertTrue(result.metrics().get("planning").get("queryRequests")>result.metrics().get("planning").get("uniqueQueries"));
            assertEquals(result.consumers().get(0).facts(),result.consumers().get(1).facts());
        }
    }
    @Test void literalPlannerHasZeroValuesDependenciesAndNoValuesProviderInstalled() throws Exception {
        var s=session(input("literal"));
        try(var execution=new PlanningExecution(s,new AnalysisRegistry(List.of(new ReachabilityProvider())))) {
            var result=execution.execute("no-values-provider",execution.plan(CallDependencyPlan.select(s)));
            assertEquals(PreparedAnalysisResult.PreparationStatus.COMPLETE,result.preparationStatus());assertEquals(1,result.analyses().size());
            assertEquals("Reachability",result.analyses().getFirst().key().implementation());assertEquals("PROGA",result.consumers().getFirst().facts().getFirst().candidates().getFirst().referenceName());
        }
    }
    @Test void strictWireInputsAreWrittenForIndependentParserAndOriginAudit() throws Exception {
        Path out=Path.of("target/w1d");Files.createDirectories(out);
        for(String name:List.of("dynamic-x8","literal","dynamic-no-move")) {
            var p=input(name);var r=new DependencyAnalysis().prepare(p);var bytes=wire(r);assertArrayEquals(bytes,wire(r));Files.write(out.resolve(name+".json"),bytes);
        }
        Files.write(out.resolve("orphan.json"),wire(new DependencyAnalysis().prepare(orphan(input("literal")))));
    }
}
