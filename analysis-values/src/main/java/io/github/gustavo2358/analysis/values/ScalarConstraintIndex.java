package io.github.gustavo2358.analysis.values;

import io.github.gustavo2358.analysis.structure.ProgramStore;
import java.util.*;
import java.util.function.*;

/** Iterative Kosaraju SCC with sink-first rank minima. AIR guards live in its
 * callers; columns are native-owned or explicit resident compatibility. */
final class ScalarConstraintIndex implements AutoCloseable {
    private final ProgramStore store;
    private final ProgramStore.OrdinalColumn[] columns=new ProgramStore.OrdinalColumn[11];
    private final ProgramStore.CapacityLease capacity;
    private int owned;
    private boolean closed;
    private ProgramStore.OrdinalColumn offset,edge,reverseOffset,reverseEdge,seen,stack,next,post,owner,minimum,members;
    public long buildVertexVisits,buildEdgeVisits,traversalVertexVisits,traversalEdgeVisits;
    static ScalarConstraintIndex open(ProgramStore.Structural store,int vertices,IntUnaryOperator degree,IntBinaryOperator target,IntToLongFunction rank) {
        Objects.requireNonNull(store);if(vertices<0)throw new IllegalArgumentException("negative graph size");
        var budget=store.executionBudget();
        if(budget.isPresent()&&store.storageInventory().isEmpty())throw new IllegalArgumentException("native graph requires owned columns");
        var lease=budget.isPresent()?budget.orElseThrow().reserve(ProgramStore.CapacityPool.RESIDENT,512,ProgramStore.ExecutionPhase.DEMAND):null;
        ScalarConstraintIndex result=null;
        try {
            result=new ScalarConstraintIndex(store,lease);
            if(lease!=null)lease.onOwnerClose(result::close);
            result.compile(vertices,Objects.requireNonNull(degree),Objects.requireNonNull(target),Objects.requireNonNull(rank));return result;
        } catch(RuntimeException|Error failure) {
            try{if(result!=null)result.close();else if(lease!=null)lease.close();}catch(RuntimeException|Error cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}throw failure;
        }
    }
    private ScalarConstraintIndex(ProgramStore store,ProgramStore.CapacityLease capacity){this.store=store;this.capacity=capacity;}
    private ProgramStore.OrdinalColumn column(long length) {
        var value=((ProgramStore.Structural)store).storageInventory().map(inventory->inventory.column(length)).orElseGet(()->ProgramStore.residentColumn(length));
        columns[owned++]=value;return value;
    }
    private void progress(){if(closed)throw new IllegalStateException("constraint index closed");store.progress(ProgramStore.ExecutionPhase.DEMAND);}
    private void compile(int n,IntUnaryOperator degree,IntBinaryOperator target,IntToLongFunction rank) {
        offset=column((long)n+1);reverseOffset=column((long)n+1);seen=column(n);stack=column(n);next=column(n);post=column(n);owner=column(n);minimum=column(n);members=column(n);
        long total=0;
        for(int v=0;v<n;v++){progress();buildVertexVisits++;int count=degree.applyAsInt(v);if(count<0)throw new IllegalArgumentException("negative degree");offset.set(v,total);total=Math.addExact(total,count);owner.set(v,-1);}
        offset.set(n,total);edge=column(total);reverseEdge=column(total);
        for(int v=0;v<n;v++) {
            progress();buildVertexVisits++;int count=Math.toIntExact(offset.get(v+1)-offset.get(v));
            for(int at=0;at<count;at++){progress();int w=target.applyAsInt(v,at);if(w<0||w>=n)throw new IllegalArgumentException("edge target outside graph");edge.set(offset.get(v)+at,w);reverseOffset.set(w+1,Math.incrementExact(reverseOffset.get(w+1)));buildEdgeVisits++;}
        }
        for(int v=0;v<n;v++){progress();buildVertexVisits++;reverseOffset.set(v+1,Math.addExact(reverseOffset.get(v),reverseOffset.get(v+1)));next.set(v,reverseOffset.get(v));}
        for(int v=0;v<n;v++) {
            progress();buildVertexVisits++;
            for(long at=offset.get(v);at<offset.get(v+1);at++){int w=Math.toIntExact(edge.get(at));long destination=next.get(w);reverseEdge.set(destination,v);next.set(w,destination+1);buildEdgeVisits++;}
        }
        long finished=0;
        for(int root=0;root<n;root++)if(seen.get(root)==0) {
            long depth=1;seen.set(root,1);traversalVertexVisits++;stack.set(0,root);next.set(0,reverseOffset.get(root));
            while(depth>0) {
                progress();long level=depth-1;int v=Math.toIntExact(stack.get(level));long at=next.get(level);
                if(at==reverseOffset.get(v+1)){post.set(finished++,v);depth--;continue;}
                next.set(level,at+1);int w=Math.toIntExact(reverseEdge.get(at));traversalEdgeVisits++;
                if(seen.get(w)==0){seen.set(w,1);traversalVertexVisits++;stack.set(depth,w);next.set(depth,reverseOffset.get(w));depth++;}
            }
        }
        int components=0;
        for(long at=finished;at>0;) {
            int root=Math.toIntExact(post.get(--at));if(owner.get(root)>=0)continue;
            int component=components++;long depth=1,count=0,least=Long.MAX_VALUE;owner.set(root,component);stack.set(0,root);
            while(depth>0) {
                progress();int v=Math.toIntExact(stack.get(--depth));members.set(count++,v);traversalVertexVisits++;
                long direct=rank.applyAsLong(v);if(direct<0)throw new IllegalArgumentException("negative refusal rank");least=Math.min(least,direct);
                for(long position=offset.get(v);position<offset.get(v+1);position++) {
                    int w=Math.toIntExact(edge.get(position));traversalEdgeVisits++;
                    if(owner.get(w)<0){owner.set(w,component);stack.set(depth++,w);}
                }
            }
            for(long member=0;member<count;member++) {
                progress();int v=Math.toIntExact(members.get(member));traversalVertexVisits++;
                for(long position=offset.get(v);position<offset.get(v+1);position++) {
                    int w=Math.toIntExact(edge.get(position));long dependency=owner.get(w);traversalEdgeVisits++;
                    if(dependency!=component){if(dependency<0||dependency>=component)throw new IllegalStateException("component order violated");least=Math.min(least,minimum.get(dependency));}
                }
            }
            minimum.set(component,least);
        }
    }
    public long minimum(int root){progress();return minimum.get(owner.get(root));}
    @Override public void close() {
        if(closed)return;closed=true;Throwable failure=null;
        while(owned>0){var column=columns[--owned];columns[owned]=null;try{column.close();}catch(RuntimeException|Error cleanup){if(failure==null)failure=cleanup;else if(failure!=cleanup)failure.addSuppressed(cleanup);}}
        if(capacity!=null)try{capacity.close();}catch(RuntimeException|Error cleanup){if(failure==null)failure=cleanup;else if(failure!=cleanup)failure.addSuppressed(cleanup);}
        if(failure instanceof RuntimeException exception)throw exception;if(failure instanceof Error error)throw error;
    }
}
