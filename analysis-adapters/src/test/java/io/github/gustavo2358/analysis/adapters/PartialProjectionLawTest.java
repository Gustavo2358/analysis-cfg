package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.cfg.application.*;
import io.github.gustavo2358.analysis.cfg.domain.*;
import io.github.gustavo2358.analysis.cfg.extension.SemanticInterpreterRegistry;
import io.github.gustavo2358.analysis.structure.*;
import io.github.gustavo2358.analysis.query.*;
import io.github.gustavo2358.analysis.storage.*;
import io.github.gustavo2358.analysis.rd.*;
import io.github.gustavo2358.analysis.values.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PartialProjectionLawTest {
    @Test void unknownControlUsesSameSnapshotSolverAndBeforeEvidenceWithoutEntryBackedge() {
        var p=EvidenceMonotonicityTest.baseline();var u=p.units().getFirst();var invoke=(Operations.Invoke)u.sequences().getFirst().terminator();
        p=EvidenceMonotonicityTest.changed(p,W1dEffectsTest.replace(invoke,invoke.effectBound(),new Control.InvocationOutcomes(List.of(invoke.outcomes().known().getFirst(),Control.Diverge.INSTANCE),Scopes.NoControl.INSTANCE)));
        var defaults=BuildOptions.defaults();var options=new BuildOptions(defaults.validation(),ProjectionPolicy.PARTIAL_ANALYSIS);
        var cfg=new CfgBuildCoordinator(SemanticInterpreterRegistry.empty()).build(p,options);
        assertEquals(CfgBuildResult.Status.CFG_BUILT,cfg.status());assertSame(p,cfg.graph().orElseThrow().publication());
        var session=AnalysisSession.open(cfg,p,options.projectionPolicy(),u.entries()).session().orElseThrow();var context=session.contexts().iterator().next();
        assertFalse(context.predecessors(context.entryNode()).advance(),"unknown control never re-enters an invocation boundary");
        assertTrue(session.index().partialControl(u.id()));
        var cursor=context.successors(session.index().sequence(u.sequences().getFirst().label()));var kinds=new HashSet<CfgTransition.Kind>();
        while(cursor.advance()){kinds.add(cursor.transition().kind());assertNotEquals(context.entryNode(),cursor.target());}
        assertTrue(kinds.contains(CfgTransition.Kind.INVOKE_NORMAL));assertTrue(kinds.contains(CfgTransition.Kind.OPAQUE_UNKNOWN));
        var query=new PointQuery<>(ProgramPoint.before(context.entry().id(),invoke.header().id()),new ObjectId(u.id(),"object-0"));
        var rd=new ReachingDefinitions(new StatementEffects(new StorageIndex(session))).execute().observe(List.of(query)).observations().getFirst().value();
        assertTrue(rd.sourceUnknownRemainder());assertTrue(rd.definitions().stream().anyMatch(d->d.definition().operation().map(id->id.localId().equals("source-support")).orElse(false)));
        var run=RegionalValuesAnalysis.prepare(session).analysis().orElseThrow().execute();var before=run.observe(List.of(query)).observations().getFirst().value();
        assertEquals(List.of(new Values.TextValue("PROGA")),before.candidates());assertTrue(before.sourceUnknownRemainder());
        assertEquals(before,run.observe(List.of(query)).observations().getFirst().value());
        assertEquals(CfgBuildResult.Status.UNSUPPORTED_INPUT,new CfgBuildCoordinator(SemanticInterpreterRegistry.empty()).build(p,defaults).status(),"legacy admission is unchanged");
    }
}
