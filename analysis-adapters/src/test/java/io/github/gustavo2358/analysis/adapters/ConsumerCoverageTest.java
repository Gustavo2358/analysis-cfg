package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.json.AirJson;
import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.dependencies.*;
import io.github.gustavo2358.analysis.query.*;
import io.github.gustavo2358.analysis.storage.*;
import io.github.gustavo2358.analysis.values.*;
import java.io.ByteArrayOutputStream;
import java.nio.file.*;
import java.math.BigInteger;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.adapters.ResultFixtures.*;
import static io.github.gustavo2358.analysis.adapters.FileComputedOracleTest.*;

/** W3: independent expectations at storage query, consumer and public wire boundaries. */
class ConsumerCoverageTest {
    static Operations.Invoke invoke(String family,String shape,String literal) {
        var base=file("consumer","end",shape.equals("choice")?"name":shape,literal,POLICY,false);
        String ns=family.equals("native")?"cobol.external-file-name":family.equals("file")?"cics.file":"cics.program";
        var policy=family.equals("native")?Interactions.ExactName.INSTANCE:family.equals("file")?POLICY:new Interactions.ExtensionName("cics-ts.program","1");
        String category=family.equals("file")||family.equals("native")?"file":"program";
        Expression read=base.target() instanceof Interactions.ComputedTarget c?c.name():null;
        if(shape.equals("choice")) {
            var op=base.header().id();
            read=new Expressions.Read(operand(op,"read-choice",Operand.Role.CALL_TARGET),new Places.Choice(operand(op,"choice",Operand.Role.VALUE_READ),
                List.of(new Places.ObjectPlace(operand(op,"choice-a",Operand.Role.VALUE_READ),obj("name")),new Places.ObjectPlace(operand(op,"choice-b",Operand.Role.VALUE_READ),obj("alias"))),Scopes.NoMemory.INSTANCE,Types.known(Types.Builtin.TEXT)));
        }
        var target=literal!=null?new Interactions.LiteralTarget(category,ns,literal,policy,O):new Interactions.ComputedTarget(category,ns,read,policy,O);
        return new Operations.Invoke(base.header(),family.equals("LINK")?"call":family.equals("XCTL")?"execute":"read",target,base.arguments(),base.results(),base.signature(),base.effectOperands(),base.effectBound(),
            family.equals("XCTL")?new Control.InvocationOutcomes(List.of(),new Scopes.WithinControl(new Scopes.UnitControl(U,false,false,false,false,false,true))):base.outcomes(),base.contract());
    }
    static Publication model(String family,String mode,String shape) {
        var sequences=new ArrayList<Sequence>();
        if(mode.equals("multiple")||mode.equals("partial")) {
            sequences.add(choose("start","a","b"));sequences.add(move("a","seed-a","PROGA   ","name","use"));
            sequences.add(mode.equals("multiple")?move("b","seed-b","PROGB   ","name","use"):jump(U,"b","use"));
        } else sequences.add(mode.equals("unknown")||mode.equals("literal")?jump(U,"start","use"):move("start","seed-a","PROGA   ","name","use"));
        sequences.add(seq("use",List.of(),invoke(family,shape,mode.equals("literal")?"PROGA":null)));sequences.add(returning(U,"end",List.of()));
        return publication(sequences);
    }
    static StorageValueFact provider(Publication p,String shape) {
        var session=ValueToCallEvidenceTest.session(p);var e=p.units().getFirst().entries().getFirst().id();
        var prepared=new StorageValuesProvider().prepare(session,StorageValuesProvider.key(e));assertNull(prepared.refusal());
        StorageSubject subject=switch(shape) {
            case "slice" -> new StorageSubject.PhysicalRange(BASE,StorageRange.exact(BigInteger.valueOf(4),BigInteger.valueOf(8)),CODEC);
            case "choice" -> new StorageSubject.PlaceOccurrence(new OperandId(new OperationOwner(new OperationId(U,"consumer")),"choice"));
            default -> new StorageSubject.NamedObject(obj(shape));
        };
        var run=prepared.execute();
        if(shape.equals("choice"))System.out.println("W3_CHOICE_METRICS "+new TreeMap<>(run.outcome().metrics()));
        var result=run.observe(List.of(new PointQuery<>(ProgramPoint.before(e,new OperationId(U,"consumer")),subject))).batch();
        assertEquals(ObservationBatch.Status.COMPLETE,result.status());assertEquals(ObservationBatch.QueryStatus.VALUE,result.observations().getFirst().status());
        return result.observations().getFirst().value();
    }
    static void wire(String name,DependencyResult result)throws Exception {
        var out=new ByteArrayOutputStream();new DependencyJson().write(result,out);
        var dir=Path.of("target/analysis-gaps-w3");Files.createDirectories(dir);Files.write(dir.resolve(name+".json"),out.toByteArray());
    }
    static DependencyResult analyze(Publication p) {
        var codec=new AirJson();assertEquals(p,codec.decode(codec.encode(p)));return new DependencyAnalysis().prepare(p);
    }
    static List<String> expected(String mode) {return mode.equals("unknown")?List.of():mode.equals("multiple")?List.of("PROGA","PROGB"):List.of("PROGA");}
    static void providerExpected(StorageValueFact f,String mode) {providerExpected(f,mode,false);}
    static void providerExpected(StorageValueFact f,String mode,boolean sourceOpen) {
        assertEquals(expected(mode).stream().map(n->new Values.TextValue(n+"   ")).toList(),f.candidates());
        assertEquals(mode.equals("unknown")||mode.equals("partial"),f.modelValueRemainder());assertEquals(sourceOpen,f.sourceUnknownRemainder());
        for(var c:f.candidateSupports())assertEquals(List.of(c.candidate().value().startsWith("PROGA")?"seed-a":"seed-b"),c.producers().stream().map(s->s.evidence().localId()).toList());
    }
    @Test void cicsLiteralCommandsAreDistinctAndNeedNoValues()throws Exception {
        for(String command:List.of("LINK","XCTL")) {
            var p=model(command,"literal","name");var result=analyze(p);var site=result.sites().getFirst();
            assertEquals(command,site.command());assertEquals("CICS",site.technology());assertEquals("cics.program",site.namespace());
            assertEquals("cics-ts.program@1",site.nameProfile());assertEquals(DependencySiteFact.TargetStatus.RESOLVED_CANDIDATES,site.targetStatus());
            assertEquals(DependencySiteFact.SupportKind.CICS_LITERAL,site.candidates().getFirst().supports().getFirst().kind());
            assertEquals(new OperationId(U,"consumer"),site.candidates().getFirst().supports().getFirst().producer());assertEquals(command.equals("XCTL"),site.sourceValueRemainder());assertEquals(command.equals("XCTL"),site.effectiveUnknownRemainder());
            assertTrue(CallDependencyPlan.select(ValueToCallEvidenceTest.session(p)).stream().flatMap(r->r.dependencies().requiredAnalysisKeys().stream()).noneMatch(k->k.implementation().contains("Values")));
            wire(command+"-literal",result);
        }
    }
    @Test void cicsComputedObjectAndPhysicalQueriesKeepAllValuesAndSupports()throws Exception {
        for(String command:List.of("LINK","XCTL"))for(String shape:List.of("name","slice","choice"))for(String mode:List.of("simple","multiple","unknown","partial")) {
            var p=model(command,mode,shape);var fact=provider(p,shape);providerExpected(fact,mode,command.equals("XCTL"));
            var result=analyze(p);var site=result.sites().getFirst();var names=expected(mode);
            assertEquals(command,site.command());assertEquals(names,site.candidates().stream().map(DependencySiteFact.Candidate::referenceName).toList());
            assertEquals(fact.effectiveUnknownRemainder(),site.effectiveUnknownRemainder());assertEquals(fact.modelValueRemainder(),site.modelValueRemainder());
            assertEquals(names.isEmpty()?DependencySiteFact.TargetStatus.OPEN_TARGET:DependencySiteFact.TargetStatus.RESOLVED_CANDIDATES,site.targetStatus());
            assertTrue(site.evidence().containsAll(fact.evidence()));assertTrue(site.provenance().containsAll(fact.provenance()));assertTrue(site.premises().containsAll(fact.premises()));
            for(var c:site.candidates()) {
                var original=fact.candidateSupports().stream().filter(x->x.candidate().value().equals(c.rawValue())).findFirst().orElseThrow();
                assertEquals(original.producers().stream().map(x->new DependencySiteFact.Support(DependencySiteFact.SupportKind.VALUE_PRODUCER,x.evidence(),x.origin(),x.premises())).toList(),c.supports());
            }
            var keys=CallDependencyPlan.select(ValueToCallEvidenceTest.session(p)).stream().flatMap(r->r.dependencies().requiredAnalysisKeys().stream()).map(k->k.implementation()).collect(java.util.stream.Collectors.toSet());
            assertEquals(Set.of("Reachability",shape.equals("name")?"RegionalValues":"StorageValues"),keys);
            wire(command+"-"+shape+"-"+mode,result);
        }
    }
    @Test void cicsFileUsesStorageFactsThroughPublicWire()throws Exception {
        for(String shape:List.of("name","slice","choice"))for(String mode:List.of("simple","multiple","unknown","partial")) {
            var p=model("file",mode,shape);var fact=provider(p,shape);providerExpected(fact,mode);var result=analyze(p);var site=result.fileDependencies().sites().getFirst();
            assertTrue(result.sites().isEmpty());assertEquals("cics.file",site.namespace());assertEquals(expected(mode),site.candidates().stream().map(FileDependencyResult.Candidate::referenceName).toList());
            assertEquals(fact.effectiveUnknownRemainder(),site.unknownRemainder());assertEquals(1L,result.fileDependencies().metrics().get("possibleValuesPreparations"));
            for(var c:site.candidates()) {
                var original=fact.candidateSupports().stream().filter(x->x.candidate().value().equals(c.rawValue())).findFirst().orElseThrow();
                assertEquals(original.producers().stream().map(x->new FileDependencyResult.Support("VALUE_PRODUCER",x.evidence(),x.origin(),x.premises())).toList(),c.supports());
            }
            wire("file-"+shape+"-"+mode,result);
        }
    }
    @Test void cicsProgramClosedChoiceConsumesAlreadyPreparedStorageEvidence()throws Exception {
        var p=model("LINK","multiple","choice");providerExpected(provider(p,"choice"),"multiple");
        var result=analyze(p);var site=result.sites().getFirst();assertEquals(DependencySiteFact.TargetStatus.RESOLVED_CANDIDATES,site.targetStatus());
        assertEquals(List.of("PROGA","PROGB"),site.candidates().stream().map(DependencySiteFact.Candidate::referenceName).toList());
        assertFalse(site.interpretationUnknownRemainder());assertFalse(site.effectiveUnknownRemainder());
        assertEquals(List.of("seed-a","seed-b"),site.candidates().stream().map(c->c.supports().getFirst().producer().localId()).toList());
        assertEquals(1L,result.metrics().get("possibleValuesRuns"));wire("program-choice-reconciled",result);
    }
    @Test void cicsChoiceCannotBorrowProofFromOnlyOneAlternative()throws Exception {
        for(String mode:List.of("short-leaf","open")) {
            var p=model("LINK","simple","choice");var u=p.units().getFirst();var sequences=new ArrayList<>(u.sequences());
            var use=sequences.get(sequences.size()-2);var invoke=(Operations.Invoke)use.terminator();var target=(Interactions.ComputedTarget)invoke.target();
            var read=(Expressions.Read)target.name();var choice=(Places.Choice)read.place();var leaves=new ArrayList<>(choice.candidates());
            if(mode.equals("short-leaf"))leaves.set(1,new Places.ObjectPlace(leaves.get(1).header(),obj("short")));
            var changed=new Places.Choice(choice.header(),leaves,mode.equals("open")?new Scopes.WithinMemory(new Scopes.StorageMemory(List.of(BASE))):Scopes.NoMemory.INSTANCE,
                choice.typeRef());
            var name=new Expressions.Read(read.header(),changed);var t=new Interactions.ComputedTarget(target.category(),target.namespace(),name,target.namePolicy(),target.origin());
            sequences.set(sequences.size()-2,new Sequence(use.label(),use.instructions(),new Operations.Invoke(invoke.header(),invoke.action(),t,invoke.arguments(),invoke.results(),invoke.signature(),invoke.effectOperands(),invoke.effectBound(),invoke.outcomes(),invoke.contract()),use.origin()));
            p=W1dEffectsTest.sequences(p,sequences,u.entries());
            if(mode.equals("open"))p=new Publication(p.id(),p.airVersion(),p.capabilities(),p.artifacts(),p.units(),p.storage(),p.resources(),p.artifactRelations(),p.origins(),p.coverage(),p.uncertainties(),
                List.of(new Proofs.Premise(new PremiseId(P,"open-text-domain"),"synthetic oracle","text domain only, not an eight-byte area proof",O,
                    new Proofs.SameDomain(new Proofs.OperandDomain(choice.header().id()),new Proofs.ObjectDomain(obj("name")),new Proofs.OperationDomain(invoke.header().id())))));
            var validation=io.github.gustavo2358.air.validation.AirValidator.validate(p);assertEquals(io.github.gustavo2358.air.validation.ValidationResult.Status.STRUCTURALLY_VALID,validation.status(),mode+": "+validation.issues());
            // SameDomain is valid in-memory AIR but the pinned AIR codec does not encode it.
            var result=mode.equals("open")?new DependencyAnalysis().prepare(p):analyze(p);var site=result.sites().getFirst();
            assertEquals(DependencySiteFact.TargetStatus.UNSUPPORTED_TARGET_EXPRESSION,site.targetStatus());assertTrue(site.candidates().isEmpty());assertTrue(site.effectiveUnknownRemainder());
            wire("program-choice-"+mode,result);
        }
    }
    @Test void nativeComputedNameIsOutsideCurrentFileQueryContract()throws Exception {
        var p=model("native","simple","name");providerExpected(provider(p,"name"),"simple");
        var result=analyze(p);var site=result.fileDependencies().sites().getFirst();assertTrue(site.candidates().isEmpty());assertTrue(site.unknownRemainder());
        assertTrue(site.analysisReasons().contains("FILE_VALUES_UNAVAILABLE"));assertEquals(0L,result.fileDependencies().metrics().get("possibleValuesPreparations"));
        wire("native-computed-unadmitted",result);
    }
    @Test void nativeExactResourceIdentityAndCicsLiteralStaySeparate()throws Exception {
        var result=analyze(ResourceBindingOracle.publication("A2"));var files=result.fileDependencies();var site=files.sites().getFirst();
        assertEquals("cobol.external-file-name",site.namespace());assertEquals("CLIENTDD",site.candidates().getFirst().referenceName());
        var resource=site.bindings().getFirst().declaration();var declaration=files.declarations().stream().filter(d->d.id().equals(resource)).findFirst().orElseThrow();
        assertEquals(declaration.name(),site.candidates().getFirst().referenceName());assertFalse(site.unknownRemainder());assertEquals(0L,files.metrics().get("possibleValuesPreparations"));
        assertEquals(1,files.edges().size());wire("native-literal",result);
        result=analyze(model("file","literal","name"));site=result.fileDependencies().sites().getFirst();assertEquals("cics.file",site.namespace());assertEquals("FILE_LITERAL",site.candidates().getFirst().supports().getFirst().kind());
        assertFalse(site.unknownRemainder());assertEquals(0L,result.fileDependencies().metrics().get("possibleValuesPreparations"));wire("file-literal",result);
    }
}
