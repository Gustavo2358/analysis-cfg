package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.dependencies.*;
import java.io.*;
import java.util.*;
import io.github.gustavo2358.analysis.structure.ProgramStore;
import static io.github.gustavo2358.analysis.dependencies.DependencySiteFact.*;

/** Explicit, deterministic dependency-result mapping. No bean, enum-name or AIR parsing shortcuts. */
public final class DependencyJson {
    public void write(DependencyResult result,OutputStream stream) throws IOException {
        write(result,stream,null);
    }
    /** Native input uses the complete existing fact projection, never the direct-profile lossy projection. */
    public void writeSnapshot(DependencyResult result,ProgramStore.Structural source,OutputStream stream) throws IOException {
        Objects.requireNonNull(source);Objects.requireNonNull(stream);
        if(!source.publicationId().equals(result.publication()))throw new IllegalArgumentException("foreign snapshot result source");
        var budget=source.executionBudget();
        if(budget.isPresent()) {
            // Reserve the bounded UTF8 buffer and fixed root-field scratch before
            // JsonOutput allocation. The managed SOURCE overload owns its own
            // single reservation and does not pass through this compatibility port.
            try(var buffer=budget.orElseThrow().reserve(ProgramStore.CapacityPool.RESIDENT,65536+4096,ProgramStore.ExecutionPhase.ENCODE)) {
                Objects.requireNonNull(buffer);write(result,stream,source);
            }
        }else write(result,stream,source); // Explicit caller-managed resident compatibility.
    }
    /** One funded root buffer; every SOURCE section borrows its complete prepared owner. */
    public void writeSnapshot(ManagedDependencyResult result,SnapshotProgram source,io.github.gustavo2358.analysis.solver.PageStore pages,
            io.github.gustavo2358.analysis.solver.AnalysisResources resources,OutputStream stream)throws IOException {
        Objects.requireNonNull(result);Objects.requireNonNull(source);Objects.requireNonNull(stream);result.admission().snapshot(pages,resources);
        if(result.program()!=source)throw new IllegalArgumentException("foreign native managed result owner");
        try(var buffer=resources.reserve(io.github.gustavo2358.analysis.solver.AnalysisResources.Pool.RESIDENT,65536+4096,io.github.gustavo2358.analysis.solver.AnalysisResources.Phase.ENCODE)) {
            if(buffer.amount()!=65536+4096)throw new IllegalStateException("unfunded managed root buffer");var output=new JsonOutput(stream);
            try(var writer=SourceSnapshotWriter.open(result.admission(),output,pages,resources)){write(result.executable(),source,output,result,writer);writer.verify();result.programs().count();}
        }
    }
    private void write(DependencyResult result,OutputStream stream,ProgramStore.Structural programSource) throws IOException {
        write(result,programSource,new JsonOutput(stream),null,null);
    }
    private void write(DependencyResult result,ProgramStore.Structural programSource,JsonOutput out,ManagedDependencyResult managed,SourceSnapshotWriter writer)throws IOException {
        boolean snapshot=programSource!=null;
        // Native v3 borrows scalar headers from the existing paged catalogue.
        // Only the explicit non-native compatibility API builds a resident map.
        java.util.function.Function<OperationId,Evidence.CoverageStatus> coverage=identity->null;
        if(programSource instanceof SnapshotProgram nativeProgram) {
            coverage=identity->nativeProgram.invokeCoverage(identity).orElse(null);
        }else if(snapshot) {
            var legacyCoverage=new HashMap<OperationId,Evidence.CoverageStatus>();
            for(var unit:programSource.units())for(var sequence:unit.sequences()) {
                var terminator=sequence.terminator();
                if(terminator instanceof Operations.Invoke)legacyCoverage.put(terminator.header().id(),terminator.header().coverage());
            }
            coverage=legacyCoverage::get;
        }
        var siteCoverage=coverage;
        var document=object("schema","analysis-dependency-result","version",result.sourceQualifiedDependencies().map(s->Set.of("1.1.0","1.2.0","1.3.0","1.4.0","1.5.0","1.6.0").contains(s.evidence().version())?"2.7.0":"2.6.0").orElse("2.5.0"),"airVersion",version(result.airVersion()),
            "publication",id(result.publication()),"interpretationProfile","per-site","valuesProfile","scalar-text-effects@1",
            "modelScope",result.structuralScope()?"STRUCTURAL_AIR_OCCURRENCES":"KNOWN_GRAPH_ENTRY","publicationInventory",inventory(result.publicationInventory()),
            "sites",result.sites().stream().sorted(Comparator.comparing(DependencySiteFact::entry,io.github.gustavo2358.analysis.plan.AnalysisKey.ENTRY_ORDER).thenComparing(f->f.operation().localId())).map(s->{
                var value=site(s,true);if(snapshot)value.put("coverage",Objects.requireNonNull(siteCoverage.apply(s.operation()),"missing original site coverage").name());return value;
            }),
            "edges",result.edges().stream().sorted(Comparator.comparing(DependencyResult.Edge::entry,io.github.gustavo2358.analysis.plan.AnalysisKey.ENTRY_ORDER).thenComparing(e->e.site().localId()).thenComparing(e->e.candidate().referenceName()).thenComparing(e->e.candidate().rawValue())).map(e->object("caller",id(e.caller()),"entry",id(e.entry()),"site",id(e.site()),"candidate",candidate(e.candidate()),"openSite",e.openSite())),
            "metrics",result.metrics(),"origins",orderedMetadata(result.origins(),Comparator.comparing(o->o.id().localId())).map(DependencyJson::origin),
            "artifacts",orderedMetadata(result.artifacts(),Comparator.comparing(a->a.id().localId())).map(a->object("id",id(a.id()),"logicalName",a.logicalName(),"contentDigest",a.contentDigest().orElse(null))),
            "sourceUncertaintyRefs",ids(result.sourceUncertaintyRefs()));
        boolean partial=managed==null?result.partial():managed.partial();document.put("analysisStatus",partial?"PARTIAL":"COMPLETE");document.put("analysisReasons",managed==null?result.analysisReasons().stream().distinct().sorted():writer.globalReasons(managed.programs()));
        if(snapshot) {
            document.put("version","3.0.0");document.put("modelScope","VALIDATED_SNAPSHOT_DEPENDENCY");
            document.put("analysisStatus",partial||result.publicationInventory()!=Evidence.InventoryStatus.COMPLETE?"PARTIAL":"COMPLETE");
            document.put("generalAnalysisMetrics",managed==null?result.metrics():writer.metrics(managed.programs()));
            document.put("metrics",object("sites",(long)result.sites().size(),"candidates",result.sites().stream().mapToLong(s->s.candidates().size()).sum()));
        }
        document.put("analysisBoundary","COBOL_SOURCE_ONLY");document.put("fileDependencies",FileDependencyJson.value(result.fileDependencies()));
        document.put("sourceDependencies",SourceDependencyJson.value(result.sourceDependencies()));
        result.sourceQualifiedDependencies().ifPresent(source->document.put("sourceQualifiedDependencies",object(
            "analysisBoundary","NON_EXECUTABLE_SOURCE","evidence",QualifiedSourceJson.value(source.evidence()),
            "occurrences",source.occurrences().stream().map(o->object("occurrence",QualifiedSourceJson.value(o.occurrence()),"status",o.status().name(),"valueRemainder",o.valueRemainder(),"interpretationRemainder",o.interpretationRemainder(),
                "candidates",o.candidates().stream().map(c->object("referenceName",c.referenceName(),"rawValue",c.rawValue(),"occurrence",QualifiedSourceJson.value(c.occurrence()),"qualifications",c.qualifications())))))));
        if(managed!=null)document.put("sourceQualifiedDependencies",writer.qualified(managed.qualified()));
        var dependencies=object("programs",managed==null?result.programDependencies().stream().map(DependencyJson::program):writer.programs(managed.programs()));
        if(managed!=null&&managed.nativeFileCount()>0)dependencies.put("files",writer.nativeFiles(managed.qualified()));
        result.sourceQualifiedDependencies().filter(s->!s.nativeFiles().isEmpty()).ifPresent(s->dependencies.put("files",s.nativeFiles().stream().map(f->object(
            "analysisBoundary","NON_EXECUTABLE_SOURCE","source",QualifiedSourceJson.value(f.source()),"status",f.status().name(),"remainder",f.remainder(),
            "candidates",f.candidates().stream().map(c->object("referenceName",c.referenceName(),"rawValue",c.rawValue(),"qualifications",c.qualifications()))))));
        document.put("dependencies",dependencies);
        out.value(document);out.finish();
    }
    static Object program(TargetResolver.Resolution resolved) {
        var o=resolved.occurrence();
        var result=object("sourceOccurrence",o.source().map(QualifiedSourceJson::value).orElse(null),"caller",o.caller(),
            "technology",o.technology(),"nameProfile",o.nameProfile(),"targetKind",o.targetKind(),"authorities",resolved.authorities(),
            "qualifications",o.qualifications(),"executableOperations",ids(o.executableOperations()),
            "executableSites",resolved.executableSites().stream().map(s->object("entry",id(s.entry()),"operation",id(s.operation()),"reachability",s.reachability().name(),"valuePoint",s.valuePoint()==null?null:ResultJson.point(s.valuePoint()),"premises",ids(s.premises()),"provenance",ids(s.provenance()))),
            "candidates",resolved.candidates().stream().map(DependencyJson::programCandidate),
            "valueRemainder",resolved.valueRemainder(),"interpretationRemainder",resolved.interpretationRemainder(),"analysisReasons",resolved.analysisReasons());
        if(o.sourceControlRemainder())result.put("controlRemainder",true);
        return result;
    }
    private static Object programCandidate(TargetResolver.Candidate c) {
        var out=object("referenceName",c.referenceName(),"rawValue",c.rawValue(),"supports",supports(c.executableSupports()),"qualifications",c.sourceQualifications());
        if(!c.conditionalSupports().isEmpty())out.put("conditionalSupports",c.conditionalSupports().stream().map(s->object(
            "provider",s.provider(),"analysisBoundary","NON_EXECUTABLE_SOURCE","assumptions",s.assumptions(),
            "evidence",s.evidence().stream().map(e->object("kind",e.kind(),"reference",e.reference(),"provenance",QualifiedSourceJson.value(e.provenance()))),
            "uncertainties",s.uncertainties().stream().map(u->object("id",u.id(),"kind",u.kind(),"provenance",QualifiedSourceJson.value(u.provenance()))))));
        return out;
    }
    private static JsonOutput.Fields site(DependencySiteFact f,boolean extended) {
        var value=object("caller",id(f.caller()),"entry",id(f.entry()),"sequence",id(f.sequence()),"operation",id(f.operation()),"offset",f.offset(),
            "technology",f.technology(),"command",f.command(),"namespace",f.namespace(),"nameProfile",f.nameProfile(),
            "siteOrigin",id(f.siteOrigin()),"targetOrigin",id(f.targetOrigin()),"targetKind",switch(f.targetKind()){case LITERAL->"LITERAL";case COMPUTED->"COMPUTED";},
            "subject",id(f.subject()),"valuePoint",f.valuePoint()==null?null:ResultJson.point(f.valuePoint()),
            "reachability",switch(f.reachability()){case REACHABLE->"REACHABLE";case UNREACHABLE_IN_MODEL->"UNREACHABLE_IN_MODEL";case UNKNOWN->"UNKNOWN";},
            "targetStatus",switch(f.targetStatus()){case RESOLVED_CANDIDATES->"RESOLVED_CANDIDATES";case OPEN_TARGET->"OPEN_TARGET";case UNREACHABLE_IN_MODEL->"UNREACHABLE_IN_MODEL";case UNSUPPORTED_TARGET_EXPRESSION->"UNSUPPORTED_TARGET_EXPRESSION";case UNSUPPORTED_INVOCATION_SHAPE->"UNSUPPORTED_INVOCATION_SHAPE";case ANALYSIS_INCOMPLETE->"ANALYSIS_INCOMPLETE";},
            "rawCandidates",f.rawCandidates().stream().sorted(Comparator.comparing(RawCandidate::rawValue)).map(r->object("rawValue",r.rawValue(),"supports",supports(r.supports()))),
            "candidates",f.candidates().stream().sorted(Comparator.comparing(Candidate::referenceName).thenComparing(Candidate::rawValue)).map(DependencyJson::candidate),
            "modelValueRemainder",f.modelValueRemainder(),"sourceValueRemainder",f.sourceValueRemainder(),"interpretationUnknownRemainder",f.interpretationUnknownRemainder(),
            "effectiveUnknownRemainder",f.effectiveUnknownRemainder(),"openControlRemainder",f.openControlRemainder(),"evidence",ids(f.evidence()),"provenance",ids(f.provenance()),"premises",ids(f.premises()),"uncertaintyRefs",ids(f.uncertaintyRefs()));
        if(extended) {value.put("analysisStatus",switch(f.analysisStatus()){case COMPLETE->"COMPLETE";case PARTIAL->"PARTIAL";});value.put("analysisReasons",f.analysisReasons().stream().distinct().sorted());}
        return value;
    }
    private static Object candidate(Candidate c){return object("referenceName",c.referenceName(),"rawValue",c.rawValue(),"supports",supports(c.supports()));}
    private static Object supports(List<Support> supports){return supports.stream().sorted(Comparator.comparing(Support::producer,WireIds.ORDER).thenComparing(Support::origin,WireIds.ORDER)).map(s->object("kind",switch(s.kind()){case VALUE_PRODUCER->"VALUE_PRODUCER";case CALL_LITERAL->"CALL_LITERAL";case CICS_LITERAL->"CICS_LITERAL";},"producer",id(s.producer()),"origin",id(s.origin()),"premises",ids(s.premises())));}
    private static Object ids(List<? extends Id> ids){return orderedMetadata(ids,WireIds.ORDER).map(DependencyJson::id);}
    private static <T> java.util.stream.Stream<T> orderedMetadata(List<T> values,Comparator<? super T> order){
        return DependencyResult.isBorrowedMetadata(values)?values.stream():values.stream().sorted(order);
    }
    private static Object id(Id id){return id instanceof ArtifactId a?object("domain","artifact","localId",a.localId(),"publication",a.publication().localId()):WireIds.id(id);}
    private static String version(SemanticVersion v){return v.major()+"."+v.minor()+"."+v.patch();}
    private static String inventory(Evidence.InventoryStatus s){return switch(s){case COMPLETE->"COMPLETE";case PARTIAL->"PARTIAL";case UNAVAILABLE->"UNAVAILABLE";};}
    private static Object origin(Origins.Origin o) {
        return switch(o) {
            case Origins.Derived d -> object("id",id(d.id()),"kind","DERIVED","inputs",ids(d.inputs()),"rule",d.rule());
            case Origins.Unavailable u -> object("id",id(u.id()),"kind","UNAVAILABLE","reason",u.reason());
            case Origins.Contractual c -> object("id",id(c.id()),"kind","CONTRACTUAL","authority",c.authority(),"version",c.version());
            case Origins.Written w -> object("id",id(w.id()),"kind","WRITTEN","artifact",id(w.artifact()),"location",w.location().map(DependencyJson::location).orElse(null),"exact",w.exact(),
                "includes",w.includes().stream().map(i->object("including",id(i.including()),"included",id(i.included()),"requestedName",i.requestedName(),"site",i.site().map(DependencyJson::location).orElse(null))));
        };
    }
    private static Object location(Origins.Location l) {
        return switch(l) {
            case Origins.Offsets o -> object("kind","OFFSETS","start",o.start().toString(),"end",o.end().toString(),"unit",o.unit(),"endExclusive",o.endExclusive());
            case Origins.LineColumns p -> {
                var s=p.span();yield object("kind","LINE_COLUMNS","startLine",s.start().line().toString(),"startColumn",s.start().column().toString(),"endLine",s.end().line().toString(),"endColumn",s.end().column().toString(),"lineBase",s.lineBase().toString(),"columnBase",s.columnBase().toString(),
                    "columnUnit",switch(s.columnUnit()){case UNICODE_SCALAR->"UNICODE_SCALAR";case UTF16_CODE_UNIT->"UTF16_CODE_UNIT";case OCTET->"OCTET";},"endExclusive",s.endExclusive());
            }
        };
    }
    private static JsonOutput.Fields object(Object... fields){return new JsonOutput.Fields(fields);}
}
