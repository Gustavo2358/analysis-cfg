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
                for(var target:actual) {
                    var backward=context.predecessors(target);boolean found=false;
                    while(backward.advance())if(backward.transition().kind()==CfgTransition.Kind.OPAQUE_UNKNOWN&&backward.source()==source)found=true;
                    assertTrue(found,"forward/reverse consistency");
                }
            }
        }
    }
}
