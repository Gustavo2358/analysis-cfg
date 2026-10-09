package io.github.gustavo2358.analysis.values;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class TextPredicateSourceEqualityTest {
    private static int oracle(List<String> left,boolean leftOpen,List<String> right,boolean rightOpen) {
        if(leftOpen||rightOpen||left.isEmpty()||right.isEmpty())return TextPredicate.BOTH;
        int result=0;
        for(String a:left)for(String b:right) {
            int ac=a.codePointCount(0,a.length()),bc=b.codePointCount(0,b.length()),length=Math.max(ac,bc);
            result|=(a+" ".repeat(length-ac)).equals(b+" ".repeat(length-bc))?TextPredicate.TRUE:TextPredicate.FALSE;
        }
        return result;
    }
    private static final class Counted extends AbstractCollection<String> {
        final List<String> values;long visits;
        Counted(List<String> values){this.values=values;}
        @Override public int size(){return values.size();}
        @Override public Iterator<String> iterator() {
            var iterator=values.iterator();return new Iterator<>() {
                @Override public boolean hasNext(){return iterator.hasNext();}
                @Override public String next(){visits++;return iterator.next();}
            };
        }
    }
    @Test void sourceEqualityReadsEachCandidateOnceInsteadOfComparingEveryPair() {
        int n=256;var a=new ArrayList<String>();var b=new ArrayList<String>();
        for(int i=0;i<n;i++){a.add("NAME"+i);b.add("NAME"+i+" ");}
        var left=new Counted(a);var right=new Counted(b);
        assertEquals(TextPredicate.BOTH,TextPredicate.sourceEquality(left,false,right,false));
        assertTrue(left.visits<=n&&right.visits<=n,"candidate product: "+left.visits+" / "+right.visits);
    }
    @Test void indexedTruthImageMatchesIndependentUnicodePaddingOracle() {
        var random=new Random(18123);var alphabet=List.of(""," ","A","A "," A","A  ","AA","😀","😀 ","A😀","A\t","\t","A\u00a0","A\n");
        for(int round=0;round<400;round++) {
            var a=new ArrayList<String>();var b=new ArrayList<String>();
            for(int i=random.nextInt(12);i>0;i--)a.add(alphabet.get(random.nextInt(alphabet.size())));
            for(int i=random.nextInt(12);i>0;i--)b.add(alphabet.get(random.nextInt(alphabet.size())));
            boolean ao=random.nextBoolean(),bo=random.nextBoolean();
            assertEquals(oracle(a,ao,b,bo),TextPredicate.sourceEquality(a,ao,b,bo));
            assertEquals(oracle(a,false,b,false),TextPredicate.sourceEquality(a,false,b,false));
        }
    }
    @Test void spacePaddedEquivalenceClassesPreserveDuplicatesEmptyUnicodeAndNonSpaceDifferences() {
        assertEquals(TextPredicate.TRUE,TextPredicate.sourceEquality(List.of("A","A ","A  "),false,List.of("A "),false));
        assertEquals(TextPredicate.TRUE,TextPredicate.sourceEquality(List.of(""," "),false,List.of("  "),false));
        assertEquals(TextPredicate.TRUE,TextPredicate.sourceEquality(List.of("😀"),false,List.of("😀 "),false));
        for(String different:List.of(" A","A\t","A\u00a0","A\n"))
            assertEquals(TextPredicate.FALSE,TextPredicate.sourceEquality(List.of("A"),false,List.of(different),false));
        assertEquals(TextPredicate.BOTH,TextPredicate.sourceEquality(List.of("A","B"),false,List.of("A"),false));
    }
}
