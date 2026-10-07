package io.github.gustavo2358.analysis.solver;

import io.github.gustavo2358.analysis.structure.ActivationControl;
import io.github.gustavo2358.analysis.structure.ProgramIndex;
import java.util.*;

/** Typed unconditioned call superset, compiled before any symbolic guard saturation. */
final class PreliminaryCallerSupport implements AutoCloseable {
    private record Push(int parent,int child,int variable) { }
    private final Map<ActivationControl.Frame,Integer> ordinals=new IdentityHashMap<>();
    private AnalysisResources.Reservation metadata;
    private ResidentPageStore pages;
    private PersistentGraphClosure closure;
    PreliminaryCallerSupport(ActivationControl control,AnalysisResources resources) {
        try {
            var frames=new ArrayList<ActivationControl.Frame>();frames.add(null);ordinals.put(null,0);
            var pushes=new ArrayList<Push>();var unwind=control.positiveUnwindLandings();
            for(int ordinal=0;ordinal<frames.size();ordinal++) {
                var frame=frames.get(ordinal);var pending=new ArrayDeque<ProgramIndex.Node>();pending.add(control.entry(frame));pending.addAll(unwind);
                if(frame==null)pending.addAll(control.resetLandings());
                var seen=Collections.newSetFromMap(new IdentityHashMap<ProgramIndex.Node,Boolean>());
                while(!pending.isEmpty()) {
                    var node=pending.removeFirst();if(!seen.add(node))continue;
                    for(var move:control.moves(node,frame)) {
                        if(move.action()==ActivationControl.Action.NEXT||frame==null&&move.action()==ActivationControl.Action.ROOT)pending.addLast(move.destination());
                        else if(move.action()==ActivationControl.Action.CALL) {
                            var child=control.body(move.frame());var index=ordinals.get(child);
                            if(index==null){index=frames.size();ordinals.put(child,index);frames.add(child);}
                            pushes.add(new Push(ordinal,index,move.frame().variable()));pending.addAll(control.continuations(move.frame()));
                        }
                    }
                }
            }
            int bodies=frames.size(),bindings=pushes.size();int[] counts=new int[bodies];
            for(var push:pushes)counts[push.child()]++;
            int[] variables=new int[Math.addExact(bodies,bindings)];Arrays.fill(variables,-1);int[][] incoming=new int[variables.length][];
            for(int i=0;i<bodies;i++)incoming[i]=new int[counts[i]];Arrays.fill(counts,0);
            for(int i=0;i<bindings;i++) {
                var push=pushes.get(i);int binding=bodies+i;variables[binding]=push.variable();
                incoming[push.child()][counts[push.child()]++]=binding;incoming[binding]=new int[]{push.parent()};
            }
            metadata=resources.reserve(AnalysisResources.Pool.RESIDENT,256+48L*bodies,AnalysisResources.Phase.CONTROL);
            pages=new ResidentPageStore(4096,resources,AnalysisResources.Phase.CONTROL);
            closure=new PersistentGraphClosure(pages,resources,AnalysisResources.Phase.CONTROL,variables,incoming);
        }catch(RuntimeException|Error failure){try{close();}catch(RuntimeException|Error cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}throw failure;}
    }
    boolean mayContain(ActivationControl.Frame frame,int variable) {
        if(frame==null)return false;var index=ordinals.get(frame);
        if(index==null)throw new IllegalArgumentException("body missing from conservative caller graph");return closure.contains(index,variable);
    }
    @Override public void close(){try{if(closure!=null)closure.close();}finally{try{if(pages!=null)pages.close();}finally{if(metadata!=null)metadata.close();ordinals.clear();}}}
}
