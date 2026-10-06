package io.github.gustavo2358.analysis.solver;

import io.github.gustavo2358.analysis.structure.*;
import java.util.*;

/** Structural reachability and balanced frame shapes, with symbolic ancestor conditions. */
record ActivationModel(ContextView context,ActivationControl control,BooleanConditions conditions,int empty,
        Map<ActivationControl.Frame,Shape> shapes,Map<ActivationControl.Frame,Map<ActivationControl.Frame,Integer>> parents,Map<ActivationControl.Frame,Integer> environments,List<ProgramIndex.Node> rootTargets,List<ProgramIndex.Node> unwindTargets,int maxUnwind) {
    record Point(int condition,List<ActivationControl.Move> moves) { }
    record Shape(ActivationControl.Frame frame,Map<ProgramIndex.Node,Point> points) { }
    void visitConditions(java.util.function.IntConsumer root) {
        root.accept(empty);environments.values().forEach(root::accept);
        parents.values().forEach(links->links.values().forEach(root::accept));
        shapes.values().forEach(shape->shape.points().values().forEach(point->root.accept(point.condition())));
    }
}
