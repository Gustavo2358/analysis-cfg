package io.github.gustavo2358.analysis.values;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.query.*;
import io.github.gustavo2358.analysis.storage.*;
import java.math.BigInteger;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.values.ValuesFixtures.*;
import static io.github.gustavo2358.analysis.values.RegionalValuesTest.*;
import static io.github.gustavo2358.analysis.values.RegionalCompositionTest.*;
import static io.github.gustavo2358.analysis.values.RegionalInitialTest.seed;

/** The relation compares semantic bytes/supports, not incidental inventory IDs or span partitions. */
class RegionalMetamorphicTest {
    record Meaning(List<Values.TextValue> candidates,boolean model,boolean source,boolean effective,
                   Set<String> contributions,Set<String> supports) { }
    static String definitionId(io.github.gustavo2358.analysis.rd.DefinitionEvent d) {
        return d.operation().map(OperationId::localId).orElseGet(()->d.destination().orElseThrow().localId());
    }
    static Meaning meaning(Publication p,ObjectId object) {
        var q=new PointQuery<StorageSubject>(ProgramPoint.before(new EntryId(U,"entry"),new OperationId(U,"return-exit")),new StorageSubject.NamedObject(object));
        var fact=run(p).observeStorage(List.of(q)).observations().getFirst().value();
        var contributions=new TreeSet<String>();var supports=new TreeSet<String>();
        fact.candidateSupports().forEach(s->s.producers().forEach(v->supports.add(s.candidate().value()+":"+v.evidence().localId())));
        for(var a:fact.alternatives())for(var f:a.fragments()) {
            var range=f.location().location().range().orElseThrow();
            // Eight-byte oracle only: expand the small observed range to disregard legitimate fragmentation differences.
            for(int i=0;i<range.end().orElseThrow().subtract(range.start()).intValueExact();i++) {
                var delta=BigInteger.valueOf(i);var offset=range.start().add(delta);
                var producer=f.producer().map(v->definitionId(v.definition())+"@"+v.contributedRange().location().range().orElseThrow().start().add(delta)).orElse("unknown");
                var captures=new TreeSet<String>();
                for(var c:f.captures())captures.add(definitionId(c.definition())+"@"+c.sourceContribution().location().range().orElseThrow().start().add(delta)+">"+c.destinationContribution().location().range().orElseThrow().start().add(delta));
                contributions.add(a.candidate()+":"+offset+":"+f.bytes().map(b->b.octets().get(offset.subtract(range.start()).intValueExact())).orElse(-1)+":"+producer+":"+captures+":"+f.modelReasons());
            }
        }
        return new Meaning(fact.candidates(),fact.modelValueRemainder(),fact.sourceUnknownRemainder(),fact.effectiveUnknownRemainder(),contributions,supports);
    }
    static Publication matrix(boolean unknownBranch) {
        var p=twoBases(List.of(branch(U,"s0","a","b"),
            with(jump(U,"a","join"),assign(U,"prefix-a",PREFIX,"ABCD")),
            with(jump(U,"b","join"),assign(U,"prefix-b",PREFIX,unknownBranch?"WXYZ":"ABCD")),
            with(jump(U,"join","head"),copy("copy",R,0,Y,0,8)),
            branch(U,"head","loop","exit"),with(jump(U,"loop","head"),assign(U,"later-source",WHOLE,"XXXXXXXX")),returning(U,"exit",List.of())));
        var u=p.units().getFirst();var e=u.entries().getFirst();
        var entry=new Entries.Entry(e.id(),e.initialLabel(),e.signature(),new Entries.EntryState(List.of(seed("initial-whole",0,"ABCDEFGH"),seed("initial-suffix",4,"EFGH")),List.of()),e.origin());
        return replace(p,List.of(unit(U,List.of(entry),u.sequences(),u.objects())),p.coverage(),p.uncertainties(),p.premises());
    }
    @Test void nominalRenameAndEquivalentAliasViewsKeepByteContributionsAndSupports() {
        for(boolean multiple:List.of(false,true)) {
            var p=matrix(multiple);var expected=meaning(p,YWHOLE);var u=p.units().getFirst();
            var alias=new ObjectId(U,"renamed-and-qualified-alias");var objects=new ArrayList<Memory.ObjectDeclaration>();
            for(var o:u.objects())objects.add(new Memory.ObjectDeclaration(o.id(),Optional.of("entirely renamed "+o.id().localId()),o.typeRef(),o.storage(),o.visibility(),o.origin(),o.coverage(),o.precision()));
            var original=objects.getLast();objects.add(new Memory.ObjectDeclaration(alias,Optional.of("unrelated nominal alias"),original.typeRef(),original.storage(),original.visibility(),original.origin(),original.coverage(),original.precision()));
            var transformed=replace(p,List.of(unit(U,u.entries(),u.sequences(),objects)),p.coverage(),p.uncertainties(),p.premises());
            assertEquals(expected,meaning(transformed,alias));assertFalse(expected.contributions().isEmpty());assertFalse(expected.supports().isEmpty());
        }
    }
    @Test void unorderedInventoriesAndSimultaneousInitialConditionsArePermutationInvariant() {
        for(boolean multiple:List.of(false,true)) {
            var p=matrix(multiple);var expected=meaning(p,YWHOLE);var u=p.units().getFirst();
            for(int permutation=0;permutation<6;permutation++) {
                var random=new Random(7100+permutation);var sequences=new ArrayList<>(u.sequences());Collections.shuffle(sequences,random);
                var objects=new ArrayList<>(u.objects());Collections.shuffle(objects,random);var storage=new ArrayList<>(p.storage());Collections.shuffle(storage,random);
                var e=u.entries().getFirst();var conditions=new ArrayList<>(e.state().conditions());Collections.shuffle(conditions,random);
                var entry=new Entries.Entry(e.id(),e.initialLabel(),e.signature(),new Entries.EntryState(conditions,List.of()),e.origin());
                var transformed=new Publication(P,p.airVersion(),p.capabilities(),p.artifacts(),List.of(unit(U,List.of(entry),sequences,objects)),storage,p.resources(),p.artifactRelations(),p.origins(),p.coverage(),p.uncertainties(),p.premises());
                assertEquals(expected,meaning(transformed,YWHOLE),"permutation "+permutation);
            }
        }
    }
    @Test void insertionOfProvablyDisjointOverlayDoesNotPolluteIndependentObservation() {
        for(boolean multiple:List.of(false,true)) {
            var p=matrix(multiple);var expected=meaning(p,YWHOLE);var u=p.units().getFirst();var z=new StorageId(P,"disjoint-overlay");
            var storage=new ArrayList<>(p.storage());storage.add(new Memory.Region(new Memory.StorageHeader(z,Optional.of(U),Memory.Lifetime.PERSISTENT,Memory.Visibility.PRIVATE,origin(P)),Optional.of(BigInteger.valueOf(1000)),Optional.empty()));
            var objects=new ArrayList<>(u.objects());
            for(int i=0;i<12;i++)objects.add(new Memory.ObjectDeclaration(new ObjectId(U,"irrelevant-alias-"+i),Optional.of("same display"),Types.known(Types.Builtin.TEXT),new Memory.ViewBinding(z,BigInteger.valueOf(i),BigInteger.valueOf(8),IBM),Memory.Visibility.PRIVATE,origin(P),Evidence.CoverageStatus.MODELED,header(U,"metadata").precision()));
            var proof=new Proofs.Premise(new PremiseId(P,"separate"),"manual contract","independent allocations",origin(P),new Proofs.DisjointStorage(List.of(R,Y,z)));
            var transformed=new Publication(P,p.airVersion(),p.capabilities(),p.artifacts(),List.of(unit(U,u.entries(),u.sequences(),objects)),storage,p.resources(),p.artifactRelations(),p.origins(),p.coverage(),p.uncertainties(),List.of(proof));
            assertEquals(expected,meaning(transformed,YWHOLE));
        }
    }
}
