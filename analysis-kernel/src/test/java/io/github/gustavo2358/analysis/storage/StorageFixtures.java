package io.github.gustavo2358.analysis.storage;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.cfg.application.*;
import io.github.gustavo2358.analysis.cfg.domain.*;
import io.github.gustavo2358.analysis.cfg.extension.SemanticInterpreterRegistry;
import io.github.gustavo2358.analysis.structure.AnalysisSession;
import java.math.BigInteger;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Manual AIR data, without producer fixtures or JSON encoder as oracle. */
final class StorageFixtures {
    static final PublicationId P=new PublicationId("regional");
    static final UnitId U=new UnitId(P,"unit");
    static final OriginId O=new OriginId(P,"origin");
    static final UncertaintyId UNKNOWN=new UncertaintyId(P,"unknown");
    static final Types.TypeRef BYTES=Types.known(Types.Builtin.BYTES);
    static StorageId base(String name) { return new StorageId(P,name); }
    static ObjectId object(String name) { return new ObjectId(U,name); }
    static Evidence.Coverage coverage(Scopes.FactScope scope) { return new Evidence.Coverage(Evidence.InventoryStatus.COMPLETE,scope,List.of(),List.of()); }
    static Operations.Header header(String name) {
        var c=new Evidence.Claim(new Scopes.UnitScope(U),Evidence.PrecisionStatus.EXACT,List.of());
        return new Operations.Header(new OperationId(U,name),O,Evidence.CoverageStatus.MODELED,new Evidence.Precision(c,c,c,c,c),List.of());
    }
    static Operand.Header operand(String op,String name,Operand.Role role) { return new Operand.Header(new OperandId(new OperationOwner(new OperationId(U,op)),name),role,O); }
    static Places.ObjectPlace place(String op,String name) { return new Places.ObjectPlace(operand(op,"place-"+name,Operand.Role.VALUE_WRITE),object(name)); }
    static Memory.Storage region(String name,Long size,Memory.Lifetime lifetime) {
        return new Memory.Region(new Memory.StorageHeader(base(name),Optional.of(U),lifetime,Memory.Visibility.PRIVATE,O),
            Optional.ofNullable(size).map(BigInteger::valueOf),size==null?Optional.of(UNKNOWN):Optional.empty());
    }
    static Memory.ObjectDeclaration declaration(String name,Memory.Binding binding) {
        return new Memory.ObjectDeclaration(object(name),Optional.empty(),BYTES,binding,Memory.Visibility.PRIVATE,O,Evidence.CoverageStatus.MODELED,header("metadata").precision());
    }
    static Memory.ObjectDeclaration view(String name,String base,long start,long size) {
        return declaration(name,new Memory.ViewBinding(base(base),BigInteger.valueOf(start),BigInteger.valueOf(size),Memory.IdentityBytes.INSTANCE));
    }
    static Entries.Entry entry(String id,String start) {
        return new Entries.Entry(new EntryId(U,id),Optional.of(new LabelId(U,start)),
            new Interactions.Signature(new Interactions.ParameterInventory(List.of(),Interactions.NoRemainder.INSTANCE),new Interactions.ResultInventory(List.of(),Interactions.NoRemainder.INSTANCE),O),new Entries.EntryState(List.of(),List.of()),O);
    }
    static Sequence sequence(String label,List<Instruction> instructions) { return new Sequence(new LabelId(U,label),instructions,new Operations.Return(header("return-"+label),List.of()),O); }
    static Operations.Assign assign(String id,String destination,int... bytes) {
        return new Operations.Assign(header(id),place(id,destination),new Expressions.Literal(operand(id,"literal",Operand.Role.VALUE_READ),new Values.BytesValue(Arrays.stream(bytes).boxed().toList())));
    }
    static Envelopes.Envelope envelope(Scopes.MemoryBound writes,Control.ControlAlternative successor) {
        return new Envelopes.Envelope(new Envelopes.MemoryEnvelope(List.of(),writes,List.of(),writes,List.of()),new Control.ControlEnvelope(List.of(successor),Scopes.NoControl.INSTANCE),new Envelopes.DependencyEnvelope(List.of(),Scopes.NoResources.INSTANCE));
    }
    static Operations.Header uncertainHeader(String name) { var h=header(name);return new Operations.Header(h.id(),h.origin(),h.coverage(),h.precision(),List.of(UNKNOWN)); }
    static Publication withEntries(Publication p,List<Entries.Entry> entries) {
        var u=p.units().getFirst();var unit=new io.github.gustavo2358.air.model.Unit(u.id(),u.containingUnit(),u.objects(),u.visibleObjects(),entries,u.sequences(),u.completionPorts(),u.body(),u.bodyUnavailable(),u.coverage(),u.origin());
        return new Publication(p.id(),p.airVersion(),p.capabilities(),p.artifacts(),List.of(unit),p.storage(),p.resources(),p.artifactRelations(),p.origins(),p.coverage(),p.uncertainties(),p.premises());
    }
    static Entries.Entry seeded(String name,String start,String subject,int... bytes) {
        var e=entry(name,start);var owner=new EntryOwner(e.id());
        var place=new Places.ObjectPlace(new Operand.Header(new OperandId(owner,"destination"),Operand.Role.VALUE_WRITE,O),object(subject));
        var literal=new Expressions.Literal(new Operand.Header(new OperandId(owner,"initial"),Operand.Role.VALUE_READ,O),new Values.BytesValue(Arrays.stream(bytes).boxed().toList()));
        return new Entries.Entry(e.id(),e.initialLabel(),e.signature(),new Entries.EntryState(List.of(new Entries.InitialCondition(place,new Entries.LiteralInitial(literal),O,List.of())),List.of()),e.origin());
    }
    static Publication publication(List<Memory.Storage> bases,List<Memory.ObjectDeclaration> objects,List<Sequence> sequences,List<Proofs.Premise> premises) {
        var unit=new io.github.gustavo2358.air.model.Unit(U,Optional.empty(),objects,List.of(),List.of(entry("main",sequences.getFirst().label().localId())),sequences,List.of(),io.github.gustavo2358.air.model.Unit.BodyAvailability.AVAILABLE,Optional.empty(),coverage(new Scopes.UnitScope(U)),O);
        var uncertainty=new Evidence.Uncertainty(UNKNOWN,"UNPROVED",List.of(Evidence.Dimension.STORAGE),new Scopes.PublicationScope(P),"test unknown",O);
        return new Publication(P,SemanticVersion.AIR_2_0_0,new Capabilities.Manifest(List.of(Capabilities.MEMORY_REGIONS),List.of(Capabilities.MEMORY_REGIONS)),List.of(),List.of(unit),bases,List.of(),List.of(),List.of(new Origins.Unavailable(O,"manual fixture")),coverage(new Scopes.PublicationScope(P)),List.of(uncertainty),premises);
    }
    static AnalysisSession session(Publication p) {
        var build=new CfgBuildCoordinator(SemanticInterpreterRegistry.empty()).build(p,BuildOptions.defaults());
        assertEquals(CfgBuildResult.Status.CFG_BUILT,build.status(),()->build.preflight()+" "+build.projectionIssues());
        var admitted=AnalysisSession.open(build,p,ProjectionPolicy.KNOWN_SUBSET,p.units().getFirst().entries());
        assertEquals(AnalysisSession.Status.ACCEPTED,admitted.status(),admitted.reason());return admitted.session().orElseThrow();
    }
    static Proofs.Premise disjoint(String... bases) { return new Proofs.Premise(new PremiseId(P,"disjoint"),"manual oracle","separate allocations",O,new Proofs.DisjointStorage(Arrays.stream(bases).map(StorageFixtures::base).toList())); }
}
