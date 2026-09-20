package io.github.gustavo2358.analysis.storage;

import io.github.gustavo2358.air.model.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static io.github.gustavo2358.analysis.storage.StorageFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

class KillAuthorityTest {
    private StatementEffects.Write write() {
        var operation=assign("write","target",1,2,3,4);
        var p=publication(List.of(region("r",4L,Memory.Lifetime.ACTIVATION),region("other",4L,Memory.Lifetime.ACTIVATION)),
            List.of(view("target","r",0,4),view("other","other",0,4)),List.of(sequence("s",List.of(operation))),List.of());
        return new StatementEffects(new StorageIndex(session(p))).statement(operation.header().id()).writes().getFirst();
    }
    @Test void permitRequiresExecutionDestinationAndDirectTargetTogether() {
        var write=write();var direct=write.targets().stream().filter(StatementEffects.Target::sourceApplicable).findFirst().orElseThrow();
        assertTrue(KillAuthority.exact(write,direct,KillAuthority.Execution.REQUIRED).isPresent());
        assertTrue(KillAuthority.exact(write,direct,KillAuthority.Execution.POSSIBLE).isEmpty());
        // Deliberately modeled possible target remains unable to kill.
        var alias=new StatementEffects.Target(direct.location(),StatementEffects.Strength.MAY,false,List.of(),List.of("MODELED_ALTERNATIVE"));
        assertTrue(KillAuthority.exact(write,alias,KillAuthority.Execution.REQUIRED).isEmpty());
        for(var strength:StatementEffects.Strength.values())for(var selection:StatementEffects.Selection.values()) {
            var changed=new StatementEffects.Write(write.slot(),write.occurrence(),write.destination(),write.source(),write.targets(),selection,strength);
            assertEquals(strength==StatementEffects.Strength.MUST&&selection==StatementEffects.Selection.SINGLE_DESTINATION,
                KillAuthority.exact(changed,direct,KillAuthority.Execution.REQUIRED).isPresent());
        }
    }
    @Test void destinationRemainderAndIncompleteRepresentationForbidExclusion() {
        var write=write();var direct=write.targets().stream().filter(StatementEffects.Target::sourceApplicable).findFirst().orElseThrow();
        var open=new StorageIndex.Resolution(write.destination().candidates(),new Scopes.WithinMemory(new Scopes.AllMemory(P,true)),List.of("OPEN"));
        var changed=new StatementEffects.Write(write.slot(),write.occurrence(),open,write.source(),write.targets());
        assertTrue(KillAuthority.exact(changed,direct,KillAuthority.Execution.REQUIRED).isEmpty());
        assertFalse(KillAuthority.exhaustive(changed,List.of(direct),KillAuthority.Execution.REQUIRED));
        assertFalse(KillAuthority.exhaustive(write,List.of(),KillAuthority.Execution.REQUIRED));
        assertTrue(KillAuthority.exhaustive(write,List.of(direct),KillAuthority.Execution.REQUIRED));
    }
    @Test void genericUpdatesNeedAuthorityToForgetAndDoNotInventSupport() {
        var previous=Set.of("source-supported");
        assertEquals(Set.of("source-supported","new-supported"),KillAuthority.weakUpdate(previous,Set.of("new-supported")));
        assertEquals(Set.of("source-supported","unknown-effect"),KillAuthority.widenUnknown(previous,"unknown-effect"));
        assertThrows(NullPointerException.class,()->KillAuthority.strongOverwrite(null,Set.of("new-supported")));
        var write=write();var direct=write.targets().stream().filter(StatementEffects.Target::sourceApplicable).findFirst().orElseThrow();
        assertEquals(Set.of("new-supported"),KillAuthority.strongOverwrite(KillAuthority.exact(write,direct,KillAuthority.Execution.REQUIRED).orElseThrow(),Set.of("new-supported")));
    }
}
