package io.github.gustavo2358.analysis.values;

import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.analysis.application.AnalysisProvider;
import io.github.gustavo2358.analysis.plan.*;
import io.github.gustavo2358.analysis.query.PointQuery;
import io.github.gustavo2358.analysis.solver.Direction;
import io.github.gustavo2358.analysis.structure.AnalysisSession;
import java.util.*;

/** W4 adapter: one selected Entry, one W3 execution, and that same execution's bound observation API. */
public final class PossibleValuesProvider implements AnalysisProvider<ObjectId,ValueFact> {
    public static final String IMPLEMENTATION = "PossibleValues";
    public static final String VERSION = "1";
    public static final String PRECISION = "FINITE_PROGRAM_TEXT_VALUES";
    public static final String PROJECTION = "ValueFact@1";
    public static AnalysisKey key(EntryId entry) {
        return key(entry,PossibleValuesAnalysis.PROFILE);
    }
    public static AnalysisKey key(EntryId entry,String profile) {
        return new AnalysisKey(IMPLEMENTATION,VERSION,profile,Direction.FORWARD,PRECISION,Map.of(),entry);
    }
    /** Demand participates in cache identity; different query sets never reuse a narrower run. */
    public static AnalysisKey key(EntryId entry,String profile,Collection<ObjectId> demand) {
        var encoded=demand.stream().map(o->encode(o.unit().publication().localId())+"."+encode(o.unit().localId())+"."+encode(o.localId())).distinct().sorted().toList();
        return new AnalysisKey(IMPLEMENTATION,VERSION,profile,Direction.FORWARD,PRECISION,Map.of("demandObjects",String.join(";",encoded)),entry);
    }
    private static String encode(String s){return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    private static String decode(String s){return new String(Base64.getUrlDecoder().decode(s),java.nio.charset.StandardCharsets.UTF_8);}
    private Set<ObjectId> demand(AnalysisKey key) {
        if(!key.options().containsKey("demandObjects"))return null;
        var result=new HashSet<ObjectId>();var text=key.options().get("demandObjects");
        if(!text.isEmpty())for(var item:text.split(";")) {
            var parts=item.split("\\.",-1);if(parts.length!=3)throw new IllegalArgumentException("invalid demand identity");
            result.add(new ObjectId(new UnitId(new PublicationId(decode(parts[0])),decode(parts[1])),decode(parts[2])));
        }
        return Set.copyOf(result);
    }
    public static ObservationBatchId<ObjectId,ValueFact> batch(String id, AnalysisKey key) {
        return new ObservationBatchId<>(id,key,PROJECTION,ObjectId.class,ValueFact.class);
    }
    public String implementation() { return IMPLEMENTATION; }
    public String version() { return VERSION; }
    public Set<String> semanticOptionNames() { return Set.of("demandObjects"); }
    public boolean supports(AnalysisKey key) {
        return key.implementation().equals(IMPLEMENTATION) && key.version().equals(VERSION)
            && (key.profile().equals(PossibleValuesAnalysis.PROFILE)||key.profile().equals(PossibleValuesAnalysis.EFFECTS_PROFILE)) && key.direction() == Direction.FORWARD
            && key.precisionPolicy().equals(PRECISION) && semanticOptionNames().containsAll(key.options().keySet());
    }
    public String projection() { return PROJECTION; }
    public Class<ObjectId> subjectType() { return ObjectId.class; }
    public Class<ValueFact> factType() { return ValueFact.class; }
    public Comparator<ObjectId> subjectOrder() {
        return Comparator.comparing((ObjectId id) -> id.unit().publication().localId()).thenComparing(id -> id.unit().localId()).thenComparing(ObjectId::localId);
    }
    public Prepared<ObjectId,ValueFact> prepare(AnalysisSession owner, AnalysisKey key) {
        if (!supports(key)) throw new IllegalArgumentException("unsupported PossibleValues key");
        var scoped = owner.selectEntries(List.of(key.entry()));
        var admission = PossibleValuesAnalysis.prepare(scoped,key.profile(),demand(key));
        return new Prepared<>() {
            public AnalysisKey key() { return key; }
            public AnalysisOutcome refusal() {
                return admission.status() == PossibleValuesAnalysis.Status.ACCEPTED ? null : new AnalysisOutcome(key,
                    admission.status() == PossibleValuesAnalysis.Status.INVALID_INPUT ? AnalysisOutcome.Status.INVALID_INPUT : AnalysisOutcome.Status.UNSUPPORTED,
                    admission.reason(),Map.of("unsupportedStorageProfiles",admission.unsupportedStorageProfiles(),"unsupportedEffectProfiles",admission.unsupportedEffectProfiles()));
            }
            public Run<ObjectId,ValueFact> execute() {
                var execution = admission.analysis().orElseThrow().execute();
                var metrics = new TreeMap<String,Long>();
                var solver = execution.dataflow().metrics();
                metrics.put("analysisPoints",solver.analysisPoints());
                metrics.put("contextualEdges",solver.contextualEdges());
                metrics.put("boundaryJoins",solver.boundaryJoins());
                metrics.put("initializationAttempts",solver.initializationAttempts());
                metrics.put("worklistAttempts",solver.worklistAttempts());
                metrics.put("worklistPushes",solver.worklistPushes());
                metrics.put("nodesPopped",solver.nodesPopped());
                metrics.put("duplicatePushesSuppressed",solver.duplicatePushesSuppressed());
                metrics.put("maxWorklistSize",solver.maxWorklistSize());
                metrics.put("nodesTransferred",solver.nodesTransferred());
                metrics.put("operationsTransferred",solver.operationsTransferred());
                metrics.put("firstPublications",solver.firstPublications());
                metrics.put("publishedStatesChanged",solver.publishedStatesChanged());
                metrics.put("publishedStatesUnchanged",solver.publishedStatesUnchanged());
                metrics.put("edgeTransferInvocations",solver.edgeTransferInvocations());
                metrics.put("edgeContributionJoins",solver.edgeContributionJoins());
                metrics.put("accumulatorStatesChanged",solver.accumulatorStatesChanged());
                metrics.put("accumulatorStatesUnchanged",solver.accumulatorStatesUnchanged());
                metrics.put("predecessorContributionReads",solver.predecessorContributionReads());
                metrics.put("successorContributionReads",solver.successorContributionReads());
                metrics.put("joinEntriesVisited",solver.joinEntriesVisited());
                metrics.put("stateCompareEntries",solver.stateCompareEntries());
                execution.preparationMetrics().forEach((k,v) -> metrics.put("prepare_"+k,v));
                execution.solveMetrics().forEach((k,v) -> metrics.put("solve_"+k,v));
                var outcome = new AnalysisOutcome(key,AnalysisOutcome.Status.STABLE,null,metrics);
                return new Run<>() {
                    public AnalysisOutcome outcome() { return outcome; }
                    public Materialized<ObjectId,ValueFact> observe(List<PointQuery<ObjectId>> queries) {
                        var observations = execution.observe(queries);
                        var work = new TreeMap<String,Long>(observations.quality());
                        observations.stateMetrics().forEach((k,v) -> work.put("replay_"+k,v));
                        return new Materialized<>(observations.batch(),work);
                    }
                };
            }
        };
    }
}
