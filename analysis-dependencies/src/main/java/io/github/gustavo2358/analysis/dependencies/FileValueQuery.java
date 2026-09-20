package io.github.gustavo2358.analysis.dependencies;
import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.consumers.SiteView;
import io.github.gustavo2358.analysis.query.*;
import io.github.gustavo2358.analysis.storage.*;
import io.github.gustavo2358.analysis.structure.AnalysisSession;
import java.math.BigInteger;

/** Query routing only; all evaluation remains in the general storage provider. */
final class FileValueQuery {
    private FileValueQuery() { }
    static boolean selected(Operations.Invoke i,AnalysisSession session){
        return i.target() instanceof Interactions.ComputedTarget t&&t.namespace().equals("cics.file")&&FileNamePolicy.supported(t.namePolicy())&&t.name() instanceof Expressions.Read r&&readable(r.place());
    }
    static boolean readable(Place place) {
        return place instanceof Places.ObjectPlace||place instanceof Places.Choice
            ||place instanceof Places.RegionSlice s&&s.offset() instanceof Expressions.Literal o&&o.value() instanceof Values.IntValue
                &&s.length() instanceof Expressions.Literal l&&l.value() instanceof Values.IntValue;
    }
    static boolean exactNameArea(Operations.Invoke i,AnalysisSession session) {
        return i.target() instanceof Interactions.ComputedTarget t&&t.name() instanceof Expressions.Read read&&area(read.place(),session,8);
    }
    static boolean contextSelected(Operations.Invoke i,AnalysisSession session){
        return "EXPLICIT".equals(FileSystemContext.selection(i))&&FileSystemContext.expression(i) instanceof Expressions.Read r&&area(r.place(),session,4);
    }
    private static boolean area(Place p,AnalysisSession session,int width){
        if(p instanceof Places.ObjectPlace o){var declaration=session.index().object(o.object());return declaration!=null&&declaration.storage() instanceof Memory.ViewBinding b&&area(b.extent(),b.codec(),width);}
        if(p instanceof Places.RegionSlice s)return s.offset() instanceof Expressions.Literal o&&o.value() instanceof Values.IntValue&&s.length() instanceof Expressions.Literal l&&l.value() instanceof Values.IntValue n&&area(n.value(),s.codec(),width);
        if(p instanceof Places.Choice c)return c.typeRef() instanceof Types.Known t&&t.type()==Types.Builtin.TEXT&&!c.candidates().isEmpty()&&c.candidates().stream().allMatch(q->area(q,session,width));
        return false;
    }
    private static boolean area(BigInteger size,Memory.Codec codec,int width){return size.equals(BigInteger.valueOf(width))&&codec instanceof Memory.ExtensionCodec c&&c.name().equals("text.ebcdic.ibm1047")&&c.version().equals("1");}
    static PointQuery<StorageSubject> query(SiteView site){
        var target=(Interactions.ComputedTarget)((Operations.Invoke)site.operation()).target();return query(site,((Expressions.Read)target.name()).place());
    }
    static PointQuery<StorageSubject> contextQuery(SiteView site){return query(site,((Expressions.Read)FileSystemContext.expression((Operations.Invoke)site.operation())).place());}
    private static PointQuery<StorageSubject> query(SiteView site,Place p){
        StorageSubject subject;
        if(p instanceof Places.ObjectPlace o)subject=new StorageSubject.NamedObject(o.object());
        else if(p instanceof Places.Choice c)subject=new StorageSubject.PlaceOccurrence(c.header().id());
        else {var s=(Places.RegionSlice)p;subject=new StorageSubject.PhysicalRange(s.region(),StorageRange.exact(((Values.IntValue)((Expressions.Literal)s.offset()).value()).value(),((Values.IntValue)((Expressions.Literal)s.length()).value()).value()),s.codec());}
        return new PointQuery<>(ProgramPoint.before(site.entry(),site.operationId()),subject);
    }
}
