package io.github.gustavo2358.analysis.values;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.storage.*;
import java.math.BigInteger;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.values.ValuesFixtures.*;
import static io.github.gustavo2358.analysis.values.RegionalValuesTest.*;

/** Historical fan-out witnesses migrated to the positive-base contract. */
class RegionalExplosionFixturesTest {
    static ObjectId object(int i) { return new ObjectId(U,"synthetic-object-"+i); }

    /** One whole view per region avoids accidental partition/copy connectivity. */
    static Publication fixture(int regions,int producers,boolean disjoint) {
        var storage=new ArrayList<Memory.Storage>();
        var objects=new ArrayList<Memory.ObjectDeclaration>();
        var ids=new ArrayList<StorageId>();
        for(int i=0;i<regions;i++) {
            var id=new StorageId(P,"synthetic-region-"+i);ids.add(id);
            storage.add(new Memory.Region(new Memory.StorageHeader(id,Optional.of(U),Memory.Lifetime.PERSISTENT,
                Memory.Visibility.PRIVATE,origin(P)),Optional.of(BigInteger.valueOf(8)),Optional.empty()));
            objects.add(new Memory.ObjectDeclaration(object(i),Optional.empty(),Types.known(Types.Builtin.TEXT),
                new Memory.ViewBinding(id,BigInteger.ZERO,BigInteger.valueOf(8),IBM),Memory.Visibility.PRIVATE,
                origin(P),Evidence.CoverageStatus.MODELED,header(U,"metadata").precision()));
        }
        var writes=new ArrayList<Instruction>();
        for(int i=0;i<producers;i++)writes.add(assign(U,"synthetic-producer-"+i,object(0),"ABCDEFGH"));
        var p=regional(List.of(returning(U,"s0",writes)));
        var unit=p.units().getFirst();
        var premises=disjoint?List.of(new Proofs.Premise(new PremiseId(P,"synthetic-separation"),
            "synthetic contract","independent allocations",origin(P),new Proofs.DisjointStorage(ids))):List.<Proofs.Premise>of();
        return new Publication(P,p.airVersion(),p.capabilities(),p.artifacts(),
            List.of(unit(U,unit.entries(),unit.sequences(),objects)),storage,p.resources(),p.artifactRelations(),
            p.origins(),p.coverage(),p.uncertainties(),premises);
    }

    static List<StatementEffects.Target> targets(Publication p) {
        var effects=new StatementEffects(new StorageIndex(session(p)));
        return effects.statements().stream().flatMap(s->s.writes().stream()).flatMap(w->w.targets().stream()).toList();
    }
    static long unproven(List<StatementEffects.Target> targets) {
        return targets.stream().filter(t->t.reasons().contains("UNPROVEN_BASE_SEPARATION")).count();
    }

    @Test void independentSingletonGroupsNeverFanOutWithOrWithoutRedundantPremise() {
        for(int n:List.of(2,4,8)) {
            var open=fixture(n,1,false);var closed=fixture(n,1,true);
            // The entire AIR publication is equal after removing the sole causal premise.
            assertEquals(open,replace(closed,closed.units(),closed.coverage(),closed.uncertainties(),List.of()));
            var openTargets=targets(open);var closedTargets=targets(closed);
            assertEquals(1,openTargets.size());assertEquals(0,unproven(openTargets));
            assertEquals(openTargets,closedTargets,"redundant separation does not become semantic evidence");
            assertEquals(1,closedTargets.size());assertEquals(0,unproven(closedTargets));
            for(var target:openTargets) {
                boolean direct=target.location().base().id().localId().equals("synthetic-region-0");
                assertEquals(direct,target.sourceApplicable());
                assertEquals(direct?StatementEffects.Strength.MUST:StatementEffects.Strength.MAY,target.strength());
            }
            for(boolean disjoint:List.of(false,true)) {
                var execution=run(disjoint?closed:open);var metrics=execution.preparationMetrics();
                assertEquals((long)n,metrics.get("basesIndexed"));
                assertEquals(disjoint?(long)n:0L,metrics.get("premiseMemberships"));
                assertEquals((long)n,metrics.get("correlationGroups"));assertEquals(1L,metrics.get("maxGroupBases"));
            }
            System.out.printf("W1_A regions=%d targets=%d unproven=%d disjointTargets=%d disjointUnproven=%d%n",
                n,openTargets.size(),unproven(openTargets),closedTargets.size(),unproven(closedTargets));
        }
    }

    /** Shape erases ONLY Part.producer. Every other Part field and extent is retained.
     * ByteImage has no codec: IBM remains fixed on fixture views; payload type stays explicit.
     */
    record ImageShape(Optional<BigInteger> extent,List<ByteImage.Part> parts) { }
    static ImageShape shape(ByteImage image) {
        return new ImageShape(image.extent(),image.parts().stream().map(p->new ByteImage.Part(
            p.range(),p.payload(),p.payloadOffset(),-1,p.producerOffset(),p.capturedOffsets(),p.reasons(),
            p.sourceGaps(),p.coInitial(),p.logicalSupports())).toList());
    }

    @Test void producerAlonePreventsInterningAndUnionCanonicalization() {
        var domain=new FactorizedAlternatives<ByteImage>();
        FactorizedAlternatives.Node<ByteImage> joined=null;
        var labels=new HashSet<ByteImage>();var shapes=new HashSet<ImageShape>();var hashes=new HashSet<Integer>();
        var first=ByteImage.unknown(Optional.of(BigInteger.valueOf(8)),"UNPROVEN_WRITE_DESTINATION",0);
        var firstNode=domain.node(0,Map.of(first,domain.terminal));
        for(int i=0;i<10;i++) {
            var image=ByteImage.unknown(first.extent(),"UNPROVEN_WRITE_DESTINATION",i);
            var node=domain.node(0,Map.of(image,domain.terminal));
            assertSame(node,domain.node(0,Map.of(ByteImage.unknown(first.extent(),"UNPROVEN_WRITE_DESTINATION",i),domain.terminal)));
            if(i>0){assertNotEquals(first,image);assertNotSame(firstNode,node);}
            labels.add(image);shapes.add(shape(image));hashes.add(image.hashCode());
            joined=domain.union(joined,node);
            assertEquals(i+1,joined.edges.size());assertEquals(i+1,domain.selections(joined).size());
            assertSame(joined,domain.union(joined,node));
        }
        assertEquals(10,labels.size());assertEquals(1,shapes.size());
        assertEquals(labels,joined.edges.keySet());
        // Hash cardinality is diagnostic only; collisions are legal and do not fail this test.
        System.out.printf("W1_B producers=10 labels=%d shapes=%d distinctHashes=%d alternatives=%d metrics=%s%n",
            labels.size(),shapes.size(),hashes.size(),FactorizedAlternatives.size(List.of(joined)).alternatives(),new TreeMap<>(domain.metrics()));
    }

    @Test void shapePreservesPayloadExtentReasonAndAllNonProducerMetadata() {
        var a=ByteImage.unknown(Optional.of(BigInteger.valueOf(8)),"reason-a",1);
        assertNotEquals(shape(a),shape(ByteImage.unknown(a.extent(),"reason-b",1)));
        assertNotEquals(shape(a),shape(ByteImage.unknown(Optional.of(BigInteger.valueOf(4)),"reason-a",1)));
        var bytes=ByteImage.literal(new Values.BytesValue(List.of(1,2,3,4)),1);
        assertEquals(shape(bytes),shape(ByteImage.literal(new Values.BytesValue(List.of(1,2,3,4)),2)));
        assertNotEquals(shape(bytes),shape(ByteImage.literal(new Values.BytesValue(List.of(1,2,3,5)),1)));
        assertNotEquals(shape(bytes),shape(bytes.copied(2)));
        assertNotEquals(shape(bytes),shape(bytes.withSourceGap(StorageRange.exact(BigInteger.ZERO,BigInteger.ONE),2)));
        assertNotEquals(shape(bytes),shape(bytes.withLogicalSupport(Set.of(new ByteImage.LogicalSupport(object(0),2)))));
    }

    record Composition(long targets,long unproven,long alternatives,long nodes,long crossLabels,long crossShapes,
                       long internedNodes,long internedEdges) { }

    /** On cross-base unknown alternatives only, normalize the operation identity and
     * its operand owner. Keep all event metadata and the full interpreted alternative.
     */
    static StorageValueFact.Alternative crossShape(StorageValueFact.Alternative alternative) {
        return new StorageValueFact.Alternative(alternative.interpretation(),alternative.candidate(),
            alternative.fragments().stream().map(f->new StorageValueFact.Fragment(f.location(),f.kind(),f.bytes(),
                f.producer(),f.unknownWriter().map(RegionalExplosionFixturesTest::writerShape),f.captures(),f.sourceGaps(),f.modelReasons(),f.logicalCapture())).toList());
    }
    static io.github.gustavo2358.analysis.rd.DefinitionEvent writerShape(io.github.gustavo2358.analysis.rd.DefinitionEvent event) {
        var normalized=new OperationId(event.operation().orElseThrow().unit(),"shape-producer");
        var destination=event.destination().map(id->{
            assertEquals(new OperationOwner(event.operation().orElseThrow()),id.owner());
            return new OperandId(new OperationOwner(normalized),id.localId());
        });
        return new io.github.gustavo2358.analysis.rd.DefinitionEvent(event.entry(),Optional.of(normalized),destination,
            event.slot(),event.outcome(),event.storage(),event.kind(),event.unknown(),event.origin(),event.premises(),
            event.uncertainties(),event.reasons(),event.logicalObject());
    }
    static Composition composition(int producers,boolean disjoint) {
        var p=fixture(4,producers,disjoint);var preparedTargets=targets(p);
        var selected=session(p);var admission=RegionalValuesAnalysis.prepare(selected,StorageAnalysisMode.EXPERIMENTAL_PHYSICAL);
        assertEquals(RegionalValuesAnalysis.Status.ACCEPTED,admission.status());
        var analysis=admission.analysis().orElseThrow();var execution=analysis.execute();
        assertEquals(io.github.gustavo2358.analysis.solver.DataflowResult.Status.STABLE,execution.dataflow().status());
        var metrics=execution.solveMetrics();
        assertEquals(4L,execution.preparationMetrics().get("correlationGroups"));
        assertEquals(1L,execution.preparationMetrics().get("maxGroupBases"));
        // Direct transfer and the actual solver must agree on the materialized state size.
        var engine=analysis.new Engine();var state=engine.boundaries(selected).iterator().next().state();
        for(var write:p.units().getFirst().sequences().getFirst().instructions())state=engine.operation(state,write);
        assertEquals(state.materializedAlternatives(),metrics.get("maxStateAlternatives"));
        assertEquals(1,state.materializedAlternatives(),"only the written independent group changes");
        assertEquals(0L,metrics.get("maxProvenanceRows"));
        var labels=new HashSet<StorageValueFact.Alternative>();var shapes=new HashSet<StorageValueFact.Alternative>();
        var writerIds=new HashSet<String>();
        for(int base=1;base<4;base++) {
            var query=new io.github.gustavo2358.analysis.query.PointQuery<StorageSubject>(
                io.github.gustavo2358.analysis.query.ProgramPoint.before(new EntryId(U,"entry"),new OperationId(U,"return-s0")),
                new StorageSubject.NamedObject(object(base)));
            var batch=execution.observeStorage(List.of(query));
            assertEquals(io.github.gustavo2358.analysis.query.ObservationBatch.Status.COMPLETE,batch.status());
            var fact=batch.observations().getFirst().value();
            assertTrue(fact.modelValueRemainder());assertTrue(fact.candidates().isEmpty());
            assertEquals(1,fact.alternatives().size(),"untouched bases retain only their unspecified entry content");
            for(var alternative:fact.alternatives()) {
                assertEquals(1,alternative.fragments().size());
                var fragment=alternative.fragments().getFirst();
                if(!fragment.modelReasons().contains("UNPROVEN_WRITE_DESTINATION"))continue;
                assertEquals(StorageValueFact.FragmentKind.UNKNOWN_BYTES,fragment.kind());
                assertTrue(fragment.bytes().isEmpty());assertTrue(fragment.producer().isEmpty());
                assertTrue(fragment.unknownWriter().isPresent());
                writerIds.add(fragment.unknownWriter().orElseThrow().operation().orElseThrow().localId());
                labels.add(alternative);shapes.add(crossShape(alternative));
            }
        }
        assertEquals(0,writerIds.size());
        assertEquals(0,labels.size());assertEquals(0,shapes.size());
        // Strong own-base writes keep only the last literal producer.
        var own=at(execution,"return-s0",object(0));assertEquals(List.of("ABCDEFGH"),texts(own));
        assertEquals(List.of("synthetic-producer-"+(producers-1)),own.candidateSupports().getFirst().producers()
            .stream().map(support->support.evidence().localId()).toList());
        var result=new Composition(preparedTargets.size(),unproven(preparedTargets),metrics.get("maxExpandedAlternatives"),
            state.decisionNodes(),labels.size(),shapes.size(),metrics.get("internedNodes"),metrics.get("internedAlternatives"));
        System.out.printf("W1_C disjoint=%s producers=%d %s%n",disjoint,producers,result);return result;
    }

    @Test void repeatedIndependentWritesDoNotCreateCompensatingAlternatives() {
        var a=composition(1,true);var b=composition(1,false);
        var c=composition(5,true);var d=composition(5,false);
        assertEquals(a,b);assertEquals(c,d);
        for(var result:List.of(a,b,c,d)) {
            assertEquals(0,result.unproven());assertEquals(1,result.alternatives());
            assertEquals(0,result.crossLabels());assertEquals(0,result.crossShapes());
        }
        assertEquals(1,a.targets());assertEquals(5,c.targets());
    }

    @Test void repeatingOneEventDoesNotMimicFiveDistinctProducers() {
        var p=fixture(4,1,false);var selected=session(p);
        var analysis=RegionalValuesAnalysis.prepare(selected,StorageAnalysisMode.EXPERIMENTAL_PHYSICAL).analysis().orElseThrow();var engine=analysis.new Engine();
        var state=engine.boundaries(selected).iterator().next().state();
        var operation=p.units().getFirst().sequences().getFirst().instructions().getFirst();
        state=engine.operation(state,operation);var once=state;
        for(int i=1;i<5;i++)state=engine.operation(state,operation);
        assertTrue(engine.equivalent(once,state,new io.github.gustavo2358.analysis.solver.DomainWork()));
        assertEquals(1,state.materializedAlternatives());
    }

}
