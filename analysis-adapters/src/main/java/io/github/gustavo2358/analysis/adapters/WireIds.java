package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.model.Ids.*;
import java.util.*;

final class WireIds {
    private WireIds() { }
    static Map<String,Object> id(Id id) {
        if(id==null)return null;
        String domain=switch(id) {
            case PublicationId ignored -> "publication";case UnitId ignored -> "unit";case EntryId ignored -> "entry";
            case LabelId ignored -> "label";case ObjectId ignored -> "object";case OperationId ignored -> "operation";
            case StorageId ignored -> "storage";case OriginId ignored -> "origin";case PremiseId ignored -> "premise";
            case OperandId ignored -> "operand";case UncertaintyId ignored -> "uncertainty";
            default -> throw new IllegalArgumentException("ID outside result contract");
        };
        var out=new TreeMap<String,Object>();out.put("domain",domain);out.put("localId",id.localId());
        if(!(id instanceof PublicationId))out.put("publication",id.publication().localId());
        UnitId unit=switch(id) {
            case EntryId v -> v.unit();case LabelId v -> v.unit();case OperationId v -> v.unit();case ObjectId v -> v.unit();
            case OperandId v -> v.owner().unit();default -> null;
        };
        if(unit!=null)out.put("unit",unit.localId());
        if(id instanceof OperandId operand)out.put("owner",switch(operand.owner()) {
            case OperationOwner owner -> id(owner.operation());case EntryOwner owner -> id(owner.entry());
        });
        return Collections.unmodifiableMap(out);
    }
    static final Comparator<Id> ORDER=Comparator.comparing((Id id)->id.publication().localId())
        .thenComparing(id->(String)id(id).get("domain"))
        .thenComparing(id->(String)id(id).getOrDefault("unit",""))
        .thenComparing(id->id instanceof OperandId v ? (v.owner() instanceof OperationOwner o?"operation:"+o.operation().localId():"entry:"+((EntryOwner)v.owner()).entry().localId()) : "")
        .thenComparing(Id::localId);
}
