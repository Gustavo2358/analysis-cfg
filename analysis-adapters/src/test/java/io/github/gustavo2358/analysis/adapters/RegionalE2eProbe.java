package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.json.AirJson;
import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.analysis.dataflow.RegionalAnalysis;
import io.github.gustavo2358.analysis.query.*;
import io.github.gustavo2358.analysis.storage.StorageSubject;
import java.nio.file.*;
import java.util.*;

/** Test-only file driver: public RD/value product for every object at every Invoke and Opaque. */
public final class RegionalE2eProbe {
    private RegionalE2eProbe() { }
    public static void main(String[] args) throws Exception {
        if(args.length!=2)throw new IllegalArgumentException("AIR input and regional output required");
        var codec=new AirJson();var publication=codec.decode(Files.readAllBytes(Path.of(args[0])));var bytes=codec.encode(publication);
        if(!Arrays.equals(bytes,codec.encode(codec.decode(bytes))))throw new AssertionError("AIR roundtrip");
        var queries=new ArrayList<PointQuery<StorageSubject>>();
        for(var unit:publication.units())for(var entry:unit.entries())for(var sequence:unit.sequences())
            if(sequence.terminator() instanceof Operations.Invoke||sequence.terminator() instanceof Operations.Opaque)for(var object:unit.objects())
                queries.add(new PointQuery<>(ProgramPoint.before(entry.id(),sequence.terminator().header().id()),new StorageSubject.NamedObject(object.id())));
        var result=new RegionalAnalysis().prepare(publication,"storage-w5-e2e",queries);
        try(var out=Files.newOutputStream(Path.of(args[1]))) {new RegionalResultJson().write(result,out);}
    }
}
