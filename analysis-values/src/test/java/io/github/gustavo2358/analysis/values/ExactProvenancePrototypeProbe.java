package io.github.gustavo2358.analysis.values;

import java.nio.file.*;
import java.util.*;

/** Manual diagnostic, not a timed test. Separates real-fixture equivalence from
 * a bounded weak-unknown union microbenchmark; does NOT claim a factored solver.
 */
public final class ExactProvenancePrototypeProbe {
    private ExactProvenancePrototypeProbe() { }
    private record Trial(long buildNs,long expandNs,long historyEdges,long liveEdges,long rows,long expandedLabels,long fallbacks) { }
    private static Trial trial(int regions,int producers,boolean compact) {
        long start=System.nanoTime();var d=new FactorizedAlternatives<ByteImage>();
        var p=ExactProvenancePrototypeTest.prototype(d);
        var originals=new ArrayList<FactorizedAlternatives.Node<ByteImage>>();
        var factored=new ArrayList<ExactProvenancePrototype.Node<ByteImage>>();
        for(int r=1;r<regions;r++) {
            var initial=d.node(r,Map.of(ByteImage.unknown(Optional.of(java.math.BigInteger.valueOf(8)),"UNSPECIFIED_ENTRY_CONTENT"),d.terminal));
            if(compact) {
                var root=p.factor(initial);
                for(int i=0;i<producers;i++)root=p.union(root,p.factor(d.node(r,Map.of(ExactProvenancePrototypeTest.unknown(r*producers+i),d.terminal))));
                factored.add(root);
            } else {
                var root=initial;
                for(int i=0;i<producers;i++)root=d.union(root,d.node(r,Map.of(ExactProvenancePrototypeTest.unknown(r*producers+i),d.terminal)));
                originals.add(root);
            }
        }
        long build=System.nanoTime()-start;
        if(compact&&p.expandedLabels!=0)throw new AssertionError("hot-path union unexpectedly exploded");
        start=System.nanoTime();if(compact)for(var root:factored)originals.add(p.expand(root));long expand=compact?System.nanoTime()-start:0;
        // Validate every complete label after the timed work (same reason/range, exact event IDs).
        for(int r=1;r<regions;r++) {
            var expected=new HashSet<ByteImage>();expected.add(ByteImage.unknown(Optional.of(java.math.BigInteger.valueOf(8)),"UNSPECIFIED_ENTRY_CONTENT"));
            for(int i=0;i<producers;i++)expected.add(ExactProvenancePrototypeTest.unknown(r*producers+i));
            if(!expected.equals(originals.get(r-1).edges.keySet()))throw new AssertionError("benchmark changed labels");
        }
        var size=compact?p.size(factored):null;
        return new Trial(build,expand,compact?p.internedStructuralEdges():d.metrics().get("internedAlternatives"),
            compact?size.structuralEdges():FactorizedAlternatives.size(originals).alternatives(),compact?size.provenanceRows():0,p.expandedLabels,p.fallbackCalls);
    }
    public static void main(String[] args) throws Exception {
        int regions=Integer.parseInt(args[0]),producers=Integer.parseInt(args[1]),warmups=Integer.parseInt(args[2]),repeats=Integer.parseInt(args[3]);
        if(repeats<=0||regions<2||producers<=0||warmups<0)throw new IllegalArgumentException("invalid diagnostic dimensions");
        var output=Path.of(args[4]);Files.createDirectories(output);
        for(int i=0;i<warmups;i++){trial(regions,producers,false);trial(regions,producers,true);}
        var results=new StringBuilder("mode,buildNs,expandNs,historyStructuralEdges,liveStructuralEdges,provenanceRows,expandedLabels,fallbacks\n");
        for(int i=0;i<repeats;i++)for(boolean compact:(i%2==0?List.of(false,true):List.of(true,false))) {
            var t=trial(regions,producers,compact);
            results.append(compact?"factored":"concrete").append(',').append(t.buildNs()).append(',').append(t.expandNs()).append(',')
                .append(t.historyEdges()).append(',').append(t.liveEdges()).append(',').append(t.rows()).append(',').append(t.expandedLabels()).append(',').append(t.fallbacks()).append('\n');
        }
        Files.writeString(output.resolve("trials.csv"),results);
        // Real solver + replay + full observations, separately from the microbenchmark above.
        var publication=RegionalExplosionFixturesTest.fixture(regions,producers,false);
        var result=ExactProvenanceFixtureBridge.check(publication,regions);
        Files.writeString(output.resolve("before.typed"),RegionalSemanticSnapshot.encode(result.before()));
        Files.writeString(output.resolve("facts.typed"),RegionalSemanticSnapshot.encode(result.after()));
        Files.writeString(output.resolve("targets.typed"),RegionalSemanticSnapshot.encode(RegionalExplosionFixturesTest.targets(publication)));
        System.out.printf("W32_PROTOTYPE regions=%d producers=%d concrete=%d factored=%s targets=%d unproven=%d facts=%s solve=%s%n",
            regions,producers,result.concreteEdges(),result.compact(),result.targets(),result.unproven(),RegionalSemanticSnapshot.digest(result.after()),new TreeMap<>(result.solveMetrics()));
        System.out.println("W32_BENCHMARK alternating trials in trials.csv; cross-base unknown union only; no factored production solver");
    }
}
