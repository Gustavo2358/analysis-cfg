package io.github.gustavo2358.analysis.solver;

/** Exact decisions over a borrowed circuit view. Required memo, primary-key index,
 * formula and traversal state spill. Optional explicit scopes share definitions and
 * entailed clauses while handles preserve their Boolean meaning; endScope releases
 * all query state before handle reuse. Outside a scope every query is independent.
 * An operational interruption aborts the owner, never produces a Boolean result. */
final class BooleanCircuitDecisions implements AutoCloseable {
    private final PageStore pages;
    private final AnalysisResources resources;
    private final BooleanCircuitView view;
    private final AnalysisResources.Reservation controls;
    private int variables;
    private boolean closed,failed,scoped;
    private Session retained;
    BooleanCircuitDecisions(PageStore pages,AnalysisResources resources,BooleanCircuitView view) {
        this.pages=pages;this.resources=resources;this.view=view;
        controls=resources.reserve(AnalysisResources.Pool.RESIDENT,512,AnalysisResources.Phase.CONTROL);
    }
    private void open(){if(closed||failed)throw new IllegalStateException("circuit decisions closed or aborted");}
    private PagedLongArray array(){return new PagedLongArray(pages,Long.MAX_VALUE,resources,AnalysisResources.Phase.CONTROL);}
    private long normalized(long root) {
        resources.work(1,AnalysisResources.Phase.CONTROL);long result=view.normalize(root);
        if(result<0)throw new PageStore.Failure(PageStore.Reason.CORRUPT,"negative circuit root");
        return result;
    }
    int lastDecisionVariables(){open();return variables;}
    boolean satisfiable(long root){return query(root,0,false);}
    boolean equivalent(long a,long b){return query(a,b,true);}
    /** Caller retains all encountered handle meanings until endScope. New graph
     * definitions may be appended; replacing a definition requires a proof that
     * its Boolean meaning is unchanged. Recycled handles require a new scope. */
    void beginScope(){open();if(scoped)throw new IllegalStateException("nested circuit decision scope");scoped=true;}
    void endScope(){
        if(!scoped)throw new IllegalStateException("missing circuit decision scope");
        scoped=false;Session old=retained;retained=null;
        if(old!=null)try{old.close();}catch(RuntimeException error){failed=true;throw error;}
    }
    private final class Session implements AutoCloseable {
        PagedBooleanDecisions decisions;
        PagedLongArray memo,pending;
        PagedLongIndex primaries;
        Session(){
            try{decisions=new PagedBooleanDecisions(pages,resources);memo=array();pending=array();primaries=new PagedLongIndex(pages,resources,AnalysisResources.Phase.CONTROL);}
            catch(RuntimeException|Error error){try{close();}catch(RuntimeException cleanup){error.addSuppressed(cleanup);}throw error;}
        }
        public void close(){
            RuntimeException failure=null;
            failure=ActivationSolver.closeResource(primaries,failure);primaries=null;
            failure=ActivationSolver.closeResource(pending,failure);pending=null;
            failure=ActivationSolver.closeResource(memo,failure);memo=null;
            failure=ActivationSolver.closeResource(decisions,failure);decisions=null;
            if(failure!=null)throw failure;
        }
    }
    private Session session(){if(retained==null)retained=new Session();return retained;}
    private boolean query(long a,long b,boolean equality) {
        open();variables=0;
        try {
            a=normalized(a);if(equality)b=normalized(b);
            if(equality&&a==b)return true;if(equality&&a==(b^1))return false;
            if(!equality&&a<2)return a==1;
            try(var local=scoped?null:new Session()) {
                Session current=scoped?session():local;
                long left=encode(a,current.decisions,current.memo,current.pending,current.primaries);
                if(!equality)return sat(current.decisions,left);
                long right=encode(b,current.decisions,current.memo,current.pending,current.primaries);
                return !satBoth(current.decisions,left,right^1)&&!satBoth(current.decisions,left^1,right);
            }
        }catch(AnalysisResources.Exhausted|PageStore.Failure failure){failed=true;throw failure;}
    }
    private static boolean sat(PagedBooleanDecisions decisions,long literal) {
        return literal<2?literal==1:decisions.satisfiable(literal);
    }
    private static boolean satBoth(PagedBooleanDecisions decisions,long a,long b) {
        if(a==0||b==0||a==(b^1))return false;
        if(a==1||a==b)return sat(decisions,b);if(b==1)return sat(decisions,a);
        return decisions.satisfiable(a,b);
    }
    private static long encodedChild(PagedLongArray memo,long root) {
        if(root<2)return root;
        long result=memo.get(root>>>1);
        if(result<0)throw new PageStore.Failure(PageStore.Reason.CORRUPT,"cyclic circuit definition");
        return result==0?-1:(result-1)^(root&1);
    }
    private long encode(long root,PagedBooleanDecisions decisions,PagedLongArray memo,
                        PagedLongArray pending,PagedLongIndex primaries) {
        if(root<2)return root;
        long known=encodedChild(memo,root);if(known>=0)return known;
        long top=1;pending.set(0,root>>>1);memo.set(root>>>1,-1);
        while(top>0) {
            long handle=pending.get(top-1),result;int key=view.primary(handle);
            if(key>=0) {
                result=primaries.find(key);
                if(result==0){result=(long)decisions.newVariable()<<1;variables++;primaries.intern(key,result);}
            }else {
                if(key!= -1)throw new PageStore.Failure(PageStore.Reason.CORRUPT,"invalid primary key");
                long leftRoot=normalized(view.left(handle)),rightRoot=normalized(view.right(handle));
                long left=encodedChild(memo,leftRoot),right=encodedChild(memo,rightRoot);
                if(left<0){pending.set(top++,leftRoot>>>1);memo.set(leftRoot>>>1,-1);continue;}
                if(right<0){pending.set(top++,rightRoot>>>1);memo.set(rightRoot>>>1,-1);continue;}
                if(left==0||right==0||left==(right^1))result=0;
                else if(left==1||left==right)result=right;
                else if(right==1)result=left;
                else {
                    result=(long)decisions.newVariable()<<1;variables++;
                    decisions.addClause(result^1,left);decisions.addClause(result^1,right);
                    decisions.addClause(result,left^1,right^1);
                }
            }
            memo.set(handle,result+1);pending.set(--top,0);
        }
        return encodedChild(memo,root);
    }
    @Override public void close(){
        if(closed)return;closed=true;scoped=false;
        RuntimeException failure=ActivationSolver.closeResource(retained,null);retained=null;
        controls.close();if(failure!=null)throw failure;
    }
}
