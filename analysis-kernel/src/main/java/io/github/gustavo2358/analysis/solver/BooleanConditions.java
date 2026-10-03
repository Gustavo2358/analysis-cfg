package io.github.gustavo2358.analysis.solver;

import java.util.*;

/** Per-execution reduced ordered BDDs. Operations use explicit stacks, not Java recursion. */
final class BooleanConditions {
    static final int FALSE=0, TRUE=1;
    private record Node(int variable,int low,int high) { }
    private record Pair(int first,int second) { }
    private final List<Node> nodes=new ArrayList<>();
    private final Map<Node,Integer> unique=new HashMap<>();
    // Direct-mapped computed table: collisions only evict memoized work. They never
    // identify semantic nodes or truncate conditions. Memory is fixed per manager.
    private final int[] cacheA,cacheB,cacheOperation,cacheResult;
    BooleanConditions(){this(16384);}
    BooleanConditions(int cacheSlots){
        if(cacheSlots<1||Integer.bitCount(cacheSlots)!=1)throw new IllegalArgumentException("cache size must be a power of two");
        cacheA=new int[cacheSlots];cacheB=new int[cacheSlots];cacheOperation=new int[cacheSlots];cacheResult=new int[cacheSlots];
        Arrays.fill(cacheOperation,-1);nodes.add(new Node(Integer.MAX_VALUE,0,0));nodes.add(new Node(Integer.MAX_VALUE,1,1));
    }
    private int cacheSlot(int a,int b,int operation){int hash=a*0x9e3779b9+b*0x85ebca6b+operation;hash^=hash>>>16;return hash&(cacheA.length-1);}
    private int cached(int a,int b,int operation){int slot=cacheSlot(a,b,operation);return cacheA[slot]==a&&cacheB[slot]==b&&cacheOperation[slot]==operation?cacheResult[slot]:-1;}
    private int remember(int a,int b,int operation,int result){int slot=cacheSlot(a,b,operation);cacheA[slot]=a;cacheB[slot]=b;cacheOperation[slot]=operation;cacheResult[slot]=result;return result;}
    int variable(int variable){return node(variable,FALSE,TRUE);}
    int node(int variable,int low,int high) {
        if(low==high)return low;
        var key=new Node(variable,low,high);
        var old=unique.get(key);if(old!=null)return old;
        int id=nodes.size();nodes.add(key);unique.put(key,id);return id;
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
            var x=nodes.get(key.first);var y=nodes.get(key.second);int variable=Math.min(x.variable,y.variable);
            var low=pair(x.variable==variable?x.low:key.first,y.variable==variable?y.low:key.second);
            var high=pair(x.variable==variable?x.high:key.first,y.variable==variable?y.high:key.second);
            if(!memo.containsKey(low)){pending.push(low);continue;}
            if(!memo.containsKey(high)){pending.push(high);continue;}
            memo.put(key,remember(key.first,key.second,operation,node(variable,memo.get(low),memo.get(high))));pending.pop();
        }
        return remember(root.first,root.second,operation,memo.get(root));
    }
    private int unary(int root,int variable,boolean present) {
        if(root<2)return variable<0?1-root:root;
        var first=nodes.get(root);
        if(variable>=0&&first.variable>=variable)return first.variable==variable?(present?first.high:first.low):root;
        int operation=variable<0?2:present?3:4;int hit=cached(root,variable,operation);if(hit>=0)return hit;
        var memo=new HashMap<Integer,Integer>();var pending=new ArrayDeque<Integer>();pending.push(root);
        while(!pending.isEmpty()) {
            int id=pending.peek();if(memo.containsKey(id)){pending.pop();continue;}
            int reusable=cached(id,variable,operation);if(reusable>=0){memo.put(id,reusable);pending.pop();continue;}
            var n=nodes.get(id);
            if(id<2){memo.put(id,variable<0?1-id:id);pending.pop();continue;}
            if(variable>=0&&n.variable>=variable){memo.put(id,n.variable==variable?(present?n.high:n.low):id);pending.pop();continue;}
            if(!memo.containsKey(n.low)){pending.push(n.low);continue;}
            if(!memo.containsKey(n.high)){pending.push(n.high);continue;}
            memo.put(id,remember(id,variable,operation,node(n.variable,memo.get(n.low),memo.get(n.high))));pending.pop();
        }
        return remember(root,variable,operation,memo.get(root));
    }
    boolean test(int value,BitSet assignment) {
        while(value>=2){var n=nodes.get(value);value=assignment.get(n.variable)?n.high:n.low;}
        return value==TRUE;
    }
    /** Drop only scratch nodes allocated by a read-only query after its checkpoint.
     * No condition created in that scope may escape. Surviving IDs stay stable. */
    void discardAfter(int checkpoint) {
        if(checkpoint<2||checkpoint>nodes.size())throw new IllegalArgumentException("invalid BDD checkpoint");
        if(checkpoint==nodes.size())return;
        for(int id=nodes.size()-1;id>=checkpoint;id--)unique.remove(nodes.remove(id));
        // IDs can be reused. Invalidate every computed entry referring to scratch
        // operands/results, while retaining all computations on surviving nodes.
        for(int i=0;i<cacheOperation.length;i++)if(cacheA[i]>=checkpoint||cacheResult[i]>=checkpoint||cacheOperation[i]<2&&cacheB[i]>=checkpoint)cacheOperation[i]=-1;
    }
    int size(){return nodes.size();}
}
