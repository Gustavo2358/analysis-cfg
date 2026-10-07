package io.github.gustavo2358.analysis.solver;

import io.github.gustavo2358.analysis.structure.*;
import java.util.*;

/** Structural reachability and balanced frame shapes, with symbolic ancestor conditions. */
record ActivationModel(ContextView context,ActivationControl control,BooleanConditions conditions,
        Map<ActivationControl.Frame,Shape> shapes,Map<ActivationControl.Frame,List<Push>> parents,List<ProgramIndex.Node> rootTargets,List<ProgramIndex.Node> unwindTargets,int maxUnwind) {
    record Push(ActivationControl.Frame parent,ActivationControl.Frame symbol,int condition) { }
    record Point(int condition,List<ActivationControl.Move> moves) { }
    record Shape(ActivationControl.Frame frame,Map<ProgramIndex.Node,Point> points) { }
    void visitConditions(java.util.function.IntConsumer root) {
        parents.values().forEach(links->links.forEach(link->root.accept(link.condition())));
        shapes.values().forEach(shape->shape.points().values().forEach(point->root.accept(point.condition())));
    }
}
