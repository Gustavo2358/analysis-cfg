package io.github.gustavo2358.analysis.dependencies;

import io.github.gustavo2358.analysis.solver.*;
import java.util.*;

/** Native executable application facts plus complete managed SOURCE authority.
 * Owns derived source facts and the general native CALL result; the admitted
 * source, checked program and backend remain borrowed. Compatibility preparation
 * may explicitly borrow caller-managed base facts. No SOURCE enrichment bridge. */
public final class ManagedDependencyResult extends SourceAdmissionSnapshot.Dependent {
    private volatile SourceAdmissionSnapshot admission;
    private SnapshotProgram program;
    private DependencyResult executable;
    private ManagedExecutableDependencyResult executableOwner;
    private SourceCorrelationSnapshot correlations;
    private SourceQualifiedSnapshot qualified;
    private SourceTargetPlan plan;
    private SourceProgramSnapshot programs;
    private AnalysisResources.Reservation control;
    private long nativeFiles;
    private volatile boolean closed,ready;
    private ManagedDependencyResult(SourceAdmissionSnapshot source,SnapshotProgram program,DependencyResult executable,AnalysisResources.Reservation control){admission=source;this.program=program;this.executable=executable;this.control=control;}
    static ManagedDependencyResult prepare(SnapshotProgram program,SourceAdmissionSnapshot source,DependencyResult executable,List<DependencyInput.StatementCorrelation> links,PageStore pages,AnalysisResources resources) {
        return prepare(program,source,executable,links,null,pages,resources);
    }
    static ManagedDependencyResult prepare(SnapshotProgram program,SourceAdmissionSnapshot source,DependencyResult executable,StatementCorrelations links,PageStore pages,AnalysisResources resources) {
        Objects.requireNonNull(links);return prepare(program,source,executable,null,links,pages,resources);
    }
    static ManagedDependencyResult prepare(SnapshotProgram program,SourceAdmissionSnapshot source,ManagedExecutableDependencyResult executable,List<DependencyInput.StatementCorrelation> links,PageStore pages,AnalysisResources resources) {
        executable.requireContext(program,resources);return prepare(program,source,executable.result(),links,null,pages,resources,executable);
    }
    static ManagedDependencyResult prepare(SnapshotProgram program,SourceAdmissionSnapshot source,ManagedExecutableDependencyResult executable,StatementCorrelations links,PageStore pages,AnalysisResources resources) {
        Objects.requireNonNull(links);executable.requireContext(program,resources);return prepare(program,source,executable.result(),null,links,pages,resources,executable);
    }
    private static ManagedDependencyResult prepare(SnapshotProgram program,SourceAdmissionSnapshot source,DependencyResult executable,List<DependencyInput.StatementCorrelation> links,StatementCorrelations typed,PageStore pages,AnalysisResources resources) {
        return prepare(program,source,executable,links,typed,pages,resources,null);
    }
    private static ManagedDependencyResult prepare(SnapshotProgram program,SourceAdmissionSnapshot source,DependencyResult executable,List<DependencyInput.StatementCorrelation> links,StatementCorrelations typed,PageStore pages,AnalysisResources resources,ManagedExecutableDependencyResult owned) {
        source.requireContext(pages,resources);program.admission();if(!executable.publication().equals(program.publicationId()))throw new IllegalArgumentException("foreign executable publication");
        var capacity=resources.reserve(AnalysisResources.Pool.RESIDENT,2048,AnalysisResources.Phase.DEMAND);ManagedDependencyResult result=null;
        try {
            result=new ManagedDependencyResult(source,program,executable,capacity);result.executableOwner=owned;var registration=capacity;capacity=null;registration.onOwnerClose(result::close);
            result.correlations=typed==null?SourceCorrelationSnapshot.prepare(source,program,links,pages,resources):SourceCorrelationSnapshot.prepare(source,program,typed,pages,resources);result.qualified=SourceQualifiedSnapshot.prepare(source,pages,resources);
            result.plan=owned==null?SourceTargetPlan.prepare(result.correlations,result.qualified,executable,pages,resources):SourceTargetPlan.prepare(result.correlations,result.qualified,owned,pages,resources);result.programs=SourceProgramSnapshot.prepare(result.plan,result.qualified,pages,resources);
            for(long u=0,n=result.qualified.unitCount();u<n;u++)result.nativeFiles=Math.addExact(result.nativeFiles,result.qualified.count(u,SourceQualifiedSnapshot.Kind.NATIVE_FILE));
            result.ready=true;source.attach(result,pages,resources);result.open();return result;
        }catch(RuntimeException|Error failure){if(result!=null)result.abort(failure);else if(capacity!=null)try{capacity.close();}catch(RuntimeException|Error cleanup){suppress(failure,cleanup);}throw failure;}
    }
    private void open(){if(closed||!ready)throw new IllegalStateException("managed dependency result expired or incomplete");try{admission.snapshot();program.admission();if(executableOwner!=null)executableOwner.result();qualified.admission();programs.count();}catch(RuntimeException|Error failure){abort(failure);throw failure;}}
    public SourceAdmissionSnapshot admission(){open();return admission;}
    public SnapshotProgram program(){open();return program;}
    public DependencyResult executable(){open();return executable;}
    /** Productive SOURCE owns this result; explicit compatibility may return null. */
    public ManagedExecutableDependencyResult executableOwner(){open();return executableOwner;}
    public SourceQualifiedSnapshot qualified(){open();return qualified;}
    public SourceProgramSnapshot programs(){open();return programs;}
    public long nativeFileCount(){open();return nativeFiles;}
    public boolean partial(){open();return executable.partial()||programs.globalReasonCount()!=0;}
    /** These are the same fixed product metrics, not a cardinality-dependent map. */
    public static final List<String> SOURCE_METRIC_KEYS=List.of("sourceQualifiedResolvedByExistingQuery","qualifiedComputedOccurrences","conditionalSourceValueRuns","conditionalSourceValueQueries","conditionalSourceWorkItems","conditionalSourceResourceLimits","targetResolutionRequests");
    public long sourceMetric(String key){open();return switch(key){case "sourceQualifiedResolvedByExistingQuery"->programs.reusedByExistingQuery();case "qualifiedComputedOccurrences"->programs.qualifiedComputedOccurrences();case "conditionalSourceValueRuns"->programs.conditionalRuns();case "conditionalSourceValueQueries"->programs.conditionalQueries();case "conditionalSourceWorkItems"->programs.conditionalWorkItems();case "conditionalSourceResourceLimits"->0;case "targetResolutionRequests"->programs.count();default->throw new IllegalArgumentException("unknown source metric");};}
    private static void suppress(Throwable primary,Throwable cleanup){if(primary!=cleanup)primary.addSuppressed(cleanup);}
    private void abort(Throwable primary){try{close();}catch(RuntimeException|Error cleanup){suppress(primary,cleanup);}}
    @Override public void close() {
        AutoCloseable[] owned;
        synchronized(this){if(closed)return;closed=true;ready=false;owned=new AutoCloseable[]{programs,plan,qualified,correlations,executableOwner,control};programs=null;plan=null;qualified=null;correlations=null;executableOwner=null;control=null;admission=null;program=null;executable=null;}
        var parent=owner;if(parent!=null)parent.release(this);Throwable failure=null;for(var value:owned)if(value!=null)try{value.close();}catch(Exception|Error cleanup){if(failure==null)failure=cleanup;else suppress(failure,cleanup);}
        if(failure instanceof RuntimeException error)throw error;if(failure instanceof Error error)throw error;if(failure!=null)throw new IllegalStateException("managed dependency cleanup",failure);
    }
}
