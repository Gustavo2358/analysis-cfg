package io.github.gustavo2358.analysis.solver;

import java.util.ArrayList;
import java.util.List;

/** A partial function from Boolean environments to abstract states, canonicalized
 * into disjoint guards for distinct equivalent-state classes. Both solver directions
 * use the same pointwise join; missing environments remain uninitialized, even at bottom. */
class GuardedStates<S> {
    static final class Piece<S> {
        final int condition;final S state;long root;
        Piece(int condition,S state){this.condition=condition;this.state=state;}
    }
    private final BooleanConditions conditions;
    private final AnalysisDefinition<S> definition;
    private final DomainWork work;
    private int domain;
    private long domainRoot;
    List<Piece<S>> pieces=List.of();
    GuardedStates(BooleanConditions conditions,AnalysisDefinition<S> definition,DomainWork work) {
        this.conditions=conditions;this.definition=definition;this.work=work;
    }
    boolean add(int condition,S contribution) {
        if(condition==0)return false;
        int checkpoint=conditions.checkpoint();boolean committed=false;
        try {var previous=pieces;boolean changed=merge(condition,contribution);
            if(changed&&conditions.ownershipEnabled()){
                for(var piece:pieces)if(piece.root==0)piece.root=conditions.retainRoot(piece.condition);
                if(domainRoot==0)domainRoot=conditions.retainRoot(domain);else conditions.bindRoot(domainRoot,domain);
                for(var piece:previous)if(piece.root!=0)conditions.releaseRoot(piece.root);
            }
            conditions.commitAfter(checkpoint,this::visitConditions);committed=true;return changed;}
        finally {if(!committed)conditions.discardAfter(checkpoint);}
    }
    private boolean merge(int condition,S contribution) {
        // Joining equal values is union of their domains. Partitioning that union
        // into old-only/new-only/intersection constructs conditions with no semantic use.
        if(pieces.size()==1&&definition.equivalent(pieces.getFirst().state,contribution,work)) {
            var old=pieces.getFirst();int combined=conditions.or(old.condition,condition);
            if(combined==old.condition)return false;
            pieces=List.of(new Piece<>(combined,old.state));domain=combined;return true;
        }
        int combinedDomain=conditions.or(domain,condition),excluded=0;
        boolean modified=combinedDomain!=domain;var next=new ArrayList<Piece<S>>();
        for(var piece:pieces) {
            if(definition.equivalent(piece.state,contribution,work)){put(next,piece.condition,piece.state);continue;}
            var joined=definition.joinInto(piece.state,contribution,work);
            boolean supplied=definition.equivalent(joined.state(),contribution,work);
            if(!supplied)excluded=conditions.or(excluded,piece.condition);
            if(!joined.changed()){put(next,piece.condition,piece.state);continue;}
            int outside=conditions.difference(piece.condition,condition);
            modified|=outside!=piece.condition;put(next,outside,piece.state);
            // When the joined value equals the supplied class, its overlap is already
            // covered by the supplied guard. Only genuinely different results need a split.
            if(!supplied)put(next,conditions.and(piece.condition,condition),joined.state());
        }
        put(next,conditions.difference(condition,excluded),contribution);
        if(modified){pieces=List.copyOf(next);domain=combinedDomain;}return modified;
    }
    void clear(){
        if(domainRoot!=0){conditions.releaseRoot(domainRoot);domainRoot=0;}
        for(var piece:pieces)if(piece.root!=0)conditions.releaseRoot(piece.root);
        pieces=List.of();domain=0;
    }
    void visitConditions(java.util.function.IntConsumer root) {
        root.accept(domain);for(var piece:pieces)root.accept(piece.condition);
    }
    private void put(List<Piece<S>> result,int condition,S value) {
        if(condition==0)return;
        for(int i=0;i<result.size();i++)if(definition.equivalent(result.get(i).state,value,work)) {
            result.set(i,new Piece<>(conditions.or(result.get(i).condition,condition),value));return;
        }
        result.add(new Piece<>(condition,value));
    }
}
