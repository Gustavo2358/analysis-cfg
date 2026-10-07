package io.github.gustavo2358.analysis.solver;

import java.util.*;

/** Canonical ordered conditions with maximal literal junctions stored as balanced signed sets.
 * General decisions retain Shannon semantics. Operations use explicit stacks, not Java recursion.
 * The literal payload can spill; the legacy decision/catalog metadata remains resident. */
final class BooleanConditions implements AutoCloseable {
    static final int FALSE=0, TRUE=1;
    private record Node(int variable,int low,int high,long literals,int junction) {
        Node(int variable,int low,int high){this(variable,low,high,0,0);}
    }
    private final AnalysisResources resources;
    private final PageStore suppliedStore;
    private PageStore literalPages;
    private CanonicalTupleArena literalArena;
    private SignedLiteralSet literals;
    private long[] literalTokens=new long[16];
    private long literalCollectionThreshold=65536;
    private boolean closed;
    private record Pair(int first,int second) { }
    private final List<Node> nodes=new ArrayList<>();
    private Map<Node,Integer> unique=new HashMap<>();
    private int[] recycled=new int[0];
    private int recycledSize;
    private long allocationsSinceCollection,collectionThreshold=65536;
    private boolean scratch;
    private int scratchFloor;
    private long scratchAllocations;
    private final BitSet scratchDirty=new BitSet();
    private int[] scratchSlots=new int[0];
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
        cacheA=new int[cacheSlots];cacheB=new int[cacheSlots];cacheOperation=new int[cacheSlots];cacheResult=new int[cacheSlots];
        Arrays.fill(cacheOperation,-1);nodes.add(new Node(Integer.MAX_VALUE,0,0));nodes.add(new Node(Integer.MAX_VALUE,1,1));
    }
    private int cacheSlot(int a,int b,int operation){int hash=a*0x9e3779b9+b*0x85ebca6b+operation;hash^=hash>>>16;return hash&(cacheA.length-1);}
    private int cached(int a,int b,int operation){int slot=cacheSlot(a,b,operation);return cacheA[slot]==a&&cacheB[slot]==b&&cacheOperation[slot]==operation?cacheResult[slot]:-1;}
    private int remember(int a,int b,int operation,int result){
        int slot=cacheSlot(a,b,operation);cacheA[slot]=a;cacheB[slot]=b;cacheOperation[slot]=operation;cacheResult[slot]=result;
        if(scratch&&(a>=scratchFloor||result>=scratchFloor||operation<2&&b>=scratchFloor)&&!scratchDirty.get(slot)) {
            scratchDirty.set(slot);
            if(scratchSlotCount==scratchSlots.length)scratchSlots=Arrays.copyOf(scratchSlots,Math.min(cacheA.length,Math.max(16,scratchSlots.length*2)));
            scratchSlots[scratchSlotCount++]=slot;
        }
        return result;
    }
    private void open(){if(closed)throw new IllegalStateException("condition manager closed");}
    private void literalStorage() {
        open();if(literals!=null)return;
        literalPages=suppliedStore==null?new ResidentPageStore(4096,resources,AnalysisResources.Phase.CONTROL):suppliedStore;
        try {
            literalArena=new CanonicalTupleArena(literalPages,resources,AnalysisResources.Phase.CONTROL,6,new int[]{2,4,5});
            literals=new SignedLiteralSet(literalArena,resources);
        }catch(RuntimeException|Error failure) {
            if(literalArena!=null)try{literalArena.close();}catch(RuntimeException cleanup){failure.addSuppressed(cleanup);}
            if(suppliedStore==null)try{literalPages.close();}catch(RuntimeException cleanup){failure.addSuppressed(cleanup);}
            literalArena=null;literalPages=null;throw failure;
        }
    }
    int variable(int variable){return node(variable,FALSE,TRUE);}
    /** A maximal literal chain has one canonical balanced set rather than all decision prefixes. */
    int node(int variable,int low,int high) {
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
        return intern(new Node(variable,low,high));
    }
    private int intern(Node key) {
        var old=unique.get(key);if(old!=null)return old;
        int id=!scratch&&recycledSize>0?recycled[recycledSize-1]:nodes.size();
        if(id>=literalTokens.length)literalTokens=Arrays.copyOf(literalTokens,Math.max(id+1,literalTokens.length*2));
        long token=key.junction==0?0:literalArena.retain(key.literals>>>1);
        literalTokens[id]=token;
        if(!scratch&&recycledSize>0){recycledSize--;nodes.set(id,key);}else nodes.add(key);
        allocationsSinceCollection++;peakNodes=Math.max(peakNodes,unique.size()+3L);unique.put(key,id);return id;
    }
    /** -1 means a general condition; zero is the neutral literal set. */
    private long junctionRoot(int value,boolean union) {
        if(value<2)return value==(union?FALSE:TRUE)?emptyLiterals():-1;
        var n=nodes.get(value);
        if(n.junction!=0)return n.junction==(union?1:2)?n.literals:-1;
        if(n.low<2&&n.high<2) {literalStorage();return literals.put(0,n.variable,n.low==TRUE);}
        return -1;
    }
    private long emptyLiterals(){literalStorage();return 0;}
    private int junction(long root,boolean union) {
        if(root==0)return union?FALSE:TRUE;
        int key=literals.firstKey(root);
        if(literals.size(root)==1)return intern((root&1)==0?new Node(key,FALSE,TRUE):new Node(key,TRUE,FALSE));
        return intern(new Node(key,0,0,root,union?1:2));
    }
    private int junctionApply(Pair pair,boolean union) {
        var a=nodes.get(pair.first);var b=nodes.get(pair.second);
        if(a.junction==0&&(a.low>=2||a.high>=2)||b.junction==0&&(b.low>=2||b.high>=2))return -1;
        int desired=union?1:2,leftKind=a.junction==0?desired:a.junction,rightKind=b.junction==0?desired:b.junction;
        long left=junctionRoot(pair.first,leftKind==1),right=junctionRoot(pair.second,rightKind==1);
        if(leftKind==rightKind) {
            if(leftKind==desired) {
                long joined=literals.union(left,right);return joined==-1?(union?TRUE:FALSE):junction(joined,union);
            }
            if(literals.includes(left,right))return pair.second;
            if(literals.includes(right,left))return pair.first;
            return -1;
        }
        long conjunction=leftKind==2?left:right,disjunction=leftKind==1?left:right;
        if(literals.intersectsSame(conjunction,disjunction))return union?(leftKind==1?pair.first:pair.second):(leftKind==2?pair.first:pair.second);
        if(union?literals.includes(disjunction^1,conjunction):literals.includes(conjunction^1,disjunction))return union?TRUE:FALSE;
        return -1;
    }
    private int branch(int id,boolean present) {
        var n=nodes.get(id);if(n.junction==0)return present?n.high:n.low;
        boolean union=n.junction==1,negative=literals.polarity(n.literals,n.variable)==2;
        if((present!=negative)==union)return union?TRUE:FALSE;
        int hit=cached(id,-1,8);if(hit>=0)return hit;
        return remember(id,-1,8,junction(literals.remove(n.literals,n.variable),union));
    }
    int and(int a,int b){return apply(a,b,false);}
    int or(int a,int b){return apply(a,b,true);}
    int not(int value){return unary(value,-1,false);}
    int restrict(int value,int variable,boolean present){return unary(value,variable,present);}
    int difference(int a,int b){return and(a,not(b));}
    int setPresent(int value,int variable){return and(or(restrict(value,variable,false),restrict(value,variable,true)),variable(variable));}
    private Pair pair(int a,int b){return a<=b?new Pair(a,b):new Pair(b,a);}
    private int terminal(Pair pair,boolean union) {
        int a=pair.first,b=pair.second;
        if(a==b)return a;
        if(union){if(a==TRUE||b==TRUE)return TRUE;if(a==FALSE)return b;}
        else {if(a==FALSE)return FALSE;if(a==TRUE)return b;}
        return -1;
    }
    private int apply(int a,int b,boolean union) {
        var root=pair(a,b);int simple=terminal(root,union);if(simple>=0)return simple;
        int operation=union?1:0;int hit=cached(root.first,root.second,operation);if(hit>=0)return hit;
        var memo=new HashMap<Pair,Integer>();var pending=new ArrayDeque<Pair>();pending.push(root);
        while(!pending.isEmpty()) {
            var key=pending.peek();var known=memo.get(key);if(known!=null){pending.pop();continue;}
            int reusable=cached(key.first,key.second,operation);if(reusable>=0){memo.put(key,reusable);pending.pop();continue;}
            int terminal=terminal(key,union);if(terminal>=0){memo.put(key,terminal);pending.pop();continue;}
            int compressed=junctionApply(key,union);
            if(compressed>=0){memo.put(key,remember(key.first,key.second,operation,compressed));pending.pop();continue;}
            var x=nodes.get(key.first);var y=nodes.get(key.second);int variable=Math.min(x.variable,y.variable);
            var low=pair(x.variable==variable?branch(key.first,false):key.first,y.variable==variable?branch(key.second,false):key.second);
            var high=pair(x.variable==variable?branch(key.first,true):key.first,y.variable==variable?branch(key.second,true):key.second);
            if(!memo.containsKey(low)){pending.push(low);continue;}
            if(!memo.containsKey(high)){pending.push(high);continue;}
            memo.put(key,remember(key.first,key.second,operation,node(variable,memo.get(low),memo.get(high))));pending.pop();
        }
        return remember(root.first,root.second,operation,memo.get(root));
    }
    private int unarySimple(int id,int variable,boolean present) {
        if(id<2)return variable<0?1-id:id;
        var n=nodes.get(id);
        if(n.junction!=0) {
            boolean union=n.junction==1;
            if(variable<0)return junction(n.literals^1,!union);
            int polarity=literals.polarity(n.literals,variable);if(polarity==0)return id;
            boolean truth=present!=(polarity==2);
            if(truth==union)return union?TRUE:FALSE;
            return junction(literals.remove(n.literals,variable),union);
        }
        if(variable>=0&&n.variable>=variable)return n.variable==variable?(present?n.high:n.low):id;
        return -1;
    }
    private int unary(int root,int variable,boolean present) {
        int simple=unarySimple(root,variable,present);if(simple>=0)return simple;
        int operation=variable<0?2:present?3:4;int hit=cached(root,variable,operation);if(hit>=0)return hit;
        var memo=new HashMap<Integer,Integer>();var pending=new ArrayDeque<Integer>();pending.push(root);
        while(!pending.isEmpty()) {
            int id=pending.peek();if(memo.containsKey(id)){pending.pop();continue;}
            int reusable=cached(id,variable,operation);if(reusable>=0){memo.put(id,reusable);pending.pop();continue;}
            int direct=unarySimple(id,variable,present);
            if(direct>=0){memo.put(id,remember(id,variable,operation,direct));pending.pop();continue;}
            var n=nodes.get(id);
            if(!memo.containsKey(n.low)){pending.push(n.low);continue;}
            if(!memo.containsKey(n.high)){pending.push(n.high);continue;}
            memo.put(id,remember(id,variable,operation,node(n.variable,memo.get(n.low),memo.get(n.high))));pending.pop();
        }
        return remember(root,variable,operation,memo.get(root));
    }
    /** One necessarily present key from the forced prefix, or -1 if none exists. */
    int requiredPresent(int value) {
        while(value>=2) {
            var node=nodes.get(value);
            if(node.junction!=0)return node.junction==2?literals.firstPolarity(node.literals,false):-1;
            if(node.low==FALSE)return node.variable;
            if(node.high!=FALSE)break;
            value=node.low;
        }
        return -1;
    }
    /** A potentially present key for an individual-word hint, not a required key. */
    int possiblePresent(int value) {
        while(value>=2) {
            var node=nodes.get(value);
            if(node.junction!=0)return literals.firstPolarity(node.literals,false);
            if(node.high!=FALSE)return node.variable;
            value=node.low;
        }
        return -1;
    }
    /** Cofactor only encountered absent variables. The memo belongs to one immutable binding. */
    int restrictAbsent(int root,java.util.function.IntPredicate allowed,Map<Integer,Integer> memo) {
        if(root<2)return root;
        var pending=new ArrayDeque<Integer>();pending.push(root);
        while(!pending.isEmpty()) {
            int id=pending.peek();if(memo.containsKey(id)){pending.pop();continue;}
            if(id<2){memo.put(id,id);pending.pop();continue;}
            var n=nodes.get(id);
            if(n.junction!=0) {
                boolean union=n.junction==1;long result=literals.restrict(n.literals,union,allowed);
                memo.put(id,result==-1?(union?TRUE:FALSE):junction(result,union));pending.pop();continue;
            }
            if(!memo.containsKey(n.low)){pending.push(n.low);continue;}
            if(!allowed.test(n.variable)){memo.put(id,memo.get(n.low));pending.pop();continue;}
            if(!memo.containsKey(n.high)){pending.push(n.high);continue;}
            memo.put(id,node(n.variable,memo.get(n.low),memo.get(n.high)));pending.pop();
        }
        return memo.get(root);
    }
    /** Bind the formal ancestor parameters at the empty root without an absence vector. */
    int atEmpty(int value) {
        if(value<2)return value;
        int hit=cached(value,-1,5);if(hit>=0)return hit;
        int root=value;
        while(value>=2) {
            hit=cached(value,-1,5);if(hit>=0){value=hit;break;}
            var n=nodes.get(value);
            if(n.junction!=0){long positives=literals.positiveCount(n.literals);value=n.junction==1?(positives<literals.size(n.literals)?TRUE:FALSE):(positives==0?TRUE:FALSE);break;}
            value=n.low;
        }
        return remember(root,-1,5,value);
    }
    boolean test(int value,BitSet assignment) {
        while(value>=2){var n=nodes.get(value);if(n.junction!=0)return literals.test(n.literals,n.junction==1,assignment);value=assignment.get(n.variable)?n.high:n.low;}
        return value==TRUE;
    }
    boolean test(int value,PersistentLongMap assignment,long root) {
        while(value>=2){var n=nodes.get(value);if(n.junction!=0)return literals.test(n.literals,n.junction==1,assignment,root);value=assignment.contains(root,n.variable)?n.high:n.low;}
        return value==TRUE;
    }
    /** Drop only scratch nodes allocated by a read-only query after its checkpoint.
     * No condition created in that scope may escape. Surviving IDs stay stable. */
    void discardAfter(int checkpoint) {
        if(checkpoint<2||checkpoint>nodes.size())throw new IllegalArgumentException("invalid BDD checkpoint");
        if(scratch&&checkpoint!=scratchFloor)throw new IllegalArgumentException("foreign BDD scratch checkpoint");
        int removed=nodes.size()-checkpoint;
        for(int id=nodes.size()-1;id>=checkpoint;id--){releaseLiteral(id);unique.remove(nodes.remove(id));}
        if(removed>0&&literalArena!=null&&literalArena.size()>=literalCollectionThreshold)collectLiterals();
        // IDs can be reused. Invalidate every computed entry referring to scratch
        // operands/results, while retaining all computations on surviving nodes.
        if(scratch) {
            for(int j=0;j<scratchSlotCount;j++) {
                int i=scratchSlots[j];scratchCacheVisits++;
                if(cacheA[i]>=checkpoint||cacheResult[i]>=checkpoint||cacheOperation[i]<2&&cacheB[i]>=checkpoint)cacheOperation[i]=-1;
                scratchDirty.clear(i);
            }
            scratchSlotCount=0;
        } else if(removed>0) {
            // Retain the unmanaged test/diagnostic seam, whose writes had no journal.
            for(int i=0;i<cacheOperation.length;i++) {
                scratchCacheVisits++;
                if(cacheA[i]>=checkpoint||cacheResult[i]>=checkpoint||cacheOperation[i]<2&&cacheB[i]>=checkpoint)cacheOperation[i]=-1;
            }
        }
        allocationsSinceCollection=scratch?scratchAllocations:Math.max(0,allocationsSinceCollection-removed);scratch=false;
    }
    /** Commit an operation's escaping roots; older nodes cannot reference its append-only nodes. */
    void commitAfter(int checkpoint,java.util.function.Consumer<java.util.function.IntConsumer> roots) {
        if(!scratch||checkpoint!=scratchFloor)throw new IllegalArgumentException("foreign operation checkpoint");
        var marked=new BitSet(nodes.size()-checkpoint);var pending=new ArrayDeque<Integer>();
        java.util.function.IntConsumer mark=id->{
            if(id<checkpoint)return;
            if(id>=nodes.size()||nodes.get(id)==null)throw new IllegalStateException("unowned operation root");
            pending.push(id);
            while(!pending.isEmpty()) {
                int current=pending.pop();if(current<checkpoint||marked.get(current-checkpoint))continue;
                marked.set(current-checkpoint);var n=nodes.get(current);
                if(n.junction==0){pending.push(n.low);pending.push(n.high);}
            }
        };
        roots.accept(mark);int kept=marked.cardinality(),end=nodes.size();
        for(int id=checkpoint;id<end;id++)if(!marked.get(id-checkpoint)) {
            releaseLiteral(id);unique.remove(nodes.get(id));nodes.set(id,null);
        }
        while(nodes.size()>checkpoint&&nodes.getLast()==null)nodes.removeLast();
        for(int id=checkpoint;id<nodes.size();id++)if(nodes.get(id)==null) {
            if(recycledSize==recycled.length)recycled=Arrays.copyOf(recycled,Math.max(16,recycled.length*2));
            recycled[recycledSize++]=id;
        }
        for(int j=0;j<scratchSlotCount;j++) {
            int slot=scratchSlots[j];scratchCacheVisits++;
            if(retired(cacheA[slot],checkpoint,marked)||retired(cacheResult[slot],checkpoint,marked)
                ||cacheOperation[slot]<2&&retired(cacheB[slot],checkpoint,marked))cacheOperation[slot]=-1;
            scratchDirty.clear(slot);
        }
        scratchSlotCount=0;allocationsSinceCollection=scratchAllocations+kept;scratch=false;
        if(literalArena!=null&&literalArena.size()>=literalCollectionThreshold)collectLiterals();
    }
    private static boolean retired(int id,int checkpoint,BitSet marked){return id>=checkpoint&&!marked.get(id-checkpoint);}
    /** Scratch allocations append; they cannot reuse an older collected slot. */
    int checkpoint() {
        if(scratch)throw new IllegalStateException("nested BDD scratch scope");
        scratch=true;scratchFloor=nodes.size();scratchAllocations=allocationsSinceCollection;return scratchFloor;
    }
    boolean collectionDue(){return !scratch&&allocationsSinceCollection>=collectionThreshold;}
    int retainedNodes(){return unique.size()+2;}
    /** Called only between processing steps. The visitor supplies every persistent root. */
    int collect(java.util.function.Consumer<java.util.function.IntConsumer> roots) {
        if(scratch)throw new IllegalStateException("BDD collection during scratch scope");
        var marked=new BitSet(nodes.size());var pending=new ArrayDeque<Integer>();
        java.util.function.IntConsumer mark=root->{
            if(root<2)return;
            if(root>=nodes.size()||nodes.get(root)==null)throw new IllegalStateException("unowned BDD root");
            pending.push(root);
            while(!pending.isEmpty()) {
                int id=pending.pop();if(id<2||marked.get(id))continue;
                marked.set(id);var node=nodes.get(id);if(node.junction==0){pending.push(node.low);pending.push(node.high);}
            }
        };
        roots.accept(mark);int before=retainedNodes();
        for(int id=2;id<nodes.size();id++)if(!marked.get(id)){releaseLiteral(id);nodes.set(id,null);}
        if(literalArena!=null)collectLiterals();
        while(nodes.size()>2&&nodes.getLast()==null)nodes.removeLast();
        // Rebuild the unique table as well: an oversized HashMap bucket array
        // must not retain the historical allocation peak after its keys die.
        unique.clear();unique=new HashMap<>();recycledSize=0;
        for(int id=2;id<nodes.size();id++) {
            var node=nodes.get(id);
            if(node!=null)unique.put(node,id);
            else {
                if(recycledSize==recycled.length)recycled=Arrays.copyOf(recycled,Math.max(16,recycled.length*2));
                recycled[recycledSize++]=id;
            }
        }
        Arrays.fill(cacheOperation,-1);allocationsSinceCollection=0;
        collectionThreshold=Math.max(65536,2L*retainedNodes());return before-retainedNodes();
    }
    private void releaseLiteral(int id){long token=literalTokens[id];if(token!=0){literalArena.release(token);literalTokens[id]=0;}}
    private void collectLiterals(){literalArena.collect();literalCollectionThreshold=Math.max(65536,2L*literalArena.size());}
    @Override public void close() {
        if(closed)return;closed=true;
        try {if(literals!=null)literals.close();}
        finally {try {if(literalArena!=null)literalArena.close();}finally {if(suppliedStore==null&&literalPages!=null)literalPages.close();}}
        nodes.clear();unique.clear();literalTokens=new long[0];
    }
    int size(){return nodes.size();}
}
