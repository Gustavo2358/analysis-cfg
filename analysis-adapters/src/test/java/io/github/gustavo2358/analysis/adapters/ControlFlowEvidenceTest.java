package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.application.AnalysisProvider;
import io.github.gustavo2358.analysis.cfg.domain.*;
import io.github.gustavo2358.analysis.dependencies.*;
import io.github.gustavo2358.analysis.plan.AnalysisOutcome;
import io.github.gustavo2358.analysis.query.*;
import io.github.gustavo2358.analysis.values.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.adapters.ResultFixtures.*;
import static io.github.gustavo2358.analysis.adapters.ValueToCallEvidenceTest.P;
import static io.github.gustavo2358.analysis.adapters.ValueToCallEvidenceTest.U;
import static io.github.gustavo2358.analysis.adapters.ValueToCallEvidenceTest.E;
import static io.github.gustavo2358.analysis.adapters.ValueToCallEvidenceTest.A;
import static io.github.gustavo2358.analysis.adapters.ValueToCallEvidenceTest.B;

/** W2 independent path oracle: typed CFG edges, predecessor facts, join, then CALL. */
class ControlFlowEvidenceTest {
    record Expected(Map<String,Set<String>> supports,boolean open) { }
    static Expected known(String name,String producer) {return new Expected(Map.of(name,Set.of(producer)),false);}
    static Expected unknown() {return new Expected(Map.of(),true);}
    static String padded(String text) {return String.format("%-8s",text);}
    static Operations.Branch decision(String label,String yes,String no) {
        var h=header(U,"branch-"+label);
        var read=new Expressions.Read(operand(h.id(),"read",Operand.Role.VALUE_READ),new Places.ObjectPlace(operand(h.id(),"place",Operand.Role.VALUE_READ),A));
        var predicate=new Expressions.Binary(operand(h.id(),"predicate",Operand.Role.PREDICATE),Expressions.BinaryOperator.EQ,read,
            new Expressions.Literal(operand(h.id(),"literal",Operand.Role.VALUE_READ),new Values.TextValue("YES     ")));
        return new Operations.Branch(h,predicate,new LabelId(U,yes),new LabelId(U,no));
    }
    static List<Instruction> write(String operation,String text) {
        return text==null?List.of():List.of(assign(U,operation,B,padded(text)));
    }
    static Sequence arm(String label,String target,String value) {
        return new Sequence(new LabelId(U,label),write("write-"+label,value),new Operations.Jump(header(U,"jump-"+label),new LabelId(U,target)),origin(P));
    }
    static Publication diamond(boolean regional,String seed,String left,String right,boolean nested) {
        var seq=new ArrayList<Sequence>();
        seq.add(new Sequence(new LabelId(U,"start"),write("seed",seed),decision("start","left","right"),origin(P)));
        seq.add(nested?new Sequence(new LabelId(U,"left"),write("write-left",left),decision("left","inner-a","inner-b"),origin(P)):arm("left","join",left));
        seq.add(arm("right","join",right));
        if(nested) {
            seq.add(arm("inner-a","middle","PROGA"));seq.add(arm("inner-b","middle","PROGB"));
            seq.add(arm("middle","join",null));
        }
        seq.add(new Sequence(new LabelId(U,"join"),List.of(),W1dModelTest.call(U,"invoke","end",B,false),origin(P)));
        seq.add(returning(U,"end",List.of()));
        var base=ValueToCallEvidenceTest.fixture(List.of(),false);var unit=base.units().getFirst();
        List<Memory.ObjectDeclaration> objects=unit.objects();List<Memory.Storage> storage=base.storage();List<Proofs.Premise> premises=List.of();
        if(!regional) {
            var declarations=new ArrayList<Memory.ObjectDeclaration>();var cells=new ArrayList<Memory.Storage>();
            for(var object:objects) {
                var id=new StorageId(P,"cell-"+object.id().localId());
                declarations.add(new Memory.ObjectDeclaration(object.id(),object.displayName(),object.typeRef(),new Memory.CellBinding(id),object.visibility(),object.origin(),object.coverage(),object.precision()));
                cells.add(new Memory.Cell(new Memory.StorageHeader(id,Optional.of(U),Memory.Lifetime.PERSISTENT,Memory.Visibility.PRIVATE,origin(P)),Types.known(Types.Builtin.TEXT)));
            }
            objects=declarations;storage=cells;
            premises=List.of(new Proofs.Premise(new PremiseId(P,"separate-cells"),"synthetic contract","separate predicate and target storage",origin(P),new Proofs.DisjointStorage(cells.stream().map(c->c.header().id()).toList())));
        }
        return new Publication(P,base.airVersion(),regional?base.capabilities():new Capabilities.Manifest(List.of(),List.of()),base.artifacts(),
            List.of(unit(U,unit.entries(),seq,objects)),storage,base.resources(),base.artifactRelations(),base.origins(),base.coverage(),base.uncertainties(),premises);
    }
    static void topology(Publication p,boolean nested) {
        var built=W1dBoundaryTest.build(p);assertEquals(io.github.gustavo2358.analysis.cfg.application.CfgBuildResult.Status.CFG_BUILT,built.status());
        var graph=built.graph().orElseThrow();var names=new HashMap<CfgNodeId,String>();
        for(var node:graph.nodes())names.put(node.id(),node instanceof CfgNode.SequenceNode s?s.source().label().localId():node instanceof CfgNode.EntryNode?"ENTRY":"EXIT");
        var expected=new TreeSet<>(List.of("ENTRY>start:ENTRY","start>left:BRANCH_TRUE","start>right:BRANCH_FALSE","join>end:INVOKE_NORMAL","end>EXIT:RETURN"));
        expected.add("right>join:JUMP");
        if(nested)expected.addAll(List.of("left>inner-a:BRANCH_TRUE","left>inner-b:BRANCH_FALSE","inner-a>middle:JUMP","inner-b>middle:JUMP","middle>join:JUMP"));
        else expected.add("left>join:JUMP");
        var actual=new TreeSet<String>();var reachable=new HashSet<CfgNodeId>();var pending=new ArrayDeque<CfgNodeId>();
        graph.nodes().stream().filter(CfgNode.EntryNode.class::isInstance).forEach(n->pending.add(n.id()));
        for(var edge:graph.transitions()) {assertEquals(E,edge.activationEntry());actual.add(names.get(edge.from())+">"+names.get(edge.to())+":"+edge.kind());}
        assertEquals(expected,actual);assertEquals(expected.size(),graph.transitions().size());
        while(!pending.isEmpty()) {var n=pending.remove();if(reachable.add(n))graph.transitions().stream().filter(t->t.from().equals(n)).forEach(t->pending.add(t.to()));}
        assertEquals(graph.nodes().size(),reachable.size(),"every expected branch and join must be reachable");
    }
    static void observation(TextValueFact f,Expected expected) {
        assertEquals(ValueFact.Reachability.REACHABLE,f.reachability());
        assertEquals(expected.supports().keySet().stream().sorted().map(ControlFlowEvidenceTest::padded).toList(),f.candidates().stream().map(Values.TextValue::value).toList());
        var supports=new TreeMap<String,Set<String>>();
        for(var candidate:f.candidateSupports())supports.put(candidate.candidate().value().stripTrailing(),new TreeSet<>(candidate.producers().stream().map(s->s.evidence().localId()).toList()));
        assertEquals(expected.supports(),supports);assertEquals(expected.open(),f.modelValueRemainder());
        assertFalse(f.sourceUnknownRemainder());assertEquals(expected.open(),f.effectiveUnknownRemainder());
    }
    static void check(String scenario,String seed,String left,String right,Expected l,Expected r,Expected joined,boolean nested) {
        for(boolean regional:List.of(false,true)) {
            var p=diamond(regional,seed,left,right,nested);topology(p,nested);var s=ValueToCallEvidenceTest.session(p);
            var key=regional?RegionalValuesProvider.key(E,StorageAnalysisMode.EXPERIMENTAL_PHYSICAL):PossibleValuesProvider.key(E,PossibleValuesAnalysis.EFFECTS_PROFILE);
            AnalysisProvider<ObjectId,? extends TextValueFact> provider=regional?new RegionalValuesProvider():new PossibleValuesProvider();
            var prepared=provider.prepare(s,key);assertNull(prepared.refusal());var run=prepared.execute();assertEquals(AnalysisOutcome.Status.STABLE,run.outcome().status());
            var queries=List.of(new PointQuery<>(ProgramPoint.before(E,new OperationId(U,nested?"jump-inner-a":"jump-left")),B),
                new PointQuery<>(ProgramPoint.before(E,new OperationId(U,nested?"jump-inner-b":"jump-right")),B),new PointQuery<>(ProgramPoint.before(E,new OperationId(U,"invoke")),B));
            var batch=run.observe(queries).batch();assertEquals(ObservationBatch.Status.COMPLETE,batch.status());
            var expected=List.of(l,r,joined);
            for(int i=0;i<3;i++) {final var q=queries.get(i);var o=batch.observations().stream().filter(x->x.query().equals(q)).findFirst().orElseThrow();assertEquals(ObservationBatch.QueryStatus.VALUE,o.status());observation(o.value(),expected.get(i));}
            var keys=CallDependencyPlan.select(s).stream().flatMap(x->x.dependencies().requiredAnalysisKeys().stream()).collect(java.util.stream.Collectors.toSet());
            assertEquals(Set.of(key.implementation(),"Reachability"),keys.stream().map(x->x.implementation()).collect(java.util.stream.Collectors.toSet()));
            var result=new DependencyAnalysis(regional?StorageAnalysisMode.EXPERIMENTAL_PHYSICAL:StorageAnalysisMode.LOGICAL_ONLY).prepare(p);assertEquals(1,result.sites().size());var call=result.sites().getFirst();
            assertEquals(joined.supports().keySet().stream().sorted().toList(),call.candidates().stream().map(DependencySiteFact.Candidate::referenceName).toList());
            var finalSupports=new TreeMap<String,Set<String>>();
            for(var candidate:call.candidates())finalSupports.put(candidate.referenceName(),new TreeSet<>(candidate.supports().stream().map(x->x.producer().localId()).toList()));
            assertEquals(joined.supports(),finalSupports);assertEquals(joined.open(),call.modelValueRemainder());assertFalse(call.sourceValueRemainder());
            assertFalse(call.interpretationUnknownRemainder());assertEquals(joined.open(),call.effectiveUnknownRemainder());
            assertEquals(DependencySiteFact.TargetStatus.RESOLVED_CANDIDATES,call.targetStatus());assertEquals(DependencySiteFact.Reachability.REACHABLE,call.reachability());
            var post=batch.observations().stream().filter(x->x.query().equals(queries.getLast())).findFirst().orElseThrow().value();
            assertTrue(call.evidence().containsAll(post.evidence()));assertTrue(call.provenance().containsAll(post.provenance()));assertTrue(call.premises().containsAll(post.premises()));
            assertEquals(1L,result.metrics().get("possibleValuesRuns"));
            System.out.println("W2_CONTROL "+scenario+" regional="+regional+" candidates="+finalSupports+" modelOpen="+call.modelValueRemainder()+" metrics="+new TreeMap<>(run.outcome().metrics()));
        }
    }
    @Test void aSameValueUnifiesCandidateButKeepsBothSupports() {
        check("A",null,"PROGA","PROGA",known("PROGA","write-left"),known("PROGA","write-right"),new Expected(Map.of("PROGA",Set.of("write-left","write-right")),false),false);
    }
    @Test void bDifferentValuesKeepTheirOwnSupports() {
        check("B",null,"PROGA","PROGB",known("PROGA","write-left"),known("PROGB","write-right"),new Expected(Map.of("PROGA",Set.of("write-left"),"PROGB",Set.of("write-right")),false),false);
    }
    @Test void cUnwrittenPathPreservesUnknown() {
        check("C",null,"PROGA",null,known("PROGA","write-left"),unknown(),new Expected(Map.of("PROGA",Set.of("write-left")),true),false);
    }
    @Test void dStrongWriteKillsOnlyItsOwnPath() {
        check("D","OLD","NEW",null,known("NEW","write-left"),known("OLD","seed"),new Expected(Map.of("NEW",Set.of("write-left"),"OLD",Set.of("seed")),false),false);
    }
    @Test void eBothStrongWritesEliminateOldAtJoin() {
        check("E","OLD","PROGA","PROGB",known("PROGA","write-left"),known("PROGB","write-right"),new Expected(Map.of("PROGA",Set.of("write-left"),"PROGB",Set.of("write-right")),false),false);
    }
    @Test void nestedDiamondDoesNotDependOnSingleJoin() {
        check("nested",null,"OLD","PROGA",known("PROGA","write-inner-a"),known("PROGB","write-inner-b"),new Expected(Map.of("PROGA",Set.of("write-inner-a","write-right"),"PROGB",Set.of("write-inner-b")),false),true);
    }
}
