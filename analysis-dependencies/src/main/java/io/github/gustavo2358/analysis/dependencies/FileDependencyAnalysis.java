package io.github.gustavo2358.analysis.dependencies;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.application.*;
import io.github.gustavo2358.analysis.consumers.*;
import io.github.gustavo2358.analysis.plan.*;
import io.github.gustavo2358.analysis.query.*;
import io.github.gustavo2358.analysis.structure.AnalysisSession;
import java.util.*;
import static io.github.gustavo2358.analysis.dependencies.FileDependencyResult.*;

/** FILE orchestration over the existing shared session and providers. No source access. */
final class FileDependencyAnalysis {
    private FileDependencyAnalysis() { }
    private record Key(EntryId entry,OperationId operation) { }
    static FileDependencyResult prepare(Publication p,AnalysisSession session,PlanningExecution execution,String unavailable){
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
            var kinds=new HashMap<UnitId,Set<Class<? extends Operation>>>();
            for(var indexed:session.index().sites(Operations.Invoke.class))if(FileDependencyConsumer.selected(indexed.operation(),locals))kinds.computeIfAbsent(indexed.owner().id(),k->new HashSet<>()).add(Operations.Invoke.class);
            for(var id:locals){var indexed=session.index().site(id);if(indexed!=null)kinds.computeIfAbsent(indexed.owner().id(),k->new HashSet<>()).add(indexed.operation().getClass());}
            for(var context:session.contexts()){
                var entry=context.entry().id();if(!kinds.containsKey(entry.unit()))continue;
                String key=part(entry.unit().localId())+part(entry.localId());var batch=ReachabilityProvider.batch("file-reach:"+key,entry);
                var interests=kinds.get(entry.unit()).stream().sorted(Comparator.comparing(Class::getName)).map(kind->new SiteInterest(kind,entry,s->FileDependencyConsumer.selected(s.operation(),locals),List.of(new SiteInterest.SiteQuery<>(batch,FileDependencyConsumer::query)))).toList();
                registrations.add(new ConsumerRegistration<>(new ConsumerPlan("file:"+key,List.of(batch.analysisKey()),List.of(batch.id())),interests,List.of(),new FileDependencyConsumer(bindings,batch)));
            }
            var result=execution.execute("file-dependencies@1",execution.plan(registrations));
            for(var outcome:result.analyses())if(outcome.status()==AnalysisOutcome.Status.INVALID_INPUT)throw new DependencyAnalysis.Failure(DependencyAnalysis.Kind.INVALID_INPUT,outcome.reason());
            result.consumers().stream().flatMap(c->c.facts().stream()).forEach(s->retained.put(new Key(s.entry(),s.operation()),s));
            result.metrics().forEach((phase,counts)->counts.forEach((key,value)->metrics.put(phase+"."+key,value)));
        }
        for(var unit:p.units())for(var entry:unit.entries())for(var sequence:unit.sequences()) {
            var operations=new ArrayList<Operation>(sequence.instructions());operations.add(sequence.terminator());
            for(int offset=0;offset<operations.size();offset++) {
                var operation=operations.get(offset);if(!FileDependencyConsumer.selected(operation,locals))continue;
                var view=new SiteView(entry.id(),sequence.label(),offset,operation);
                retained.computeIfAbsent(new Key(entry.id(),operation.header().id()),key->FileDependencyConsumer.site(view,bindings,Optional.empty(),List.of(unavailable==null?"FILE_REACHABILITY_UNAVAILABLE":unavailable)));
            }
        }
        var sites=retained.values().stream().sorted(Comparator.comparing(Site::entry,AnalysisKey.ENTRY_ORDER).thenComparing(s->s.operation().localId())).toList();
        var edges=new ArrayList<Edge>();for(var site:sites)if(site.reachability()!=Reachability.UNREACHABLE_IN_MODEL)for(var c:site.candidates())edges.add(new Edge(site.owner(),site.entry(),site.operation(),c,site.unknownRemainder()));
        return new FileDependencyResult(p.capabilities().required().contains(Capabilities.RESOURCE_BINDINGS)?p.coverage().inventory():Evidence.InventoryStatus.UNAVAILABLE,declarations,sites,edges,metrics);
    }
    private static boolean isFile(Interactions.ResourceDescription d){return switch(d){case Interactions.LiteralTarget t->t.category().equals("file");case Interactions.ComputedResource t->t.category().equals("file");case Interactions.LocalResource t->t.category().equals("file");case Interactions.UnknownResource t->t.category().equals("file");default->false;};}
    private static String sourceKind(String kind){return switch(kind){case "cobol.assignment-name"->"ASSIGNMENT_NAME";case "cobol.sort-comment"->"SORT_COMMENT";case "cobol.unsupported-name"->"UNSUPPORTED";case "cobol.absent-name"->"ABSENT";default->kind;};}
    private static String part(String s){return s.length()+":"+s;}
}
