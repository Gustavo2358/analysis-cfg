package io.github.gustavo2358.analysis.values;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.values.ValuesFixtures.*;
import static io.github.gustavo2358.analysis.values.ValuesTest.*;

class TextPredicateTest {
    private Publication program(String initial) {
        var p=graph(new String[]{null,null,null,null},new int[][]{{1,2},{3},{3},{}},2,true,true);
        var u=p.units().getFirst();var guard=u.objects().get(0).id();var result=u.objects().get(1).id();
        var seq=new ArrayList<>(u.sequences());var first=seq.getFirst();var h=first.terminator().header();
        var read=new Expressions.Read(operand(h.id(),"read",Operand.Role.VALUE_READ),new Places.ObjectPlace(operand(h.id(),"place",Operand.Role.VALUE_READ),guard));
        var literal=new Expressions.Literal(operand(h.id(),"literal",Operand.Role.VALUE_READ),new Values.TextValue("MATCH"));
        var predicate=new Expressions.Binary(operand(h.id(),"compare",Operand.Role.PREDICATE),Expressions.BinaryOperator.EQ,read,literal);
        seq.set(0,new Sequence(first.label(),initial==null?List.of():List.of(assign(u.id(),"guard",guard,initial)),new Operations.Branch(h,predicate,seq.get(1).label(),seq.get(2).label()),first.origin()));
        for(int i=1;i<=2;i++){var s=seq.get(i);seq.set(i,new Sequence(s.label(),List.of(assign(u.id(),"choice"+i,result,i==1?"YES":"NO")),s.terminator(),s.origin()));}
        return replace(p,List.of(unit(u.id(),u.entries(),seq,u.objects())),p.coverage(),p.uncertainties(),p.premises());
    }
    private void check(Publication p,String... expected) {
        var s=session(p);var q=before(p,3,1);
        var scalar=PossibleValuesAnalysis.prepare(s,PossibleValuesAnalysis.EFFECTS_PROFILE,Set.of(q.subject()));
        assertEquals(PossibleValuesAnalysis.Status.ACCEPTED,scalar.status(),scalar.reason());
        var a=fact(scalar.analysis().orElseThrow().execute(),q);
        var b=RegionalValuesAnalysis.prepare(s).analysis().orElseThrow().execute().observe(List.of(q)).observations().getFirst().value();
        var oracle=new TreeSet<>(List.of(expected));
        assertEquals(oracle,new TreeSet<>(a.candidates().stream().map(Values.TextValue::value).toList()),"scalar");
        assertEquals(oracle,new TreeSet<>(b.candidates().stream().map(Values.TextValue::value).toList()),"regional");
    }
    @Test void closedEqualityAndUnknownInput(){check(program("OTHER"),"NO");check(program("MATCH"),"YES");check(program(null),"NO","YES");}
    @Test void mayScopedWriteInvalidatesClosure(){
        var p=program("OTHER");var u=p.units().getFirst();var s=u.sequences().getFirst();
        var gap=new UncertaintyId(p.id(),"write");var uncertainty=new Evidence.Uncertainty(gap,"UNKNOWN_WRITE",List.of(Evidence.Dimension.EFFECTS,Evidence.Dimension.VALUES),new Scopes.UnitScope(u.id()),"input may change",origin(p.id()));
        var h=header(u.id(),"change");var exact=h.precision().control();var open=new Evidence.Claim(new Scopes.UnitScope(u.id()),Evidence.PrecisionStatus.OPEN,List.of(gap));
        var effect=new Operations.Header(h.id(),h.origin(),Evidence.CoverageStatus.ABSTRACTED,new Evidence.Precision(exact,exact,open,open,exact),List.of(gap));
        for(var scope:List.of(new Scopes.ObjectsMemory(List.of(u.objects().getFirst().id())),new Scopes.AllMemory(p.id(),false))) {
            var instructions=new ArrayList<>(s.instructions());instructions.add(new Operations.HavocMay(effect,scope,gap));
            var seq=new ArrayList<>(u.sequences());seq.set(0,new Sequence(s.label(),instructions,s.terminator(),s.origin()));
            check(replace(p,List.of(unit(u.id(),u.entries(),seq,u.objects())),p.coverage(),List.of(uncertainty),p.premises()),"NO","YES");
        }
    }
    @Test void openEntryPossibilityNeverClosesAPredicate(){
        var p=program(null);var u=p.units().getFirst();var e=u.entries().getFirst();var owner=new EntryOwner(e.id());
        var gap=new UncertaintyId(p.id(),"entry-open");
        var place=new Places.ObjectPlace(new Operand.Header(new OperandId(owner,"initial-place"),Operand.Role.VALUE_WRITE,e.origin()),u.objects().getFirst().id());
        var literal=new Expressions.Literal(new Operand.Header(new OperandId(owner,"initial-value"),Operand.Role.VALUE_READ,e.origin()),new Values.TextValue("OTHER"));
        for(boolean exact:List.of(false,true)) {
            Entries.InitialValue value=exact?new Entries.LiteralInitial(literal):new Entries.PossibleLiterals(List.of(literal),gap);
            var condition=new Entries.InitialCondition(place,value,e.origin(),List.of());
            var entry=new Entries.Entry(e.id(),e.initialLabel(),e.signature(),new Entries.EntryState(List.of(condition),List.of()),e.origin());
            var changed=new Publication(p.id(),p.airVersion(),new Capabilities.Manifest(List.of(Capabilities.ENTRY_POSSIBILITIES),List.of()),p.artifacts(),List.of(unit(u.id(),List.of(entry),u.sequences(),u.objects())),p.storage(),p.resources(),p.artifactRelations(),p.origins(),p.coverage(),List.of(new Evidence.Uncertainty(gap,"OPEN",List.of(Evidence.Dimension.VALUES),new Scopes.UnitScope(u.id()),"unspecified prior invocation",e.origin())),p.premises());
            check(changed,exact?new String[]{"NO"}:new String[]{"NO","YES"});
        }
    }
    @Test void anOpenPredecessorSurvivesAJoinWithAClosedPredecessor(){
        var p=program(null);var u=p.units().getFirst();var seq=new ArrayList<>(u.sequences());
        seq.add(branch(u.id(),"choose","defined","s0"));
        var jump=jump(u.id(),"defined","s0");
        seq.add(new Sequence(jump.label(),List.of(assign(u.id(),"define",u.objects().getFirst().id(),"OTHER")),jump.terminator(),jump.origin()));
        var e=u.entries().getFirst();var entry=new Entries.Entry(e.id(),Optional.of(seq.get(4).label()),e.signature(),e.state(),e.origin());
        check(replace(p,List.of(unit(u.id(),List.of(entry),seq,u.objects())),p.coverage(),p.uncertainties(),p.premises()),"NO","YES");
    }

    @Test void predicateImagesRespectBooleanCompositionUnicodeAndRepresentableExtents() {
        var p=program("OTHER");var h=p.units().getFirst().sequences().getFirst().terminator().header();
        var text=new Expressions.Literal(operand(h.id(),"unicode",Operand.Role.VALUE_READ),new Values.TextValue("😀X"));
        var fitted=new Expressions.FitText(operand(h.id(),"fit",Operand.Role.VALUE_READ),text,java.math.BigInteger.ONE," ");
        var expected=new Expressions.Literal(operand(h.id(),"expected",Operand.Role.VALUE_READ),new Values.TextValue("😀"));
        var eq=new Expressions.Binary(operand(h.id(),"eq",Operand.Role.PREDICATE),Expressions.BinaryOperator.EQ,fitted,expected);
        var not=new Expressions.Unary(operand(h.id(),"not",Operand.Role.PREDICATE),Expressions.UnaryOperator.NOT,eq);
        var and=new Expressions.Binary(operand(h.id(),"and",Operand.Role.PREDICATE),Expressions.BinaryOperator.AND,eq,not);
        var or=new Expressions.Binary(operand(h.id(),"or",Operand.Role.PREDICATE),Expressions.BinaryOperator.OR,eq,not);
        assertEquals(TextPredicate.TRUE,TextPredicate.truth(eq,x->TextPredicate.Text.unknown()));
        assertEquals(TextPredicate.FALSE,TextPredicate.truth(and,x->TextPredicate.Text.unknown()));
        assertEquals(TextPredicate.TRUE,TextPredicate.truth(or,x->TextPredicate.Text.unknown()));
        var huge=new Expressions.FitText(operand(h.id(),"huge",Operand.Role.VALUE_READ),text,java.math.BigInteger.ONE.shiftLeft(40)," ");
        var comparison=new Expressions.Binary(operand(h.id(),"huge-eq",Operand.Role.PREDICATE),Expressions.BinaryOperator.EQ,huge,expected);
        assertEquals(TextPredicate.BOTH,TextPredicate.truth(comparison,x->TextPredicate.Text.unknown()));
    }

}
