package io.github.gustavo2358.analysis.dependencies;

import io.github.gustavo2358.analysis.adapters.*;
import io.github.gustavo2358.analysis.solver.*;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Global relation checker only; admission ownership/certificate is a separate pending boundary. */
final class SourceSnapshotRelationsTest {
    @TempDir Path directory;
    private static final ObjectMapper JSON=new ObjectMapper();
    private static AnalysisResources resources(){return new AnalysisResources(new AnalysisResources.Limits(1048576,16384,0,128L*1024*1024,2,Long.MAX_VALUE,0));}
    private void check(byte[] wire,boolean valid)throws Exception {
        var resources=resources();
        try(var pages=new FilePageStore(directory,4096,4,resources,AnalysisResources.Phase.VALIDATION);
            var storage=new QualifiedSourceJson().decodeSnapshot(new ByteArrayInputStream(wire),pages,resources)) {
            long before=pages.statistics().livePages();
            if(valid)SourceSnapshotRelations.check(storage,pages,resources);
            else assertThrows(IllegalArgumentException.class,()->SourceSnapshotRelations.check(storage,pages,resources));
            assertEquals(before,pages.statistics().livePages());assertTrue(storage.root()>0);
        }
        for(var pool:AnalysisResources.Pool.values())assertEquals(0,resources.used(pool));
    }
    @Test void everyExistingLiteralPassesTheGlobalRelationChecker()throws Exception {
        int attempted=0;
        try(var paths=Files.walk(Path.of("src/test/resources"))) {
            for(var path:paths.filter(p->p.toString().endsWith(".source.json")).sorted().toList()) {
                byte[] wire=Files.readAllBytes(path);
                if(!JSON.readTree(wire).path("schema").asText().equals("qualified-source-dependencies"))continue;
                new QualifiedSourceJson().decode(wire);check(wire,true);attempted++;
            }
        }
        assertEquals(53,attempted);System.out.println("SOURCE_GLOBAL_RELATION_LITERALS accepted="+attempted+" certificate=NOT_ISSUED");
    }
    private static ObjectNode fixture(String field,boolean needsNonemptyQualifications)throws Exception {
        try(var paths=Files.walk(Path.of("src/test/resources"))) {
            for(var path:paths.filter(p->p.toString().endsWith(".source.json")).sorted().toList()) {
                var root=JSON.readTree(Files.readAllBytes(path));
                if(!root.path("schema").asText().equals("qualified-source-dependencies"))continue;
                for(var unit:root.path("units"))if(unit.path(field).isArray()&&!unit.path(field).isEmpty()) {
                    if(needsNonemptyQualifications&&unit.path(field).get(0).path("qualifications").isEmpty())continue;
                    var selected=((ObjectNode)root).deepCopy();var units=JSON.createArrayNode().add(unit.deepCopy());selected.set("units",units);
                    new QualifiedSourceJson().decode(JSON.writeValueAsBytes(selected));return selected;
                }
            }
        }
        throw new AssertionError("independent literal witness absent: "+field);
    }
    private void reject(ObjectNode root,Consumer<ObjectNode> mutate)throws Exception {
        byte[] original=JSON.writeValueAsBytes(root);new QualifiedSourceJson().decode(original);check(original,true);
        mutate.accept(root);byte[] wire=JSON.writeValueAsBytes(root);
        assertThrows(IllegalArgumentException.class,()->new QualifiedSourceJson().decode(wire),"resident precondition must independently reject");
        check(wire,false);
    }
    private static ObjectNode unit(ObjectNode root){return (ObjectNode)root.path("units").get(0);}
    @Test void duplicateInventoryAndForeignStatementOwnershipAreRejected()throws Exception {
        reject(fixture("statements",false),r->{var statements=(ArrayNode)unit(r).get("statements");statements.add(statements.get(0).deepCopy());});
        reject(fixture("proofs",false),r->{var proofs=(ArrayNode)unit(r).get("proofs");proofs.add(proofs.get(0).deepCopy());});
        reject(fixture("occurrences",false),r->{var occurrences=(ArrayNode)unit(r).get("occurrences");occurrences.add(occurrences.get(0).deepCopy());});
        reject(fixture("statements",false),r->{var id=(ObjectNode)unit(r).path("statements").get(0).path("id").path("unit");id.set("structuralPath",JSON.createArrayNode().add(999));});
        reject(fixture("statements",false),r->{var units=(ArrayNode)r.get("units");units.add(units.get(0).deepCopy());});
    }
    @Test void unusedProofCyclesAndUngroundedQualificationNodesAreRejected()throws Exception {
        reject(fixture("proofs",false),r->{var proof=(ObjectNode)unit(r).path("proofs").get(0);proof.set("dependencies",JSON.createArrayNode().add(proof.path("id").asText()));});
        reject(fixture("proofs",false),r->{var proofs=(ArrayNode)unit(r).get("proofs");var unused=((ObjectNode)proofs.get(0)).deepCopy();unused.put("id","independent-unused-proof");unused.set("dependencies",JSON.createArrayNode().add("independent-unused-proof"));proofs.add(unused);});
        reject(fixture("derivations",false),r->{for(var item:unit(r).path("derivations")){var derivation=(ObjectNode)item;derivation.set("source",JSON.createArrayNode().add(derivation.path("destination").asText()));derivation.set("callerPremise",JSON.createArrayNode());derivation.set("selection",JSON.createArrayNode());}});
    }
    @Test void missingAndDuplicateAlternativesCannotProduceSuccess()throws Exception {
        for(String field:new String[]{"occurrences","nativeFiles"}) {
            reject(fixture(field,true),r->((ObjectNode)unit(r).path(field).get(0)).set("qualifications",JSON.createArrayNode()));
            reject(fixture(field,true),r->{var qualifications=(ArrayNode)unit(r).path(field).get(0).get("qualifications");qualifications.add(qualifications.get(0).deepCopy());});
        }
    }
    private static ObjectNode finiteGraph()throws Exception {
        var root=fixture("proofs",false);var unit=unit(root);unit.put("controlAvailable",true);
        for(String field:new String[]{"occurrences","targets","derivations","selections","events","guards","frontiers","nativeFiles"})unit.set(field,JSON.createArrayNode());
        unit.remove("nominalValues");
        var proof=((ObjectNode)unit.path("proofs").get(0)).deepCopy();proof.set("dependencies",JSON.createArrayNode());unit.set("proofs",JSON.createArrayNode().add(proof));
        var nodes=JSON.createArrayNode();
        for(int i=0;i<5;i++) {
            var node=JSON.createObjectNode();node.put("id","n"+i);node.put("context","SYNTHETIC");node.put("location","location"+i);
            var support=JSON.createObjectNode();support.put("kind","ENTRY_UNKNOWN");support.put("cause","NONE");support.set("target",JSON.createArrayNode());support.set("activation",JSON.createArrayNode());node.set("support",support);nodes.add(node);
        }
        unit.set("nodes",nodes);return root;
    }
    private static void derivation(ObjectNode root,String id,int source,int caller,int destination) {
        var row=JSON.createObjectNode();row.put("id",id);row.put("destination","n"+destination);row.put("authority",source<0?"PRIMARY_ENTRY":"SYNTHETIC_TRANSFER");
        row.set("source",source<0?JSON.createArrayNode():JSON.createArrayNode().add("n"+source));row.set("callerPremise",caller<0?JSON.createArrayNode():JSON.createArrayNode().add("n"+caller));
        row.set("selection",JSON.createArrayNode());row.set("proofs",JSON.createArrayNode().add(unit(root).path("proofs").get(0).path("id").asText()));
        ((ArrayNode)unit(root).get("derivations")).add(row);
    }
    @Test void callerAndSourceRemainAndWhileIncomingDerivationsRemainOr()throws Exception {
        var and=finiteGraph();derivation(and,"root0",-1,-1,0);derivation(and,"root1",-1,-1,1);
        derivation(and,"both",0,1,2);derivation(and,"reentry",2,-1,1);derivation(and,"tail",2,-1,3);derivation(and,"same-premise",0,0,4);
        new QualifiedSourceJson().decode(JSON.writeValueAsBytes(and));check(JSON.writeValueAsBytes(and),true);
        reject(and,r->((ArrayNode)unit(r).get("derivations")).remove(1)); // OR mutation would wrongly bootstrap n1 via n2.
        var or=finiteGraph();derivation(or,"root0",-1,-1,0);derivation(or,"root1",-1,-1,1);
        derivation(or,"cyclic-alternative",3,-1,2);derivation(or,"cycle",2,-1,3);derivation(or,"grounded-alternative",0,-1,2);derivation(or,"same-premise",0,0,4);
        new QualifiedSourceJson().decode(JSON.writeValueAsBytes(or));check(JSON.writeValueAsBytes(or),true);
    }
}
