package io.github.gustavo2358.analysis.launcher;
import io.github.gustavo2358.air.json.AirJson;
import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.analysis.adapters.*;
import io.github.gustavo2358.analysis.dependencies.*;
import io.github.gustavo2358.analysis.query.ProgramPoint;
import java.util.*;
import java.nio.file.*;
import java.io.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Existing regional solver observes canonical choice bytes BEFORE, preserving supports and open domain. */
class PhysicalChoiceTargetTest {
 @TempDir Path dir;
 @Test void physicalAlternativesSurviveMemoryAndCliWithoutResurrectingMustValues() throws Exception {
  var expected=Map.of("ambiguous",List.of("PROGA","PROGB"),"ambiguous-must",List.of("PROGC"),"ambiguous-mixed",List.of("PROGA"));
  for(var name:List.of("ambiguous","ambiguous-must","ambiguous-mixed")) {
   byte[] bytes;try(var in=getClass().getResourceAsStream("/recall/"+name+".air.json")){bytes=Objects.requireNonNull(in).readAllBytes();}
   var p=new AirJson().decode(bytes);var logical=new DependencyAnalysis().prepare(p);
   assertEquals(0L,logical.metrics().get("physicalGroupsApplied"));assertTrue(logical.sites().getFirst().effectiveUnknownRemainder());
   var result=new DependencyAnalysis(io.github.gustavo2358.analysis.values.StorageAnalysisMode.EXPERIMENTAL_PHYSICAL).prepare(p);var site=result.sites().getFirst();
   assertEquals(expected.get(name),site.candidates().stream().map(DependencySiteFact.Candidate::referenceName).toList());
   assertTrue(site.effectiveUnknownRemainder());assertTrue(site.interpretationUnknownRemainder());
   assertTrue(p.uncertainties().stream().filter(g->g.code().equals("TYPE_UNKNOWN")).anyMatch(g->site.uncertaintyRefs().contains(g.id())));
   assertEquals(ProgramPoint.Kind.BEFORE,site.valuePoint().kind());
   assertTrue(site.candidates().stream().allMatch(candidate->!candidate.supports().isEmpty()));
   var path=dir.resolve(name+".json");var output=dir.resolve(name+"-dependencies.json");Files.write(path,bytes);
   var errors=new ByteArrayOutputStream();assertEquals(0,AnalysisDependencies.run(new String[]{path.toString(),output.toString(),"--experimental-physical"},new PrintStream(errors)),errors.toString());
   var memory=new ByteArrayOutputStream();new DependencyJson().write(result,memory);assertArrayEquals(memory.toByteArray(),Files.readAllBytes(output));
   if(name.equals("ambiguous")) {
    var invoke=(Operations.Invoke)p.units().getFirst().sequences().stream().map(Sequence::terminator).filter(Operations.Invoke.class::isInstance).findFirst().orElseThrow();
    var place=((Expressions.Read)((Interactions.ComputedTarget)invoke.target()).name()).place();
    var query=new io.github.gustavo2358.analysis.query.PointQuery<io.github.gustavo2358.analysis.storage.StorageSubject>(site.valuePoint(),new io.github.gustavo2358.analysis.storage.StorageSubject.PlaceOccurrence(place.header().id()));
    var regional=new io.github.gustavo2358.analysis.dataflow.RegionalAnalysis(io.github.gustavo2358.analysis.values.StorageAnalysisMode.EXPERIMENTAL_PHYSICAL).prepare(p,"choice-query",List.of(query));
    var wire=new ByteArrayOutputStream();new RegionalResultJson().write(regional,wire);
    Files.write(Path.of("target/recall-choice-result.json"),wire.toByteArray());
   }
  }
 }
}
