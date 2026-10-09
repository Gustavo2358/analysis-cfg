package io.github.gustavo2358.analysis.cfg.domain;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.cfg.application.*;
import io.github.gustavo2358.analysis.cfg.extension.SemanticInterpreterRegistry;
import io.github.gustavo2358.air.validation.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static io.github.gustavo2358.analysis.cfg.testing.CfgFirstPublications.*;
import static org.junit.jupiter.api.Assertions.*;

class LocalControlProjectionTest {
    @Test void ordinaryControlDoesNotAllocateASecondProgramSizedLabelIndex() {
        var bean=java.lang.management.ManagementFactory.getThreadMXBean();
        assertInstanceOf(com.sun.management.ThreadMXBean.class,bean);
        var allocations=(com.sun.management.ThreadMXBean)bean;
        assertTrue(allocations.isThreadAllocatedMemorySupported());
        if(!allocations.isThreadAllocatedMemoryEnabled())allocations.setThreadAllocatedMemoryEnabled(true);
        long thread=Thread.currentThread().threadId();
        for(int count:new int[]{16,256,4096,65536}) {
            var nodes=new ArrayList<CfgNode>();
            var returned=new CfgControl.Return(new OperationId(U,"return"));
            for(int i=0;i<count;i++)nodes.add(new CfgNode.SequenceNode(new CfgNodeId(P,i),new LabelId(U,"ordinary-"+i),List.of(),returned));
            for(int warmup=0;warmup<3;warmup++)assertEquals(Map.of(),LocalControlRules.project(nodes));
            long before=allocations.getThreadAllocatedBytes(thread);
            var rules=LocalControlRules.project(nodes);
            long allocated=allocations.getThreadAllocatedBytes(thread)-before;
            assertEquals(Map.of(),rules);
            assertTrue(allocated<=131072,"ordinary control allocated an unused label index: nodes="+count+" bytes="+allocated);
            System.out.println("CFG_LOCAL_RULES_NO_LOCAL_METRICS nodes="+count+" allocatedBytes="+allocated);
        }
    }

    @Test void sharedBodyIsProjectedOnceWithoutUnconditionalReturns() {
        var body = new LabelId(U,"body"); var end = new LabelId(U,"end");
        var fallback = new Envelopes.Envelope(
            new Envelopes.MemoryEnvelope(List.of(),Scopes.NoMemory.INSTANCE,List.of(),Scopes.NoMemory.INSTANCE,List.of()),
            new Control.ControlEnvelope(List.of(),new Scopes.WithinControl(new Scopes.UnitControl(U,true,true,true,true,true,true))),
            new Envelopes.DependencyEnvelope(List.of(),Scopes.NoResources.INSTANCE));
        var seqs = List.of(new Sequence(L,List.of(),new Operations.LocalInvoke(header(new OperationId(U,"call")),body,List.of(),end,fallback),ORIGIN),
            new Sequence(body,List.of(),new Operations.LocalResume(header(new OperationId(U,"resume")),fallback),ORIGIN),returning(end));
        var old=publication(List.of(unit(U,List.of(entry(E,L)),seqs)));
        var p=new Publication(old.id(),old.airVersion(),new Capabilities.Manifest(List.of(Capabilities.LOCAL_CONTROL),List.of()),old.artifacts(),old.units(),old.storage(),old.resources(),old.artifactRelations(),old.origins(),old.coverage(),old.uncertainties(),old.premises());
        assertEquals(ValidationResult.Status.STRUCTURALLY_VALID,AirValidator.validate(p).status(),()->AirValidator.validate(p).toString());
        var result = new CfgBuildCoordinator(SemanticInterpreterRegistry.empty()).build(p,BuildOptions.defaults());
        assertEquals(CfgBuildResult.Status.CFG_BUILT,result.status());
        var graph=result.graph().orElseThrow();
        assertEquals(3,graph.nodes().stream().filter(CfgNode.SequenceNode.class::isInstance).count());
        assertEquals(2,graph.transitions().size(),"only ENTRY and final RETURN are ordinary edges");
    }
}
