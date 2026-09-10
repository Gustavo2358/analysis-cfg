package io.github.gustavo2358.analysis.values;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.query.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.values.ValuesFixtures.*;
import static io.github.gustavo2358.analysis.values.ValuesTest.*;

class SupportSourceTest {
    @Test void producerSurvivesBlocksAndStrongUpdateKillsOldSupport() {
        var p=producerOrigins(graph(new String[]{"A","PROGA",null,"POOL-ONLY"},new int[][]{{1},{2},{},{}},1,false,false));
        var f=fact(execute(p),before(p,2,0));expected(f,false,"PROGA");
        assertEquals(Set.of(new OperationId(p.units().getFirst().id(),"assign-1")),new HashSet<>(f.evidence()),"W3-F1 producing Assign, not queried Return or pool inventory");
        assertEquals(List.of(new OriginId(p.id(),"producer-assign-1")),f.provenance(),"W3-F1 producer origin");
        assertEquals(f.evidence(),f.candidateSupports().getFirst().producers().stream().map(ValueFact.Support::evidence).toList());
        assertThrows(UnsupportedOperationException.class,()->f.candidateSupports().clear());
        var same=producerOrigins(graph(new String[]{"PROGA","PROGA",null},new int[][]{{1},{2},{}},1,false,false));
        assertEquals(List.of(new OperationId(same.units().getFirst().id(),"assign-1")),fact(execute(same),before(same,2,0)).evidence(),"same-value strong update kills old support");
    }
    @Test void equalCandidateDiamondUnionsBothProducers() {
        var p=producerOrigins(graph(new String[]{null,"PROGA","PROGA",null},new int[][]{{1,2},{3},{3},{}},1,false,false));
        var f=fact(execute(p),before(p,3,0));expected(f,false,"PROGA");var u=p.units().getFirst().id();
        assertEquals(Set.of(new OperationId(u,"assign-1"),new OperationId(u,"assign-2")),new HashSet<>(f.evidence()),"W3-F1 union support even when candidate value does not change");
    }
    @Test void entrySeedPremisesFollowValueAndAreKilledByAssignment() {
        var p=seeded(false,true);var u=p.units().getFirst();var entry=u.entries().getFirst();var object=u.objects().getFirst().id();
        var f=fact(execute(p),new PointQuery<>(ProgramPoint.entry(entry.id()),object));expected(f,false,"BOOT");
        assertEquals(List.of(new PremiseId(p.id(),"seed-premise")),f.premises(),"W3-F1 EntryState premises survive");
        assertEquals(List.of(entry.state().conditions().getFirst().place().header().id()),f.evidence());
        assertEquals(List.of(entry.state().conditions().getFirst().origin()),f.provenance(),"W3-F1 seed origin");
        assertEquals(f.premises(),f.candidateSupports().getFirst().producers().getFirst().premises());
        var after=fact(execute(p),before(p,0,0));expected(after,false,"NEXT");assertTrue(after.premises().isEmpty(),"strong update kills seed premise");
    }
    @Test void entryUncertaintyOpensOnlyThatEntryWithoutChangingModel() {
        var p=seeded(true,false);var u=p.units().getFirst();var run=execute(p);
        for(var entry:u.entries())for(var point:List.of(ProgramPoint.entry(entry.id()),ProgramPoint.before(entry.id(),u.sequences().getFirst().terminator().header().id()))) {
            var f=fact(run,new PointQuery<>(point,u.objects().getFirst().id()));expected(f,false,"BOOT");
            boolean open=entry==u.entries().getFirst();
            assertEquals(open,f.sourceUnknownRemainder(),"W3-F2 EntryState uncertainty independent of complete publication/unit/object");
            assertEquals(open,f.effectiveUnknownRemainder());
        }
    }
    @Test void relevantAliasSourceGapCannotDisappearByQueryingExactAlias() {
        var p=aliasGap(false,Evidence.Dimension.VALUES);var f=fact(execute(p),before(p,1,0));expected(f,false,"PROGA");
        assertTrue(f.sourceUnknownRemainder(),"W3-F2 same-Cell alias value gap");assertTrue(f.effectiveUnknownRemainder());
        p=aliasGap(false,Evidence.Dimension.STORAGE);f=fact(execute(p),before(p,1,0));assertTrue(f.sourceUnknownRemainder(),"same-Cell storage gap");
    }
    @Test void sourceGapDoesNotLeakFromOtherCellOrDependencyDimension() {
        var p=aliasGap(true,Evidence.Dimension.VALUES);var f=fact(execute(p),before(p,1,0));expected(f,false,"A");assertFalse(f.sourceUnknownRemainder());
        p=aliasGap(false,Evidence.Dimension.DEPENDENCIES);f=fact(execute(p),before(p,1,0));expected(f,false,"PROGA");assertFalse(f.sourceUnknownRemainder());
    }
    @Test void candidateSupportsStayAssociatedWithTheirValue() {
        var p=producerOrigins(graph(new String[]{null,"A","B",null,"A"},new int[][]{{1,2},{3},{3},{},{}},1,false,false));
        var run=execute(p);var f=fact(run,before(p,3,0));expected(f,false,"A","B");
        var byValue=new HashMap<String,List<Id>>();
        for(var s:f.candidateSupports())byValue.put(s.candidate().value(),s.producers().stream().map(ValueFact.Support::evidence).toList());
        var u=p.units().getFirst().id();
        assertEquals(Map.of("A",List.of(new OperationId(u,"assign-1")),"B",List.of(new OperationId(u,"assign-2"))),byValue,"no cross-candidate or orphan pool support");
        // Reordering the source inventory changes preparation ordinals, not public evidence order.
        var unit=p.units().getFirst();var sequences=new ArrayList<>(unit.sequences());Collections.reverse(sequences);
        var permuted=replace(p,List.of(unit(unit.id(),unit.entries(),sequences,unit.objects())),p.coverage(),p.uncertainties(),p.premises());
        assertEquals(f,fact(execute(permuted),before(p,3,0)));
    }
    @Test void supportGrowthAtFixedValuePropagatesThroughCycle() {
        var p=producerOrigins(graph(new String[]{null,"PROGA",null,"PROGA",null,null,null},new int[][]{{1,2},{4},{3},{4},{5,6},{4},{}},1,false,false));
        var run=execute(p);var f=fact(run,before(p,6,0));expected(f,false,"PROGA");var u=p.units().getFirst().id();
        assertEquals(Set.of(new OperationId(u,"assign-1"),new OperationId(u,"assign-3")),new HashSet<>(f.evidence()),"support-only change must propagate downstream");
        assertEquals(2,f.candidateSupports().getFirst().producers().size());
    }
    @Test void supportLatticeLawsAndStrongUpdatesRemainFinite() {
        var w=new ValuesWork();var plain=Candidates.singleton(0,w);var a=plain.supportedBy(0,w);var b=plain.supportedBy(1,w);
        var cases=List.of(Candidates.UNKNOWN,a,b,a.join(b,w),a.withOpen(w),a.join(b,w).withOpen(w));
        for(var x:cases)for(var y:cases)for(var z:cases) {
            assertTrue(x.join(x,w).equivalent(x));assertTrue(x.join(y,w).equivalent(y.join(x,w)));
            assertTrue(x.join(y,w).join(z,w).equivalent(x.join(y.join(z,w),w)));
        }
        var first=PossibleValuesState.reached().assign(0,a,w);var second=first.assign(0,b,w);
        assertFalse(first.equivalent(second,w),"support-only change is semantic change");
        assertEquals(1,second.value(0,w).supports.size());assertEquals(1,second.value(0,w).supports.at(0));
        assertEquals(0,first.value(0,w).supports.at(0),"old immutable support remains intact");
    }
    @Test void sameCellInitialConditionsRetainBothPremises() {
        var p=seeded(false,true);var u=p.units().getFirst();var entry=u.entries().getFirst();var original=entry.state().conditions().getFirst();
        var secondPlace=new Places.ObjectPlace(new Operand.Header(new OperandId(new EntryOwner(entry.id()),"other-place"),Operand.Role.VALUE_WRITE,original.origin()),u.objects().getFirst().id());
        var secondLiteral=new Expressions.Literal(new Operand.Header(new OperandId(new EntryOwner(entry.id()),"other-value"),Operand.Role.VALUE_READ,original.origin()),new Values.TextValue("BOOT"));
        var premise=new PremiseId(p.id(),"second-premise");var premises=new ArrayList<>(p.premises());premises.add(new Proofs.Premise(premise,"fixture","second initial fact",original.origin(),new Proofs.SameDomain(new Proofs.ObjectDomain(u.objects().getFirst().id()),new Proofs.OperandDomain(secondLiteral.header().id()),new Proofs.EntryDomain(entry.id()))));
        var second=new Entries.InitialCondition(secondPlace,new Entries.LiteralInitial(secondLiteral),original.origin(),List.of(premise));
        var updated=new Entries.Entry(entry.id(),entry.initialLabel(),entry.signature(),new Entries.EntryState(List.of(original,second),List.of()),entry.origin());
        p=replace(p,List.of(unit(u.id(),List.of(updated),u.sequences(),u.objects())),p.coverage(),p.uncertainties(),premises);
        var f=fact(execute(p),new PointQuery<>(ProgramPoint.entry(entry.id()),u.objects().getFirst().id()));expected(f,false,"BOOT");
        assertEquals(2,f.candidateSupports().getFirst().producers().size());assertEquals(Set.of(original.premises().getFirst(),premise),new HashSet<>(f.premises()));
    }
    @Test void supportCardinalityPreservesAllEqualValueProducers() {
        for(int n:new int[]{1000,2000,4000,10000}) {
            var p=ValuesScaleTest.fanIn(n);var u=p.units().getFirst();var sequences=new ArrayList<Sequence>();
            for(var seq:u.sequences()) {
                var instructions=new ArrayList<Instruction>();
                for(var op:seq.instructions()) {var a=(Operations.Assign)op;instructions.add(new Operations.Assign(a.header(),a.destination(),new Expressions.Literal(a.value().header(),new Values.TextValue("PROGA"))));}
                sequences.add(new Sequence(seq.label(),instructions,seq.terminator(),seq.origin()));
            }
            p=replace(p,List.of(unit(u.id(),u.entries(),sequences,u.objects())),p.coverage(),p.uncertainties(),p.premises());
            var run=execute(p);var f=fact(run,before(p,2*n-1,0));expected(f,false,"PROGA");
            assertEquals(n,f.candidateSupports().getFirst().producers().size(),"no support cap");assertEquals(n,f.evidence().size());
            System.out.println("W3_SUPPORT_METRICS {\"N\":"+n+",\"candidates\":1,\"supports\":"+f.evidence().size()+",\"supportUnionEntriesVisited\":"+run.solveMetrics().get("supportUnionEntriesVisited")+",\"supportBytesAllocatedEstimate\":"+run.solveMetrics().get("supportBytesAllocatedEstimate")+"}");
        }
    }
    static Publication producerOrigins(Publication p) {
        var u=p.units().getFirst();var sequences=new ArrayList<Sequence>();var origins=new ArrayList<Origins.Origin>(p.origins());
        for(var seq:u.sequences()) {
            var instructions=new ArrayList<Instruction>();
            for(var op:seq.instructions()) {
                var a=(Operations.Assign)op;var h=a.header();var origin=new OriginId(p.id(),"producer-"+h.id().localId());origins.add(new Origins.Unavailable(origin,"producer fixture"));
                instructions.add(new Operations.Assign(new Operations.Header(h.id(),origin,h.coverage(),h.precision(),h.uncertainties()),a.destination(),a.value()));
            }
            sequences.add(new Sequence(seq.label(),instructions,seq.terminator(),seq.origin()));
        }
        return new Publication(p.id(),p.airVersion(),p.capabilities(),p.artifacts(),List.of(unit(u.id(),u.entries(),sequences,u.objects())),p.storage(),p.resources(),p.artifactRelations(),origins,p.coverage(),p.uncertainties(),p.premises());
    }
    static Publication seeded(boolean gap,boolean overwrite) {
        var p=graph(new String[]{overwrite?"NEXT":null},new int[][]{{}},1,false,false);var u=p.units().getFirst();var object=u.objects().getFirst().id();
        var entries=new ArrayList<Entries.Entry>();var premises=new ArrayList<Proofs.Premise>();var origins=new ArrayList<Origins.Origin>(p.origins());
        var uncertaintyId=new UncertaintyId(p.id(),"initial-gap");
        for(int i=0;i<(gap?2:1);i++) {
            var entry=entry(u.id(),"entry-"+i,"s0");var placeId=new OperandId(new EntryOwner(entry.id()),"seed-place");var valueId=new OperandId(new EntryOwner(entry.id()),"seed-value");
            var origin=new OriginId(p.id(),"seed-origin-"+i);origins.add(new Origins.Unavailable(origin,"seed fixture"));
            var place=new Places.ObjectPlace(new Operand.Header(placeId,Operand.Role.VALUE_WRITE,origin),object);
            var literal=new Expressions.Literal(new Operand.Header(valueId,Operand.Role.VALUE_READ,origin),new Values.TextValue("BOOT"));
            var premise=new PremiseId(p.id(),"seed-premise");
            if(overwrite)premises.add(new Proofs.Premise(premise,"fixture","explicit initial domain",origin,new Proofs.SameDomain(new Proofs.ObjectDomain(object),new Proofs.OperandDomain(valueId),new Proofs.EntryDomain(entry.id()))));
            var state=new Entries.EntryState(List.of(new Entries.InitialCondition(place,new Entries.LiteralInitial(literal),origin,overwrite?List.of(premise):List.of())),gap&&i==0?List.of(uncertaintyId):List.of());
            entries.add(new Entries.Entry(entry.id(),entry.initialLabel(),entry.signature(),state,entry.origin()));
        }
        var uncertainties=gap?List.of(new Evidence.Uncertainty(uncertaintyId,"NOT_A_WHITELIST_CODE",List.of(Evidence.Dimension.VALUES),new Scopes.EntityScope(List.of(entries.getFirst().id())),"declared entry gap",origin(p.id()))):List.<Evidence.Uncertainty>of();
        return new Publication(p.id(),p.airVersion(),p.capabilities(),p.artifacts(),List.of(unit(u.id(),entries,u.sequences(),u.objects())),p.storage(),p.resources(),p.artifactRelations(),origins,p.coverage(),uncertainties,premises);
    }
    static Publication aliasGap(boolean distinct,Evidence.Dimension dimension) {
        var p=graph(new String[]{"A","PROGA"},new int[][]{{1},{}},2,distinct,distinct);var u=p.units().getFirst();var objects=new ArrayList<>(u.objects());var b=objects.get(1);
        var id=new UncertaintyId(p.id(),"alias-gap");var claim=new Evidence.Claim(new Scopes.EntityScope(List.of(b.id())),Evidence.PrecisionStatus.OPEN,List.of(id));var old=b.precision();
        var precision=new Evidence.Precision(old.control(),dimension==Evidence.Dimension.STORAGE?claim:old.storage(),old.effects(),dimension==Evidence.Dimension.VALUES?claim:old.values(),dimension==Evidence.Dimension.DEPENDENCIES?claim:old.dependencies());
        objects.set(1,new Memory.ObjectDeclaration(b.id(),b.displayName(),b.typeRef(),b.storage(),b.visibility(),b.origin(),b.coverage(),precision));
        return replace(p,List.of(unit(u.id(),u.entries(),u.sequences(),objects)),p.coverage(),List.of(new Evidence.Uncertainty(id,"THIS_TEXT_CANNOT_PROVE_IRRELEVANCE",List.of(dimension),new Scopes.EntityScope(List.of(b.id())),"alias gap",origin(p.id()))),p.premises());
    }
}
