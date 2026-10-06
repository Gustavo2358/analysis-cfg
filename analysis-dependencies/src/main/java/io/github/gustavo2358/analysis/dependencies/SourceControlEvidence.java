package io.github.gustavo2358.analysis.dependencies;

import java.util.*;
import io.github.gustavo2358.analysis.dependencies.source.QualifiedSourceDependencies.*;

/** Grounded source certificates without an unknown-control premise. No executable reachability. */
public final class SourceControlEvidence {
    private SourceControlEvidence() { }
    record ProofClosure(Set<String> ids,long proofVisits,long edgeVisits) { }
    /** Exact seeded proof reachability; no index is retained when there is no seed. */
    static ProofClosure hypotheticalProofs(List<Proof> proofs) {
        var hypothetical=new HashSet<String>();long proofVisits=0,edgeVisits=0;
        for(var proof:proofs){proofVisits++;if(proof.kind().equals("CONTROL_POSSIBILITY"))hypothetical.add(proof.id());}
        if(hypothetical.isEmpty())return new ProofClosure(Set.of(),proofVisits,0);
        var dependents=new HashMap<String,List<String>>();
        for(var proof:proofs) {
            proofVisits++;
            for(var dependency:proof.dependencies()){edgeVisits++;dependents.computeIfAbsent(dependency,k->new ArrayList<>()).add(proof.id());}
        }
        var queue=new ArrayDeque<>(hypothetical);
        while(!queue.isEmpty())for(var id:dependents.getOrDefault(queue.removeFirst(),List.of())) {
            edgeVisits++;if(hypothetical.add(id))queue.addLast(id);
        }
        return new ProofClosure(Collections.unmodifiableSet(hypothetical),proofVisits,edgeVisits);
    }
    /** Nodes with at least one qualified alternative carrying an unknown-control premise. */
    public static Set<String> affected(UnitEvidence unit) {
        var hypothetical=hypotheticalProofs(unit.proofs()).ids();
        if(hypothetical.isEmpty())return Set.of();
        var waiting=new HashMap<String,List<String>>();var result=new HashSet<String>();var queue=new ArrayDeque<String>();
        for(var d:unit.derivations()) {
            if(d.proofs().stream().anyMatch(hypothetical::contains)&&result.add(d.destination()))queue.add(d.destination());
            var premises=new HashSet<>(d.source());premises.addAll(d.callerPremise());
            for(var p:premises)waiting.computeIfAbsent(p,k->new ArrayList<>()).add(d.destination());
        }
        while(!queue.isEmpty())for(var destination:waiting.getOrDefault(queue.removeFirst(),List.of()))if(result.add(destination))queue.add(destination);
        return Set.copyOf(result);
    }
    public static Set<String> assumedOnly(UnitEvidence unit) {
        var hypothetical=hypotheticalProofs(unit.proofs()).ids();
        var queue=new ArrayDeque<String>();
        if(hypothetical.isEmpty())return Set.of();
        var waiting=new HashMap<String,List<Derivation>>();var remaining=new HashMap<String,Integer>();var proved=new HashSet<String>();
        for(var d:unit.derivations()) {
            if(d.proofs().stream().anyMatch(hypothetical::contains))continue;
            var premises=new HashSet<>(d.source());premises.addAll(d.callerPremise());remaining.put(d.id(),premises.size());
            if(premises.isEmpty()) {if(proved.add(d.destination()))queue.add(d.destination());}
            else for(var p:premises)waiting.computeIfAbsent(p,k->new ArrayList<>()).add(d);
        }
        while(!queue.isEmpty())for(var d:waiting.getOrDefault(queue.removeFirst(),List.of()))
            if(remaining.compute(d.id(),(k,n)->n-1)==0&&proved.add(d.destination()))queue.addLast(d.destination());
        var result=new HashSet<String>();for(var n:unit.nodes())if(!proved.contains(n.id()))result.add(n.id());return Set.copyOf(result);
    }
}
