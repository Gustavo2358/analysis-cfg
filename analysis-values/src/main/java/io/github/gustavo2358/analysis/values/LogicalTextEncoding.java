package io.github.gustavo2358.analysis.values;
import io.github.gustavo2358.air.model.*;
import java.math.BigInteger;
import java.util.*;
/** Encode the proved one-byte repertoire once per run, preserving compressed extents. */
final class LogicalTextEncoding {
    private LogicalTextEncoding() { }
    static Optional<ByteImage> encode(LogicalText text,Memory.Codec codec,BigInteger extent,int producer) {
        if(!text.complete()||!extent.equals(BigInteger.valueOf(text.length()))||!(MemoryCodecs.isIbm1047(codec)||codec instanceof Memory.AsciiText))return Optional.empty();
        var runs=new ArrayList<ByteImage.Repeat>();
        for(var run:text.runs()) {
            var scalar=new Values.TextValue(new String(Character.toChars(run.scalar())));
            var encoded=MemoryCodecs.encodeText(codec,scalar,BigInteger.ONE);
            if(encoded.status()!=MemoryCodecs.Status.EXACT)return Optional.empty();
            runs.add(new ByteImage.Repeat(encoded.value().orElseThrow().octets().getFirst(),run.length()));
        }
        return Optional.of(ByteImage.repeated(runs,producer));
    }
}
