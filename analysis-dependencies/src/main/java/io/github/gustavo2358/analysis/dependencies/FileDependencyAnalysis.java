package io.github.gustavo2358.analysis.dependencies;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.application.*;
import io.github.gustavo2358.analysis.consumers.*;
import io.github.gustavo2358.analysis.plan.*;
import io.github.gustavo2358.analysis.query.*;
import io.github.gustavo2358.analysis.structure.AnalysisSession;
import java.util.*;
import io.github.gustavo2358.analysis.values.StorageValuesProvider;
import io.github.gustavo2358.analysis.values.StorageAnalysisMode;
import static io.github.gustavo2358.analysis.dependencies.FileDependencyResult.*;

/** FILE orchestration over the existing shared session and providers. No source access. */
final class FileDependencyAnalysis {
    private FileDependencyAnalysis() { }
    private record Key(EntryId entry,OperationId operation) { }
    static FileDependencyResult prepare(Publication p,AnalysisSession session,PlanningExecution execution,String unavailable,StorageAnalysisMode mode){
        var bindings=new HashMap<OperationId,List<Binding>>();var declarations=new ArrayList<Declaration>();var locals=new HashSet<OperationId>();
        for(var r:p.resources()){
            if(!isFile(r.description()))continue;
            var d=r.declaration().orElse(null);
            String kind=r.description() instanceof Interactions.LiteralTarget?"LITERAL":r.description() instanceof Interactions.LocalResource?"LOCAL":r.description() instanceof Interactions.ComputedResource?"COMPUTED":"UNKNOWN";
            String ns=switch(r.description()){case Interactions.LiteralTarget t->t.namespace();case Interactions.ComputedResource t->t.namespace();case Interactions.UnknownResource t->t.namespace();default->null;};
            String name=r.description() instanceof Interactions.LiteralTarget t&&t.namePolicy() instanceof Interactions.ExactName?t.name():null;
            if(kind.equals("LITERAL")&&name==null)kind="UNKNOWN";
            declarations.add(new Declaration(r.id(),d==null?null:d.owner(),d==null?null:d.name(),d==null?"unavailable":d.classification(),d==null?"UNAVAILABLE":sourceKind(d.nameSource()),kind,ns,name,d==null?List.of():d.objects(),r.origin()));
            if(d!=null)for(var use:d.uses()){bindings.computeIfAbsent(use.operation(),key->new ArrayList<>()).add(new Binding(r.id(),use.role(),use.origin()));if(r.description() instanceof Interactions.LocalResource)locals.add(use.operation());}
        }
        declarations.sort(Comparator.comparing(d->d.id().localId()));
        bindings.values().forEach(bs->bs.sort(Comparator.comparing((Binding b)->b.declaration().localId()).thenComparing(Binding::role)));
        var retained=new HashMap<Key,Site>();var metrics=new TreeMap<String,Long>();
        if(session!=null&&execution!=null){
            var registrations=new ArrayList<ConsumerRegistration<Site>>();
            var routes=new HashMap<OperationId,Integer>();
            for(var indexed:session.index().sites(Operations.Invoke.class)) {
                var invoke=(Operations.Invoke)indexed.operation();if(!FileDependencyConsumer.externalFile(invoke))continue;
                routes.put(invoke.header().id(),(FileValueQuery.selected(invoke,session)?1:0)|(FileValueQuery.contextSelected(invoke,session)?2:0)|(FileValueQuery.selected(invoke,session)&&!FileValueQuery.exactNameArea(invoke,session)?4:0));
            }
            var kinds=new HashMap<UnitId,Set<Class<? extends Operation>>>();
            for(var indexed:session.index().sites(Operations.Invoke.class))if(FileDependencyConsumer.selected(indexed.operation(),locals))kinds.computeIfAbsent(indexed.owner().id(),k->new HashSet<>()).add(Operations.Invoke.class);
            for(var id:locals){var indexed=session.index().site(id);if(indexed!=null)kinds.computeIfAbsent(indexed.owner().id(),k->new HashSet<>()).add(indexed.operation().getClass());}
            for(var context:session.contexts()){
                var entry=context.entry().id();if(!kinds.containsKey(entry.unit()))continue;
                String key=part(entry.unit().localId())+part(entry.localId());var batch=ReachabilityProvider.batch("file-reach:"+key,entry);
                var values=StorageValuesProvider.batch("file-values:"+key,StorageValuesProvider.key(entry,mode));
                for(var kind:kinds.get(entry.unit()).stream().sorted(Comparator.comparing(Class::getName)).toList())for(int route:List.of(0,1,2,3,5,7)) {
                    if(route!=0&&(kind!=Operations.Invoke.class||routes.entrySet().stream().noneMatch(e->e.getKey().unit().equals(entry.unit())&&e.getValue()==route)))continue;
                    boolean dynamic=route!=0;
                    var queries=new ArrayList<SiteInterest.SiteQuery<?,?>>();queries.add(new SiteInterest.SiteQuery<>(batch,FileDependencyConsumer::query));
                    if((route&1)!=0)queries.add(new SiteInterest.SiteQuery<>(values,FileValueQuery::query));
                    if((route&2)!=0)queries.add(new SiteInterest.SiteQuery<>(values,FileValueQuery::contextQuery));
                    var interest=new SiteInterest(kind,entry,s->FileDependencyConsumer.selected(s.operation(),locals)&&routes.getOrDefault(s.operationId(),0)==route,queries);
                    registrations.add(new ConsumerRegistration<>(new ConsumerPlan("file:"+key+":"+kind.getSimpleName()+":"+route,dynamic?List.of(batch.analysisKey(),values.analysisKey()):List.of(batch.analysisKey()),dynamic?List.of(batch.id(),values.id()):List.of(batch.id())),List.of(interest),List.of(),new FileDependencyConsumer(bindings,batch,dynamic?values:null,route)));
                }
            }
            var result=execution.execute("file-dependencies@1",execution.plan(registrations));
            for(var outcome:result.analyses())if(outcome.status()==AnalysisOutcome.Status.INVALID_INPUT)throw new DependencyAnalysis.Failure(DependencyAnalysis.Kind.INVALID_INPUT,outcome.reason());
            result.consumers().stream().flatMap(c->c.facts().stream()).forEach(s->retained.put(new Key(s.entry(),s.operation()),s));
            result.metrics().forEach((phase,counts)->counts.forEach((key,value)->metrics.put(phase+"."+key,value)));
            for(var outcome:result.analyses())outcome.metrics().forEach((key,value)->metrics.merge(outcome.key().implementation()+"."+key,value,Math::addExact));
            metrics.put("possibleValuesPreparations",result.analyses().stream().filter(a->a.key().implementation().equals(StorageValuesProvider.IMPLEMENTATION)).count());
            metrics.put("possibleValuesStable",result.analyses().stream().filter(a->a.key().implementation().equals(StorageValuesProvider.IMPLEMENTATION)&&a.status()==AnalysisOutcome.Status.STABLE).count());
        }
        for(var unit:p.units())for(var entry:unit.entries())for(var sequence:unit.sequences()) {
            var operations=new ArrayList<Operation>(sequence.instructions());operations.add(sequence.terminator());
            for(int offset=0;offset<operations.size();offset++) {
                var operation=operations.get(offset);if(!FileDependencyConsumer.selected(operation,locals))continue;
                var view=new SiteView(entry.id(),sequence.label(),offset,operation);
                retained.computeIfAbsent(new Key(entry.id(),operation.header().id()),key->FileDependencyConsumer.site(view,bindings,Optional.empty(),List.of(unavailable==null?"FILE_REACHABILITY_UNAVAILABLE":unavailable)));
            }
        }
        metrics.putIfAbsent("possibleValuesPreparations",0L);metrics.putIfAbsent("possibleValuesStable",0L);
        var sites=retained.values().stream().sorted(Comparator.comparing(Site::entry,AnalysisKey.ENTRY_ORDER).thenComparing(s->s.operation().localId())).toList();
        var edges=new ArrayList<Edge>();for(var site:sites)if(site.reachability()!=Reachability.UNREACHABLE_IN_MODEL)for(var c:site.candidates())edges.add(new Edge(site.owner(),site.entry(),site.operation(),c,site.unknownRemainder()||site.context()!=null&&site.context().unknownRemainder(),site.context()));
        return new FileDependencyResult(p.capabilities().required().contains(Capabilities.RESOURCE_BINDINGS)?p.coverage().inventory():Evidence.InventoryStatus.UNAVAILABLE,declarations,sites,edges,metrics);
    }
    private static boolean isFile(Interactions.ResourceDescription d){return switch(d){case Interactions.LiteralTarget t->t.category().equals("file");case Interactions.ComputedResource t->t.category().equals("file");case Interactions.LocalResource t->t.category().equals("file");case Interactions.UnknownResource t->t.category().equals("file");default->false;};}
    private static String sourceKind(String kind){return switch(kind){case "cobol.assignment-name"->"ASSIGNMENT_NAME";case "cobol.sort-comment"->"SORT_COMMENT";case "cobol.unsupported-name"->"UNSUPPORTED";case "cobol.absent-name"->"ABSENT";default->kind;};}
    private static String part(String s){return s.length()+":"+s;}
}
