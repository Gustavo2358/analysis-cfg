package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.cfg.domain.*;
import io.github.gustavo2358.analysis.dependencies.*;
import io.github.gustavo2358.analysis.query.ProgramPoint;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.adapters.ResultFixtures.*;

/** AIR-only probes through the public CFG, planning and dependency boundaries. */
final class W2dModelTest {
    static Publication diamond(boolean open, boolean orphan) {
        var base=W1dModelTest.model(1,u->List.of(returning(u,"start",List.of())));
        var u=base.units().getFirst().id();var object=new ObjectId(u,"object-0");
        var h=header(u,"if");var gap=new UncertaintyId(base.id(),"predicate");
        var predicate=new Expressions.Unknown(operand(h.id(),"predicate",Operand.Role.PREDICATE),
            new Types.Known(Types.Builtin.BOOL),List.of(),Scopes.NoMemory.INSTANCE,gap);
        var seq=new ArrayList<Sequence>();
        seq.add(new Sequence(new LabelId(u,"start"),List.of(),new Operations.Branch(h,predicate,
            new LabelId(u,"then"),new LabelId(u,open?"call":"else")),origin(base.id())));
        seq.add(arm(u,object,"then","assign-a","PROGA   "));
        if(!open)seq.add(arm(u,object,"else","assign-b","PROGB   "));
        seq.add(new Sequence(new LabelId(u,"call"),List.of(),W1dModelTest.call(u,"invoke","end",object,true),origin(base.id())));
        seq.add(returning(u,"end",List.of()));
        if(orphan)seq.add(arm(u,object,"orphan","assign-bad","BADPROG "));
        var origins=new ArrayList<>(base.origins());
        for(String id:List.of("assign-a","assign-b","assign-bad"))
            origins.add(new Origins.Contractual(new OriginId(base.id(),id),"model-only "+id,"1"));
        return new Publication(base.id(),base.airVersion(),base.capabilities(),base.artifacts(),
            List.of(unit(u,base.units().getFirst().entries(),seq,base.units().getFirst().objects())),base.storage(),
            base.resources(),base.artifactRelations(),origins,base.coverage(),
            List.of(new Evidence.Uncertainty(gap,"EXTERNAL_VALUE",List.of(Evidence.Dimension.VALUES),
                new Scopes.EntityScope(List.of(h.id())),"unknown predicate",origin(base.id()))),base.premises());
    }

    private static Sequence arm(UnitId u,ObjectId object,String label,String id,String text) {
        var a=assign(u,id,object,text);var h=a.header();
        var unique=new Operations.Header(h.id(),new OriginId(u.publication(),id),h.coverage(),h.precision(),h.uncertainties());
        return new Sequence(new LabelId(u,label),List.of(new Operations.Assign(unique,a.destination(),a.value())),
            new Operations.Jump(header(u,"jump-"+label),new LabelId(u,"call")),origin(u.publication()));
    }

    static DependencyResult oracle(Publication p,boolean open) {
        var result=assertDoesNotThrow(()->new DependencyAnalysis().prepare(p));assertEquals(1,result.sites().size());
        var f=result.sites().getFirst();var u=p.units().getFirst();
        var names=open?List.of("PROGA"):List.of("PROGA","PROGB");
        var padded=open?List.of("PROGA   "):List.of("PROGA   ","PROGB   ");
        assertEquals(names,f.candidates().stream().map(DependencySiteFact.Candidate::referenceName).toList());
        assertEquals(padded,f.rawCandidates().stream().map(DependencySiteFact.RawCandidate::rawValue).toList());
        assertEquals(padded,f.candidates().stream().map(DependencySiteFact.Candidate::rawValue).toList());
        assertEquals(open,f.modelValueRemainder());
        assertFalse(f.sourceValueRemainder());assertFalse(f.interpretationUnknownRemainder());
        assertEquals(open,f.effectiveUnknownRemainder());
        assertEquals(ProgramPoint.before(u.entries().getFirst().id(),new OperationId(u.id(),"invoke")),f.valuePoint());
        assertEquals(new LabelId(u.id(),"call"),f.sequence());assertEquals(0,f.offset());
        assertEquals(DependencySiteFact.Reachability.REACHABLE,f.reachability());
        assertEquals(1L,result.metrics().get("possibleValuesRuns"));
        assertEquals(names,result.edges().stream().map(e->e.candidate().referenceName()).toList());
        assertTrue(result.edges().stream().allMatch(e->e.caller().equals(u.id())&&e.site().equals(f.operation())));
        for(int i=0;i<names.size();i++) {
            String id=i==0?"assign-a":"assign-b";
            var expected=List.of(new DependencySiteFact.Support(DependencySiteFact.SupportKind.VALUE_PRODUCER,
                new OperationId(u.id(),id),new OriginId(p.id(),id),List.of()));
            assertEquals(expected,f.candidates().get(i).supports());
            assertEquals(expected,f.rawCandidates().get(i).supports());
            assertTrue(result.origins().stream().anyMatch(o->o.id().equals(expected.getFirst().origin())));
        }
        return result;
    }

    @Test void closedUnknownBooleanJoinKeepsTwoPaddedCandidatesAndSpecificSupports() { oracle(diamond(false,false),false); }
    @Test void openUnknownBooleanJoinKeepsCandidateAndNaturalRemainder() { oracle(diamond(true,false),true); }
    @Test void unreachableAssignmentCannotContaminateEitherJoin() {
        for(boolean open:List.of(false,true))oracle(diamond(open,true),open);
    }
    @Test void physicalSequencePermutationPreservesBothResults() {
        for(boolean open:List.of(false,true)) {
            var p=diamond(open,true);var expected=oracle(p,open);var seq=new ArrayList<>(p.units().getFirst().sequences());
            // Every block occupies the first physical position, including the orphan.
            for(int i=0;i<seq.size();i++) {
                Collections.rotate(seq,1);
                var actual=oracle(W1dEffectsTest.sequences(p,seq,p.units().getFirst().entries()),open);
                assertEquals(expected.sites(),actual.sites());assertEquals(expected.edges(),actual.edges());
            }
        }
    }
    @Test void cfgUsesExplicitEntryAndExistingLabeledEdges() {
        for(boolean open:List.of(false,true)) {
            var p=diamond(open,false);var built=assertDoesNotThrow(()->W1dBoundaryTest.build(p));
            assertEquals(io.github.gustavo2358.analysis.cfg.application.CfgBuildResult.Status.CFG_BUILT,built.status());
            var g=built.graph().orElseThrow();
            var labels=new HashMap<CfgNodeId,String>();
            for(var n:g.nodes())labels.put(n.id(),n instanceof CfgNode.SequenceNode s?s.source().label().localId():
                n instanceof CfgNode.EntryNode?"ENTRY":"EXIT");
            var actual=g.transitions().stream().map(t->labels.get(t.from())+" "+t.kind()+" "+labels.get(t.to())).collect(java.util.stream.Collectors.toSet());
            var expected=new HashSet<>(List.of("ENTRY ENTRY start","start BRANCH_TRUE then",
                "start BRANCH_FALSE "+(open?"call":"else"),"then JUMP call","call INVOKE_NORMAL end","end RETURN EXIT"));
            if(!open)expected.add("else JUMP call");
            assertEquals(expected,actual);assertEquals(expected.size(),g.transitions().size());
        }
    }
    @Test void interpretationRemainderIsIndependentOfModelAndSource() throws Exception {
        for(boolean open:List.of(false,true)) {
            var p=diamond(open,false);var old=W1dEffectsTest.invoke(p);
            var target=(Interactions.ComputedTarget)old.target();
            var unknown=new Interactions.ComputedTarget(target.category(),target.namespace(),target.name(),
                new Interactions.UnknownName(new UncertaintyId(p.id(),"predicate")),target.origin());
            var changed=target(p,unknown);var fact=new DependencyAnalysis().prepare(changed).sites().getFirst();
            assertEquals(open,fact.modelValueRemainder());assertFalse(fact.sourceValueRemainder());
            assertTrue(fact.interpretationUnknownRemainder());assertTrue(fact.effectiveUnknownRemainder());
            assertEquals(oracle(p,open).sites().getFirst().candidates(),fact.candidates());
        }
        // Conversely source openness survives a fully known name policy.
        var real=W1dBoundaryTest.input("dynamic-x8");var old=(Interactions.ComputedTarget)W1dEffectsTest.invoke(real).target();
        var exact=new Interactions.ComputedTarget(old.category(),old.namespace(),old.name(),Interactions.ExactName.INSTANCE,old.origin());
        var fact=new DependencyAnalysis().prepare(target(real,exact)).sites().getFirst();
        assertFalse(fact.modelValueRemainder());assertTrue(fact.sourceValueRemainder());
        assertFalse(fact.interpretationUnknownRemainder());assertTrue(fact.effectiveUnknownRemainder());
    }
    @Test void w1SingletonAndLiteralKeepTheirCandidatesAndSupports() throws Exception {
        for(String name:List.of("dynamic-x8","literal")) {
            var p=W1dBoundaryTest.input(name);var result=new DependencyAnalysis().prepare(p);
            var fact=result.sites().getFirst();boolean computed=name.equals("dynamic-x8");
            assertEquals(List.of("PROGA"),fact.candidates().stream().map(DependencySiteFact.Candidate::referenceName).toList());
            assertEquals(computed?"PROGA   ":"PROGA",fact.candidates().getFirst().rawValue());
            assertEquals(1,result.edges().size());assertFalse(fact.modelValueRemainder());
            assertEquals(computed?1L:0L,result.metrics().get("possibleValuesRuns"));
            assertEquals(computed?1L:0L,result.metrics().get("possibleValuesPreparations"));
            var producer=computed?p.units().getFirst().sequences().stream().flatMap(s->s.instructions().stream())
                .filter(Operations.Assign.class::isInstance).findFirst().orElseThrow():W1dEffectsTest.invoke(p);
            assertEquals(List.of(producer.header().id()),fact.candidates().getFirst().supports().stream().map(DependencySiteFact.Support::producer).toList());
            if(computed)assertEquals(producer.header().origin(),fact.candidates().getFirst().supports().getFirst().origin());
        }
    }
    private static Publication target(Publication p,Interactions.Target target) {
        var seq=p.units().getFirst().sequences().stream().map(s->{
            if(!(s.terminator() instanceof Operations.Invoke i))return s;
            var replacement=new Operations.Invoke(i.header(),i.action(),target,i.arguments(),i.results(),i.signature(),
                i.effectOperands(),i.effectBound(),i.outcomes(),i.contract());
            return new Sequence(s.label(),s.instructions(),replacement,s.origin());
        }).toList();
        return W1dEffectsTest.sequences(p,seq,p.units().getFirst().entries());
    }
}
