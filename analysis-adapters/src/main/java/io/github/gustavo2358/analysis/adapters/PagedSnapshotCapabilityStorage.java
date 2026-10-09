package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.model.AirSnapshotBuilder;
import io.github.gustavo2358.air.validation.SnapshotCapabilities;
import io.github.gustavo2358.analysis.solver.*;
import java.util.Objects;

/** Managed exact manifest/name-policy capability sets. */
public final class PagedSnapshotCapabilityStorage implements SnapshotCapabilities.Storage {
    private static final AnalysisResources.Phase PHASE=AnalysisResources.Phase.VALIDATION;
    private final AnalysisResources resources;private final AnalysisResources.Reservation resident;
    private CanonicalTupleArena names,required,policies;private long[] pair;private boolean closed,failed;
    public PagedSnapshotCapabilityStorage(PageStore pages,AnalysisResources resources){Objects.requireNonNull(pages);this.resources=Objects.requireNonNull(resources);resident=resources.reserve(AnalysisResources.Pool.RESIDENT,256,PHASE);try{pair=new long[2];names=new CanonicalTupleArena(pages,resources,PHASE,2,new int[0]);required=new CanonicalTupleArena(pages,resources,PHASE,2,new int[0]);policies=new CanonicalTupleArena(pages,resources,PHASE,2,new int[0]);}catch(RuntimeException|Error failure){closeSuppressed(failure);throw failure;}}
    private void key(long a,long b){if(a<=0||b<=0)throw new IllegalArgumentException("positive capability keys required");pair[0]=a;pair[1]=b;}
    private boolean add(CanonicalTupleArena set,long a,long b){key(a,b);if(set.find(pair)!=0)return false;set.intern(pair);return true;}
    private boolean has(CanonicalTupleArena set,long a,long b){key(a,b);return set.find(pair)!=0;}
    public synchronized boolean firstName(long list,long name){open();try{return add(names,list,name);}catch(RuntimeException|Error failure){failed=true;throw failure;}}
    public synchronized void require(long name,long version){open();try{add(required,name,version);}catch(RuntimeException|Error failure){failed=true;throw failure;}}
    public synchronized boolean required(long name,long version){open();try{return has(required,name,version);}catch(RuntimeException|Error failure){failed=true;throw failure;}}
    public synchronized void addNamePolicy(long name,long version){open();try{add(policies,name,version);}catch(RuntimeException|Error failure){failed=true;throw failure;}}
    public synchronized boolean hasNamePolicy(long name,long version){open();try{return has(policies,name,version);}catch(RuntimeException|Error failure){failed=true;throw failure;}}
    public synchronized AirSnapshotBuilder.Lease claim(long bytes){open();try{var lease=resources.reserve(AnalysisResources.Pool.RESIDENT,bytes,PHASE);return lease::close;}catch(RuntimeException|Error failure){failed=true;throw failure;}}
    private void open(){if(closed||failed)throw new IllegalStateException("paged capability storage is closed or aborted");}
    private void closeSuppressed(Throwable failure){try{close();}catch(RuntimeException|Error cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}}
    @Override public synchronized void close(){if(closed)return;closed=true;Throwable failure=null;try{if(names!=null)names.close();}catch(RuntimeException|Error cleanup){failure=cleanup;}try{if(required!=null)required.close();}catch(RuntimeException|Error cleanup){if(failure==null)failure=cleanup;else if(failure!=cleanup)failure.addSuppressed(cleanup);}try{if(policies!=null)policies.close();}catch(RuntimeException|Error cleanup){if(failure==null)failure=cleanup;else if(failure!=cleanup)failure.addSuppressed(cleanup);}names=required=policies=null;pair=null;resident.close();if(failure instanceof RuntimeException error)throw error;if(failure instanceof Error error)throw error;}
}
