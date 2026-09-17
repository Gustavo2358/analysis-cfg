package io.github.gustavo2358.analysis.values;

import java.util.*;
import java.util.function.*;

/** Ordered finite relation with shared suffixes. No complete-world enumeration.
 * Null denotes the empty relation; terminal denotes the single empty tuple.
 * Nodes keep every selected level, including singleton levels.
 */
final class FactorizedAlternatives<T> {
    static final class Node<T> {
        final int level;
        final Map<T,Node<T>> edges;
        private Node(int level,Map<T,Node<T>> edges){this.level=level;this.edges=Map.copyOf(edges);}
        boolean terminal(){return level==Integer.MAX_VALUE;}
    }
    record Size(long nodes,long alternatives,long maxComponent) { }
    private record Key<T>(int level,Map<T,Node<T>> edges) { }
    private record Pair<T>(Node<T> a,Node<T> b) { }
    final Node<T> terminal=new Node<>(Integer.MAX_VALUE,Map.of());
    private final Map<Key<T>,Node<T>> interned=new HashMap<>();
    private long internedEdges,unionPairs,projectedAlternatives;
    private final Map<Node<T>,Size> componentSizes=new IdentityHashMap<>();

    Node<T> node(int level,Map<T,Node<T>> edges) {
        var live=new HashMap<T,Node<T>>();edges.forEach((value,next)->{if(next!=null)live.put(value,next);});
        if(live.isEmpty())return null;
        if(live.values().stream().anyMatch(n->n.level<=level))throw new IllegalArgumentException("unordered factor");
        var key=new Key<>(level,Map.copyOf(live));var known=interned.get(key);if(known!=null)return known;
        var result=new Node<>(level,key.edges());interned.put(key,result);internedEdges+=live.size();return result;
    }
    Node<T> singleton(NavigableMap<Integer,T> values) {
        var result=terminal;
        for(var entry:values.descendingMap().entrySet())result=node(entry.getKey(),Map.of(entry.getValue(),result));
        return result;
    }
    /** Suspended DFS cursor. Continuations live on the heap, never on the Java call stack. */
    private static final class Frame<T> {
        final Node<T> node;
        final Iterator<Map.Entry<T,Node<T>>> remaining;
        Frame(Node<T> node){this.node=node;this.remaining=node.edges.entrySet().iterator();}
    }
    private static final class UnionFrame<T> {
        final Pair<T> pair;
        final Map<T,Node<T>> edges;
        final Iterator<Map.Entry<T,Node<T>>> remaining;
        Map.Entry<T,Node<T>> current;
        UnionFrame(Node<T> a,Node<T> b) {
            if(a.level!=b.level)throw new IllegalArgumentException("different relation dimensions");
            pair=new Pair<>(a,b);edges=new HashMap<>(a.edges);remaining=b.edges.entrySet().iterator();
        }
    }
    Node<T> union(Node<T> a,Node<T> b) {
        if(a==b||b==null)return a;if(a==null)return b;
        var memo=new HashMap<Pair<T>,Node<T>>();var pending=new ArrayDeque<UnionFrame<T>>();
        var first=new UnionFrame<>(a,b);pending.push(first);unionPairs++;
        while(!pending.isEmpty()) {
            var frame=pending.peek();
            if(frame.current==null&&frame.remaining.hasNext())frame.current=frame.remaining.next();
            if(frame.current==null) {
                memo.put(frame.pair,node(frame.pair.a().level,frame.edges));pending.pop();continue;
            }
            var left=frame.edges.get(frame.current.getKey());var right=frame.current.getValue();Node<T> joined;
            if(left==right||right==null)joined=left;
            else if(left==null)joined=right;
            else {
                joined=memo.get(new Pair<>(left,right));
                if(joined==null) {
                    pending.push(new UnionFrame<>(left,right));unionPairs++;continue;
                }
            }
            frame.edges.put(frame.current.getKey(),joined);frame.current=null;
        }
        return memo.get(first.pair);
    }
    /** Memoized post-order image. Prune rejected edges before visiting their suffixes.
     * A null image is memoized too (restriction can empty a shared suffix).
     */
    private Node<T> rewrite(Node<T> root,BiPredicate<Node<T>,T> include,
            BiFunction<Node<T>,Map<Node<T>,Node<T>>,Node<T>> rebuild) {
        if(root==null||root.terminal())return root;
        var done=new IdentityHashMap<Node<T>,Node<T>>();var pending=new ArrayDeque<Frame<T>>();
        pending.push(new Frame<>(root));
        while(!pending.isEmpty()) {
            var frame=pending.peek();
            if(frame.remaining.hasNext()) {
                var edge=frame.remaining.next();if(!include.test(frame.node,edge.getKey()))continue;
                var child=edge.getValue();
                if(child.terminal())done.put(child,child);
                else if(!done.containsKey(child))pending.push(new Frame<>(child));
            } else {
                done.put(frame.node,rebuild.apply(frame.node,done));pending.pop();
            }
        }
        return done.get(root);
    }
    /** Relational image under local updates. Colliding labels union their suffixes. */
    Node<T> update(Node<T> root,Map<Integer,UnaryOperator<T>> updates) {
        return rewrite(root,(n,v)->true,(n,done)->{
            var edges=new HashMap<T,Node<T>>();var fn=updates.get(n.level);
            n.edges.forEach((value,next)->{
                var changed=fn==null?value:fn.apply(value);
                edges.put(changed,union(edges.get(changed),done.get(next)));
            });
            return node(n.level,edges);
        });
    }
    /** Existentially forget unrequested components before enumerating a local read. */
    Node<T> project(Node<T> root,Set<Integer> selected) {
        return rewrite(root,(n,v)->true,(n,done)->{
            if(selected.contains(n.level)) {
                var edges=new HashMap<T,Node<T>>();n.edges.forEach((v,next)->edges.put(v,done.get(next)));
                return node(n.level,edges);
            }
            Node<T> result=null;for(var next:n.edges.values())result=union(result,done.get(next));
            return result;
        });
    }
    /** Keep matching source edges in the original BEFORE relation, retaining other dimensions. */
    Node<T> restrict(Node<T> root,Map<Integer,T> selected) {
        BiPredicate<Node<T>,T> matches=(n,v)->selected.get(n.level)==null||selected.get(n.level).equals(v);
        return rewrite(root,matches,(n,done)->{
            var edges=new HashMap<T,Node<T>>();
            n.edges.forEach((v,next)->{if(matches.test(n,v))edges.put(v,done.get(next));});
            return node(n.level,edges);
        });
    }
    List<Map<Integer,T>> selections(Node<T> projected) {
        var result=new ArrayList<Map<Integer,T>>();var path=new HashMap<Integer,T>();
        var pending=new ArrayDeque<Frame<T>>();
        if(projected!=null) {
            if(projected.terminal())result.add(Map.of());else pending.push(new Frame<>(projected));
        }
        while(!pending.isEmpty()) {
            var frame=pending.peek();
            if(!frame.remaining.hasNext()){path.remove(frame.node.level);pending.pop();continue;}
            var edge=frame.remaining.next();path.put(frame.node.level,edge.getKey());
            if(edge.getValue().terminal())result.add(Map.copyOf(path));
            else pending.push(new Frame<>(edge.getValue()));
        }
        projectedAlternatives+=result.size();return List.copyOf(result);
    }
    /** Exact immutable-root summary, scoped to this interner's existing node lifetime.
     * Single-level groups need no label hashing or DAG traversal. Other groups are
     * traversed once per distinct queried root, preserving shared-suffix counting.
     */
    Size componentSize(Node<T> root) {
        if(root==null||root.terminal())return new Size(0,0,0);
        return componentSizes.computeIfAbsent(root,node->{
            if(node.edges.values().stream().allMatch(Node::terminal))
                return new Size(1,node.edges.size(),node.edges.size());
            return size(List.of(node));
        });
    }
    static Size size(Collection<? extends Node<?>> roots) {
        var visited=Collections.newSetFromMap(new IdentityHashMap<Node<?>,Boolean>());var pending=new ArrayDeque<Node<?>>();
        roots.forEach(n->{if(n!=null)pending.add(n);});long edges=0;var components=new HashMap<Integer,Set<Object>>();
        while(!pending.isEmpty()) {
            var node=pending.removeFirst();if(node.terminal()||!visited.add(node))continue;
            edges+=node.edges.size();components.computeIfAbsent(node.level,ignored->new HashSet<>()).addAll(node.edges.keySet());pending.addAll(node.edges.values());
        }
        return new Size(visited.size(),edges,components.values().stream().mapToLong(Set::size).max().orElse(0));
    }
    Map<String,Long> metrics(){return Map.of("internedNodes",(long)interned.size(),"internedAlternatives",internedEdges,"relationUnionPairs",unionPairs,"projectedAlternatives",projectedAlternatives);}
}
