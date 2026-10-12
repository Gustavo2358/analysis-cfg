package io.github.gustavo2358.analysis.dependencies;

import io.github.gustavo2358.analysis.adapters.*;
import io.github.gustavo2358.analysis.dependencies.source.*;
import io.github.gustavo2358.analysis.solver.*;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

final class SourceAdmissionSnapshotTest {
    @TempDir Path directory;
    private static byte[] wire()throws Exception{return Files.readAllBytes(Path.of("src/test/resources/qualified-source-r9/ordinary.source.json"));}
    private static AnalysisResources resources(){return new AnalysisResources(new AnalysisResources.Limits(1048576,16384,0,128L*1024*1024,2,Long.MAX_VALUE,0));}
    private static void zero(AnalysisResources resources){for(var pool:AnalysisResources.Pool.values())assertEquals(0,resources.used(pool));}
    @Test void legitimateFactoryBindsAllUnitOrdinalsAndCannotBeConstructedOrClaimedTwice()throws Exception {
        for(var constructor:SourceAdmissionSnapshot.class.getDeclaredConstructors())assertTrue(Modifier.isPrivate(constructor.getModifiers()));
        var resources=resources();
        try(var pages=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.VALIDATION)) {
            var storage=new QualifiedSourceJson().decodeSnapshot(new ByteArrayInputStream(wire()),pages,resources);
            try(var admission=SourceAdmissionSnapshot.admit(storage,pages,resources)) {
                assertSame(storage,admission.snapshot());long units=storage.field(storage.root(),SourceShape.ROOT,5);
                assertEquals(storage.size(units,SourceShape.UNIT_EVIDENCE),admission.unitCount());
                for(long i=0;i<admission.unitCount();i++)assertEquals(storage.element(units,SourceShape.UNIT_EVIDENCE,i),admission.unit(i));
                assertThrows(IllegalStateException.class,()->SourceAdmissionSnapshot.admit(storage,pages,resources));
                assertSame(storage,admission.snapshot());
            }
            assertThrows(IllegalStateException.class,storage::root);assertEquals(0,pages.statistics().livePages());
            long borrowed=pages.allocate();pages.release(borrowed);
        }zero(resources);
    }
    @Test void foreignResourcesAndForeignBackendCannotStealOrCertifyTheOwner()throws Exception {
        var resources=resources();var foreign=resources();
        try(var pages=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.VALIDATION);
            var other=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.VALIDATION);
            var storage=new QualifiedSourceJson().decodeSnapshot(new ByteArrayInputStream(wire()),pages,resources)) {
            long before=pages.statistics().livePages();
            assertThrows(IllegalArgumentException.class,()->SourceAdmissionSnapshot.admit(storage,other,resources));
            assertThrows(IllegalArgumentException.class,()->SourceAdmissionSnapshot.admit(storage,pages,foreign));
            assertTrue(storage.root()>0);assertEquals(before,pages.statistics().livePages());
            try(var admission=SourceAdmissionSnapshot.admit(storage,pages,resources)){assertSame(storage,admission.snapshot());}
        }zero(resources);zero(foreign);
    }
    @Test void controlDenialAfterOwnershipClaimRetiresTheStorageBeforeAnotherQuery()throws Exception {
        var resources=resources();
        try(var pages=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.VALIDATION)) {
            var storage=new QualifiedSourceJson().decodeSnapshot(new ByteArrayInputStream(wire()),pages,resources);
            try(var pressure=resources.reserve(AnalysisResources.Pool.RESIDENT,resources.limits().heapBytes()-resources.heapUsed()-511,AnalysisResources.Phase.VALIDATION)) {
                assertNotNull(pressure);var failure=assertThrows(AnalysisResources.Exhausted.class,()->SourceAdmissionSnapshot.admit(storage,pages,resources));
                assertEquals(AnalysisResources.Resource.HEAP,failure.resource());assertEquals(AnalysisResources.Phase.VALIDATION,failure.phase());
                assertEquals(0,pages.statistics().livePages());assertThrows(IllegalStateException.class,storage::root);
            }
        }zero(resources);
    }
    @Test void explicitStorageCloseExpiresTheIssuedCertificateBeforeTheNextQuery()throws Exception {
        var resources=resources();
        try(var pages=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.VALIDATION)) {
            var storage=new QualifiedSourceJson().decodeSnapshot(new ByteArrayInputStream(wire()),pages,resources);
            try(var admission=SourceAdmissionSnapshot.admit(storage,pages,resources)) {
                storage.close();assertEquals(0,pages.statistics().livePages());
                assertThrows(IllegalStateException.class,admission::snapshot);assertThrows(IllegalStateException.class,admission::unitCount);assertThrows(IllegalStateException.class,()->admission.unit(0));
            }
        }zero(resources);
    }
    @Test void nativeCommonOwnerCloseImmediatelyExpiresStorageAndCertificate()throws Exception {
        var resources=new AnalysisResources(new AnalysisResources.Limits(64L*1024*1024,16L*1024*1024,0,256L*1024*1024,8,Long.MAX_VALUE,0));
        try(var input=new DataflowAirReader().readSnapshot(Path.of("src/test/resources/cp6/dynamic-x8.air.json"),resources);
            var pages=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.VALIDATION)) {
            var program=new SnapshotProgram(input.checked(),input.newIdentityStorage(),input.newOrderStorage(),resources);
            try {
                var execution=AnalysisResources.executionFor(program);
                var storage=new QualifiedSourceJson().decodeSnapshot(new ByteArrayInputStream(wire()),pages,execution);
                try(var admission=SourceAdmissionSnapshot.admit(storage,pages,execution)) {
                    assertTrue(admission.unitCount()>0);program.close();assertEquals(0,pages.statistics().livePages());zero(execution);
                    assertThrows(IllegalStateException.class,storage::root);assertThrows(IllegalStateException.class,admission::snapshot);assertThrows(IllegalStateException.class,admission::unitCount);
                }
            }finally{program.close();}
            long borrowed=pages.allocate();pages.release(borrowed);
        }zero(resources);
    }
    @Test void arbitrarySourcePortCannotManufactureAdmissionEvenWithoutAnObservableMethodCall()throws Exception {
        var resources=resources();
        try(var pages=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.VALIDATION);
            var storage=new QualifiedSourceJson().decodeSnapshot(new ByteArrayInputStream(wire()),pages,resources)) {
            var forged=(SourceSnapshot)java.lang.reflect.Proxy.newProxyInstance(SourceSnapshot.class.getClassLoader(),new Class<?>[]{SourceSnapshot.class},
                    (proxy,method,arguments)->{throw new AssertionError("factory queried a potentially mutable/forged port");});
            long before=pages.statistics().livePages();
            assertThrows(IllegalArgumentException.class,()->SourceAdmissionSnapshot.admit(forged,pages,resources));
            assertEquals(before,pages.statistics().livePages());assertTrue(storage.root()>0);
        }zero(resources);
    }
    @Test void semanticRejectionExpiresTheClaimedSourceWithoutLeakingPartialCertificate()throws Exception {
        var resources=resources();byte[] invalid=new String(wire(),java.nio.charset.StandardCharsets.UTF_8).replace("\"qualified-source-dependencies\"","\"not-qualified\"").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertThrows(IllegalArgumentException.class,()->new QualifiedSourceJson().decode(invalid));
        try(var pages=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.VALIDATION)) {
            var storage=new QualifiedSourceJson().decodeSnapshot(new ByteArrayInputStream(invalid),pages,resources);
            assertThrows(IllegalArgumentException.class,()->SourceAdmissionSnapshot.admit(storage,pages,resources));
            assertEquals(0,pages.statistics().livePages());assertThrows(IllegalStateException.class,storage::root);
        }zero(resources);
    }
    @Test void everyPrimitiveFaultDuringAdmissionAndReadFaultAfterAdmissionRetireAllOwners()throws Exception {
        for(int operation:new int[]{0,1,2,3}) {
            var resources=resources();
            try(var pages=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.VALIDATION)) {
                var failure=new PageStore.Failure(PageStore.Reason.IO,"independent SOURCE admission/consumer interruption");
                var port=new FaultPages(pages,failure);var storage=new QualifiedSourceJson().decodeSnapshot(new ByteArrayInputStream(wire()),port,resources);
                if(operation<3) {
                    port.arm(operation);assertSame(failure,assertThrows(PageStore.Failure.class,()->SourceAdmissionSnapshot.admit(storage,port,resources)));
                }else {
                    try(var admission=SourceAdmissionSnapshot.admit(storage,port,resources)) {
                        port.arm(1);assertSame(failure,assertThrows(PageStore.Failure.class,admission::unitCount));
                        assertThrows(IllegalStateException.class,admission::snapshot);
                    }
                }
                assertEquals(0,pages.statistics().livePages());assertThrows(IllegalStateException.class,storage::root);assertFalse(port.closed);
                long borrowed=pages.allocate();pages.release(borrowed);
            }zero(resources);
        }
    }
    private static final class FaultPages implements PageStore {
        private final PageStore delegate;private final Failure primary;private int operation=-1;private boolean closed;
        FaultPages(PageStore delegate,Failure primary){this.delegate=delegate;this.primary=primary;}
        void arm(int operation){this.operation=operation;}
        private void fail(int candidate){if(candidate==operation){operation=-1;throw primary;}}
        @Override public int pageBytes(){return delegate.pageBytes();}
        @Override public long allocate(){fail(0);return delegate.allocate();}
        @Override public void read(long p,int offset,byte[] bytes,int at,int count){fail(1);delegate.read(p,offset,bytes,at,count);}
        @Override public void write(long p,int offset,byte[] bytes,int at,int count){fail(2);delegate.write(p,offset,bytes,at,count);}
        @Override public void release(long p){delegate.release(p);}
        @Override public void readForCleanup(long p,int offset,byte[] bytes,int at,int count){delegate.readForCleanup(p,offset,bytes,at,count);}
        @Override public void releaseForCleanup(long p){delegate.releaseForCleanup(p);}
        @Override public void flush(){delegate.flush();}
        @Override public Statistics statistics(){return delegate.statistics();}
        @Override public void close(){closed=true;delegate.close();}
    }
}
