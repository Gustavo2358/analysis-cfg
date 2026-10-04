package io.github.gustavo2358.analysis.values;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class LogicalTextTest {
 @Test void billionCharacterRunsRemainCompressedAndSlicedExactly(){
  assertDoesNotThrow(()->{
   var unknown=LogicalText.unknown(1000000000);
   var known=LogicalText.of("PROGA").fit(1000000000,' ');
   assertFalse(unknown.complete());assertTrue(known.complete());
   assertEquals("PROGA   ",known.slice(0,8).text());
   assertEquals("    ",known.slice(999999996,4).text());
   assertEquals(known,LogicalText.of("PROGA ").fit(1000000000,' '));
   assertEquals(known.hashCode(),LogicalText.of("PROGA ").fit(1000000000,' ').hashCode());
   assertEquals("PROGA",unknown.slice(0,4).concat(known.slice(0,5)).slice(4,5).text());
  });
 }

 @Test void compressedOperationsAgreeWithDenseUnicodeOracle(){
  for(String source:java.util.List.of("", "aaaa", "a😀😀βbb", "ABCD", "😀")) {
   var points=source.codePoints().toArray();
   for(int width=0;width<12;width++) {
    var dense=java.util.Arrays.copyOf(points,width);if(width>points.length)java.util.Arrays.fill(dense,points.length,width,' ');
    var fitted=LogicalText.of(source).fit(width,' ');
    assertEquals(new String(dense,0,dense.length),fitted.text());
    for(int start=0;start<=width;start++)for(int size=0;size<=width-start;size++) {
     var expected=new String(dense,start,size);var actual=fitted.slice(start,size);
     assertEquals(expected,actual.text());assertEquals(LogicalText.of(expected),actual);
     assertEquals(LogicalText.of(expected).hashCode(),actual.hashCode());
    }
   }
  }
 }
 @Test void unknownSiblingDoesNotEraseKnownProjection(){
  var t=LogicalText.unknown(4).concat(LogicalText.of("PROGA   "));
  assertFalse(t.complete());assertEquals("PROGA   ",t.slice(4,8).text());assertThrows(IllegalStateException.class,t::text);
 }
 @Test void immutableSnapshotAndFit(){
  var source=LogicalText.of("ABCDEFGH");var copy=source.slice(0,4);
  var changed=LogicalText.of("ZZZ").concat(source.slice(3,5));
  assertEquals("ABCD",copy.text());assertEquals("ZZZDEFGH",changed.text());assertEquals("ABCD  ",copy.fit(6,' ').text());assertEquals("ABC",copy.fit(3,' ').text());
 }
}
