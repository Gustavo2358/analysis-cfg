package io.github.gustavo2358.analysis.dataflow;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.application.ConsumerRegistration;
import io.github.gustavo2358.analysis.plan.*;
import io.github.gustavo2358.analysis.query.*;
import io.github.gustavo2358.analysis.structure.AnalysisSession;
import io.github.gustavo2358.analysis.values.*;
import java.util.*;

/** One read of the indexed Assign bucket; selection follows actual destinations, never declarations. */
public final class DefaultValuePlan {
    private DefaultValuePlan() { }
    public record Selection(List<ConsumerRegistration<ObservedValueFact>> registrations,Map<String,Long> metrics) {
        public Selection { registrations=List.copyOf(registrations); metrics=Map.copyOf(metrics); }
    }
    public static Selection select(AnalysisSession session) {
        var byUnit=new HashMap<UnitId,Map<LabelId,Map<ObjectId,OperationId>>>();
        long destinations=0,visits=0,queries=0;
        for(var site:session.index().sites(Operations.Assign.class)) {
            visits=Math.incrementExact(visits);
            var assign=(Operations.Assign)site.operation();
            if(assign.destination() instanceof Places.ObjectPlace place) {
                destinations=Math.incrementExact(destinations);
                byUnit.computeIfAbsent(site.owner().id(),ignored->new HashMap<>())
                    .computeIfAbsent(site.sequence().label(),ignored->new HashMap<>())
                    .put(place.object(),site.sequence().terminator().header().id());
            }
        }
        var registrations=new ArrayList<ConsumerRegistration<ObservedValueFact>>();
        var contexts=new ArrayList<>(session.contexts());
        contexts.sort(Comparator.comparing(c->c.entry().id(),AnalysisKey.ENTRY_ORDER));
        for(var context:contexts) {
            var entry=context.entry().id(); var sequences=byUnit.get(entry.unit());
            if(sequences==null||sequences.isEmpty()) continue;
            var key=PossibleValuesProvider.key(entry);
            // Length-prefixed identity is injective even when IDs contain delimiters.
            String identity=part(entry.publication().localId())+part(entry.unit().localId())+part(entry.localId());
            var batch=PossibleValuesProvider.batch("default-values:"+identity,key);
            var requested=new ArrayList<PointQuery<ObjectId>>();
            var bySequence=new HashMap<LabelId,List<PointQuery<ObjectId>>>();
            for(var sequence:sequences.entrySet()) {
                var local=new ArrayList<PointQuery<ObjectId>>();
                sequence.getValue().forEach((subject,terminator)->local.add(new PointQuery<>(ProgramPoint.before(entry,terminator),subject)));
                local.sort(Comparator.comparing(PointQuery<ObjectId>::point,ProgramPoint.ORDER).thenComparing(PointQuery::subject,new PossibleValuesProvider().subjectOrder()));
                bySequence.put(sequence.getKey(),List.copyOf(local)); requested.addAll(local);
                queries=Math.addExact(queries,local.size());
            }
            var frozen=Map.copyOf(bySequence);
            List<SiteInterest> interests=new ArrayList<>();
            for(var kind:List.of(Operations.Return.class,Operations.Jump.class,Operations.Branch.class,Operations.Halt.class))
                interests.add(new SiteInterest(kind,entry,s->frozen.containsKey(s.sequence())));
            registrations.add(new ConsumerRegistration<>(new ConsumerPlan("observed-values:"+identity,List.of(key),List.of(batch.id())),
                interests,List.of(new ObservationRequest<>(batch,requested)),(site,facts,sink)-> {
                    for(var query:frozen.get(site.sequence())) {
                        var lookup=facts.lookup(batch,query);
                        if(lookup.status()!=io.github.gustavo2358.analysis.consumers.PreparedFacts.LookupStatus.AVAILABLE)
                            throw new IllegalStateException("planned value observation unavailable");
                        sink.emit(new ObservedValueFact(site.sequence(),batch.id(),query));
                    }
                }));
        }
        return new Selection(registrations,Map.of("defaultPlanDestinations",destinations,"defaultPlanQueries",queries,"defaultPlanAssignVisits",visits));
    }
    private static String part(String text) { return text.length()+":"+text; }
}
