package io.github.gustavo2358.analysis.dependencies;

import io.github.gustavo2358.air.model.Interactions;
import io.github.gustavo2358.air.model.Ids.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Synthetic examples of the generic leading-dollar policy; no real identifiers. */
final class LeadingDollarNameTest {
    @Test void prereleaseCorrectionRetainsTheCurrentProfileIdentity() {assertEquals("cobol-zos-dynamic-call-minimal@1",CallNameInterpreter.PROFILE);}
    private static void accepted(String raw,boolean computed,String expected) {
        var exact=CallNameInterpreter.interpret(raw,computed,Interactions.ExactName.INSTANCE);
        assertEquals(expected,exact.referenceName());assertFalse(exact.unknownRemainder());
        var unknown=new Interactions.UnknownName(new UncertaintyId(new PublicationId("f2-policy"),"name-policy"));
        var open=CallNameInterpreter.interpret(raw,computed,unknown);
        assertEquals(expected,open.referenceName());assertTrue(open.unknownRemainder());
        var extension=CallNameInterpreter.interpret(raw,computed,new Interactions.ExtensionName("unimplemented","1"));
        assertNull(extension.referenceName());assertTrue(extension.unknownRemainder());
    }
    @Test void computedLeadingDollarPreservesNameAndOnlyDropsTrailingPadding() {accepted("$PROGA   ",true,"$PROGA");}
    @Test void literalLeadingDollarPreservesName() {accepted("$PROGA",false,"$PROGA");}
    @Test void syntheticAlphanumericLeadingDollarIsSupported() {accepted("$ABC1",true,"$ABC1");}
    @Test void syntheticLeadingDollarWithPaddingIsSupported() {accepted("$TEST123   ",true,"$TEST123");accepted("$TEST123",false,"$TEST123");}
    @Test void syntheticNameUsesExactlyEightCharactersIncludingDollar() {accepted("$ABCDEFG",true,"$ABCDEFG");}
    @Test void existingAcceptedFormsKeepTheirExactNames() {
        for(var name:new String[]{"PROGA","A$PROGA","A$PROG","_PROGA","ABCDEFGH"}) {
            accepted(name,false,name);accepted(name+"   ",true,name);
        }
    }
    @Test void existingRejectionsAndLiteralPaddingRemainOpen() {
        for(var raw:new String[]{"1PROGA","1$PROGA"," PROGA"," $PROGA","proga","$proga","PROG-A","$PROG-A","ABCDEFGHI","$ABCDEFGH","","   ","$PROGA\t","$PROGA\u00a0","$PRO GA","@PROGA","#PROGA"}) {
            for(boolean computed:new boolean[]{false,true}) {
                var rejected=CallNameInterpreter.interpret(raw,computed,Interactions.ExactName.INSTANCE);
                assertNull(rejected.referenceName(),raw);assertTrue(rejected.unknownRemainder(),raw);
            }
        }
        assertNull(CallNameInterpreter.interpret("$PROGA   ",false,Interactions.ExactName.INSTANCE).referenceName());
    }
}
