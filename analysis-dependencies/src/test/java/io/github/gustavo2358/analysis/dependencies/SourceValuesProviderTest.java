package io.github.gustavo2358.analysis.dependencies;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import io.github.gustavo2358.analysis.dependencies.source.*;
import static io.github.gustavo2358.analysis.dependencies.source.QualifiedSourceDependencies.*;

class SourceValuesProviderTest {
    static final Location LOCATION=new Location("synthetic.cbl",1,0,1,10);
    static final Provenance ORIGIN=new Provenance(LOCATION,LOCATION,List.of(),true);
    static final UnitId UNIT=new UnitId("synthetic",List.of(0),"SAMPLE");
    static NominalValues.Term read(String id){return new NominalValues.Term("READ",id);}
    static NominalValues.Term literal(String text){return new NominalValues.Term("LITERAL",text);}
    static UnitEvidence fixture(List<NominalValues.Assignment> assignments,List<NominalValues.Condition> conditions,List<NominalValueEvidence.Branch> branches) {
        var statements=new ArrayList<Statement>();var nodes=new ArrayList<Node>();var derivations=new ArrayList<Derivation>();
        for(int i=0;i<3;i++) {
            statements.add(new Statement(new StatementId(UNIT,"s"+i),ORIGIN));
            nodes.add(new Node("n"+i,"ROOT","s"+i,new Support("ENTRY_UNKNOWN",List.of(),List.of(),"NONE")));
            derivations.add(new Derivation("d"+i,i==0?List.of():List.of("n"+(i-1)),"n"+i,List.of(),i==0?"PRIMARY_ENTRY":"flow"+i,List.of("p"),List.of()));
        }
        var call=new StatementId(UNIT,"s2");var occurrences=List.of(new Occurrence(call,"COBOL","CALL","PROGRAM","cobol-zos-dynamic-call-minimal@1","COMPUTED",List.of(new Operand(new OperandId(call,"o"),ORIGIN)),List.of(),true,List.of("n2")));
        var facts=new NominalValues(assignments.stream().anyMatch(a->a.source().extended())?"NOMINAL_TEXT_SOURCE_V3":"NOMINAL_TEXT_SOURCE_V1",List.of(new NominalValues.Symbol("P",8),new NominalValues.Symbol("Q",8)),assignments,conditions,List.of(new NominalValues.Query("s2","P")));
        var evidence=new NominalValueEvidence(facts,List.of(new NominalValueEvidence.Declaration("P",ORIGIN),new NominalValueEvidence.Declaration("Q",ORIGIN)),List.of(new NominalValueEvidence.Seed("P","SELF0001","DECLARATIVE_POSSIBILITY",ORIGIN),new NominalValueEvidence.Seed("Q","OLD00001","DECLARATIVE_POSSIBILITY",ORIGIN)),branches,List.of(new NominalValueEvidence.Uncertainty("missing","MISSING_COPY",ORIGIN)));
        return new UnitEvidence(UNIT,true,statements,occurrences,List.of(),nodes,derivations,List.of(),List.of(),List.of(),List.of(new Proof("p","LOCAL_GRAMMAR","fixture",ORIGIN,List.of())),List.of(),Optional.of(evidence));
    }
    static List<String> values(UnitEvidence unit){return new SourceValuesProvider(unit,Set.of("s2")).candidates("s2").stream().map(SourceValuesProvider.Candidate::rawValue).toList();}
    @Test void overwriteDoesNotRetainOldSeed(){assertEquals(List.of("REAL0001"),values(fixture(List.of(new NominalValues.Assignment("s0","P",literal("REAL0001"))),List.of(),List.of())));}
    @Test void copyCapturesSnapshot(){assertEquals(List.of("OLD00001"),values(fixture(List.of(new NominalValues.Assignment("s0","P",read("Q")),new NominalValues.Assignment("s1","Q",literal("NEW00001"))),List.of(),List.of())));}
    @Test void branchExcludesKnownNameWithOpenStoragePremises(){
        var condition=new NominalValues.Condition("s1",new NominalValues.Predicate("EQ",List.of(read("P"),read("Q")),List.of()));
        assertTrue(values(fixture(List.of(new NominalValues.Assignment("s0","P",read("Q"))),List.of(condition),List.of(new NominalValueEvidence.Branch("d2",false)))).isEmpty());
    }
    @Test void retainedNameCarriesCopySeedAndMissingInput(){
        var unit=fixture(List.of(new NominalValues.Assignment("s0","P",read("Q"))),List.of(),List.of());
        var result=new SourceValuesProvider(unit,Set.of("s2")).candidates("s2").getFirst();
        assertEquals("OLD00001",result.rawValue());assertFalse(result.support().assumptions().isEmpty());
        assertEquals("MISSING_COPY",result.support().uncertainties().getFirst().kind());
        assertEquals(Set.of("DECLARATION_VALUE","ASSIGNMENT"),result.support().evidence().stream().map(SourceValuesProvider.Evidence::kind).collect(java.util.stream.Collectors.toSet()));
    }
    @Test void lowValuesCannotMatchATextWithDifferentCharacters(){
        var low=new NominalValues.Term("LOW_VALUES","");
        var condition=new NominalValues.Condition("s0",new NominalValues.Predicate("EQ",List.of(read("P"),low),List.of()));
        assertTrue(values(fixture(List.of(),List.of(condition),List.of(new NominalValueEvidence.Branch("d1",true)))).isEmpty());
    }
    @Test void unknownCollatingCharacterAndUnicodeRemainExplicit(){
        assertEquals(io.github.gustavo2358.analysis.values.TextPredicate.BOTH,io.github.gustavo2358.analysis.values.TextPredicate.sourceFigurativeEquality(List.of("AAAAAAAA"),false));
        assertEquals(io.github.gustavo2358.analysis.values.TextPredicate.FALSE,io.github.gustavo2358.analysis.values.TextPredicate.sourceFigurativeEquality(List.of("A😀A😀"),false));
        assertEquals("😀   ",io.github.gustavo2358.analysis.values.TextPredicate.fit("😀",4));
    }

    @Test void unobservedRootedTailDoesNotRetainStatesOrChangeCompleteSupports() {
        var base=fixture(List.of(new NominalValues.Assignment("s0","P",read("Q"))),List.of(),List.of());
        var nodes=new ArrayList<>(base.nodes());var derivations=new ArrayList<>(base.derivations());
        for(int i=0;i<2000;i++) {
            nodes.add(new Node("tail"+i,"ROOT","s0",base.nodes().getFirst().support()));
            derivations.add(new Derivation("tail-edge"+i,List.of(i==0?"n2":"tail"+(i-1)),"tail"+i,List.of(),"unobserved-flow",List.of("p"),List.of()));
        }
        var larger=new UnitEvidence(base.unit(),base.controlAvailable(),base.statements(),base.occurrences(),base.targets(),nodes,derivations,base.selections(),base.events(),base.guards(),base.proofs(),base.frontiers(),base.nominalValues());
        var expected=new SourceValuesProvider(base,Set.of("s2"));var actual=new SourceValuesProvider(larger,Set.of("s2"));
        assertEquals(expected.candidates("s2"),actual.candidates("s2"));
        assertEquals(expected.workItems(),actual.workItems());
        assertEquals(0,new SourceValuesProvider(larger,Set.of()).workItems());
    }

    static UnitEvidence model(UnitEvidence u, String symbol, int extent) {
        var old=u.nominalValues().orElseThrow();var f=old.facts();
        var symbols=f.symbols().stream().map(s->new NominalValues.Symbol(s.node(),s.node().equals(symbol)?extent:s.extent(),s.node().equals(symbol))).toList();
        var facts=new NominalValues(f.authority().equals("NOMINAL_TEXT_SOURCE_V3")?f.authority():"NOMINAL_TEXT_SOURCE_V2",symbols,f.assignments(),f.conditions(),f.queries());
        var evidence=new NominalValueEvidence(facts,old.declarations(),old.seeds(),old.branches(),old.uncertainties());
        return new UnitEvidence(u.unit(),u.controlAvailable(),u.statements(),u.occurrences(),u.targets(),u.nodes(),u.derivations(),u.selections(),u.events(),u.guards(),u.proofs(),u.frontiers(),Optional.of(evidence));
    }
    @Test void modelValuesAreNotSeeds() {
        assertTrue(values(model(fixture(List.of(),List.of(),List.of()),"P",1)).isEmpty());
    }
    @Test void unknownModelCopyCannotKillAnObservedName() {
        var u=model(fixture(List.of(new NominalValues.Assignment("s0","P",read("Q"))),List.of(),List.of()),"Q",1);
        assertEquals(List.of("SELF0001"),values(u));
    }
    @Test void modelWidthAndUnknownOverwriteCannotEraseSourceText() {
        var u=model(fixture(List.of(new NominalValues.Assignment("s0","P",literal("PROGA001")),
            new NominalValues.Assignment("s1","P",new NominalValues.Term("UNKNOWN",""))),List.of(),List.of()),"P",1);
        assertEquals(List.of("P","PROGA001"),values(u));
        var candidates=new SourceValuesProvider(u,Set.of("s2")).candidates("s2");
        assertTrue(candidates.stream().allMatch(c->c.support().evidence().stream().anyMatch(e->e.reference().equals("s0"))));
        assertTrue(candidates.stream().noneMatch(c->c.support().evidence().stream().anyMatch(e->e.kind().equals("DECLARATION_VALUE"))));
    }
    @Test void modelInfluenceSurvivesACopyAndCannotFilterEitherBranch() {
        var condition=new NominalValues.Condition("s1",new NominalValues.Predicate("EQ",List.of(read("P"),literal("SELF0001")),List.of()));
        for(boolean branch:List.of(true,false)) {
            var u=model(fixture(List.of(new NominalValues.Assignment("s0","P",read("Q"))),List.of(condition),List.of(new NominalValueEvidence.Branch("d2",branch))),"Q",1);
            assertEquals(List.of("SELF0001"),values(u));
        }
    }
    @Test void ordinaryKnownWriteAfterModelCopyStillProvesKill() {
        var u=model(fixture(List.of(new NominalValues.Assignment("s0","P",read("Q")),
            new NominalValues.Assignment("s1","P",literal("REAL0001"))),List.of(),List.of()),"Q",1);
        assertEquals(List.of("REAL0001"),values(u));
    }
    static NominalValues.Term unary(String kind,NominalValues.Term argument){return new NominalValues.Term(kind,"",List.of(argument));}
    @Test void nestedTextTransformsRunBeforeReceivingFit() {
        var expression=unary("UPPER_ASCII",unary("TRIM_SPACES",literal("  progb001  ")));
        assertEquals(List.of("PROGB001"),values(fixture(List.of(new NominalValues.Assignment("s0","P",expression)),List.of(),List.of())));
        assertEquals(List.of("        "),values(fixture(List.of(new NominalValues.Assignment("s0","P",unary("TRIM_SPACES",literal("   ")))),List.of(),List.of())));
    }
    @Test void trimOnlyRemovesTheSpaceCharacter() {
        assertEquals(List.of("\tA\t     "),values(fixture(List.of(new NominalValues.Assignment("s0","P",unary("TRIM_SPACES",literal(" \tA\t ")))),List.of(),List.of())));
    }
    @Test void textTransformReadsRetainTheirDeclarationAndAssignmentSupports() {
        var unit=fixture(List.of(new NominalValues.Assignment("s0","Q",literal("new00001")),new NominalValues.Assignment("s1","P",unary("UPPER_ASCII",read("Q")))),List.of(),List.of());
        var result=new SourceValuesProvider(unit,Set.of("s2")).candidates("s2").getFirst();assertEquals("NEW00001",result.rawValue());
        assertTrue(result.support().evidence().stream().anyMatch(e->e.reference().equals("s0")));
        assertTrue(result.support().evidence().stream().anyMatch(e->e.reference().equals("s1")));
    }
    @Test void modelInfluenceSurvivesTextTransforms() {
        var unit=model(fixture(List.of(new NominalValues.Assignment("s0","P",unary("UPPER_ASCII",read("Q")))),List.of(),List.of()),"Q",8);
        assertEquals(List.of("SELF0001"),values(unit));
    }
    @Test void nonAsciiDoesNotUseTheJvmLocaleAsACobolCaseTable() {
        assertTrue(values(fixture(List.of(new NominalValues.Assignment("s0","P",unary("UPPER_ASCII",literal("éprog001")))),List.of(),List.of())).isEmpty());
    }
    @Test void malformedExpressionOperatorsArityAndReferencesAreRejected() {
        assertThrows(IllegalArgumentException.class,()->new NominalValues.Term("UPPER_ASCII",""));
        assertThrows(IllegalArgumentException.class,()->unary("READ",literal("x")));
        assertThrows(IllegalArgumentException.class,()->unary("COBOL_FUNCTION",literal("x")));
        assertThrows(IllegalArgumentException.class,()->fixture(List.of(new NominalValues.Assignment("s0","P",unary("TRIM_SPACES",read("missing")))),List.of(),List.of()));
    }

    static UnitEvidence table(UnitEvidence u, boolean modeledOrigin) {
        var old=u.nominalValues().orElseThrow();var f=old.facts();
        var symbols=f.symbols().stream().map(s->new NominalValues.Symbol(s.node(),s.extent(),modeledOrigin&&s.node().equals("Q"))).toList();
        var fields=List.of(new NominalValues.TableField("P",List.of(new NominalValues.Initial("Q","PROGA001"),new NominalValues.Initial("Q","PROGB001"))));
        var facts=new NominalValues("NOMINAL_TEXT_SOURCE_V4",symbols,f.assignments(),f.conditions(),f.queries(),fields);
        var e=new NominalValueEvidence(facts,old.declarations(),List.of(),old.branches(),old.uncertainties());
        return new UnitEvidence(u.unit(),u.controlAvailable(),u.statements(),u.occurrences(),u.targets(),u.nodes(),u.derivations(),u.selections(),u.events(),u.guards(),u.proofs(),u.frontiers(),Optional.of(e));
    }
    @Test void tableWriteCannotKillOtherOccurrencesOrLoseInitializerSupports() {
        var u=table(fixture(List.of(new NominalValues.Assignment("s0","P",literal("PROGC001"))),List.of(),List.of()),false);
        assertEquals(List.of("PROGA001","PROGB001","PROGC001"),values(u));
        var candidates=new SourceValuesProvider(u,Set.of("s2")).candidates("s2");
        assertTrue(candidates.stream().allMatch(c->c.support().assumptions().contains("TABLE_INDEX_NOT_REFINED")));
        for(var c:candidates)assertTrue(c.support().evidence().stream().anyMatch(e->e.reference().equals(c.rawValue().equals("PROGC001")?"s0":"Q")));
    }
    @Test void unknownTableWriteAndPredicateRetainPossibleElements() {
        var c=new NominalValues.Condition("s1",new NominalValues.Predicate("EQ",List.of(read("P"),literal("PROGA001")),List.of()));
        for(boolean branch:List.of(true,false))assertEquals(List.of("PROGA001","PROGB001"),values(table(fixture(List.of(new NominalValues.Assignment("s0","P",new NominalValues.Term("UNKNOWN",""))),List.of(c),List.of(new NominalValueEvidence.Branch("d2",branch))),false)));
    }
    @Test void modelTableInitializerNeverBecomesRuntimeEvidence() {
        assertTrue(values(table(fixture(List.of(),List.of(),List.of()),true)).isEmpty());
        assertEquals(List.of("PROGC001"),values(table(fixture(List.of(new NominalValues.Assignment("s0","P",literal("PROGC001"))),List.of(),List.of()),true)));
    }
    @Test void choiceKeepsEveryAlternativeAndRejectsOldAuthority() {
        var choice=new NominalValues.Term("CHOICE","",List.of(literal("PROGC001"),literal("PROGD001")));
        var base=fixture(List.of(),List.of(),List.of());var f=base.nominalValues().orElseThrow().facts();
        assertThrows(IllegalArgumentException.class,()->new NominalValues("NOMINAL_TEXT_SOURCE_V3",f.symbols(),List.of(new NominalValues.Assignment("s0","P",choice)),f.conditions(),f.queries()));
        var u=table(base,false);var old=u.nominalValues().orElseThrow();
        var changed=new NominalValues("NOMINAL_TEXT_SOURCE_V4",old.facts().symbols(),List.of(new NominalValues.Assignment("s0","P",choice)),List.of(),old.facts().queries(),old.facts().tableFields());
        var e=new NominalValueEvidence(changed,old.declarations(),old.seeds(),old.branches(),old.uncertainties());
        var selected=new UnitEvidence(u.unit(),u.controlAvailable(),u.statements(),u.occurrences(),u.targets(),u.nodes(),u.derivations(),u.selections(),u.events(),u.guards(),u.proofs(),u.frontiers(),Optional.of(e));
        assertEquals(List.of("PROGA001","PROGB001","PROGC001","PROGD001"),values(selected));
    }
}
