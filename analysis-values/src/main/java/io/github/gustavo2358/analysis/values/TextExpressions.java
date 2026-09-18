package io.github.gustavo2358.analysis.values;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import java.util.*;

/** Logical expression evaluation; repeated reads select the same immutable input alternative. */
final class TextExpressions {
    private TextExpressions() { }
    static Set<ObjectId> reads(Expression expression){
        var out=new LinkedHashSet<ObjectId>();var pending=new ArrayDeque<Expression>();pending.push(expression);
        while(!pending.isEmpty()) {
            var e=pending.pop();
            if(e instanceof Expressions.Read r&&r.place() instanceof Places.ObjectPlace p)out.add(p.object());
            else if(e instanceof Expressions.Literal l&&l.value() instanceof Values.TextValue) { }
            else if(e instanceof Expressions.FitText f){f.length().intValueExact();pending.push(f.value());}
            else if(e instanceof Expressions.SliceText slice){integer(slice.start());integer(slice.count());pending.push(slice.value());}
            else if(e instanceof Expressions.Binary b&&b.operator()==Expressions.BinaryOperator.CONCAT){pending.push(b.right());pending.push(b.left());}
            else throw new TextProfile.Refusal(false,"UNSUPPORTED_EFFECT_PROFILE");
        }
        return out;
    }
    private static int integer(Expression e){
        if(e instanceof Expressions.Literal l&&l.value() instanceof Values.IntValue n&&n.value().signum()>=0)return n.value().intValueExact();
        throw new TextProfile.Refusal(false,"UNSUPPORTED_EFFECT_PROFILE");
    }
    static Candidates evaluate(Operations.Assign operation,PossibleValuesState state,Map<ObjectId,TextProfile.Location> subjects,ValueUniverse universe,ValuesWork work){
        var inputs=new LinkedHashMap<TextProfile.Location,List<LogicalText>>();
        for(var object:reads(operation.value())){
            var location=subjects.get(object);if(inputs.containsKey(location))continue;
            var candidates=state.value(location.ordinal(),work);var values=new ArrayList<LogicalText>();
            for(int i=0;i<candidates.size();i++)values.add(universe.value(candidates.at(i)));
            if(candidates.open()||values.isEmpty())values.add(null);
            inputs.put(location,values);
        }
        var varying=inputs.entrySet().stream().filter(e->e.getValue().size()>1).map(Map.Entry::getKey).toList();
        var snapshots=new ArrayList<Map<TextProfile.Location,LogicalText>>();
        var fixed=new HashMap<TextProfile.Location,LogicalText>();inputs.forEach((k,v)->fixed.put(k,v.size()==1?v.getFirst():null));
        if(varying.isEmpty())snapshots.add(fixed);
        // No cross product: if several cells vary, other varying inputs stay unknown.
        for(var location:varying)for(var value:inputs.get(location)){var snapshot=new HashMap<>(fixed);snapshot.put(location,value);snapshots.add(snapshot);}
        Candidates result=null;
        for(var snapshot:snapshots){
            var value=evaluate(operation.value(),o->snapshot.get(subjects.get(o)));
            var candidate=value==null?Candidates.UNKNOWN:universe.supported(value,operation.header().id(),operation.header().origin(),List.of(),work);
            result=result==null?candidate:result.join(candidate,work);
        }
        return result==null?Candidates.UNKNOWN:result;
    }
    private record Frame(Expression expression,boolean expanded) { }
    private static LogicalText evaluate(Expression expression,java.util.function.Function<ObjectId,LogicalText> input){
        var pending=new ArrayDeque<Frame>();var values=new IdentityHashMap<Expression,LogicalText>();pending.push(new Frame(expression,false));
        while(!pending.isEmpty()) {
            var frame=pending.pop();var e=frame.expression();
            if(!frame.expanded()) {
                pending.push(new Frame(e,true));
                if(e instanceof Expressions.FitText f)pending.push(new Frame(f.value(),false));
                else if(e instanceof Expressions.SliceText s)pending.push(new Frame(s.value(),false));
                else if(e instanceof Expressions.Binary b){pending.push(new Frame(b.right(),false));pending.push(new Frame(b.left(),false));}
                continue;
            }
            LogicalText result;
            if(e instanceof Expressions.Read r)result=input.apply(((Places.ObjectPlace)r.place()).object());
            else if(e instanceof Expressions.Literal l)result=LogicalText.of(((Values.TextValue)l.value()).value());
            else if(e instanceof Expressions.FitText f){var value=values.get(f.value());int length=f.length().intValueExact();result=value==null?LogicalText.unknown(length):value.fit(length,f.pad().codePointAt(0));}
            else if(e instanceof Expressions.SliceText s){var value=values.get(s.value());int start=integer(s.start()),count=integer(s.count());result=value==null||start>value.length()-count?LogicalText.unknown(count):value.slice(start,count);}
            else {var b=(Expressions.Binary)e;var left=values.get(b.left());var right=values.get(b.right());result=left==null||right==null?null:left.concat(right);}
            values.put(e,result);
        }
        return values.get(expression);
    }
}
