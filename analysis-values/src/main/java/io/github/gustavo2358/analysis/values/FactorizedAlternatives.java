package io.github.gustavo2358.analysis.values;

import java.util.*;
import java.util.function.UnaryOperator;

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
    Node<T> union(Node<T> a,Node<T> b){return union(a,b,new HashMap<>());}
    private Node<T> union(Node<T> a,Node<T> b,Map<Pair<T>,Node<T>> memo) {
        if(a==b||b==null)return a;if(a==null)return b;
        if(a.level!=b.level)throw new IllegalArgumentException("different relation dimensions");
        var key=new Pair<>(a,b);var prior=memo.get(key);if(prior!=null)return prior;unionPairs++;
        var edges=new HashMap<>(a.edges);
        b.edges.forEach((value,next)->edges.put(value,union(edges.get(value),next,memo)));
        var result=node(a.level,edges);memo.put(key,result);return result;
    }
    /** Relational image under local updates. Colliding labels union their suffixes. */
    Node<T> update(Node<T> root,Map<Integer,UnaryOperator<T>> updates) {
        return update(root,updates,new IdentityHashMap<>());
    }
    private Node<T> update(Node<T> root,Map<Integer,UnaryOperator<T>> updates,Map<Node<T>,Node<T>> memo) {
        if(root==null||root.terminal())return root;
        var cached=memo.get(root);if(cached!=null)return cached;
        var edges=new HashMap<T,Node<T>>();var fn=updates.get(root.level);
        root.edges.forEach((value,next)->{
            var changed=fn==null?value:fn.apply(value);var suffix=update(next,updates,memo);
            edges.put(changed,union(edges.get(changed),suffix));
        });
        var result=node(root.level,edges);memo.put(root,result);return result;
    }
    /** Existentially forget unrequested components before enumerating a local read. */
    Node<T> project(Node<T> root,Set<Integer> selected) {
        return project(root,selected,new IdentityHashMap<>());
    }
    private Node<T> project(Node<T> root,Set<Integer> selected,Map<Node<T>,Node<T>> memo) {
        if(root==null||root.terminal())return root;
        var cached=memo.get(root);if(cached!=null)return cached;
        Node<T> result;
        if(selected.contains(root.level)) {
            var edges=new HashMap<T,Node<T>>();root.edges.forEach((v,n)->edges.put(v,project(n,selected,memo)));
            result=node(root.level,edges);
        } else {
            result=null;for(var next:root.edges.values())result=union(result,project(next,selected,memo));
        }
        memo.put(root,result);return result;
    }
    /** Keep matching source edges in the original BEFORE relation, retaining other dimensions. */
    Node<T> restrict(Node<T> root,Map<Integer,T> selected) {
        return restrict(root,selected,new IdentityHashMap<>());
    }
    private Node<T> restrict(Node<T> root,Map<Integer,T> selected,Map<Node<T>,Node<T>> memo) {
        if(root==null||root.terminal())return root;
        if(memo.containsKey(root))return memo.get(root);
        var edges=new HashMap<T,Node<T>>();var chosen=selected.get(root.level);
        root.edges.forEach((v,n)->{if(chosen==null||chosen.equals(v))edges.put(v,restrict(n,selected,memo));});
        var result=node(root.level,edges);memo.put(root,result);return result;
    }
    List<Map<Integer,T>> selections(Node<T> projected) {
        var result=new ArrayList<Map<Integer,T>>();selections(projected,new HashMap<>(),result);projectedAlternatives+=result.size();return List.copyOf(result);
    }
    private void selections(Node<T> node,Map<Integer,T> path,List<Map<Integer,T>> out) {
        if(node==null)return;if(node.terminal()){out.add(Map.copyOf(path));return;}
        node.edges.forEach((value,next)->{path.put(node.level,value);selections(next,path,out);});path.remove(node.level);
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
