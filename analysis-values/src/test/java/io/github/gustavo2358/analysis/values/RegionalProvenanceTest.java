package io.github.gustavo2358.analysis.values;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.values.ValuesFixtures.*;
import static io.github.gustavo2358.analysis.values.RegionalValuesTest.*;
import static io.github.gustavo2358.analysis.values.RegionalCompositionTest.*;
import static io.github.gustavo2358.analysis.values.RegionalTransferTest.slice;

class RegionalProvenanceTest {
    static final UncertaintyId GAP=new UncertaintyId(P,"source-value-gap");
    static final OriginId GAP_ORIGIN=new OriginId(P,"source-gap-origin");
    static Publication sourceGap(Publication p,ObjectId subject) {
        var u=p.units().getFirst();var objects=new ArrayList<Memory.ObjectDeclaration>();
        var claim=new Evidence.Claim(new Scopes.EntityScope(List.of(subject)),Evidence.PrecisionStatus.OPEN,List.of(GAP));
        for(var object:u.objects()) {
            if(!object.id().equals(subject)){objects.add(object);continue;}
            var old=object.precision();var precision=new Evidence.Precision(old.control(),old.storage(),old.effects(),claim,old.dependencies());
            objects.add(new Memory.ObjectDeclaration(object.id(),object.displayName(),object.typeRef(),object.storage(),object.visibility(),GAP_ORIGIN,object.coverage(),precision));
        }
        var origins=new ArrayList<>(p.origins());origins.add(new Origins.Unavailable(GAP_ORIGIN,"independent source gap"));
        var gap=new Evidence.Uncertainty(GAP,"NOT_A_WHITELIST",List.of(Evidence.Dimension.VALUES),new Scopes.EntityScope(List.of(subject)),"source value not fully modeled",GAP_ORIGIN);
        return new Publication(P,p.airVersion(),p.capabilities(),p.artifacts(),List.of(unit(U,u.entries(),u.sequences(),objects)),p.storage(),p.resources(),p.artifactRelations(),origins,p.coverage(),List.of(gap),p.premises());
    }
    @Test void copiedSourceRemainderSurvivesDisjointDestinationAndDiesOnOverwrite() {
        var p=sourceGap(twoBases(List.of(returning(U,"s0",List.of(assign(U,"x",WHOLE,"ABCDEFGH"),assign(U,"y",YWHOLE,"12345678"),
            copy("copy",R,0,Y,0,8),assign(U,"repair",YWHOLE,"WXYZWXYZ"))))),WHOLE);
        assertEquals(Evidence.InventoryStatus.COMPLETE,p.coverage().inventory());assertEquals(Evidence.InventoryStatus.COMPLETE,p.units().getFirst().coverage().inventory());
        var execution=run(p);assertFalse(at(execution,"copy",YWHOLE).sourceUnknownRemainder());
        var captured=at(execution,"repair",YWHOLE);assertEquals(List.of("ABCDEFGH"),texts(captured));assertFalse(captured.modelValueRemainder());
        assertTrue(captured.sourceUnknownRemainder(),"captured source uncertainty must follow the copied bytes");assertTrue(captured.provenance().contains(GAP_ORIGIN));
        var repaired=at(execution,"return-s0",YWHOLE);assertFalse(repaired.sourceUnknownRemainder());assertFalse(repaired.modelValueRemainder());
        assertEquals(List.of("WXYZWXYZ"),texts(repaired));
    }
    @Test void sourceGapIsCroppedWithItsFragmentAndDoesNotContaminateCopiedSuffix() {
        var p=sourceGap(twoBases(List.of(returning(U,"s0",List.of(assign(U,"x",WHOLE,"ABCDEFGH"),assign(U,"y",YWHOLE,"12345678"),
            copy("clean-suffix",R,4,Y,0,4),copy("whole-copy",R,0,Y,0,8),slice("repair-prefix",Y,0,"WXYZ"))))),PREFIX);
        var execution=run(p);var clean=at(execution,"whole-copy",YWHOLE);
        assertEquals(List.of("EFGH5678"),texts(clean));assertFalse(clean.sourceUnknownRemainder());
        var copied=at(execution,"repair-prefix",YWHOLE);assertTrue(copied.sourceUnknownRemainder());assertFalse(copied.modelValueRemainder());
        var repaired=at(execution,"return-s0",YWHOLE);assertEquals(List.of("WXYZEFGH"),texts(repaired));assertFalse(repaired.sourceUnknownRemainder());
    }
}
