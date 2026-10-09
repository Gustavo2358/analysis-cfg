package io.github.gustavo2358.analysis.solver;

import io.github.gustavo2358.analysis.structure.*;
import java.util.*;

/** Structural reachability and balanced frame shapes, with symbolic ancestor conditions. */
record ActivationModel(ContextView context,ActivationControl control,BooleanConditions conditions,
        Map<ActivationControl.Frame,Shape> shapes,Map<ActivationControl.Frame,List<Push>> parents,List<ProgramIndex.Node> rootTargets,List<ProgramIndex.Node> unwindTargets,int maxUnwind) {
    record Push(ActivationControl.Frame parent,ActivationControl.Frame symbol,int condition) { }
    record Point(int condition,List<ActivationControl.Move> moves) { }
    /** Formal pop interface is compiled once, never rediscovered over ordinary points
     * at each caller evaluation. returnSources is an internal immutable borrowed array. */
    record Shape(ActivationControl.Frame frame,Map<ProgramIndex.Node,Point> points,ProgramIndex.Node[] returnSources) {
        Shape(ActivationControl.Frame frame,Map<ProgramIndex.Node,Point> points){this(frame,points,popSources(points));}
        private static ProgramIndex.Node[] popSources(Map<ProgramIndex.Node,Point> points) {
            int count=0;for(var point:points.values())for(var move:point.moves())if(move.action()==ActivationControl.Action.POP){count++;break;}
            var sources=new ProgramIndex.Node[count];int position=0;
            for(var point:points.entrySet())for(var move:point.getValue().moves())if(move.action()==ActivationControl.Action.POP){sources[position++]=point.getKey();break;}
            return sources;
        }
    }
    void visitConditions(java.util.function.IntConsumer root) {
        parents.values().forEach(links->links.forEach(link->root.accept(link.condition())));
        shapes.values().forEach(shape->shape.points().values().forEach(point->root.accept(point.condition())));
    }
}
