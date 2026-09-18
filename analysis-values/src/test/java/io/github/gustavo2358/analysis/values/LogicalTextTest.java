package io.github.gustavo2358.analysis.values;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class LogicalTextTest {
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
