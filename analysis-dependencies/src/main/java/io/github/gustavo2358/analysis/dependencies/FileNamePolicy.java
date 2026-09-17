package io.github.gustavo2358.analysis.dependencies;
import io.github.gustavo2358.air.model.Interactions;

/** C-FC 5.6 API Reference pp3–4. No CALL interpretation or external resolution. */
final class FileNamePolicy {
    private FileNamePolicy() { }
    static boolean supported(Interactions.NamePolicy p){return p instanceof Interactions.ExtensionName e&&e.name().equals("cics-ts.file")&&e.version().equals("1");}
    static String name(String namespace,String raw,boolean computed,Interactions.NamePolicy policy){
        if(!computed&&!namespace.equals("cics.file")&&policy instanceof Interactions.ExactName)return raw.isEmpty()?null:raw;
        if(!namespace.equals("cics.file")||!supported(policy))return null;
        return cicsName(raw,computed,8);
    }
    static String cicsName(String raw,boolean computed,int width){
        if(computed&&raw.length()!=width)return null;
        int end=raw.length();while(end>0&&raw.charAt(end-1)==' ')end--;
        if(end==0||end>width||raw.length()>width)return null;
        for(int n=0;n<end;n++){char c=raw.charAt(n);if(!(c>='A'&&c<='Z'||c>='0'&&c<='9'||c=='$'||c=='@'||c=='#'))return null;}
        return raw.substring(0,end);
    }
}
