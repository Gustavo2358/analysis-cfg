package io.github.gustavo2358.analysis.cfg.application;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.validation.*;
import io.github.gustavo2358.analysis.cfg.extension.SemanticInterpreterRegistry;
import io.github.gustavo2358.analysis.cfg.testing.AirPublications;
import java.lang.reflect.Modifier;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CheckedPreflightTest {
    @Test void preservesSuccessfulInvalidUnsupportedAndLimitedRuns() {
        var builder=new CfgBuildCoordinator(SemanticInterpreterRegistry.empty());
        for(var publication:List.of(AirPublications.withArtifact(),
                AirPublications.withCoverageOwner(new Ids.PublicationId("foreign")),
                AirPublications.requiring(new Capabilities.Capability("unknown","1")),
                AirPublications.providing(new Capabilities.Capability("AIR-STRUCTURE","2")))) {
            for(var options:List.of(BuildOptions.defaults(),new BuildOptions(new ValidationOptions(128,1,1)))) {
                var checked=AirValidator.check(publication,options.validation());
                var result=builder.buildChecked(checked,options);
                assertEquals(builder.build(publication,options),result);
                assertSame(checked.result(),result.preflight(),"the original complete diagnostic result must be reused");
                assertSame(publication,checked.publication());
            }
        }
    }
    @Test void differentBudgetsMustRevalidateInBothDirections() {
        var publication=AirPublications.withArtifact();var builder=new CfgBuildCoordinator(SemanticInterpreterRegistry.empty());
        var defaults=BuildOptions.defaults();var restricted=new BuildOptions(new ValidationOptions(128,1,1));
        var complete=AirValidator.check(publication,defaults.validation());
        var limited=AirValidator.check(publication,restricted.validation());
        assertEquals(CfgBuildResult.Status.RESOURCE_LIMIT,builder.buildChecked(complete,restricted).status());
        assertEquals(builder.build(publication,defaults),builder.buildChecked(limited,defaults));
        assertNotSame(complete.result(),builder.buildChecked(complete,restricted).preflight());
    }
    @Test void clientsCannotSupplyInventedResultsAndCustomBuildersRemainCompatible() {
        for(var constructor:AirValidator.CheckedPublication.class.getDeclaredConstructors())assertTrue(Modifier.isPrivate(constructor.getModifiers()));
        var checked=AirValidator.check(AirPublications.valid(),ValidationOptions.defaults());
        BuildCfg custom=(publication,options)->{assertSame(checked.publication(),publication);return new CfgBuildCoordinator(SemanticInterpreterRegistry.empty()).build(publication,options);};
        assertEquals(CfgBuildResult.Status.CFG_BUILT,custom.buildChecked(checked,BuildOptions.defaults()).status());
    }
}
