package io.github.gustavo2358.analysis.solver;

import java.util.Arrays;
import java.util.function.IntConsumer;

/** Individual guarded witnesses and an exact unguarded scheduling superset. */
final class CallerPathCertificates implements AutoCloseable {
    private static final AnalysisResources.Phase PHASE=AnalysisResources.Phase.CONTROL;
    private final BooleanConditions conditions;
    private final AnalysisResources resources;
    private final boolean ownsPages;
    private PageStore pages;
    private CanonicalTupleArena arena;
    private PersistentLongMap sets;
    private AnalysisResources.Reservation metadata;
    private Node first;
    private boolean closed,failed;
    private long edgesRead,indexProbes,nextCollection=64;

    final class Node {
        final CallerPathCertificates owner=CallerPathCertificates.this;
        final boolean basis;
        final AnalysisResources.Reservation lease;
        final KeyIndex index=new KeyIndex();
        final IntConsumer rawChange;
        boolean reached,retired;
        boolean rawReached;
        long word,token;
        Arc incoming,outgoing,proof;
        Node previous,next,children,previousSibling,nextSibling;
        Arc rawProof;
        Node rawChildren,rawPreviousSibling,rawNextSibling;
        Node(boolean basis,AnalysisResources.Reservation lease,IntConsumer rawChange){this.basis=basis;this.lease=lease;this.rawChange=rawChange;reached=basis;rawReached=basis;}
    }
    final class Arc {
        final CallerPathCertificates owner=CallerPathCertificates.this;
        final Node from,to;
        final int variable;
        int condition;
        final AnalysisResources.Reservation lease;
        Arc previousIn,nextIn,previousOut,nextOut,previousKey,nextKey;
        long word,token;
        boolean valid,removed;
        Arc(Node from,Node to,int variable,int condition,AnalysisResources.Reservation lease){this.from=from;this.to=to;this.variable=variable;this.condition=condition;this.lease=lease;}
    }
    CallerPathCertificates(BooleanConditions conditions,AnalysisResources resources,PageStore storage) {
        this.conditions=conditions;this.resources=resources;ownsPages=storage==null;
        try {
            metadata=resources.reserve(AnalysisResources.Pool.RESIDENT,512,PHASE);
            pages=ownsPages?new ResidentPageStore(4096,resources,PHASE):storage;
            arena=new CanonicalTupleArena(pages,resources,PHASE,6,new int[]{2,4,5});
            sets=new PersistentLongMap(arena,resources,PHASE);
        }catch(RuntimeException|Error error){try{close();}catch(RuntimeException|Error cleanup){if(cleanup!=error)error.addSuppressed(cleanup);}throw error;}
    }
    long edgesRead(){return edgesRead;}
    long indexProbes(){return indexProbes;}
    long valuationNodeVisits(){return sets.nodeVisits();}
    private void open(){if(closed||failed)throw new IllegalStateException("caller certificates unavailable");}
    private void owned(Node node){open();if(node==null||node.owner!=this)throw new IllegalArgumentException("foreign caller node");}
    Node node(boolean basis) {
        return node(basis,null);
    }
    Node node(boolean basis,IntConsumer rawChange) {
        open();var lease=resources.reserve(AnalysisResources.Pool.RESIDENT,384,PHASE);
        Node node;
        try{node=new Node(basis,lease,rawChange);}catch(RuntimeException|Error error){lease.close();throw error;}
        node.next=first;if(first!=null)first.previous=node;first=node;return node;
    }
    boolean rawReached(Node node){owned(node);return !node.retired&&node.rawReached;}
    Arc add(Node from,Node to,int variable,int condition) {
        owned(from);owned(to);if(from.retired||to.retired||variable<0)throw new IllegalArgumentException("invalid caller binding");
        var lease=resources.reserve(AnalysisResources.Pool.RESIDENT,128,PHASE);Arc arc;
        try{arc=new Arc(from,to,variable,condition,lease);to.index.prepare(variable);}catch(RuntimeException|Error error){lease.close();throw error;}
        arc.nextOut=from.outgoing;if(arc.nextOut!=null)arc.nextOut.previousOut=arc;from.outgoing=arc;
        arc.nextIn=to.incoming;if(arc.nextIn!=null)arc.nextIn.previousIn=arc;to.incoming=arc;to.index.insert(arc);
        try {
            growRaw(arc);
            refresh(arc);
            if(arc.valid&&!to.reached)try(var queue=new Nodes()){attach(arc);queue.add(to);expand(queue);}
            collect();return arc;
        }catch(RuntimeException|Error error){failed=true;throw error;}
    }
    boolean matches(Node node,int condition) {
        owned(node);if(node.retired||condition==0)return false;
        int required=conditions.requiredPresent(condition);
        if(required<0)required=conditions.possiblePresent(condition);
        if(required>=0)for(var arc=node.index.get(required);arc!=null;arc=arc.nextKey) {
            edgesRead++;if(arc.valid&&conditions.test(condition,sets,arc.word))return true;
        }
        return node.reached&&conditions.test(condition,sets,node.word);
    }
    void remove(Arc arc) {
        open();if(arc==null||arc.owner!=this||arc.removed)throw new IllegalArgumentException("missing caller binding");
        try {
            if(arc.previousOut==null)arc.from.outgoing=arc.nextOut;else arc.previousOut.nextOut=arc.nextOut;
            if(arc.nextOut!=null)arc.nextOut.previousOut=arc.previousOut;
            if(arc.previousIn==null)arc.to.incoming=arc.nextIn;else arc.previousIn.nextIn=arc.nextIn;
            if(arc.nextIn!=null)arc.nextIn.previousIn=arc.previousIn;
            arc.removed=true;release(arc);arc.lease.close();arc.to.index.remove(arc);
            if(arc.to.rawProof==arc)repairRaw(arc.to);
            if(arc.to.proof==arc)repair(arc.to);
            collect();
        }catch(RuntimeException|Error error){failed=true;throw error;}
    }
    void update(Arc arc,int condition) {
        open();if(arc==null||arc.owner!=this||arc.removed)throw new IllegalArgumentException("missing caller binding");
        if(arc.condition==condition)return;
        try {
            int previous=arc.condition;arc.condition=condition;
            if(condition==0&&arc.to.rawProof==arc)repairRaw(arc.to);
            else if(previous==0&&condition!=0)growRaw(arc);
            refresh(arc);
            if(arc.to.proof==arc&&!arc.valid)repair(arc.to);
            else if(arc.valid&&!arc.to.reached)try(var queue=new Nodes()){attach(arc);queue.add(arc.to);expand(queue);}
            collect();
        }catch(RuntimeException|Error error){failed=true;throw error;}
    }
    void retire(Node node) {
        owned(node);if(node.retired)return;if(node.basis)throw new IllegalArgumentException("cannot retire Entry basis");
        while(node.incoming!=null)remove(node.incoming);
        while(node.outgoing!=null)remove(node.outgoing);
        node.retired=true;node.index.close();node.lease.close();
        if(node.previous==null)first=node.next;else node.previous.next=node.next;
        if(node.next!=null)node.next.previous=node.previous;node.previous=null;node.next=null;
    }
    private void refresh(Arc arc) {
        edgesRead++;
        if(arc.from.reached&&conditions.test(arc.condition,sets,arc.from.word)) {
            long word=sets.put(arc.from.word,arc.variable,1);
            if(arc.valid&&arc.word==word)return;
            long token=arena.retain(word);release(arc);arc.word=word;arc.token=token;arc.valid=true;
        }else release(arc);
    }
    private void release(Arc arc){if(arc.token!=0)arena.release(arc.token);arc.word=0;arc.token=0;arc.valid=false;}
    private void attach(Arc arc) {
        var node=arc.to;node.token=arena.retain(arc.word);node.word=arc.word;node.reached=true;node.proof=arc;
        node.nextSibling=arc.from.children;if(node.nextSibling!=null)node.nextSibling.previousSibling=node;arc.from.children=node;
    }
    private void unlinkProof(Node node) {
        var parent=node.proof.from;
        if(node.previousSibling==null)parent.children=node.nextSibling;else node.previousSibling.nextSibling=node.nextSibling;
        if(node.nextSibling!=null)node.nextSibling.previousSibling=node.previousSibling;
        node.previousSibling=null;node.nextSibling=null;
    }
    private void repair(Node start) {
        unlinkProof(start);
        try(var affected=new Nodes();var queue=new Nodes()) {
            affected.add(start);
            for(int i=0;i<affected.size;i++) {
                var node=affected.get(i);
                for(var child=node.children;child!=null;) {
                    edgesRead++;var next=child.nextSibling;child.previousSibling=null;child.nextSibling=null;affected.add(child);child=next;
                }
                node.children=null;node.proof=null;node.reached=false;node.word=0;
                if(node.token!=0)arena.release(node.token);node.token=0;
            }
            for(int i=0;i<affected.size;i++)for(var arc=affected.get(i).outgoing;arc!=null;arc=arc.nextOut)refresh(arc);
            for(int i=0;i<affected.size;i++) {
                var node=affected.get(i);if(node.reached)continue;
                for(var arc=node.incoming;arc!=null;arc=arc.nextIn) {
                    refresh(arc);if(arc.valid){attach(arc);queue.add(node);break;}
                }
            }
            expand(queue);
        }
    }
    private void expand(Nodes queue) {
        for(int i=0;i<queue.size;i++)for(var arc=queue.get(i).outgoing;arc!=null;arc=arc.nextOut) {
            refresh(arc);if(arc.valid&&!arc.to.reached){attach(arc);queue.add(arc.to);}
        }
    }
    private void attachRaw(Arc arc,boolean notify) {
        var node=arc.to;node.rawReached=true;node.rawProof=arc;
        node.rawNextSibling=arc.from.rawChildren;
        if(node.rawNextSibling!=null)node.rawNextSibling.rawPreviousSibling=node;
        arc.from.rawChildren=node;
        if(notify&&node.rawChange!=null)node.rawChange.accept(1);
    }
    private void growRaw(Arc arc) {
        if(arc.condition==0||!arc.from.rawReached||arc.to.rawReached)return;
        try(var queue=new Nodes()){attachRaw(arc,true);queue.add(arc.to);expandRaw(queue,true);}
    }
    private void expandRaw(Nodes queue,boolean notify) {
        for(int i=0;i<queue.size;i++)for(var arc=queue.get(i).outgoing;arc!=null;arc=arc.nextOut) {
            edgesRead++;
            if(arc.condition!=0&&!arc.to.rawReached){attachRaw(arc,notify);queue.add(arc.to);}
        }
    }
    private void repairRaw(Node start) {
        var parent=start.rawProof.from;
        if(start.rawPreviousSibling==null)parent.rawChildren=start.rawNextSibling;else start.rawPreviousSibling.rawNextSibling=start.rawNextSibling;
        if(start.rawNextSibling!=null)start.rawNextSibling.rawPreviousSibling=start.rawPreviousSibling;
        start.rawPreviousSibling=null;start.rawNextSibling=null;
        try(var affected=new Nodes();var queue=new Nodes()) {
            affected.add(start);
            for(int i=0;i<affected.size;i++) {
                var node=affected.get(i);
                for(var child=node.rawChildren;child!=null;) {
                    edgesRead++;var next=child.rawNextSibling;child.rawPreviousSibling=null;child.rawNextSibling=null;affected.add(child);child=next;
                }
                node.rawChildren=null;node.rawProof=null;node.rawReached=false;
            }
            for(int i=0;i<affected.size;i++) {
                var node=affected.get(i);if(node.rawReached)continue;
                for(var arc=node.incoming;arc!=null;arc=arc.nextIn) {
                    edgesRead++;
                    if(arc.condition!=0&&arc.from.rawReached){attachRaw(arc,false);queue.add(node);break;}
                }
            }
            expandRaw(queue,false);
            for(int i=0;i<affected.size;i++) {
                var node=affected.get(i);if(!node.rawReached&&node.rawChange!=null)node.rawChange.accept(0);
            }
        }
    }
    private void collect(){if(arena.size()>=nextCollection){arena.collect();nextCollection=Math.max(64,Math.multiplyExact(2,arena.size()));}}
    private final class Nodes implements AutoCloseable {
        Object[] values;int size;AnalysisResources.Reservation capacity;
        Nodes(){capacity=resources.reserve(AnalysisResources.Pool.SCRATCH,192,PHASE);try{values=new Object[8];}catch(RuntimeException|Error error){capacity.close();throw error;}}
        void add(Node node) {
            if(size==values.length) {
                int count=Math.multiplyExact(2,values.length);var staged=resources.reserve(AnalysisResources.Pool.SCRATCH,128+8L*count,PHASE);
                try{values=Arrays.copyOf(values,count);capacity.close();capacity=staged;staged=null;}finally{if(staged!=null)staged.close();}
            }
            values[size++]=node;
        }
        Node get(int index){return (Node)values[index];}
        public void close(){capacity.close();values=null;}
    }
    /** One bucket per actual pushed key; no frame-universe vector per summary. */
    private final class KeyIndex implements AutoCloseable {
        int[] keys;Object[] heads;int size,tombstones;
        AnalysisResources.Reservation capacity;
        private int slot(int key) {
            int slot=(int)StateIndex.mix(key)&(keys.length-1);
            while(keys[slot]!=-1&&keys[slot]!=key){indexProbes++;slot=(slot+1)&(keys.length-1);}indexProbes++;return slot;
        }
        Arc get(int key){if(keys==null)return null;int slot=slot(key);return keys[slot]==key?(Arc)heads[slot]:null;}
        void prepare(int key) {
            if(keys==null)resize(8);
            if(get(key)!=null)return;
            if(4L*(size+tombstones+1)>3L*keys.length)resize(4L*(size+1)<=keys.length?keys.length:Math.multiplyExact(2,keys.length));
        }
        void insert(Arc arc) {
            int slot=slot(arc.variable);
            if(keys[slot]!=arc.variable) {
                // Reuse the first tombstone before an empty slot.
                int candidate=(int)StateIndex.mix(arc.variable)&(keys.length-1);
                while(candidate!=slot&&keys[candidate]!=-2)candidate=(candidate+1)&(keys.length-1);
                if(keys[candidate]==-2){slot=candidate;tombstones--;}
                keys[slot]=arc.variable;size++;
            }
            arc.nextKey=(Arc)heads[slot];if(arc.nextKey!=null)arc.nextKey.previousKey=arc;heads[slot]=arc;
        }
        void remove(Arc arc) {
            int slot=slot(arc.variable);
            if(arc.previousKey==null)heads[slot]=arc.nextKey;else arc.previousKey.nextKey=arc.nextKey;
            if(arc.nextKey!=null)arc.nextKey.previousKey=arc.previousKey;arc.previousKey=null;arc.nextKey=null;
            if(heads[slot]==null){keys[slot]=-2;size--;tombstones++;}
            if(size==0)close();else if(keys.length>8&&4L*size<keys.length)resize(keys.length/2);
        }
        void resize(int count) {
            var staged=resources.reserve(AnalysisResources.Pool.RESIDENT,128+12L*count,PHASE);
            try {
                int[] newKeys=new int[count];Arrays.fill(newKeys,-1);Object[] newHeads=new Object[count];
                if(keys!=null)for(int i=0;i<keys.length;i++)if(keys[i]>=0) {
                    int slot=(int)StateIndex.mix(keys[i])&(count-1);while(newKeys[slot]!=-1){indexProbes++;slot=(slot+1)&(count-1);}indexProbes++;
                    newKeys[slot]=keys[i];newHeads[slot]=heads[i];
                }
                keys=newKeys;heads=newHeads;tombstones=0;if(capacity!=null)capacity.close();capacity=staged;staged=null;
            }finally{if(staged!=null)staged.close();}
        }
        public void close(){if(capacity!=null)capacity.close();capacity=null;keys=null;heads=null;size=0;tombstones=0;}
    }
    @Override public void close() {
        if(closed)return;closed=true;
        try {
            for(var node=first;node!=null;node=node.next) {
                for(var arc=node.outgoing;arc!=null;arc=arc.nextOut)arc.lease.close();
                node.index.close();node.lease.close();
            }
            first=null;
        }finally {try{if(sets!=null)sets.close();}finally{try{if(arena!=null)arena.close();}finally{try{if(ownsPages&&pages!=null)pages.close();}finally{if(metadata!=null)metadata.close();}}}}
    }
}
