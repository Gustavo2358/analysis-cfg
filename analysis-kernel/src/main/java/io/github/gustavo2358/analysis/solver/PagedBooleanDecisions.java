package io.github.gustavo2358.analysis.solver;

/** Exact incremental clausal decisions. Watched propagation and first-UIP learning
 * follow Een/Sorensson SAT2003; storage, variable ordering and ownership are local.
 * All cardinality-dependent state spills. Operational interruptions never return UNSAT.
 * The backend is borrowed. Learned clauses remain owned until this engine closes. */
final class PagedBooleanDecisions implements AutoCloseable {
    private static final int VW=11,CW=8;
    private static final int VALUE=0,LEVEL=1,REASON=2,SEEN=3,MODEL=4,ACTIVITY=5,POSITION=6;
    private static final long DERIVED=-1;
    private static final int LEFT=9,RIGHT=10;
    private static final int LENGTH=0,BASE=1,NEXT0=2,PREV0=3,NEXT1=4,PREV1=5,LEARNED=6,LOCKED=7;
    private final AnalysisResources.Reservation controls;
    private long[] clauseInput,assumptionInput;
    private PagedLongArray variables,clauses,literals,trail,levels,heap,buffer,deferredUnits;
    // Independent heap addresses are below Integer.MAX_VALUE. The disjoint
    // paged suffix holds the required definition directory, not a resident cache.
    private long gateBase=1L<<32,gateCapacity=16,gateCount;
    private int variableCount,heapSize,levelCount,bufferSize;
    private long clauseCount,literalCount,trailSize,propagationHead,epoch,activityEpoch,deferredSize;
    private long watchedVisits,learnedCount;
    private boolean closed,failed,permanentUnsat,hasModel;

    PagedBooleanDecisions(PageStore pages,AnalysisResources resources) {
        controls=resources.reserve(AnalysisResources.Pool.RESIDENT,1024,AnalysisResources.Phase.CONTROL);
        try {
            clauseInput=new long[3];assumptionInput=new long[2];
            variables=array(pages,resources);clauses=array(pages,resources);literals=array(pages,resources);
            trail=array(pages,resources);levels=array(pages,resources);heap=array(pages,resources);
            buffer=array(pages,resources);deferredUnits=array(pages,resources);
        }catch(RuntimeException|Error failure){try{close();}catch(RuntimeException cleanup){failure.addSuppressed(cleanup);}throw failure;}
    }
    private static PagedLongArray array(PageStore pages,AnalysisResources resources) {
        return new PagedLongArray(pages,Long.MAX_VALUE,resources,AnalysisResources.Phase.CONTROL);
    }
    private void open(){if(closed||failed)throw new IllegalStateException("Boolean decisions closed or aborted");}
    private long v(int variable,int field){return variables.get((long)(variable-1)*VW+field);}
    private void v(int variable,int field,long value){variables.set((long)(variable-1)*VW+field,value);}
    private long c(long clause,int field){return clauses.get((clause-1)*CW+field);}
    private void c(long clause,int field,long value){clauses.set((clause-1)*CW+field,value);}
    private void valid(long literal) {
        long variable=literal>>>1;
        if(literal<2||variable>variableCount)throw new IllegalArgumentException("foreign Boolean literal");
    }
    private long nextEpoch(){if(epoch==Long.MAX_VALUE/2)throw new PageStore.Failure(PageStore.Reason.INVALID_HANDLE,"decision mark space exhausted");return ++epoch;}
    int newVariable() {
        return variable(true);
    }
    int allocatedVariables(){open();return variableCount;}
    private int variable(boolean independent) {
        open();try {
            if(variableCount==Integer.MAX_VALUE)throw new PageStore.Failure(PageStore.Reason.INVALID_HANDLE,"decision variable space exhausted");
            hasModel=false;int variable=++variableCount;
            if(independent)insertHeap(variable);else v(variable,POSITION,DERIVED);
            return variable;
        }catch(AnalysisResources.Exhausted|PageStore.Failure failure){failed=true;throw failure;}
    }
    /** A fresh exact acyclic definition, never a free search dimension. Both
     * inputs precede the output; assigning all independent atoms therefore
     * determines every gate through propagation. Arbitrary CNF callers retain
     * newVariable(), rather than an unchecked non-decision-variable API. */
    long conjunction(long left,long right) {
        open();if(left>=2)valid(left);else if(left<0)throw new IllegalArgumentException("foreign Boolean literal");
        if(right>=2)valid(right);else if(right<0)throw new IllegalArgumentException("foreign Boolean literal");
        if(left==0||right==0||left==(right^1))return 0;
        if(left==1||left==right)return right;if(right==1)return left;
        try{
            long a=Math.min(left,right),b=Math.max(left,right),slot=gateSlot(gateBase,gateCapacity,a,b);
            long known=heap.get(gateBase+slot);if(known!=0)return known<<1;
            if(4*(gateCount+1)>3*gateCapacity){growGates();slot=gateSlot(gateBase,gateCapacity,a,b);}
            int output=variable(false);long result=(long)output<<1;
            v(output,LEFT,a);v(output,RIGHT,b);
            addClause(result^1,left);addClause(result^1,right);addClause(result,left^1,right^1);
            heap.set(gateBase+slot,output);gateCount++;
            return result;
        }catch(AnalysisResources.Exhausted|PageStore.Failure failure){failed=true;throw failure;}
    }
    private static long gateHash(long a,long b){
        long key=a*0x9e3779b97f4a7c15L+Long.rotateLeft(b,31);
        key^=key>>>33;key*=0xff51afd7ed558ccdL;key^=key>>>33;key*=0xc4ceb9fe1a85ec53L;
        return key^(key>>>33);
    }
    private long gateSlot(long base,long capacity,long a,long b){
        long slot=gateHash(a,b)&(capacity-1);
        for(long probes=0;probes<capacity;probes++){
            long stored=heap.get(base+slot);
            if(stored==0||v((int)stored,LEFT)==a&&v((int)stored,RIGHT)==b)return slot;
            slot=(slot+1)&(capacity-1);
        }
        throw new IllegalStateException("full conjunction directory");
    }
    private void growGates(){
        // At most Integer.MAX_VALUE variables exist: capacity<=2^32, and the
        // sum of all table address ranges fits well below the array's limit.
        long oldBase=gateBase,oldCapacity=gateCapacity,newBase=oldBase+oldCapacity,newCapacity=oldCapacity*2;
        for(long slot=0;slot<oldCapacity;slot++){
            int output=(int)heap.get(oldBase+slot);if(output==0)continue;
            long a=v(output,LEFT),b=v(output,RIGHT),target=gateSlot(newBase,newCapacity,a,b);
            heap.set(newBase+target,output);
        }
        gateBase=newBase;gateCapacity=newCapacity;
        // Zeroing retires old radix payload/directory pages. No historical table
        // survives; page allocation admits the old-plus-new peak before growth.
        for(long slot=0;slot<oldCapacity;slot++)heap.set(oldBase+slot,0);
    }
    long watchedClausesVisited(){open();return watchedVisits;}
    long learnedClauses(){open();return learnedCount;}
    boolean value(int variable) {
        open();if(!hasModel)throw new IllegalStateException("no satisfying model");
        if(variable<1||variable>variableCount)throw new IllegalArgumentException("foreign Boolean variable");
        try{return v(variable,MODEL)==2;}
        catch(AnalysisResources.Exhausted|PageStore.Failure failure){failed=true;throw failure;}
    }
    private int valueOf(long literal) {
        int assignment=(int)v((int)(literal>>>1),VALUE);
        if(assignment==0)return 0;
        return (literal&1)==0?assignment:3-assignment;
    }
    void addClause(long... input) {
        addClause(input,input.length);
    }
    void addClause(long a,long b) {open();clauseInput[0]=a;clauseInput[1]=b;addClause(clauseInput,2);}
    void addClause(long a,long b,long c) {open();clauseInput[0]=a;clauseInput[1]=b;clauseInput[2]=c;addClause(clauseInput,3);}
    private void addClause(long[] input,int length) {
        open();hasModel=false;
        for(int i=0;i<length;i++)valid(input[i]);
        if(permanentUnsat)return;
        try {
            long current=nextEpoch();bufferSize=0;boolean satisfied=false;
            for(int i=0;i<length;i++) {
                long literal=input[i];
                int variable=(int)(literal>>>1);long seen=v(variable,SEEN),stamp=(current<<1)|(literal&1);
                if((seen>>>1)==current){if(seen!=stamp){satisfied=true;break;}continue;}
                v(variable,SEEN,stamp);int value=valueOf(literal);
                if(value==2){satisfied=true;break;}
                if(value==0)buffer.set(bufferSize++,literal);
            }
            if(!satisfied) {
                if(bufferSize==0)permanentUnsat=true;
                else if(bufferSize==1)permanentUnsat=!enqueue(buffer.get(0),0)||propagate()!=0;
                else record(bufferSize,false);
            }
            clearBuffer();
        }catch(AnalysisResources.Exhausted|PageStore.Failure failure){failed=true;throw failure;}
    }
    private void clearBuffer(){while(bufferSize>0)buffer.set(--bufferSize,0);}
    private long watchNext(long watch){return c(watch>>>1,(watch&1)==0?NEXT0:NEXT1);}
    private void watchNext(long watch,long next){c(watch>>>1,(watch&1)==0?NEXT0:NEXT1,next);}
    private long watchPrevious(long watch){return c(watch>>>1,(watch&1)==0?PREV0:PREV1);}
    private void watchPrevious(long watch,long previous){c(watch>>>1,(watch&1)==0?PREV0:PREV1,previous);}
    private long head(long literal){return v((int)(literal>>>1),7+(int)(literal&1));}
    private void head(long literal,long value){v((int)(literal>>>1),7+(int)(literal&1),value);}
    private void attach(long watch,long literal) {
        long first=head(literal);watchPrevious(watch,0);watchNext(watch,first);
        if(first!=0)watchPrevious(first,watch);head(literal,watch);
    }
    private void detach(long watch,long literal) {
        long previous=watchPrevious(watch),next=watchNext(watch);
        if(previous==0)head(literal,next);else watchNext(previous,next);
        if(next!=0)watchPrevious(next,previous);watchNext(watch,0);watchPrevious(watch,0);
    }
    private long record(int count,boolean learned) {
        if(clauseCount==Long.MAX_VALUE/CW||count>Long.MAX_VALUE-1-literalCount)
            throw new PageStore.Failure(PageStore.Reason.INVALID_HANDLE,"decision clause space exhausted");
        long clause=++clauseCount,base=literalCount;
        c(clause,LENGTH,count);c(clause,BASE,base);if(learned){c(clause,LEARNED,1);learnedCount++;}
        for(int i=0;i<count;i++)literals.set(literalCount++,buffer.get(i));
        attach(clause<<1,literals.get(base));attach((clause<<1)|1,literals.get(base+1));return clause;
    }
    private boolean enqueue(long literal,long reason) {
        int value=valueOf(literal);if(value!=0)return value==2;
        int variable=(int)(literal>>>1);
        v(variable,VALUE,(literal&1)==0?2:1);v(variable,LEVEL,levelCount);v(variable,REASON,reason);
        if(reason!=0)c(reason,LOCKED,c(reason,LOCKED)+1);
        removeHeap(variable);trail.set(trailSize++,literal);return true;
    }
    /** Only false watched literals are touched; unprocessed links remain intact on conflict. */
    private long propagate() {
        while(propagationHead<trailSize) {
            long falseLiteral=trail.get(propagationHead++)^1;
            for(long watch=head(falseLiteral);watch!=0;) {
                long next=watchNext(watch),clause=watch>>>1,base=c(clause,BASE);
                int position=(int)(watch&1),count=(int)c(clause,LENGTH);
                long other=literals.get(base+(position^1));watchedVisits++;
                if(valueOf(other)!=2) {
                    int replacement=2;while(replacement<count&&valueOf(literals.get(base+replacement))==1)replacement++;
                    if(replacement<count) {
                        long newLiteral=literals.get(base+replacement);detach(watch,falseLiteral);
                        literals.set(base+position,newLiteral);literals.set(base+replacement,falseLiteral);attach(watch,newLiteral);
                    }else if(!enqueue(other,clause))return clause;
                }
                watch=next;
            }
        }
        return 0;
    }
    private void assume(long literal){levels.set(levelCount++,trailSize);if(!enqueue(literal,0))throw new IllegalStateException("conflicting decision");}
    private void cancelUntil(int level) {
        if(levelCount<=level)return;
        long end=levels.get(level);
        while(trailSize>end) {
            long literal=trail.get(--trailSize);trail.set(trailSize,0);int variable=(int)(literal>>>1);
            long reason=v(variable,REASON);if(reason!=0)c(reason,LOCKED,c(reason,LOCKED)-1);
            v(variable,VALUE,0);v(variable,LEVEL,0);v(variable,REASON,0);insertHeap(variable);
        }
        propagationHead=Math.min(propagationHead,trailSize);
        while(levelCount>level)levels.set(--levelCount,0);
    }
    /** Resolution of actual implication reasons; no assumptions become permanent clauses. */
    private int analyze(long conflict) {
        long current=nextEpoch(),index=trailSize;int paths=0,resolved=0,backLevel=0;
        clearBuffer();buffer.set(bufferSize++,-1);
        long literal;
        do {
            long base=c(conflict,BASE);int count=(int)c(conflict,LENGTH);
            for(int i=0;i<count;i++) {
                long p=literals.get(base+i);int variable=(int)(p>>>1),level=(int)v(variable,LEVEL);
                if(variable==resolved||level==0||v(variable,SEEN)==(current<<1))continue;
                v(variable,SEEN,current<<1);bump(variable);
                if(level==levelCount)paths++;
                else {buffer.set(bufferSize++,p);backLevel=Math.max(backLevel,level);}
            }
            do {
                if(index==0)throw new IllegalStateException("conflict lacks current-level implication");
                literal=trail.get(--index);resolved=(int)(literal>>>1);
            }while(v(resolved,SEEN)!=(current<<1));
            v(resolved,SEEN,0);paths--;conflict=v(resolved,REASON);
            if(paths>0&&conflict==0)throw new IllegalStateException("conflict reaches an unresolved decision");
        }while(paths>0);
        buffer.set(0,literal^1);
        if(bufferSize>1) {
            int second=1;for(int i=2;i<bufferSize;i++)if(v((int)(buffer.get(i)>>>1),LEVEL)>v((int)(buffer.get(second)>>>1),LEVEL))second=i;
            long p=buffer.get(1);buffer.set(1,buffer.get(second));buffer.set(second,p);
        }
        return backLevel;
    }
    boolean satisfiable(long... assumptions) {
        return satisfiable(assumptions,assumptions.length);
    }
    boolean satisfiable(){return satisfiable(assumptionInput,0);}
    boolean satisfiable(long a){open();assumptionInput[0]=a;return satisfiable(assumptionInput,1);}
    boolean satisfiable(long a,long b){open();assumptionInput[0]=a;assumptionInput[1]=b;return satisfiable(assumptionInput,2);}
    private boolean satisfiable(long[] assumptions,int length) {
        open();hasModel=false;for(int i=0;i<length;i++)valid(assumptions[i]);if(permanentUnsat)return false;
        try {
            boolean result=true;int rootLevel=0;
            if(propagate()!=0){permanentUnsat=true;return false;}
            for(int i=0;i<length;i++) {
                long literal=assumptions[i];
                int value=valueOf(literal);
                if(value==1){result=false;break;}
                if(value==0){assume(literal);if(propagate()!=0){result=false;break;}}
            }
            rootLevel=levelCount;
            while(result) {
                long conflict=propagate();
                if(conflict!=0) {
                    if(levelCount==rootLevel){result=false;break;}
                    if(activityEpoch==Long.MAX_VALUE)throw new PageStore.Failure(PageStore.Reason.INVALID_HANDLE,"decision activity space exhausted");
                    activityEpoch++;int back=analyze(conflict);cancelUntil(Math.max(rootLevel,back));
                    long asserting=buffer.get(0),reason=0;
                    if(bufferSize==1){deferredUnits.set(deferredSize++,asserting);learnedCount++;}
                    else reason=record(bufferSize,true);
                    if(!enqueue(asserting,reason))throw new IllegalStateException("learned clause is not asserting");
                    clearBuffer();
                }else if(heapSize==0)break;
                else assume((long)(int)heap.get(0)<<1);
            }
            if(result)for(int index=0;index<variableCount;index++){
                int variable=index+1;long value=v(variable,VALUE);
                if(value==0)throw new IllegalStateException("incomplete Boolean model");
                v(variable,MODEL,value);
            }
            cancelUntil(0);
            for(long i=0;i<deferredSize;i++) {
                long literal=deferredUnits.get(i);deferredUnits.set(i,0);
                if(!enqueue(literal,0))permanentUnsat=true;
            }
            deferredSize=0;
            if(!permanentUnsat&&propagate()!=0)permanentUnsat=true;
            if(result&&permanentUnsat)throw new IllegalStateException("satisfying model conflicts with entailed unit");
            hasModel=result;return result;
        }catch(AnalysisResources.Exhausted|PageStore.Failure failure){failed=true;throw failure;}
    }
    // Exact priority queue: conflict recency affects search order only, never admissible assignments.
    private boolean better(int a,int b){long x=v(a,ACTIVITY),y=v(b,ACTIVITY);return x>y||x==y&&a<b;}
    private void heapAt(int at,int variable){heap.set(at,variable);v(variable,POSITION,(long)at+1);}
    private void up(int at) {
        int variable=(int)heap.get(at);
        while(at>0) {int parent=(at-1)>>>1,other=(int)heap.get(parent);if(!better(variable,other))break;heapAt(at,other);at=parent;}
        heapAt(at,variable);
    }
    private void down(int at) {
        int variable=(int)heap.get(at);
        while(at<heapSize/2) {
            int child=at*2+1;if(child+1<heapSize&&better((int)heap.get(child+1),(int)heap.get(child)))child++;
            int other=(int)heap.get(child);if(!better(other,variable))break;heapAt(at,other);at=child;
        }
        heapAt(at,variable);
    }
    private void insertHeap(int variable){long position=v(variable,POSITION);if(position==DERIVED)return;if(position!=0)throw new IllegalStateException("duplicate decision variable");heapAt(heapSize++,variable);up(heapSize-1);}
    private void removeHeap(int variable) {
        if(v(variable,POSITION)==DERIVED)return;
        int at=(int)v(variable,POSITION)-1;if(at<0)throw new IllegalStateException("missing unassigned variable");
        int last=(int)heap.get(--heapSize);heap.set(heapSize,0);v(variable,POSITION,0);
        if(at<heapSize){heapAt(at,last);if(at>0&&better(last,(int)heap.get((at-1)>>>1)))up(at);else down(at);}
    }
    private void bump(int variable){v(variable,ACTIVITY,activityEpoch);int at=(int)v(variable,POSITION)-1;if(at>=0)up(at);}
    @Override public void close() {
        if(closed)return;closed=true;RuntimeException failure=null;
        failure=ActivationSolver.closeResource(deferredUnits,failure);failure=ActivationSolver.closeResource(buffer,failure);
        failure=ActivationSolver.closeResource(heap,failure);failure=ActivationSolver.closeResource(levels,failure);
        failure=ActivationSolver.closeResource(trail,failure);failure=ActivationSolver.closeResource(literals,failure);
        failure=ActivationSolver.closeResource(clauses,failure);failure=ActivationSolver.closeResource(variables,failure);
        controls.close();clauseInput=assumptionInput=null;deferredUnits=buffer=heap=levels=trail=literals=clauses=variables=null;if(failure!=null)throw failure;
    }
}
