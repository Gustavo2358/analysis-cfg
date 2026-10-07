package io.github.gustavo2358.analysis.solver;

import java.util.Arrays;
import java.util.Objects;

/** SCC caller closure with shared canonical sets; no frame-wide absence vectors. */
final class PersistentCallerSupport implements AutoCloseable {
    private final AnalysisResources.Reservation metadata;
    private CanonicalTupleArena arena;
    private PersistentLongMap sets;
    private int[] components;
    private long[] roots;
    private boolean closed;

    PersistentCallerSupport(PageStore store,AnalysisResources resources,int[] variables,int[][] parents) {
        Objects.requireNonNull(variables);Objects.requireNonNull(parents);
        if(parents.length!=variables.length)throw new IllegalArgumentException("caller graph dimensions");
        int n=variables.length,edges=0;
        for(int i=0;i<n;i++) {
            if(variables[i]<0)throw new IllegalArgumentException("negative activation key");
            for(int parent:parents[i])if(parent<0||parent>=n)throw new IllegalArgumentException("foreign caller ordinal");
            edges=Math.addExact(edges,parents[i].length);
        }
        metadata=resources.reserve(AnalysisResources.Pool.RESIDENT,4096L+128L*n+16L*edges,AnalysisResources.Phase.CONTROL);
        try {
            arena=new CanonicalTupleArena(store,resources,AnalysisResources.Phase.CONTROL,6,new int[]{2,4,5});
            sets=new PersistentLongMap(arena,resources,AnalysisResources.Phase.CONTROL);
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
                int to=components[child];roots[to]=sets.put(roots[to],variables[child],1);
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
            if(last!=count)throw new IllegalStateException("cyclic caller condensation");
            for(long root:roots)if(root!=0)arena.retain(root);
        }catch(RuntimeException|Error failure){closeSuppressed(sets,failure);closeSuppressed(arena,failure);metadata.close();throw failure;}
    }
    long root(int frame){open();return roots[components[frame]];}
    boolean contains(int frame,int key){open();return sets.contains(root(frame),key);}
    long records(){open();return arena.size();}
    private void open(){if(closed)throw new IllegalStateException("caller support closed");}
    private static void closeSuppressed(AutoCloseable value,Throwable failure) {
        if(value!=null)try{value.close();}catch(Exception|Error closing){failure.addSuppressed(closing);}
    }
    @Override public void close() {
        if(closed)return;closed=true;
        try{sets.close();}finally{try{arena.close();}finally{components=null;roots=null;metadata.close();}}
    }
}
