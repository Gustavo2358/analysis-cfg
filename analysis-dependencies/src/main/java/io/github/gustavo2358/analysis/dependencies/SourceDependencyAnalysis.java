package io.github.gustavo2358.analysis.dependencies;

import java.util.*;
import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import static io.github.gustavo2358.analysis.dependencies.SourceDependencyResult.*;

/** A single indexed pass over nominal resources. No CFG/dataflow/physical solver. */
public final class SourceDependencyAnalysis {
    private record Key(UnitId program,Kind kind,String name,String qualification) {}
    public SourceDependencyResult prepare(Publication publication) {
        var origins=new HashMap<OriginId,Origins.Origin>();
        publication.origins().forEach(o->origins.put(o.id(),o));
        var groups=new LinkedHashMap<Key,List<Support>>();var gaps=new TreeSet<String>();
        var inventories=new HashSet<UnitId>();boolean available=false;long occurrences=0;
        for(var resource:publication.resources()) {
            String category=category(resource.description());
            if(!category.startsWith("source-"))continue;
            var declaration=resource.declaration().orElseThrow(()->new IllegalArgumentException("Source resource requires declaration"));
            if(!declaration.objects().isEmpty()||!declaration.uses().isEmpty())throw new IllegalArgumentException("Source dependency cannot be executable");
            if(category.equals("source-dependency-inventory")) {
                if(!(resource.description() instanceof Interactions.LocalResource)||!declaration.name().equals("source-dependencies@1")||!declaration.nameSource().equals("source.inventory@1")
                        ||!Set.of("source.KNOWN","source.PARTIAL","source.INPUT_MISSING").contains(declaration.classification())||!inventories.add(declaration.owner()))throw new IllegalArgumentException("Invalid source inventory");
                available=true;if(!declaration.classification().equals("source.KNOWN"))gaps.add("SOURCE_INVENTORY_PARTIAL");continue;
            }
            if(!(resource.description() instanceof Interactions.LiteralTarget target)||target.namePolicy()!=Interactions.ExactName.INSTANCE||!target.origin().equals(resource.origin()))throw new IllegalArgumentException("Source dependency requires exact nominal target and origin");
            if(category.equals("source-dependency-gap")) {
                if(!target.namespace().equals("source-dependencies@1")||!declaration.nameSource().equals("source.inventory@1"))throw new IllegalArgumentException("Invalid source gap");
                gaps.add(target.name());continue;
            }
            Kind kind=switch(category){case "source-copybook"->Kind.COPYBOOK;case "source-dclgen"->Kind.DCLGEN;case "source-sql_include"->Kind.SQL_INCLUDE;default->throw new IllegalArgumentException("Unknown source dependency kind");};
            Resolution resolution=switch(declaration.classification()){case "source.RESOLVED"->Resolution.RESOLVED;case "source.UNRESOLVED"->Resolution.UNRESOLVED;case "source.CYCLIC"->Resolution.CYCLIC;case "source.IO_ERROR"->Resolution.IO_ERROR;default->throw new IllegalArgumentException("Unknown source resolution");};
            String authority=switch(declaration.nameSource()){case "source.COPY_SYNTAX@1"->"COPY_SYNTAX";case "source.CONFIGURED_DCLGEN@1"->"CONFIGURED_DCLGEN";case "source.CONFIGURED_SQL_INCLUDE@1"->"CONFIGURED_SQL_INCLUDE";case "source.BUILTIN_SQL_INCLUDE@1"->"BUILTIN_SQL_INCLUDE";case "source.UNKNOWN@1"->"UNKNOWN";default->throw new IllegalArgumentException("Unknown source authority");};
            if((kind==Kind.DCLGEN)!=authority.equals("CONFIGURED_DCLGEN")||kind==Kind.DCLGEN&&Set.of("SQLCA","SQLDA").contains(target.name())||(kind==Kind.COPYBOOK)!=authority.equals("COPY_SYNTAX"))throw new IllegalArgumentException("Source classification lacks authority");
            if(!target.name().equals(target.name().toUpperCase(Locale.ROOT))||target.name().isBlank())throw new IllegalArgumentException("Noncanonical source name");
            var origin=origins.get(resource.origin());
            if(origin instanceof Origins.Derived derived&&derived.rule().equals("sp-provenance/original-expanded@1")&&derived.inputs().size()==2)origin=origins.get(derived.inputs().getFirst());
            if(!(origin instanceof Origins.Written written)||written.location().isEmpty())throw new IllegalArgumentException("Source occurrence requires original written evidence");
            var key=new Key(declaration.owner(),kind,target.name(),target.namespace().equals("source-member")?"":target.namespace());
            groups.computeIfAbsent(key,unused->new ArrayList<>()).add(new Support(resource.id(),resource.origin(),written.artifact(),!written.includes().isEmpty(),resolution,resolution==Resolution.RESOLVED?declaration.name():"",authority));
            occurrences++;
            if(resolution!=Resolution.RESOLVED)gaps.add("SOURCE_ARTIFACT_"+resolution);
            if(authority.equals("UNKNOWN"))gaps.add("SQL_INCLUDE_CLASSIFICATION_UNKNOWN");
        }
        if(!available&&groups.isEmpty())return SourceDependencyResult.unavailable();
        if(publication.units().stream().anyMatch(unit->!inventories.contains(unit.id())))gaps.add("SOURCE_UNIT_INVENTORY_UNAVAILABLE");
        var dependencies=new ArrayList<Dependency>();
        for(var entry:groups.entrySet()) {
            var k=entry.getKey();if(!inventories.contains(k.program()))throw new IllegalArgumentException("Source occurrence without inventory");
            var supports=entry.getValue().stream().sorted(Comparator.comparing(s->s.occurrence().localId())).toList();
            dependencies.add(new Dependency(k.program(),k.kind(),k.name(),k.qualification(),supports,supports.stream().anyMatch(s->s.resolution()!=Resolution.RESOLVED||s.authority().equals("UNKNOWN"))));
        }
        dependencies.sort(Comparator.comparing((Dependency d)->d.program().localId()).thenComparing(d->d.kind().name()).thenComparing(Dependency::name).thenComparing(Dependency::qualification));
        return new SourceDependencyResult(available,dependencies,List.copyOf(gaps),occurrences);
    }
    private static String category(Interactions.ResourceDescription description) {
        if(description instanceof Interactions.LiteralTarget t)return t.category();
        if(description instanceof Interactions.LocalResource t)return t.category();
        if(description instanceof Interactions.UnknownResource t)return t.category();
        if(description instanceof Interactions.ComputedResource t)return t.category();
        return "";
    }
}
