package io.github.gustavo2358.analysis.solver;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongConsumer;

/** Strong root/edge ownership for a caller-proved acyclic expression DAG. Creation
 * holds survive until publication; replacing a root acquires before release.
 * Retirement visits each outgoing edge once and uses a paged queue. Required
 * metadata, construction journal and roots spill. Graph callbacks are single-writer
 * and cannot reenter this owner. Nodes0/1 are immortal constants. Root tokens encode
 * a non-reused owner31/local32 identity; capacity exhaustion is operational failure.
 * Cyclic equations use the separate SummaryCollector. */
final class PagedDagOwnership implements AutoCloseable {
    interface Graph {
        /** Pure idempotent identity mapping; constants map below2. Root values
         * keep their original identity/polarity, edge counts use the canonical node. */
        default long canonical(long node){return node;}
        void children(long node,LongConsumer accept);void retire(long node);
    }
    private static final int LIVE=1,LINKED=2,BORN=4;
    private static final long ROOT_MASK=0xffffffffL,MAX_NODE=(Long.MAX_VALUE-3)/3;
    private static final AtomicLong OWNERS=new AtomicLong();
    private final long owner;
    private final Graph graph;
    private final AnalysisResources.Reservation controls;
    private final LongConsumer retainChild=this::retain,releaseChild=this::release;
    private PagedLongArray rows,roots,births;
    private PagedWorklist pending;
    private long issuedRoots,issuedGenerations,birthCount,linking;
    private boolean closed,failed,busy;
    PagedDagOwnership(PageStore pages,AnalysisResources resources,AnalysisResources.Phase phase,Graph graph){
        this.graph=Objects.requireNonNull(graph);owner=ownerId();controls=resources.reserve(AnalysisResources.Pool.RESIDENT,512,phase);
        try{
            rows=new PagedLongArray(pages,Long.MAX_VALUE,resources,phase);
            roots=new PagedLongArray(pages,Long.MAX_VALUE,resources,phase);
            births=new PagedLongArray(pages,Long.MAX_VALUE,resources,phase);
            pending=new PagedWorklist(pages,resources,phase);
        }catch(RuntimeException|Error error){try{close();}catch(RuntimeException cleanup){error.addSuppressed(cleanup);}throw error;}
    }
    private static long ownerId(){
        while(true){long old=OWNERS.get();if(old==Integer.MAX_VALUE)throw invalid("DAG owner space exhausted");if(OWNERS.compareAndSet(old,old+1))return old+1;}
    }
    private static PageStore.Failure invalid(String message){return new PageStore.Failure(PageStore.Reason.INVALID_HANDLE,message);}
    private void open(){if(closed||failed)throw new IllegalStateException("DAG ownership closed or aborted");if(busy)throw new IllegalStateException("reentrant DAG ownership callback");}
    void checkOpen(){open();}
    private static void node(long node){if(node<0||node>MAX_NODE)throw new IllegalArgumentException("DAG node outside metadata address space");}
    private long canonical(long value){node(value);long result=graph.canonical(value);node(result);return result;}
    private long field(long node,int column){return rows.get(node*3+column);}
    private void field(long node,int column,long value){rows.set(node*3+column,value);}
    private void active(long node){if(node>=2&&(field(node,1)&LIVE)==0)throw invalid("inactive DAG node");}
    private void retain(long node){
        node=canonical(node);if(node<2)return;active(node);if(node==linking)throw invalid("self-referencing expression DAG");
        long count=field(node,0);if(count==Long.MAX_VALUE)throw invalid("DAG reference count exhausted");field(node,0,count+1);
    }
    private void release(long node){
        node=canonical(node);if(node<2)return;active(node);long count=field(node,0);
        if(count<=0)throw invalid("DAG reference underflow");field(node,0,count-1);if(count==1)pending.add(node);
    }
    private void declareUnchecked(long node){
        if(node<2)return;if(field(node,1)!=0)throw invalid("duplicate live DAG declaration");
        if(issuedGenerations==Long.MAX_VALUE||birthCount>=Long.MAX_VALUE/2)throw invalid("DAG construction identity exhausted");
        long generation=++issuedGenerations;field(node,0,1);field(node,1,LIVE|BORN);field(node,2,generation);
        births.set(birthCount*2,node);births.set(birthCount*2+1,generation);birthCount++;
    }
    private void linkUnchecked(long node){
        if(node<2)return;active(node);if((field(node,1)&LINKED)!=0)throw invalid("DAG edges already owned");
        linking=node;try{graph.children(node,retainChild);}finally{linking=0;}
        field(node,1,field(node,1)|LINKED);
    }
    void created(long node){
        open();node=canonical(node);busy=true;
        try{declareUnchecked(node);linkUnchecked(node);}catch(RuntimeException error){failed=true;throw error;}finally{busy=false;}
    }
    /** Two-pass import declares every existing live node before linking edges.
     * The graph must already be acyclic; numeric IDs need not be topological. */
    void declare(long node){open();node=canonical(node);try{declareUnchecked(node);}catch(RuntimeException error){failed=true;throw error;}}
    void linkDeclared(long node){
        open();node=canonical(node);busy=true;try{linkUnchecked(node);}catch(RuntimeException error){failed=true;throw error;}finally{busy=false;}
    }
    private void drain(){
        while(pending.size()!=0){
            long node=pending.remove();if(field(node,0)!=0)throw invalid("referenced node scheduled for retirement");
            if((field(node,1)&LINKED)==0)throw invalid("unsealed DAG node scheduled for retirement");
            graph.children(node,releaseChild);graph.retire(node);
            field(node,0,0);field(node,1,0);field(node,2,0);
        }
    }
    long constructionSize(){open();return birthCount;}
    void commitCreated(){commitCreatedSince(0);}
    /** first is a journal offset, not a root handle. Nested publication leaves
     * earlier construction holds intact; strong published roots survive. */
    void commitCreatedSince(long first){
        open();if(first<0||first>birthCount)throw new IllegalArgumentException("invalid construction journal offset");busy=true;
        try{
            for(long at=first;at<birthCount;at++){
                long node=births.get(at*2),generation=births.get(at*2+1);
                births.set(at*2,0);births.set(at*2+1,0);
                if(field(node,2)==generation&&(field(node,1)&BORN)!=0){field(node,1,field(node,1)&~BORN);release(node);drain();}
            }
            birthCount=first;
        }catch(RuntimeException error){failed=true;throw error;}finally{busy=false;}
    }
    void abandonCreated(long node){
        open();node=canonical(node);busy=true;
        try{active(node);if((field(node,1)&BORN)==0)throw invalid("DAG node has no construction hold");field(node,1,field(node,1)&~BORN);release(node);drain();}
        catch(RuntimeException error){failed=true;throw error;}finally{busy=false;}
    }
    /** The caller has already replaced this parent's representation by a leaf
     * with the same meaning. Only its former owned edges are dropped. */
    void replacedByLeaf(long parent,long first,long second){
        open();parent=canonical(parent);node(first);node(second);busy=true;
        try{active(parent);if((field(parent,1)&LINKED)==0)throw invalid("unlinked replacement parent");release(first);release(second);drain();}
        catch(RuntimeException error){failed=true;throw error;}finally{busy=false;}
    }
    private long slot(long token){
        if((token>>>32)!=owner)throw new IllegalArgumentException("foreign DAG root owner");
        long slot=token&ROOT_MASK;if(slot==0||slot>issuedRoots)throw new IllegalArgumentException("unknown DAG root token");return slot;
    }
    private long rootValue(long slot){if(roots.get(slot*2+1)==0)throw new IllegalStateException("expired DAG root");return roots.get(slot*2);}
    long root(long node){
        open();node(node);busy=true;
        try{
            if(issuedRoots==ROOT_MASK)throw invalid("DAG root token space exhausted");retain(node);
            long slot=++issuedRoots;roots.set(slot*2,node);roots.set(slot*2+1,1);return (owner<<32)|slot;
        }catch(RuntimeException error){failed=true;throw error;}finally{busy=false;}
    }
    long value(long token){open();long slot=slot(token);try{return rootValue(slot);}catch(AnalysisResources.Exhausted|PageStore.Failure error){failed=true;throw error;}}
    void bind(long token,long node){
        open();node(node);long slot=slot(token),old;
        try{old=rootValue(slot);}catch(AnalysisResources.Exhausted|PageStore.Failure error){failed=true;throw error;}
        if(old==node)return;busy=true;
        try{retain(node);roots.set(slot*2,node);release(old);drain();}
        catch(RuntimeException error){failed=true;throw error;}finally{busy=false;}
    }
    void closeRoot(long token){
        open();long slot=slot(token);busy=true;
        try{if(roots.get(slot*2+1)==0)return;long old=rootValue(slot);roots.set(slot*2+1,0);roots.set(slot*2,0);release(old);drain();}
        catch(RuntimeException error){failed=true;throw error;}finally{busy=false;}
    }
    long generation(long node){
        open();node=canonical(node);if(node<2)return 0;
        try{return (field(node,1)&LIVE)==0?0:field(node,2);}catch(AnalysisResources.Exhausted|PageStore.Failure error){failed=true;throw error;}
    }
    @Override public void close(){
        if(closed)return;closed=true;
        RuntimeException failure=null;
        failure=ActivationSolver.closeResource(pending,failure);pending=null;
        failure=ActivationSolver.closeResource(births,failure);births=null;
        failure=ActivationSolver.closeResource(roots,failure);roots=null;
        failure=ActivationSolver.closeResource(rows,failure);rows=null;
        controls.close();if(failure!=null)throw failure;
    }
}
