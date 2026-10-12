package io.github.gustavo2358.analysis.dependencies;

import io.github.gustavo2358.analysis.dependencies.source.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static io.github.gustavo2358.analysis.dependencies.source.QualifiedSourceDependencies.*;
import static org.junit.jupiter.api.Assertions.*;

final class SourceExpressionAlgebraTest {
    private record Image(Set<String> values,boolean open) { }
    private static Image transform(Image input,String kind) {
        var result=new TreeSet<String>();boolean open=input.open();
        for(String value:input.values()) {
            if(kind.equals("UPPER_ASCII")) {
                boolean ascii=true;for(int i=0;i<value.length();i++)if(value.charAt(i)>127)ascii=false;
                if(!ascii){open=true;continue;}
                var chars=value.toCharArray();for(int i=0;i<chars.length;i++)if(chars[i]>='a'&&chars[i]<='z')chars[i]=(char)(chars[i]-32);
                result.add(new String(chars));
            } else {
                int from=0,to=value.length();
                if(!kind.equals("TRIM_TRAILING_SPACES"))while(from<to&&value.charAt(from)==32)from++;
                if(!kind.equals("TRIM_LEADING_SPACES"))while(to>from&&value.charAt(to-1)==32)to--;
                result.add(value.substring(from,to));
            }
        }
        return new Image(result,open);
    }
    private static NominalValues.Term unary(String kind,NominalValues.Term child){return new NominalValues.Term(kind,"",List.of(child));}
    private static List<SourceValuesProvider.Candidate> actual(NominalValues.Term term) {
        var base=SourceValuesProviderTest.fixture(List.of(),List.of(),List.of());var old=base.nominalValues().orElseThrow();
        var facts=new NominalValues("NOMINAL_TEXT_SOURCE_V4",old.facts().symbols(),List.of(new NominalValues.Assignment("s0","P",term)),List.of(),old.facts().queries());
        var evidence=new NominalValueEvidence(facts,old.declarations(),old.seeds(),List.of(),old.uncertainties());
        var unit=new UnitEvidence(base.unit(),base.controlAvailable(),base.statements(),base.occurrences(),base.targets(),base.nodes(),base.derivations(),base.selections(),base.events(),base.guards(),base.proofs(),base.frontiers(),Optional.of(evidence));
        return new SourceValuesProvider(unit,Set.of("s2")).candidates("s2");
    }
    private static Set<String> fit(Set<String> values) {
        var result=new TreeSet<String>();
        for(String value:values) {
            int count=value.codePointCount(0,value.length());
            result.add(count>8?value.substring(0,value.offsetByCodePoints(0,8)):value+" ".repeat(8-count));
        }
        return result;
    }
    @Test void composedTransformsAndTheirDistributionPreserveIndependentUnicodeImagesAndCompleteEvidence() {
        var random=new Random(78910);var words=List.of(""," ","  aB  ","a ","A","😀 "," é "," A\t ","ABCdefghi");
        var kinds=List.of("UPPER_ASCII","TRIM_SPACES","TRIM_LEADING_SPACES","TRIM_TRAILING_SPACES");
        for(int round=0;round<100;round++) {
            var selected=new TreeSet<String>();var leaves=new ArrayList<NominalValues.Term>();
            for(int i=0;i<3;i++){String word=words.get(random.nextInt(words.size()));selected.add(word);leaves.add(new NominalValues.Term("LITERAL",word));}
            NominalValues.Term term=new NominalValues.Term("CHOICE","",leaves);var image=new Image(selected,false);
            for(int i=random.nextInt(10);i>0;i--){String kind=kinds.get(random.nextInt(kinds.size()));term=unary(kind,term);image=transform(image,kind);}
            var candidates=actual(term);var found=new TreeSet<String>();
            for(var candidate:candidates) {
                found.add(candidate.rawValue());assertEquals(List.of(new SourceValuesProvider.Evidence("ASSIGNMENT","s0",SourceValuesProviderTest.ORIGIN)),candidate.support().evidence());
                assertEquals("MISSING_COPY",candidate.support().uncertainties().getFirst().kind());
            }
            assertEquals(fit(image.values()),found);
        }
    }
    @Test void distributionKeepsEveryProducerWhenDifferentInputsMergeIntoOneTransformedCandidate() {
        var base=SourceValuesProviderTest.fixture(List.of(),List.of(),List.of());var old=base.nominalValues().orElseThrow();
        var choice=new NominalValues.Term("CHOICE","",List.of(new NominalValues.Term("READ","P"),new NominalValues.Term("READ","Q")));
        var facts=new NominalValues("NOMINAL_TEXT_SOURCE_V4",old.facts().symbols(),List.of(new NominalValues.Assignment("s0","P",new NominalValues.Term("LITERAL","alpha")),new NominalValues.Assignment("s1","P",unary("UPPER_ASCII",choice))),List.of(),old.facts().queries());
        var seeds=List.of(new NominalValueEvidence.Seed("Q","ALPHA","DECLARATIVE_POSSIBILITY",SourceValuesProviderTest.ORIGIN));
        var evidence=new NominalValueEvidence(facts,old.declarations(),seeds,List.of(),old.uncertainties());
        var unit=new UnitEvidence(base.unit(),base.controlAvailable(),base.statements(),base.occurrences(),base.targets(),base.nodes(),base.derivations(),base.selections(),base.events(),base.guards(),base.proofs(),base.frontiers(),Optional.of(evidence));
        var candidate=new SourceValuesProvider(unit,Set.of("s2")).candidates("s2").getFirst();
        assertEquals("ALPHA   ",candidate.rawValue());
        assertEquals(Set.of("s0","s1","Q"),new HashSet<>(candidate.support().evidence().stream().map(SourceValuesProvider.Evidence::reference).toList()));
        assertEquals(3,candidate.support().evidence().size());
    }
}
