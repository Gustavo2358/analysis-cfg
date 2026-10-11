package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.dependencies.*;
import io.github.gustavo2358.analysis.solver.*;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.*;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Compare borrowed identity reads with unchanged typed compatibility contracts;
 * borrowed fixture/expected Strings/streams are caller-owned,not provider proof. */
final class ManagedSupportIdentityWireTest {
    @TempDir Path directory;
    private static DependencySiteFact all()throws Exception {
        var method=PagedDependencyFactStorageTest.class.getDeclaredMethod("fact",int.class);method.setAccessible(true);return (DependencySiteFact)method.invoke(null,0);
    }
    private static DependencySiteFact wide(int count,int width)throws Exception {
        var method=PagedDependencySupportCursorTest.class.getDeclaredMethod("fact",int.class,int.class);method.setAccessible(true);return (DependencySiteFact)method.invoke(null,count,width);
    }
    private static AnalysisResources resources(){return new AnalysisResources(new AnalysisResources.Limits(650_000,8_000_000,0,256_000_000,4,Long.MAX_VALUE,Long.MAX_VALUE));}
    private static void wire(PagedDependencyFactStorage.IdentityView view,Id expected)throws Exception {
        var old=new ByteArrayOutputStream();var original=new JsonOutput(old);original.value(WireIds.id(expected));original.finish();
        var actual=new ByteArrayOutputStream();var cursor=new JsonOutput(actual);cursor.identity(view,new char[7]);cursor.finish();assertArrayEquals(old.toByteArray(),actual.toByteArray());
    }
    @Test void everyExistingWireIdentityRetainsExactBytesAndUnsupportedSealedIdsStayRejectedWithLargeUtf16Blocks()throws Exception {
        var expected=all();var resources=resources();
        try(var pages=new FilePageStore(directory,256,1,resources);var storage=new PagedDependencyFactStorage(pages,resources)) {
            storage.append(expected);int admitted=0,rejected=0;
            for(int i=0,n=expected.candidates().getFirst().supports().size();i<n;i++)try(var scope=storage.openSupport(0,0,i);var producer=scope.producer()){
                Id id=expected.candidates().getFirst().supports().get(i).producer();
                try{WireIds.id(id);}catch(IllegalArgumentException unsupported){
                    var encoded=new JsonOutput(new ByteArrayOutputStream());var failure=assertThrows(IllegalArgumentException.class,()->encoded.identity(producer,new char[7]));assertEquals(unsupported.getMessage(),failure.getMessage());rejected++;continue;
                }wire(producer,id);admitted++;assertTrue(resources.used(AnalysisResources.Pool.SCRATCH)<=1024);
            }assertEquals(17,admitted+rejected);assertTrue(admitted>0);assertTrue(rejected>0);
            var value=wide(1,16384);storage.append(value);
            try(var scope=storage.openSupport(1,0,0);var premise=scope.premise(0)){wire(premise,value.candidates().getFirst().supports().getFirst().premises().getFirst());assertTrue(resources.used(AnalysisResources.Pool.SCRATCH)<=1024);}
        }for(var pool:AnalysisResources.Pool.values())assertEquals(0,resources.used(pool));
    }
    @Test void blockComparatorRetainsEverySealedFullIdentityAndRepeatedCollidingPremiseOrder()throws Exception {
        var expected=all();var resources=resources();
        var block=PagedDependencyFactStorage.class.getDeclaredMethod("compareIdentity",PagedDependencyFactStorage.IdentityView.class,PagedDependencyFactStorage.IdentityView.class,char[].class,char[].class);block.setAccessible(true);
        // Unchanged typed resident compatibility comparator is an independent
        // implementation of the published order,not a regenerated paged oracle.
        var typed=SourceProgramSnapshot.class.getDeclaredMethod("compareId",Id.class,Id.class);typed.setAccessible(true);char[] left=new char[7],right=new char[11];
        try(var pages=new FilePageStore(directory,256,1,resources);var storage=new PagedDependencyFactStorage(pages,resources)) {
            storage.append(expected);var supports=expected.candidates().getFirst().supports();
            for(int a=0;a<supports.size();a++)for(int b=0;b<supports.size();b++)try(var x=storage.openSupport(0,0,a);var y=storage.openSupport(0,0,b);var first=x.producer();var second=y.producer()) {
                int wanted=(Integer)typed.invoke(null,supports.get(a).producer(),supports.get(b).producer());int actual=(Integer)block.invoke(null,first,second,left,right);assertEquals(Integer.signum(wanted),Integer.signum(actual));assertTrue(resources.used(AnalysisResources.Pool.SCRATCH)<=2048);
            }
            var value=wide(9,1024);storage.append(value);var premises=value.candidates().getFirst().supports().getFirst().premises();assertEquals("Aa".hashCode(),"BB".hashCode());
            try(var scope=storage.openSupport(1,0,0)){for(int a=0;a<premises.size();a++)for(int b=0;b<premises.size();b++)try(var first=scope.premise(a);var second=scope.premise(b)){
                int wanted=(Integer)typed.invoke(null,premises.get(a),premises.get(b));int actual=(Integer)block.invoke(null,first,second,left,right);assertEquals(Integer.signum(wanted),Integer.signum(actual));assertTrue(resources.used(AnalysisResources.Pool.SCRATCH)<=1536);
            }}
        }for(var pool:AnalysisResources.Pool.values())assertEquals(0,resources.used(pool));
    }
}
