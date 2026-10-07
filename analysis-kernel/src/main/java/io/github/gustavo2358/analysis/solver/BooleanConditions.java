package io.github.gustavo2358.analysis.solver;

import java.util.*;

/** Canonical ordered conditions with maximal literal junctions stored as balanced signed sets.
 * General decisions retain Shannon semantics. Operations use explicit stacks, not Java recursion.
 * Literal payload and the exact node catalog use the same borrowed page backend. */
final class BooleanConditions implements AutoCloseable {
    static final int FALSE=0, TRUE=1;
    private final AnalysisResources resources;
    private final PageStore suppliedStore;
    private PageStore literalPages;
    private CanonicalTupleArena literalArena;
    private SignedLiteralSet literals;
    private BooleanNodeStore nodes;
    private final AnalysisResources.Reservation controls;
    private long markEpoch;
    private long literalCollectionThreshold=65536;
    private boolean closed,failed;

    private long allocationsSinceCollection,collectionThreshold=65536;
    private boolean scratch;
    private int scratchFloor;
    private long scratchAllocations;
    private final boolean[] scratchDirty;
    private final int[] scratchSlots;
    private int scratchSlotCount;
    private long peakNodes=2;
    long peakNodes(){return peakNodes;}
    private long scratchCacheVisits;
    long scratchCacheVisits(){return scratchCacheVisits;}

    // Direct-mapped computed table: collisions only evict memoized work. They never
    // identify semantic nodes or truncate conditions. Memory is fixed per manager.
    private final int[] cacheA,cacheB,cacheOperation,cacheResult;
    // Four int arrays: a fixed 1 MiB computed table per execution manager.
    BooleanConditions(){this(65536);}
    BooleanConditions(int cacheSlots){this(cacheSlots,null,null);}
    BooleanConditions(int cacheSlots,AnalysisResources resources,PageStore store){
        if((resources==null)!=(store==null))throw new IllegalArgumentException("store and resources must be supplied together");
        this.resources=resources==null?new AnalysisResources(new AnalysisResources.Limits(Long.MAX_VALUE,Long.MAX_VALUE,Long.MAX_VALUE,Long.MAX_VALUE,Long.MAX_VALUE,Long.MAX_VALUE,Long.MAX_VALUE)):resources;
        suppliedStore=store;
        if(cacheSlots<1||Integer.bitCount(cacheSlots)!=1)throw new IllegalArgumentException("cache size must be a power of two");
        controls=this.resources.reserve(AnalysisResources.Pool.RESIDENT,512L+21L*cacheSlots,AnalysisResources.Phase.CONTROL);
        try {
            cacheA=new int[cacheSlots];cacheB=new int[cacheSlots];cacheOperation=new int[cacheSlots];cacheResult=new int[cacheSlots];
            scratchSlots=new int[cacheSlots];scratchDirty=new boolean[cacheSlots];Arrays.fill(cacheOperation,-1);
        }catch(RuntimeException|Error failure){controls.close();throw failure;}
    }
    private int cacheSlot(int a,int b,int operation){int hash=a*0x9e3779b9+b*0x85ebca6b+operation;hash^=hash>>>16;return hash&(cacheA.length-1);}
    private int cached(int a,int b,int operation){int slot=cacheSlot(a,b,operation);return cacheA[slot]==a&&cacheB[slot]==b&&cacheOperation[slot]==operation?cacheResult[slot]:-1;}
    private int remember(int a,int b,int operation,int result){
        int slot=cacheSlot(a,b,operation);cacheA[slot]=a;cacheB[slot]=b;cacheOperation[slot]=operation;cacheResult[slot]=result;
        if(scratch&&(a>=scratchFloor||result>=scratchFloor||operation<2&&b>=scratchFloor)&&!scratchDirty[slot]) {
            scratchDirty[slot]=true;
            scratchSlots[scratchSlotCount++]=slot;
        }
        return result;
    }
    private void open(){if(closed||failed)throw new IllegalStateException("condition manager closed or aborted");}
    private void pageStorage() {
        open();if(literalPages==null)literalPages=suppliedStore==null?new ResidentPageStore(4096,resources,AnalysisResources.Phase.CONTROL):suppliedStore;
    }
    private void nodeStorage() {
        pageStorage();if(nodes==null)nodes=new BooleanNodeStore(literalPages,resources,64);
    }
    private void literalStorage() {
        open();if(literals!=null)return;pageStorage();
        try {
            literalArena=new CanonicalTupleArena(literalPages,resources,AnalysisResources.Phase.CONTROL,6,new int[]{2,4,5});
            literals=new SignedLiteralSet(literalArena,resources);
        }catch(RuntimeException|Error failure) {
            if(literalArena!=null)try{literalArena.close();}catch(RuntimeException cleanup){failure.addSuppressed(cleanup);}
            literalArena=null;throw failure;
        }
    }
    int variable(int variable){return node(variable,FALSE,TRUE);}
    /** A maximal literal chain has one canonical balanced set rather than all decision prefixes. */
    int node(int variable,int low,int high) {
        open();try{return nodeUnchecked(variable,low,high);}
        catch(AnalysisResources.Exhausted|PageStore.Failure failure){failed=true;throw failure;}
    }
    private int nodeUnchecked(int variable,int low,int high) {
        open();if(low==high)return low;
        boolean union=high==TRUE||low==TRUE;
        int rest=high==TRUE||low==FALSE?low:high;
        if(high==TRUE||low==TRUE||low==FALSE||high==FALSE) {
            long tail=junctionRoot(rest,union);
            if(tail>=0) {
                boolean negative=low==TRUE||high==FALSE;
                return junction(literals.put(tail,variable,negative),union);
            }
        }
        return intern(variable,low,high,0,0);
    }
    private int intern(int variable,int low,int high,long literalRoot,int kind) {
        nodeStorage();int old=nodes.find(variable,low,high,literalRoot,kind);if(old>=0)return old;
        long token=kind==0?0:literalArena.retain(literalRoot>>>1);
        try {
            int id=nodes.create(variable,low,high,literalRoot,kind,token,scratch);
            allocationsSinceCollection++;peakNodes=Math.max(peakNodes,nodes.retainedNodes());return id;
        }catch(RuntimeException|Error failure){if(token!=0)try{literalArena.release(token);}catch(RuntimeException cleanup){failure.addSuppressed(cleanup);}throw failure;}
    }
    /** -1 means a general condition; zero is the neutral literal set. */
    private long junctionRoot(int value,boolean union) {
        if(value<2)return value==(union?FALSE:TRUE)?emptyLiterals():-1;
        int kind=nodes.junction(value),low=nodes.low(value),high=nodes.high(value);
        if(kind!=0)return kind==(union?1:2)?nodes.literals(value):-1;
        if(low<2&&high<2) {literalStorage();return literals.put(0,nodes.variable(value),low==TRUE);}
        return -1;
    }
    private long emptyLiterals(){literalStorage();return 0;}
    private int junction(long root,boolean union) {
        if(root==0)return union?FALSE:TRUE;
        int key=literals.firstKey(root);
        if(literals.size(root)==1)return intern(key,(root&1)==0?FALSE:TRUE,(root&1)==0?TRUE:FALSE,0,0);
        return intern(key,0,0,root,union?1:2);
    }
    private int junctionApply(int a,int b,boolean union) {
        int ak=nodes.junction(a),bk=nodes.junction(b);
        if(ak==0&&(nodes.low(a)>=2||nodes.high(a)>=2)||bk==0&&(nodes.low(b)>=2||nodes.high(b)>=2))return -1;
        int desired=union?1:2,leftKind=ak==0?desired:ak,rightKind=bk==0?desired:bk;
        long left=junctionRoot(a,leftKind==1),right=junctionRoot(b,rightKind==1);
        if(leftKind==rightKind) {
            if(leftKind==desired) {
                long joined=literals.union(left,right);return joined==-1?(union?TRUE:FALSE):junction(joined,union);
            }
            if(literals.includes(left,right))return b;
            if(literals.includes(right,left))return a;
            return -1;
        }
        long conjunction=leftKind==2?left:right,disjunction=leftKind==1?left:right;
        if(literals.intersectsSame(conjunction,disjunction))return union?(leftKind==1?a:b):(leftKind==2?a:b);
        if(union?literals.includes(disjunction^1,conjunction):literals.includes(conjunction^1,disjunction))return union?TRUE:FALSE;
        return -1;
    }
    private int branch(int id,boolean present) {
        int kind=nodes.junction(id);if(kind==0)return present?nodes.high(id):nodes.low(id);
        long root=nodes.literals(id);int variable=nodes.variable(id);
        boolean union=kind==1,negative=literals.polarity(root,variable)==2;
        if((present!=negative)==union)return union?TRUE:FALSE;
        int hit=cached(id,-1,8);if(hit>=0)return hit;
        return remember(id,-1,8,junction(literals.remove(root,variable),union));
    }
    int and(int a,int b) {
        open();try{return andUnchecked(a,b);}
        catch(AnalysisResources.Exhausted|PageStore.Failure failure){failed=true;throw failure;}
    }
    private int andUnchecked(int a,int b){return apply(a,b,false);}
    int or(int a,int b) {
        open();try{return orUnchecked(a,b);}
        catch(AnalysisResources.Exhausted|PageStore.Failure failure){failed=true;throw failure;}
    }
    private int orUnchecked(int a,int b){return apply(a,b,true);}
    int not(int value) {
        open();try{return notUnchecked(value);}
        catch(AnalysisResources.Exhausted|PageStore.Failure failure){failed=true;throw failure;}
    }
    private int notUnchecked(int value){return unary(value,-1,false);}
    int restrict(int value,int variable,boolean present) {
        open();try{return restrictUnchecked(value,variable,present);}
        catch(AnalysisResources.Exhausted|PageStore.Failure failure){failed=true;throw failure;}
    }
    private int restrictUnchecked(int value,int variable,boolean present){return unary(value,variable,present);}
    int difference(int a,int b){return and(a,not(b));}
    int setPresent(int value,int variable){return and(or(restrict(value,variable,false),restrict(value,variable,true)),variable(variable));}
    private static long pair(int a,int b){return a<=b?((long)a<<32)|(b&0xffffffffL):((long)b<<32)|(a&0xffffffffL);}
    private int terminal(int a,int b,boolean union) {
        if(a==b)return a;
        if(union){if(a==TRUE||b==TRUE)return TRUE;if(a==FALSE)return b;}
        else {if(a==FALSE)return FALSE;if(a==TRUE)return b;}
        return -1;
    }
    private static int memo(PagedLongIndex memo,long key){long value=memo.find(key);return value==0?-1:(int)(value-1);}
    private static void memo(PagedLongIndex memo,long key,int value){memo.intern(key,(long)value+1);}
    private int apply(int first,int second,boolean union) {
        open();long root=pair(first,second);int a=(int)(root>>>32),b=(int)root;
        int simple=terminal(a,b,union);if(simple>=0)return simple;
        int operation=union?1:0,hit=cached(a,b,operation);if(hit>=0)return hit;
        int compressed=junctionApply(a,b,union);
        if(compressed>=0)return remember(a,b,operation,compressed);
        pageStorage();
        try(var memo=new PagedLongIndex(literalPages,resources,AnalysisResources.Phase.CONTROL);
            var pending=new PagedLongArray(literalPages,Long.MAX_VALUE,resources,AnalysisResources.Phase.CONTROL)) {
            long top=1;pending.set(0,root);
            while(top>0) {
                long key=pending.get(top-1);a=(int)(key>>>32);b=(int)key;
                if(memo(memo,key)>=0){pending.set(--top,0);continue;}
                int result=cached(a,b,operation);
                if(result<0)result=terminal(a,b,union);
                if(result<0)result=junctionApply(a,b,union);
                if(result>=0){memo(memo,key,remember(a,b,operation,result));pending.set(--top,0);continue;}
                int av=nodes.variable(a),bv=nodes.variable(b),variable=Math.min(av,bv);
                long low=pair(av==variable?branch(a,false):a,bv==variable?branch(b,false):b);
                int lo=memo(memo,low);if(lo<0){pending.set(top++,low);continue;}
                long high=pair(av==variable?branch(a,true):a,bv==variable?branch(b,true):b);
                int hi=memo(memo,high);if(hi<0){pending.set(top++,high);continue;}
                memo(memo,key,remember(a,b,operation,node(variable,lo,hi)));pending.set(--top,0);
            }
            return remember((int)(root>>>32),(int)root,operation,memo(memo,root));
        }
    }
    private int unarySimple(int id,int variable,boolean present) {
        if(id<2)return variable<0?1-id:id;
        int kind=nodes.junction(id);
        if(kind!=0) {
            long root=nodes.literals(id);boolean union=kind==1;
            if(variable<0)return junction(root^1,!union);
            int polarity=literals.polarity(root,variable);if(polarity==0)return id;
            boolean truth=present!=(polarity==2);
            if(truth==union)return union?TRUE:FALSE;
            return junction(literals.remove(root,variable),union);
        }
        int key=nodes.variable(id);
        if(variable>=0&&key>=variable)return key==variable?(present?nodes.high(id):nodes.low(id)):id;
        return -1;
    }
    private int unary(int root,int variable,boolean present) {
        open();int simple=unarySimple(root,variable,present);if(simple>=0)return simple;
        int operation=variable<0?2:present?3:4,hit=cached(root,variable,operation);if(hit>=0)return hit;
        pageStorage();
        try(var memo=new PagedLongIndex(literalPages,resources,AnalysisResources.Phase.CONTROL);
            var pending=new PagedLongArray(literalPages,Long.MAX_VALUE,resources,AnalysisResources.Phase.CONTROL)) {
            long top=1;pending.set(0,root);
            while(top>0) {
                int id=(int)pending.get(top-1);
                if(memo(memo,id)>=0){pending.set(--top,0);continue;}
                int result=cached(id,variable,operation);if(result<0)result=unarySimple(id,variable,present);
                if(result>=0){memo(memo,id,remember(id,variable,operation,result));pending.set(--top,0);continue;}
                int low=nodes.low(id),high=nodes.high(id);
                int lo=memo(memo,low);if(lo<0){pending.set(top++,low);continue;}
                int hi=memo(memo,high);if(hi<0){pending.set(top++,high);continue;}
                memo(memo,id,remember(id,variable,operation,node(nodes.variable(id),lo,hi)));pending.set(--top,0);
            }
            return remember(root,variable,operation,memo(memo,root));
        }
    }
    /** One necessarily present key from the forced prefix, or -1 if none exists. */
    int requiredPresent(int value) {
        open();while(value>=2) {
            int kind=nodes.junction(value);
            if(kind!=0)return kind==2?literals.firstPolarity(nodes.literals(value),false):-1;
            int low=nodes.low(value);if(low==FALSE)return nodes.variable(value);
            if(nodes.high(value)!=FALSE)break;value=low;
        }
        return -1;
    }
    /** A potentially present key for an individual-word hint, not a required key. */
    int possiblePresent(int value) {
        open();while(value>=2) {
            if(nodes.junction(value)!=0)return literals.firstPolarity(nodes.literals(value),false);
            if(nodes.high(value)!=FALSE)return nodes.variable(value);value=nodes.low(value);
        }
        return -1;
    }
    /** Cofactor only encountered absent variables. The memo belongs to one immutable binding. */
    int restrictAbsent(int root,java.util.function.IntPredicate allowed,Map<Integer,Integer> memo) {
        open();try{return restrictAbsentUnchecked(root,allowed,memo);}
        catch(AnalysisResources.Exhausted|PageStore.Failure failure){failed=true;throw failure;}
    }
    private int restrictAbsentUnchecked(int root,java.util.function.IntPredicate allowed,Map<Integer,Integer> memo) {
        open();if(root<2)return root;pageStorage();
        try(var pending=new PagedLongArray(literalPages,Long.MAX_VALUE,resources,AnalysisResources.Phase.CONTROL)) {
            long top=1;pending.set(0,root);
            while(top>0) {
                int id=(int)pending.get(top-1);
                if(memo.containsKey(id)){pending.set(--top,0);continue;}
                if(id<2){memo.put(id,id);pending.set(--top,0);continue;}
                int kind=nodes.junction(id);
                if(kind!=0) {
                    boolean union=kind==1;long result=literals.restrict(nodes.literals(id),union,allowed);
                    memo.put(id,result==-1?(union?TRUE:FALSE):junction(result,union));pending.set(--top,0);continue;
                }
                int low=nodes.low(id),high=nodes.high(id),variable=nodes.variable(id);
                if(!memo.containsKey(low)){pending.set(top++,low);continue;}
                if(!allowed.test(variable)){memo.put(id,memo.get(low));pending.set(--top,0);continue;}
                if(!memo.containsKey(high)){pending.set(top++,high);continue;}
                memo.put(id,node(variable,memo.get(low),memo.get(high)));pending.set(--top,0);
            }
            return memo.get(root);
        }
    }
    /** Bind the formal ancestor parameters at the empty root without an absence vector. */
    int atEmpty(int value) {
        open();if(value<2)return value;
        int hit=cached(value,-1,5);if(hit>=0)return hit;int root=value;
        while(value>=2) {
            hit=cached(value,-1,5);if(hit>=0){value=hit;break;}
            int kind=nodes.junction(value);
            if(kind!=0) {
                long literal=nodes.literals(value),positives=literals.positiveCount(literal);
                value=kind==1?(positives<literals.size(literal)?TRUE:FALSE):(positives==0?TRUE:FALSE);break;
            }
            value=nodes.low(value);
        }
        return remember(root,-1,5,value);
    }
    boolean test(int value,BitSet assignment) {
        open();while(value>=2) {
            int kind=nodes.junction(value);
            if(kind!=0)return literals.test(nodes.literals(value),kind==1,assignment);
            value=assignment.get(nodes.variable(value))?nodes.high(value):nodes.low(value);
        }
        return value==TRUE;
    }
    boolean test(int value,PersistentLongMap assignment,long root) {
        open();while(value>=2) {
            int kind=nodes.junction(value);
            if(kind!=0)return literals.test(nodes.literals(value),kind==1,assignment,root);
            value=assignment.contains(root,nodes.variable(value))?nodes.high(value):nodes.low(value);
        }
        return value==TRUE;
    }
    /** Drop only scratch nodes allocated by a read-only query after its checkpoint.
     * No condition created in that scope may escape. Surviving IDs stay stable. */
    void discardAfter(int checkpoint) {
        open();try{discardAfterUnchecked(checkpoint);}
        catch(AnalysisResources.Exhausted|PageStore.Failure failure){failed=true;throw failure;}
    }
    private void discardAfterUnchecked(int checkpoint) {
        int end=size();if(checkpoint<2||checkpoint>end)throw new IllegalArgumentException("invalid BDD checkpoint");
        if(scratch&&checkpoint!=scratchFloor)throw new IllegalArgumentException("foreign BDD scratch checkpoint");
        int removed=0;
        for(int id=end-1;id>=checkpoint;id--)if(nodes.live(id)){releaseLiteral(id);nodes.retire(id,false);removed++;}
        if(nodes!=null)nodes.trimUnlinkedTail();
        if(removed>0&&literalArena!=null&&literalArena.size()>=literalCollectionThreshold)collectLiterals();
        if(scratch) {
            for(int j=0;j<scratchSlotCount;j++) {
                int i=scratchSlots[j];scratchCacheVisits++;
                if(cacheA[i]>=checkpoint||cacheResult[i]>=checkpoint||cacheOperation[i]<2&&cacheB[i]>=checkpoint)cacheOperation[i]=-1;
                scratchDirty[i]=false;
            }
            scratchSlotCount=0;
        }else if(removed>0) {
            for(int i=0;i<cacheOperation.length;i++) {
                scratchCacheVisits++;
                if(cacheA[i]>=checkpoint||cacheResult[i]>=checkpoint||cacheOperation[i]<2&&cacheB[i]>=checkpoint)cacheOperation[i]=-1;
            }
        }
        allocationsSinceCollection=scratch?scratchAllocations:Math.max(0,allocationsSinceCollection-removed);scratch=false;
    }
    private long epoch() {
        if(markEpoch==Long.MAX_VALUE)throw new PageStore.Failure(PageStore.Reason.INVALID_HANDLE,"condition mark epoch exhausted");
        return ++markEpoch;
    }
    private void trace(int root,int floor,long epoch,PagedLongArray pending) {
        if(root<floor)return;
        if(nodes==null||!nodes.live(root))throw new IllegalStateException("unowned condition root");
        long top=0;pending.set(top++,root);
        while(top>0) {
            int id=(int)pending.get(--top);pending.set(top,0);
            if(id<floor||nodes.mark(id)==epoch)continue;
            nodes.mark(id,epoch);
            if(nodes.junction(id)==0) {
                int low=nodes.low(id),high=nodes.high(id);
                if(low>=floor)pending.set(top++,low);
                if(high>=floor)pending.set(top++,high);
            }
        }
    }
    /** Commit only escaping append-only nodes, with marks and frontier outside heap. */
    void commitAfter(int checkpoint,java.util.function.Consumer<java.util.function.IntConsumer> roots) {
        open();try{commitAfterUnchecked(checkpoint,roots);}
        catch(AnalysisResources.Exhausted|PageStore.Failure failure){failed=true;throw failure;}
    }
    private void commitAfterUnchecked(int checkpoint,java.util.function.Consumer<java.util.function.IntConsumer> roots) {
        if(!scratch||checkpoint!=scratchFloor)throw new IllegalArgumentException("foreign operation checkpoint");
        pageStorage();long epoch=epoch();int end=size(),kept=0,last=checkpoint;
        try(var pending=new PagedLongArray(literalPages,Long.MAX_VALUE,resources,AnalysisResources.Phase.CONTROL)) {
            roots.accept(id->trace(id,checkpoint,epoch,pending));
        }
        for(int id=checkpoint;id<end;id++)if(nodes.mark(id)==epoch){kept++;last=id+1;}
        for(int id=checkpoint;id<end;id++)if(nodes.mark(id)!=epoch){releaseLiteral(id);nodes.retire(id,id<last);}
        if(nodes!=null)nodes.trimUnlinkedTail();
        for(int j=0;j<scratchSlotCount;j++) {
            int slot=scratchSlots[j];scratchCacheVisits++;
            if(retired(cacheA[slot],checkpoint)||retired(cacheResult[slot],checkpoint)
                ||cacheOperation[slot]<2&&retired(cacheB[slot],checkpoint))cacheOperation[slot]=-1;
            scratchDirty[slot]=false;
        }
        scratchSlotCount=0;allocationsSinceCollection=scratchAllocations+kept;scratch=false;
        if(literalArena!=null&&literalArena.size()>=literalCollectionThreshold)collectLiterals();
    }
    private boolean retired(int id,int checkpoint){return id>=checkpoint&&(nodes==null||!nodes.live(id));}
    int checkpoint() {
        open();if(scratch)throw new IllegalStateException("nested BDD scratch scope");
        scratch=true;scratchFloor=size();scratchAllocations=allocationsSinceCollection;return scratchFloor;
    }
    boolean collectionDue(){return !scratch&&allocationsSinceCollection>=collectionThreshold;}
    int retainedNodes(){open();return nodes==null?2:nodes.retainedNodes();}
    int collect(java.util.function.Consumer<java.util.function.IntConsumer> roots) {
        open();try{return collectUnchecked(roots);}
        catch(AnalysisResources.Exhausted|PageStore.Failure failure){failed=true;throw failure;}
    }
    private int collectUnchecked(java.util.function.Consumer<java.util.function.IntConsumer> roots) {
        open();if(scratch)throw new IllegalStateException("BDD collection during scratch scope");
        pageStorage();long epoch=epoch();int before=retainedNodes(),end=size(),last=2;
        try(var pending=new PagedLongArray(literalPages,Long.MAX_VALUE,resources,AnalysisResources.Phase.CONTROL)) {
            roots.accept(root->trace(root,2,epoch,pending));
        }
        for(int id=2;id<end;id++)if(nodes.live(id)&&nodes.mark(id)==epoch)last=id+1;
        for(int id=2;id<end;id++)if(nodes.live(id)&&nodes.mark(id)!=epoch){releaseLiteral(id);nodes.retire(id,id<last);}
        // Previously retired holes above the new last row also need unlinking.
        if(nodes!=null)nodes.trim();
        if(literalArena!=null)collectLiterals();
        Arrays.fill(cacheOperation,-1);allocationsSinceCollection=0;
        collectionThreshold=Math.max(65536,2L*retainedNodes());return before-retainedNodes();
    }
    private void releaseLiteral(int id){long token=nodes.token(id);if(token!=0)literalArena.release(token);}
    private void collectLiterals(){literalArena.collect();literalCollectionThreshold=Math.max(65536,2L*literalArena.size());}
    @Override public void close() {
        if(closed)return;closed=true;
        RuntimeException failure=ActivationSolver.closeResource(nodes,null);
        failure=ActivationSolver.closeResource(literals,failure);failure=ActivationSolver.closeResource(literalArena,failure);
        if(suppliedStore==null)failure=ActivationSolver.closeResource(literalPages,failure);
        controls.close();nodes=null;literalArena=null;literals=null;literalPages=null;if(failure!=null)throw failure;
    }
    int size(){open();return nodes==null?2:nodes.size();}
}
