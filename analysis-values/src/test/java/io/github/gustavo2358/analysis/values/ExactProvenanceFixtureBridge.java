package io.github.gustavo2358.analysis.values;

import io.github.gustavo2358.air.model.Publication;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.query.*;
import io.github.gustavo2358.analysis.storage.*;
import java.lang.reflect.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.values.ValuesFixtures.*;
import static io.github.gustavo2358.analysis.values.RegionalValuesTest.*;

/** TEST ONLY reflection bridge: no production hooks. Read actual relation roots,
 * round-trip before each concrete transfer, and project the reconstructed final
 * state through the unchanged real observation code. Independent solver is oracle.
 */
final class ExactProvenanceFixtureBridge {
    record Result(List<StorageValueFact> before,List<StorageValueFact> after,ExactProvenancePrototype.Size compact,
                  long concreteEdges,int targets,long unproven,Map<String,Long> solveMetrics) { }
    private static Object field(Object owner,String name) throws ReflectiveOperationException {
        var field=owner.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(owner);
    }
    @SuppressWarnings("unchecked")
    static Result check(Publication publication,int regions) throws ReflectiveOperationException {
        var selected=session(publication);var analysis=RegionalValuesAnalysis.prepare(selected,StorageAnalysisMode.EXPERIMENTAL_PHYSICAL).analysis().orElseThrow();
        var execution=analysis.execute();var engine=analysis.new Engine();
        var production=(RegionalAlternatives<Object>)field(engine,"relations");
        var domain=new FactorizedAlternatives<Object>();
        var bytes=Class.forName(RegionalValuesAnalysis.class.getName()+"$Bytes");
        var makeBytes=bytes.getDeclaredConstructor(ByteImage.class);makeBytes.setAccessible(true);
        var image=bytes.getDeclaredMethod("image");image.setAccessible(true);
        var prototype=new ExactProvenancePrototype<>(domain,new ExactProvenancePrototype.Codec<Object>() {
            public ByteImage image(Object label) {
                try {return bytes.isInstance(label)?(ByteImage)image.invoke(label):null;}
                catch(ReflectiveOperationException e){throw new AssertionError(e);}
            }
            public Object label(ByteImage value) {
                try {return makeBytes.newInstance(value);}
                catch(ReflectiveOperationException e){throw new AssertionError(e);}
            }
        });
        var state=engine.boundaries(selected).iterator().next().state();
        for(var operation:publication.units().getFirst().sequences().getFirst().instructions())
            state=engine.operation(roundTrip(state,prototype,production,domain),operation);
        state=roundTrip(state,prototype,production,domain);
        var bindings=(SegmentMap<RegionalAlternatives.Node<Object>>)field(state,"bindings");
        var roots=new ArrayList<ExactProvenancePrototype.Node<Object>>();bindings.forEach((key,value)->roots.add(prototype.factor(production.expand(value,domain))));
        var replayConstructor=execution.getClass().getDeclaredConstructors()[0];replayConstructor.setAccessible(true);
        var replay=replayConstructor.newInstance(analysis,engine,execution.dataflow());
        var fact=execution.getClass().getDeclaredMethod("fact",PointQuery.class,RegionalValuesAnalysis.State.class);fact.setAccessible(true);
        var before=new ArrayList<StorageValueFact>();var after=new ArrayList<StorageValueFact>();
        for(int i=0;i<regions;i++) {
            var query=new PointQuery<StorageSubject>(ProgramPoint.before(new EntryId(U,"entry"),new OperationId(U,"return-s0")),
                new StorageSubject.NamedObject(RegionalExplosionFixturesTest.object(i)));
            var observed=execution.observeStorage(List.of(query));assertEquals(ObservationBatch.Status.COMPLETE,observed.status());
            before.add(observed.observations().getFirst().value());after.add((StorageValueFact)fact.invoke(replay,query,state));
        }
        assertEquals(before,after,"every typed fact, independent solver vs expanded replay");
        assertEquals(RegionalSemanticSnapshot.encode(before),RegionalSemanticSnapshot.encode(after));
        var targets=RegionalExplosionFixturesTest.targets(publication);
        return new Result(List.copyOf(before),List.copyOf(after),prototype.size(roots),prototype.size(roots).expandedEdges(),
            targets.size(),RegionalExplosionFixturesTest.unproven(targets),execution.solveMetrics());
    }
    @SuppressWarnings("unchecked")
    private static RegionalValuesAnalysis.State roundTrip(RegionalValuesAnalysis.State state,ExactProvenancePrototype<Object> prototype,RegionalAlternatives<Object> production,FactorizedAlternatives<Object> domain) throws ReflectiveOperationException {
        var bindings=(SegmentMap<RegionalAlternatives.Node<Object>>)field(state,"bindings");
        var result=new ArrayList<SegmentMap<RegionalAlternatives.Node<Object>>>();result.add(new SegmentMap<>());
        bindings.forEach((key,root)->{
            var concrete=production.expand(root,domain);var expanded=prototype.expand(prototype.factor(concrete));assertSame(concrete,expanded,"exact canonical concrete root, not a shape comparison");
            var factored=production.factor(expanded);assertSame(root,factored,"canonical production root");
            result.set(0,result.getFirst().put(key,factored));
        });
        var constructor=RegionalValuesAnalysis.State.class.getDeclaredConstructors()[0];constructor.setAccessible(true);
        return (RegionalValuesAnalysis.State)constructor.newInstance(field(state,"entry"),result.getFirst(),field(state,"logical"),field(state,"groupSizes"));
    }
}
