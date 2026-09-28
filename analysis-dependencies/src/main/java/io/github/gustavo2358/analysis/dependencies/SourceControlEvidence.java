package io.github.gustavo2358.analysis.dependencies;

import java.util.*;
import io.github.gustavo2358.analysis.dependencies.source.QualifiedSourceDependencies.*;

/** Grounded source certificates without an unknown-control premise. No executable reachability. */
public final class SourceControlEvidence {
    private SourceControlEvidence() { }
    /** Nodes with at least one qualified alternative carrying an unknown-control premise. */
    public static Set<String> affected(UnitEvidence unit) {
        var hypothetical=new HashSet<String>();boolean changed;
        unit.proofs().stream().filter(p->p.kind().equals("CONTROL_POSSIBILITY")).forEach(p->hypothetical.add(p.id()));
        do{changed=false;for(var p:unit.proofs())if(p.dependencies().stream().anyMatch(hypothetical::contains))changed|=hypothetical.add(p.id());}while(changed);
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
        var dependentProofs=new HashMap<String,List<String>>();var hypothetical=new HashSet<String>();var queue=new ArrayDeque<String>();
        for(var p:unit.proofs()) {
            if(p.kind().equals("CONTROL_POSSIBILITY")&&hypothetical.add(p.id()))queue.add(p.id());
            for(var dependency:p.dependencies())dependentProofs.computeIfAbsent(dependency,k->new ArrayList<>()).add(p.id());
        }
        while(!queue.isEmpty())for(var id:dependentProofs.getOrDefault(queue.removeFirst(),List.of()))if(hypothetical.add(id))queue.addLast(id);
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
