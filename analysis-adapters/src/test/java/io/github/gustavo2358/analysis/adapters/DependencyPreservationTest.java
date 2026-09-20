package io.github.gustavo2358.analysis.adapters;
import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.dependencies.*;
import io.github.gustavo2358.analysis.values.StorageAnalysisMode;
import java.util.*;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.adapters.ResultFixtures.*;

/** Laws over actual CFG/value queries. Expected names are independent of production output. */
class DependencyPreservationTest {
    static Publication cics(Publication p,boolean file) {
        var u=p.units().getFirst();var sequences=new ArrayList<Sequence>();
        for(var s:u.sequences()) {
            var t=s.terminator();
            if(t instanceof Operations.Invoke i&&i.target() instanceof Interactions.ComputedTarget c) {
                var target=new Interactions.ComputedTarget(file?"file":"program",file?"cics.file":"cics.program",c.name(),new Interactions.ExtensionName(file?"cics-ts.file":"cics-ts.program","1"),c.origin());
                t=new Operations.Invoke(i.header(),file?"READ":"execute",target,i.arguments(),i.results(),i.signature(),i.effectOperands(),i.effectBound(),i.outcomes(),i.contract());
            }
            sequences.add(new Sequence(s.label(),s.instructions(),t,s.origin()));
        }
        return new Publication(p.id(),p.airVersion(),new Capabilities.Manifest(List.of(new Capabilities.Capability(file?"cics-ts.file":"cics-ts.program","1")),List.of()),p.artifacts(),List.of(unit(u.id(),u.entries(),sequences,u.objects())),p.storage(),p.resources(),p.artifactRelations(),p.origins(),p.coverage(),p.uncertainties(),p.premises());
    }
    static Publication linear(boolean seed,boolean overwrite) {
        return W1dModelTest.model(1,u->{var object=new ObjectId(u,"object-0");var writes=new ArrayList<Instruction>();
            if(seed)writes.add(assign(u,"seed",object,"PROGA   "));
            if(overwrite)writes.add(assign(u,"replace",object,"PROGB   "));
            return List.of(new Sequence(new LabelId(u,"start"),writes,W1dModelTest.call(u,"invoke","end",object,false),origin(u.publication())),returning(u,"end",List.of()));});
    }
    static List<String> names(DependencySiteFact f){return f.candidates().stream().map(DependencySiteFact.Candidate::referenceName).toList();}
    @Test void knownRuntimeAndOrphanPreserveOnlyReachingEvidence() {
        for(boolean open:List.of(false,true)) {
            var f=new DependencyAnalysis().prepare(cics(W2dModelTest.diamond(open,true),false)).sites().getFirst();
            assertEquals(open?List.of("PROGA"):List.of("PROGA","PROGB"),names(f));
            assertEquals(open,f.effectiveUnknownRemainder());assertFalse(f.interpretationUnknownRemainder());
            assertTrue(f.candidates().stream().flatMap(c->c.supports().stream()).noneMatch(s->s.producer().localId().equals("assign-bad")));
        }
    }
    @Test void addingRuntimeUncertaintyNeverRemovesExistingCandidate() {
        var known=new DependencyAnalysis().prepare(cics(linear(true,false),false)).sites().getFirst();
        var open=new DependencyAnalysis().prepare(cics(W2dModelTest.diamond(true,false),false)).sites().getFirst();
        assertEquals(List.of("PROGA"),names(known));assertTrue(names(open).containsAll(names(known)));assertTrue(open.modelValueRemainder());
    }
    @Test void trulyUnknownRemainsEmptyOpen() {
        var f=new DependencyAnalysis().prepare(cics(linear(false,false),false)).sites().getFirst();
        assertEquals(List.of(),names(f));assertTrue(f.effectiveUnknownRemainder());assertNotNull(f.subject());assertNotNull(f.valuePoint());
    }
    @Test void positiveOverwriteCanRemoveAnEarlierCandidate() {
        var f=new DependencyAnalysis().prepare(cics(linear(true,true),false)).sites().getFirst();
        assertEquals(List.of("PROGB"),names(f));assertEquals("replace",f.candidates().getFirst().supports().getFirst().producer().localId());
    }
    @Test void omittedPhysicalProofPreservesClosedSupportedText() {
        var weak=cics(linear(true,false),false);var u=weak.units().getFirst();var object=u.objects().getFirst();var storage=weak.storage().getFirst();
        var view=new Memory.ViewBinding(storage.header().id(),BigInteger.ZERO,BigInteger.valueOf(8),new Memory.ExtensionCodec("text.ebcdic.ibm1047","1",Types.known(Types.Builtin.TEXT)));
        var declaration=new Memory.ObjectDeclaration(object.id(),object.displayName(),object.typeRef(),view,object.visibility(),object.origin(),object.coverage(),object.precision());
        var strong=new Publication(weak.id(),weak.airVersion(),new Capabilities.Manifest(List.of(new Capabilities.Capability("cics-ts.program","1"),Capabilities.MEMORY_REGIONS,Capabilities.IBM1047),List.of()),weak.artifacts(),List.of(unit(u.id(),u.entries(),u.sequences(),List.of(declaration))),List.of(new Memory.Region(storage.header(),Optional.of(BigInteger.valueOf(8)),Optional.empty())),weak.resources(),weak.artifactRelations(),weak.origins(),weak.coverage(),weak.uncertainties(),weak.premises());
        var precise=new DependencyAnalysis(StorageAnalysisMode.EXPERIMENTAL_PHYSICAL).prepare(strong).sites().getFirst();
        var partial=new DependencyAnalysis().prepare(weak).sites().getFirst();
        assertEquals(List.of("PROGA"),names(precise));assertFalse(precise.effectiveUnknownRemainder());
        assertEquals(names(precise),names(partial));assertFalse(partial.effectiveUnknownRemainder());
    }
    @Test void fileConsumerAlsoPreservesKnownPlusRuntime() {
        for(boolean open:List.of(false,true)) {
            var r=new DependencyAnalysis().prepare(cics(W2dModelTest.diamond(open,true),true));
            var f=r.fileDependencies().sites().getFirst();
            assertEquals(open?List.of("PROGA"):List.of("PROGA","PROGB"),f.candidates().stream().map(FileDependencyResult.Candidate::referenceName).toList());
            assertTrue(f.unknownRemainder());assertNotNull(f.valuePoint());
        }
    }
    private static Publication lowerProducedRedefines() throws Exception {
        try(var in=DependencyPreservationTest.class.getResourceAsStream("/dependency-preservation/redefines-unknown.air.json")) {
            return new io.github.gustavo2358.air.json.AirJson().decode(in.readAllBytes());
        }
    }
    @Test void lowerAreaUncertaintyReachesSiteAndWire() throws Exception {
        var p=lowerProducedRedefines();
        var gap=p.uncertainties().stream().filter(u->u.code().equals("cobol-lower:CICS_PHYSICAL_NAME_AREA_UNPROVEN")).findFirst().orElseThrow();
        var result=new DependencyAnalysis().prepare(p);var site=result.sites().getFirst();
        assertTrue(site.uncertaintyRefs().contains(gap.id()),"specific physical-area cause must reach site");
        assertEquals(List.of("PROGA"),names(site));assertTrue(site.effectiveUnknownRemainder());
        var bytes=new java.io.ByteArrayOutputStream();new DependencyJson().write(result,bytes);
        var json=bytes.toString(java.nio.charset.StandardCharsets.UTF_8);
        int start=json.indexOf("\"uncertaintyRefs\"");assertTrue(start>=0);
        var refs=json.substring(start,json.indexOf(']',start)+1);
        assertTrue(refs.contains(gap.id().localId()),"specific ref must survive dependency JSON");
    }
    @Test void redefinesUnknownBindingCannotInventAliasOverwrite() throws Exception {
        var p=lowerProducedRedefines();
        var objects=p.units().getFirst().objects().stream().filter(o->o.displayName().filter(n->n.equals("A")||n.equals("B")).isPresent()).toList();
        assertEquals(2,objects.size());assertNotEquals(objects.get(0).id(),objects.get(1).id());
        assertTrue(objects.stream().allMatch(o->o.storage() instanceof Memory.UnknownBinding));
        var site=new DependencyAnalysis().prepare(p).sites().getFirst();
        assertEquals(List.of("PROGA"),names(site));assertTrue(site.modelValueRemainder());
        assertFalse(site.interpretationUnknownRemainder());assertTrue(site.effectiveUnknownRemainder());
    }

}
