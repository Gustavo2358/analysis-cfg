package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.validation.*;
import io.github.gustavo2358.analysis.solver.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Identity-index backend laws; caller-owned Publication input is outside the managed quota. */
final class PagedSnapshotIdentityStorageTest {
    @TempDir Path directory;
    private static AnalysisResources resources(long heap) {
        return new AnalysisResources(new AnalysisResources.Limits(heap,heap,0,64_000_000,2,100_000_000,1_000_000));
    }
    @Test void completeTypedNamespacesAndUnicodeAgreeAcrossResidentAndOnePageStores() {
        Publication input=publication("P\u0000𝄞é");
        assertEquals(ValidationResult.Status.STRUCTURALLY_VALID,AirValidator.validate(input).status());
        var resident=resources(8_000_000);var spilled=resources(32768);
        try(var memory=new MemoryPageStore(128,resident);var file=new FilePageStore(directory,128,1,spilled)) {
            assertArrayEquals(check(input,memory,resident,false),check(input,file,spilled,false));
            assertTrue(file.statistics().evictions()>0);assertEquals(0,file.statistics().livePages());
            assertTrue(spilled.heapPeak()<=32768);
        }
        assertEquals(0,resident.heapUsed());assertEquals(0,spilled.heapUsed());
        assertEquals(0,spilled.used(AnalysisResources.Pool.TEMPORARY));assertEquals(0,spilled.used(AnalysisResources.Pool.OPEN_FILES));
    }
    @Test void exactIdentityPayloadExceedsManagedResidencyAndMemoizationAvoidsRepeatedWork() {
        char[] text=new char[32769];var random=new Random(90210);
        for(int n=0;n<text.length;n++)text[n]=(char)('A'+random.nextInt(26));
        Publication input=publication(new String(text));
        assertEquals(ValidationResult.Status.STRUCTURALLY_VALID,AirValidator.validate(input).status());
        var ledger=resources(32768);
        try(var pages=new FilePageStore(directory,128,1,ledger)) {
            check(input,pages,ledger,true);
            assertTrue(ledger.heapPeak()<=32768);assertTrue(pages.statistics().evictions()>1000);
            assertEquals(0,pages.statistics().livePages());
        }
        assertEquals(0,ledger.heapUsed());assertEquals(0,ledger.used(AnalysisResources.Pool.TEMPORARY));
    }
    @Test void failedConstructionAndSpentWorkReleaseOwnedIndexesWithoutClosingBorrowedInput() {
        for(long heap:new long[]{1000,2000,4000,6000}) {
            var ledger=resources(heap);
            try(var pages=new FilePageStore(directory,128,1,ledger)) {
                long initial=ledger.heapUsed();
                var failure=assertThrows(AnalysisResources.Exhausted.class,()->new PagedSnapshotIdentityStorage(pages,ledger));
                assertEquals(AnalysisResources.Phase.VALIDATION,failure.phase());
                assertEquals(initial,ledger.heapUsed());assertEquals(0,pages.statistics().livePages());
            }
            assertEquals(0,ledger.heapUsed());
        }
        var ledger=resources(32768);
        try(var pages=new FilePageStore(directory,128,1,ledger);var snapshot=AirSnapshot.fromPublication(publication("P"))) {
            long initial=ledger.heapUsed();var keys=new SnapshotIdentityKeys(snapshot,new PagedSnapshotIdentityStorage(pages,ledger));
            long id=field(snapshot,snapshot.root(),"id");long key=keys.key(id);assertTrue(key>0);
            ledger.work(ledger.limits().workUnits()-ledger.workUsed(),AnalysisResources.Phase.VALIDATION);
            var failure=assertThrows(AnalysisResources.Exhausted.class,()->keys.key(id));
            assertEquals(AnalysisResources.Resource.WORK,failure.resource());assertEquals(AnalysisResources.Phase.VALIDATION,failure.phase());
            assertThrows(IllegalStateException.class,()->keys.key(id));assertDoesNotThrow(keys::close);assertDoesNotThrow(keys::close);
            assertEquals(initial,ledger.heapUsed());assertEquals(0,pages.statistics().livePages());
            assertEquals(AirShape.PUBLICATION,snapshot.shape(snapshot.root()));
            assertEquals(ledger.limits().workUnits(),ledger.workUsed());
        }
        assertEquals(0,ledger.heapUsed());assertEquals(0,ledger.used(AnalysisResources.Pool.TEMPORARY));
    }
    @Test void streamedAtomSummariesMatchAcrossBackendsAndSurvivePayloadLargerThanResidency() {
        var resident=resources(8_000_000);var spilled=resources(65536);
        try(var memory=new MemoryPageStore(128,resident);var file=new FilePageStore(directory,128,1,spilled)) {
            assertArrayEquals(atomSummaries(memory,resident),atomSummaries(file,spilled));
            assertTrue(spilled.heapPeak()<=65536);assertTrue(file.statistics().evictions()>0);
        }
        assertEquals(0,resident.heapUsed());assertEquals(0,spilled.heapUsed());
        assertEquals(0,spilled.used(AnalysisResources.Pool.TEMPORARY));
        assertEquals(0,spilled.used(AnalysisResources.Pool.OPEN_FILES));
    }
    private static long[] atomSummaries(PageStore pages,AnalysisResources ledger) {
        long[] result=new long[6];
        // Scalar-storage laws; this is not a certificate for unreachable atoms or a full Validator.
        try(var original=AirSnapshot.fromPublication(publication("P"));
            var builder=new AirSnapshotBuilder(new PagedAirStorage(pages,ledger,AnalysisResources.Phase.DECODE))) {
            long text;
            try(var out=builder.text(AirShape.TEXT)) {
                char[] block=new char[1024];java.util.Arrays.fill(block,'x');block[1023]='\uD83D';
                out.append(block,0,block.length);out.append(new char[]{'\uDE00'},0,1);text=out.finish();
            }
            long integer;
            try(var out=builder.text(AirShape.INTEGER)) {
                out.append(new char[]{'-','1'},0,2);char[] zeros=new char[1024];java.util.Arrays.fill(zeros,'0');
                for(int n=0;n<100_000;n+=zeros.length)out.append(zeros,0,Math.min(zeros.length,100_000-n));
                out.append(new char[]{'7'},0,1);integer=out.finish();
            }
            long root=PagedAirStorageTest.copy(original,original.root(),null,builder);
            try(var snapshot=builder.finish(root);var keys=new SnapshotIdentityKeys(snapshot,new PagedSnapshotIdentityStorage(pages,ledger))) {
                var facts=SnapshotIdentityKeys.AtomFact.values();
                result[0]=keys.atomFact(text,facts[0]);result[1]=keys.atomFact(text,facts[1]);
                for(int n=0;n<4;n++)result[n+2]=keys.atomFact(integer,facts[n]);
                assertArrayEquals(new long[]{1025,1024,100003,100003,-1,7},result);
                long before=pages.statistics().livePages(),work=ledger.workUsed();
                for(int repeat=0;repeat<64;repeat++) {
                    assertEquals(1024,keys.atomFact(text,SnapshotIdentityKeys.AtomFact.UNICODE_SCALARS));
                    for(int n=0;n<4;n++)assertEquals(result[n+2],keys.atomFact(integer,facts[n]));
                }
                assertEquals(before,pages.statistics().livePages());
                assertTrue(ledger.workUsed()-work<64L*5*256,"memo hit scanned or reinterned a huge atom");
                if(pages instanceof FilePageStore) {
                    assertTrue(ledger.used(AnalysisResources.Pool.TEMPORARY)>65536);
                    System.out.println("SNAPSHOT_ATOM_STORAGE_METRICS {\"managedHeapPeak\":"+ledger.heapPeak()+",\"temporaryBytes\":"+ledger.used(AnalysisResources.Pool.TEMPORARY)+",\"memoWork\":"+(ledger.workUsed()-work)+"}");
                }
            }
        }
        assertEquals(0,pages.statistics().livePages());return result;
    }
    @Test void tupleMetadataReadFailureAbortsPortAndKeepsBorrowedSnapshotAvailable() {
        var ledger=resources(32768);
        try(var pages=new FilePageStore(directory,128,1,ledger);var snapshot=AirSnapshot.fromPublication(publication("P"))) {
            long initial=ledger.heapUsed();var port=new PagedSnapshotIdentityStorage(pages,ledger);
            var keys=new SnapshotIdentityKeys(snapshot,port);
            try {
                long id=field(snapshot,snapshot.root(),"id"),text=field(snapshot,id,"localId");
                long key=keys.atomKey(text);
                ledger.work(ledger.limits().workUnits()-ledger.workUsed(),AnalysisResources.Phase.VALIDATION);
                var failure=assertThrows(AnalysisResources.Exhausted.class,()->port.word(key,3));
                assertEquals(AnalysisResources.Resource.WORK,failure.resource());assertEquals(AnalysisResources.Phase.VALIDATION,failure.phase());
                assertThrows(IllegalStateException.class,()->port.word(key,3));
                assertEquals(AirShape.PUBLICATION,snapshot.shape(snapshot.root()));
            } finally {keys.close();}
            assertEquals(initial,ledger.heapUsed());assertEquals(0,pages.statistics().livePages());
        }
        assertEquals(0,ledger.heapUsed());assertEquals(0,ledger.used(AnalysisResources.Pool.TEMPORARY));
    }
    @Test void hugeIntegerOrderAndWhitespaceFactsAgreeAcrossBackendsWithoutPayloadReplay() {
        var resident=resources(8_000_000);var spilled=resources(65536);
        try(var memory=new MemoryPageStore(128,resident);var file=new FilePageStore(directory,128,1,spilled)) {
            assertArrayEquals(integerOrder(memory,resident),integerOrder(file,spilled));assertTrue(spilled.heapPeak()<=65536);
        }
        assertEquals(0,resident.heapUsed());assertEquals(0,spilled.heapUsed());
        assertEquals(0,spilled.used(AnalysisResources.Pool.TEMPORARY));
    }
    private static long[] integerOrder(PageStore pages,AnalysisResources ledger) {
        try(var original=AirSnapshot.fromPublication(publication("P"));
            var builder=new AirSnapshotBuilder(new PagedAirStorage(pages,ledger,AnalysisResources.Phase.DECODE))) {
            long first=integer(builder,'7'),second=integer(builder,'8'),blank,nonblank;
            try(var out=builder.text(AirShape.TEXT)){out.append(new char[]{' ','\t','\n','\u2003'},0,4);blank=out.finish();}
            try(var out=builder.text(AirShape.TEXT)){out.append(new char[]{'\u00A0'},0,1);nonblank=out.finish();}
            long root=PagedAirStorageTest.copy(original,original.root(),null,builder);
            try(var snapshot=builder.finish(root);var keys=new SnapshotIdentityKeys(snapshot,new PagedSnapshotIdentityStorage(pages,ledger))) {
                long[] result={keys.compareIntegers(first,second),keys.compareIntegers(second,first),keys.compareIntegers(first,first),
                    keys.atomFact(blank,SnapshotIdentityKeys.AtomFact.NONBLANK),keys.atomFact(nonblank,SnapshotIdentityKeys.AtomFact.NONBLANK)};
                assertArrayEquals(new long[]{1,-1,0,0,1},result);
                long work=ledger.workUsed(),live=pages.statistics().livePages();
                for(int n=0;n<128;n++)assertEquals(1,keys.compareIntegers(first,second));
                assertEquals(live,pages.statistics().livePages());
                assertTrue(ledger.workUsed()-work<128L*16384,"comparison replayed a huge integer payload");
                if(pages instanceof FilePageStore) {
                    assertTrue(ledger.used(AnalysisResources.Pool.TEMPORARY)>65536);
                    System.out.println("SNAPSHOT_INTEGER_ORDER_STORAGE_METRICS {\"managedHeapPeak\":"+ledger.heapPeak()+",\"temporaryBytes\":"+ledger.used(AnalysisResources.Pool.TEMPORARY)+",\"queries\":128,\"work\":"+(ledger.workUsed()-work)+"}");
                }
                return result;
            }
        } finally {assertEquals(0,pages.statistics().livePages());}
    }
    private static long integer(AirSnapshotBuilder builder,char last) {
        try(var out=builder.text(AirShape.INTEGER)) {
            out.append(new char[]{'-','1'},0,2);char[] zeroes=new char[1024];java.util.Arrays.fill(zeroes,'0');
            for(int n=0;n<32766;n+=zeroes.length)out.append(zeroes,0,Math.min(zeroes.length,32766-n));
            out.append(new char[]{last},0,1);return out.finish();
        }
    }
    private static long[] check(Publication publication,PageStore pages,AnalysisResources ledger,boolean pressure) {
        long initial=ledger.heapUsed();var handles=new ArrayList<Long>();var expected=new ArrayList<Object>();long[] result;
        try(var snapshot=AirSnapshot.fromPublication(publication);var keys=new SnapshotIdentityKeys(snapshot,new PagedSnapshotIdentityStorage(pages,ledger))) {
            add(handles,expected,field(snapshot,snapshot.root(),"id"),publication.id());
            long units=field(snapshot,snapshot.root(),"units");
            for(int n=0;n<publication.units().size();n++) {
                var value=publication.units().get(n);long unit=snapshot.element(units,AirShape.UNIT,n);long id=field(snapshot,unit,"id");
                add(handles,expected,id,value.id());add(handles,expected,field(snapshot,id,"publication"),value.id().publication());
                long entry=snapshot.element(field(snapshot,unit,"entries"),AirShape.ENTRIES_ENTRY,0);
                add(handles,expected,field(snapshot,entry,"id"),value.entries().get(0).id());
                long sequence=snapshot.element(field(snapshot,unit,"sequences"),AirShape.SEQUENCE,0);
                add(handles,expected,field(snapshot,sequence,"label"),value.sequences().get(0).label());
                long operation=field(snapshot,sequence,"terminator");long header=field(snapshot,operation,"header");
                add(handles,expected,field(snapshot,header,"id"),value.sequences().get(0).terminator().header().id());
                add(handles,expected,field(snapshot,unit,"origin"),value.origin());
            }
            result=new long[handles.size()];for(int n=0;n<result.length;n++)result[n]=keys.key(handles.get(n));
            for(int a=0;a<result.length;a++)for(int b=0;b<result.length;b++)assertEquals(expected.get(a).equals(expected.get(b)),result[a]==result[b]);
            long tuples=pages.statistics().livePages();long work=ledger.workUsed();
            for(int epoch=0;epoch<3;epoch++)for(int n=0;n<result.length;n++)assertEquals(result[n],keys.key(handles.get(n)));
            assertEquals(tuples,pages.statistics().livePages());
            assertTrue(ledger.workUsed()-work<handles.size()*3L*512,"memo hit reconstructed full text namespace");
            if(pressure) {
                assertTrue(ledger.used(AnalysisResources.Pool.TEMPORARY)>ledger.limits().heapBytes());
                System.out.println("SNAPSHOT_IDENTITY_STORAGE_METRICS {\"managedHeapPeak\":"+ledger.heapPeak()
                        +",\"temporaryBytes\":"+ledger.used(AnalysisResources.Pool.TEMPORARY)
                        +",\"livePages\":"+pages.statistics().livePages()
                        +",\"evictions\":"+pages.statistics().evictions()
                        +",\"memoChecks\":"+(handles.size()*3)+",\"memoWork\":"+(ledger.workUsed()-work)+"}");
            }
        }
        assertEquals(0,pages.statistics().livePages());
        if(pages instanceof FilePageStore)assertEquals(initial,ledger.heapUsed());return result;
    }
    private static void add(List<Long> handles,List<Object> expected,long handle,Object value){handles.add(handle);expected.add(value);}
    private static long field(AirSnapshot snapshot,long record,String name) {
        AirShape shape=snapshot.shape(record);
        for(int n=0;n<shape.fieldCount();n++)if(shape.field(n).name().equals(name))return snapshot.field(record,shape,n);
        throw new AssertionError("fixture field missing: "+name);
    }
    private static Publication publication(String name) {
        var pub=new Ids.PublicationId(name);var origin=new Ids.OriginId(pub,"same");var units=new ArrayList<io.github.gustavo2358.air.model.Unit>();
        for(String local:List.of("u","v")) {
            var unit=new Ids.UnitId(new Ids.PublicationId(new String(name.toCharArray())),local);var label=new Ids.LabelId(unit,"same");
            var scope=new Scopes.UnitScope(unit);var claim=new Evidence.Claim(scope,Evidence.PrecisionStatus.EXACT,List.of());
            var precision=new Evidence.Precision(claim,claim,claim,claim,claim);
            var header=new Operations.Header(new Ids.OperationId(unit,"same"),origin,Evidence.CoverageStatus.MODELED,precision,List.of());
            var sequence=new Sequence(label,List.of(),new Operations.Halt(header,Operations.HaltKind.NORMAL),origin);
            var signature=new Interactions.Signature(new Interactions.ParameterInventory(List.of(),Interactions.NoRemainder.INSTANCE),new Interactions.ResultInventory(List.of(),Interactions.NoRemainder.INSTANCE),origin);
            var entry=new Entries.Entry(new Ids.EntryId(unit,"same"),Optional.of(label),signature,new Entries.EntryState(List.of(),List.of()),origin);
            units.add(new io.github.gustavo2358.air.model.Unit(unit,Optional.empty(),List.of(),List.of(),List.of(entry),List.of(sequence),List.of(),io.github.gustavo2358.air.model.Unit.BodyAvailability.AVAILABLE,Optional.empty(),new Evidence.Coverage(Evidence.InventoryStatus.COMPLETE,scope,List.of(),List.of()),origin));
        }
        return new Publication(pub,SemanticVersion.AIR_2_0_0,new Capabilities.Manifest(List.of(),List.of()),List.of(),units,List.of(),List.of(),List.of(),List.of(new Origins.Unavailable(origin,"synthetic")),new Evidence.Coverage(Evidence.InventoryStatus.COMPLETE,new Scopes.PublicationScope(pub),List.of(),List.of()),List.of(),List.of());
    }
}
