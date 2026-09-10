package io.github.gustavo2358.analysis.values;

import io.github.gustavo2358.analysis.structure.*;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.cfg.application.*;
import io.github.gustavo2358.analysis.cfg.domain.*;
import io.github.gustavo2358.analysis.cfg.extension.SemanticInterpreterRegistry;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** AIR-only generators. Expected topology and counts are supplied independently by each oracle. */
final class ValuesFixtures {
    private ValuesFixtures() { }
    static OriginId origin(PublicationId p) { return new OriginId(p, "origin"); }
    static Evidence.Coverage coverage(Scopes.FactScope s) {
        return new Evidence.Coverage(Evidence.InventoryStatus.COMPLETE, s, List.of(), List.of());
    }
    static Operations.Header header(UnitId u, String id) {
        var claim = new Evidence.Claim(new Scopes.UnitScope(u), Evidence.PrecisionStatus.EXACT, List.of());
        return new Operations.Header(new OperationId(u, id), origin(u.publication()), Evidence.CoverageStatus.MODELED,
                new Evidence.Precision(claim, claim, claim, claim, claim), List.of());
    }
    static Operand.Header operand(OperationId op, String id, Operand.Role role) {
        return new Operand.Header(new OperandId(new OperationOwner(op), id), role, origin(op.publication()));
    }
    static Entries.Entry entry(UnitId u, String id, String initial) {
        var o = origin(u.publication());
        return new Entries.Entry(new EntryId(u, id), Optional.of(new LabelId(u, initial)),
                new Interactions.Signature(new Interactions.ParameterInventory(List.of(), Interactions.NoRemainder.INSTANCE),
                        new Interactions.ResultInventory(List.of(), Interactions.NoRemainder.INSTANCE), o),
                new Entries.EntryState(List.of(), List.of()), o);
    }
    static Sequence returning(UnitId u, String label, List<Instruction> instructions) {
        return new Sequence(new LabelId(u, label), instructions, new Operations.Return(header(u, "return-"+label), List.of()), origin(u.publication()));
    }
    static Sequence jump(UnitId u, String label, String target) {
        return new Sequence(new LabelId(u,label), List.of(), new Operations.Jump(header(u,"jump-"+label),new LabelId(u,target)),origin(u.publication()));
    }
    static Sequence branch(UnitId u, String label, String yes, String no) {
        var h = header(u, "branch-"+label);
        return new Sequence(new LabelId(u,label), List.of(), new Operations.Branch(h,
                new Expressions.Literal(operand(h.id(),"predicate",Operand.Role.PREDICATE),new Values.BoolValue(true)),
                new LabelId(u,yes),new LabelId(u,no)),origin(u.publication()));
    }
    static Operations.Assign assign(UnitId u,String id,ObjectId object){return assign(u,id,object,"literal");}
    static Operations.Assign assign(UnitId u, String id, ObjectId object,String text) {
        var h=header(u,id);
        return new Operations.Assign(h,new Places.ObjectPlace(operand(h.id(),"destination",Operand.Role.VALUE_WRITE),object),
                new Expressions.Literal(operand(h.id(),"value",Operand.Role.VALUE_READ),new Values.TextValue(text)));
    }
    static Unit unit(UnitId u, List<Entries.Entry> entries, List<Sequence> sequences, List<Memory.ObjectDeclaration> objects) {
        return new Unit(u,Optional.empty(),objects,List.of(),entries,sequences,List.of(),Unit.BodyAvailability.AVAILABLE,
                Optional.empty(),coverage(new Scopes.UnitScope(u)),origin(u.publication()));
    }
    static Publication publication(PublicationId p, List<Unit> units, List<Memory.Storage> storage) {
        return new Publication(p,SemanticVersion.AIR_2_0_0,new Capabilities.Manifest(List.of(),List.of()),List.of(),units,storage,
                List.of(),List.of(),List.of(new Origins.Unavailable(origin(p),"test fixture")),
                coverage(new Scopes.PublicationScope(p)),List.of(),List.of());
    }
    static Publication linear(int sequences, int instructions, int objects, int entries) {
        var p=new PublicationId("scale"); var u=new UnitId(p,"unit"); var cellId=new StorageId(p,"cell");
        List<Memory.ObjectDeclaration> declarations=new ArrayList<>();
        for(int d=0;d<objects;d++) declarations.add(new Memory.ObjectDeclaration(new ObjectId(u,"object-"+d),Optional.of("same display"),
                new Types.Known(Types.Builtin.TEXT),new Memory.CellBinding(cellId),Memory.Visibility.PRIVATE,origin(p),
                Evidence.CoverageStatus.MODELED,header(u,"metadata").precision()));
        List<Instruction> ins=new ArrayList<>();
        for(int i=0;i<instructions;i++) ins.add(objects==0 ? new Operations.Nop(header(u,"instruction-"+i)) : assign(u,"instruction-"+i,new ObjectId(u,"object-"+(i%objects))));
        List<Sequence> seq=new ArrayList<>();
        for(int n=0;n<sequences;n++) seq.add(n+1==sequences ? returning(u,"seq-"+n,ins) : jump(u,"seq-"+n,"seq-"+(n+1)));
        List<Entries.Entry> ens=new ArrayList<>();
        for(int e=0;e<entries;e++) ens.add(entry(u,"entry-"+e,"seq-0"));
        return publication(p,List.of(unit(u,ens,seq,declarations)),objects==0?List.of():List.of(new Memory.Cell(
                new Memory.StorageHeader(cellId,Optional.of(u),Memory.Lifetime.ACTIVATION,Memory.Visibility.PRIVATE,origin(p)),new Types.Known(Types.Builtin.TEXT))));
    }
    static CfgBuildResult build(Publication p) {
        var result=new CfgBuildCoordinator(SemanticInterpreterRegistry.empty()).build(p,BuildOptions.defaults());
        assertEquals(CfgBuildResult.Status.CFG_BUILT,result.status(),()->result.preflight()+" "+result.projectionIssues());
        return result;
    }
    static AnalysisSession session(Publication p) {
        var build=build(p);var admission=AnalysisSession.open(build,p,ProjectionPolicy.KNOWN_SUBSET,
            p.units().stream().flatMap(u->u.entries().stream()).toList());
        assertEquals(AnalysisSession.Status.ACCEPTED,admission.status(),admission.reason());return admission.session().orElseThrow();
    }
    static PossibleValuesAnalysis.Execution execute(Publication p) {
        var admission=PossibleValuesAnalysis.prepare(session(p));
        assertEquals(PossibleValuesAnalysis.Status.ACCEPTED,admission.status(),admission.reason());return admission.analysis().orElseThrow().execute();
    }
    static Publication graph(String[] writes,int[][] targets,int objects,boolean separateCells,boolean disjoint) {
        var p=new PublicationId("values");var u=new UnitId(p,"unit");
        var cells=new ArrayList<Memory.Storage>();var declarations=new ArrayList<Memory.ObjectDeclaration>();
        for(int i=0;i<(separateCells?objects:Math.min(1,objects));i++)cells.add(new Memory.Cell(new Memory.StorageHeader(new StorageId(p,"cell-"+i),Optional.of(u),Memory.Lifetime.ACTIVATION,Memory.Visibility.PRIVATE,origin(p)),new Types.Known(Types.Builtin.TEXT)));
        for(int i=0;i<objects;i++)declarations.add(new Memory.ObjectDeclaration(new ObjectId(u,"object-"+i),Optional.of("WS-PGM"),new Types.Known(Types.Builtin.TEXT),new Memory.CellBinding(cells.get(separateCells?i:0).header().id()),Memory.Visibility.PRIVATE,origin(p),Evidence.CoverageStatus.MODELED,header(u,"metadata").precision()));
        var sequences=new ArrayList<Sequence>();
        for(int i=0;i<writes.length;i++) {
            String label="s"+i;Sequence control=targets[i].length==0?returning(u,label,List.of()):targets[i].length==1?jump(u,label,"s"+targets[i][0]):branch(u,label,"s"+targets[i][0],"s"+targets[i][1]);
            var instructions=writes[i]==null?List.<Instruction>of():List.<Instruction>of(assign(u,"assign-"+i,declarations.get(i%objects).id(),writes[i]));
            sequences.add(new Sequence(control.label(),instructions,control.terminator(),origin(p)));
        }
        var result=publication(p,List.of(unit(u,List.of(entry(u,"entry","s0")),sequences,declarations)),cells);
        if(disjoint&&cells.size()>1)return replace(result,result.units(),result.coverage(),result.uncertainties(),List.of(new Proofs.Premise(new PremiseId(p,"disjoint"),"synthetic fixture explicit contract","independent cells in this finite machine",origin(p),new Proofs.DisjointStorage(cells.stream().map(c->c.header().id()).toList()))));
        return result;
    }
    static Publication replace(Publication p,List<Unit> units,Evidence.Coverage coverage,List<Evidence.Uncertainty> uncertainties,List<Proofs.Premise> premises) {
        return new Publication(p.id(),p.airVersion(),p.capabilities(),p.artifacts(),units,p.storage(),p.resources(),p.artifactRelations(),p.origins(),coverage,uncertainties,premises);
    }
    static Publication partial(Publication p) {
        var id=new UncertaintyId(p.id(),"missing-control");var scope=new Scopes.PublicationScope(p.id());
        var uncertainty=new Evidence.Uncertainty(id,"ARBITRARY_GAP_TEXT",List.of(Evidence.Dimension.CONTROL),scope,"fixture omitted source control",origin(p.id()));
        return replace(p,p.units(),new Evidence.Coverage(Evidence.InventoryStatus.PARTIAL,scope,List.of(),List.of(id)),List.of(uncertainty),p.premises());
    }
    static CfgBuildResult withGraph(CfgBuildResult b, CfgGraph g) {
        return new CfgBuildResult(b.status(),b.publicationId(),b.airVersion(),b.options(),b.preflight(),b.unsupportedCapabilities(),b.projectionIssues(),Optional.of(g));
    }
}
