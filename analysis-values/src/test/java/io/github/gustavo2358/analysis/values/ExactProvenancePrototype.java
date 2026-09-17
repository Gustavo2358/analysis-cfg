package io.github.gustavo2358.analysis.values;

import io.github.gustavo2358.analysis.storage.StorageRange;
import java.math.BigInteger;
import java.util.*;
import java.util.function.UnaryOperator;

/** TEST ONLY. Bounded disjunctive edge encoding, scoped to one concrete interner.
 * A node retains no concrete root. Codec must be lossless for Bytes labels and
 * must return null for other Content variants. Recursive prototype: not a production stack policy.
 */
final class ExactProvenancePrototype<T> {
    interface Codec<T> { ByteImage image(T label); T label(ByteImage image); }
    record Shape(BigInteger extent,String reason) {
        ByteImage image(int producer){return ByteImage.unknown(Optional.of(extent),reason,producer);}
    }
    record Edge<T>(T concrete,Shape shape,Set<Integer> disjunctiveEvents,Node<T> child) {
        Edge {disjunctiveEvents=Set.copyOf(disjunctiveEvents);Objects.requireNonNull(child);}
    }
    static final class Node<T> {
        final int level;final Set<Edge<T>> edges;
        private Node(int level,Set<Edge<T>> edges){this.level=level;this.edges=Set.copyOf(edges);}
        boolean terminal(){return level==Integer.MAX_VALUE;}
    }
    record Size(long nodes,long structuralEdges,long provenanceRows,long expandedEdges) { }
    private record Key<T>(int level,Set<Edge<T>> edges) { }
    private record Group<T>(T concrete,Shape shape,Node<T> child) { }
    private final FactorizedAlternatives<T> concrete;
    private final Codec<T> codec;
    private final Map<Key<T>,Node<T>> interned=new HashMap<>();
    final Node<T> terminal=new Node<>(Integer.MAX_VALUE,Set.of());
    long fallbackCalls,expandedLabels;
    ExactProvenancePrototype(FactorizedAlternatives<T> concrete,Codec<T> codec){this.concrete=concrete;this.codec=codec;}
    static Shape shape(ByteImage image) {
        if(image==null||image.parts().size()!=1||image.extent().isEmpty()||image.extent().get().signum()<=0)return null;
        var p=image.parts().getFirst();
        if(p.producer()<0||p.payload().isPresent()||p.payloadOffset()!=0||p.producerOffset().signum()!=0
                ||!p.range().equals(StorageRange.exact(BigInteger.ZERO,image.extent().get()))
                ||p.reasons().size()!=1||!p.capturedOffsets().isEmpty()||!p.coInitial().isEmpty()
                ||!p.sourceGaps().isEmpty()||!p.logicalSupports().isEmpty())return null;
        return new Shape(image.extent().get(),p.reasons().iterator().next());
    }
    static boolean canFactor(ByteImage a,ByteImage b,int levelA,int levelB,Object childA,Object childB) {
        var shape=shape(a);return shape!=null&&shape.equals(shape(b))&&levelA==levelB&&childA==childB;
    }
    private Edge<T> edge(T label,Node<T> child) {
        var image=codec.image(label);var shape=shape(image);
        return shape==null?new Edge<>(label,null,Set.of(),child):new Edge<>(null,shape,Set.of(image.parts().getFirst().producer()),child);
    }
    private Node<T> node(int level,Collection<Edge<T>> input) {
        var groups=new HashMap<Group<T>,Set<Integer>>();
        for(var edge:input) {
            if(edge.child().level<=level)throw new IllegalArgumentException("unordered factor");
            var key=new Group<>(edge.concrete(),edge.shape(),edge.child());
            groups.computeIfAbsent(key,ignored->new HashSet<>()).addAll(edge.disjunctiveEvents());
        }
        var edges=new HashSet<Edge<T>>();
        groups.forEach((key,events)->edges.add(new Edge<>(key.concrete(),key.shape(),events,key.child())));
        if(edges.isEmpty())return null;
        var key=new Key<>(level,Set.copyOf(edges));return interned.computeIfAbsent(key,k->new Node<>(level,k.edges()));
    }
    Node<T> factor(FactorizedAlternatives.Node<T> root){return factor(root,new IdentityHashMap<>());}
    private Node<T> factor(FactorizedAlternatives.Node<T> root,Map<FactorizedAlternatives.Node<T>,Node<T>> memo) {
        if(root==null)return null;if(root.terminal())return terminal;
        if(memo.containsKey(root))return memo.get(root);
        var edges=new ArrayList<Edge<T>>();root.edges.forEach((label,child)->edges.add(edge(label,factor(child,memo))));
        var result=node(root.level,edges);memo.put(root,result);return result;
    }
    FactorizedAlternatives.Node<T> expand(Node<T> root){return expand(root,new IdentityHashMap<>());}
    private FactorizedAlternatives.Node<T> expand(Node<T> root,Map<Node<T>,FactorizedAlternatives.Node<T>> memo) {
        if(root==null)return null;if(root.terminal())return concrete.terminal;
        if(memo.containsKey(root))return memo.get(root);
        var edges=new HashMap<T,FactorizedAlternatives.Node<T>>();
        for(var edge:root.edges) {
            var child=expand(edge.child(),memo);
            if(edge.shape()==null){edges.put(edge.concrete(),child);expandedLabels++;}
            else for(int event:edge.disjunctiveEvents()){edges.put(codec.label(edge.shape().image(event)),child);expandedLabels++;}
        }
        var result=concrete.node(root.level,edges);memo.put(root,result);return result;
    }
    private boolean overlaps(Edge<T> a,Edge<T> b) {
        if(a.shape()==null||b.shape()==null)return a.shape()==null&&b.shape()==null&&a.concrete().equals(b.concrete());
        return a.shape().equals(b.shape())&&!Collections.disjoint(a.disjunctiveEvents(),b.disjunctiveEvents());
    }
    Node<T> union(Node<T> a,Node<T> b) {
        if(a==b||b==null)return a;if(a==null)return b;
        if(a.level!=b.level)throw new IllegalArgumentException("different dimensions");
        for(var x:a.edges)for(var y:b.edges)if(x.child()!=y.child()&&overlaps(x,y)) {
            fallbackCalls++;return factor(concrete.union(expand(a),expand(b)));
        }
        var edges=new ArrayList<>(a.edges);edges.addAll(b.edges);return node(a.level,edges);
    }
    Node<T> project(Node<T> root,Set<Integer> selected){return project(root,selected,new IdentityHashMap<>());}
    private Node<T> project(Node<T> root,Set<Integer> selected,Map<Node<T>,Node<T>> memo) {
        if(root==null||root.terminal())return root;if(memo.containsKey(root))return memo.get(root);
        var edges=new ArrayList<Edge<T>>();Node<T> result=null;
        for(var edge:root.edges) {
            var child=project(edge.child(),selected,memo);
            if(selected.contains(root.level)) {if(child!=null)edges.add(new Edge<>(edge.concrete(),edge.shape(),edge.disjunctiveEvents(),child));}
            else result=union(result,child);
        }
        if(selected.contains(root.level))result=node(root.level,edges);memo.put(root,result);return result;
    }
    Node<T> restrict(Node<T> root,Map<Integer,T> selected){return restrict(root,selected,new IdentityHashMap<>());}
    private Node<T> restrict(Node<T> root,Map<Integer,T> selected,Map<Node<T>,Node<T>> memo) {
        if(root==null||root.terminal())return root;if(memo.containsKey(root))return memo.get(root);
        var edges=new ArrayList<Edge<T>>();var wanted=selected.get(root.level);
        for(var edge:root.edges) {
            Set<Integer> events=edge.disjunctiveEvents();
            if(wanted!=null) {
                if(edge.shape()==null){if(!edge.concrete().equals(wanted))continue;}
                else {
                    var image=codec.image(wanted);if(!edge.shape().equals(shape(image)))continue;
                    int event=image.parts().getFirst().producer();if(!events.contains(event))continue;events=Set.of(event);
                }
            }
            var child=restrict(edge.child(),selected,memo);
            if(child!=null)edges.add(new Edge<>(edge.concrete(),edge.shape(),events,child));
        }
        var result=node(root.level,edges);memo.put(root,result);return result;
    }
    /** Exact local-component fallback; never distribute independent producer sets over Parts. */
    Node<T> update(Node<T> root,Map<Integer,UnaryOperator<T>> updates) {
        if(updates.isEmpty())return root;
        fallbackCalls++;return factor(concrete.update(expand(root),updates));
    }
    Size size(Collection<Node<T>> roots) {
        var visited=Collections.newSetFromMap(new IdentityHashMap<Node<T>,Boolean>());var pending=new ArrayDeque<Node<T>>();
        roots.forEach(r->{if(r!=null)pending.add(r);});long edges=0,rows=0,expanded=0;
        while(!pending.isEmpty()) {
            var n=pending.remove();if(n.terminal()||!visited.add(n))continue;
            edges+=n.edges.size();for(var e:n.edges){rows+=e.disjunctiveEvents().size();expanded+=e.shape()==null?1:e.disjunctiveEvents().size();pending.add(e.child());}
        }
        return new Size(visited.size(),edges,rows,expanded);
    }
    long internedStructuralEdges(){return interned.values().stream().mapToLong(n->n.edges.size()).sum();}
}
