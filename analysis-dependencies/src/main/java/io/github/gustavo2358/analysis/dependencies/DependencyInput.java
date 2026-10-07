package io.github.gustavo2358.analysis.dependencies;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.dependencies.source.QualifiedSourceDependencies;
import java.util.*;

/** Explicit memory port. Physical adapters verify the snapshot digests before construction. */
public final class DependencyInput {
    private final Publication publication;
    private final Optional<QualifiedSourceDependencies> source;
    private final List<StatementCorrelation> correlations;
    private final Optional<io.github.gustavo2358.air.validation.AirValidator.CheckedPublication> checked;
    private final Optional<SourceQualifiedDependencyResult> preparedSource;
    private final List<QualifiedDependencyOccurrence> occurrences;
    public Publication publication(){return publication;}
    public Optional<QualifiedSourceDependencies> source(){return source;}
    public List<StatementCorrelation> correlations(){return correlations;}
    public Optional<io.github.gustavo2358.air.validation.AirValidator.CheckedPublication> checked(){return checked;}
    /** Validated against this exact input; cannot be supplied by the caller. */
    public Optional<SourceQualifiedDependencyResult> preparedSource(){return preparedSource;}
    @Override public boolean equals(Object other){return other instanceof DependencyInput i&&publication.equals(i.publication)&&source.equals(i.source)&&correlations.equals(i.correlations)&&checked.equals(i.checked);}
    @Override public int hashCode(){return ((31*publication.hashCode()+source.hashCode())*31+correlations.hashCode())*31+checked.hashCode();}
    @Override public String toString(){return "DependencyInput[publication="+publication+", source="+source+", correlations="+correlations+", checked="+checked+"]";}

    public DependencyInput(Publication publication,Optional<QualifiedSourceDependencies> source,List<StatementCorrelation> correlations) {
        this(publication,source,correlations,Optional.empty());
    }
    public record StatementCorrelation(QualifiedSourceDependencies.StatementId source,OperationId operation,
                                       LabelId label,OriginId origin) {
        public StatementCorrelation { Objects.requireNonNull(source);Objects.requireNonNull(operation);Objects.requireNonNull(label);Objects.requireNonNull(origin); }
    }
    public DependencyInput(Publication publication,Optional<QualifiedSourceDependencies> source,List<StatementCorrelation> correlations,
                           Optional<io.github.gustavo2358.air.validation.AirValidator.CheckedPublication> checked) {
        this.publication=Objects.requireNonNull(publication);this.source=Objects.requireNonNull(source);this.correlations=List.copyOf(correlations);
        this.checked=Objects.requireNonNull(checked);
        checked.ifPresent(c -> { if (c.publication() != publication) throw new IllegalArgumentException("validation snapshot mismatch"); });
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
        var bySource=new HashMap<QualifiedSourceDependencies.StatementId,Set<OperationId>>();
        var expectedOrigins=new HashSet<OriginId>();for(var link:this.correlations)expectedOrigins.add(link.origin());
        try(var ancestry=this.correlations.isEmpty()?null:new OriginAncestryIndex(origins,expectedOrigins)) {
        for(var link:this.correlations) {
            if(!seen.add(link))throw new IllegalArgumentException("duplicate source/AIR correlation");
            var occurrence=occurrences.get(link.source());var operation=operations.get(link.operation());
            if(occurrence==null||!occurrence.namespace().equals("PROGRAM")||operation==null||!link.label().equals(labels.get(link.operation())))
                throw new IllegalArgumentException("source/AIR correlation ownership");
            var previous=owners.putIfAbsent(link.operation(),link.source());
            if(previous!=null&&!previous.equals(link.source()))throw new IllegalArgumentException("conflicting source/AIR correlation");
            if(!ancestry.derivedFrom(operation.header().origin(),link.origin()))throw new IllegalArgumentException("source/AIR origin correlation");
            if(operation instanceof Operations.Invoke invoke) {
                if(!CallDependencyPlan.selected(invoke))throw new IllegalArgumentException("source/AIR target category");
                boolean literal=invoke.target() instanceof Interactions.LiteralTarget;
                if(literal!=occurrence.targetKind().equals("LITERAL"))throw new IllegalArgumentException("source/AIR target kind");
                var namespace=literal?((Interactions.LiteralTarget)invoke.target()).namespace():((Interactions.ComputedTarget)invoke.target()).namespace();
                if(namespace.equals("cics.program")!=occurrence.technology().equals("CICS"))throw new IllegalArgumentException("source/AIR technology");
                if(literal&&occurrence.values().stream().noneMatch(v->v.value().equals(((Interactions.LiteralTarget)invoke.target()).name())))
                    throw new IllegalArgumentException("source/AIR literal disagreement");
            }
            bySource.computeIfAbsent(link.source(),ignored->new HashSet<>()).add(link.operation());
        }
        }
        preparedSource=source.map(evidence->SourceQualifiedDependencyResult.admit(evidence,publication.id().localId()));
        this.occurrences=prepareOccurrences(bySource);
    }
    /** Qualification and canonical correlation happen before any target query executes. */
    public List<QualifiedDependencyOccurrence> occurrences(){return occurrences;}
    private List<QualifiedDependencyOccurrence> prepareOccurrences(Map<QualifiedSourceDependencies.StatementId,Set<OperationId>> bySource) {
        var invokes=new LinkedHashMap<OperationId,Operations.Invoke>();
        for(var unit:publication.units())for(var sequence:unit.sequences())if(sequence.terminator() instanceof Operations.Invoke i&&CallDependencyPlan.selected(i))invokes.put(i.header().id(),i);
        var mapped=new HashSet<OperationId>();var result=new ArrayList<QualifiedDependencyOccurrence>();
        if(source.isPresent())for(var u:source.get().units()) {
            var assumed=preparedSource.orElseThrow().affected(u);
            for(var o:u.occurrences())if(o.namespace().equals("PROGRAM")) {
                var sites=new ArrayList<OperationId>();
                for(var operation:bySource.getOrDefault(o.id(),Set.of()))if(invokes.containsKey(operation))sites.add(operation);
                sites.sort(Comparator.comparing(OperationId::localId));mapped.addAll(sites);
                var values=new ArrayList<String>(o.values().size());for(var value:o.values())values.add(value.value());
                boolean assumedControl=false;for(var qualification:o.qualifications())if(assumed.contains(qualification)){assumedControl=true;break;}
                result.add(new QualifiedDependencyOccurrence(Optional.of(o.id()),o.id().unit().canonicalProgramName(),o.technology(),o.nameProfile(),o.targetKind(),values,u.controlAvailable()?o.qualifications():List.of(),sites,o.valueRemainder(),assumedControl));
            }
        }
        invokes.forEach((id,i)->{if(!mapped.contains(id)) {
            boolean literal=i.target() instanceof Interactions.LiteralTarget;
            var namespace=literal?((Interactions.LiteralTarget)i.target()).namespace():((Interactions.ComputedTarget)i.target()).namespace();
            boolean cics=namespace.equals("cics.program");
            result.add(new QualifiedDependencyOccurrence(Optional.empty(),id.unit().localId(),cics?"CICS":"COBOL",cics?CicsNameInterpreter.PROFILE:CallNameInterpreter.PROFILE,literal?"LITERAL":"COMPUTED",List.of(),List.of(),List.of(id),!literal));
        }});
        return List.copyOf(result);
    }
}
