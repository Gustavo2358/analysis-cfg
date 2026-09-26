package io.github.gustavo2358.analysis.dependencies;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.dependencies.source.QualifiedSourceDependencies;
import java.util.*;

/** Explicit memory port. Physical adapters verify the snapshot digests before construction. */
public record DependencyInput(Publication publication,Optional<QualifiedSourceDependencies> source,
                              List<StatementCorrelation> correlations) {
    public record StatementCorrelation(QualifiedSourceDependencies.StatementId source,OperationId operation,
                                       LabelId label,OriginId origin) {
        public StatementCorrelation { Objects.requireNonNull(source);Objects.requireNonNull(operation);Objects.requireNonNull(label);Objects.requireNonNull(origin); }
    }
    public DependencyInput {
        Objects.requireNonNull(publication);Objects.requireNonNull(source);correlations=List.copyOf(correlations);
        source.ifPresent(s->{if(s.air().size()!=1||!s.air().getFirst().publication().equals(publication.id().localId()))throw new IllegalArgumentException("AIR publication mismatch");});
        var occurrences=new HashMap<QualifiedSourceDependencies.StatementId,QualifiedSourceDependencies.Occurrence>();
        source.ifPresent(s->s.units().forEach(u->u.occurrences().forEach(o->occurrences.put(o.id(),o))));
        var operations=new HashMap<OperationId,Operation>();var labels=new HashMap<OperationId,LabelId>();
        for(var unit:publication.units())for(var sequence:unit.sequences()) {
            for(var instruction:sequence.instructions()){operations.put(instruction.header().id(),instruction);labels.put(instruction.header().id(),sequence.label());}
            operations.put(sequence.terminator().header().id(),sequence.terminator());labels.put(sequence.terminator().header().id(),sequence.label());
        }
        var origins=new HashMap<OriginId,Origins.Origin>();publication.origins().forEach(o->origins.put(o.id(),o));
        var seen=new HashSet<StatementCorrelation>();var owners=new HashMap<OperationId,QualifiedSourceDependencies.StatementId>();
        for(var link:correlations) {
            if(!seen.add(link))throw new IllegalArgumentException("duplicate source/AIR correlation");
            var occurrence=occurrences.get(link.source());var operation=operations.get(link.operation());
            if(occurrence==null||!occurrence.namespace().equals("PROGRAM")||operation==null||!link.label().equals(labels.get(link.operation())))
                throw new IllegalArgumentException("source/AIR correlation ownership");
            var previous=owners.putIfAbsent(link.operation(),link.source());
            if(previous!=null&&!previous.equals(link.source()))throw new IllegalArgumentException("conflicting source/AIR correlation");
            if(!derivedFrom(operation.header().origin(),link.origin(),origins))throw new IllegalArgumentException("source/AIR origin correlation");
            if(operation instanceof Operations.Invoke invoke) {
                if(!CallDependencyPlan.selected(invoke))throw new IllegalArgumentException("source/AIR target category");
                boolean literal=invoke.target() instanceof Interactions.LiteralTarget;
                if(literal!=occurrence.targetKind().equals("LITERAL"))throw new IllegalArgumentException("source/AIR target kind");
                var namespace=literal?((Interactions.LiteralTarget)invoke.target()).namespace():((Interactions.ComputedTarget)invoke.target()).namespace();
                if(namespace.equals("cics.program")!=occurrence.technology().equals("CICS"))throw new IllegalArgumentException("source/AIR technology");
                if(literal&&occurrence.values().stream().noneMatch(v->v.value().equals(((Interactions.LiteralTarget)invoke.target()).name())))
                    throw new IllegalArgumentException("source/AIR literal disagreement");
            }
        }
    }
    private static boolean derivedFrom(OriginId actual,OriginId expected,Map<OriginId,Origins.Origin> origins) {
        if(!origins.containsKey(expected))return false;
        var pending=new ArrayDeque<OriginId>();pending.add(actual);var seen=new HashSet<OriginId>();
        while(!pending.isEmpty()) {var id=pending.removeFirst();if(id.equals(expected))return true;if(!seen.add(id))continue;
            if(origins.get(id) instanceof Origins.Derived d)pending.addAll(d.inputs());}
        return false;
    }
    /** Qualification and canonical correlation happen before any target query executes. */
    public List<QualifiedDependencyOccurrence> occurrences() {
        var invokes=new LinkedHashMap<OperationId,Operations.Invoke>();
        for(var unit:publication.units())for(var sequence:unit.sequences())if(sequence.terminator() instanceof Operations.Invoke i&&CallDependencyPlan.selected(i))invokes.put(i.header().id(),i);
        var mapped=new HashSet<OperationId>();var result=new ArrayList<QualifiedDependencyOccurrence>();
        source.ifPresent(e->e.units().forEach(u->u.occurrences().stream().filter(o->o.namespace().equals("PROGRAM")).forEach(o->{
            var sites=correlations.stream().filter(l->l.source().equals(o.id())&&invokes.containsKey(l.operation())).map(StatementCorrelation::operation).distinct().sorted(Comparator.comparing(OperationId::localId)).toList();
            mapped.addAll(sites);
            result.add(new QualifiedDependencyOccurrence(Optional.of(o.id()),o.id().unit().canonicalProgramName(),o.technology(),o.nameProfile(),o.targetKind(),o.values().stream().map(QualifiedSourceDependencies.Value::value).toList(),u.controlAvailable()?o.qualifications():List.of(),sites,o.valueRemainder()));
        })));
        invokes.forEach((id,i)->{if(!mapped.contains(id)) {
            boolean literal=i.target() instanceof Interactions.LiteralTarget;
            var namespace=literal?((Interactions.LiteralTarget)i.target()).namespace():((Interactions.ComputedTarget)i.target()).namespace();
            boolean cics=namespace.equals("cics.program");
            result.add(new QualifiedDependencyOccurrence(Optional.empty(),id.unit().localId(),cics?"CICS":"COBOL",cics?CicsNameInterpreter.PROFILE:CallNameInterpreter.PROFILE,literal?"LITERAL":"COMPUTED",List.of(),List.of(),List.of(id),!literal));
        }});
        return List.copyOf(result);
    }
}
