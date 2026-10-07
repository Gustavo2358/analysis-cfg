package io.github.gustavo2358.analysis.solver;

import java.util.*;
import java.util.function.IntPredicate;

/** Signed functional handles over shared AND/OR logic and native literal sets.
 * Each stored representative is false at the all-absent assignment; the handle
 * phase bit complements it without allocating a second record. Terminals are0/1.
 * Samples disprove equality or nominate exact decisions; they never merge functions.
 * Current rows, aliases and query scratch share the borrowed paged backend. */
final class BooleanConditions implements AutoCloseable {
    static final int FALSE=0, TRUE=1;
    private static final int AND=3,NOT=4,OR=5;
    private final AnalysisResources resources;
    private final PageStore suppliedStore;
    private PageStore literalPages;
    private CanonicalTupleArena literalArena;
    private SignedLiteralSet literals;
    private BooleanNodeStore nodes;
    private BooleanFunctionIndex functions;
    private BooleanCircuitDecisions decisions;
    private PagedDagOwnership ownership;
    private PagedWorklist deferredRetirements;
    private int mutationDepth;
    private AnalysisResources.Reservation ownershipCacheCapacity;
    private long[] cacheGenerationA,cacheGenerationB,cacheGenerationResult;
    private long scratchBirths;
    private final AnalysisResources.Reservation controls;
    private long[] stagedSamples;
    private long markEpoch,literalCollectionThreshold=65536;
    private boolean closed,failed;
    private long allocationsSinceCollection,collectionThreshold=65536;
    private boolean scratch;
    private int scratchFloor;
    private long scratchAllocations;
    private boolean[] scratchDirty;
    private int[] scratchSlots;
    private int scratchSlotCount;
    private long peakNodes=2,scratchCacheVisits;
    long peakNodes(){return peakNodes;}
    long scratchCacheVisits(){return scratchCacheVisits;}
    private int[] cacheA,cacheB,cacheOperation,cacheResult;
    BooleanConditions(){this(65536);}
    BooleanConditions(int cacheSlots){this(cacheSlots,null,null);}
    BooleanConditions(int cacheSlots,AnalysisResources resources,PageStore store){
        if((resources==null)!=(store==null))throw new IllegalArgumentException("store and resources must be supplied together");
        this.resources=resources==null?new AnalysisResources(new AnalysisResources.Limits(Long.MAX_VALUE,Long.MAX_VALUE,Long.MAX_VALUE,Long.MAX_VALUE,Long.MAX_VALUE,Long.MAX_VALUE,Long.MAX_VALUE)):resources;
        suppliedStore=store;
        if(cacheSlots<1||Integer.bitCount(cacheSlots)!=1)throw new IllegalArgumentException("cache size must be a power of two");
        controls=this.resources.reserve(AnalysisResources.Pool.RESIDENT,512L+21L*cacheSlots+8L*PagedBooleanCircuit.SAMPLE_WORDS,AnalysisResources.Phase.CONTROL);
        try {
            cacheA=new int[cacheSlots];cacheB=new int[cacheSlots];cacheOperation=new int[cacheSlots];cacheResult=new int[cacheSlots];
            scratchSlots=new int[cacheSlots];scratchDirty=new boolean[cacheSlots];Arrays.fill(cacheOperation,-1);
            stagedSamples=new long[PagedBooleanCircuit.SAMPLE_WORDS];
        }catch(RuntimeException|Error failure){controls.close();throw failure;}
    }
    private int cacheSlot(int a,int b,int operation){int hash=a*0x9e3779b9+b*0x85ebca6b+operation;hash^=hash>>>16;return hash&(cacheA.length-1);}
    private int cached(int a,int b,int operation){int slot=cacheSlot(a,b,operation);if(cacheA[slot]!=a||cacheB[slot]!=b||cacheOperation[slot]!=operation)return -1;
        if(ownership!=null&&(cacheGenerationA[slot]!=generation(a)
                ||operation<2&&cacheGenerationB[slot]!=generation(b)
                ||operation<6&&cacheGenerationResult[slot]!=generation(cacheResult[slot])))return -1;
        return cacheResult[slot];}
    private int remember(int a,int b,int operation,int result){
        int slot=cacheSlot(a,b,operation);cacheA[slot]=a;cacheB[slot]=b;cacheOperation[slot]=operation;cacheResult[slot]=result;
        if(ownership!=null){cacheGenerationA[slot]=generation(a);cacheGenerationB[slot]=operation<2?generation(b):0;cacheGenerationResult[slot]=operation<6?generation(result):0;}
        if(scratch&&(record(a)>=scratchFloor||operation<6&&record(result)>=scratchFloor||operation<2&&record(b)>=scratchFloor)&&!scratchDirty[slot]) {
            scratchDirty[slot]=true;scratchSlots[scratchSlotCount++]=slot;
        }
        return result;
    }
    private void open(){if(closed||failed)throw new IllegalStateException("condition manager closed or aborted");if(ownership!=null)ownership.checkOpen();}
    /** Handoff the existing acyclic catalog. Callers must retain every published
     * model/container root before publishing the initial construction journal. */
    void enableOwnership(){
        open();if(ownership!=null)return;if(scratch)throw new IllegalStateException("ownership handoff during decision scope");
        try{
            nodeStorage();
            ownershipCacheCapacity=resources.reserve(AnalysisResources.Pool.RESIDENT,24L*cacheA.length,AnalysisResources.Phase.CONTROL);
            cacheGenerationA=new long[cacheA.length];cacheGenerationB=new long[cacheA.length];cacheGenerationResult=new long[cacheA.length];
            Arrays.fill(cacheOperation,-1);
            deferredRetirements=new PagedWorklist(literalPages,resources,AnalysisResources.Phase.CONTROL);
            ownership=new PagedDagOwnership(literalPages,resources,AnalysisResources.Phase.CONTROL,new PagedDagOwnership.Graph(){
                public long canonical(long node){return node&~1L;}
                public void children(long node,java.util.function.LongConsumer accept){
                    int id=record(Math.toIntExact(node)),kind=nodes.junction(id);
                    if(kind>=3){accept.accept(nodes.low(id));if(kind!=NOT)accept.accept(nodes.high(id));}
                }
                public void retire(long node){
                    int id=record(Math.toIntExact(node));releaseLiteral(id);nodes.retire(id,!scratch);
                    if(scratch)deferredRetirements.add(id);
                }
            });
            for(int id=2;id<nodes.size();id++)if(nodes.live(id))ownership.declare(handle(id));
            for(int id=2;id<nodes.size();id++)if(nodes.live(id))ownership.linkDeclared(handle(id));
        }catch(RuntimeException|Error error){failed=true;throw error;}
    }
    boolean ownershipEnabled(){open();return ownership!=null;}
    private long generation(int id){return id<2?0:ownership.generation(id);}
    long retainRoot(int value){open();if(ownership==null)throw new IllegalStateException("condition ownership disabled");return ownership.root(value);}
    long rootValue(long token){open();return ownership.value(token);}
    void bindRoot(long token,int value){
        open();try{
            long old=ownership.value(token);if(old==value)return;
            ownership.bind(token,value);
        }catch(AnalysisResources.Exhausted|PageStore.Failure error){failed=true;throw error;}
    }
    void releaseRoot(long token){
        open();try{ownership.closeRoot(token);}
        catch(AnalysisResources.Exhausted|PageStore.Failure error){failed=true;throw error;}
    }
    void beginMutation(){open();if(ownership==null)throw new IllegalStateException("condition ownership disabled");if(mutationDepth==Integer.MAX_VALUE)throw new IllegalStateException("mutation nesting exhausted");mutationDepth++;}
    void endMutation(){
        open();if(mutationDepth<=0||scratch)throw new IllegalStateException("invalid mutation publication");
        try{if(--mutationDepth==0){ownership.commitCreated();maybeCollectLiterals();}}catch(AnalysisResources.Exhausted|PageStore.Failure error){failed=true;throw error;}
    }
    long constructionMark(){open();if(scratch)throw new IllegalStateException("construction mark during decision scope");return ownership.constructionSize();}
    void publishSince(long mark){
        open();if(scratch)throw new IllegalStateException("publication during decision scope");
        try{ownership.commitCreatedSince(mark);maybeCollectLiterals();}
        catch(AnalysisResources.Exhausted|PageStore.Failure error){failed=true;throw error;}
    }
    long generationOf(int value){open();return ownership==null?0:generation(value);}
    void retainPermanentRoot(int value){
        open();if(value<2)return;
        try{if(nodes.mark(record(value))!=Long.MAX_VALUE){ownership.root(value);nodes.mark(record(value),Long.MAX_VALUE);}}
        catch(AnalysisResources.Exhausted|PageStore.Failure error){failed=true;throw error;}
    }
    void publishCreated(){publishSince(0);}
    private void finishOwnedScope(){
        if(decisions!=null)decisions.endScope();ownership.commitCreatedSince(scratchBirths);
        while(deferredRetirements.size()!=0)nodes.recycleRetired(Math.toIntExact(deferredRetirements.remove()));
        scratch=false;scratchSlotCount=0;Arrays.fill(scratchDirty,false);maybeCollectLiterals();
    }
    private void pageStorage(){open();if(literalPages==null)literalPages=suppliedStore==null?new ResidentPageStore(4096,resources,AnalysisResources.Phase.CONTROL):suppliedStore;}
    private void nodeStorage(){
        pageStorage();if(nodes==null)nodes=new BooleanNodeStore(literalPages,resources,64);
        if(functions==null)functions=new BooleanFunctionIndex(literalPages,resources,PagedBooleanCircuit.SAMPLE_WORDS,
                (a,b)->decisionStorage().equivalent(graphRecord(a),graphRecord(b)));
    }
    private void literalStorage(){
        open();if(literals!=null)return;pageStorage();
        try {
            literalArena=new CanonicalTupleArena(literalPages,resources,AnalysisResources.Phase.CONTROL,63,new int[]{2,4,5,62});
            literals=new SignedLiteralSet(literalArena,resources);
        }catch(RuntimeException|Error failure){
            if(literalArena!=null)try{literalArena.close();}catch(RuntimeException cleanup){failure.addSuppressed(cleanup);}
            literalArena=null;throw failure;
        }
    }
    private static int record(int value){return value<2?value:(value>>>1)+1;}
    private static int handle(int record){
        if(record<2)return record;
        if(record>(Integer.MAX_VALUE>>>1)+1)throw new PageStore.Failure(PageStore.Reason.INVALID_HANDLE,"condition handle space exhausted");
        return (record-1)<<1;
    }
    private static long graphRecord(int record){return record<2?record:(long)record<<3;}
    private static long graph(int value){return value<2?value:graphRecord(record(value))|(value&1);}
    private int nodeKind(int value){int kind=nodes.junction(record(value));return (value&1)!=0?(kind==AND?OR:kind==OR?AND:kind):kind;}
    private int lowOf(int value){return nodes.low(record(value))^(value&1);}
    private int highOf(int value){return nodes.high(record(value))^(value&1);}
    private long supportOf(int value){return value<2?0:functions.support(record(value));}
    private long nativeRoot(int value){return functions.descriptorField(record(value),3)^(value&1);}
    private static long primaryGraph(int key,boolean negative){return ((long)key<<3)|4|(negative?1:0);}
    private long literalGraph(long root){
        if(root==0)return TRUE;
        if(literals.size(root)==1)return primaryGraph(literals.firstKey(root),(root&1)!=0);
        return ((root+1)<<3)|2;
    }
    private long nativeGraph(int variable,int low,long root,int kind){
        return kind==0?primaryGraph(variable,low==TRUE):kind==1?literalGraph(root^1)^1:literalGraph(root);
    }
    private BooleanCircuitDecisions decisionStorage(){
        if(decisions==null){decisions=new BooleanCircuitDecisions(literalPages,resources,new BooleanCircuitView(){
            public long normalize(long root){
                while(root>=2) {
                    long handle=root>>>1,parity=root&1;int type=(int)(handle&3);
                    if(type==2||type==3)return root;
                    if(type==1)return literalGraph((handle>>>2)-1)^parity;
                    if(type!=0)throw new PageStore.Failure(PageStore.Reason.CORRUPT,"invalid condition graph handle");
                    int id=Math.toIntExact(handle>>>2),kind=functions.nativeKind(id);
                    if(kind>=0)return nativeGraph((int)functions.descriptorField(id,0),(int)functions.descriptorField(id,1),functions.descriptorField(id,3),kind)^parity;
                    kind=nodes.junction(id);
                    if(kind<3)return nativeGraph(nodes.variable(id),nodes.low(id),nodes.literals(id),kind)^parity;
                    if(kind==AND)return root;
                    if(kind==OR)return (((long)id<<3)|7)^parity;
                    if(kind!=NOT)throw new PageStore.Failure(PageStore.Reason.CORRUPT,"invalid condition row kind");
                    root=graph(nodes.low(id))^(parity^1);
                }
                return root;
            }
            public int primary(long handle){return (handle&3)==2?Math.toIntExact(handle>>>2):-1;}
            private long child(long handle,int side){
                if((handle&3)==1)return literalGraph(literals.child((handle>>>2)-1,side));
                int id=Math.toIntExact(handle>>>2);return graph(side==0?nodes.low(id):nodes.high(id))^((handle&3)==3?1:0);
            }
            public long left(long handle){return child(handle,0);}
            public long right(long handle){return child(handle,1);}
        });if(scratch)decisions.beginScope();}
        return decisions;
    }
    int variable(int variable){
        open();if(variable<0)throw new IllegalArgumentException("negative primary key");
        try{return intern(variable,FALSE,TRUE,0,0);}
        catch(AnalysisResources.Exhausted|PageStore.Failure failure){failed=true;throw failure;}
    }
    int node(int variable,int low,int high){
        open();try {
            if(low==high)return low;
            int key=variable(variable);
            return or(and(not(key),low),and(key,high));
        }catch(AnalysisResources.Exhausted|PageStore.Failure failure){failed=true;throw failure;}
    }
    private long sample(int id,int word){if(id<2)return id==0?0:-1;long value=functions.sample(record(id),word);return (id&1)==0?value:~value;}
    private int intern(int variable,int low,int high,long literalRoot,int kind){
        nodeStorage();if(kind==NOT)return low^1;
        int phase=kind==0?low:kind<3?(int)(literals.sample(literalRoot,kind==1,0)&1)
            :(int)((kind==AND?sample(low,0)&sample(high,0):sample(low,0)|sample(high,0))&1);
        if(phase!=0){
            if(kind==0){low^=1;high^=1;}
            else if(kind<3){kind=kind==1?2:1;literalRoot^=1;}
            else{kind=kind==AND?OR:AND;low^=1;high^=1;}
        }
        if(kind>=3&&low>high){int swap=low;low=high;high=swap;}
        boolean nativeInput=kind<3;
        int old=nativeInput?functions.nativeClass(variable,low,high,literalRoot,kind):nodes.find(variable,low,high,literalRoot,kind);
        if(old>=0)return handle(old)^phase;
        if(kind==0) {
            PagedBooleanCircuit.writePrimarySamples(variable,stagedSamples,0);
            if(low==TRUE)for(int word=0;word<stagedSamples.length;word++)stagedSamples[word]=~stagedSamples[word];
        }else for(int word=0;word<stagedSamples.length;word++)
            stagedSamples[word]=kind<3?literals.sample(literalRoot,kind==1,word):kind==NOT?~sample(low,word):kind==OR?sample(low,word)|sample(high,word):sample(low,word)&sample(high,word);
        long support=certifySupport(variable,low,high,literalRoot,kind);
        long supportToken=support==0?0:literalArena.retain(support>>>1);
        long token=kind==1||kind==2?literalArena.retain(literalRoot>>>1):0;
        int id;
        try{id=nodes.create(variable,low,high,literalRoot,kind,token,scratch);}
        catch(RuntimeException|Error failure){if(token!=0)try{literalArena.release(token);}catch(RuntimeException cleanup){failure.addSuppressed(cleanup);}throw failure;}
        allocationsSinceCollection++;peakNodes=Math.max(peakNodes,nodes.retainedNodes());
        functions.prepare(id,stagedSamples,support,supportToken);
        if(ownership!=null)ownership.created(handle(id));
        if(!nativeInput&&support==0) {
            boolean maybeFalse=true,maybeTrue=true;
            for(long word:stagedSamples){maybeFalse&=word==0;maybeTrue&=word== -1;}
            if(maybeFalse&&!decisionStorage().satisfiable(graphRecord(id))){retireProvisional(id);return phase;}
            if(maybeTrue&&decisionStorage().equivalent(graphRecord(id),TRUE)){retireProvisional(id);return TRUE^phase;}
        }
        int equal=functions.candidate(id,nativeInput);
        if(equal>=0) {
            if(support!=0&&functions.support(equal)==0)functions.certify(equal,support,literalArena.retain(support>>>1));
            if(nativeInput) {
                long alias=token==0?0:literalArena.retain(literalRoot>>>1);
                retireProvisional(id);
                functions.bindNative(equal,variable,low,high,literalRoot,kind,alias);
                int oldKind=nodes.junction(equal),oldLow=nodes.low(equal),oldHigh=nodes.high(equal);
                long previous=nodes.replace(equal,variable,low,high,literalRoot,kind,0);
                if(ownership!=null&&oldKind>=3)ownership.replacedByLeaf(handle(equal),oldLow,oldKind==NOT?0:oldHigh);
                if(previous!=0)literalArena.release(previous);
            }else retireProvisional(id);
            return handle(equal)^phase;
        }
        if(nativeInput)functions.insertNative(id,variable,low,high,literalRoot,kind,0);else functions.insertMixed(id);
        return handle(id)^phase;
    }
    /** Every admitted nonconstant native junction depends on all its keys. NOT
     * preserves essential support. AND/OR of nonconstant functions with disjoint
     * essential supports depends on their union (choose a satisfying/falsifying
     * assignment independently for the other operand). Opposing full junctions
     * on >=2 keys describe all-equal/not-all-equal, with every key essential. */
    private long certifySupport(int variable,int low,int high,long root,int kind) {
        literalStorage();
        if(kind==0)return literals.put(0,variable,false);
        if(kind<3)return literals.unsigned(root);
        long left=supportOf(low);if(kind==NOT)return left;
        long right=supportOf(high);
        if(left!=0&&right!=0&&!literals.intersectsSame(left,right))return literals.union(left,right);
        int a=nativeKind(low),b=nativeKind(high);
        if((kind==OR&&a==2&&b==2||kind==AND&&a==1&&b==1)
                &&nativeRoot(low)==(nativeRoot(high)^1))return left;
        return 0;
    }
    long equivalenceComparisons(){open();return functions==null?0:functions.equivalenceCalls();}
    /** -1 unknown; otherwise exact membership. Constants have empty support. */
    int certifiedSupportContains(int value,int key){
        open();try{if(value<2)return 0;long support=supportOf(value);return support==0?-1:literals.polarity(support,key);}
        catch(AnalysisResources.Exhausted|PageStore.Failure failure){failed=true;throw failure;}
    }
    private void retireProvisional(int id){
        if(ownership!=null){ownership.abandonCreated(handle(id));return;}
        releaseLiteral(id);boolean tail=!scratch&&id==nodes.size()-1;
        nodes.retire(id,!scratch&&!tail);if(tail)nodes.trimUnlinkedTail();
    }
    private int nativeKind(int id){int kind=functions.nativeKind(record(id));return (id&1)!=0?(kind==1?2:kind==2?1:kind):kind;}
    private int nativeVariable(int id){return (int)functions.descriptorField(record(id),0);}
    private int nativeLow(int id){return (int)functions.descriptorField(record(id),1)^(id&1);}
    private long junctionRoot(int value,boolean union){
        if(value<2){if(value!=(union?FALSE:TRUE))return -1;literalStorage();return 0;}
        int kind=nativeKind(value);
        if(kind<0)return -1;
        if(kind!=0)return kind==(union?1:2)?nativeRoot(value):-1;
        literalStorage();return literals.put(0,nativeVariable(value),nativeLow(value)==TRUE);
    }
    private int junction(long root,boolean union){
        if(root==0)return union?FALSE:TRUE;
        int key=literals.firstKey(root);
        if(literals.size(root)==1)return intern(key,(root&1)==0?FALSE:TRUE,(root&1)==0?TRUE:FALSE,0,0);
        return intern(key,0,0,root,union?1:2);
    }
    private int junctionApply(int a,int b,boolean union){
        int ak=nativeKind(a),bk=nativeKind(b);if(ak<0||bk<0)return -1;
        int desired=union?1:2,leftKind=ak==0?desired:ak,rightKind=bk==0?desired:bk;
        long left=junctionRoot(a,leftKind==1),right=junctionRoot(b,rightKind==1);
        if(leftKind==rightKind) {
            if(leftKind==desired){long joined=literals.union(left,right);return joined==-1?(union?TRUE:FALSE):junction(joined,union);}
            if(literals.includes(left,right))return b;
            if(literals.includes(right,left))return a;
            return -1;
        }
        long conjunction=leftKind==2?left:right,disjunction=leftKind==1?left:right;
        if(literals.intersectsSame(conjunction,disjunction))return union?(leftKind==1?a:b):(leftKind==2?a:b);
        if(union?literals.includes(disjunction^1,conjunction):literals.includes(conjunction^1,disjunction))return union?TRUE:FALSE;
        return -1;
    }
    int and(int a,int b){open();try{return apply(a,b,false);}catch(AnalysisResources.Exhausted|PageStore.Failure failure){failed=true;throw failure;}}
    int or(int a,int b){open();try{return apply(a,b,true);}catch(AnalysisResources.Exhausted|PageStore.Failure failure){failed=true;throw failure;}}
    private int terminal(int a,int b,boolean union){
        if(a==b)return a;if((a^1)==b)return union?TRUE:FALSE;
        if(union){if(a==TRUE||b==TRUE)return TRUE;if(a==FALSE)return b;}
        else {if(a==FALSE)return FALSE;if(a==TRUE)return b;}
        return -1;
    }
    /** Pull common signed literals out of products, and dually out of sums.
     * Distributivity is exact for arbitrary Boolean environments. Residual
     * construction bypasses this rewrite, so factoring cannot recurse on DAG
     * depth or cause distributive expansion. Native set operations skip shared
     * Patricia subtrees; no cube Cartesian product is materialized. */
    private int factorChild(int value,boolean union){
        int kind=nativeKind(value),nativeFactor=union?2:1;
        if(kind==0||kind==nativeFactor)return value;
        if(kind>=0||nodeKind(value)!=(union?AND:OR))return -1;
        int left=lowOf(value),right=highOf(value),kindLeft=nativeKind(left),kindRight=nativeKind(right);
        if((kindLeft==0||kindLeft==nativeFactor)&&independent(left,right))return left;
        return (kindRight==0||kindRight==nativeFactor)&&independent(right,left)?right:-1;
    }
    /** A mixed factor is a separate component only with certified disjoint
     * essential supports. Overlapping or unknown supports stay in the exact
     * circuit; rewriting those does not provide independent decomposition. */
    private boolean independent(int left,int right){
        long a=supportOf(left),b=supportOf(right);
        return a!=0&&b!=0&&!literals.intersectsSame(a,b);
    }
    private int residual(int value,int factor,long common,boolean union){
        int remaining=junction(literals.without(junctionRoot(factor,!union),common),!union);
        if(value==factor)return remaining;
        int other=lowOf(value)==factor?highOf(value):lowOf(value);
        return apply(remaining,other,!union,false);
    }
    private int factor(int left,int right,boolean union){
        int a=factorChild(left,union),b=factorChild(right,union);if(a<0||b<0)return -1;
        long common=literals.intersection(junctionRoot(a,!union),junctionRoot(b,!union));if(common==0)return -1;
        int remainder=apply(residual(left,a,common,union),residual(right,b,common,union),union,false);
        return apply(junction(common,!union),remainder,!union,false);
    }
    private int apply(int first,int second,boolean union){return apply(first,second,union,true);}
    private int apply(int first,int second,boolean union,boolean factoring){
        int a=Math.min(first,second),b=Math.max(first,second),simple=terminal(a,b,union);
        if(simple>=0)return simple;
        int operation=union?1:0,hit=cached(a,b,operation);if(hit>=0)return hit;
        int compressed=junctionApply(a,b,union);
        if(compressed>=0)return remember(a,b,operation,compressed);
        if(factoring){int factored=factor(a,b,union);if(factored>=0)return remember(a,b,operation,factored);}
        int result=intern(-1,a,b,0,union?OR:AND);
        return remember(a,b,operation,result);
    }
    int not(int value){
        open();try{if(value>=2)functions.sample(record(value),0);return value^1;}
        catch(AnalysisResources.Exhausted|PageStore.Failure failure){failed=true;throw failure;}
    }
    int difference(int a,int b){return and(a,not(b));}
    int setPresent(int value,int variable){return and(or(restrict(value,variable,false),restrict(value,variable,true)),variable(variable));}
    int restrict(int value,int variable,boolean present){
        open();try {
            if(value<2)return value;int operation=present?3:4,hit=cached(value,variable,operation);if(hit>=0)return hit;
            return remember(value,variable,operation,substitute(value,variable,present,null,null));
        }
        catch(AnalysisResources.Exhausted|PageStore.Failure failure){failed=true;throw failure;}
    }
    int restrictAbsent(int root,IntPredicate allowed,Map<Integer,Integer> memo){
        open();try{return substitute(root,-1,false,allowed,memo);}
        catch(AnalysisResources.Exhausted|PageStore.Failure failure){failed=true;throw failure;}
    }
    private PagedLongArray array(){return new PagedLongArray(literalPages,Long.MAX_VALUE,resources,AnalysisResources.Phase.CONTROL);}
    private int substitute(int root,int variable,boolean present,IntPredicate allowed,Map<Integer,Integer> external){
        if(root<2)return root;pageStorage();
        try(var memo=array();var pending=array()) {
            long top=1;pending.set(0,root);
            while(top>0) {
                int id=(int)pending.get(top-1);long known=memo.get(id);
                if(known!=0){pending.set(--top,0);continue;}
                Integer saved=external==null?null:external.get(id);
                int result=allowed==null?cached(id,variable,present?3:4):saved==null?-1:saved;
                if(id<2)result=id;
                else if(result<0&&allowed==null&&certifiedSupportContains(id,variable)==0)result=id;
                int kind=id<2?-1:nativeKind(id);
                if(result<0&&kind>=0) {
                    if(kind==0) {
                        int key=nativeVariable(id);boolean bound=allowed==null?key==variable:!allowed.test(key);
                        result=bound?((allowed==null&&present)!=(nativeLow(id)==TRUE)?TRUE:FALSE):id;
                    }else {
                        boolean union=kind==1;long literalsRoot=nativeRoot(id),changed;
                        if(allowed!=null)changed=literals.restrict(literalsRoot,union,allowed);
                        else {
                            int polarity=literals.polarity(literalsRoot,variable);
                            if(polarity==0){result=id;changed=0;}
                            else if((present!=(polarity==2))==union){result=union?TRUE:FALSE;changed=0;}
                            else changed=literals.remove(literalsRoot,variable);
                        }
                        if(result<0)result=changed== -1?(union?TRUE:FALSE):junction(changed,union);
                    }
                }
                if(result<0) {
                    kind=nodeKind(id);int low=lowOf(id),high=highOf(id);
                    long left=memo.get(low);if(left==0){pending.set(top++,low);continue;}
                    if(kind==NOT)result=not((int)(left-1));
                    else {
                        long right=memo.get(high);if(right==0){pending.set(top++,high);continue;}
                        result=kind==OR?or((int)(left-1),(int)(right-1)):and((int)(left-1),(int)(right-1));
                    }
                }
                memo.set(id,(long)result+1);if(external!=null)external.put(id,result);
                if(allowed==null)remember(id,variable,present?3:4,result);pending.set(--top,0);
            }
            return (int)(memo.get(root)-1);
        }
    }
    /** Sound positive requirement; a missing hint falls back to exact word checking. */
    int requiredPresent(int value){
        open();try {
            if(value<2)return -1;
            int hit=cached(value,-1,6);if(hit>=0)return hit-1;
            pageStorage();int found=-1;
            try(var pending=array();var seen=array()) {
                long top=1;pending.set(0,value);
                while(top>0&&found<0) {
                    int id=(int)pending.get(--top);pending.set(top,0);if(id<2||seen.get(id)!=0)continue;seen.set(id,1);
                    int kind=nativeKind(id);
                    if(kind==0){if(nativeLow(id)==FALSE)found=nativeVariable(id);}
                    else if(kind==2)found=literals.firstPolarity(nativeRoot(id),false);
                    else if(kind<0&&nodeKind(id)==AND){pending.set(top++,highOf(id));pending.set(top++,lowOf(id));}
                }
            }
            remember(value,-1,6,found+1);return found;
        }catch(AnalysisResources.Exhausted|PageStore.Failure failure){failed=true;throw failure;}
    }
    /** An optional positive occurrence nominates caller words only. Each nominated
     * word is evaluated exactly; a missing or unsuccessful hint still needs the
     * exact feasibility fallback. Traverse complemented DAGs in both polarities. */
    int possiblePresent(int value){
        open();try {
            if(value<2)return -1;
            int hit=cached(value,-1,7);if(hit>=0)return hit-1;
            pageStorage();int found=-1;
            try(var pending=array();var seen=array()) {
                long top=1;pending.set(0,(long)value<<1);
                while(top>0&&found<0) {
                    long root=pending.get(--top);pending.set(top,0);
                    int id=(int)(root>>>1);boolean negative=(root&1)!=0;
                    if(id<2||seen.get(root)!=0)continue;seen.set(root,1);
                    int kind=nativeKind(id);
                    if(kind==0){if((nativeLow(id)==FALSE)!=negative)found=nativeVariable(id);}
                    else if(kind>0)found=literals.firstPolarity(nativeRoot(id),negative);
                    else {
                        kind=nodeKind(id);
                        if(kind==NOT)pending.set(top++,((long)lowOf(id)<<1)|(negative?0:1));
                        else {
                            pending.set(top++,((long)highOf(id)<<1)|(negative?1:0));
                            pending.set(top++,((long)lowOf(id)<<1)|(negative?1:0));
                        }
                    }
                }
            }
            remember(value,-1,7,found+1);return found;
        }catch(AnalysisResources.Exhausted|PageStore.Failure failure){failed=true;throw failure;}
    }
    int atEmpty(int value){open();try{return value<2?value:(int)(sample(value,0)&1);}catch(AnalysisResources.Exhausted|PageStore.Failure failure){failed=true;throw failure;}}
    boolean test(int value,BitSet assignment){open();try{return evaluate(value,assignment,null,0);}catch(AnalysisResources.Exhausted|PageStore.Failure failure){failed=true;throw failure;}}
    boolean test(int value,PersistentLongMap assignment,long root){open();try{return evaluate(value,null,assignment,root);}catch(AnalysisResources.Exhausted|PageStore.Failure failure){failed=true;throw failure;}}
    private boolean evaluate(int root,BitSet bits,PersistentLongMap word,long assignment){
        if(root<2)return root==TRUE;pageStorage();
        int rootKind=nativeKind(root);
        if(rootKind>=0)return evaluateNative(root,rootKind,bits,word,assignment);
        try(var memo=array();var pending=array()) {
            long top=1;pending.set(0,root);
            while(top>0) {
                int id=(int)pending.get(top-1);long result=memo.get(id);
                if(result!=0){pending.set(--top,0);continue;}
                if(id<2)result=id+1;
                else {
                    int kind=nativeKind(id);
                    if(kind>=0)result=evaluateNative(id,kind,bits,word,assignment)?2:1;
                    else {
                        kind=nodeKind(id);int low=lowOf(id),high=highOf(id);
                        long left=memo.get(low);if(left==0){pending.set(top++,low);continue;}
                        if(kind==NOT)result=3-left;
                        else if(kind==AND&&left==1||kind==OR&&left==2)result=left;
                        else {long right=memo.get(high);if(right==0){pending.set(top++,high);continue;}result=right;}
                    }
                }
                memo.set(id,result);pending.set(--top,0);
            }
            return memo.get(root)==2;
        }
    }
    private boolean evaluateNative(int id,int kind,BitSet bits,PersistentLongMap word,long assignment){
        if(kind==0){boolean present=bits!=null?bits.get(nativeVariable(id)):word.contains(assignment,nativeVariable(id));return present!=(nativeLow(id)==TRUE);}
        return bits!=null?literals.test(nativeRoot(id),kind==1,bits):literals.test(nativeRoot(id),kind==1,word,assignment);
    }
    /** Drop only scratch nodes allocated by a read-only query after its checkpoint.
     * No condition created in that scope may escape. Surviving IDs stay stable. */
    void discardAfter(int checkpoint) {
        open();try{discardAfterUnchecked(checkpoint);}
        catch(AnalysisResources.Exhausted|PageStore.Failure failure){failed=true;throw failure;}
    }
    private void discardAfterUnchecked(int checkpoint) {
        if(ownership!=null){if(!scratch||checkpoint!=scratchFloor)throw new IllegalArgumentException("foreign operation checkpoint");finishOwnedScope();return;}
        int end=size();if(checkpoint<2||checkpoint>end)throw new IllegalArgumentException("invalid BDD checkpoint");
        if(scratch&&checkpoint!=scratchFloor)throw new IllegalArgumentException("foreign BDD scratch checkpoint");
        if(scratch&&decisions!=null)decisions.endScope();
        int removed=0;
        for(int id=end-1;id>=checkpoint;id--)if(nodes.live(id)){releaseLiteral(id);nodes.retire(id,false);removed++;}
        if(nodes!=null)nodes.trimUnlinkedTail();
        if(removed>0&&literalArena!=null&&literalArena.size()>=literalCollectionThreshold)collectLiterals();
        if(scratch) {
            for(int j=0;j<scratchSlotCount;j++) {
                int i=scratchSlots[j];scratchCacheVisits++;
                if(record(cacheA[i])>=checkpoint||cacheOperation[i]<6&&record(cacheResult[i])>=checkpoint||cacheOperation[i]<2&&record(cacheB[i])>=checkpoint)cacheOperation[i]=-1;
                scratchDirty[i]=false;
            }
            scratchSlotCount=0;
        }else if(removed>0) {
            for(int i=0;i<cacheOperation.length;i++) {
                scratchCacheVisits++;
                if(record(cacheA[i])>=checkpoint||cacheOperation[i]<6&&record(cacheResult[i])>=checkpoint||cacheOperation[i]<2&&record(cacheB[i])>=checkpoint)cacheOperation[i]=-1;
            }
        }
        allocationsSinceCollection=scratch?scratchAllocations:Math.max(0,allocationsSinceCollection-removed);scratch=false;
    }
    private long epoch() {
        if(markEpoch==Long.MAX_VALUE)throw new PageStore.Failure(PageStore.Reason.INVALID_HANDLE,"condition mark epoch exhausted");
        return ++markEpoch;
    }
    private void trace(int root,int floor,long epoch,PagedLongArray pending) {
        int start=record(root);if(start<floor)return;
        if(nodes==null||!nodes.live(start))throw new IllegalStateException("unowned condition root");
        long top=0;pending.set(top++,start);
        while(top>0){
            int id=(int)pending.get(--top);pending.set(top,0);if(id<floor||nodes.mark(id)==epoch)continue;
            nodes.mark(id,epoch);int kind=nodes.junction(id);
            if(kind==AND||kind==OR||kind==NOT){
                int low=record(nodes.low(id)),high=record(nodes.high(id));
                if(low>=floor)pending.set(top++,low);if(high>=floor)pending.set(top++,high);
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
        if(ownership!=null){finishOwnedScope();return;}
        if(decisions!=null)decisions.endScope();
        pageStorage();long epoch=epoch();int end=size(),kept=0,last=checkpoint;
        try(var pending=new PagedLongArray(literalPages,Long.MAX_VALUE,resources,AnalysisResources.Phase.CONTROL)) {
            roots.accept(id->trace(id,checkpoint,epoch,pending));
        }
        for(int id=checkpoint;id<end;id++)if(nodes.live(id)&&nodes.mark(id)==epoch){kept++;last=id+1;}
        for(int id=checkpoint;id<end;id++)if(nodes.live(id)&&nodes.mark(id)!=epoch){releaseLiteral(id);nodes.retire(id,id<last);}
        if(nodes!=null)nodes.trimUnlinkedTail();
        for(int j=0;j<scratchSlotCount;j++) {
            int slot=scratchSlots[j];scratchCacheVisits++;
            if(retired(cacheA[slot],checkpoint)||cacheOperation[slot]<6&&retired(cacheResult[slot],checkpoint)
                ||cacheOperation[slot]<2&&retired(cacheB[slot],checkpoint))cacheOperation[slot]=-1;
            scratchDirty[slot]=false;
        }
        scratchSlotCount=0;allocationsSinceCollection=scratchAllocations+kept;scratch=false;
        if(literalArena!=null&&literalArena.size()>=literalCollectionThreshold)collectLiterals();
    }
    private boolean retired(int value,int checkpoint){int id=record(value);return id>=checkpoint&&(nodes==null||!nodes.live(id));}
    int checkpoint() {
        open();if(scratch)throw new IllegalStateException("nested BDD scratch scope");
        scratch=true;scratchFloor=size();scratchAllocations=allocationsSinceCollection;
        if(ownership!=null)scratchBirths=ownership.constructionSize();
        if(decisions!=null)decisions.beginScope();return scratchFloor;
    }
    boolean collectionDue(){return ownership==null&&!scratch&&allocationsSinceCollection>=collectionThreshold;}
    int retainedNodes(){open();return nodes==null?2:nodes.retainedNodes();}
    int collect(java.util.function.Consumer<java.util.function.IntConsumer> roots) {
        open();try{return collectUnchecked(roots);}
        catch(AnalysisResources.Exhausted|PageStore.Failure failure){failed=true;throw failure;}
    }
    private int collectUnchecked(java.util.function.Consumer<java.util.function.IntConsumer> roots) {
        open();if(ownership!=null)throw new IllegalStateException("owned conditions retire by explicit roots");if(scratch)throw new IllegalStateException("BDD collection during scratch scope");
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
    private void releaseLiteral(int id){
        long support=functions.supportToken(id),alias=functions.remove(id);
        if(alias!=0)literalArena.release(alias);if(support!=0)literalArena.release(support);
        long token=nodes.token(id);if(token!=0)literalArena.release(token);
    }
    private void maybeCollectLiterals(){if(literalArena!=null&&literalArena.size()>=literalCollectionThreshold)collectLiterals();}
    private void collectLiterals(){literalArena.collect();literalCollectionThreshold=Math.max(65536,2L*literalArena.size());}
    @Override public void close() {
        if(closed)return;closed=true;
        RuntimeException failure=ActivationSolver.closeResource(decisions,null);
        failure=ActivationSolver.closeResource(ownership,failure);failure=ActivationSolver.closeResource(deferredRetirements,failure);
        failure=ActivationSolver.closeResource(ownershipCacheCapacity,failure);
        failure=ActivationSolver.closeResource(functions,failure);failure=ActivationSolver.closeResource(nodes,failure);
        failure=ActivationSolver.closeResource(literals,failure);failure=ActivationSolver.closeResource(literalArena,failure);
        if(suppliedStore==null)failure=ActivationSolver.closeResource(literalPages,failure);
        controls.close();cacheA=cacheB=cacheOperation=cacheResult=scratchSlots=null;scratchDirty=null;
        cacheGenerationA=cacheGenerationB=cacheGenerationResult=null;ownership=null;deferredRetirements=null;ownershipCacheCapacity=null;
        stagedSamples=null;functions=null;decisions=null;nodes=null;literalArena=null;literals=null;literalPages=null;if(failure!=null)throw failure;
    }
    int size(){open();return nodes==null?2:nodes.size();}
}
