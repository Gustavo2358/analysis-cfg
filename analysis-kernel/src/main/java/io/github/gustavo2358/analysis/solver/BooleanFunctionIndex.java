package io.github.gustavo2358.analysis.solver;

/** Live functional nomination and complete native-key aliases over externally owned
 * condition IDs. Samples nominate only; the exact primitive equivalence callback
 * decides every match. Pure native construction never scans earlier native classes.
 * All cardinality-dependent metadata spills. Native tokens belong to the enclosing
 * literal arena: removal returns the token to its owner, which closes that arena
 * after this index on failure/teardown. The page backend remains borrowed. */
final class BooleanFunctionIndex implements AutoCloseable {
    interface Equivalent {boolean test(int left,int right);}
    private static final int PREPARED=1,REGISTERED=2,NATIVE=4;
    private final AnalysisResources resources;
    private final PageStore pages;
    private final Equivalent equivalent;
    private final int nominationWords;
    private final AnalysisResources.Reservation controls;
    private PagedLongArray samples,states,links,nativeKeys;
    private PagedLongIndex aliases,nominees,partitions;
    private long[] stagedNative;
    private long count,equivalenceCalls,linkReads,stagedSupport;
    private int stagedSampleId;
    private boolean stagedNativeBucket;
    private boolean closed,failed;
    BooleanFunctionIndex(PageStore pages,AnalysisResources resources,int nominationWords,Equivalent equivalent) {
        if(nominationWords<0||nominationWords>PagedBooleanCircuit.SAMPLE_WORDS)throw new IllegalArgumentException("foreign nomination width");
        this.pages=pages;this.resources=resources;this.nominationWords=nominationWords;this.equivalent=equivalent;
        controls=resources.reserve(AnalysisResources.Pool.RESIDENT,2048,AnalysisResources.Phase.CONTROL);
        try {
            stagedNative=new long[5];samples=array();states=array();links=array();nativeKeys=array();
            aliases=new PagedLongIndex(pages,resources,AnalysisResources.Phase.CONTROL,this::compareNative);
            nominees=new PagedLongIndex(pages,resources,AnalysisResources.Phase.CONTROL,this::compareNominees);
            partitions=new PagedLongIndex(pages,resources,AnalysisResources.Phase.CONTROL,this::compareSupports);
        }catch(RuntimeException|Error failure){try{close();}catch(RuntimeException cleanup){failure.addSuppressed(cleanup);}throw failure;}
    }
    private PagedLongArray array(){return new PagedLongArray(pages,Long.MAX_VALUE,resources,AnalysisResources.Phase.CONTROL);}
    private void open(){if(closed||failed)throw new IllegalStateException("function index closed or aborted");}
    private void prepared(int id){if(id<2||(states.get(id)&PREPARED)==0)throw new IllegalArgumentException("inactive function ID");}
    private void unregistered(int id){prepared(id);if((states.get(id)&REGISTERED)!=0)throw new IllegalArgumentException("function already registered");}
    private long nativeField(int id,int field){return nativeKeys.get((long)id*8+field);}
    private void nativeField(int id,int field,long value){nativeKeys.set((long)id*8+field,value);}
    private long sampleField(int id,int word){return samples.get((long)id*PagedBooleanCircuit.SAMPLE_WORDS+word);}
    long sample(int id,int word) {
        open();if(word<0||word>=PagedBooleanCircuit.SAMPLE_WORDS)throw new IllegalArgumentException("foreign sample channel");
        try{prepared(id);return sampleField(id,word);}
        catch(AnalysisResources.Exhausted|PageStore.Failure failure){failed=true;throw failure;}
    }
    void prepare(int id,long[] values){prepare(id,values,0,0);}
    /** Nonzero support names a canonical exact essential-key set certified by the
     * caller. Zero means unknown, never the empty support of a constant. */
    void prepare(int id,long[] values,long support,long token) {
        open();if(support<0||token<0)throw new IllegalArgumentException("negative support metadata");
        if(id<2||values.length!=PagedBooleanCircuit.SAMPLE_WORDS)throw new IllegalArgumentException("function ID or sample width");
        try {
            if(states.get(id)!=0)throw new IllegalArgumentException("function slot already prepared");
            for(int word=0;word<values.length;word++)samples.set((long)id*values.length+word,values[word]);
            nativeField(id,6,support);nativeField(id,7,token);states.set(id,PREPARED);
        }catch(AnalysisResources.Exhausted|PageStore.Failure failure){failed=true;throw failure;}
    }
    private int compareNative(long first,long second) {
        if(first==second)return 0;
        for(int field=0;field<5;field++) {
            long a=first==0?stagedNative[field]:nativeField((int)(first-1),field);
            long b=second==0?stagedNative[field]:nativeField((int)(second-1),field);
            int order=Long.compare(a,b);if(order!=0)return order;
        }
        return 0;
    }
    private int compareSamples(long first,long second) {
        if(first==second)return 0;
        for(int word=0;word<nominationWords;word++) {
            int order=Long.compare(sampleField((int)(first-1),word),sampleField((int)(second-1),word));
            if(order!=0)return order;
        }
        return 0;
    }
    private int nativeTag(long key){return key==0?(stagedNativeBucket?1:0):(states.get((int)(key-1))&NATIVE)!=0?1:0;}
    private int compareNominees(long first,long second) {
        if(first==second)return 0;int group=Integer.compare(nativeTag(first),nativeTag(second));
        return group!=0?group:compareSamples(first==0?(long)stagedSampleId+1:first,second==0?(long)stagedSampleId+1:second);
    }
    private int compareSupports(long first,long second) {
        if(first==second)return 0;int group=Integer.compare(nativeTag(first),nativeTag(second));if(group!=0)return group;
        int order=Long.compare(first==0?stagedSupport:nativeField((int)(first-1),6),second==0?stagedSupport:nativeField((int)(second-1),6));
        return order!=0?order:compareSamples(first==0?(long)stagedSampleId+1:first,second==0?(long)stagedSampleId+1:second);
    }
    long support(int id){open();try{prepared(id);return nativeField(id,6);}
        catch(AnalysisResources.Exhausted|PageStore.Failure failure){failed=true;throw failure;}}
    long supportToken(int id){open();try{prepared(id);return nativeField(id,7);}
        catch(AnalysisResources.Exhausted|PageStore.Failure failure){failed=true;throw failure;}}
    int nativeClass(int variable,int low,int high,long literals,int kind) {
        open();try {
            stagedNative[0]=variable;stagedNative[1]=low;stagedNative[2]=high;stagedNative[3]=literals;stagedNative[4]=kind;
            long found=aliases.find(0);return found==0?-1:(int)(found-1);
        }catch(AnalysisResources.Exhausted|PageStore.Failure failure){failed=true;throw failure;}
    }
    private int linked(int id,int field){linkReads++;return (int)links.get((long)id*4+field);}
    private void linked(int id,int field,int value){links.set((long)id*4+field,value);}
    private int search(PagedLongIndex table,int id,int offset,boolean nativeBucket,long support) {
        stagedSampleId=id;stagedNativeBucket=nativeBucket;stagedSupport=support;
        return searchHead(table.find(0),id,offset);
    }
    private int searchHead(long head,int id,int offset) {
        for(int candidate=head==0?0:(int)(head-1);candidate!=0;candidate=linked(candidate,offset)) {
            equivalenceCalls++;if(equivalent.test(id,candidate))return candidate;
        }
        return -1;
    }
    int candidate(int id,boolean nativeInput) {
        open();try {
            unregistered(id);int found;
            if(nativeField(id,6)==0) {
                found=search(nominees,id,0,false,0);
                return found>=0||nativeInput?found:search(nominees,id,0,true,0);
            }
            found=search(partitions,id,2,false,nativeField(id,6));if(found<0)found=search(partitions,id,2,false,0);
            if(found>=0||nativeInput)return found;
            found=search(partitions,id,2,true,nativeField(id,6));return found>=0?found:search(partitions,id,2,true,0);
        }catch(AnalysisResources.Exhausted|PageStore.Failure failure){failed=true;throw failure;}
    }
    private void add(PagedLongIndex table,int id,int offset) {
        long old=table.find((long)id+1);int head=old==0?0:(int)(old-1);
        if(old!=0&&!table.remove(old))throw new IllegalStateException("function bucket missing");
        linked(id,offset,head);linked(id,offset+1,0);if(head!=0)linked(head,offset+1,id);
        table.intern((long)id+1,(long)id+1);
    }
    private void unlink(PagedLongIndex table,int id,int offset) {
        int next=linked(id,offset),previous=linked(id,offset+1);
        if(previous!=0)linked(previous,offset,next);
        else {
            if(!table.remove((long)id+1))throw new IllegalStateException("function bucket missing");
            if(next!=0)table.intern((long)next+1,(long)next+1);
        }
        if(next!=0)linked(next,offset+1,previous);linked(id,offset,0);linked(id,offset+1,0);
    }
    void insertMixed(int id) {
        open();try {unregistered(id);add(nominees,id,0);add(partitions,id,2);states.set(id,PREPARED|REGISTERED);count++;}
        catch(AnalysisResources.Exhausted|PageStore.Failure failure){failed=true;throw failure;}
    }
    private void descriptor(int id,int variable,int low,int high,long literals,int kind,long token) {
        if(nativeClass(variable,low,high,literals,kind)>=0)throw new IllegalArgumentException("native key already registered");
        nativeField(id,0,variable);nativeField(id,1,low);nativeField(id,2,high);
        nativeField(id,3,literals);nativeField(id,4,kind);nativeField(id,5,token);
        aliases.intern((long)id+1,(long)id+1);
    }
    void insertNative(int id,int variable,int low,int high,long literals,int kind,long token) {
        open();try {
            unregistered(id);descriptor(id,variable,low,high,literals,kind,token);
            states.set(id,PREPARED|NATIVE);add(nominees,id,0);add(partitions,id,2);states.set(id,PREPARED|REGISTERED|NATIVE);count++;
        }catch(AnalysisResources.Exhausted|PageStore.Failure failure){failed=true;throw failure;}
    }
    /** Install a caller-proved certificate on a previously unknown live class. */
    void certify(int id,long support,long token) {
        open();if(support<=0||token<0)throw new IllegalArgumentException("invalid support certificate");
        try {
            prepared(id);long state=states.get(id);boolean nativeBucket=(state&NATIVE)!=0;
            if((state&REGISTERED)==0||nativeField(id,6)!=0)throw new IllegalArgumentException("certificate requires an unknown registered class");
            unlink(partitions,id,2);nativeField(id,6,support);nativeField(id,7,token);
            add(partitions,id,2);
        }catch(AnalysisResources.Exhausted|PageStore.Failure failure){failed=true;throw failure;}
    }
    void bindNative(int id,int variable,int low,int high,long literals,int kind,long token) {
        open();try {
            prepared(id);long state=states.get(id);
            if((state&REGISTERED)==0||(state&NATIVE)!=0)throw new IllegalArgumentException("native binding requires a mixed registered class");
            descriptor(id,variable,low,high,literals,kind,token);
            unlink(nominees,id,0);unlink(partitions,id,2);states.set(id,state|NATIVE);add(nominees,id,0);add(partitions,id,2);
        }catch(AnalysisResources.Exhausted|PageStore.Failure failure){failed=true;throw failure;}
    }
    int nativeKind(int id) {
        open();try{return (states.get(id)&NATIVE)==0?-1:(int)nativeField(id,4);}
        catch(AnalysisResources.Exhausted|PageStore.Failure failure){failed=true;throw failure;}
    }
    long descriptorField(int id,int field) {
        open();if(field<0||field>=5)throw new IllegalArgumentException("foreign descriptor column");
        try{if((states.get(id)&NATIVE)==0)throw new IllegalArgumentException("no native descriptor");return nativeField(id,field);}
        catch(AnalysisResources.Exhausted|PageStore.Failure failure){failed=true;throw failure;}
    }
    long remove(int id) {
        open();try {
            prepared(id);long state=states.get(id),token=0;
            if((state&REGISTERED)!=0) {
                if((state&NATIVE)!=0) {
                    if(!aliases.remove((long)id+1))throw new IllegalStateException("native alias missing");
                    token=nativeField(id,5);unlink(nominees,id,0);unlink(partitions,id,2);
                }else {unlink(nominees,id,0);unlink(partitions,id,2);}
                count--;
            }
            for(int word=0;word<PagedBooleanCircuit.SAMPLE_WORDS;word++)samples.set((long)id*PagedBooleanCircuit.SAMPLE_WORDS+word,0);
            for(int field=0;field<8;field++)nativeField(id,field,0);
            states.set(id,0);return token;
        }catch(AnalysisResources.Exhausted|PageStore.Failure failure){failed=true;throw failure;}
    }
    long size(){open();return count;}
    long equivalenceCalls(){open();return equivalenceCalls;}
    long bucketLinksRead(){open();return linkReads;}
    @Override public void close() {
        if(closed)return;closed=true;RuntimeException failure=null;
        failure=ActivationSolver.closeResource(partitions,failure);failure=ActivationSolver.closeResource(nominees,failure);
        failure=ActivationSolver.closeResource(aliases,failure);failure=ActivationSolver.closeResource(nativeKeys,failure);
        failure=ActivationSolver.closeResource(links,failure);failure=ActivationSolver.closeResource(states,failure);
        failure=ActivationSolver.closeResource(samples,failure);controls.close();
        partitions=nominees=aliases=null;nativeKeys=links=states=samples=null;stagedNative=null;
        if(failure!=null)throw failure;
    }
}
