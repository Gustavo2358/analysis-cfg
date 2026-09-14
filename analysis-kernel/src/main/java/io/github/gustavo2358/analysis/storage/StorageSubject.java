package io.github.gustavo2358.analysis.storage;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import java.util.*;

/** A query subject, never an invented AIR declaration or a proof of physical independence. */
public sealed interface StorageSubject permits StorageSubject.NamedObject,StorageSubject.PhysicalRange {
    record NamedObject(ObjectId object) implements StorageSubject {
        public NamedObject { Objects.requireNonNull(object); }
    }
    record PhysicalRange(StorageId storage,StorageRange range,Memory.Codec codec) implements StorageSubject {
        public PhysicalRange { Objects.requireNonNull(storage);Objects.requireNonNull(range);Objects.requireNonNull(codec); }
    }
    Comparator<Memory.Codec> CODEC_ORDER=(a,b)->compare(codecParts(a),codecParts(b));
    Comparator<StorageSubject> ORDER=(left,right)->{
        if(left instanceof NamedObject a) {
            if(!(right instanceof NamedObject b))return -1;
            return compare(List.of(a.object().publication().localId(),a.object().unit().localId(),a.object().localId()),
                List.of(b.object().publication().localId(),b.object().unit().localId(),b.object().localId()));
        }
        if(right instanceof NamedObject)return 1;
        var a=(PhysicalRange)left;var b=(PhysicalRange)right;
        int result=compare(List.of(a.storage().publication().localId(),a.storage().localId()),List.of(b.storage().publication().localId(),b.storage().localId()));
        if(result!=0)return result;result=a.range().start().compareTo(b.range().start());if(result!=0)return result;
        if(a.range().end().isPresent()!=b.range().end().isPresent())return a.range().end().isPresent()?-1:1;
        if(a.range().end().isPresent()){result=a.range().end().get().compareTo(b.range().end().get());if(result!=0)return result;}
        return CODEC_ORDER.compare(a.codec(),b.codec());
    };
    private static int compare(List<String> a,List<String> b) {
        for(int i=0;i<Math.min(a.size(),b.size());i++){int result=a.get(i).compareTo(b.get(i));if(result!=0)return result;}return Integer.compare(a.size(),b.size());
    }
    private static List<String> codecParts(Memory.Codec codec) {
        var parts=new ArrayList<String>();
        switch(codec) {
            case Memory.IdentityBytes ignored -> parts.add("identity-bytes");
            case Memory.AsciiText ignored -> parts.add("ascii-text");
            case Memory.BinaryCodec c -> parts.addAll(List.of("binary",Boolean.toString(c.signed()),c.width().toString(),c.order().name()));
            case Memory.ExtensionCodec c -> {parts.addAll(List.of("extension",c.name(),c.version()));typeParts(c.logicalType(),parts);}
            case Memory.UnknownCodec c -> {parts.addAll(List.of("unknown",c.reason().publication().localId(),c.reason().localId()));typeParts(c.logicalType(),parts);}
        }
        return parts;
    }
    private static void typeParts(Types.TypeRef type,List<String> parts) {
        if(type instanceof Types.UnknownType u){parts.addAll(List.of("unknown-type",u.uncertainty().publication().localId(),u.uncertainty().localId()));return;}
        switch(((Types.Known)type).type()) {
            case Types.Builtin builtin -> parts.addAll(List.of("builtin",builtin.name()));
            case Types.ExtensionType extension -> parts.addAll(List.of("extension-type",extension.name(),extension.version()));
            case Types.LabelType labels -> {
                parts.addAll(List.of("labels",labels.unit().publication().localId(),labels.unit().localId()));
                for(var label:labels.labels())parts.addAll(List.of(label.publication().localId(),label.unit().localId(),label.localId()));
            }
        }
    }
}
