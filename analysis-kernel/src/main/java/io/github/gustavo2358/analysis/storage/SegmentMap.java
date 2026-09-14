package io.github.gustavo2358.analysis.storage;

import java.util.Objects;
import java.util.function.BiConsumer;

/** Immutable sparse ordinal map with path-copying AVL updates; values must be immutable. */
public final class SegmentMap<V> {
    private final Node<V> root;
    public SegmentMap(){this(null);}
    private SegmentMap(Node<V> root){this.root=root;}
    private record Node<V>(int key,V value,Node<V> left,Node<V> right,int height,int size) { }
    private static int height(Node<?> n){return n==null?0:n.height;}
    private static int size(Node<?> n){return n==null?0:n.size;}
    private static <V> Node<V> node(int key,V value,Node<V> left,Node<V> right) {
        return new Node<>(key,value,left,right,Math.incrementExact(Math.max(height(left),height(right))),Math.incrementExact(Math.addExact(size(left),size(right))));
    }
    public int size(){return size(root);}
    public V get(int key){var n=root;while(n!=null){if(n.key==key)return n.value;n=key<n.key?n.left:n.right;}return null;}
    public SegmentMap<V> put(int key,V value) { var next=put(root,key,Objects.requireNonNull(value));return next==root?this:new SegmentMap<>(next); }
    private static <V> Node<V> put(Node<V> n,int key,V value) {
        if(n==null)return node(key,value,null,null);
        if(key==n.key)return Objects.equals(value,n.value)?n:node(key,value,n.left,n.right);
        var left=n.left;var right=n.right;
        if(key<n.key)left=put(left,key,value);else right=put(right,key,value);
        if(left==n.left&&right==n.right)return n;
        var result=node(n.key,n.value,left,right);
        if(height(left)-height(right)>1) {
            if(height(left.left)<height(left.right))result=node(n.key,n.value,rotateLeft(left),right);
            return rotateRight(result);
        }
        if(height(right)-height(left)>1) {
            if(height(right.right)<height(right.left))result=node(n.key,n.value,left,rotateRight(right));
            return rotateLeft(result);
        }
        return result;
    }
    private static <V> Node<V> rotateLeft(Node<V> n){var r=n.right;return node(r.key,r.value,node(n.key,n.value,n.left,r.left),r.right);}
    private static <V> Node<V> rotateRight(Node<V> n){var l=n.left;return node(l.key,l.value,l.left,node(n.key,n.value,l.right,n.right));}
    public void forEach(BiConsumer<Integer,V> visitor){each(root,visitor);}
    private static <V> void each(Node<V> n,BiConsumer<Integer,V> visitor){if(n==null)return;each(n.left,visitor);visitor.accept(n.key,n.value);each(n.right,visitor);}
}
