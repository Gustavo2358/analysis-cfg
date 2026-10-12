package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.cfg.application.*;
import io.github.gustavo2358.analysis.cfg.extension.SemanticInterpreterRegistry;
import io.github.gustavo2358.analysis.dependencies.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Prospective real wide CALL header law, not executed or qualified yet.
 * A literal target isolates header retention from nominal value solving.
 * NativeCase preserves its original quotas and forced-eviction page geometry.
 * Upstream AIR admission remains an independently observed prerequisite. */
final class NativeGeneralHeaderWidthTest {
    @TempDir Path directory;
    private static Publication publication(String local) {
        return W1dModelTest.model(0,unit->{
            var original=W1dModelTest.call(unit,local,"end",null,false);
            var target=new Interactions.LiteralTarget("program","cobol.program",
                "PROGA",Interactions.ExactName.INSTANCE,original.header().origin());
            var invoke=new Operations.Invoke(original.header(),original.action(),target,
                original.arguments(),original.results(),original.signature(),
                original.effectOperands(),original.effectBound(),original.outcomes(),original.contract());
            return List.of(new Sequence(new LabelId(unit,"start"),List.of(),invoke,
                ResultFixtures.origin(unit.publication())),ResultFixtures.returning(unit,"end",List.of()));
        });
    }
    @Test void realSingleLiteralCallKeepsCompleteWideOperationIdentityWithoutFullHeaderProjection()throws Exception {
        // Independent expected identity; never regenerated from the consumer.
        int width=Integer.getInteger("native.general.header.width",300005);
        assertTrue(width>=64);String local="call-"+"x".repeat(width-6)+"Z";
        var input=publication(local);var fixture=new SourceCorrelationSnapshotTest();fixture.directory=directory;
        long began=System.nanoTime();
        try(var context=fixture.new NativeCase(input,fixture.source(input))) {
            System.out.println("GENERAL_HEADER_WIDTH AIR_ADMITTED chars="+width+" elapsedNanos="+(System.nanoTime()-began));
            var cfg=new CfgBuildCoordinator(SemanticInterpreterRegistry.empty())
                .buildChecked(context.program,context.checked,BuildOptions.defaults());
            assertEquals(CfgBuildResult.Status.CFG_BUILT,cfg.status());
            System.out.println("GENERAL_HEADER_WIDTH CFG_BUILT chars="+width);
            try(var owner=new DependencyAnalysis().prepareManaged(context.program,cfg,context.pages,context.execution)) {
                System.out.println("GENERAL_HEADER_WIDTH MANAGED_PREPARED chars="+width);
                assertEquals(1,owner.result().sites().size());assertEquals(0,owner.fullFactProjections());
                try(var cursor=owner.openProgram(0)) {
                    assertEquals(1,cursor.siteCount());assertEquals(1,cursor.candidateCount());
                    char[] reference=new char[5];cursor.readReference(0,0,reference,0,5);
                    assertEquals("PROGA",new String(reference));
                    try(var site=cursor.openSite(0);var operation=site.operation()) {
                        assertEquals(width,operation.characterCount(PagedDependencyFactStorage.IdentityPart.LOCAL));
                        char[] block=new char[256];
                        for(int at=0;at<width;at+=Math.min(block.length,width-at)) {
                            int size=Math.min(block.length,width-at);
                            operation.readCharacters(PagedDependencyFactStorage.IdentityPart.LOCAL,at,block,0,size);
                            assertEquals(local.substring(at,at+size),new String(block,0,size));
                        }
                    }
                }
                assertEquals(0,owner.fullFactProjections());assertNotNull(context.program.admission().snapshot());
                System.out.println("GENERAL_HEADER_WIDTH COMPLETE chars="+width+" fullFact=0");
            }
        }
    }
}
