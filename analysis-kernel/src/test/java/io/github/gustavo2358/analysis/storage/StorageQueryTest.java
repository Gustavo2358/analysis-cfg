package io.github.gustavo2358.analysis.storage;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.query.*;
import io.github.gustavo2358.analysis.rd.*;
import java.math.BigInteger;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.storage.StorageFixtures.*;

class StorageQueryTest {
    static ProgramPoint point(){return ProgramPoint.before(new EntryId(U,"main"),new OperationId(U,"return-s"));}
    static StorageSubject.PhysicalRange range(String base,long start,long length) {
        return new StorageSubject.PhysicalRange(base(base),StorageRange.exact(BigInteger.valueOf(start),BigInteger.valueOf(length)),Memory.IdentityBytes.INSTANCE);
    }
    static ReachingDefinitions.Execution execute(Publication p) {return new ReachingDefinitions(new StatementEffects(new StorageIndex(session(p)))).execute();}
    @Test void namedViewAndPhysicalRangeShareTheSameDefinitionProduct() {
        var p=publication(List.of(region("r",8L,Memory.Lifetime.ACTIVATION)),List.of(view("whole","r",0,8),view("prefix","r",0,4)),
            List.of(sequence("s",List.of(assign("d1","whole",1,2,3,4,5,6,7,8),assign("d2","prefix",9,10,11,12)))),List.of());
        var execution=execute(p);var objectFact=execution.observe(List.of(new PointQuery<>(point(),object("whole")))).observations().getFirst().value();
        var named=new PointQuery<StorageSubject>(point(),new StorageSubject.NamedObject(object("whole")));
        var physical=new PointQuery<StorageSubject>(point(),range("r",0,8));
        var batch=execution.observeStorage(List.of(named,physical));assertEquals(ObservationBatch.Status.COMPLETE,batch.status());assertEquals(2,batch.metrics().uniqueQueries());
        for(var observation:batch.observations()){assertEquals(ObservationBatch.QueryStatus.VALUE,observation.status());assertEquals(objectFact,observation.value());}
        var sliced=execution.observeStorage(List.of(new PointQuery<StorageSubject>(point(),range("r",2,4)))).observations().getFirst().value();
        var expected=Map.of("d2",StorageRange.exact(BigInteger.valueOf(2),BigInteger.valueOf(2)),"d1",StorageRange.exact(BigInteger.valueOf(4),BigInteger.valueOf(2)));
        assertEquals(2,sliced.definitions().size());
        for(var c:sliced.definitions())assertEquals(expected.get(c.definition().operation().orElseThrow().localId()),c.contributedRanges().getFirst().location().range().orElseThrow());
        assertFalse(sliced.unknownRemainder());
    }
    @Test void physicalQueriesKeepBigIntegerUnknownTailAndZeroDistinct() {
        var huge=BigInteger.ONE.shiftLeft(100);var start=BigInteger.ONE.shiftLeft(90);
        var r=new Memory.Region(new Memory.StorageHeader(base("r"),Optional.of(U),Memory.Lifetime.PERSISTENT,Memory.Visibility.PRIVATE,O),Optional.of(huge),Optional.empty());
        var p=publication(List.of(r,region("open",null,Memory.Lifetime.PERSISTENT)),List.of(),List.of(sequence("s",List.of())),List.of(disjoint("r","open")));
        var execution=execute(p);var selected=StorageRange.exact(start,BigInteger.valueOf(3));
        var request=new PointQuery<StorageSubject>(point(),new StorageSubject.PhysicalRange(base("r"),selected,Memory.IdentityBytes.INSTANCE));
        var value=execution.observeStorage(List.of(request)).observations().getFirst().value();assertTrue(value.unknownRemainder());
        assertEquals(selected,value.definitions().getFirst().contributedRanges().getFirst().location().range().orElseThrow());
        var open=new PointQuery<StorageSubject>(point(),new StorageSubject.PhysicalRange(base("open"),new StorageRange(BigInteger.ZERO,Optional.empty()),Memory.IdentityBytes.INSTANCE));
        var tail=execution.observeStorage(List.of(open)).observations().getFirst().value();assertTrue(tail.unknownRemainder());assertTrue(tail.resolutionRemainder());
        assertTrue(tail.definitions().getFirst().contributedRanges().getFirst().location().range().orElseThrow().end().isEmpty());
        var empty=execution.observeStorage(List.of(new PointQuery<StorageSubject>(point(),range("r",0,0)))).observations().getFirst().value();
        assertEquals(List.of(),empty.definitions());assertFalse(empty.unknownRemainder());
    }
    @Test void foreignHiddenCellAndOutOfBoundsSubjectsAreExplicitlyUnsupported() {
        var hidden=new Memory.Region(new Memory.StorageHeader(base("hidden"),Optional.empty(),Memory.Lifetime.PERSISTENT,Memory.Visibility.PRIVATE,O),Optional.of(BigInteger.valueOf(8)),Optional.empty());
        var cell=new Memory.Cell(new Memory.StorageHeader(base("cell"),Optional.of(U),Memory.Lifetime.PERSISTENT,Memory.Visibility.PRIVATE,O),BYTES);
        var p=publication(List.of(region("r",8L,Memory.Lifetime.PERSISTENT),hidden,cell),List.of(view("whole","r",0,8)),List.of(sequence("s",List.of())),List.of());
        var requests=List.<StorageSubject>of(range("r",8,1),range("missing",0,1),range("hidden",0,1),range("cell",0,1),new StorageSubject.NamedObject(object("missing")),
            new StorageSubject.PhysicalRange(new StorageId(new PublicationId("foreign"),"r"),StorageRange.exact(BigInteger.ZERO,BigInteger.ONE),Memory.IdentityBytes.INSTANCE));
        var batch=execute(p).observeStorage(requests.stream().map(s->new PointQuery<>(point(),s)).toList());
        assertEquals(ObservationBatch.Status.COMPLETE,batch.status());assertEquals(6,batch.metrics().unsupportedQueries());
        for(var q:batch.observations()){assertEquals(ObservationBatch.PointReason.UNSUPPORTED_SUBJECT,q.reason());assertNull(q.value());}
    }
    @Test void codecIsPartOfQueryIdentityAndDuplicateRequestsShareReplay() {
        var p=publication(List.of(region("r",8L,Memory.Lifetime.PERSISTENT)),List.of(view("whole","r",0,8)),List.of(sequence("s",List.of(assign("d","whole",65,66,67,68,69,70,71,72)))),List.of());
        var bytes=new PointQuery<StorageSubject>(point(),range("r",0,8));
        var text=new PointQuery<StorageSubject>(point(),new StorageSubject.PhysicalRange(base("r"),StorageRange.exact(BigInteger.ZERO,BigInteger.valueOf(8)),Memory.AsciiText.INSTANCE));
        var batch=execute(p).observeStorage(List.of(bytes,text,bytes,text));
        assertEquals(4,batch.metrics().queryRequests());assertEquals(2,batch.metrics().uniqueQueries());assertEquals(1,batch.metrics().sequencesReplayed());
        assertNotEquals(0,StorageSubject.ORDER.compare(bytes.subject(),text.subject()));
        assertEquals(0,StorageSubject.ORDER.compare(bytes.subject(),range("r",0,8)));
    }
    @Test void queryCodecReferencesMustBelongToTheValidatedPublication() {
        var p=publication(List.of(region("r",8L,Memory.Lifetime.PERSISTENT)),List.of(),List.of(sequence("s",List.of())),List.of());
        var absent=new UncertaintyId(P,"absent");var foreign=new UncertaintyId(new PublicationId("foreign"),UNKNOWN.localId());
        var codecs=List.<Memory.Codec>of(new Memory.UnknownCodec(BYTES,absent),new Memory.UnknownCodec(BYTES,foreign),
            new Memory.ExtensionCodec("opaque","1",new Types.UnknownType(absent)),
            new Memory.ExtensionCodec("labels","1",new Types.Known(new Types.LabelType(U,List.of(new LabelId(U,"absent"))))));
        var requests=codecs.stream().map(c->new PointQuery<StorageSubject>(point(),new StorageSubject.PhysicalRange(base("r"),StorageRange.exact(BigInteger.ZERO,BigInteger.ONE),c))).toList();
        var batch=execute(p).observeStorage(requests);assertEquals(4,batch.metrics().unsupportedQueries());
        var valid=new PointQuery<StorageSubject>(point(),new StorageSubject.PhysicalRange(base("r"),StorageRange.exact(BigInteger.ZERO,BigInteger.ONE),new Memory.UnknownCodec(BYTES,UNKNOWN)));
        assertEquals(ObservationBatch.QueryStatus.VALUE,execute(p).observeStorage(List.of(valid)).observations().getFirst().status());
    }
}
