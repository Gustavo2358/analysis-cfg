package io.github.gustavo2358.analysis.solver;

import io.github.gustavo2358.analysis.structure.*;
import java.util.*;

/** Decide unique boundary stacks by predicate reachability and a word-exclusion automaton. */
final class ActivationBoundaries {
    record Key(ActivationControl.Frame frame,int prefix) { }
    private record Visit(Key key,int condition,Visit previous) { }
    record Location(ActivationControl.Frame frame,ProgramIndex.Node node) { }
    static Location require(ActivationModel model,ProgramIndex.Node node) {
        List<ActivationControl.Frame> witness=null;ActivationControl.Frame owner=null;
        for(var shape:model.shapes().values()) {
            var point=shape.points().get(node);if(point==null)continue;
            var found=find(model,shape.frame(),point.condition(),null);
            if(found==null)continue;
            if(witness!=null||find(model,shape.frame(),point.condition(),found)!=null)
                throw new IllegalArgumentException("boundary requires a unique local context");
            witness=found;owner=shape.frame();
        }
        if(witness==null)throw new IllegalArgumentException("point is outside the selected reachable contexts");
        return new Location(owner,node);
    }
    private static List<ActivationControl.Frame> find(ActivationModel model,ActivationControl.Frame frame,int condition,List<ActivationControl.Frame> excluded) {
        var b=model.conditions();int checkpoint=b.size();
        try {
        var seen=new HashMap<Key,Integer>();var pending=new ArrayDeque<Visit>();
        int prefix=excluded==null?-1:0;
        var start=new Key(frame,prefix);seen.put(start,condition);pending.add(new Visit(start,condition,null));
        while(!pending.isEmpty()) {
            var visit=pending.removeFirst();var current=visit.key.frame();int position=visit.key.prefix();
            if(current==null) {
                if(b.atEmpty(visit.condition)==0||excluded!=null&&position==excluded.size())continue;
                var result=new ArrayList<ActivationControl.Frame>();
                for(var v=visit.previous;v!=null;v=v.previous)result.add(v.key.frame());
                Collections.reverse(result);return List.copyOf(result);
            }
            int nextPosition=position>=0&&position<excluded.size()&&excluded.get(position)==current?position+1:-1;
            for(var link:model.parents().getOrDefault(current,Map.of()).entrySet()) {
                var parent=link.getKey();int need=parent==null?b.atEmpty(visit.condition):b.restrict(visit.condition,parent.variable(),true);
                need=b.and(need,link.getValue());var key=new Key(parent,nextPosition);
                int old=seen.getOrDefault(key,0),extra=b.difference(need,old);if(extra==0)continue;
                seen.put(key,b.or(old,extra));pending.addLast(new Visit(key,extra,visit));
            }
        }
        return null;
        } finally {b.discardAfter(checkpoint);}
    }
}
