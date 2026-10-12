package io.github.gustavo2358.analysis.values;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.ObjectId;
import java.util.Map;

/** Shared scalar assignment shape guards. No candidate preparation or execution.
 * Destination is global; source/value is checked only after demand selection.
 * Expression arithmetic failures deliberately propagate, not semantic refusals. */
final class ScalarAssignmentAdmission {
    private ScalarAssignmentAdmission() { }
    static TextProfile.Location destination(Operations.Assign assign,Map<ObjectId,TextProfile.Location> subjects) {
        if(!(assign.destination() instanceof Places.ObjectPlace destination))
            throw new TextProfile.Refusal(false,"UNSUPPORTED_EFFECT_PROFILE");
        var location=subjects.get(destination.object());
        if(location==null)throw new TextProfile.Refusal(false,"UNSUPPORTED_STORAGE_PROFILE");
        return location;
    }
    /** Direct copies return their canonical source; admitted literals and outer
     * FitText/SliceText expressions return null. All other shapes refuse. */
    static TextProfile.Location source(Operations.Assign assign,Map<ObjectId,TextProfile.Location> subjects) {
        if(assign.value() instanceof Expressions.Literal literal&&literal.value() instanceof Values.TextValue)return null;
        if(assign.value() instanceof Expressions.Read read&&read.place() instanceof Places.ObjectPlace source) {
            var location=subjects.get(source.object());
            if(location==null)throw new TextProfile.Refusal(false,"UNSUPPORTED_STORAGE_PROFILE");
            return location;
        }
        if(assign.value() instanceof Expressions.FitText||assign.value() instanceof Expressions.SliceText) {
            for(var source:TextExpressions.reads(assign.value()))
                if(!subjects.containsKey(source))throw new TextProfile.Refusal(false,"UNSUPPORTED_STORAGE_PROFILE");
            return null;
        }
        throw new TextProfile.Refusal(false,"UNSUPPORTED_EFFECT_PROFILE");
    }
}
