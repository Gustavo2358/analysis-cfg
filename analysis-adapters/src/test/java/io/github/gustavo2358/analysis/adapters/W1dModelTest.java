package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.dependencies.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.adapters.ResultFixtures.*;

/** Model-level probes; never substituted for the real frontend/lower vertical. */
final class W1dModelTest {
    static Operations.Invoke call(UnitId u,String id,String next,ObjectId object,boolean writes) {
        var h=header(u,id);var o=origin(u.publication());
        Interactions.Target t=object==null?new Interactions.LiteralTarget("program","cobol.program","PROGA",Interactions.ExactName.INSTANCE,o):
            new Interactions.ComputedTarget("program","cobol.program",new Expressions.Read(operand(h.id(),"name",Operand.Role.CALL_TARGET),new Places.ObjectPlace(operand(h.id(),"place",Operand.Role.VALUE_READ),object)),Interactions.ExactName.INSTANCE,o);
        return new Operations.Invoke(h,"call",t,List.of(),List.of(),new Interactions.ExternalSignature(entry(u,"unused","start").signature()),List.of(),
            new Interactions.EffectBound(new Interactions.ForeignEffects(Scopes.NoMemory.INSTANCE,writes?new Scopes.WithinMemory(new Scopes.AllMemory(u.publication(),true)):Scopes.NoMemory.INSTANCE,List.of()),List.of()),
            new Control.InvocationOutcomes(List.of(new Control.Normal(new LabelId(u,next))),Scopes.NoControl.INSTANCE),new Interactions.KnownContract(new Interactions.ContractRef("model","1",List.of(o))));
    }
    static Publication model(int cells,java.util.function.Function<UnitId,List<Sequence>> make) {
        var p=new PublicationId("w1d-model");var u=new UnitId(p,"caller");var o=origin(p);
        var objects=new ArrayList<Memory.ObjectDeclaration>();var storage=new ArrayList<Memory.Storage>();
        for(int n=0;n<cells;n++) {
            var c=new StorageId(p,"cell-"+n);objects.add(new Memory.ObjectDeclaration(new ObjectId(u,"object-"+n),Optional.empty(),new Types.Known(Types.Builtin.TEXT),new Memory.CellBinding(c),Memory.Visibility.PRIVATE,o,Evidence.CoverageStatus.MODELED,header(u,"meta").precision()));
            storage.add(new Memory.Cell(new Memory.StorageHeader(c,Optional.of(u),Memory.Lifetime.ACTIVATION,Memory.Visibility.PRIVATE,o),new Types.Known(Types.Builtin.TEXT)));
        }
        var original=publication(p,List.of(unit(u,List.of(entry(u,"entry","start")),make.apply(u),objects)),storage);
        var premises=cells<2?List.<Proofs.Premise>of():List.of(new Proofs.Premise(new PremiseId(p,"disjoint"),"test-model","independent declared cells",o,new Proofs.DisjointStorage(storage.stream().map(s->s.header().id()).toList())));
        var complete=new Publication(p,original.airVersion(),original.capabilities(),original.artifacts(),original.units(),storage,original.resources(),original.artifactRelations(),original.origins(),original.coverage(),original.uncertainties(),premises);
        var check=W1dBoundaryTest.build(complete);assertEquals(io.github.gustavo2358.analysis.cfg.application.CfgBuildResult.Status.CFG_BUILT,check.status(),check.toString());return complete;
    }
    @Test void joinKeepsDifferentCandidatesAndTheirSpecificAssignments() {
        var p=model(1,u->{var object=new ObjectId(u,"object-0");return List.of(branch(u,"start","a","b"),
            new Sequence(new LabelId(u,"a"),List.of(assign(u,"assign-a",object,"PROGA   ")),new Operations.Jump(header(u,"jump-a"),new LabelId(u,"call")),origin(u.publication())),
            new Sequence(new LabelId(u,"b"),List.of(assign(u,"assign-b",object,"PROGB   ")),new Operations.Jump(header(u,"jump-b"),new LabelId(u,"call")),origin(u.publication())),
            new Sequence(new LabelId(u,"call"),List.of(),call(u,"invoke","end",object,true),origin(u.publication())),returning(u,"end",List.of()));});
        var r=new DependencyAnalysis().prepare(p);var f=r.sites().getFirst();assertEquals(2,f.candidates().size());assertFalse(f.modelValueRemainder());
        assertEquals(List.of("PROGA","PROGB"),f.candidates().stream().map(DependencySiteFact.Candidate::referenceName).toList());
        for(int n=0;n<2;n++)assertEquals(List.of("assign-"+(n==0?"a":"b")),f.candidates().get(n).supports().stream().map(s->s.producer().localId()).toList());
        assertFalse(f.effectiveUnknownRemainder());assertEquals(2,r.edges().size());
    }
    @Test void targetObjectIdentityIsNotFirstObjectInUnit() {
        var p=model(2,u->List.of(new Sequence(new LabelId(u,"start"),List.of(assign(u,"wrong",new ObjectId(u,"object-0"),"WRONG"),assign(u,"right",new ObjectId(u,"object-1"),"RIGHT   ")),call(u,"invoke","end",new ObjectId(u,"object-1"),true),origin(u.publication())),returning(u,"end",List.of())));
        var f=new DependencyAnalysis().prepare(p).sites().getFirst();assertEquals("object-1",f.subject().localId());assertEquals("RIGHT",f.candidates().getFirst().referenceName());assertEquals("right",f.candidates().getFirst().supports().getFirst().producer().localId());
    }
    @Test void allMemoryOpensEveryModeledCellWhileNoMemoryPreservesThem() {
        for(boolean writes:List.of(false,true)) {
            var p=model(2,u->List.of(new Sequence(new LabelId(u,"start"),List.of(assign(u,"a",new ObjectId(u,"object-0"),"A"),assign(u,"b",new ObjectId(u,"object-1"),"B")),call(u,"effect","next",null,writes),origin(u.publication())),
                new Sequence(new LabelId(u,"next"),List.of(),call(u,"read-second","end",new ObjectId(u,"object-1"),false),origin(u.publication())),returning(u,"end",List.of())));
            var f=new DependencyAnalysis().prepare(p).sites().stream().filter(s->s.operation().localId().equals("read-second")).findFirst().orElseThrow();
            assertEquals(writes,f.modelValueRemainder());assertEquals("B",f.candidates().getFirst().referenceName());assertEquals("b",f.candidates().getFirst().supports().getFirst().producer().localId());
        }
    }
    @Test void invokeAndEffectsScaleHaveLinearWorkCounts() {
        for(int n:List.of(256,512)) {
            var p=model(1,u->{var seq=new ArrayList<Sequence>();var object=new ObjectId(u,"object-0");
                for(int i=0;i<n;i++)seq.add(new Sequence(new LabelId(u,i==0?"start":"s"+i),i==0?List.of(assign(u,"seed",object,"PROGA   ")):List.of(),call(u,"call-"+i,i==n-1?"end":"s"+(i+1),object,true),origin(u.publication())));
                seq.add(returning(u,"end",List.of()));return seq;});
            long start=System.nanoTime();var r=new DependencyAnalysis().prepare(p);long elapsed=System.nanoTime()-start;
            assertEquals(n,r.sites().size());assertEquals(n,r.edges().size());assertEquals(1L,r.metrics().get("possibleValuesRuns"));
            assertEquals(2L*n,r.metrics().get("planning.uniqueQueries"));assertEquals((long)n+2,r.metrics().get("indexedOperations"));
            assertTrue(r.metrics().get("PossibleValues.operationsTransferred")<=4L*n+10);
            System.out.println("W1D_SCALE {\"dimension\":\"invokes\",\"N\":"+n+",\"sites\":"+r.sites().size()+",\"queries\":"+r.metrics().get("planning.uniqueQueries")+",\"transfers\":"+r.metrics().get("PossibleValues.operationsTransferred")+",\"elapsedNanos\":"+elapsed+"}");
        }
    }
}
