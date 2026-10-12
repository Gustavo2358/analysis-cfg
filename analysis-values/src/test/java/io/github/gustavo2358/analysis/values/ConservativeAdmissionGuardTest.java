package io.github.gustavo2358.analysis.values;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.structure.AnalysisSession;
import java.util.*;
import org.junit.jupiter.api.Test;
import static io.github.gustavo2358.analysis.values.ValuesFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

/** Explicit scalar eligibility guards for conservative scopes, not MAY/MUST facts. */
final class ConservativeAdmissionGuardTest {
    private record Fixture(Publication publication,AnalysisSession session,Map<ObjectId,TextProfile.Location> subjects) { }
    private static Fixture fixture() {
        var p=graph(new String[]{null},new int[][]{{}},3,true,false);var u=p.units().getFirst();
        var objects=new ArrayList<>(u.objects());var old=objects.get(1);var bool=Types.known(Types.Builtin.BOOL);
        objects.set(1,new Memory.ObjectDeclaration(old.id(),old.displayName(),bool,old.storage(),old.visibility(),old.origin(),old.coverage(),old.precision()));
        var storage=new ArrayList<>(p.storage());var cell=(Memory.Cell)storage.get(1);storage.set(1,new Memory.Cell(cell.header(),bool));
        p=new Publication(p.id(),p.airVersion(),p.capabilities(),p.artifacts(),List.of(unit(u.id(),u.entries(),u.sequences(),objects)),storage,p.resources(),p.artifactRelations(),p.origins(),p.coverage(),p.uncertainties(),p.premises());
        var subjects=Map.of(objects.get(0).id(),new TextProfile.Location(0,(Memory.Cell)storage.get(0)),objects.get(2).id(),new TextProfile.Location(1,(Memory.Cell)storage.get(2)));
        return new Fixture(p,session(p),subjects);
    }
    @Test void explicitUnsupportedObjectsAreGlobalEvenInsideNestedScopes() {
        var f=fixture();var objects=f.publication().units().getFirst().objects();
        assertDoesNotThrow(()->ScalarConservativeAdmission.scope(new Scopes.ObjectsMemory(List.of(objects.getFirst().id())),f.session(),f.subjects()));
        var bad=new Scopes.MemoryUnion(List.of(new Scopes.AllMemory(f.publication().id(),false),new Scopes.MemoryUnion(List.of(new Scopes.ObjectsMemory(List.of(objects.get(1).id()))))));
        assertEquals("UNSUPPORTED_EFFECT_PLACE",assertThrows(TextProfile.Refusal.class,()->ScalarConservativeAdmission.scope(bad,f.session(),f.subjects())).getMessage());
    }
    @Test void allAndStorageScopesDoNotInventAnExplicitObjectGuard() {
        var f=fixture();var unsupported=f.publication().storage().get(1).header().id();
        assertDoesNotThrow(()->ScalarConservativeAdmission.scope(new Scopes.AllMemory(f.publication().id(),false),f.session(),f.subjects()));
        assertDoesNotThrow(()->ScalarConservativeAdmission.scope(new Scopes.StorageMemory(List.of(unsupported)),f.session(),f.subjects()));
    }
    @Test void visibleScopeAndExactPlaceRequireTheirIncludedStorage() {
        var f=fixture();var unit=f.publication().units().getFirst();
        assertEquals("UNSUPPORTED_EFFECT_PLACE",assertThrows(TextProfile.Refusal.class,
            ()->ScalarConservativeAdmission.scope(new Scopes.VisibleMemory(unit.id(),false),f.session(),f.subjects())).getMessage());
        var h=header(unit.id(),"component-place");
        var good=new Places.ObjectPlace(operand(h.id(),"good",Operand.Role.VALUE_WRITE),unit.objects().getFirst().id());
        assertSame(f.subjects().get(unit.objects().getFirst().id()),ScalarConservativeAdmission.exact(good,f.subjects()));
        var bad=new Places.ObjectPlace(operand(h.id(),"bad",Operand.Role.VALUE_WRITE),unit.objects().get(1).id());
        assertEquals("UNSUPPORTED_EFFECT_PLACE",assertThrows(TextProfile.Refusal.class,()->ScalarConservativeAdmission.exact(bad,f.subjects())).getMessage());
    }
}
