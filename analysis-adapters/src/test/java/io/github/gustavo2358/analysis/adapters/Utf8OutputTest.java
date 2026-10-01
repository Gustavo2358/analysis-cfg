package io.github.gustavo2358.analysis.adapters;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class Utf8OutputTest {
    private static byte[] encode(Object value) throws IOException {
        var bytes=new ByteArrayOutputStream();var writer=new JsonOutput(bytes);writer.value(value);writer.finish();return bytes.toByteArray();
    }
    @Test void canonicalEscapingAndFieldOrderHaveLiteralOracles() throws Exception {
        var fields=new JsonOutput.Fields("z",null,"a","\"\\\b\f\n\r\t\u0000\u001f/éΩ😀");fields.put("m",true);fields.put("m",false);
        String expected="{\"a\":\"\\\"\\\\\\b\\f\\n\\r\\t\\u0000\\u001f/éΩ😀\",\"m\":false,\"z\":null}\n";
        assertArrayEquals(expected.getBytes(StandardCharsets.UTF_8),encode(fields));
        assertArrayEquals("[0,-1,2147483647,9223372036854775807]\n".getBytes(StandardCharsets.UTF_8),encode(List.of(0,-1,Integer.MAX_VALUE,Long.MAX_VALUE)));
    }
    @Test void independentParserRecoversScalarsAcrossByteBufferBoundaries() throws Exception {
        var random=new Random(75122574);var mapper=new ObjectMapper();
        for(int n=0;n<120;n++) {
            var text=new StringBuilder("x".repeat(65530+n));
            for(int i=0;i<200;i++){int cp;do{cp=random.nextInt(0x110000);}while(cp>=0xd800&&cp<=0xdfff);text.appendCodePoint(cp);}
            assertEquals(text.toString(),mapper.readTree(encode(text.toString())).asText());
        }
        for(String bad:List.of("\ud800","\udc00","prefix\ud800suffix"))assertThrows(IllegalArgumentException.class,()->encode(bad));
    }
    @Test void arraysMapDuringEmissionAndPropagateIoFailures() throws Exception {
        var mapped=new AtomicInteger();var values=IntStream.range(0,1000).mapToObj(i->{mapped.incrementAndGet();return new JsonOutput.Fields("n",i);});
        assertEquals(0,mapped.get());var bytes=encode(values);assertEquals(1000,mapped.get());
        assertEquals(999,new ObjectMapper().readTree(bytes).get(999).get("n").asInt());
        var failure=new IOException("expected");var output=new OutputStream(){@Override public void write(int b)throws IOException{throw failure;}};
        var writer=new JsonOutput(output);writer.value("x".repeat(65534));assertSame(failure,assertThrows(IOException.class,writer::finish));
    }
    @Test void validationCannotBeAttachedToAnotherSnapshot() {
        var first=ResultFixtures.publication(new io.github.gustavo2358.air.model.Ids.PublicationId("first"),List.of(),List.of());
        var other=ResultFixtures.publication(new io.github.gustavo2358.air.model.Ids.PublicationId("other"),List.of(),List.of());
        var checked=io.github.gustavo2358.air.validation.AirValidator.check(first,io.github.gustavo2358.air.validation.ValidationOptions.defaults());
        assertThrows(IllegalArgumentException.class,()->new DataflowAirReader.Read(other,1,1,"",Optional.of(checked)));
        assertThrows(IllegalArgumentException.class,()->new io.github.gustavo2358.analysis.dependencies.DependencyInput(other,Optional.empty(),List.of(),Optional.of(checked)));
    }
}
