package io.github.gustavo2358.analysis.values;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.ObjectId;
import java.util.*;
import java.util.function.Function;

/** Conservative Boolean image of the supported logical text expressions. */
final class TextPredicate {
    private TextPredicate() { }
    static final int FALSE=1, TRUE=2, BOTH=3;
    record Text(Set<LogicalText> values,boolean open) {
        Text { values=Set.copyOf(values); }
        static Text unknown(){return new Text(Set.of(),true);}
    }
    static int truth(Expression expression,Function<Place,Text> read) {
        if(expression instanceof Expressions.Unary unary&&unary.operator()==Expressions.UnaryOperator.NOT)
            return negate(truth(unary.argument(),read));
        if(!(expression instanceof Expressions.Binary binary))return BOTH;
        if(binary.operator()==Expressions.BinaryOperator.AND||binary.operator()==Expressions.BinaryOperator.OR) {
            int a=truth(binary.left(),read),b=truth(binary.right(),read),result=0;
            for(int x:new int[]{FALSE,TRUE})for(int y:new int[]{FALSE,TRUE})if((a&x)!=0&&(b&y)!=0)
                result|=(binary.operator()==Expressions.BinaryOperator.AND?(x==TRUE&&y==TRUE):(x==TRUE||y==TRUE))?TRUE:FALSE;
            return result;
        }
        if(binary.operator()!=Expressions.BinaryOperator.EQ&&binary.operator()!=Expressions.BinaryOperator.NE)return BOTH;
        var a=text(binary.left(),read);var b=text(binary.right(),read);
        if(a.open||b.open||a.values.isEmpty()||b.values.isEmpty()
                ||a.values.stream().anyMatch(v->!v.complete())||b.values.stream().anyMatch(v->!v.complete()))return BOTH;
        boolean same=a.values.stream().anyMatch(b.values::contains);
        int equal=(same?TRUE:0)|(a.values.size()==1&&a.values.equals(b.values)?0:FALSE);
        return binary.operator()==Expressions.BinaryOperator.EQ?equal:negate(equal);
    }
    private static int negate(int value){return ((value&FALSE)!=0?TRUE:0)|((value&TRUE)!=0?FALSE:0);}
    static Text text(Expression expression,Function<Place,Text> read) {
        if(expression instanceof Expressions.Literal literal&&literal.value() instanceof Values.TextValue value)
            return new Text(Set.of(LogicalText.of(value.value())),false);
        if(expression instanceof Expressions.Read value)return read.apply(value.place());
        if(expression instanceof Expressions.FitText fit) {
            // The logical text domain uses Java array indices; larger AIR extents remain open.
            if(fit.length().signum()<0||fit.length().bitLength()>31)return Text.unknown();
            var value=text(fit.value(),read);if(value.open)return Text.unknown();
            var fitted=new HashSet<LogicalText>();
            for(var item:value.values)fitted.add(item.fit(fit.length().intValueExact(),fit.pad().codePointAt(0)));
            return new Text(fitted,false);
        }
        if(expression instanceof Expressions.SliceText slice) {
            if(!(slice.start() instanceof Expressions.Literal start&&start.value() instanceof Values.IntValue a)
                ||!(slice.count() instanceof Expressions.Literal count&&count.value() instanceof Values.IntValue b))return Text.unknown();
            var value=text(slice.value(),read);if(value.open)return Text.unknown();
            var selected=new HashSet<LogicalText>();
            for(var item:value.values) {
                if(a.value().signum()<0||b.value().signum()<0||a.value().add(b.value()).compareTo(java.math.BigInteger.valueOf(item.length()))>0)return Text.unknown();
                selected.add(item.slice(a.value().intValueExact(),b.value().intValueExact()));
            }
            return new Text(selected,false);
        }
        return Text.unknown();
    }
    static Set<ObjectId> reads(Expression expression) {
        var result=new HashSet<ObjectId>();var pending=new ArrayDeque<Expression>();pending.add(expression);
        while(!pending.isEmpty()) {
            var e=pending.removeFirst();
            if(e instanceof Expressions.Read r&&r.place() instanceof Places.ObjectPlace p)result.add(p.object());
            else if(e instanceof Expressions.Unary u)pending.add(u.argument());
            else if(e instanceof Expressions.Binary b){pending.add(b.left());pending.add(b.right());}
            else if(e instanceof Expressions.FitText f)pending.add(f.value());
            else if(e instanceof Expressions.SliceText s){pending.add(s.value());pending.add(s.start());pending.add(s.count());}
        }
        return Set.copyOf(result);
    }
}
