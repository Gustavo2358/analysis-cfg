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
        for(var version:List.of("0.9.0","1.3.0","2.0.0")){var w=wire("conditional");w.put("version",version);reject(w);}
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

    private ObjectNode possibility(String name)throws Exception {try(var in=getClass().getResourceAsStream("/source-possibility/"+name+".source.json")){return (ObjectNode)json.readTree(Objects.requireNonNull(in));}}
    @Test void sourcePossibilityPreservesCandidatesAndProvenExclusions()throws Exception {
        for(var name:List.of("sql-update","unknown-perform","unknown-before-terminal","unknown-unused","unknown-copy-kill","unknown-native","unknown-native-dead","next-sentence","mutable-transfer","mutable-unreachable","mutable-no-transfer","mutable-perform","unknown-branch-query","unknown-branch-kill")) {
            var e=codec.decode(json.writeValueAsBytes(possibility(name)));assertEquals(e,codec.decode(codec.encode(e)));
            var r=SourceQualifiedDependencyResult.admit(e);var expected=name.equals("sql-update")?List.of("AFTERIO"):name.equals("unknown-perform")?List.of("AFTERP"):Set.of("mutable-transfer","mutable-perform").contains(name)?List.of("POSSIBLE"):List.of();
            assertEquals(expected,r.occurrences().stream().flatMap(o->o.candidates().stream()).map(SourceQualifiedDependencyResult.Candidate::referenceName).toList(),name);
            if(!expected.isEmpty())assertTrue(r.occurrences().stream().filter(o->!o.candidates().isEmpty()).allMatch(o->o.status()==SourceQualifiedDependencyResult.Status.POSSIBLE_UNDER_UNKNOWN_CONTROL));
            if(name.equals("unknown-native")) {
                assertEquals(4,r.nativeFiles().size());
                assertEquals(Set.of("FIRSTDD","SECONDDD"),r.nativeFiles().stream().flatMap(o->o.candidates().stream()).map(SourceQualifiedDependencyResult.Candidate::referenceName).collect(java.util.stream.Collectors.toSet()));
                assertTrue(r.nativeFiles().stream().allMatch(o->o.remainder()&&o.status()==SourceQualifiedDependencyResult.Status.POSSIBLE_UNDER_UNKNOWN_CONTROL));
            }
            if(name.equals("unknown-native-dead"))assertTrue(r.nativeFiles().stream().allMatch(o->o.candidates().isEmpty()&&o.status()==SourceQualifiedDependencyResult.Status.NOT_QUALIFIED_IN_SOURCE_MODEL));
        }
    }
    @Test void sourcePossibilityVersionAndNativeProvenanceAreClosed()throws Exception {
        var old=possibility("sql-update");old.put("version","1.0.0");reject(old);
        for(var mutation:List.of("foreign-node","negative-ordinal","duplicate-use","empty-origin","wrong-point","missing-alternative")) {
            var w=possibility("unknown-native");var files=(ArrayNode)unit(w).path("nativeFiles");var f=(ObjectNode)files.get(0);
            switch(mutation) {
                case "foreign-node" -> f.putArray("qualifications").add("foreign");
                case "negative-ordinal" -> f.put("ordinal",-1);
                case "duplicate-use" -> files.add(f.deepCopy());
                case "empty-origin" -> ((ObjectNode)f.path("names").get(0)).putArray("declarationOrigins");
                case "wrong-point" -> f.put("controlLocation",files.get(1).path("controlLocation").asText());
                case "missing-alternative" -> f.putArray("qualifications");
            }
            reject(w);
        }
    }

    @Test void possibilityCertificateUsesAndForCallersAndOrForAlternatives()throws Exception {
        var base=read("ordinary").units().getFirst();var origin=base.proofs().getFirst().provenance();
        var nodes=new ArrayList<QualifiedSourceDependencies.Node>();
        for(var name:List.of("R","A","B","C","D","E","F"))nodes.add(new QualifiedSourceDependencies.Node(name,"context",base.statements().getFirst().id().handle(),base.nodes().getFirst().support()));
        var proofs=List.of(new QualifiedSourceDependencies.Proof("certain","LOCAL_GRAMMAR","test",origin,List.of()),
            new QualifiedSourceDependencies.Proof("assume","CONTROL_POSSIBILITY","test",origin,List.of()),
            new QualifiedSourceDependencies.Proof("alias","LOCAL_GRAMMAR","test",origin,List.of("assume")));
        var ds=List.of(new QualifiedSourceDependencies.Derivation("root",List.of(),"R",List.of(),"PRIMARY_ENTRY",List.of("certain"),List.of()),
            new QualifiedSourceDependencies.Derivation("a",List.of("R"),"A",List.of(),"possible",List.of("assume"),List.of()),
            new QualifiedSourceDependencies.Derivation("b",List.of("R"),"B",List.of(),"normal",List.of("certain"),List.of()),
            new QualifiedSourceDependencies.Derivation("c",List.of("B"),"C",List.of("A"),"return",List.of("certain"),List.of()),
            new QualifiedSourceDependencies.Derivation("d1",List.of("C"),"D",List.of(),"alternative",List.of("certain"),List.of()),
            new QualifiedSourceDependencies.Derivation("d2",List.of("B"),"D",List.of(),"alternative",List.of("certain"),List.of()),
            new QualifiedSourceDependencies.Derivation("e",List.of("D"),"E",List.of(),"alias",List.of("alias"),List.of()),
            new QualifiedSourceDependencies.Derivation("f",List.of("E"),"F",List.of(),"cycle",List.of("certain"),List.of()),
            new QualifiedSourceDependencies.Derivation("back",List.of("F"),"E",List.of(),"cycle",List.of("certain"),List.of()));
        var unit=new QualifiedSourceDependencies.UnitEvidence(base.unit(),true,base.statements(),List.of(),List.of(),nodes,ds,List.of(),List.of(),List.of(),proofs,List.of());
        assertEquals(Set.of("A","C","E","F"),SourceControlEvidence.assumedOnly(unit));
        assertEquals(Set.of("A","C","D","E","F"),SourceControlEvidence.affected(unit));
    }
    @Test void sourceUndefinedReentryIsConditionalAndVersioned()throws Exception {
        var names=List.of("reentry-direct","reentry-mutual","reentry-direct-handler","reentry-direct-callers",
            "reentry-callers","reentry-callers-handler","reentry-conditional","reentry-conditional-handler",
            "reentry-range","reentry-section","reentry-values","reentry-file","reentry-file-unconditional",
            "sequential-callers","sequential-callers-handler","sequential-loop","terminal-before","terminal-before-handler","halt-before","goto-before");
        for(var name:names) {
            ObjectNode wire;try(var in=getClass().getResourceAsStream("/perform-reentry/"+name+".source.json")) {wire=(ObjectNode)json.readTree(Objects.requireNonNull(in,name));}
            var q=codec.decode(json.writeValueAsBytes(wire));assertEquals(q,codec.decode(codec.encode(q)));
            var result=SourceQualifiedDependencyResult.admit(q);var candidates=result.occurrences().stream().flatMap(o->o.candidates().stream()).map(SourceQualifiedDependencyResult.Candidate::referenceName).toList();
            assertTrue(candidates.stream().noneMatch(c->c.startsWith("DEAD")),name);
            if(name.startsWith("reentry-")) {
                assertEquals("1.2.0",q.version());
                var proofs=q.units().getFirst().proofs();assertTrue(proofs.stream().anyMatch(p->p.kind().equals("CONTROL_POSSIBILITY")&&p.rule().equals("undefined-active-reentry-may-complete")));
                for(var version:List.of("1.0.0","1.1.0")){var copy=wire.deepCopy();copy.put("version",version);reject(copy);}
                var copy=wire.deepCopy();for(var n:copy.path("units").get(0).path("nodes"))if(n.path("support").path("cause").asText().equals("SOURCE_REENTRY_UNDEFINED")) {((ObjectNode)n.path("support")).put("cause","INVENTED_CAUSE");break;}reject(copy);
            } else assertNotEquals("1.2.0",q.version(),"ordinary and dead cycles require no new uncertainty");
            if(name.equals("reentry-direct")||name.equals("reentry-mutual")||name.equals("reentry-direct-handler")) {
                var after=result.occurrences().stream().filter(o->o.candidates().stream().anyMatch(c->c.referenceName().equals("AFTERP"))).findFirst().orElseThrow();
                assertEquals(SourceQualifiedDependencyResult.Status.POSSIBLE_UNDER_UNKNOWN_CONTROL,after.status());
                assertFalse(after.candidates().getFirst().qualifications().isEmpty());
            }
            if(name.equals("reentry-direct-callers"))assertTrue(candidates.containsAll(List.of("FIRST","SECOND")));
            if(name.equals("reentry-file-unconditional")) {
                assertEquals(2,result.nativeFiles().size());
                assertTrue(result.nativeFiles().stream().allMatch(f->f.status()==SourceQualifiedDependencyResult.Status.POSSIBLE_UNDER_UNKNOWN_CONTROL&&f.candidates().stream().anyMatch(c->c.referenceName().equals("CLIENTDD"))));
            }
        }
    }

}
