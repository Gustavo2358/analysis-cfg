package io.github.gustavo2358.analysis.dependencies;

import io.github.gustavo2358.air.model.Interactions;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class NameInterpreterTest {
    @Test void canonicalSubsetAndOnlyComputedTrailingSpaces() {
        for(String name:new String[]{"A","PROGA","_A0@#$","ABCDEFGH"})assertEquals(name,CallNameInterpreter.interpret(name,true,Interactions.ExactName.INSTANCE).referenceName());
        assertEquals("PROGA",CallNameInterpreter.interpret("PROGA   ",true,Interactions.ExactName.INSTANCE).referenceName());
        assertNull(CallNameInterpreter.interpret("PROGA   ",false,Interactions.ExactName.INSTANCE).referenceName());
    }
    @Test void unsupportedRuntimeTransformationsRemainOpen() {
        for(String name:new String[]{"","   "," ProGa ","proga","1PROGA","PROG-A","ABCDEFGHI","PROGA\t","PROGA\u00a0","é","😀"}) {
            var result=CallNameInterpreter.interpret(name,true,Interactions.ExactName.INSTANCE);assertNull(result.referenceName(),name);assertTrue(result.unknownRemainder(),name);
        }
    }
}
