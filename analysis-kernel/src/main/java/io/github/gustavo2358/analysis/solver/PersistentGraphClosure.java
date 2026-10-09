package io.github.gustavo2358.analysis.solver;

import java.util.Arrays;
import java.util.Objects;

/** Exact labeled ancestor closure via SCC condensation and shared canonical sets.
 * Labels are nonnegative; -1 marks an unlabeled vertex. The input graph is borrowed
 * for preparation only. Query handles belong to this instance and expire at close.
 * The page store is borrowed; metadata and all owned map/arena leases are closed here. */
public final class PersistentGraphClosure implements AutoCloseable {
    private final AnalysisResources.Reservation metadata;
    private CanonicalTupleArena arena;
    private PersistentLongMap sets;
    private int[] components;
    private long[] roots;
    private boolean closed;

    public PersistentGraphClosure(PageStore store,AnalysisResources resources,AnalysisResources.Phase phase,int[] variables,int[][] parents) {
        Objects.requireNonNull(variables);Objects.requireNonNull(parents);Objects.requireNonNull(phase);
        if(parents.length!=variables.length)throw new IllegalArgumentException("ancestor graph dimensions");
        int n=variables.length,edges=0;
        for(int i=0;i<n;i++) {
            if(variables[i]<-1)throw new IllegalArgumentException("invalid vertex label");
            for(int parent:parents[i])if(parent<0||parent>=n)throw new IllegalArgumentException("foreign ancestor ordinal");
            edges=Math.addExact(edges,parents[i].length);
        }
        metadata=resources.reserve(AnalysisResources.Pool.RESIDENT,4096L+128L*n+16L*edges,phase);
        try {
            arena=new CanonicalTupleArena(store,resources,phase,6,new int[]{2,4,5});
            sets=new PersistentLongMap(arena,resources,phase);
            int[] starts=new int[n+1];
            for(int[] incoming:parents)for(int parent:incoming)starts[parent+1]++;
            for(int i=0;i<n;i++)starts[i+1]+=starts[i];
            int[] children=new int[edges],write=starts.clone();
            for(int child=0;child<n;child++)for(int parent:parents[child])children[write[parent]++]=child;
            boolean[] seen=new boolean[n];int[] stack=new int[n],cursor=new int[n],order=new int[n];int used=0;
            for(int root=0;root<n;root++)if(!seen[root]) {
                int depth=0;stack[depth++]=root;seen[root]=true;cursor[root]=starts[root];
                while(depth>0) {
                    int v=stack[depth-1];
                    if(cursor[v]<starts[v+1]) {
                        int next=children[cursor[v]++];
                        if(!seen[next]){seen[next]=true;cursor[next]=starts[next];stack[depth++]=next;}
                    }else {depth--;order[used++]=v;}
                }
            }
            components=new int[n];Arrays.fill(components,-1);int count=0;
            for(int pos=n-1;pos>=0;pos--)if(components[order[pos]]<0) {
                int depth=0;stack[depth++]=order[pos];components[order[pos]]=count;
                while(depth>0)for(int parent:parents[stack[--depth]])if(components[parent]<0){components[parent]=count;stack[depth++]=parent;}
                count++;
            }
            roots=new long[count];int[] heads=new int[count],next=new int[edges],targets=new int[edges],indegrees=new int[count];Arrays.fill(heads,-1);int size=0;
            for(int child=0;child<n;child++) {
                int to=components[child];if(variables[child]>=0)roots[to]=sets.put(roots[to],variables[child],1);
                for(int parent:parents[child]) {
                    int from=components[parent];if(from==to)continue;
                    targets[size]=to;next[size]=heads[from];heads[from]=size++;indegrees[to]++;
                }
            }
            int[] pending=new int[count];int first=0,last=0;
            for(int i=0;i<count;i++)if(indegrees[i]==0)pending[last++]=i;
            while(first<last) {
                int from=pending[first++];
                for(int edge=heads[from];edge>=0;edge=next[edge]) {
                    int to=targets[edge];roots[to]=sets.join(roots[to],roots[from],false,(a,b)->1);
                    if(--indegrees[to]==0)pending[last++]=to;
                }
            }
            if(last!=count)throw new IllegalStateException("cyclic ancestor condensation");
            long[] retained=roots.clone();Arrays.sort(retained);long previous=0;
            for(long root:retained)if(root!=0&&root!=previous){arena.retain(root);previous=root;}
        }catch(RuntimeException|Error failure){closeSuppressed(sets,failure);closeSuppressed(arena,failure);metadata.close();throw failure;}
    }
    public long root(int vertex){open();return roots[components[vertex]];}
    public boolean contains(int vertex,int key){open();return sets.contains(root(vertex),key);}
    public long records(){open();return arena.size();}
    private void open(){if(closed)throw new IllegalStateException("graph closure closed");}
    private static void closeSuppressed(AutoCloseable value,Throwable failure) {
        if(value!=null)try{value.close();}catch(Exception|Error closing){failure.addSuppressed(closing);}
    }
    @Override public void close() {
        if(closed)return;closed=true;
        try{sets.close();}finally{try{arena.close();}finally{components=null;roots=null;metadata.close();}}
    }
}
