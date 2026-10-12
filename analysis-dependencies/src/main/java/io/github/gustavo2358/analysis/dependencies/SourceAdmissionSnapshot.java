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
    private Dependent dependents;
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
    /** Borrowed typed view only when backend and execution resources match this owner. */
    public SourceSnapshot snapshot(PageStore pages,AnalysisResources resources) {
        var current=snapshot();SourceSnapshotBuilder.requireAdmissionContext(current,this,pages,resources);return current;
    }
    public long unitCount() {
        var current=snapshot();long units=current.field(current.root(),SourceShape.ROOT,5);
        return current.size(units,SourceShape.UNIT_EVIDENCE);
    }
    public long unit(long ordinal) {
        var current=snapshot();long units=current.field(current.root(),SourceShape.ROOT,5);
        return current.element(units,SourceShape.UNIT_EVIDENCE,ordinal);
    }
    /** Intrusive links live in each funded consumer, never an unfunded owner List. */
    abstract static class Dependent implements AutoCloseable {
        volatile SourceAdmissionSnapshot owner;
        private Dependent previous,next;
        @Override public abstract void close();
    }
    void requireContext(PageStore pages,AnalysisResources resources) {
        snapshot(pages,resources);
    }
    /** Funded lifetime link for an outer managed consumer; no authority is delegated. */
    public ConsumerLease attachResource(AutoCloseable dependent,PageStore pages,AnalysisResources resources) {
        Objects.requireNonNull(dependent);requireContext(pages,resources);
        var capacity=resources.reserve(AnalysisResources.Pool.RESIDENT,512,AnalysisResources.Phase.INDEX);
        ConsumerLease lease=null;
        try {
            lease=new ConsumerLease(dependent,capacity);var registration=capacity;capacity=null;
            registration.onOwnerClose(lease::close);attach(lease,pages,resources);
            if(lease.closed)throw new IllegalStateException("source consumer lifetime expired before attachment");
            return lease;
        }catch(RuntimeException|Error failure) {
            if(lease!=null)try{lease.close();}catch(RuntimeException|Error cleanup){suppress(failure,cleanup);}
            else if(capacity!=null)try{capacity.close();}catch(RuntimeException|Error cleanup){suppress(failure,cleanup);}
            throw failure;
        }
    }
    /** Closing the lease releases its dependent, never the borrowed admission. */
    public static final class ConsumerLease extends Dependent {
        private AutoCloseable dependent;
        private AnalysisResources.Reservation control;
        private volatile boolean closed;
        private ConsumerLease(AutoCloseable dependent,AnalysisResources.Reservation control){this.dependent=dependent;this.control=control;}
        @Override public void close() {
            AutoCloseable current;AnalysisResources.Reservation capacity;
            synchronized(this){if(closed)return;closed=true;current=dependent;dependent=null;capacity=control;control=null;}
            var parent=owner;if(parent!=null)parent.release(this);
            Throwable failure=null;
            try{current.close();}catch(Exception|Error cleanup){failure=cleanup;}
            try{capacity.close();}catch(RuntimeException|Error cleanup){if(failure==null)failure=cleanup;else suppress(failure,cleanup);}
            if(failure instanceof RuntimeException error)throw error;if(failure instanceof Error error)throw error;
            if(failure!=null)throw new IllegalStateException("source consumer cleanup",failure);
        }
    }
    void attach(Dependent value,PageStore pages,AnalysisResources resources) {
        var current=snapshot();
        SourceSnapshotBuilder.requireAdmissionContext(current,this,pages,resources);
        // Never acquire the storage monitor while holding the certificate monitor.
        synchronized(this) {
            if(source!=current||!ready)throw new IllegalStateException("source admission expired before consumer attachment");
            if(value.owner!=null)throw new IllegalStateException("source consumer already owned");
            value.owner=this;value.next=dependents;if(dependents!=null)dependents.previous=value;dependents=value;
        }
    }
    synchronized void release(Dependent value) {
        if(value.owner!=this)return;
        if(value.previous==null)dependents=value.next;else value.previous.next=value.next;
        if(value.next!=null)value.next.previous=value.previous;
        value.previous=value.next=null;value.owner=null;
    }
    @Override public void close() {
        SourceSnapshot current;AnalysisResources.Reservation lease;
        synchronized(this){current=source;if(current==null)return;source=null;ready=false;lease=control;control=null;}
        // No certificate monitor is held while entering the storage owner; its
        // close callback may enter here concurrently or re-enter synchronously.
        Throwable failure=null;
        while(true) {
            Dependent dependent;
            synchronized(this){dependent=dependents;if(dependent==null)break;release(dependent);}
            try{dependent.close();}catch(RuntimeException|Error cleanup){if(failure==null)failure=cleanup;else suppress(failure,cleanup);}
        }
        try{current.close();}catch(RuntimeException|Error cleanup){if(failure==null)failure=cleanup;else suppress(failure,cleanup);}
        try{lease.close();}catch(RuntimeException|Error cleanup){if(failure==null)failure=cleanup;else suppress(failure,cleanup);}
        if(failure instanceof RuntimeException error)throw error;if(failure instanceof Error error)throw error;
    }
}
