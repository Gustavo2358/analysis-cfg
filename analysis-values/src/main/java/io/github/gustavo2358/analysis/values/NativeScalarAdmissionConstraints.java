package io.github.gustavo2358.analysis.values;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.structure.*;
import io.github.gustavo2358.analysis.storage.KillAuthority;
import java.util.*;

/** Native decision-only compilation. Graph/ranks/edges/initial addresses use
 * owned columns; this is not all-store coverage. Existing
 * transient TextExpressions.reads, typed payload decoding and a separately funded
 * first-seen Entry-unit set remain explicit resident/paging debt. */
final class NativeScalarAdmissionConstraints implements PossibleValuesAnalysis.AdmissionDecisions {
    private enum Reason {
        UNSUPPORTED_DEMAND_SOURCE, UNSUPPORTED_DEMAND_STORAGE,
        UNSUPPORTED_EFFECT_PROFILE, UNSUPPORTED_STORAGE_PROFILE,
        UNPROVED_SCALAR_OPERATION_PRECONDITION, UNPROVED_STRONG_OVERWRITE,
        UNSUPPORTED_INITIAL_PLACE, UNSUPPORTED_INITIAL_STORAGE,
        UNSUPPORTED_INITIAL_VALUE, CONTRADICTORY_INITIAL_VALUES, UNSUPPORTED_EFFECT_PLACE
    }
    private AnalysisSession session;
    private ProgramStore.StorageInventory storage;
    private Map<ObjectId,TextProfile.Location> subjects;
    private final ProgramStore.CapacityLease capacity;
    private final ProgramStore.OrdinalColumn[] columns=new ProgramStore.OrdinalColumn[10];
    private final boolean effectAware;
    private int owned;
    private boolean closed;
    private ScalarConstraintIndex graph;
    private ProgramStore.OrdinalColumn ranks,degree,offset,from,to,edge,cursor,branches,initialEpoch,initialOrdinal;
    private long global=Long.MAX_VALUE,common=Long.MAX_VALUE,position=256;
    public int graphBuilds;
    public long assignCopyPassVisits,operationGuardVisits,stagedEdges;
    static NativeScalarAdmissionConstraints open(AnalysisSession session,String profile) {
        Objects.requireNonNull(session);
        if(!PossibleValuesAnalysis.PROFILE.equals(profile)&&!PossibleValuesAnalysis.EFFECTS_PROFILE.equals(profile))
            throw new IllegalArgumentException("native decision compiler requires an existing scalar profile");
        var store=session.index().store();var budget=store.executionBudget().orElseThrow(()->new IllegalArgumentException("native owner required"));
        var inventory=store.storageInventory().orElseThrow(()->new IllegalArgumentException("native storage ordinals required"));
        if(store.operandDirectory().isEmpty())throw new IllegalArgumentException("native operand addresses required");
        var lease=budget.reserve(ProgramStore.CapacityPool.RESIDENT,1024,ProgramStore.ExecutionPhase.DEMAND);
        NativeScalarAdmissionConstraints result=null;
        try {
            result=new NativeScalarAdmissionConstraints(session,inventory,EFFECTS(profile),lease);
            lease.onOwnerClose(result::close);return result;
        } catch(RuntimeException|Error failure){try{if(result!=null)result.close();else lease.close();}catch(RuntimeException|Error cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}throw failure;}
    }
    private static boolean EFFECTS(String profile){return PossibleValuesAnalysis.EFFECTS_PROFILE.equals(profile);}
    private NativeScalarAdmissionConstraints(AnalysisSession session,ProgramStore.StorageInventory storage,boolean effectAware,ProgramStore.CapacityLease capacity) {
        this.session=session;this.storage=storage;this.effectAware=effectAware;this.capacity=capacity;
        subjects=new AbstractMap<>() {
            @Override public TextProfile.Location get(Object key) {
                available();if(!(key instanceof ObjectId id))return null;
                var index=NativeScalarAdmissionConstraints.this.session.index();var object=index.object(id);
                var base=object!=null&&object.storage() instanceof Memory.CellBinding binding?index.storage(binding.storage()):null;
                var cell=ScalarStorageAdmission.cell(object,base);if(cell==null)return null;
                int ordinal=NativeScalarAdmissionConstraints.this.storage.ordinal(cell.header().id());
                if(ordinal<0||ordinal>=NativeScalarAdmissionConstraints.this.storage.size())throw new IllegalStateException("supported Cell outside admitted storage inventory");
                return new TextProfile.Location(ordinal,cell);
            }
            @Override public boolean containsKey(Object key){return get(key)!=null;}
            @Override public Set<Map.Entry<ObjectId,TextProfile.Location>> entrySet() {
                available();return new AbstractSet<>() {
                    @Override public int size(){int count=0;for(var ignored:this)count=Math.incrementExact(count);return count;}
                    @Override public Iterator<Map.Entry<ObjectId,TextProfile.Location>> iterator() {
                        available();var declarations=NativeScalarAdmissionConstraints.this.session.index().objectDeclarations().entrySet().iterator();
                        return new Iterator<>() {
                            private Map.Entry<ObjectId,TextProfile.Location> next;
                            @Override public boolean hasNext(){available();while(next==null&&declarations.hasNext()){var item=declarations.next();var location=get(item.getKey());if(location!=null)next=new SimpleImmutableEntry<>(item.getKey(),location);}return next!=null;}
                            @Override public Map.Entry<ObjectId,TextProfile.Location> next(){if(!hasNext())throw new NoSuchElementException();var result=next;next=null;return result;}
                        };
                    }
                };
            }
        };
    }
    private void available(){if(closed)throw new IllegalStateException("closed native decision compiler");session.index().store().progress(ProgramStore.ExecutionPhase.DEMAND);}
    private ProgramStore.OrdinalColumn column(long length){if(owned>=columns.length)throw new IllegalStateException("native decision column inventory exceeded");var value=storage.column(length);columns[owned++]=value;return value;}
    @Override public PossibleValuesAnalysis.AdmissionDecision decide(ObjectId subject) {
        available();var root=subjects.get(Objects.requireNonNull(subject));
        if(root==null)return refusal(Reason.UNSUPPORTED_DEMAND_STORAGE);
        if(graph==null)compile();
        long rank=Math.min(global,Math.min(common,graph.minimum(root.ordinal())));
        return rank==Long.MAX_VALUE?new PossibleValuesAnalysis.AdmissionDecision(PossibleValuesAnalysis.Status.ACCEPTED,null,0,0)
            :refusal(Reason.values()[Math.toIntExact(rank%256)]);
    }
    private static PossibleValuesAnalysis.AdmissionDecision refusal(Reason reason) {
        var name=reason.name();return new PossibleValuesAnalysis.AdmissionDecision(reason==Reason.CONTRADICTORY_INITIAL_VALUES?PossibleValuesAnalysis.Status.INVALID_INPUT:PossibleValuesAnalysis.Status.UNSUPPORTED,name,name.contains("STORAGE")?1:0,name.contains("EFFECT")?1:0);
    }
    private long slot(){long current=position;position=Math.addExact(position,256);return current;}
    private static long rank(long slot,TextProfile.Refusal refusal){return Math.addExact(slot,Reason.valueOf(refusal.getMessage()).ordinal());}
    private void refuse(int vertex,long value){ranks.set(vertex,Math.min(ranks.get(vertex),value));}
    private void compile() {
        global=common=Long.MAX_VALUE;position=256;stagedEdges=assignCopyPassVisits=operationGuardVisits=0;
        ScalarConstraintIndex built=null;
        try {
            var index=session.index();int n=storage.size();long maximum=index.store().operandDirectory().orElseThrow().size();
            ranks=column(n);degree=column(n);offset=column((long)n+1);from=column(maximum);to=column(maximum);cursor=column(n);branches=column(n);
            for(int v=0;v<n;v++){available();ranks.set(v,Long.MAX_VALUE);degree.set(v,0);branches.set(v,0);}
            var unitCapacity=index.store().executionBudget().orElseThrow().reserve(ProgramStore.CapacityPool.SCRATCH,128,ProgramStore.ExecutionPhase.DEMAND);
            try(unitCapacity) {
            var units=new LinkedHashSet<UnitId>();unitCapacity.onOwnerClose(units::clear);
            try {
            for(var context:session.contexts()) {
                var unit=context.entryView().id().unit();
                if(!units.contains(unit)){unitCapacity.grow(128,ProgramStore.ExecutionPhase.DEMAND);units.add(unit);}
            }
            for(var unit:units)for(var site:index.sites(Operations.Branch.class,unit))
                for(var object:TextPredicate.reads(((Operations.Branch)site.operation()).predicate())) {
                    var location=subjects.get(object);if(location!=null)branches.set(location.ordinal(),1);
                }
            for(var unit:units)for(var site:index.sites(Operations.Assign.class,unit)) {
                available();assignCopyPassVisits=Math.incrementExact(assignCopyPassVisits);var assign=(Operations.Assign)site.operation();
                if(assign.destination() instanceof Places.ObjectPlace destination) {
                    var target=subjects.get(destination.object());
                    try {
                        var reads=TextExpressions.reads(assign.value());
                        if(target!=null)for(var object:reads) {
                            var source=subjects.get(object);
                            if(source==null)refuse(target.ordinal(),0);
                            else {
                                if(stagedEdges>=maximum)throw new IllegalStateException("read edges exceed admitted operand occurrence bound");
                                from.set(stagedEdges,target.ordinal());to.set(stagedEdges,source.ordinal());stagedEdges++;
                                degree.set(target.ordinal(),Math.incrementExact(degree.get(target.ordinal())));
                            }
                        }
                    } catch(TextProfile.Refusal ignored) { }
                }
            }
            long total=0;
            for(int v=0;v<n;v++){available();offset.set(v,total);cursor.set(v,total);total=Math.addExact(total,degree.get(v));}
            offset.set(n,total);if(total!=stagedEdges)throw new IllegalStateException("staged edge accounting mismatch");edge=column(total);
            for(long at=0;at<total;at++){available();long target=from.get(at),place=cursor.get(target);edge.set(place,to.get(at));cursor.set(target,place+1);}
            for(var unit:units)for(var sequence:index.unit(unit).sequences()) {
                for(var instruction:sequence.instructions())operation(instruction);
                operation(sequence.terminator());
            }
            initial(n);
            built=ScalarConstraintIndex.open(index.store(),n,v->Math.toIntExact(degree.get(v)),(v,at)->Math.toIntExact(edge.get(offset.get(v)+at)),v->ranks.get(v));
            for(int v=0;v<n;v++)if(branches.get(v)!=0)common=Math.min(common,built.minimum(v));
            graph=built;graphBuilds=Math.incrementExact(graphBuilds);
            } finally {units.clear();}
            }
        } catch(RuntimeException|Error failure) {
            if(built!=null)try{built.close();}catch(RuntimeException|Error cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}
            try{retireColumns();}catch(RuntimeException|Error cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}throw failure;
        }
    }
    private void initial(int n) {
        long epoch=0;
        for(var context:session.contexts()) {
            var conditions=context.entryView().state().conditions();epoch=Math.incrementExact(epoch);
            if(conditions.isEmpty())continue;
            if(initialEpoch==null){initialEpoch=column(n);initialOrdinal=column(n);for(int v=0;v<n;v++)initialEpoch.set(v,0);}
            for(boolean literal:new boolean[]{true,false})for(int row=0;row<conditions.size();row++) {
                var condition=conditions.get(row);if(condition.literal()!=literal)continue;available();long at=slot();
                if(!(condition.place() instanceof Places.ObjectPlace named)){global=Math.min(global,at+Reason.UNSUPPORTED_INITIAL_PLACE.ordinal());continue;}
                var location=subjects.get(named.object());
                if(location==null) {
                    if(condition.kind()==ProgramStore.InitialKind.EXTERNAL_UNKNOWN) {
                        var object=session.index().object(named.object());var cell=session.index().directCell(named.object());
                        if(object!=null&&object.storage() instanceof Memory.CellBinding&&cell!=null&&bool(object.typeRef())&&bool(cell.typeRef()))continue;
                    }
                    global=Math.min(global,at+Reason.UNSUPPORTED_INITIAL_STORAGE.ordinal());continue;
                }
                int v=location.ordinal();
                if(condition.literal()) {
                    if(!(condition.candidate(0).value() instanceof Values.TextValue text)){refuse(v,at+Reason.UNSUPPORTED_INITIAL_VALUE.ordinal());continue;}
                    if(initialEpoch.get(v)==epoch) {
                        var old=conditions.get(Math.toIntExact(initialOrdinal.get(v)));
                        if(!old.candidate(0).value().equals(text))refuse(v,at+Reason.CONTRADICTORY_INITIAL_VALUES.ordinal());
                    } else {initialEpoch.set(v,epoch);initialOrdinal.set(v,row);}
                } else if(condition.possible())for(int candidate=0;candidate<condition.candidateCount();candidate++)
                    if(!(condition.candidate(candidate).value() instanceof Values.TextValue))refuse(v,at+Reason.UNSUPPORTED_INITIAL_VALUE.ordinal());
            }
        }
    }
    private void operation(Operation operation) {
        available();operationGuardVisits=Math.incrementExact(operationGuardVisits);long shape=slot(),value=slot(),precondition=slot(),overwrite=slot();
        var index=session.index();TextProfile.Location conditional=null;boolean selectedGuard=false;
        if(operation instanceof Operations.Assign assign) {
            try{conditional=ScalarAssignmentAdmission.destination(assign,subjects);}
            catch(TextProfile.Refusal refusal){global=Math.min(global,rank(shape,refusal));return;}
            selectedGuard=true;
            try{ScalarAssignmentAdmission.source(assign,subjects);}
            catch(TextProfile.Refusal refusal){refuse(conditional.ordinal(),rank(value,refusal));return;}
        } else if(operation instanceof Operations.HavocMust||operation instanceof Operations.HavocMay||operation instanceof Operations.Opaque) {
            try {ScalarConservativeAdmission.must(operation,session,subjects,location->{if(KillAuthority.exactCell(session,operation,location.cell()).isEmpty())refuse(location.ordinal(),value+Reason.UNPROVED_STRONG_OVERWRITE.ordinal());});}
            catch(TextProfile.Refusal refusal){global=Math.min(global,rank(shape,refusal));return;}
        } else if(effectAware&&operation instanceof Operations.Invoke invoke) {
            try {
                TextProfile.prepareForeign(invoke,List.of(),subjects);
            } catch(TextProfile.Refusal refusal){global=Math.min(global,rank(shape,refusal));return;}
        } else if(!TextProfile.scalarControl(operation)) {
            global=Math.min(global,shape+Reason.UNSUPPORTED_EFFECT_PROFILE.ordinal());return;
        }
        try{TextProfile.requirePreconditions(session,operation);}
        catch(TextProfile.Refusal refusal) {
            long refused=rank(precondition,refusal);
            if(selectedGuard)refuse(conditional.ordinal(),refused);else global=Math.min(global,refused);return;
        }
        if(selectedGuard&&KillAuthority.exactCell(session,operation,conditional.cell()).isEmpty())refuse(conditional.ordinal(),overwrite+Reason.UNPROVED_STRONG_OVERWRITE.ordinal());
    }
    private static boolean bool(Types.TypeRef type){return type instanceof Types.Known known&&known.type()==Types.Builtin.BOOL;}
    private void retireColumns() {
        Throwable failure=null;
        while(owned>0){var column=columns[--owned];columns[owned]=null;try{column.close();}catch(RuntimeException|Error cleanup){if(failure==null)failure=cleanup;else if(failure!=cleanup)failure.addSuppressed(cleanup);}}
        ranks=degree=offset=from=to=edge=cursor=branches=initialEpoch=initialOrdinal=null;
        if(failure instanceof RuntimeException exception)throw exception;if(failure instanceof Error error)throw error;
    }
    @Override public void close() {
        if(closed)return;closed=true;Throwable failure=null;var ownedGraph=graph;graph=null;
        if(ownedGraph!=null)try{ownedGraph.close();}catch(RuntimeException|Error cleanup){failure=cleanup;}
        try{retireColumns();}catch(RuntimeException|Error cleanup){if(failure==null)failure=cleanup;else if(failure!=cleanup)failure.addSuppressed(cleanup);}
        session=null;storage=null;subjects=null;
        try{capacity.close();}catch(RuntimeException|Error cleanup){if(failure==null)failure=cleanup;else if(failure!=cleanup)failure.addSuppressed(cleanup);}
        if(failure instanceof RuntimeException exception)throw exception;if(failure instanceof Error error)throw error;
    }
}
