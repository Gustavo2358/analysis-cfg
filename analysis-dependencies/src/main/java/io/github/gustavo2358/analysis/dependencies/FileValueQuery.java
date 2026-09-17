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
        return i.target() instanceof Interactions.ComputedTarget t&&t.namespace().equals("cics.file")&&FileNamePolicy.supported(t.namePolicy())&&t.name() instanceof Expressions.Read r&&area(r.place(),session);
    }
    private static boolean area(Place p,AnalysisSession session){
        if(p instanceof Places.ObjectPlace o){var declaration=session.index().object(o.object());return declaration!=null&&declaration.storage() instanceof Memory.ViewBinding b&&area(b.extent(),b.codec());}
        if(p instanceof Places.RegionSlice s)return s.offset() instanceof Expressions.Literal o&&o.value() instanceof Values.IntValue&&s.length() instanceof Expressions.Literal l&&l.value() instanceof Values.IntValue n&&area(n.value(),s.codec());
        if(p instanceof Places.Choice c)return c.typeRef() instanceof Types.Known t&&t.type()==Types.Builtin.TEXT&&!c.candidates().isEmpty()&&c.candidates().stream().allMatch(q->area(q,session));
        return false;
    }
    private static boolean area(BigInteger size,Memory.Codec codec){return size.equals(BigInteger.valueOf(8))&&codec instanceof Memory.ExtensionCodec c&&c.name().equals("text.ebcdic.ibm1047")&&c.version().equals("1");}
    static PointQuery<StorageSubject> query(SiteView site){
        var target=(Interactions.ComputedTarget)((Operations.Invoke)site.operation()).target();var p=((Expressions.Read)target.name()).place();StorageSubject subject;
        if(p instanceof Places.ObjectPlace o)subject=new StorageSubject.NamedObject(o.object());
        else if(p instanceof Places.Choice c)subject=new StorageSubject.PlaceOccurrence(c.header().id());
        else {var s=(Places.RegionSlice)p;subject=new StorageSubject.PhysicalRange(s.region(),StorageRange.exact(((Values.IntValue)((Expressions.Literal)s.offset()).value()).value(),((Values.IntValue)((Expressions.Literal)s.length()).value()).value()),s.codec());}
        return new PointQuery<>(ProgramPoint.before(site.entry(),site.operationId()),subject);
    }
}
