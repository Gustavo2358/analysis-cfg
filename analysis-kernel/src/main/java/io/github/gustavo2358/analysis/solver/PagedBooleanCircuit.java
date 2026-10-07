package io.github.gustavo2358.analysis.solver;

import java.util.function.IntPredicate;
import java.util.function.IntUnaryOperator;

/** Structurally shared AND/complement logic, not functional canonical identities.
 * Exact decisions encode only the requested fanin cones with acyclic gate clauses.
 * All cardinality-dependent storage is paged; the supplied backend is borrowed.
 * A query owns and releases its decision formula. No decision cutoff is semantic. */
final class PagedBooleanCircuit implements AutoCloseable {
    private static final int INPUT=1,AND=2,EVALUATE=0,SUBSTITUTE=1,ENCODE=2;
    private final PageStore pages;
    private final AnalysisResources resources;
    private final AnalysisResources.Reservation controls;
    private CanonicalTupleArena nodes;
    private long[] tuple;
    private int decisionVariables;
    private boolean closed,failed;

    PagedBooleanCircuit(PageStore pages,AnalysisResources resources) {
        this.pages=pages;this.resources=resources;
        controls=resources.reserve(AnalysisResources.Pool.RESIDENT,512,AnalysisResources.Phase.CONTROL);
        try {
            tuple=new long[6];
            nodes=new CanonicalTupleArena(pages,resources,AnalysisResources.Phase.CONTROL,6,new int[]{2,4});
        }catch(RuntimeException|Error failure){controls.close();throw failure;}
    }
    private void open(){if(closed||failed)throw new IllegalStateException("Boolean circuit closed or aborted");}
    private void valid(long root){if(root<0)throw new IllegalArgumentException("negative circuit root");if(root>=2)nodes.field(root>>>1,0);}
    private PagedLongArray array(){return new PagedLongArray(pages,Long.MAX_VALUE,resources,AnalysisResources.Phase.CONTROL);}
    long variable(int key) {
        open();if(key<0)throw new IllegalArgumentException("negative primary key");
        try {
            tuple[0]=INPUT;tuple[1]=key;tuple[2]=tuple[3]=tuple[4]=tuple[5]=0;
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
            return nodes.intern(tuple)<<1;
        }catch(AnalysisResources.Exhausted|PageStore.Failure failure){failed=true;throw failure;}
    }
    long or(long a,long b){open();return not(and(not(a),not(b)));}
    boolean test(long root,IntPredicate valuation) {
        open();try {
            valid(root);if(root<2)return root==1;
            try(var memo=array();var pending=array()) {
                return walk(root,EVALUATE,valuation,null,null,memo,pending)==1;
            }
        }catch(AnalysisResources.Exhausted|PageStore.Failure failure){failed=true;throw failure;}
    }
    /** Binding -1 leaves a key free, zero/one substitute its Boolean value simultaneously. */
    long restrict(long root,IntUnaryOperator bindings) {
        open();try {
            valid(root);if(root<2)return root;
            try(var memo=array();var pending=array()) {
                return walk(root,SUBSTITUTE,null,bindings,null,memo,pending);
            }
        }catch(AnalysisResources.Exhausted|PageStore.Failure failure){failed=true;throw failure;}
    }
    long restrict(long root,int key,boolean value){return restrict(root,k->k==key?(value?1:0):-1);}
    /** Memo stores the result for the positive underlying handle, plus one for zero. */
    private long walk(long root,int mode,IntPredicate valuation,IntUnaryOperator bindings,
                      PagedBooleanDecisions decisions,PagedLongArray memo,PagedLongArray pending) {
        long top=1;pending.set(0,root>>>1);
        while(top>0) {
            long handle=pending.get(top-1);
            if(memo.get(handle)!=0){pending.set(--top,0);continue;}
            long result;
            if(nodes.field(handle,0)==INPUT) {
                int key=(int)nodes.field(handle,1);
                if(mode==EVALUATE)result=valuation.test(key)?1:0;
                else if(mode==ENCODE){result=(long)decisions.newVariable()<<1;decisionVariables++;}
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
                else if(mode==SUBSTITUTE)result=and(left,right);
                else {
                    result=(long)decisions.newVariable()<<1;decisionVariables++;
                    decisions.addClause(result^1,left);decisions.addClause(result^1,right);
                    decisions.addClause(result,left^1,right^1);
                }
            }
            memo.set(handle,result+1);pending.set(--top,0);
        }
        return (memo.get(root>>>1)-1)^(root&1);
    }
    int lastDecisionVariables(){open();return decisionVariables;}
    boolean satisfiable(long root) {
        open();try {
            valid(root);decisionVariables=0;if(root<2)return root==1;
            try(var decisions=new PagedBooleanDecisions(pages,resources);var memo=array();var pending=array()) {
                long literal=walk(root,ENCODE,null,null,decisions,memo,pending);
                return decisions.satisfiable(literal);
            }
        }catch(AnalysisResources.Exhausted|PageStore.Failure failure){failed=true;throw failure;}
    }
    boolean equivalent(long a,long b) {
        open();try {
            valid(a);valid(b);decisionVariables=0;if(a==b)return true;if(a==(b^1))return false;
            if(a<2)return !satisfiable(b^(a==1?1:0));
            if(b<2)return !satisfiable(a^(b==1?1:0));
            try(var decisions=new PagedBooleanDecisions(pages,resources);var memo=array();var pending=array()) {
                long left=walk(a,ENCODE,null,null,decisions,memo,pending);
                long right=walk(b,ENCODE,null,null,decisions,memo,pending);
                return !decisions.satisfiable(left,right^1)&&!decisions.satisfiable(left^1,right);
            }
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
        try{if(nodes!=null)nodes.close();}finally{nodes=null;tuple=null;controls.close();}
    }
}
