package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.json.AirJson;
import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.analysis.cfg.application.*;
import io.github.gustavo2358.analysis.cfg.extension.SemanticInterpreterRegistry;
import io.github.gustavo2358.analysis.structure.AnalysisSession;
import io.github.gustavo2358.analysis.storage.*;
import io.github.gustavo2358.analysis.rd.ReachingDefinitions;
import io.github.gustavo2358.analysis.query.*;
import io.github.gustavo2358.analysis.values.*;
import io.github.gustavo2358.analysis.dependencies.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Real SP/lower fixture: qualified target and COMMAREA overlap the same physical eight bytes. */
class CicsTargetTimingTest {
    @Test void nameAndSupportsAreObservedBeforeForeignEffects() throws Exception {
        Publication p;try(var input=getClass().getResourceAsStream("/cics/qualified.air.json")){assertNotNull(input);p=new AirJson().decode(input.readAllBytes());}
        var u=p.units().getFirst();var entry=u.entries().getFirst();var sequence=u.sequences().stream().filter(s->s.terminator() instanceof Operations.Invoke).findFirst().orElseThrow();
        var invoke=(Operations.Invoke)sequence.terminator();var read=(Expressions.Read)((Interactions.ComputedTarget)invoke.target()).name();
        var object=((Places.ObjectPlace)read.place()).object();var label=((Control.Normal)invoke.outcomes().known().getFirst()).label();
        var next=u.sequences().stream().filter(s->s.label().equals(label)).findFirst().orElseThrow().terminator();
        var options=BuildOptions.defaults();var cfg=new CfgBuildCoordinator(SemanticInterpreterRegistry.empty()).build(p,options);
        var session=AnalysisSession.open(cfg,p,options.projectionPolicy(),List.of(entry)).session().orElseThrow();
        var before=new PointQuery<StorageSubject>(ProgramPoint.before(entry.id(),invoke.header().id()),new StorageSubject.NamedObject(object));
        var after=new PointQuery<StorageSubject>(ProgramPoint.before(entry.id(),next.header().id()),before.subject());
        var values=RegionalValuesAnalysis.prepare(session,io.github.gustavo2358.analysis.values.StorageAnalysisMode.EXPERIMENTAL_PHYSICAL).analysis().orElseThrow().execute().observeStorage(List.of(before,after)).observations();
        var pre=values.stream().filter(o->o.query().equals(before)).findFirst().orElseThrow().value();
        var post=values.stream().filter(o->o.query().equals(after)).findFirst().orElseThrow().value();
        assertEquals(List.of("PROGA   "),pre.candidates().stream().map(Values.TextValue::value).toList());assertFalse(pre.modelValueRemainder());assertTrue(post.modelValueRemainder());
        var rd=ReachingDefinitions.prepare(new StatementEffects(new StorageIndex(session))).analysis().orElseThrow().execute().observeStorage(List.of(before,after)).observations();
        assertTrue(rd.stream().filter(o->o.query().equals(before)).findFirst().orElseThrow().value().definitions().stream().noneMatch(d->d.definition().operation().filter(invoke.header().id()::equals).isPresent()));
        assertTrue(rd.stream().filter(o->o.query().equals(after)).findFirst().orElseThrow().value().definitions().stream().anyMatch(d->d.definition().operation().filter(invoke.header().id()::equals).isPresent()&&d.definition().unknown()));
        var fact=new DependencyAnalysis(io.github.gustavo2358.analysis.values.StorageAnalysisMode.EXPERIMENTAL_PHYSICAL).prepare(p).sites().getFirst();assertEquals(before.point(),fact.valuePoint());assertFalse(fact.modelValueRemainder());
        assertEquals(pre.candidateSupports().getFirst().producers().getFirst().evidence(),fact.candidates().getFirst().supports().getFirst().producer());
    }
}
