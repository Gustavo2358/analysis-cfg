package io.github.gustavo2358.analysis.structure;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.cfg.domain.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.structure.StructuralFixtures.*;

class OpenLabelIndexTest {
    @Test void indexedBoundsKeepScanOrderDuplicatesAndEntryOwnership() {
        collidingGroupsKeepBothExactDestinationSets();
        var p=new PublicationId("label-index");var u=new UnitId(p,"unit");var other=new UnitId(p,"other");
        var a=new LabelId(u,"a");var b=new LabelId(u,"b");
        for(var scope:List.<Scopes.ControlScope>of(new Scopes.LabelsControl(List.of(b,a,b)),
                new Scopes.LabelsControl(List.of()),
                new Scopes.ControlUnion(List.of(new Scopes.LabelsControl(List.of(b)),new Scopes.LabelsControl(List.of(a)))),
                new Scopes.UnitControl(u,true,true,true,true,true,true),new Scopes.AllControl(p))) {
            var gap=new UncertaintyId(p,"gap");var original=header(u,"open");
            var h=new Operations.Header(original.id(),original.origin(),Evidence.CoverageStatus.ABSTRACTED,original.precision(),List.of(gap));
            var opaque=new Operations.Opaque(h,"unknown-test",List.of(),List.of(),new Envelopes.Envelope(
                new Envelopes.MemoryEnvelope(List.of(),Scopes.NoMemory.INSTANCE,List.of(),Scopes.NoMemory.INSTANCE,List.of()),
                new Control.ControlEnvelope(List.of(new Control.JumpAlternative(a)),new Scopes.WithinControl(scope)),
                new Envelopes.DependencyEnvelope(List.of(),Scopes.NoResources.INSTANCE)));
            var base=publication(p,List.of(unit(u,List.of(entry(u,"e1","open"),entry(u,"e2","open")),
                List.of(new Sequence(new LabelId(u,"open"),List.of(),opaque,origin(p)),returning(u,"a",List.of()),returning(u,"b",List.of())),List.of()),
                unit(other,List.of(entry(other,"foreign","a")),List.of(returning(other,"a",List.of())),List.of())),List.of());
            var publication=new Publication(p,base.airVersion(),base.capabilities(),base.artifacts(),base.units(),base.storage(),base.resources(),base.artifactRelations(),base.origins(),base.coverage(),
                List.of(new Evidence.Uncertainty(gap,"MODEL_GAP",List.of(Evidence.Dimension.CONTROL),new Scopes.UnitScope(u),"open control",origin(p))),List.of());
            var session=StructureTest.session(publication);var index=session.index();var source=index.sequence(new LabelId(u,"open"));
            for(var context:session.contexts()) {
                var expected=Arrays.stream(index.nodes).filter(target->OpenControl.allows(source,target,context.entry(),index.policy)).toList();
                var actual=new ArrayList<ProgramIndex.Node>();var cursor=context.successors(source);int stored=0;
                while(cursor.advance()) {
                    if(cursor.transition().kind()==CfgTransition.Kind.OPAQUE_UNKNOWN)actual.add(cursor.target());else stored++;
                }
                assertEquals(expected,actual,"symbolic edges must retain CFG encounter order");
                assertEquals(actual.size()+stored,cursor.edgesVisited());
                var splitKnown=context.knownSuccessors(source);int knownCount=0;
                while(splitKnown.advance()) {
                    assertNotEquals(CfgTransition.Kind.OPAQUE_UNKNOWN,splitKnown.transition().kind());
                    assertEquals(context.entryView().id(),splitKnown.transition().activationEntry());knownCount++;
                }
                assertEquals(stored,knownCount,"partition cannot drop stored edges of Opaque");
                var splitUnknown=context.unknownSuccessors(source);var splitTargets=new ArrayList<ProgramIndex.Node>();
                while(splitUnknown.advance()) {
                    assertEquals(CfgTransition.Kind.OPAQUE_UNKNOWN,splitUnknown.transition().kind());
                    assertEquals(context.entryView().id(),splitUnknown.transition().activationEntry());splitTargets.add(splitUnknown.target());
                }
                assertEquals(expected,splitTargets,"partition preserves full Labels/Union/Unit/All bounds and encounter order");
                if(context.entryView().id().unit().equals(u)) {
                    assertEquals(new Scopes.WithinControl(scope),context.unknownControl(source));
                    var group=context.unknownGroup(source);assertNotNull(group);
                    assertEquals(group,context.unknownGroup(source));assertEquals(group.hashCode(),context.unknownGroup(source).hashCode());
                    long[] reserved={0};assertTrue(group.keyBytes(bytes->reserved[0]+=bytes)>=512);assertTrue(reserved[0]>=128);
                    for(var otherContext:session.contexts())if(otherContext.entryView().id().unit().equals(u)&&otherContext!=context)
                        assertNotEquals(group,otherContext.unknownGroup(source),"activation-scoped normal exits are different targets");
                    var foreignSession=StructureTest.session(publication);
                    var foreignContext=foreignSession.contexts().stream().filter(c->c.entryView().id().equals(context.entryView().id())).findFirst().orElseThrow();
                    assertNotEquals(group,foreignContext.unknownGroup(foreignSession.index().sequence(new LabelId(u,"open"))),"index identity cannot be forged by equal source IDs");
                    assertNull(context.unknownGroup(index.sequence(a))); // A closed Return has no group.
                }
                else {
                    assertThrows(IllegalArgumentException.class,()->context.unknownControl(source));
                    assertThrows(IllegalArgumentException.class,()->context.unknownGroup(source));
                }
                for(var target:actual) {
                    var backward=context.predecessors(target);boolean found=false;
                    while(backward.advance())if(backward.transition().kind()==CfgTransition.Kind.OPAQUE_UNKNOWN&&backward.source()==source)found=true;
                    assertTrue(found,"forward/reverse consistency");
                }
            }
        }
    }
    private static void collidingGroupsKeepBothExactDestinationSets() {
        var p=new PublicationId("group-collision");var u=new UnitId(p,"unit");var gap=new UncertaintyId(p,"gap");
        var aa=new LabelId(u,"Aa");var bb=new LabelId(u,"BB");assertEquals(aa.hashCode(),bb.hashCode());
        var sequences=new ArrayList<Sequence>();
        for(var name:List.of("open-a","open-b")) {
            var original=header(u,name);var h=new Operations.Header(original.id(),original.origin(),Evidence.CoverageStatus.ABSTRACTED,original.precision(),List.of(gap));
            var targets=name.equals("open-a")?aa:bb;
            var known=name.equals("open-a")?List.<Control.ControlAlternative>of(new Control.JumpAlternative(new LabelId(u,"open-b"))):List.<Control.ControlAlternative>of();
            var opaque=new Operations.Opaque(h,"typed-group-test",List.of(),List.of(),new Envelopes.Envelope(
                new Envelopes.MemoryEnvelope(List.of(),Scopes.NoMemory.INSTANCE,List.of(),Scopes.NoMemory.INSTANCE,List.of()),
                new Control.ControlEnvelope(known,new Scopes.WithinControl(new Scopes.LabelsControl(List.of(targets)))),
                new Envelopes.DependencyEnvelope(List.of(),Scopes.NoResources.INSTANCE)));
            sequences.add(new Sequence(new LabelId(u,name),List.of(),opaque,origin(p)));
        }
        sequences.add(returning(u,"Aa",List.of()));sequences.add(returning(u,"BB",List.of()));
        var base=publication(p,List.of(unit(u,List.of(entry(u,"entry","open-a")),sequences,List.of())),List.of());
        var publication=new Publication(p,base.airVersion(),base.capabilities(),base.artifacts(),base.units(),base.storage(),base.resources(),base.artifactRelations(),base.origins(),base.coverage(),
            List.of(new Evidence.Uncertainty(gap,"MODEL_GAP",List.of(Evidence.Dimension.CONTROL),new Scopes.UnitScope(u),"two exact label groups",origin(p))),List.of());
        var session=StructureTest.session(publication);var context=session.contexts().iterator().next();
        var first=context.unknownGroup(session.index().sequence(new LabelId(u,"open-a")));
        var second=context.unknownGroup(session.index().sequence(new LabelId(u,"open-b")));
        assertEquals(first.hashCode(),second.hashCode());assertNotEquals(first,second);
        var definition=new io.github.gustavo2358.analysis.solver.AnalysisDefinition<Integer>() {
            public io.github.gustavo2358.analysis.solver.Direction direction(){return io.github.gustavo2358.analysis.solver.Direction.FORWARD;}
            public Integer bottom(){return 0;}
            public Iterable<Boundary<Integer>> boundaries(AnalysisSession ignored){return List.of(new Boundary<>(context,context.entryNode(),1));}
            public Join<Integer> joinInto(Integer a,Integer b,io.github.gustavo2358.analysis.solver.DomainWork work){return new Join<>(Math.max(a,b),b>a);}
            public boolean equivalent(Integer a,Integer b,io.github.gustavo2358.analysis.solver.DomainWork work){return a.equals(b);}
            public Integer transferBlock(io.github.gustavo2358.analysis.solver.AnalysisPoint point,Integer state,io.github.gustavo2358.analysis.solver.DomainWork work){return state;}
            public Integer transferEdge(io.github.gustavo2358.analysis.solver.AnalysisPoint point,CfgTransition edge,Integer state,io.github.gustavo2358.analysis.solver.DomainWork work){return state;}
        };
        try(var result=io.github.gustavo2358.analysis.solver.DataflowSolver.solve(session,definition)) {
            assertEquals(6,result.metrics().analysisPoints());assertEquals(6,result.metrics().contextualEdges());
            assertEquals(1,result.in(context,session.index().sequence(aa)));assertEquals(1,result.in(context,session.index().sequence(bb)));
        }
    }
}
