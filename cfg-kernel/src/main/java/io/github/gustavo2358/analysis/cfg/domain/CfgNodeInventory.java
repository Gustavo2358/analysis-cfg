package io.github.gustavo2358.analysis.cfg.domain;

import java.util.AbstractList;
import java.util.List;
import java.util.Objects;
import java.util.RandomAccess;
import java.util.function.IntFunction;
import java.util.function.IntUnaryOperator;

/** Immutable borrowed node/role views. Descriptors and ordinal indexes belong to the program owner. */
public final class CfgNodeInventory extends AbstractList<CfgNode> implements RandomAccess {
    private final int count;
    private final IntFunction<CfgNode> read;
    private final Runnable owner;
    private final List<CfgNode.EntryNode> entries;
    private final List<CfgNode.NormalExit> normalExits;
    private final List<CfgNode.HaltExit> haltExits;

    public CfgNodeInventory(int count,IntFunction<CfgNode> read,Runnable owner,
            int entryCount,IntUnaryOperator entryOrdinals,int normalCount,IntUnaryOperator normalOrdinals,
            int haltCount,IntUnaryOperator haltOrdinals) {
        if(count<0)throw new IllegalArgumentException("negative CFG node count");
        this.count=count;this.read=Objects.requireNonNull(read);this.owner=Objects.requireNonNull(owner);
        entries=new Roles<>(entryCount,entryOrdinals,CfgNode.EntryNode.class);
        normalExits=new Roles<>(normalCount,normalOrdinals,CfgNode.NormalExit.class);
        haltExits=new Roles<>(haltCount,haltOrdinals,CfgNode.HaltExit.class);
        owner.run();
    }
    @Override public int size(){owner.run();return count;}
    @Override public CfgNode get(int index){owner.run();Objects.checkIndex(index,count);return Objects.requireNonNull(read.apply(index));}
    @Override public void clear(){throw new UnsupportedOperationException("immutable CFG node inventory");}
    public List<CfgNode.EntryNode> entries(){owner.run();return entries;}
    public List<CfgNode.NormalExit> normalExits(){owner.run();return normalExits;}
    public List<CfgNode.HaltExit> haltExits(){owner.run();return haltExits;}

    private final class Roles<T extends CfgNode> extends AbstractList<T> implements RandomAccess {
        private final int size;
        private final IntUnaryOperator ordinals;
        private final Class<T> type;
        private Roles(int size,IntUnaryOperator ordinals,Class<T> type) {
            if(size<0)throw new IllegalArgumentException("negative CFG role count");
            this.size=size;this.ordinals=Objects.requireNonNull(ordinals);this.type=type;
        }
        @Override public int size(){owner.run();return size;}
        @Override public T get(int index){
            owner.run();Objects.checkIndex(index,size);int ordinal=ordinals.applyAsInt(index);
            if(ordinal<0||ordinal>=count)throw new IllegalArgumentException("CFG role ordinal outside node inventory");
            var node=CfgNodeInventory.this.get(ordinal);
            if(!type.isInstance(node))throw new IllegalArgumentException("CFG role ordinal has another node kind");
            return type.cast(node);
        }
        @Override public void clear(){throw new UnsupportedOperationException("immutable CFG role inventory");}
    }
}
