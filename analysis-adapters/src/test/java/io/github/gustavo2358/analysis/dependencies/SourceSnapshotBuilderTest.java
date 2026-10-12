package io.github.gustavo2358.analysis.dependencies;

import io.github.gustavo2358.analysis.dependencies.source.*;
import io.github.gustavo2358.analysis.adapters.FilePageStore;
import io.github.gustavo2358.analysis.adapters.DataflowAirReader;
import io.github.gustavo2358.analysis.solver.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

final class SourceSnapshotBuilderTest {
    @TempDir Path directory;
    private static AnalysisResources resources(){return new AnalysisResources(new AnalysisResources.Limits(65536,16384,0,128L*1024*1024,2,Long.MAX_VALUE,0));}
    private static void zero(AnalysisResources r){for(var pool:AnalysisResources.Pool.values())assertEquals(0,r.used(pool));assertEquals(0,r.heapUsed());}
    private static long list(SourceSnapshotBuilder b,SourceShape type,long... items){long list=b.beginList(type);for(long item:items)b.append(list,item);return b.finishList(list);}
    private static long unit(SourceSnapshotBuilder b,long statements) {
        long id=b.record(SourceShape.UNIT_ID,new long[]{b.text("compilation"),list(b,SourceShape.INTEGER,b.integer(0),b.integer(1)),b.text("SYNTHETIC")});
        return b.record(SourceShape.UNIT_EVIDENCE,new long[]{id,b.bool(true),statements,list(b,SourceShape.OCCURRENCE),list(b,SourceShape.TARGET),list(b,SourceShape.NODE),list(b,SourceShape.DERIVATION),list(b,SourceShape.SELECTION),list(b,SourceShape.EVENT),list(b,SourceShape.GUARD),list(b,SourceShape.PROOF),list(b,SourceShape.FRONTIER),0,0});
    }
    private static long root(SourceSnapshotBuilder b,long statements) {
        long document=b.record(SourceShape.DOCUMENT,new long[]{b.text("cobol-semantic-product"),b.text("2.67.0"),b.text("0".repeat(64))});
        long air=b.record(SourceShape.AIR_CORRELATION,new long[]{b.text("synthetic-publication"),b.text("1".repeat(64))});
        return b.record(SourceShape.ROOT,new long[]{b.text("qualified-source-dependencies"),b.text("1.6.0"),b.text("synthetic"),document,list(b,SourceShape.AIR_CORRELATION,air),list(b,SourceShape.UNIT_EVIDENCE,unit(b,statements))});
    }
    private static long statement(SourceSnapshotBuilder b,String text,int row) {
        long uid=b.record(SourceShape.UNIT_ID,new long[]{b.text("compilation"),list(b,SourceShape.INTEGER,b.integer(0),b.integer(1)),b.text("SYNTHETIC")});
        long sid=b.record(SourceShape.STATEMENT_ID,new long[]{uid,b.text("s"+row)});
        long location=b.record(SourceShape.LOCATION,new long[]{b.text(text),b.integer(row+1),b.integer(row%7),b.integer(row+2),b.integer(row%13)});
        long include=b.record(SourceShape.INCLUDE,new long[]{b.text("outer\u0000"),b.text("request\uD800"),b.text("included😀\uDC00"),b.integer(17)});
        long provenance=b.record(SourceShape.PROVENANCE,new long[]{location,location,list(b,SourceShape.INCLUDE,include,include),b.bool((row&1)==0)});
        return b.record(SourceShape.STATEMENT,new long[]{sid,provenance});
    }
    @Test void completeMetadataAndOccurrencesSpillWithFixedResidentControls() {
        for(int n:new int[]{1,4,16,64,256,8192}) {
            var r=resources();
            try(var pages=new FilePageStore(directory,512,8,r,AnalysisResources.Phase.INDEX);var b=new SourceSnapshotBuilder(pages,r)) {
                long rows=b.beginList(SourceShape.STATEMENT);long fixed=r.heapUsed();
                for(int row=0;row<n;row++)b.append(rows,statement(b,path(n,row),row));
                assertEquals(fixed,r.heapUsed());long completed=b.finishList(rows);
                try(var snapshot=b.build(root(b,completed))) {
                    assertThrows(IllegalStateException.class,()->b.text("after-transfer"));
                    long units=snapshot.field(snapshot.root(),SourceShape.ROOT,5),u=snapshot.element(units,SourceShape.UNIT_EVIDENCE,0);
                    long statements=snapshot.field(u,SourceShape.UNIT_EVIDENCE,2);assertEquals(n,snapshot.size(statements,SourceShape.STATEMENT));
                    for(int row=n-1;row>=0;row--) {
                        long s=snapshot.element(statements,SourceShape.STATEMENT,row),provenance=snapshot.field(s,SourceShape.STATEMENT,1);
                        long location=snapshot.field(provenance,SourceShape.PROVENANCE,0);assertEquals(location,snapshot.field(provenance,SourceShape.PROVENANCE,1));
                        try(var text=snapshot.project(snapshot.field(location,SourceShape.LOCATION,0))) {assertEquals(path(n,row),text.value());}
                        assertEquals(row+1,snapshot.integer(snapshot.field(location,SourceShape.LOCATION,1)));assertEquals(row%7,snapshot.integer(snapshot.field(location,SourceShape.LOCATION,2)));assertEquals(row+2,snapshot.integer(snapshot.field(location,SourceShape.LOCATION,3)));assertEquals(row%13,snapshot.integer(snapshot.field(location,SourceShape.LOCATION,4)));
                        assertEquals((row&1)==0,snapshot.bool(snapshot.field(provenance,SourceShape.PROVENANCE,3)));
                        long chain=snapshot.field(provenance,SourceShape.PROVENANCE,2);assertEquals(2,snapshot.size(chain,SourceShape.INCLUDE));assertEquals(snapshot.element(chain,SourceShape.INCLUDE,0),snapshot.element(chain,SourceShape.INCLUDE,1));
                        long include=snapshot.element(chain,SourceShape.INCLUDE,1);
                        for(int field=0;field<3;field++)try(var text=snapshot.project(snapshot.field(include,SourceShape.INCLUDE,field))) {assertEquals(switch(field){case 0->"outer\u0000";case 1->"request\uD800";case 2->"included😀\uDC00";default->throw new AssertionError();},text.value());}
                        assertEquals(17,snapshot.integer(snapshot.field(include,SourceShape.INCLUDE,3)));
                    }
                    assertTrue(r.heapPeak()<=65536);if(n>=256)assertTrue(r.peak(AnalysisResources.Pool.TEMPORARY)>65536);
                    System.out.println("SOURCE_SNAPSHOT rows="+n+" fixed="+fixed+" heapPeak="+r.heapPeak()+" temporary="+r.peak(AnalysisResources.Pool.TEMPORARY)+" bytesRead="+pages.statistics().bytesRead());
                }
                assertEquals(0,pages.statistics().livePages());long borrowed=pages.allocate();pages.release(borrowed);
            }
            zero(r);
        }
    }
    @Test void canonicalListsPreserveDuplicateOrdinalsAndFullElementNamespace() {
        var r=resources();
        try(var pages=new FilePageStore(directory,512,8,r,AnalysisResources.Phase.INDEX);var b=new SourceSnapshotBuilder(pages,r)) {
            long a=b.text("Aa"),bb=b.text("BB");assertNotEquals(a,bb);assertEquals(a,b.text("Aa"));
            long first=b.beginList(SourceShape.TEXT),second=b.beginList(SourceShape.TEXT);
            for(int i=0;i<257;i++){b.append(first,(i&1)==0?a:bb);b.append(second,(i&1)==0?a:bb);}
            long x=b.finishList(first),y=b.finishList(second);assertEquals(x,y);
            try(var snapshot=b.build(root(b,list(b,SourceShape.STATEMENT)))) {
                assertEquals(257,snapshot.size(x,SourceShape.TEXT));
                for(int i=0;i<257;i++)assertTrue(snapshot.matches(snapshot.element(x,SourceShape.TEXT,i),(i&1)==0?"Aa":"BB"));
                assertThrows(IllegalArgumentException.class,()->snapshot.size(x,SourceShape.INTEGER));
            }
        }
        zero(r);
    }
    @Test void projectionsRemainFundedUntilScopeCloseAndOwnerExpiry() {
        var r=resources();
        try(var pages=new FilePageStore(directory,512,8,r,AnalysisResources.Phase.INDEX);var b=new SourceSnapshotBuilder(pages,r)) {
            long rows=list(b,SourceShape.STATEMENT,statement(b,"A😀\uD800",0));var snapshot=b.build(root(b,rows));
            long u=snapshot.element(snapshot.field(snapshot.root(),SourceShape.ROOT,5),SourceShape.UNIT_EVIDENCE,0);
            long s=snapshot.element(snapshot.field(u,SourceShape.UNIT_EVIDENCE,2),SourceShape.STATEMENT,0),p=snapshot.field(s,SourceShape.STATEMENT,1),loc=snapshot.field(p,SourceShape.PROVENANCE,0),text=snapshot.field(loc,SourceShape.LOCATION,0);
            long before=r.heapUsed();var a=snapshot.project(text);long one=r.heapUsed();var other=snapshot.project(text);assertTrue(one>before);assertEquals(2*(one-before),r.heapUsed()-before);
            a.close();a.close();assertEquals(one,r.heapUsed());assertEquals("A😀\uD800",other.value());assertThrows(IllegalStateException.class,a::value);
            snapshot.close();snapshot.close();assertThrows(IllegalStateException.class,other::value);other.close();assertThrows(IllegalStateException.class,snapshot::root);assertEquals(0,pages.statistics().livePages());
        }
        zero(r);
    }
    @Test void wrongShapesAndIncompleteConstructionNeverBecomeSuccessfulStorage() {
        var r=resources();
        try(var pages=new FilePageStore(directory,512,8,r,AnalysisResources.Phase.INDEX);var b=new SourceSnapshotBuilder(pages,r)) {
            long empty=list(b,SourceShape.STATEMENT);long root=root(b,empty);
            long unfinished=b.beginList(SourceShape.TEXT);b.append(unfinished,b.text("pending"));
            assertThrows(IllegalStateException.class,()->b.build(root));
        }
        zero(r);
        var r2=resources();
        try(var pages=new FilePageStore(directory,512,8,r2,AnalysisResources.Phase.INDEX);var b=new SourceSnapshotBuilder(pages,r2)) {
            assertThrows(IllegalArgumentException.class,()->b.record(SourceShape.LOCATION,new long[]{b.integer(1),b.integer(1),b.integer(0),b.integer(1),b.integer(0)}));
            assertThrows(IllegalStateException.class,()->b.text("after-poison"));
        }
        zero(r2);
    }
    @Test void allThirtyFieldsResolveRealFingerprintCollisionAndSealRejectsMutation() {
        var r=resources();try(var pages=new FilePageStore(directory,512,8,r,AnalysisResources.Phase.INDEX);var rows=new SourceRecordStore(pages,r)) {
            long[] first=new long[30],second=new long[30];for(int i=0;i<30;i++)first[i]=second[i]=(i+1)*37L;
            // Independently found by FindThirtyFieldCollision: equal -2008668112 fingerprints.
            first[29]=39176;second[29]=70428;
            assertEquals(-2008668112,fingerprint(11,first));assertEquals(fingerprint(11,first),fingerprint(11,second));
            long a=rows.intern(11,first),b=rows.intern(11,second);assertNotEquals(a,b);assertEquals(a,rows.intern(11,first));assertEquals(b,rows.intern(11,second));
            for(int i=0;i<30;i++){assertEquals(first[i],rows.field(a,i+1));assertEquals(second[i],rows.field(b,i+1));}
            rows.seal();assertThrows(IllegalStateException.class,()->rows.intern(11,first));
            assertEquals(39176,rows.field(a,30));assertEquals(70428,rows.field(b,30));
        }zero(r);
    }
    private static int fingerprint(long tag,long[] fields) {
        long hash=0x9e3779b97f4a7c15L;
        for(int i=0;i<=fields.length;i++){long value=i==0?tag:fields[i-1];value^=value>>>33;value*=0xff51afd7ed558ccdL;value^=value>>>33;hash=Long.rotateLeft(hash,11)^value;}
        return (int)(hash^(hash>>>32));
    }
    @Test void ordinalLookupKeepsOrderAtEveryLeafAndCarryBoundary() {
        var r=resources();try(var pages=new FilePageStore(directory,512,8,r,AnalysisResources.Phase.INDEX);var b=new SourceSnapshotBuilder(pages,r)) {
            int[] counts={0,1,15,16,17,255,256,257,4095,4096,4097};long[] lists=new long[counts.length];long a=b.text("Aa"),bb=b.text("BB");
            long fixed=r.heapUsed();
            for(int at=0;at<counts.length;at++){long list=b.beginList(SourceShape.TEXT);for(int i=0;i<counts[at];i++)b.append(list,i%3==0?bb:a);lists[at]=b.finishList(list);}
            assertEquals(fixed,r.heapUsed());
            try(var snapshot=b.build(root(b,list(b,SourceShape.STATEMENT)))) {
                for(int at=0;at<counts.length;at++){assertEquals(counts[at],snapshot.size(lists[at],SourceShape.TEXT));for(int i=counts[at]-1;i>=0;i--)assertEquals(i%3==0?bb:a,snapshot.element(lists[at],SourceShape.TEXT,i));}
                long before=r.heapUsed();for(int at=0;at<counts.length;at++)if(counts[at]>0)snapshot.element(lists[at],SourceShape.TEXT,counts[at]-1);assertEquals(before,r.heapUsed());
            }
        }zero(r);
    }
    @Test void nativeCommonOwnerExpiryRetiresConstructionAndTransferredLiveProjections() throws Exception {
        for(boolean transferred:new boolean[]{false,true}) {
            var r=new AnalysisResources(new AnalysisResources.Limits(64L*1024*1024,16L*1024*1024,0,256L*1024*1024,8,Long.MAX_VALUE,0));
            try(var input=new DataflowAirReader().readSnapshot(Path.of("src/test/resources/cp6/dynamic-x8.air.json"),r);
                var pages=new FilePageStore(directory,512,8,r,AnalysisResources.Phase.INDEX)) {
                var program=new SnapshotProgram(input.checked(),input.newIdentityStorage(),input.newOrderStorage(),r);
                try {
                    var execution=AnalysisResources.executionFor(program);var builder=new SourceSnapshotBuilder(pages,execution);
                    try {
                        long text=builder.text("live\uD800😀"),pending=builder.beginList(SourceShape.TEXT);builder.append(pending,text);
                        if(transferred) {
                            builder.finishList(pending);
                            try(var snapshot=builder.build(root(builder,list(builder,SourceShape.STATEMENT)));var projection=snapshot.project(text)) {
                                builder.close();assertEquals("live\uD800😀",projection.value());
                                program.close();assertThrows(IllegalStateException.class,projection::value);assertThrows(IllegalStateException.class,snapshot::root);
                                assertEquals(0,pages.statistics().livePages());zero(execution);
                            }
                        } else {
                            program.close();assertEquals(0,pages.statistics().livePages());zero(execution);
                            assertThrows(IllegalStateException.class,()->builder.text("expired"));
                        }
                    } finally {builder.close();}
                } finally {program.close();}
                long page=pages.allocate();pages.release(page); // SOURCE never closes the borrowed backend.
            }zero(r);
        }
    }
    private static String path(int n,int row){return "synthetic-"+row+"-"+"x".repeat(1024)+"-"+(n-row)+".cbl";}
}
