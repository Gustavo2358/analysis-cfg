package io.github.gustavo2358.analysis.solver;

import java.util.function.IntPredicate;
import java.util.function.IntUnaryOperator;

/** Structurally shared AND/complement logic, not functional canonical identities.
 * Exact decisions encode only the requested fanin cones with acyclic gate clauses.
 * All cardinality-dependent storage is paged; the supplied backend is borrowed.
 * A query owns and releases its decision formula. No decision cutoff is semantic. */
final class PagedBooleanCircuit implements AutoCloseable {
    private static final int INPUT=1,AND=2,EVALUATE=0,SUBSTITUTE=1;
    // Fixed actual valuations, including dense/sparse complements. Samples only
    // nominate exact comparisons: agreeing samples can never establish equality.
    private static final int[] SAMPLE_DEPTHS={1,2,3,4,6,8,10,12,16,20,24,28,32};
    static final int SAMPLE_WORDS=2+2*SAMPLE_DEPTHS.length;
    private final PageStore pages;
    private final AnalysisResources resources;
    private final AnalysisResources.Reservation controls;
    private CanonicalTupleArena nodes;
    private BooleanCircuitDecisions decisions;
    private BooleanCircuitView decisionView;
    private long[] tuple;
    private int decisionVariables;
    private long sampleWords;
    private boolean closed,failed;

    PagedBooleanCircuit(PageStore pages,AnalysisResources resources) {
        this.pages=pages;this.resources=resources;
        controls=resources.reserve(AnalysisResources.Pool.RESIDENT,1536,AnalysisResources.Phase.CONTROL);
        try {
            tuple=new long[6+SAMPLE_WORDS];
            nodes=new CanonicalTupleArena(pages,resources,AnalysisResources.Phase.CONTROL,tuple.length,new int[]{2,4});
            decisionView=new BooleanCircuitView() {
                public long normalize(long root){open();valid(root);return root;}
                public int primary(long handle){return nodes.field(handle,0)==INPUT?(int)nodes.field(handle,1):-1;}
                public long left(long handle){return child(handle,0);}
                public long right(long handle){return child(handle,1);}
            };
        }catch(RuntimeException|Error failure){
            try{if(nodes!=null)nodes.close();}catch(RuntimeException cleanup){failure.addSuppressed(cleanup);}
            controls.close();throw failure;
        }
    }
    private void open(){if(closed||failed)throw new IllegalStateException("Boolean circuit closed or aborted");}
    private void valid(long root){if(root<0)throw new IllegalArgumentException("negative circuit root");if(root>=2)nodes.field(root>>>1,0);}
    private PagedLongArray array(){return new PagedLongArray(pages,Long.MAX_VALUE,resources,AnalysisResources.Phase.CONTROL);}
    long variable(int key) {
        open();if(key<0)throw new IllegalArgumentException("negative primary key");
        try {
            tuple[0]=INPUT;tuple[1]=key;tuple[2]=tuple[3]=tuple[4]=tuple[5]=0;
            writePrimarySamples(key,tuple,6);
            sampleWords+=SAMPLE_WORDS;
            return nodes.intern(tuple)<<1;
        }catch(AnalysisResources.Exhausted|PageStore.Failure failure){failed=true;throw failure;}
    }
    long not(long root) {
        open();try{valid(root);return root^1;}
        catch(AnalysisResources.Exhausted|PageStore.Failure failure){failed=true;throw failure;}
    }
    private long child(long handle,int side){return (nodes.field(handle,side==0?2:4)<<1)|nodes.field(handle,side==0?3:5);}
    /** Only syntactic absorption is established here. Other laws need exact decisions. */
    private long absorb(long literal,long compound) {
        if(compound<2||nodes.field(compound>>>1,0)!=AND)return -1;
        long a=child(compound>>>1,0),b=child(compound>>>1,1);
        if((compound&1)==0)return a==literal||b==literal?compound:-1;
        return a==(literal^1)||b==(literal^1)?literal:-1;
    }
    long and(long a,long b) {
        open();try {
            valid(a);valid(b);
            if(a==0||b==0||a==(b^1))return 0;
            if(a==1||a==b)return b;if(b==1)return a;
            long absorbed=absorb(a,b);if(absorbed>=0)return absorbed;
            absorbed=absorb(b,a);if(absorbed>=0)return absorbed;
            if(a>b){long swap=a;a=b;b=swap;}
            tuple[0]=AND;tuple[1]=0;tuple[2]=a>>>1;tuple[3]=a&1;tuple[4]=b>>>1;tuple[5]=b&1;
            for(int word=0;word<SAMPLE_WORDS;word++)tuple[6+word]=sample(a,word)&sample(b,word);
            sampleWords+=SAMPLE_WORDS;
            return nodes.intern(tuple)<<1;
        }catch(AnalysisResources.Exhausted|PageStore.Failure failure){failed=true;throw failure;}
    }
    long or(long a,long b){open();return not(and(not(a),not(b)));}
    private static long sampleBits(int key,int depth) {
        long bits=((long)key*0x9e3779b97f4a7c15L)^((long)depth*0xd1b54a32d192ed03L);
        bits=(bits^(bits>>>30))*0xbf58476d1ce4e5b9L;
        bits=(bits^(bits>>>27))*0x94d049bb133111ebL;
        return bits^(bits>>>31);
    }
    static long primarySample(int key,int word) {
        if(key<0||word<0||word>=SAMPLE_WORDS)throw new IllegalArgumentException("foreign sample key/channel");
        if(word<2)return word==0?0:-1;
        int depth=SAMPLE_DEPTHS[(word-2)/2];long bits=-1;
        for(int level=1;level<=depth;level++)bits&=sampleBits(key,level);
        return (word&1)==0?bits:~bits;
    }
    static void writePrimarySamples(int key,long[] target,int offset) {
        target[offset]=0;target[offset+1]=-1;long low=-1;int channel=0;
        for(int depth=1;depth<=32;depth++) {
            low&=sampleBits(key,depth);
            if(depth==SAMPLE_DEPTHS[channel]) {
                target[offset+2+2*channel]=low;target[offset+3+2*channel]=~low;
                if(++channel==SAMPLE_DEPTHS.length)break;
            }
        }
    }
    long sample(long root,int word) {
        open();if(word<0||word>=SAMPLE_WORDS)throw new IllegalArgumentException("foreign sample channel");
        try {
            valid(root);if(root<2)return root==0?0:-1;
            long bits=nodes.field(root>>>1,6+word);return (root&1)==0?bits:~bits;
        }catch(AnalysisResources.Exhausted|PageStore.Failure failure){failed=true;throw failure;}
    }
    long sampleWordsCalculated(){open();return sampleWords;}
    boolean test(long root,IntPredicate valuation) {
        open();try {
            valid(root);if(root<2)return root==1;
            try(var memo=array();var pending=array()) {
                return walk(root,EVALUATE,valuation,null,memo,pending)==1;
            }
        }catch(AnalysisResources.Exhausted|PageStore.Failure failure){failed=true;throw failure;}
    }
    /** Binding -1 leaves a key free, zero/one substitute its Boolean value simultaneously. */
    long restrict(long root,IntUnaryOperator bindings) {
        open();try {
            valid(root);if(root<2)return root;
            try(var memo=array();var pending=array()) {
                return walk(root,SUBSTITUTE,null,bindings,memo,pending);
            }
        }catch(AnalysisResources.Exhausted|PageStore.Failure failure){failed=true;throw failure;}
    }
    long restrict(long root,int key,boolean value){return restrict(root,k->k==key?(value?1:0):-1);}
    /** Memo stores the result for the positive underlying handle, plus one for zero. */
    private long walk(long root,int mode,IntPredicate valuation,IntUnaryOperator bindings,
                      PagedLongArray memo,PagedLongArray pending) {
        long top=1;pending.set(0,root>>>1);
        while(top>0) {
            long handle=pending.get(top-1);
            if(memo.get(handle)!=0){pending.set(--top,0);continue;}
            long result;
            if(nodes.field(handle,0)==INPUT) {
                int key=(int)nodes.field(handle,1);
                if(mode==EVALUATE)result=valuation.test(key)?1:0;
                else {
                    int binding=bindings.applyAsInt(key);
                    if(binding< -1||binding>1)throw new IllegalArgumentException("binding must be -1,0,1");
                    result=binding<0?handle<<1:binding;
                }
            }else {
                long a=child(handle,0),b=child(handle,1),left=memo.get(a>>>1),right=memo.get(b>>>1);
                if(left==0){pending.set(top++,a>>>1);continue;}
                if(right==0){pending.set(top++,b>>>1);continue;}
                left=(left-1)^(a&1);right=(right-1)^(b&1);
                if(mode==EVALUATE)result=left&right;
                else result=and(left,right);
            }
            memo.set(handle,result+1);pending.set(--top,0);
        }
        return (memo.get(root>>>1)-1)^(root&1);
    }
    int lastDecisionVariables(){open();return decisionVariables;}
    private void decisionStorage(){if(decisions==null)decisions=new BooleanCircuitDecisions(pages,resources,decisionView);}
    boolean satisfiable(long root) {
        open();try {
            valid(root);decisionVariables=0;if(root<2)return root==1;
            decisionStorage();boolean result=decisions.satisfiable(root);decisionVariables=decisions.lastDecisionVariables();return result;
        }catch(AnalysisResources.Exhausted|PageStore.Failure failure){failed=true;throw failure;}
    }
    boolean equivalent(long a,long b) {
        open();try {
            valid(a);valid(b);decisionVariables=0;if(a==b)return true;if(a==(b^1))return false;
            decisionStorage();boolean result=decisions.equivalent(a,b);decisionVariables=decisions.lastDecisionVariables();return result;
        }catch(AnalysisResources.Exhausted|PageStore.Failure failure){failed=true;throw failure;}
    }
    long retain(long root) {
        open();try{valid(root);return root<2?0:nodes.retain(root>>>1);}
        catch(AnalysisResources.Exhausted|PageStore.Failure failure){failed=true;throw failure;}
    }
    void release(long token) {
        open();try{if(token!=0)nodes.release(token);}
        catch(AnalysisResources.Exhausted|PageStore.Failure failure){failed=true;throw failure;}
    }
    void collect() {
        open();try{nodes.collect();}
        catch(AnalysisResources.Exhausted|PageStore.Failure failure){failed=true;throw failure;}
    }
    long retainedNodes(){open();return nodes.size();}
    @Override public void close() {
        if(closed)return;closed=true;
        RuntimeException failure=ActivationSolver.closeResource(decisions,null);
        failure=ActivationSolver.closeResource(nodes,failure);
        nodes=null;decisions=null;decisionView=null;tuple=null;controls.close();if(failure!=null)throw failure;
    }
}
