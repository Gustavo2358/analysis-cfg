package io.github.gustavo2358.analysis.dependencies;

import io.github.gustavo2358.air.model.*;

/** Explicit minimal CICS TS PROGRAM profile; never applies COBOL normalization. */
public final class CicsNameInterpreter {
    public static final String PROFILE="cics-ts.program@1";
    private CicsNameInterpreter(){ }
    static boolean area(Place place,Memory.Binding binding) {
        Memory.Codec codec;java.math.BigInteger length;
        if(place instanceof Places.ObjectPlace) {
            if(!(binding instanceof Memory.ViewBinding view))return false;
            length=view.extent();codec=view.codec();
        } else if(place instanceof Places.RegionSlice slice&&slice.length() instanceof Expressions.Literal l&&l.value() instanceof Values.IntValue n) {
            length=n.value();codec=slice.codec();
        } else return false;
        return length.equals(java.math.BigInteger.valueOf(8))&&codec instanceof Memory.ExtensionCodec c&&c.name().equals("text.ebcdic.ibm1047")&&c.version().equals("1");
    }
    public static CallNameInterpreter.Interpretation interpret(String raw,boolean computed,Interactions.NamePolicy policy) {
        if(!(policy instanceof Interactions.ExtensionName e&&e.name().equals("cics-ts.program")&&e.version().equals("1")))return new CallNameInterpreter.Interpretation(null,true);
        // Length validates a name possibility, not physical storage. The consumer opens
        // interpretation remainder when the physical 8-byte area is unproved.
        if(computed&&raw.length()!=8)return new CallNameInterpreter.Interpretation(null,true);
        int end=raw.length();while(end>0&&raw.charAt(end-1)==' ')end--;
        var name=raw.substring(0,end);
        if(name.isEmpty()||name.length()>8)return new CallNameInterpreter.Interpretation(null,true);
        for(int n=0;n<name.length();n++) {
            char c=name.charAt(n);
            if(!(c>='A'&&c<='Z'||c>='0'&&c<='9'||c=='$'||c=='@'||c=='#'))return new CallNameInterpreter.Interpretation(null,true);
        }
        return new CallNameInterpreter.Interpretation(name,false);
    }
}
