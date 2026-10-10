package io.github.gustavo2358.analysis.dependencies;

import io.github.gustavo2358.analysis.dependencies.source.*;
import io.github.gustavo2358.analysis.solver.*;
import java.util.Objects;

/**
 * Successful complete SOURCE admission bound to one sealed immutable owner.
 * No resident-model reconstruction. Not an AIR correlation/publication decision.
 * Closing either this certificate or its SOURCE storage expires both. PageStore
 * remains borrowed. Only admit can produce this result, after all mandatory laws.
 */
public final class SourceAdmissionSnapshot implements AutoCloseable {
    private volatile SourceSnapshot source;
    private AnalysisResources.Reservation control;
    private volatile boolean ready;
    private SourceAdmissionSnapshot(SourceSnapshot source,AnalysisResources.Reservation control){this.source=source;this.control=control;}

    /**
     * Transfers a matching unclaimed immutable SOURCE owner on entry. Rejection
     * after claim closes it; a forged/mismatched or already-claimed owner is not
     * stolen. No partial certificate escapes a failed check or teardown.
     */
    public static SourceAdmissionSnapshot admit(SourceSnapshot source,PageStore pages,AnalysisResources resources) {
        Objects.requireNonNull(source);Objects.requireNonNull(pages);Objects.requireNonNull(resources);
        SourceSnapshotBuilder.claimAdmission(source,pages,resources);
        SourceAdmissionSnapshot admission=null;AnalysisResources.Reservation capacity=null;
        try {
            capacity=resources.reserve(AnalysisResources.Pool.RESIDENT,512,AnalysisResources.Phase.VALIDATION);
            admission=new SourceAdmissionSnapshot(source,capacity);capacity=null;
            var registration=admission.control;
            SourceSnapshotBuilder.attachAdmission(source,admission);
            registration.onOwnerClose(admission::close);
            SourceSnapshotRelations.check(source,pages,resources);
            source.root(); // Includes successful teardown of every transient checker.
            if(admission.source==null)throw new IllegalStateException("source admission expired before completion");
            admission.ready=true;return admission;
        }catch(RuntimeException|Error failure) {
            if(admission!=null)try{admission.close();}catch(RuntimeException|Error cleanup){suppress(failure,cleanup);}
            else {
                if(capacity!=null)try{capacity.close();}catch(RuntimeException|Error cleanup){suppress(failure,cleanup);}
                try{source.close();}catch(RuntimeException|Error cleanup){suppress(failure,cleanup);}
            }
            throw failure;
        }
    }
    private static void suppress(Throwable primary,Throwable cleanup){if(primary!=cleanup)primary.addSuppressed(cleanup);}
    /** Borrowed owner-bound typed view; primitive handles have no cross-owner identity. */
    public SourceSnapshot snapshot() {
        var current=source;if(current==null||!ready)throw new IllegalStateException("source admission expired or incomplete");
        current.root();return current;
    }
    public long unitCount() {
        var current=snapshot();long units=current.field(current.root(),SourceShape.ROOT,5);
        return current.size(units,SourceShape.UNIT_EVIDENCE);
    }
    public long unit(long ordinal) {
        var current=snapshot();long units=current.field(current.root(),SourceShape.ROOT,5);
        return current.element(units,SourceShape.UNIT_EVIDENCE,ordinal);
    }
    @Override public void close() {
        SourceSnapshot current;AnalysisResources.Reservation lease;
        synchronized(this){current=source;if(current==null)return;source=null;ready=false;lease=control;control=null;}
        // No certificate monitor is held while entering the storage owner; its
        // close callback may enter here concurrently or re-enter synchronously.
        Throwable failure=null;
        try{current.close();}catch(RuntimeException|Error cleanup){failure=cleanup;}
        try{lease.close();}catch(RuntimeException|Error cleanup){if(failure==null)failure=cleanup;else suppress(failure,cleanup);}
        if(failure instanceof RuntimeException error)throw error;if(failure instanceof Error error)throw error;
    }
}
