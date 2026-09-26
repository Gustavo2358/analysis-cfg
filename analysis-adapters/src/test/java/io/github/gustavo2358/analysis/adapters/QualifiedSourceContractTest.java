package io.github.gustavo2358.analysis.adapters;

import java.util.*;
import java.io.*;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import io.github.gustavo2358.analysis.dependencies.*;
import io.github.gustavo2358.analysis.dependencies.source.QualifiedSourceDependencies;

class QualifiedSourceContractTest {
    private final ObjectMapper json=new ObjectMapper();
    private final QualifiedSourceJson codec=new QualifiedSourceJson();
    private ObjectNode wire(String name)throws Exception {try(var in=getClass().getResourceAsStream("/qualified-source-r9/"+name+".source.json")){return (ObjectNode)json.readTree(Objects.requireNonNull(in));}}
    private QualifiedSourceDependencies read(String name)throws Exception{return codec.decode(json.writeValueAsBytes(wire(name)));}
    private ObjectNode unit(ObjectNode wire){return (ObjectNode)wire.path("units").get(0);}
    private ObjectNode first(ObjectNode unit,String name){return (ObjectNode)unit.path(name).get(0);}
    private void reject(ObjectNode value){assertThrows(IllegalArgumentException.class,()->codec.decode(json.writeValueAsBytes(value)));}
    @Test void producerRoundtripAndCandidateOccurrenceSelectionCorrelation()throws Exception {
        for(var name:List.of("ordinary","explicit","conditional","canceled","gap","alter","replacement","deactivated","computed","alternatives","registration")) {
            var evidence=read(name);assertEquals(evidence,codec.decode(codec.encode(evidence)));
            assertArrayEquals(codec.encode(evidence),codec.encode(codec.decode(codec.encode(evidence))));
            var result=SourceQualifiedDependencyResult.admit(evidence,evidence.air().getFirst().publication());
            var occurrences=evidence.units().getFirst().occurrences();
            for(var o:result.occurrences())for(var c:o.candidates()) {
                var source=occurrences.stream().filter(s->s.id().equals(c.occurrence())).findFirst().orElseThrow();
                assertEquals(o.occurrence(),c.occurrence());assertEquals(source.qualifications(),c.qualifications());assertTrue(source.values().stream().anyMatch(v->v.value().equals(c.rawValue())));
            }
            var names=result.occurrences().stream().flatMap(o->o.candidates().stream()).map(SourceQualifiedDependencyResult.Candidate::referenceName).toList();
            if(name.equals("explicit"))assertEquals(List.of("HANDPGM"),names);
            if(name.equals("conditional"))assertEquals(Set.of("CONDPGM","X"),Set.copyOf(names));
            if(name.equals("replacement"))assertEquals(List.of("NEWPGM"),names);
            if(Set.of("canceled","deactivated","computed","registration").contains(name))assertTrue(names.isEmpty(),name);
        }
    }
    @Test void ordinaryAndConditionalAlternativesKeepGuardsSeparate()throws Exception {
        var q=read("alternatives").units().getFirst();
        var o=q.occurrences().stream().filter(x->x.values().stream().anyMatch(v->v.value().equals("BOTH"))).findFirst().orElseThrow();
        var nodes=q.nodes().stream().filter(n->o.qualifications().contains(n.id())).toList();
        assertEquals(Set.of("ACTIVE","DEACTIVATED"),nodes.stream().map(n->n.support().kind()).collect(java.util.stream.Collectors.toSet()));
        var selection=q.selections().getFirst();assertEquals(2,selection.guards().size());
        assertEquals(List.of("CONDITION_RAISED","DEFAULT_DISPOSITION_APPLIES"),selection.guards().stream().map(ref->q.guards().stream().filter(g->g.id().equals(ref)).findFirst().orElseThrow().kind()).toList());
        for(var d:q.derivations())if(!d.selection().isEmpty()) {
            assertEquals(List.of(selection.id()),d.selection());assertEquals(selection.localEntry(),List.of(d.destination()));assertEquals(selection.proofs(),d.proofs());
        }
        assertTrue(q.derivations().stream().anyMatch(d->d.selection().isEmpty()&&nodes.stream().anyMatch(n->n.id().equals(d.destination())&&n.support().kind().equals("ACTIVE"))));
    }
    @Test void inactiveDeactivatedAndComputedRemainBounded()throws Exception {
        var computed=SourceQualifiedDependencyResult.admit(read("computed"),read("computed").air().getFirst().publication()).occurrences().getFirst();
        assertEquals(SourceQualifiedDependencyResult.Status.QUALIFIED_POSSIBLE,computed.status());assertTrue(computed.valueRemainder());assertTrue(computed.candidates().isEmpty());
        for(var name:List.of("canceled","deactivated")) {
            var u=read(name).units().getFirst();assertTrue(u.selections().stream().anyMatch(s->s.target().isEmpty()&&s.outerLevelRemainder()));
            assertTrue(u.occurrences().stream().allMatch(o->o.qualifications().isEmpty()));
        }
    }
    @Test void versionsIdentitiesAndReferencesAreClosed()throws Exception {
        for(var version:List.of("0.9.0","1.1.0","2.0.0")){var w=wire("conditional");w.put("version",version);reject(w);}
        var standalone=wire("conditional");standalone.putArray("air");
        var detached=codec.decode(json.writeValueAsBytes(standalone));assertFalse(SourceQualifiedDependencyResult.admit(detached).occurrences().isEmpty());
        assertThrows(IllegalArgumentException.class,()->SourceQualifiedDependencyResult.admit(detached,"uncorrelated-air"));
        var extra=wire("conditional");extra.put("operationId","fake");reject(extra);
        var foreign=wire("conditional");((ObjectNode)first(unit(foreign),"occurrences").path("id").path("unit")).put("compilationUnitId","FOREIGN");reject(foreign);
        var operand=wire("conditional");((ObjectNode)first(unit(operand),"occurrences").path("operands").get(0).path("id").path("statement")).put("handle","invented");reject(operand);
        for(var field:List.of("guards","proofs","localEntry","target")) {var w=wire("conditional");first(unit(w),"selections").putArray(field).add("missing");reject(w);}
        var proof=wire("conditional");var p=first(unit(proof),"proofs");p.putArray("dependencies").add(p.path("id").asText());reject(proof);
        var node=wire("conditional");first(unit(node),"occurrences").putArray("qualifications").add("absent-node");reject(node);
        var correlation=wire("conditional");first(unit(correlation),"occurrences").putArray("qualifications").add(first(unit(correlation),"selections").path("source").asText());reject(correlation);
        var guard=wire("conditional");first(unit(guard),"guards").put("kind","ASSUMED_TRUE");reject(guard);
        var noGuards=wire("conditional");first(unit(noGuards),"selections").putArray("guards");reject(noGuards);
        var noActivation=wire("conditional");for(var n:unit(noActivation).path("nodes"))if(n.path("support").path("kind").asText().equals("ACTIVE"))((ObjectNode)n.path("support")).putArray("activation");reject(noActivation);
        var selected=read("conditional");assertThrows(IllegalArgumentException.class,()->SourceQualifiedDependencyResult.admit(selected,"another-publication"));
        var candidate=SourceQualifiedDependencyResult.admit(selected,selected.air().getFirst().publication());assertThrows(IllegalArgumentException.class,()->new SourceQualifiedDependencyResult(selected,List.of()));
        assertFalse(candidate.occurrences().isEmpty());
    }
}
