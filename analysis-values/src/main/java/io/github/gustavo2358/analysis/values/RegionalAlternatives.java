package io.github.gustavo2358.analysis.values;

import io.github.gustavo2358.analysis.storage.StorageRange;
import java.math.BigInteger;
import java.util.*;
import java.util.function.*;

/** Exact regional edge encoding. One instance owns a fixed ordered location schema.
 * Compact groups are disjunctions under an identical child, never co-initial evidence.
 * Concrete fallback interners/memos are call-local; only compact history is retained.
 * The domain codec must round-trip a concrete ByteImage label exactly; null images
 * identify non-byte labels. ByteImage itself always remains one concrete image.
 */
final class RegionalAlternatives<T> {
    record Shape(BigInteger extent,String reason) {
        ByteImage image(int event){return ByteImage.unknown(Optional.of(extent),reason,event);}
    }
    /** Sorted, unique, immutable primitive event IDs. Hash is never an identity. */
    static final class Events {
        private final int[] values;private final int hash;
        private Events(int[] values){this.values=values;hash=Arrays.hashCode(values);}
        static Events of(int event){if(event<0)throw new IllegalArgumentException("negative event");return new Events(new int[]{event});}
        int size(){return values.length;}
        boolean contains(int event){return Arrays.binarySearch(values,event)>=0;}
        boolean intersects(Events other){for(int value:values)if(other.contains(value))return true;return false;}
        Events union(Events other) {
            if(this==other||equals(other))return this;
            int[] merged=new int[values.length+other.values.length];int a=0,b=0,n=0;
            while(a<values.length||b<other.values.length) {
                int value;
                if(b==other.values.length||a<values.length&&values[a]<other.values[b])value=values[a++];
                else if(a==values.length||other.values[b]<values[a])value=other.values[b++];
                else {value=values[a++];b++;}
                merged[n++]=value;
            }
            if(n==values.length)return this;if(n==other.values.length)return other;
            return new Events(Arrays.copyOf(merged,n));
        }
        @Override public int hashCode(){return hash;}
        @Override public boolean equals(Object other){return this==other||other instanceof Events e&&Arrays.equals(values,e.values);}
    }
    private sealed interface Label<T> permits Concrete,Unknown { }
    private record Concrete<T>(T value) implements Label<T> { }
    private record Unknown<T>(Shape shape,Events events) implements Label<T> { }
    private record Edge<T>(Label<T> label,Node<T> child) { }
    static final class Node<T> {
        final int level;private final Set<Edge<T>> edges;private final Object owner;
        private final long rows,expanded;private final boolean leaf;
        private Node(Object owner,int level,Set<Edge<T>> edges) {
            this.owner=owner;this.level=level;this.edges=Set.copyOf(edges);long rows=0,expanded=0;boolean leaf=true;
            for(var edge:edges){long count=edge.label() instanceof Unknown<T> u?u.events().size():0;rows+=count;expanded+=count==0?1:count;leaf&=edge.child().terminal();}
            this.rows=rows;this.expanded=expanded;this.leaf=leaf;
        }
        boolean terminal(){return level==Integer.MAX_VALUE;}
        int structuralEdges(){return edges.size();}
    }
    record Size(long nodes,long alternatives,long maxComponent,long provenanceRows,long expandedAlternatives) {
        long structuralEdges(){return alternatives;}
        long expandedEdges(){return expandedAlternatives;}
    }
    private record Key<T>(int level,Set<Edge<T>> edges) { }
    private record Group<T>(T concrete,Shape shape,Node<T> child) { }
    private record Visit<N>(N node,boolean ready) { }
    // A root carries only this token, never a back-reference retaining the interner.
    private final Object owner=new Object();
    private final Function<T,ByteImage> images;private final Function<ByteImage,T> labels;
    private final Map<Key<T>,Node<T>> interned=new HashMap<>();
    private final Map<Node<T>,Size> sizes=new IdentityHashMap<>();
    final Node<T> terminal=new Node<>(owner,Integer.MAX_VALUE,Set.of());
    private long internedEdges,internedRows,internedExpanded,unionPairs,projected,fallbacks,expandedLabels;
    RegionalAlternatives(Function<T,ByteImage> images,Function<ByteImage,T> labels){this.images=images;this.labels=labels;}
    private void owned(Node<T> node){if(node!=null&&node.owner!=owner)throw new IllegalArgumentException("foreign regional node");}
    static Shape shape(ByteImage image) {
        if(image==null||image.parts().size()!=1||image.extent().isEmpty()||image.extent().get().signum()<=0)return null;
        var p=image.parts().getFirst();
        if(p.producer()<0||p.payload().isPresent()||p.payloadOffset()!=0||p.producerOffset().signum()!=0
                ||!p.range().equals(StorageRange.exact(BigInteger.ZERO,image.extent().get()))||p.reasons().size()!=1
                ||!p.capturedOffsets().isEmpty()||!p.coInitial().isEmpty()||!p.sourceGaps().isEmpty()||!p.logicalSupports().isEmpty())return null;
        return new Shape(image.extent().get(),p.reasons().iterator().next());
    }
    static boolean canFactor(ByteImage a,ByteImage b,int levelA,int levelB,Object childA,Object childB) {
        var shape=shape(a);return shape!=null&&shape.equals(shape(b))&&levelA==levelB&&childA!=null&&childA==childB;
    }
    private Label<T> label(T value) {
        var image=images.apply(value);var shape=shape(image);
        return shape==null?new Concrete<>(value):new Unknown<>(shape,Events.of(image.parts().getFirst().producer()));
    }
    private Node<T> node(int level,Collection<Edge<T>> input) {
        var groups=new HashMap<Group<T>,Label<T>>();
        for(var edge:input) {
            owned(edge.child());if(edge.child().level<=level)throw new IllegalArgumentException("unordered factor");
            var label=edge.label();
            var group=label instanceof Unknown<T> u?new Group<T>(null,u.shape(),edge.child()):new Group<>(((Concrete<T>)label).value(),null,edge.child());
            groups.merge(group,label,(a,b)->a instanceof Unknown<T> u?new Unknown<>(u.shape(),u.events().union(((Unknown<T>)b).events())):a);
        }
        if(groups.isEmpty())return null;var edges=new HashSet<Edge<T>>();groups.forEach((g,label)->edges.add(new Edge<>(label,g.child())));
        var key=new Key<>(level,Set.copyOf(edges));var known=interned.get(key);if(known!=null)return known;
        var result=new Node<T>(owner,level,key.edges());interned.put(key,result);internedEdges+=result.edges.size();internedRows+=result.rows;internedExpanded+=result.expanded;return result;
    }
    Node<T> node(int level,Map<T,Node<T>> edges) {
        var input=new ArrayList<Edge<T>>();edges.forEach((value,child)->{if(child!=null)input.add(new Edge<>(label(value),child));});return node(level,input);
    }
    Node<T> singleton(NavigableMap<Integer,T> values) {
        var result=terminal;for(var entry:values.descendingMap().entrySet())result=node(entry.getKey(),Map.of(entry.getValue(),result));return result;
    }
    Node<T> factor(FactorizedAlternatives.Node<T> root) {
        if(root==null)return null;if(root.terminal())return terminal;
        var done=new IdentityHashMap<FactorizedAlternatives.Node<T>,Node<T>>();var pending=new ArrayDeque<Visit<FactorizedAlternatives.Node<T>>>();pending.push(new Visit<>(root,false));
        while(!pending.isEmpty()) {
            var visit=pending.pop();var n=visit.node();if(done.containsKey(n))continue;
            if(n.terminal()){done.put(n,terminal);continue;}
            if(!visit.ready()){pending.push(new Visit<>(n,true));for(var child:n.edges.values())if(!done.containsKey(child))pending.push(new Visit<>(child,false));}
            else {var edges=new ArrayList<Edge<T>>();n.edges.forEach((value,child)->edges.add(new Edge<>(label(value),done.get(child))));done.put(n,node(n.level,edges));}
        }
        return done.get(root);
    }
    FactorizedAlternatives.Node<T> expand(Node<T> root,FactorizedAlternatives<T> target) {
        owned(root);if(root==null)return null;if(root.terminal())return target.terminal;
        var done=new IdentityHashMap<Node<T>,FactorizedAlternatives.Node<T>>();var pending=new ArrayDeque<Visit<Node<T>>>();pending.push(new Visit<>(root,false));
        while(!pending.isEmpty()) {
            var visit=pending.pop();var n=visit.node();if(done.containsKey(n))continue;
            if(n.terminal()){done.put(n,target.terminal);continue;}
            if(!visit.ready()){pending.push(new Visit<>(n,true));for(var edge:n.edges)if(!done.containsKey(edge.child()))pending.push(new Visit<>(edge.child(),false));}
            else {
                var edges=new HashMap<T,FactorizedAlternatives.Node<T>>();
                for(var edge:n.edges) {
                    var child=done.get(edge.child());
                    if(edge.label() instanceof Unknown<T> u)for(int event:u.events().values){edges.put(labels.apply(u.shape().image(event)),child);expandedLabels++;}
                    else {edges.put(((Concrete<T>)edge.label()).value(),child);expandedLabels++;}
                }
                done.put(n,target.node(n.level,edges));
            }
        }
        return done.get(root);
    }
    private boolean overlaps(Label<T> a,Label<T> b) {
        if(a instanceof Unknown<T> x)return b instanceof Unknown<T> y&&x.shape().equals(y.shape())&&x.events().intersects(y.events());
        return a.equals(b);
    }
    Node<T> union(Node<T> a,Node<T> b) {
        owned(a);owned(b);if(a==b||b==null)return a;if(a==null)return b;
        if(a.level!=b.level)throw new IllegalArgumentException("different dimensions");unionPairs++;
        // Equal concrete labels under different children require exact child union.
        // Expanding only this relation component preserves unrelated State bindings.
        for(var x:a.edges)for(var y:b.edges)if(x.child()!=y.child()&&overlaps(x.label(),y.label())) {
            fallbacks++;var concrete=new FactorizedAlternatives<T>();return factor(concrete.union(expand(a,concrete),expand(b,concrete)));
        }
        var edges=new ArrayList<>(a.edges);edges.addAll(b.edges);return node(a.level,edges);
    }
    private Node<T> rewrite(Node<T> root,BiFunction<Node<T>,Map<Node<T>,Node<T>>,Node<T>> rebuild) {
        owned(root);if(root==null||root.terminal())return root;
        var done=new IdentityHashMap<Node<T>,Node<T>>();var pending=new ArrayDeque<Visit<Node<T>>>();pending.push(new Visit<>(root,false));
        while(!pending.isEmpty()) {
            var visit=pending.pop();var n=visit.node();if(done.containsKey(n))continue;
            if(n.terminal()){done.put(n,terminal);continue;}
            if(!visit.ready()){pending.push(new Visit<>(n,true));for(var edge:n.edges)if(!done.containsKey(edge.child()))pending.push(new Visit<>(edge.child(),false));}
            else done.put(n,rebuild.apply(n,done));
        }
        return done.get(root);
    }
    Node<T> project(Node<T> root,Set<Integer> selected) {
        owned(root);if(root==null)return null;if(selected.isEmpty())return terminal;
        return rewrite(root,(n,done)->{
            var edges=new ArrayList<Edge<T>>();Node<T> result=null;
            for(var edge:n.edges){var child=done.get(edge.child());if(selected.contains(n.level)){if(child!=null)edges.add(new Edge<>(edge.label(),child));}else result=union(result,child);}
            return selected.contains(n.level)?node(n.level,edges):result;
        });
    }
    Node<T> restrict(Node<T> root,Map<Integer,T> selected) {
        owned(root);if(selected.isEmpty())return root;
        return rewrite(root,(n,done)->{
            var wanted=selected.get(n.level);var requested=wanted==null?null:label(wanted);var edges=new ArrayList<Edge<T>>();
            for(var edge:n.edges) {
                var value=edge.label();
                if(requested!=null){if(!overlaps(value,requested))continue;value=requested;}
                var child=done.get(edge.child());if(child!=null)edges.add(new Edge<>(value,child));
            }
            return node(n.level,edges);
        });
    }
    Node<T> update(Node<T> root,Map<Integer,UnaryOperator<T>> updates) {
        owned(root);if(root==null||root.terminal()||updates.isEmpty())return root;
        fallbacks++;var concrete=new FactorizedAlternatives<T>();return factor(concrete.update(expand(root,concrete),updates));
    }
    /** Constant replacement: only admitted unknown supplies take the native path.
     * At a replaced level, forgetting the old label unions exactly its old children.
     */
    Node<T> overwrite(Node<T> root,Map<Integer,T> supplied) {
        owned(root);if(supplied.isEmpty())return root;
        if(supplied.values().stream().anyMatch(v->shape(images.apply(v))==null)) {
            var updates=new HashMap<Integer,UnaryOperator<T>>();supplied.forEach((level,value)->updates.put(level,ignored->value));return update(root,updates);
        }
        return rewrite(root,(n,done)->{
            var value=supplied.get(n.level);var edges=new ArrayList<Edge<T>>();Node<T> child=null;
            for(var edge:n.edges) {if(value!=null)child=union(child,done.get(edge.child()));else edges.add(new Edge<>(edge.label(),done.get(edge.child())));}
            return value==null?node(n.level,edges):node(n.level,Map.of(value,child));
        });
    }
    List<Map<Integer,T>> selections(Node<T> root) {
        owned(root);if(root==null)return List.of();if(root.terminal()){projected++;return List.of(Map.of());}
        var concrete=new FactorizedAlternatives<T>();var result=concrete.selections(expand(root,concrete));projected+=result.size();return result;
    }
    Size componentSize(Node<T> root) {
        owned(root);if(root==null||root.terminal())return new Size(0,0,0,0,0);
        return sizes.computeIfAbsent(root,n->n.leaf?new Size(1,n.edges.size(),n.edges.size(),n.rows,n.expanded):size(List.of(n)));
    }
    static Size size(Collection<? extends Node<?>> roots) {
        var visited=Collections.newSetFromMap(new IdentityHashMap<Node<?>,Boolean>());var pending=new ArrayDeque<Node<?>>();roots.forEach(n->{if(n!=null)pending.add(n);});
        var widths=new HashMap<Integer,Set<Object>>();long edges=0,rows=0,expanded=0;
        while(!pending.isEmpty()) {
            var n=pending.remove();if(n.terminal()||!visited.add(n))continue;edges+=n.edges.size();rows+=n.rows;expanded+=n.expanded;
            var width=widths.computeIfAbsent(n.level,ignored->new HashSet<>());for(var edge:n.edges){width.add(edge.label());pending.add(edge.child());}
        }
        return new Size(visited.size(),edges,widths.values().stream().mapToLong(Set::size).max().orElse(0),rows,expanded);
    }
    Map<String,Long> metrics() {
        return Map.of("internedNodes",(long)interned.size(),"internedAlternatives",internedEdges,"internedProvenanceRows",internedRows,
            "internedExpandedAlternatives",internedExpanded,"relationUnionPairs",unionPairs,"projectedAlternatives",projected,
            "concreteFallbacks",fallbacks,"expandedLabels",expandedLabels);
    }
}
