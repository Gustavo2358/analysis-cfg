package io.github.gustavo2358.analysis.solver;

import io.github.gustavo2358.analysis.structure.*;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.air.model.Unit;
import io.github.gustavo2358.analysis.cfg.application.*;
import io.github.gustavo2358.analysis.cfg.domain.*;
import io.github.gustavo2358.analysis.cfg.extension.SemanticInterpreterRegistry;
import io.github.gustavo2358.analysis.solver.*;
import io.github.gustavo2358.analysis.query.*;
import java.math.BigInteger;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Sparse formal conditions must not encode absent keys of unreachable frames. */
class SparseActivationConditionsTest {
    static final PublicationId P=new PublicationId("local-control-test");
    static final UnitId U=new UnitId(P,"unit");
    static final OriginId O=new OriginId(P,"origin");
    static final CompletionPortId A=new CompletionPortId(U,"a"),B=new CompletionPortId(U,"b");
    static LabelId label(String s){return new LabelId(U,s);}
    static Operations.Header h(String s) {
        var exact=new Evidence.Claim(new Scopes.UnitScope(U),Evidence.PrecisionStatus.EXACT,List.of());
        return new Operations.Header(new OperationId(U,s),O,Evidence.CoverageStatus.MODELED,new Evidence.Precision(exact,exact,exact,exact,exact),List.of());
    }
    static Envelopes.Envelope fallback() {
        return new Envelopes.Envelope(new Envelopes.MemoryEnvelope(List.of(),Scopes.NoMemory.INSTANCE,List.of(),Scopes.NoMemory.INSTANCE,List.of()),
            new Control.ControlEnvelope(List.of(),new Scopes.WithinControl(new Scopes.UnitControl(U,true,true,true,true,true,true))),
            new Envelopes.DependencyEnvelope(List.of(),Scopes.NoResources.INSTANCE));
    }
    static Sequence seq(String name,Terminator t){return new Sequence(label(name),List.of(),t,O);}
    static Sequence call(String name,String body,String resume,CompletionPortId...ports){return seq(name,new Operations.LocalInvoke(h(name),label(body),List.of(ports),label(resume),fallback()));}
    static Sequence resume(String name){return seq(name,new Operations.LocalResume(h(name),fallback()));}
    static Sequence jump(String name,String next){return seq(name,new Operations.Jump(h(name),label(next)));}
    static Sequence ret(String name){return seq(name,new Operations.Return(h(name),List.of()));}
    static Sequence boundary(String name,CompletionPortId port,String next){return seq(name,new Operations.LocalBoundary(h(name),port,label(next),fallback()));}
    static Sequence unwind(String name,BigInteger count,String next){return seq(name,new Operations.LocalUnwind(h(name),count,label(next),fallback()));}
    static Evidence.Coverage coverage(Scopes.FactScope scope){return new Evidence.Coverage(Evidence.InventoryStatus.COMPLETE,scope,List.of(),List.of());}
    static AnalysisSession session(List<Sequence> sequences,String...starts) {
        var entries=new ArrayList<Entries.Entry>();
        for(String start:starts)entries.add(new Entries.Entry(new EntryId(U,start),Optional.of(label(start)),
            new Interactions.Signature(new Interactions.ParameterInventory(List.of(),Interactions.NoRemainder.INSTANCE),new Interactions.ResultInventory(List.of(),Interactions.NoRemainder.INSTANCE),O),new Entries.EntryState(List.of(),List.of()),O));
        var unit=new Unit(U,Optional.empty(),List.of(),List.of(),entries,sequences,List.of(new Entries.CompletionPort(A,O),new Entries.CompletionPort(B,O)),Unit.BodyAvailability.AVAILABLE,Optional.empty(),coverage(new Scopes.UnitScope(U)),O);
        var caps=new ArrayList<Capabilities.Capability>();caps.add(Capabilities.LOCAL_CONTROL);
        if(sequences.stream().anyMatch(s->s.terminator() instanceof Operations.LocalInvoke i&&i.reentryGuard().isPresent()))caps.add(Capabilities.LOCAL_REENTRY_GUARD);
        if(sequences.stream().anyMatch(s->s.terminator() instanceof Operations.LocalInvoke i&&!i.resumeRoutes().isEmpty()||s.terminator() instanceof Operations.LocalResume x&&x.resumeKey().isPresent()))caps.add(Capabilities.LOCAL_RESUME_ROUTES);
        if(sequences.stream().anyMatch(s->s.terminator() instanceof Operations.LocalBoundary x&&x.resumeKey().isPresent())) {
            caps.add(Capabilities.LOCAL_BOUNDARY_ROUTES);
            if(!caps.contains(Capabilities.LOCAL_RESUME_ROUTES))caps.add(Capabilities.LOCAL_RESUME_ROUTES);
        }
        if(sequences.stream().anyMatch(s->s.terminator() instanceof Operations.LocalUnwind x&&x.all()))caps.add(Capabilities.LOCAL_UNWIND_ALL);
        var pub=new Publication(P,SemanticVersion.AIR_2_0_0,new Capabilities.Manifest(caps,List.of()),List.of(),List.of(unit),List.of(),List.of(),List.of(),List.of(new Origins.Unavailable(O,"independent fixture")),coverage(new Scopes.PublicationScope(P)),List.of(),List.of());
        var built=new CfgBuildCoordinator(SemanticInterpreterRegistry.empty()).build(pub,BuildOptions.defaults());
        assertEquals(CfgBuildResult.Status.CFG_BUILT,built.status(),built.toString());
        var admission=AnalysisSession.open(built,pub,ProjectionPolicy.KNOWN_SUBSET,entries);
        assertEquals(AnalysisSession.Status.ACCEPTED,admission.status(),admission.reason());return admission.session().orElseThrow();
    }
    static Sequence guarded(String name,String body,String resume,String key,String rejected) {
        return seq(name,new Operations.LocalInvoke(h(name),label(body),List.of(),label(resume),fallback(),
            Optional.of(new Operations.ReentryGuard(key,label(rejected)))));
    }
    @Test void unreachableFramesAllocateNoAncestorConditions() {
        for(int n:new int[]{64,128,256,512}) {
            var sequences=new ArrayList<Sequence>();sequences.add(ret("done"));sequences.add(resume("body"));
            for(int i=0;i<n;i++)sequences.add(guarded("dead-"+i,"body","done","key-"+i,"done"));
            var model=ActivationSolver.structure(session(sequences,"done")).getFirst();
            assertEquals(1,model.shapes().size());
            assertEquals(2,model.conditions().retainedNodes(),"unreachable keys="+n);
        }
    }
    @Test void rootClosesOnlyEncounteredGuardsWithoutAbsenceVectors() {
        var sequences=new ArrayList<Sequence>();sequences.add(guarded("main","body","done","live","rejected"));
        sequences.add(resume("body"));sequences.add(ret("done"));sequences.add(ret("rejected"));
        for(int i=0;i<512;i++)sequences.add(guarded("dead-"+i,"body","done","key-"+i,"rejected"));
        var s=session(sequences,"main");var model=ActivationSolver.structure(s).getFirst();
        var root=model.shapes().get(null);
        assertFalse(root.points().containsKey(s.index().sequence(label("rejected"))));
        assertTrue(root.points().containsKey(s.index().sequence(label("done"))));
        assertTrue(model.conditions().retainedNodes()<=4,"only the live guard may occur");
    }
}
