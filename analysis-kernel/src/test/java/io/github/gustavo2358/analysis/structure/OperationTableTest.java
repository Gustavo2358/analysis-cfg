package io.github.gustavo2358.analysis.structure;

import io.github.gustavo2358.air.model.Ids.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.structure.StructuralFixtures.*;

class OperationTableTest {
    @Test void nativeValuesNeverDecodeKeysAndLookupIncludesTheFullOwner(){
        var publication=linear(1,0,0,1);var unit=publication.units().getFirst();
        var a=new OperationId(unit.id(),"Aa");var b=new OperationId(unit.id(),"BB");
        var other=new OperationId(new UnitId(publication.id(),"other"),"Aa");
        var identities=List.of(a,b,other);int[] decoded={0};boolean[] closed={false};
        var directory=new ProgramStore.OperationDirectory(){
            private void available(){if(closed[0])throw new IllegalStateException("owner closed");}
            @Override public int size(){available();return identities.size();}
            @Override public OperationId identity(int ordinal){available();decoded[0]++;return identities.get(ordinal);}
            @Override public int ordinal(OperationId identity){available();return identities.indexOf(identity);}
            @Override public boolean identityAt(int ordinal,OperationId identity){available();return ordinal>=0&&ordinal<identities.size()&&identities.get(ordinal).equals(identity);}
        };
        var resident=ProgramStore.resident(publication);
        var store=(ProgramStore.Structural)java.lang.reflect.Proxy.newProxyInstance(ProgramStore.class.getClassLoader(),new Class<?>[]{ProgramStore.Structural.class},
            (proxy,method,args)->method.getName().equals("operationDirectory")?Optional.of(directory):method.invoke(resident,args));
        var table=new OperationTable<String>(store);assertEquals(a.hashCode(),b.hashCode());
        table.put(other,"other");table.put(b,"B");table.put(a,"A");
        assertEquals(List.of("A","B","other"),List.copyOf(table.values()));assertEquals(0,decoded[0]);
        assertEquals("A",table.get(a));assertEquals("B",table.get(b));assertEquals("other",table.get(other));
        assertNull(table.get(new OperationId(new UnitId(new PublicationId("foreign"),unit.id().localId()),a.localId())));
        assertEquals(0,decoded[0]);assertEquals(identities,List.copyOf(table.keySet()));assertEquals(3,decoded[0]);
        assertThrows(IllegalArgumentException.class,()->table.put(new OperationId(unit.id(),"absent"),"not admitted"));
        assertEquals("A",table.put(a,null));assertTrue(table.containsKey(a));assertEquals(3,table.size());
        assertEquals(null,table.putIfAbsent(a,"again"));assertEquals("again",table.remove(a));assertEquals(2,table.size());
        var values=table.values();var iterator=values.iterator();assertEquals("B",iterator.next());iterator.remove();assertEquals(1,table.size());
        closed[0]=true;assertThrows(IllegalStateException.class,table::size);assertThrows(IllegalStateException.class,()->table.get(other));
        assertThrows(IllegalStateException.class,values::iterator);assertThrows(IllegalStateException.class,iterator::hasNext);
    }

    @Test void residentCompatibilityRetainsOrdinaryMapSemantics(){
        var publication=linear(1,0,0,1);var id=new OperationId(publication.units().getFirst().id(),"caller-owned");
        var table=new OperationTable<String>(ProgramStore.resident(publication));
        assertNull(table.put(id,"first"));assertEquals("first",table.putIfAbsent(id,"ignored"));
        assertEquals(Map.of(id,"first"),table);assertEquals("first",table.remove(id));assertTrue(table.isEmpty());
        table.put(id,"last");table.clear();assertTrue(table.isEmpty());
    }
}
