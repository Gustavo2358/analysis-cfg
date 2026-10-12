package io.github.gustavo2358.analysis.solver;

import java.util.*;
import java.util.function.ToIntFunction;

/** Verified individual caller paths. A miss still requires the exact symbolic search. */
final class CallerWitnesses<R> implements AutoCloseable {
    record Edge<R>(R child,int condition,int pushedVariable) {
        Edge(R child,int condition){this(child,condition,Integer.MIN_VALUE);}
    }
    private static final AnalysisResources.Phase PHASE=AnalysisResources.Phase.CONTROL;
    private final BooleanConditions conditions;
    private final AnalysisResources resources;
    private final boolean ownsPages;
    private final long baselineBytes;
    private final Map<R,Catalog> witnesses=new IdentityHashMap<>();
    private PageStore pages;
    private CanonicalTupleArena arena;
    private PersistentLongMap sets;
    private long collections, retiredRecords;
    private boolean closed;

    CallerWitnesses(BooleanConditions conditions,int variables,R root,
                    Map<R,List<Edge<R>>> successors,ToIntFunction<R> variable) {
        this(conditions,variables,root,successors,variable,
            new AnalysisResources(new AnalysisResources.Limits(Long.MAX_VALUE,Long.MAX_VALUE,Long.MAX_VALUE,Long.MAX_VALUE,Long.MAX_VALUE,Long.MAX_VALUE,Long.MAX_VALUE)),null);
    }
    CallerWitnesses(BooleanConditions conditions,int variables,R root,
                    Map<R,List<Edge<R>>> successors,ToIntFunction<R> variable,
                    AnalysisResources resources,PageStore storage) {
        if(variables<0)throw new IllegalArgumentException("negative variable universe");
        this.conditions=Objects.requireNonNull(conditions);this.resources=Objects.requireNonNull(resources);
        ownsPages=storage==null;baselineBytes=resources.heapUsed();
        try {
            pages=ownsPages?new ResidentPageStore(4096,resources,PHASE):storage;
            arena=new CanonicalTupleArena(pages,resources,PHASE,6,new int[]{2,4,5});
            sets=new PersistentLongMap(arena,resources,PHASE);
            build(root,successors,variable);
        } catch(RuntimeException|Error failure) {
            try{close();}catch(RuntimeException|Error cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}
            throw failure;
        }
    }
    private void build(R root,Map<R,List<Edge<R>>> successors,ToIntFunction<R> variable) {
        try(var pending=new Queue()) {
            pending.add(root,0);long nextCollection=64;
            while(pending.size!=0) {
                R parent=pending.region();long incoming=pending.root(),token=pending.token();pending.remove();
                try {
                    var catalog=witnesses.get(parent);
                    long always=catalog==null?incoming:sets.intersectKeys(catalog.always,incoming);
                    boolean fresh=catalog==null||always!=catalog.always||sets.intersectKeys(incoming,catalog.ever)!=incoming;
                    if(fresh) {
                        if(catalog==null){catalog=new Catalog();witnesses.put(parent,catalog);}
                        // Transfer the queued retention into the individual witness.
                        catalog.add(incoming,token);token=0;
                        long ever=sets.join(catalog.ever,incoming,false,(left,right)->left);
                        catalog.literals(ever,always);
                        for(var edge:successors.getOrDefault(parent,List.of()))if(conditions.test(edge.condition(),sets,incoming)) {
                            int key=edge.pushedVariable()==Integer.MIN_VALUE?variable.applyAsInt(parent):edge.pushedVariable();
                            long active=key<0?incoming:sets.put(incoming,key,1);
                            pending.add(edge.child(),active);
                        }
                    }
                } finally {if(token!=0)arena.release(token);}
                // Every catalog, literal summary and queued valuation owns a root.
                if(arena.size()>=nextCollection){retiredRecords+=arena.collect();collections++;nextCollection=Math.max(64,Math.multiplyExact(2,arena.size()));}
            }
            retiredRecords+=arena.collect();collections++;
        }
    }
    boolean matches(R region,int condition) {
        open();var catalog=witnesses.get(region);if(catalog==null)return false;
        for(int i=0;i<catalog.size;i++)if(conditions.test(condition,sets,catalog.roots[i]))return true;
        return false;
    }
    int size(R region){open();var catalog=witnesses.get(region);return catalog==null?0:catalog.size;}
    long retainedValuationBytes(){open();return resources.heapUsed()-baselineBytes;}
    long valuationRecords(){open();return arena.size();}
    long retiredValuationRecords(){open();return retiredRecords;}
    long collections(){open();return collections;}
    private void open(){if(closed)throw new IllegalStateException("caller witnesses closed");}
    private final class Catalog implements AutoCloseable {
        long ever,always,everToken,alwaysToken;
        long[] roots,tokens;int size;
        AnalysisResources.Reservation capacity;
        Catalog(){
            capacity=resources.reserve(AnalysisResources.Pool.RESIDENT,320,PHASE);
            try{roots=new long[4];tokens=new long[4];}catch(RuntimeException|Error failure){capacity.close();throw failure;}
        }
        void add(long root,long token) {
            if(size==roots.length) {
                int count=Math.multiplyExact(2,roots.length);
                var staged=resources.reserve(AnalysisResources.Pool.RESIDENT,256+16L*count,PHASE);
                try {
                    long[] newRoots=Arrays.copyOf(roots,count),newTokens=Arrays.copyOf(tokens,count);
                    roots=newRoots;tokens=newTokens;capacity.close();capacity=staged;staged=null;
                } finally {if(staged!=null)staged.close();}
            }
            roots[size]=root;tokens[size++]=token;
        }
        void literals(long newEver,long newAlways) {
            long first=0,second=0;
            try {
                if(newEver!=0)first=arena.retain(newEver);
                if(newAlways!=0)second=arena.retain(newAlways);
            } catch(RuntimeException|Error failure){
                if(first!=0)try{arena.release(first);}catch(RuntimeException|Error cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}
                throw failure;
            }
            if(everToken!=0)arena.release(everToken);if(alwaysToken!=0)arena.release(alwaysToken);
            ever=newEver;always=newAlways;everToken=first;alwaysToken=second;
        }
        @Override public void close(){capacity.close();roots=null;tokens=null;}
    }
    /** Parallel primitive columns; capacity admission includes the old-plus-new peak. */
    private final class Queue implements AutoCloseable {
        Object[] regions;long[] roots,tokens;int head,size;
        AnalysisResources.Reservation capacity;
        Queue(){
            capacity=resources.reserve(AnalysisResources.Pool.SCRATCH,320,PHASE);
            try{regions=new Object[8];roots=new long[8];tokens=new long[8];}catch(RuntimeException|Error failure){capacity.close();throw failure;}
        }
        void add(R region,long root) {
            if(size==regions.length)grow();
            long token=root==0?0:arena.retain(root);int slot=(head+size)%regions.length;
            regions[slot]=region;roots[slot]=root;tokens[slot]=token;size++;
        }
        void grow() {
            int count=Math.multiplyExact(2,regions.length);var staged=resources.reserve(AnalysisResources.Pool.SCRATCH,128+24L*count,PHASE);
            try {
                Object[] newRegions=new Object[count];long[] newRoots=new long[count],newTokens=new long[count];
                for(int i=0;i<size;i++){int slot=(head+i)%regions.length;newRegions[i]=regions[slot];newRoots[i]=roots[slot];newTokens[i]=tokens[slot];}
                regions=newRegions;roots=newRoots;tokens=newTokens;head=0;capacity.close();capacity=staged;staged=null;
            } finally {if(staged!=null)staged.close();}
        }
        @SuppressWarnings("unchecked") R region(){return (R)regions[head];}
        long root(){return roots[head];}long token(){return tokens[head];}
        void remove(){regions[head]=null;roots[head]=0;tokens[head]=0;head=(head+1)%regions.length;size--;}
        @Override public void close(){capacity.close();regions=null;roots=null;tokens=null;}
    }
    @Override public void close() {
        if(closed)return;closed=true;
        RuntimeException failure=null;
        for(var catalog:witnesses.values())catalog.close();witnesses.clear();
        try{if(sets!=null)sets.close();}catch(RuntimeException exception){failure=exception;}
        try{if(arena!=null)arena.close();}catch(RuntimeException exception){if(failure==null)failure=exception;else failure.addSuppressed(exception);}
        try{if(ownsPages&&pages!=null)pages.close();}catch(RuntimeException exception){if(failure==null)failure=exception;else failure.addSuppressed(exception);}
        if(failure!=null)throw failure;
    }
}
