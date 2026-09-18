package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.cfg.domain.ProjectionPolicy;
import io.github.gustavo2358.analysis.dependencies.*;
import io.github.gustavo2358.analysis.query.*;
import io.github.gustavo2358.analysis.storage.*;
import io.github.gustavo2358.analysis.structure.AnalysisSession;
import io.github.gustavo2358.analysis.values.*;
import java.math.BigInteger;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.adapters.ResultFixtures.*;

/** Experimental physical observation/consumer oracle; default product mode has separate boundary tests. W1: independent AIR oracles at producer/provider/consumer boundaries; no source parsing. */
class ValueToCallEvidenceTest {
    static final PublicationId P=new PublicationId("value-to-call-evidence");
    static final UnitId U=new UnitId(P,"unit");
    static final EntryId E=new EntryId(U,"entry");
    static final StorageId R=new StorageId(P,"allocation");
    static final ObjectId A=new ObjectId(U,"a"), B=new ObjectId(U,"b");
    static final Memory.Codec IBM=new Memory.ExtensionCodec("text.ebcdic.ibm1047","1",Types.known(Types.Builtin.TEXT));
    static final String RAW="PROGA   ";
    static Memory.ObjectDeclaration view(ObjectId id,int offset) {
        return new Memory.ObjectDeclaration(id,Optional.of("same-display-name"),Types.known(Types.Builtin.TEXT),
            new Memory.ViewBinding(R,BigInteger.valueOf(offset),BigInteger.valueOf(8),IBM),Memory.Visibility.PRIVATE,
            origin(P),Evidence.CoverageStatus.MODELED,header(U,"meta").precision());
    }
    static Publication fixture(List<Instruction> instructions, boolean initialized) {
        var e=entry(U,"entry","start");
        if(initialized) {
            var owner=new EntryOwner(E);
            var place=new Places.ObjectPlace(new Operand.Header(new OperandId(owner,"initial-place"),Operand.Role.VALUE_WRITE,origin(P)),A);
            var literal=new Expressions.Literal(new Operand.Header(new OperandId(owner,"initial-value"),Operand.Role.VALUE_READ,origin(P)),new Values.TextValue(RAW));
            e=new Entries.Entry(e.id(),e.initialLabel(),e.signature(),new Entries.EntryState(
                List.of(new Entries.InitialCondition(place,new Entries.LiteralInitial(literal),origin(P),List.of())),List.of()),e.origin());
        }
        var sequences=List.of(new Sequence(new LabelId(U,"start"),instructions,W1dModelTest.call(U,"invoke","end",B,false),origin(P)),returning(U,"end",List.of()));
        var base=publication(P,List.of(unit(U,List.of(e),sequences,List.of(view(A,0),view(B,8)))),List.of(
            new Memory.Region(new Memory.StorageHeader(R,Optional.of(U),Memory.Lifetime.PERSISTENT,Memory.Visibility.PRIVATE,origin(P)),Optional.of(BigInteger.valueOf(16)),Optional.empty())));
        return new Publication(P,base.airVersion(),new Capabilities.Manifest(List.of(Capabilities.MEMORY_REGIONS,Capabilities.IBM1047),List.of()),base.artifacts(),base.units(),base.storage(),base.resources(),base.artifactRelations(),base.origins(),base.coverage(),base.uncertainties(),base.premises());
    }
    static Operations.Assign copy() {
        var h=header(U,"copy");
        return new Operations.Assign(h,new Places.ObjectPlace(operand(h.id(),"destination",Operand.Role.VALUE_WRITE),B),
            new Expressions.FitText(operand(h.id(),"fit",Operand.Role.VALUE_READ),new Expressions.Read(operand(h.id(),"read",Operand.Role.VALUE_READ),new Places.ObjectPlace(operand(h.id(),"source",Operand.Role.VALUE_READ),A)),BigInteger.valueOf(8)," "));
    }
    static Publication target(Publication p,java.util.function.UnaryOperator<Operations.Invoke> change) {
        var u=p.units().getFirst();var start=u.sequences().getFirst();
        var sequences=List.of(new Sequence(start.label(),start.instructions(),change.apply((Operations.Invoke)start.terminator()),start.origin()),u.sequences().getLast());
        return new Publication(p.id(),p.airVersion(),p.capabilities(),p.artifacts(),List.of(unit(U,u.entries(),sequences,u.objects())),p.storage(),p.resources(),p.artifactRelations(),p.origins(),p.coverage(),p.uncertainties(),p.premises());
    }
    static Operations.Invoke name(Operations.Invoke c,Expression expression) {
        return new Operations.Invoke(c.header(),c.action(),new Interactions.ComputedTarget("program","cobol.program",expression,Interactions.ExactName.INSTANCE,origin(P)),c.arguments(),c.results(),c.signature(),c.effectOperands(),c.effectBound(),c.outcomes(),c.contract());
    }
    static AnalysisSession session(Publication p) {
        var cfg=W1dBoundaryTest.build(p);
        assertEquals(io.github.gustavo2358.analysis.cfg.application.CfgBuildResult.Status.CFG_BUILT,cfg.status(),cfg.toString());
        return AnalysisSession.open(cfg,p,ProjectionPolicy.KNOWN_SUBSET,p.units().getFirst().entries()).session().orElseThrow();
    }
    static RegionalValueFact fact(AnalysisSession s,String operation,ObjectId object) {
        var prepared=new RegionalValuesProvider().prepare(s,RegionalValuesProvider.key(E,io.github.gustavo2358.analysis.values.StorageAnalysisMode.EXPERIMENTAL_PHYSICAL));assertNull(prepared.refusal());
        var run=prepared.execute();
        var observed=run.observe(List.of(new PointQuery<>(ProgramPoint.before(E,new OperationId(U,operation)),object)));
        assertEquals(ObservationBatch.Status.COMPLETE,observed.batch().status());
        var observation=observed.batch().observations().getFirst();assertEquals(ObservationBatch.QueryStatus.VALUE,observation.status());
        return observation.value();
    }
    static Set<String> supports(TextValueFact f) {
        return new TreeSet<>(f.candidateSupports().stream().flatMap(c->c.producers().stream()).map(s->s.evidence().localId()).toList());
    }
    static DependencySiteFact call(Publication p) { return new DependencyAnalysis(io.github.gustavo2358.analysis.values.StorageAnalysisMode.EXPERIMENTAL_PHYSICAL).prepare(p).sites().getFirst(); }
    static void resolved(DependencySiteFact c,String... names) {
        assertEquals(DependencySiteFact.TargetStatus.RESOLVED_CANDIDATES,c.targetStatus());
        assertEquals(List.of(names),c.candidates().stream().map(DependencySiteFact.Candidate::referenceName).toList());
        assertFalse(c.effectiveUnknownRemainder());
    }
    @Test void w1aLiteralProducerAndPhysicalIdentity() {
        var p=fixture(List.of(assign(U,"literal",A,RAW),copy()),false);var s=session(p);
        var f=fact(s,"copy",A);assertEquals(List.of(new Values.TextValue(RAW)),f.candidates());assertEquals(Set.of("literal"),supports(f));
        assertEquals(A,f.subject());assertEquals(1,f.interpretations().size());
        var location=f.interpretations().getFirst().location().location();assertEquals(R,location.base().id());
        assertEquals(StorageRange.exact(BigInteger.ZERO,BigInteger.valueOf(8)),location.range().orElseThrow());
        assertEquals(Optional.of(IBM),f.interpretations().getFirst().codec());
        assertFalse(f.effectiveUnknownRemainder());
    }
    @Test void w1bObjectCopyCapturesLiteralAndW1cProviderFeedsConsumer() {
        var p=fixture(List.of(assign(U,"literal",A,RAW),copy(),assign(U,"later-source",A,"OTHER   ")),false);var s=session(p);
        var f=fact(s,"invoke",B);assertEquals(List.of(new Values.TextValue(RAW)),f.candidates());assertEquals(Set.of("literal"),supports(f));
        assertEquals(StorageRange.exact(BigInteger.valueOf(8),BigInteger.valueOf(8)),f.interpretations().getFirst().location().location().range().orElseThrow());
        var keys=CallDependencyPlan.select(s).stream().flatMap(r->r.dependencies().requiredAnalysisKeys().stream()).toList();
        assertTrue(keys.stream().anyMatch(k->k.implementation().equals(RegionalValuesProvider.IMPLEMENTATION)));
        assertFalse(keys.stream().anyMatch(k->k.implementation().equals(PossibleValuesProvider.IMPLEMENTATION)));
        var c=call(p);resolved(c,"PROGA");assertEquals(B,c.subject());
        assertEquals(Set.of("literal"),new TreeSet<>(c.candidates().getFirst().supports().stream().map(x->x.producer().localId()).toList()));
        assertTrue(c.evidence().containsAll(f.evidence()));assertTrue(c.provenance().containsAll(f.provenance()));assertTrue(c.premises().containsAll(f.premises()));
    }
    @Test void w1dLiteralMoveCallAndValueInitializedCopyCall() {
        for(boolean initialized:List.of(false,true)) {
            var p=initialized?fixture(List.of(copy()),true):fixture(List.of(assign(U,"move-literal",B,RAW)),false);
            var f=fact(session(p),"invoke",B);assertEquals(List.of(new Values.TextValue(RAW)),f.candidates());
            assertEquals(Set.of(initialized?"initial-place":"move-literal"),supports(f));resolved(call(p),"PROGA");
        }
    }
    @Test void literalCallNeedsNoValuesAndUnknownDoesNotBorrowUnrelatedLiteral() {
        var p=fixture(List.of(assign(U,"unrelated",A,RAW)),false);var f=fact(session(p),"invoke",B);
        assertEquals(List.of(),f.candidates());assertTrue(f.modelValueRemainder());
        var c=call(p);assertEquals(List.of(),c.candidates());assertEquals(DependencySiteFact.TargetStatus.OPEN_TARGET,c.targetStatus());assertTrue(c.effectiveUnknownRemainder());
        p=target(p,ignored->W1dModelTest.call(U,"invoke","end",null,false));resolved(call(p),"PROGA");
        assertTrue(CallDependencyPlan.select(session(p)).stream().flatMap(r->r.dependencies().requiredAnalysisKeys().stream()).noneMatch(k->k.implementation().contains("Values")));
    }
    @Test void twoLegitimatePhysicalChoicesRetainBothCandidateSpecificSupports() {
        var p=fixture(List.of(assign(U,"literal-a",A,RAW),assign(U,"literal-b",B,"PROGB   ")),false);
        p=target(p,c->{var op=c.header().id();return name(c,new Expressions.Read(operand(op,"read-choice",Operand.Role.CALL_TARGET),
            new Places.Choice(operand(op,"choice",Operand.Role.VALUE_READ),List.of(
                new Places.ObjectPlace(operand(op,"choice-a",Operand.Role.VALUE_READ),A),new Places.ObjectPlace(operand(op,"choice-b",Operand.Role.VALUE_READ),B)),Scopes.NoMemory.INSTANCE,Types.known(Types.Builtin.TEXT))));});
        var s=session(p);assertTrue(CallDependencyPlan.select(s).stream().flatMap(r->r.dependencies().requiredAnalysisKeys().stream()).anyMatch(k->k.implementation().equals(StorageValuesProvider.IMPLEMENTATION)));
        var read=(Expressions.Read)((Interactions.ComputedTarget)((Operations.Invoke)p.units().getFirst().sequences().getFirst().terminator()).target()).name();
        var prepared=new StorageValuesProvider().prepare(s,StorageValuesProvider.key(E,io.github.gustavo2358.analysis.values.StorageAnalysisMode.EXPERIMENTAL_PHYSICAL));assertNull(prepared.refusal());
        var f=prepared.execute().observe(List.of(new PointQuery<StorageSubject>(ProgramPoint.before(E,new OperationId(U,"invoke")),new StorageSubject.PlaceOccurrence(read.place().header().id())))).batch().observations().getFirst().value();
        assertEquals(List.of(new Values.TextValue(RAW),new Values.TextValue("PROGB   ")),f.candidates());
        var c=call(p);resolved(c,"PROGA","PROGB");
        for(int i=0;i<2;i++)assertEquals(List.of("literal-"+(i==0?"a":"b")),c.candidates().get(i).supports().stream().map(x->x.producer().localId()).toList());
    }
    @Test void byteCopyRetainsCaptureEventAndFittedMoveUsesExistingTransfer() {
        var h=header(U,"byte-copy");
        var zero=new Expressions.Literal(operand(h.id(),"source-offset",Operand.Role.VALUE_READ),new Values.IntValue(BigInteger.ZERO));
        var eight=new Expressions.Literal(operand(h.id(),"destination-offset",Operand.Role.VALUE_READ),new Values.IntValue(BigInteger.valueOf(8)));
        var length=new Expressions.Literal(operand(h.id(),"length",Operand.Role.VALUE_READ),new Values.IntValue(BigInteger.valueOf(8)));
        var fallback=new Envelopes.Envelope(new Envelopes.MemoryEnvelope(List.of(),new Scopes.WithinMemory(new Scopes.StorageMemory(List.of(R))),List.of(),new Scopes.WithinMemory(new Scopes.StorageMemory(List.of(R))),List.of()),
            new Control.ControlEnvelope(List.of(Control.ContinueAlternative.INSTANCE),Scopes.NoControl.INSTANCE),new Envelopes.DependencyEnvelope(List.of(),Scopes.NoResources.INSTANCE));
        var copy=new Operations.CopyBytes(h,new Memory.ByteRange(R,eight,length),new Memory.ByteRange(R,zero,new Expressions.Literal(operand(h.id(),"source-length",Operand.Role.VALUE_READ),new Values.IntValue(BigInteger.valueOf(8)))),BigInteger.valueOf(8),fallback);
        var p=fixture(List.of(assign(U,"literal",A,RAW),copy),false);var s=session(p);
        var run=new StorageValuesProvider().prepare(s,StorageValuesProvider.key(E,io.github.gustavo2358.analysis.values.StorageAnalysisMode.EXPERIMENTAL_PHYSICAL)).execute();
        var f=run.observe(List.of(new PointQuery<StorageSubject>(ProgramPoint.before(E,new OperationId(U,"invoke")),new StorageSubject.NamedObject(B)))).batch().observations().getFirst().value();
        assertEquals(List.of(new Values.TextValue(RAW)),f.candidates());
        assertTrue(f.alternatives().stream().flatMap(a->a.fragments().stream()).flatMap(x->x.captures().stream()).anyMatch(c->c.definition().operation().equals(Optional.of(h.id()))));
        resolved(call(p),"PROGA");
        System.out.println("ANALYSIS_GAPS_COPY_METRICS "+new TreeMap<>(run.outcome().metrics()));
        var logical=copy();
        var fitted=new Operations.Assign(logical.header(),logical.destination(),new Expressions.FitText(operand(logical.header().id(),"fit",Operand.Role.VALUE_READ),((Expressions.FitText)logical.value()).value(),BigInteger.valueOf(8)," "));
        resolved(call(fixture(List.of(assign(U,"literal",A,RAW),fitted),false)),"PROGA");
    }
    @Test void rawRegionalAssignmentWithoutExtentProofStopsBeforeTheProvider() {
        var fitted=copy();var raw=new Operations.Assign(fitted.header(),fitted.destination(),((Expressions.FitText)fitted.value()).value());
        var cfg=W1dBoundaryTest.build(fixture(List.of(assign(U,"literal",A,RAW),raw),false));
        assertEquals(io.github.gustavo2358.analysis.cfg.application.CfgBuildResult.Status.VALIDATION_LIMIT,cfg.status());
        assertTrue(cfg.preflight().issues().stream().anyMatch(i->i.rule().equals("PRECONDITION_NOT_DISCHARGED")));
        assertTrue(cfg.graph().isEmpty());
    }
    @Test void scalarCellCopyUsesPossibleValuesWithoutRegionalFallback() {
        var p=W1dModelTest.model(2,u->{
            var a=new ObjectId(u,"object-0");var b=new ObjectId(u,"object-1");var h=header(u,"copy");
            var copy=new Operations.Assign(h,new Places.ObjectPlace(operand(h.id(),"destination",Operand.Role.VALUE_WRITE),b),
                new Expressions.Read(operand(h.id(),"read",Operand.Role.VALUE_READ),new Places.ObjectPlace(operand(h.id(),"source",Operand.Role.VALUE_READ),a)));
            return List.of(new Sequence(new LabelId(u,"start"),List.of(assign(u,"literal",a,RAW),copy),W1dModelTest.call(u,"invoke","end",b,false),origin(u.publication())),returning(u,"end",List.of()));
        });
        var s=session(p);var u=p.units().getFirst();var e=u.entries().getFirst().id();var object=u.objects().getLast().id();
        var key=PossibleValuesProvider.key(e,PossibleValuesAnalysis.EFFECTS_PROFILE);
        assertTrue(CallDependencyPlan.select(s).stream().flatMap(r->r.dependencies().requiredAnalysisKeys().stream()).anyMatch(key::equals));
        var prepared=new PossibleValuesProvider().prepare(s,key);assertNull(prepared.refusal());
        var f=prepared.execute().observe(List.of(new PointQuery<>(ProgramPoint.before(e,new OperationId(u.id(),"invoke")),object))).batch().observations().getFirst().value();
        assertEquals(List.of(new Values.TextValue(RAW)),f.candidates());assertFalse(f.effectiveUnknownRemainder());
        resolved(call(p),"PROGA");
    }
    @Test void firstAdjacentGapIsInlineComputedExpressionNotMissingObjectEvidence() {
        var p=fixture(List.of(assign(U,"literal",B,RAW)),false);
        p=target(p,c->{var read=((Interactions.ComputedTarget)c.target()).name();return name(c,new Expressions.FitText(operand(c.header().id(),"fit",Operand.Role.CALL_TARGET),read,BigInteger.valueOf(4)," "));});
        var s=session(p);assertEquals(List.of(new Values.TextValue(RAW)),fact(s,"invoke",B).candidates());
        var c=call(p);assertEquals(DependencySiteFact.TargetStatus.UNSUPPORTED_TARGET_EXPRESSION,c.targetStatus());
        assertEquals(List.of(),c.candidates());assertTrue(c.interpretationUnknownRemainder());
        // Unwrapping would incorrectly publish PROGA; evaluating this expression requires PROG.
        assertTrue(CallDependencyPlan.select(s).stream().flatMap(r->r.dependencies().requiredAnalysisKeys().stream()).noneMatch(k->k.implementation().contains("Values")));
    }
}
