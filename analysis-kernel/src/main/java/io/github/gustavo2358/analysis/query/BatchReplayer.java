package io.github.gustavo2358.analysis.query;

import io.github.gustavo2358.air.model.Control;
import io.github.gustavo2358.air.model.Operation;
import io.github.gustavo2358.air.model.Sequence;
import io.github.gustavo2358.analysis.solver.DataflowResult;
import io.github.gustavo2358.analysis.solver.Direction;
import io.github.gustavo2358.analysis.structure.AnalysisSession;
import io.github.gustavo2358.analysis.structure.ContextView;
import io.github.gustavo2358.analysis.structure.ProgramIndex;
import java.util.*;
import static io.github.gustavo2358.analysis.query.ObservationBatch.*;

/** One ordered prefix/suffix union per context/Sequence. No solver, cache or instruction snapshots. */
public final class BatchReplayer {
    private BatchReplayer() { }
    @FunctionalInterface public interface Transfer<S> { S apply(S state,Operation operation); }
    public interface Projection<S,T,V> {
        boolean supports(PointQuery<T> query);
        V project(PointQuery<T> query,S state);
    }
    private record Group(ContextView context,ProgramIndex.Node node) { }
    private record Selected<T>(PointQuery<T> query,int boundary) { }
    public static <S,T,V> ObservationBatch<T,V> materialize(AnalysisSession session,DataflowResult<S> result,
            Direction direction,S unreachable,Iterable<PointQuery<T>> requests,Comparator<T> subjects,
            Transfer<S> transfer,Projection<S,T,V> projection) {
        Objects.requireNonNull(session);Objects.requireNonNull(result);Objects.requireNonNull(direction);
        if(result.status()!=DataflowResult.Status.STABLE)throw new IllegalArgumentException("stable result required");
        for(var context:session.contexts())if(!result.contains(context,context.entryNode()))
            throw new IllegalArgumentException("stable result belongs to another session or context selection");
        var queries=new LinkedHashSet<PointQuery<T>>();long raw=0;
        for(var q:requests){queries.add(Objects.requireNonNull(q));raw=Math.incrementExact(raw);}
        var ordered=new ArrayList<>(queries);
        ordered.sort(Comparator.<PointQuery<T>,ProgramPoint>comparing(PointQuery::point,ProgramPoint.ORDER).thenComparing(PointQuery::subject,subjects));
        var answers=new HashMap<PointQuery<T>,Observation<T,V>>();
        var groups=new LinkedHashMap<Group,List<Selected<T>>>();
        var count=new Counts();
        try {
            for(var q:ordered) {
                var p=q.point();var context=session.context(p.entry());
                PointReason reason=context==null?PointReason.CONTEXT_NOT_SELECTED:null;
                var site=p.operation()==null?null:session.index().site(p.operation());
                if(reason==null&&!projection.supports(q))reason=PointReason.UNSUPPORTED_SUBJECT;
                if(reason==null&&p.kind()!=ProgramPoint.Kind.ENTRY) {
                    if(site==null)reason=PointReason.UNKNOWN_OPERATION;
                    else if(!site.owner().id().equals(p.entry().unit()))reason=PointReason.FOREIGN_UNIT;
                    else if(p.kind()==ProgramPoint.Kind.OUTCOME)reason=PointReason.OUTCOME_UNAVAILABLE;
                    else if(p.kind()==ProgramPoint.Kind.AFTER) {
                        if(site.isTerminator())reason=PointReason.AFTER_TERMINATOR;
                        else if(p.outcome()!=Control.NormalOutcome.INSTANCE)reason=PointReason.OUTCOME_UNAVAILABLE;
                    }
                }
                if(reason!=null) { answers.put(q,new Observation<>(q,QueryStatus.UNSUPPORTED_POINT,reason,null));continue; }
                if(p.kind()==ProgramPoint.Kind.ENTRY) {
                    answers.put(q,value(q,projection.project(q,result.in(context,context.entryNode()))));continue;
                }
                int boundary=p.kind()==ProgramPoint.Kind.AFTER?Math.incrementExact(site.offset()):site.offset();
                count.maxOffset=Math.max(count.maxOffset,site.offset());
                groups.computeIfAbsent(new Group(context,session.index().sequence(site.sequence().label())),ignored->new ArrayList<>()).add(new Selected<>(q,boundary));
            }
            boolean forward=direction==Direction.FORWARD;
            for(var group:groups.entrySet()) {
                var context=group.getKey().context();var sequence=((io.github.gustavo2358.analysis.cfg.domain.CfgNode.SequenceNode)group.getKey().node().source()).source();
                ProgramIndex.Node node=session.index().sequence(sequence.label());
                var selected=group.getValue();selected.sort(Comparator.comparingInt(Selected<T>::boundary));
                if(!forward)Collections.reverse(selected);
                if(!result.contains(context,node)) {
                    for(var q:selected)answers.put(q.query(),value(q.query(),projection.project(q.query(),unreachable)));
                    continue;
                }
                S state=forward?result.in(context,node):result.out(context,node);
                int cursor=forward?0:Math.incrementExact(sequence.instructions().size());
                count.groups=Math.incrementExact(count.groups);
                for(var q:selected) {
                    while(cursor!=q.boundary()) {
                        int offset=forward?cursor:cursor-1;
                        Operation operation=offset==sequence.instructions().size()?sequence.terminator():sequence.instructions().get(offset);
                        count.operations=Math.incrementExact(count.operations);
                        state=Objects.requireNonNull(transfer.apply(state,operation));
                        cursor+=forward?1:-1;
                    }
                    answers.put(q.query(),value(q.query(),projection.project(q.query(),state)));
                }
            }
            var output=new ArrayList<Observation<T,V>>();long answered=0,unsupported=0;
            for(var q:ordered) {
                var answer=Objects.requireNonNull(answers.get(q));output.add(answer);
                if(answer.status()==QueryStatus.VALUE)answered=Math.incrementExact(answered);else unsupported=Math.incrementExact(unsupported);
            }
            return new ObservationBatch<>(Status.COMPLETE,null,output,new Metrics(raw,ordered.size(),count.groups,count.operations,count.maxOffset,answered,unsupported,0,0));
        } catch(ObservationException failure) {
            return new ObservationBatch<>(Status.FAILED,"OBSERVATION_ERROR",List.of(),new Metrics(raw,ordered.size(),count.groups,count.operations,count.maxOffset,0,0,ordered.size(),1));
        }
    }
    private static <T,V> Observation<T,V> value(PointQuery<T> q,V value) { return new Observation<>(q,QueryStatus.VALUE,null,value); }
    private static final class Counts { long groups,operations,maxOffset; }
}
