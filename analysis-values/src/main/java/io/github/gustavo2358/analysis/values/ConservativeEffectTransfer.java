package io.github.gustavo2358.analysis.values;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import java.util.*;
import io.github.gustavo2358.analysis.storage.KillAuthority;

/** Generic mandatory/possible writes from AIR, using the unchanged candidate lattice. */
final class ConservativeEffectTransfer {
    private final List<TextProfile.Location> may, must;
    private final Map<TextProfile.Location,KillAuthority.Permit> authority=new HashMap<>();
    private ConservativeEffectTransfer(Set<TextProfile.Location> may,Set<TextProfile.Location> must) {this.may=may.stream().sorted(Comparator.comparingInt(TextProfile.Location::ordinal)).toList();this.must=must.stream().sorted(Comparator.comparingInt(TextProfile.Location::ordinal)).toList();}
    static ConservativeEffectTransfer prepare(Operation operation,TextProfile profile) {
        var may=new HashSet<TextProfile.Location>();var must=new HashSet<TextProfile.Location>();
        if(operation instanceof Operations.HavocMust h)must.add(exact(h.destination(),profile));
        else if(operation instanceof Operations.HavocMay h)select(h.scope(),profile,may);
        else {
            var m=((Operations.Opaque)operation).envelope().memory();
            if(m.otherWrites() instanceof Scopes.WithinMemory w)select(w.scope(),profile,may);
            for(var id:m.knownWrites())may.add(occurrence(id,profile));
            for(var id:m.mustOverwrite())must.add(occurrence(id,profile));
        }
        may.removeIf(l->!profile.selected(l));must.removeIf(l->!profile.selected(l));
        var result=new ConservativeEffectTransfer(may,must);
        for(var location:must)result.authority.put(location,KillAuthority.exactCell(profile.session,operation,location.cell()).orElseThrow(()->new TextProfile.Refusal(false,"UNPROVED_STRONG_OVERWRITE")));
        return result;
    }
    private static TextProfile.Location occurrence(OperandId id,TextProfile p) {
        var object=p.session.index().referencedObject(id);
        if(object==null||!p.subjects.containsKey(object.id()))throw new TextProfile.Refusal(false,"UNSUPPORTED_EFFECT_PLACE");
        return p.subjects.get(object.id());
    }
    private static TextProfile.Location exact(Place place,TextProfile p) {
        if(!(place instanceof Places.ObjectPlace o)||!p.subjects.containsKey(o.object()))throw new TextProfile.Refusal(false,"UNSUPPORTED_EFFECT_PLACE");
        return p.subjects.get(o.object());
    }
    private static void select(Scopes.MemoryScope scope,TextProfile p,Set<TextProfile.Location> out) {
        // Unmodeled declarations may alias a demanded Cell. Never interpret their
        // absence from the scalar table as proof that an effect is disjoint.
        var pending=new ArrayDeque<Scopes.MemoryScope>();pending.add(scope);
        while(!pending.isEmpty()) {
            var current=pending.removeFirst();
            if(current instanceof Scopes.ObjectsMemory objects) {
                if(objects.objects().stream().anyMatch(o->!p.subjects.containsKey(o)))throw new TextProfile.Refusal(false,"UNSUPPORTED_EFFECT_PLACE");
            } else if(current instanceof Scopes.VisibleMemory visible) {
                var unit=p.session.index().unit(visible.unit());
                for(var owner:p.session.index().publication().units())for(var object:owner.objects()) {
                    boolean included=owner.id().equals(visible.unit())||unit.visibleObjects().contains(object.id())
                        ||visible.includingExternal()&&object.visibility()!=Memory.Visibility.PRIVATE;
                    if(included&&!p.subjects.containsKey(object.id()))throw new TextProfile.Refusal(false,"UNSUPPORTED_EFFECT_PLACE");
                }
            } else if(current instanceof Scopes.MemoryUnion union)pending.addAll(union.members());
        }
        for(var item:p.subjects.entrySet())if(p.selected(item.getValue())&&contains(scope,item.getKey(),item.getValue(),p))out.add(item.getValue());
    }
    private static boolean contains(Scopes.MemoryScope s,ObjectId id,TextProfile.Location l,TextProfile p) {
        if(s instanceof Scopes.AllMemory)return true;
        if(s instanceof Scopes.ObjectsMemory o)return o.objects().stream().anyMatch(x->Objects.equals(p.subjects.get(x),l));
        if(s instanceof Scopes.StorageMemory m)return m.storage().contains(l.cell().header().id());
        if(s instanceof Scopes.VisibleMemory v)return id.unit().equals(v.unit())||p.session.index().unit(v.unit()).visibleObjects().contains(id)
            ||v.includingExternal()&&l.cell().header().visibility()!=Memory.Visibility.PRIVATE;
        return ((Scopes.MemoryUnion)s).members().stream().anyMatch(m->contains(m,id,l,p));
    }
    PossibleValuesState apply(PossibleValuesState state,ValuesWork work) {
        if(!state.isReached())return state;var result=state;
        for(var l:may)result=result.widenUnknown(l.ordinal(),work);
        for(var l:must)result=result.strongOverwrite(l.ordinal(),Candidates.UNKNOWN,authority.get(l),work);
        return result;
    }
}
