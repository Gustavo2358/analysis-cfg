package io.github.gustavo2358.analysis.values;

import java.util.function.BiConsumer;

/** Path-copying AVL map. Height is logarithmic in explicit bindings, independent of history. */
final class PersistentBindings {
    private PersistentBindings() { }
    static final class Node {
        final int key,height,size;
        final Candidates value;
        final Node left,right;
        Node(int key,Candidates value,Node left,Node right,ValuesWork w) {
            this.key=key;this.value=value;this.left=left;this.right=right;
            height=Math.incrementExact(Math.max(height(left),height(right)));
            size=Math.incrementExact(Math.addExact(size(left),size(right)));
            w.stateNodes=Math.incrementExact(w.stateNodes);
        }
    }
    static int size(Node n) { return n==null?0:n.size; }
    static int height(Node n) { return n==null?0:n.height; }
    static Candidates get(Node n,int key,ValuesWork w) {
        while(n!=null) {
            w.bindingLookups=Math.incrementExact(w.bindingLookups);
            if(n.key==key)return n.value;
            n=key<n.key?n.left:n.right;
        }
        return null;
    }
    static Node put(Node n,int key,Candidates value,ValuesWork w) {
        w.bindingUpdates=Math.incrementExact(w.bindingUpdates);
        if(n==null)return new Node(key,value,null,null,w);
        if(key==n.key)return n.value.equivalent(value)?n:new Node(key,value,n.left,n.right,w);
        Node left=n.left,right=n.right;
        if(key<n.key)left=put(left,key,value,w);else right=put(right,key,value,w);
        if(left==n.left&&right==n.right)return n;
        Node result=new Node(n.key,n.value,left,right,w);
        if(height(left)-height(right)>1) {
            if(height(left.left)<height(left.right))result=new Node(n.key,n.value,rotateLeft(left,w),right,w);
            return rotateRight(result,w);
        }
        if(height(right)-height(left)>1) {
            if(height(right.right)<height(right.left))result=new Node(n.key,n.value,left,rotateRight(right,w),w);
            return rotateLeft(result,w);
        }
        return result;
    }
    private static Node rotateLeft(Node n,ValuesWork w) {
        Node r=n.right;return new Node(r.key,r.value,new Node(n.key,n.value,n.left,r.left,w),r.right,w);
    }
    private static Node rotateRight(Node n,ValuesWork w) {
        Node l=n.left;return new Node(l.key,l.value,l.left,new Node(n.key,n.value,l.right,n.right,w),w);
    }
    static void each(Node n,BiConsumer<Integer,Candidates> visitor) {
        if(n==null)return;each(n.left,visitor);visitor.accept(n.key,n.value);each(n.right,visitor);
    }
}
