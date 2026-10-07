package io.github.gustavo2358.analysis.solver;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;

/** Exact rooted connectivity for a mutable, finite directed multigraph.
 * A proof forest makes queries constant-time; deletions repair only its affected subtree.
 * This graph carries no guard predicates and cannot establish guarded feasibility.
 */
final class CallerConnectivity {
    private long edgesRead;
    long edgesRead(){return edgesRead;}
    static final class Node {
        private final boolean root;
        private boolean reached;
        private Node parent;
        private final Map<Node,Integer> incoming=new IdentityHashMap<>(),outgoing=new IdentityHashMap<>();
        private final Map<Node,Boolean> children=new IdentityHashMap<>();
        private Node(boolean root){this.root=root;reached=root;}
        boolean reached(){return reached;}
    }
    Node node(boolean root){return new Node(root);}
    void add(Node from,Node to) {
        edgesRead++;from.outgoing.merge(to,1,Integer::sum);to.incoming.merge(from,1,Integer::sum);
        if(from.reached&&!to.reached){attach(from,to);var queue=new ArrayDeque<Node>();queue.add(to);expand(queue);}
    }
    void remove(Node from,Node to) {
        edgesRead++;Integer count=from.outgoing.get(to);
        if(count==null)throw new IllegalStateException("missing caller link");
        if(count>1){from.outgoing.put(to,count-1);to.incoming.put(from,count-1);return;}
        from.outgoing.remove(to);to.incoming.remove(from);
        if(to.parent!=from)return;
        from.children.remove(to);
        var affected=new ArrayList<Node>();var queue=new ArrayDeque<Node>();queue.add(to);
        while(!queue.isEmpty()) {
            var node=queue.removeFirst();affected.add(node);node.reached=false;node.parent=null;
            edgesRead+=node.children.size();queue.addAll(node.children.keySet());node.children.clear();
        }
        for(var node:affected) {
            if(node.reached)continue;
            for(var predecessor:node.incoming.keySet()) {
                edgesRead++;if(!predecessor.reached)continue;
                attach(predecessor,node);queue.add(node);break;
            }
        }
        expand(queue);
    }
    private static void attach(Node from,Node to) {
        if(to.root||to.reached)return;
        to.reached=true;to.parent=from;from.children.put(to,Boolean.TRUE);
    }
    private void expand(ArrayDeque<Node> queue) {
        while(!queue.isEmpty()) {
            var from=queue.removeFirst();
            for(var to:from.outgoing.keySet()){edgesRead++;if(!to.reached){attach(from,to);queue.addLast(to);}}
        }
    }
}
