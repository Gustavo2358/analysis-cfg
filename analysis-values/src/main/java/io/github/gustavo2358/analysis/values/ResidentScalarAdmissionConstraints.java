package io.github.gustavo2358.analysis.values;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.structure.*;
import io.github.gustavo2358.analysis.storage.KillAuthority;
import java.util.*;

/** Explicit resident decision compatibility; catalog/edges/ranks are caller-managed.
 * No native residency, execution, candidate pool or proof projection promise. */
final class ResidentScalarAdmissionConstraints implements PossibleValuesAnalysis.AdmissionDecisions {
    private enum Reason {
        UNSUPPORTED_DEMAND_SOURCE, UNSUPPORTED_DEMAND_STORAGE,
        UNSUPPORTED_EFFECT_PROFILE, UNSUPPORTED_STORAGE_PROFILE,
        UNPROVED_SCALAR_OPERATION_PRECONDITION, UNPROVED_STRONG_OVERWRITE,
        UNSUPPORTED_INITIAL_PLACE, UNSUPPORTED_INITIAL_STORAGE,
        UNSUPPORTED_INITIAL_VALUE, CONTRADICTORY_INITIAL_VALUES, UNSUPPORTED_EFFECT_PLACE
    }
    private AnalysisSession session;
    private final ProgramStore.CapacityLease capacity;
    private final boolean supportedProfile,effectAware;
    private Map<ObjectId,TextProfile.Location> subjects=new HashMap<>();
    private List<TextProfile.Location> cells=new ArrayList<>();
    private ScalarConstraintIndex graph;
    private long global=Long.MAX_VALUE,common=Long.MAX_VALUE,position=256;
    private boolean catalogReady,closed;
    public int graphBuilds;
    static ResidentScalarAdmissionConstraints open(AnalysisSession session,String profile) {
        Objects.requireNonNull(session);
        // The only native use is a constant-size unsupported-profile handle:
        // its refusal precedes any catalog or expression traversal. Fund its
        // wrappers as well; supported native profiles never take this route.
        var budget=session.index().store().executionBudget();
        if(budget.isPresent()&&(PossibleValuesAnalysis.PROFILE.equals(profile)||PossibleValuesAnalysis.EFFECTS_PROFILE.equals(profile)))
            throw new IllegalArgumentException("supported native profile requires native decision storage");
        var lease=budget.isPresent()?budget.orElseThrow().reserve(ProgramStore.CapacityPool.RESIDENT,256,ProgramStore.ExecutionPhase.DEMAND):null;
        ResidentScalarAdmissionConstraints result=null;
        try {
            result=new ResidentScalarAdmissionConstraints(session,profile,lease);
            if(lease!=null)lease.onOwnerClose(result::close);return result;
        } catch(RuntimeException|Error failure) {
            try{if(result!=null)result.close();else if(lease!=null)lease.close();}
            catch(RuntimeException|Error cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}throw failure;
        }
    }
    private ResidentScalarAdmissionConstraints(AnalysisSession session,String profile,ProgramStore.CapacityLease capacity) {
        this.session=session;this.capacity=capacity;supportedProfile=PossibleValuesAnalysis.PROFILE.equals(profile)||PossibleValuesAnalysis.EFFECTS_PROFILE.equals(profile);
        effectAware=PossibleValuesAnalysis.EFFECTS_PROFILE.equals(profile);
    }
    private void available(){if(closed)throw new IllegalStateException("closed resident decision compiler");session.index().store().progress(ProgramStore.ExecutionPhase.DEMAND);}
    private void catalog() {
        if(catalogReady)return;
        var index=session.index();var canonical=new HashMap<StorageId,TextProfile.Location>();
        var preparedSubjects=new HashMap<ObjectId,TextProfile.Location>();var preparedCells=new ArrayList<TextProfile.Location>();
        for(var entry:index.objectDeclarations().entrySet()) {
            available();var object=entry.getValue();
            var base=object.storage() instanceof Memory.CellBinding binding?index.storage(binding.storage()):null;
            var cell=ScalarStorageAdmission.cell(object,base);if(cell==null)continue;
            var location=canonical.get(cell.header().id());
            if(location==null){location=new TextProfile.Location(preparedCells.size(),cell);canonical.put(cell.header().id(),location);preparedCells.add(location);}
            preparedSubjects.put(entry.getKey(),location);
        }
        subjects=preparedSubjects;cells=preparedCells;catalogReady=true;
    }
    @Override public PossibleValuesAnalysis.AdmissionDecision decide(ObjectId subject) {
        available();Objects.requireNonNull(subject);
        if(!supportedProfile)return refusal(Reason.UNSUPPORTED_EFFECT_PROFILE);
        catalog();var root=subjects.get(subject);if(root==null)return refusal(Reason.UNSUPPORTED_DEMAND_STORAGE);
        // Storage refusal precedes expression traversal, including arithmetic errors.
        if(graph==null)compile();
        long rank=Math.min(global,Math.min(common,graph.minimum(root.ordinal())));
        return rank==Long.MAX_VALUE?new PossibleValuesAnalysis.AdmissionDecision(PossibleValuesAnalysis.Status.ACCEPTED,null,0,0)
            :refusal(Reason.values()[Math.toIntExact(rank%256)]);
    }
    private static PossibleValuesAnalysis.AdmissionDecision refusal(Reason reason) {
        var name=reason.name();return new PossibleValuesAnalysis.AdmissionDecision(reason==Reason.CONTRADICTORY_INITIAL_VALUES?PossibleValuesAnalysis.Status.INVALID_INPUT:PossibleValuesAnalysis.Status.UNSUPPORTED,
            name,name.contains("STORAGE")?1:0,name.contains("EFFECT")?1:0);
    }
    private long slot(){long current=position;position=Math.addExact(position,256);return current;}
    private static long rank(long slot,TextProfile.Refusal refusal){return Math.addExact(slot,Reason.valueOf(refusal.getMessage()).ordinal());}
    private void compile() {
        global=common=Long.MAX_VALUE;position=256; // failed-build retries must not mix rank namespaces
        var index=session.index();var units=session.contexts().stream().map(c->c.entryView().id().unit()).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        var edges=new ArrayList<Set<Integer>>();var ranks=new long[cells.size()];Arrays.fill(ranks,Long.MAX_VALUE);
        for(int at=0;at<cells.size();at++)edges.add(new LinkedHashSet<>());
        var branches=new HashSet<Integer>();
        for(var unit:units)for(var site:index.sites(Operations.Branch.class,unit))
                for(var object:TextPredicate.reads(((Operations.Branch)site.operation()).predicate())) {
                    var location=subjects.get(object);if(location!=null)branches.add(location.ordinal());
                }
        for(var unit:units) {
            for(var site:index.sites(Operations.Assign.class,unit)) {
                available();var assign=(Operations.Assign)site.operation();
                if(assign.destination() instanceof Places.ObjectPlace destination) {
                    var to=subjects.get(destination.object());
                    try {
                        // As in original closure: Refusal is ignored, arithmetic is not.
                        var reads=TextExpressions.reads(assign.value());
                        if(to!=null)for(var object:reads) {
                            var from=subjects.get(object);
                            if(from==null)ranks[to.ordinal()]=0; // demanded unsupported source precedes operations
                            else edges.get(to.ordinal()).add(from.ordinal());
                        }
                    } catch(TextProfile.Refusal ignored) { }
                }
            }
        }
        for(var unit:units)for(var sequence:index.unit(unit).sequences()) {
            for(var instruction:sequence.instructions())operation(instruction,ranks);
            operation(sequence.terminator(),ranks);
        }
        for(var context:session.contexts()) {
            var initial=new HashMap<Integer,ProgramStore.InitialConditionView>();
            // Stable literal-first partition; no reordered context or other condition.
            for(boolean literal:new boolean[]{true,false})for(var condition:context.entryView().state().conditions()) {
                if(condition.literal()!=literal)continue;available();long at=slot();
                if(!(condition.place() instanceof Places.ObjectPlace named)){global=Math.min(global,at+Reason.UNSUPPORTED_INITIAL_PLACE.ordinal());continue;}
                var location=subjects.get(named.object());
                if(location==null) {
                    if(condition.kind()==ProgramStore.InitialKind.EXTERNAL_UNKNOWN) {
                        var object=index.object(named.object());var cell=index.directCell(named.object());
                        if(object!=null&&object.storage() instanceof Memory.CellBinding&&cell!=null
                            &&bool(object.typeRef())&&bool(cell.typeRef()))continue;
                    }
                    global=Math.min(global,at+Reason.UNSUPPORTED_INITIAL_STORAGE.ordinal());continue;
                }
                if(condition.literal()) {
                    if(!(condition.candidate(0).value() instanceof Values.TextValue text)) {
                        ranks[location.ordinal()]=Math.min(ranks[location.ordinal()],at+Reason.UNSUPPORTED_INITIAL_VALUE.ordinal());continue;
                    }
                    var old=initial.putIfAbsent(location.ordinal(),condition);
                    if(old!=null&&!old.candidate(0).value().equals(text))ranks[location.ordinal()]=Math.min(ranks[location.ordinal()],at+Reason.CONTRADICTORY_INITIAL_VALUES.ordinal());
                } else if(condition.possible())for(int candidate=0;candidate<condition.candidateCount();candidate++)
                    if(!(condition.candidate(candidate).value() instanceof Values.TextValue))ranks[location.ordinal()]=Math.min(ranks[location.ordinal()],at+Reason.UNSUPPORTED_INITIAL_VALUE.ordinal());
            }
        }
        // Callback arrays are explicit caller-managed resident compatibility, not
        // native input-sized controls. The owned graph itself is the prior tested one.
        var adjacency=edges.stream().map(e->e.stream().mapToInt(Integer::intValue).toArray()).toArray(int[][]::new);
        ScalarConstraintIndex built=null;
        try {
            built=ScalarConstraintIndex.open(index.store(),cells.size(),v->adjacency[v].length,(v,at)->adjacency[v][at],v->ranks[v]);
            for(int branch:branches)common=Math.min(common,built.minimum(branch));
            graph=built;graphBuilds=Math.incrementExact(graphBuilds);
        } catch(RuntimeException|Error failure){if(built!=null)try{built.close();}catch(RuntimeException|Error cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}throw failure;}
    }
    private void operation(Operation operation,long[] ranks) {
        available();long shape=slot(),value=slot(),precondition=slot(),overwrite=slot();
        var index=session.index();TextProfile.Location conditional=null;boolean selectedGuard=false;
        if(operation instanceof Operations.Assign assign) {
            try{conditional=ScalarAssignmentAdmission.destination(assign,subjects);}
            catch(TextProfile.Refusal refusal){global=Math.min(global,rank(shape,refusal));return;}
            selectedGuard=true;
            try{ScalarAssignmentAdmission.source(assign,subjects);}
            catch(TextProfile.Refusal refusal){ranks[conditional.ordinal()]=Math.min(ranks[conditional.ordinal()],rank(value,refusal));return;}
        } else if(operation instanceof Operations.HavocMust||operation instanceof Operations.HavocMay||operation instanceof Operations.Opaque) {
            try {
                ScalarConservativeAdmission.must(operation,session,subjects,location->{
                    // Conservative preparation checks selected MUST authority
                    // before the common operation-precondition guard (unlike Assign).
                    if(KillAuthority.exactCell(session,operation,location.cell()).isEmpty())
                        ranks[location.ordinal()]=Math.min(ranks[location.ordinal()],value+Reason.UNPROVED_STRONG_OVERWRITE.ordinal());
                });
            } catch(TextProfile.Refusal refusal){global=Math.min(global,rank(shape,refusal));return;}
        }
        else if(effectAware&&operation instanceof Operations.Invoke invoke) {
            try {
                TextProfile.prepareForeign(invoke,List.of(),subjects);
            } catch(TextProfile.Refusal refusal){global=Math.min(global,rank(shape,refusal));return;}
        } else if(!TextProfile.scalarControl(operation)) {
            global=Math.min(global,shape+Reason.UNSUPPORTED_EFFECT_PROFILE.ordinal());return;
        }
        try{TextProfile.requirePreconditions(session,operation);}
        catch(TextProfile.Refusal refusal) {
            long refused=rank(precondition,refusal);
            if(selectedGuard)ranks[conditional.ordinal()]=Math.min(ranks[conditional.ordinal()],refused);else global=Math.min(global,refused);
            return;
        }
        if(selectedGuard&&KillAuthority.exactCell(session,operation,conditional.cell()).isEmpty())
            ranks[conditional.ordinal()]=Math.min(ranks[conditional.ordinal()],overwrite+Reason.UNPROVED_STRONG_OVERWRITE.ordinal());
    }
    private static boolean bool(Types.TypeRef type){return type instanceof Types.Known known&&known.type()==Types.Builtin.BOOL;}
    @Override public void close() {
        if(closed)return;closed=true;subjects=null;cells=null;session=null;
        Throwable failure=null;var owned=graph;graph=null;
        if(owned!=null)try{owned.close();}catch(RuntimeException|Error cleanup){failure=cleanup;}
        if(capacity!=null)try{capacity.close();}catch(RuntimeException|Error cleanup){if(failure==null)failure=cleanup;else if(failure!=cleanup)failure.addSuppressed(cleanup);}
        if(failure instanceof RuntimeException exception)throw exception;if(failure instanceof Error error)throw error;
    }
}
