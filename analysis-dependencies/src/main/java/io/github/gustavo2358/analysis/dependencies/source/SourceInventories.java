package io.github.gustavo2358.analysis.dependencies.source;

import java.util.*;
import static io.github.gustavo2358.analysis.dependencies.source.QualifiedSourceDependencies.*;

/** Lossless unit-owned immutable tuple columns. No context or proof alternative is merged. */
public final class SourceInventories {
    private SourceInventories() { }
    private static final int INLINE_ROWS=4096;
    public record Snapshot(List<Node> nodes,List<Derivation> derivations) {
        public Snapshot {nodes=copyNodes(nodes);derivations=copyDerivations(derivations);}
    }
    static List<Node> copyNodes(List<Node> nodes){return nodes instanceof Nodes?nodes:List.copyOf(nodes);}
    static List<Derivation> copyDerivations(List<Derivation> steps){return steps instanceof Steps?steps:List.copyOf(steps);}
    public static final class Builder {
        private final Pool<String> strings=new Pool<>();
        private final Pool<Support> supports=new Pool<>();
        private final Pool<List<String>> proofs=new Pool<>();
        private final Rows nodes=new Rows(4),steps=new Rows(7);
        private boolean frozen;
        public int nodeCount(){return nodes.count;}
        public int derivationCount(){return steps.count;}
        private void writable(){if(frozen)throw new IllegalStateException("source inventory already frozen");}
        public void addNode(Node node) {
            writable();Objects.requireNonNull(node);
            nodes.add4(strings.id(node.id()),strings.id(node.context()),strings.id(node.location()),supports.id(node.support()));
        }
        public void addDerivation(Derivation step) {
            writable();Objects.requireNonNull(step);
            steps.add7(strings.id(step.id()),optional(step.source()),strings.id(step.destination()),optional(step.callerPremise()),strings.id(step.authority()),proofs.id(step.proofs()),optional(step.selection()));
        }
        private int optional(List<String> values){return values.isEmpty()?-1:strings.id(values.getFirst());}
        public Snapshot build() {
            writable();frozen=true;
            var text=strings.freeze().toArray(String[]::new);var support=supports.freeze().toArray(Support[]::new);var proof=proofs.freeze();
            var nodeRows=new Nodes(text,support,nodes.freeze());var stepRows=new Steps(text,proof,steps.freeze());
            // Expand bounded small snapshots once, preserving canonical dictionary payloads.
            // Large snapshots retain only columns, never a cache of expanded historical rows.
            if((long)nodeRows.size()+stepRows.size()<=INLINE_ROWS)
                return new Snapshot(List.copyOf(nodeRows),List.copyOf(stepRows));
            return new Snapshot(nodeRows,stepRows);
        }
    }
    private static final class Pool<T> {
        private Map<T,Integer> ordinals=new HashMap<>();
        private List<T> values=new ArrayList<>();
        int id(T value){var known=ordinals.get(value);if(known!=null)return known;int result=values.size();values.add(value);ordinals.put(value,result);return result;}
        List<T> freeze(){var result=List.copyOf(values);ordinals=null;values=null;return result;}
    }
    private static final class Rows {
        private final int width;private int[] data;private int count;
        Rows(int width){this.width=width;data=new int[width*16];}
        private int reserve(int expectedWidth) {
            if(expectedWidth!=width)throw new IllegalArgumentException("tuple width");
            int end=Math.multiplyExact(Math.addExact(count,1),width);
            if(end>data.length)data=Arrays.copyOf(data,Math.max(end,Math.multiplyExact(data.length,2)));
            return count++*width;
        }
        void add4(int a,int b,int c,int d){int at=reserve(4);data[at]=a;data[at+1]=b;data[at+2]=c;data[at+3]=d;}
        void add7(int a,int b,int c,int d,int e,int f,int g){int at=reserve(7);data[at]=a;data[at+1]=b;data[at+2]=c;data[at+3]=d;data[at+4]=e;data[at+5]=f;data[at+6]=g;}
        int[] freeze(){var result=Arrays.copyOf(data,Math.multiplyExact(count,width));data=null;return result;}
    }
    private static final class Nodes extends AbstractList<Node> implements RandomAccess {
        private final String[] text;private final Support[] supports;private final int[] rows;
        Nodes(String[] text,Support[] supports,int[] rows){this.text=text;this.supports=supports;this.rows=rows;}
        @Override public int size(){return rows.length/4;}
        @Override public Node get(int index){int at=Objects.checkIndex(index,size())*4;return new Node(text[rows[at]],text[rows[at+1]],text[rows[at+2]],supports[rows[at+3]]);}
    }
    private static final class Steps extends AbstractList<Derivation> implements RandomAccess {
        private final String[] text;private final List<List<String>> proofs;private final int[] rows;
        Steps(String[] text,List<List<String>> proofs,int[] rows){this.text=text;this.proofs=proofs;this.rows=rows;}
        @Override public int size(){return rows.length/7;}
        private List<String> optional(int ordinal){return ordinal<0?List.of():List.of(text[ordinal]);}
        @Override public Derivation get(int index){int at=Objects.checkIndex(index,size())*7;return new Derivation(text[rows[at]],optional(rows[at+1]),text[rows[at+2]],optional(rows[at+3]),text[rows[at+4]],proofs.get(rows[at+5]),optional(rows[at+6]));}
    }
}
