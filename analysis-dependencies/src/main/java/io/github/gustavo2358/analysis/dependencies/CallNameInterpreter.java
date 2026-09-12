package io.github.gustavo2358.analysis.dependencies;

import io.github.gustavo2358.air.model.Interactions;
import java.util.regex.Pattern;

/** Explicit consumer policy subset, not a universal COBOL/runtime name resolver. */
public final class CallNameInterpreter {
    public static final String PROFILE="cobol-zos-dynamic-call-minimal@1";
    private static final Pattern CANONICAL=Pattern.compile("[A-Z_][A-Z0-9_@#$]{0,7}");
    private CallNameInterpreter(){ }
    public record Interpretation(String referenceName,boolean unknownRemainder) { }
    public static Interpretation interpret(String raw,boolean computed,Interactions.NamePolicy policy) {
        int end=raw.length();if(computed)while(end>0&&raw.charAt(end-1)==' ')end--;
        String candidate=raw.substring(0,end);
        if(policy instanceof Interactions.ExtensionName||!CANONICAL.matcher(candidate).matches())return new Interpretation(null,true);
        return new Interpretation(candidate,policy instanceof Interactions.UnknownName);
    }
}
