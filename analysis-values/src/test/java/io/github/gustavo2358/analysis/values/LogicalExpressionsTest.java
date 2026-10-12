package io.github.gustavo2358.analysis.values;
import java.util.*;
import java.math.BigInteger;
import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static io.github.gustavo2358.analysis.values.ValuesFixtures.*;
import static io.github.gustavo2358.analysis.values.ValuesTest.*;
class LogicalExpressionsTest {
 private static io.github.gustavo2358.analysis.structure.AnalysisSession partialSession(Publication p){
  var policy=io.github.gustavo2358.analysis.cfg.domain.ProjectionPolicy.PARTIAL_ANALYSIS;
  var options=new io.github.gustavo2358.analysis.cfg.application.BuildOptions(io.github.gustavo2358.air.validation.ValidationOptions.defaults(),policy);
  var cfg=new io.github.gustavo2358.analysis.cfg.application.CfgBuildCoordinator(io.github.gustavo2358.analysis.cfg.extension.SemanticInterpreterRegistry.empty()).build(p,options);
  return io.github.gustavo2358.analysis.structure.AnalysisSession.open(cfg,p,policy,p.units().getFirst().entries()).session().orElseThrow();
 }
 private static PossibleValuesAnalysis.Execution logicalRun(Publication p){return PossibleValuesAnalysis.prepare(partialSession(p)).analysis().orElseThrow().execute();}

 private static Publication correlatedFanIn(int leaves,boolean reverse) {
  int join=2*leaves-1;var targets=new int[join+1][];var writes=new String[join+1];
  for(int i=0;i<leaves-1;i++)targets[i]=reverse?new int[]{2*i+2,2*i+1}:new int[]{2*i+1,2*i+2};
  for(int i=leaves-1;i<join;i++)targets[i]=new int[]{join};targets[join]=new int[]{};
  var p=graph(writes,targets,4,true,true);var u=p.units().getFirst();var x=u.objects().get(0).id();var y=u.objects().get(1).id();var z=u.objects().get(2).id();var output=u.objects().get(3).id();
  var sequences=new ArrayList<>(u.sequences());
  for(int leaf=0;leaf<leaves;leaf++) {
   int index=leaves-1+leaf;var sequence=sequences.get(index);String suffix=String.format(Locale.ROOT,"%02d",leaf);
   sequences.set(index,new Sequence(sequence.label(),List.of(assign(u.id(),"seed-a-"+suffix,x,"A"+suffix),assign(u.id(),"seed-x-"+suffix,y,"X"+suffix)),sequence.terminator(),sequence.origin()));
  }
  var sequence=sequences.get(join);var expression=new E(u.id(),"fan-in-concat");var copy=new E(u.id(),"fan-in-copy");
  var directCopy=new Operations.Assign(copy.h,new Places.ObjectPlace(operand(copy.h.id(),"dst",Operand.Role.VALUE_WRITE),output),copy.read(z));
  sequences.set(join,new Sequence(sequence.label(),List.of(expression.set(z,expression.concat(expression.read(x),expression.read(y)),6),directCopy),sequence.terminator(),sequence.origin()));
  return replace(p,List.of(unit(u.id(),u.entries(),sequences,u.objects())),p.coverage(),p.uncertainties(),p.premises());
 }

 private static class E {
  final Operations.Header h;int n;E(UnitId u,String key){h=header(u,key);}
  Operand.Header next(){return operand(h.id(),"e"+(n++),Operand.Role.VALUE_READ);}
  Expression read(ObjectId o){return new Expressions.Read(next(),new Places.ObjectPlace(next(),o));}
  Expression text(String s){return new Expressions.Literal(next(),new Values.TextValue(s));}
  Expression integer(int n){return new Expressions.Literal(next(),new Values.IntValue(BigInteger.valueOf(n)));}
  Expression slice(Expression e,int start,int length){return new Expressions.SliceText(next(),new Expressions.FitText(next(),e,BigInteger.valueOf(start+length)," "),integer(start),integer(length));}
  Expression concat(Expression a,Expression b){return new Expressions.Binary(next(),Expressions.BinaryOperator.CONCAT,a,b);}
  Instruction set(ObjectId o,Expression e,int size){return new Operations.Assign(h,new Places.ObjectPlace(operand(h.id(),"dst",Operand.Role.VALUE_WRITE),o),new Expressions.FitText(next(),e,BigInteger.valueOf(size)," "));}
 }
 @Test void hugeIntermediateRetainsSmallProjectedValueAndItsProducers(){
  var p=graph(new String[]{null},new int[][]{{}},2,true,true);var u=p.units().getFirst();var root=u.objects().get(0).id();var dest=u.objects().get(1).id();var s=u.sequences().getFirst();
  var a=new E(u.id(),"huge-fill");var b=new E(u.id(),"small-projection");
  var instructions=List.of(a.set(root,a.text("PROGA"),1000000000),b.set(dest,b.slice(b.read(root),0,8),8));
  var q=replace(p,List.of(unit(u.id(),u.entries(),List.of(new Sequence(s.label(),instructions,s.terminator(),s.origin())),u.objects())),p.coverage(),p.uncertainties(),p.premises());
  var run=logicalRun(q);var observed=fact(run,before(q,0,1));expected(observed,false,"PROGA   ");
  assertEquals(List.of("huge-fill","small-projection"),observed.candidateSupports().getFirst().producers().stream().map(x->x.evidence().localId()).toList());
 }
 @Test void repeatedCharacterUsesConstantStorageAndPreservesNeighbors(){
  var p=graph(new String[]{null},new int[][]{{}},2,true,true);var u=p.units().getFirst();var root=u.objects().get(0).id();var dest=u.objects().get(1).id();var s=u.sequences().getFirst();
  var a=new E(u.id(),"repeat");var b=new E(u.id(),"tail");
  var repeated=new Expressions.FillText(a.next(),a.text("😀"),BigInteger.valueOf(1000000000));
  var instructions=List.of(a.set(root,a.concat(a.text("AB"),repeated),1000000002),b.set(dest,b.slice(b.read(root),0,4),4));
  var q=replace(p,List.of(unit(u.id(),u.entries(),List.of(new Sequence(s.label(),instructions,s.terminator(),s.origin())),u.objects())),p.coverage(),p.uncertainties(),p.premises());
  expected(fact(logicalRun(q),before(q,0,1)),false,"AB😀😀");
 }
 @Test void unknownCharacterDoesNotEraseKnownNeighboringPositions(){
  var p=graph(new String[]{null},new int[][]{{}},2,true,true);var u=p.units().getFirst();var root=u.objects().get(0).id();var dest=u.objects().get(1).id();var s=u.sequences().getFirst();
  var a=new E(u.id(),"open-fill");var b=new E(u.id(),"known-neighbor");
  var reason=new UncertaintyId(p.id(),"character");
  var character=new Expressions.Unknown(a.next(),Types.known(Types.Builtin.TEXT),List.of(),Scopes.NoMemory.INSTANCE,reason);
  var one=new Expressions.FitText(a.next(),character,BigInteger.ONE," ");
  var repeated=new Expressions.FillText(a.next(),one,BigInteger.valueOf(1000000000));
  var instructions=List.of(a.set(root,a.concat(a.text("AB"),repeated),1000000002),b.set(dest,b.slice(b.read(root),0,2),2));
  var uncertainties=new ArrayList<>(p.uncertainties());uncertainties.add(new Evidence.Uncertainty(reason,"CHARACTER",List.of(Evidence.Dimension.VALUES),new Scopes.UnitScope(u.id()),"unspecified character",s.origin()));
  var q=replace(p,List.of(unit(u.id(),u.entries(),List.of(new Sequence(s.label(),instructions,s.terminator(),s.origin())),u.objects())),p.coverage(),uncertainties,p.premises());
  expected(fact(logicalRun(q),before(q,0,1)),false,"AB");
 }
 @Test void regionalFittingAlsoKeepsCompressedIntermediates(){
  var p=graph(new String[]{null},new int[][]{{}},2,true,true);var u=p.units().getFirst();var root=u.objects().get(0).id();var dest=u.objects().get(1).id();var s=u.sequences().getFirst();
  var a=new E(u.id(),"huge-fill");var b=new E(u.id(),"small-fitting");
  var instructions=List.of(a.set(root,a.text("PROGA"),1000000000),b.set(dest,b.read(root),8));
  var q=replace(p,List.of(unit(u.id(),u.entries(),List.of(new Sequence(s.label(),instructions,s.terminator(),s.origin())),u.objects())),p.coverage(),p.uncertainties(),p.premises());
  var run=RegionalValuesAnalysis.prepare(partialSession(q)).analysis().orElseThrow().execute();
  var observed=run.observe(List.of(before(q,0,1))).observations().getFirst().value();
  assertEquals(List.of("PROGA   "),RegionalValuesTest.texts(observed));
 }
 @Test void partialRootSnapshotAndDemand(){
  var p=graph(new String[]{null},new int[][]{{}},20,true,true);var u=p.units().getFirst();var root=u.objects().get(0).id();var dest=u.objects().get(1).id();var s=u.sequences().getFirst();
  var a=new E(u.id(),"partial");var b=new E(u.id(),"capture");
  var instructions=List.of(a.set(root,a.concat(a.slice(a.read(root),0,4),a.text("PROGA   ")),12),b.set(dest,b.slice(b.read(root),4,8),8),assign(u.id(),"later",root,"CHANGED"));
  var q=replace(p,List.of(unit(u.id(),u.entries(),List.of(new Sequence(s.label(),instructions,s.terminator(),s.origin())),u.objects())),p.coverage(),p.uncertainties(),p.premises());
  var admission=PossibleValuesAnalysis.prepare(partialSession(q),PossibleValuesAnalysis.EFFECTS_PROFILE,Set.of(dest));assertEquals(PossibleValuesAnalysis.Status.ACCEPTED,admission.status(),admission.reason());
  var run=admission.analysis().orElseThrow().execute();expected(fact(run,before(q,0,1)),false,"PROGA   ");assertEquals(2L,run.preparationMetrics().get("demandCellsPrepared"));
  var supports=fact(run,before(q,0,1)).candidateSupports().getFirst().producers().stream().map(x->x.evidence().localId()).toList();
  assertEquals(List.of("capture","partial"),supports,"retain the actual expression chain; later overwrite is not a producer");
  // Structural preparation already knows the canonical Cell cardinality. A
  // demanded relation must not enumerate every cold alias to rediscover it.
  var sparse=new TextProfile.Location(19,(Memory.Cell)p.storage().get(19));
  var lookupOnly=new AbstractMap<ObjectId,TextProfile.Location>() {
   @Override public Set<Entry<ObjectId,TextProfile.Location>> entrySet(){throw new AssertionError("relation preparation rescanned the complete nominal catalogue");}
  };
  assertFalse(ScalarRelations.create(List.of(sparse),List.of(),lookupOnly,20,()->{}).active());
 }
 @Test void branchAlternativesStayWholeThroughRepeatedRootReads(){
  var p=graph(new String[]{null,null,null,null},new int[][]{{1,2},{3},{3},{}},2,true,true);var u=p.units().getFirst();var root=u.objects().get(0).id();var dest=u.objects().get(1).id();var sequences=new ArrayList<>(u.sequences());
  for(int i=1;i<=2;i++){
   var s=sequences.get(i);var a=new E(u.id(),"head"+i);var b=new E(u.id(),"tail"+i);
   sequences.set(i,new Sequence(s.label(),List.of(a.set(root,a.concat(a.text(i==1?"PROG":"MODU"),a.slice(a.read(root),4,4)),8),b.set(root,b.concat(b.slice(b.read(root),0,4),b.text(i==1?"0001":"0002")),8)),s.terminator(),s.origin()));
  }
  var s=sequences.get(3);var e=new E(u.id(),"join");sequences.set(3,new Sequence(s.label(),List.of(e.set(dest,e.concat(e.slice(e.read(root),0,4),e.slice(e.read(root),4,4)),8)),s.terminator(),s.origin()));
  var q=replace(p,List.of(unit(u.id(),u.entries(),sequences,u.objects())),p.coverage(),p.uncertainties(),p.premises());
  expected(fact(logicalRun(q),before(q,3,1)),false,"MODU0002","PROG0001");
 }
 @Test void admittedConcatPreservesWholeBranchStoresAndCandidateSupports(){
  var p=graph(new String[]{null,null,null,null},new int[][]{{1,2},{3},{3},{}},3,true,true);
  var u=p.units().getFirst();var x=u.objects().get(0).id();var y=u.objects().get(1).id();var z=u.objects().get(2).id();
  var sequences=new ArrayList<>(u.sequences());
  for(int i=1;i<=2;i++){
   var s=sequences.get(i);
   sequences.set(i,new Sequence(s.label(),List.of(assign(u.id(),i==1?"seed-A":"seed-B",x,i==1?"A":"B"),assign(u.id(),i==1?"seed-X":"seed-Y",y,i==1?"X":"Y")),s.terminator(),s.origin()));
  }
  var expression=new E(u.id(),"fit-concat");var s=sequences.get(3);
  sequences.set(3,new Sequence(s.label(),List.of(expression.set(z,expression.concat(expression.read(x),expression.read(y)),2)),s.terminator(),s.origin()));
  var q=replace(p,List.of(unit(u.id(),u.entries(),sequences,u.objects())),p.coverage(),p.uncertainties(),p.premises());
  var observed=fact(logicalRun(q),before(q,3,2));expected(observed,false,"AX","BY");
  var supports=new HashMap<String,Set<String>>();
  for(var candidate:observed.candidateSupports())supports.put(candidate.candidate().value(),candidate.producers().stream().map(xp->xp.evidence().localId()).collect(java.util.stream.Collectors.toSet()));
  assertEquals(Map.of("AX",Set.of("seed-A","seed-X","fit-concat"),"BY",Set.of("seed-B","seed-Y","fit-concat")),supports);
  assertFalse(observed.candidates().stream().map(Values.TextValue::value).anyMatch(Set.of("AY","BX")::contains));
 }
 @Test void unknownIndependentBooleanInitialStateDoesNotDiscardCorrelatedTextDemand(){
  var p=graph(new String[]{null,null,null,null},new int[][]{{1,2},{3},{3},{}},4,true,true);
  var u=p.units().getFirst();var x=u.objects().get(0).id();var y=u.objects().get(1).id();var z=u.objects().get(2).id();var condition=u.objects().get(3);
  var bool=Types.known(Types.Builtin.BOOL);var objects=new ArrayList<>(u.objects());
  objects.set(3,new Memory.ObjectDeclaration(condition.id(),condition.displayName(),bool,condition.storage(),condition.visibility(),condition.origin(),condition.coverage(),condition.precision()));
  var storage=new ArrayList<>(p.storage());var cell=(Memory.Cell)storage.get(3);storage.set(3,new Memory.Cell(cell.header(),bool));
  var sequences=new ArrayList<>(u.sequences());var choose=(Operations.Branch)sequences.getFirst().terminator();
  var predicate=new Expressions.Read(operand(choose.header().id(),"condition",Operand.Role.PREDICATE),new Places.ObjectPlace(operand(choose.header().id(),"condition-place",Operand.Role.VALUE_READ),condition.id()));
  sequences.set(0,new Sequence(sequences.getFirst().label(),List.of(),new Operations.Branch(choose.header(),predicate,choose.trueDestination(),choose.falseDestination()),choose.header().origin()));
  for(int i=1;i<=2;i++){var s=sequences.get(i);sequences.set(i,new Sequence(s.label(),List.of(assign(u.id(),i==1?"seed-A":"seed-B",x,i==1?"A":"B"),assign(u.id(),i==1?"seed-X":"seed-Y",y,i==1?"X":"Y")),s.terminator(),s.origin()));}
  var expression=new E(u.id(),"fit-concat");var join=sequences.get(3);sequences.set(3,new Sequence(join.label(),List.of(expression.set(z,expression.concat(expression.read(x),expression.read(y)),2)),join.terminator(),join.origin()));
  var entry=u.entries().getFirst();var place=new Places.ObjectPlace(new Operand.Header(new OperandId(new EntryOwner(entry.id()),"condition"),Operand.Role.VALUE_WRITE,entry.origin()),condition.id());
  var reason=new UncertaintyId(p.id(),"external-condition");
  var uncertainties=List.of(new Evidence.Uncertainty(reason,"VALUE_UNKNOWN",List.of(Evidence.Dimension.VALUES),new Scopes.UnitScope(u.id()),"external boolean",entry.origin()));
  entry=new Entries.Entry(entry.id(),entry.initialLabel(),entry.signature(),new Entries.EntryState(List.of(new Entries.InitialCondition(place,new Entries.ExternalUnknown(reason),entry.origin(),List.of())),List.of(reason)),entry.origin());
  var q=new Publication(p.id(),p.airVersion(),p.capabilities(),p.artifacts(),List.of(unit(u.id(),List.of(entry),sequences,objects)),storage,p.resources(),p.artifactRelations(),p.origins(),p.coverage(),uncertainties,p.premises());
  var admission=PossibleValuesAnalysis.prepare(partialSession(q),PossibleValuesAnalysis.EFFECTS_PROFILE,Set.of(z));
  assertEquals(PossibleValuesAnalysis.Status.ACCEPTED,admission.status(),admission.reason());
  var observed=fact(admission.analysis().orElseThrow().execute(),before(q,3,2));expected(observed,false,"AX","BY");
  assertTrue(observed.sourceUnknownRemainder());
  for(var candidate:observed.candidateSupports())assertEquals(candidate.candidate().value().equals("AX")?Set.of("seed-A","seed-X","fit-concat"):Set.of("seed-B","seed-Y","fit-concat"),candidate.producers().stream().map(producer->producer.evidence().localId()).collect(java.util.stream.Collectors.toSet()));
  // A known Boolean is not evaluated by this text profile: it must not silently
  // become an unconstrained predicate under the new independent-unknown rule.
  var literal=new Expressions.Literal(new Operand.Header(new OperandId(new EntryOwner(entry.id()),"known-condition-value"),Operand.Role.VALUE_READ,entry.origin()),new Values.BoolValue(false));
  var knownEntry=new Entries.Entry(entry.id(),entry.initialLabel(),entry.signature(),new Entries.EntryState(List.of(new Entries.InitialCondition(place,new Entries.LiteralInitial(literal),entry.origin(),List.of())),List.of()),entry.origin());
  var known=replace(q,List.of(unit(u.id(),List.of(knownEntry),sequences,objects)),q.coverage(),q.uncertainties(),q.premises());
  var refused=PossibleValuesAnalysis.prepare(partialSession(known),PossibleValuesAnalysis.EFFECTS_PROFILE,Set.of(z));
  assertEquals(PossibleValuesAnalysis.Status.UNSUPPORTED,refused.status());assertEquals("UNSUPPORTED_INITIAL_STORAGE",refused.reason());
 }
 @Test void openBranchRetainsKnownCorrelatedImageWithoutInventingPairs(){
  var p=graph(new String[]{null,null,null,null},new int[][]{{2,1},{3},{3},{}},3,true,true);
  var u=p.units().getFirst();var x=u.objects().get(0).id();var y=u.objects().get(1).id();var z=u.objects().get(2).id();
  var sequences=new ArrayList<>(u.sequences());var left=sequences.get(1);var right=sequences.get(2);
  sequences.set(1,new Sequence(left.label(),List.of(assign(u.id(),"seed-A",x,"A"),assign(u.id(),"seed-X",y,"X")),left.terminator(),left.origin()));
  sequences.set(2,new Sequence(right.label(),List.of(assign(u.id(),"seed-B",x,"B")),right.terminator(),right.origin()));
  var expression=new E(u.id(),"open-fit-concat");var join=sequences.get(3);
  sequences.set(3,new Sequence(join.label(),List.of(expression.set(z,expression.concat(expression.read(x),expression.read(y)),2)),join.terminator(),join.origin()));
  var q=replace(p,List.of(unit(u.id(),u.entries(),sequences,u.objects())),p.coverage(),p.uncertainties(),p.premises());
  var observed=fact(logicalRun(q),before(q,3,2));expected(observed,true,"AX");
  assertFalse(observed.candidates().stream().map(Values.TextValue::value).anyMatch(Set.of("AY","BX","BY")::contains));
  assertEquals(Set.of("seed-A","seed-X","open-fit-concat"),observed.candidateSupports().getFirst().producers().stream().map(xp->xp.evidence().localId()).collect(java.util.stream.Collectors.toSet()));
 }
 @Test void finiteFanInStaysDiagonalThroughExpressionAndCopyWithoutCartesianProjection(){
  for(int leaves:List.of(4,8,16,32,64)) {
   Set<String> expected=new TreeSet<>();for(int leaf=0;leaf<leaves;leaf++){String suffix=String.format(Locale.ROOT,"%02d",leaf);expected.add("A"+suffix+"X"+suffix);}
   Map<String,Long> baseline=null;
   for(boolean reverse:List.of(false,true)) {
    var p=correlatedFanIn(leaves,reverse);int join=2*leaves-1;var run=logicalRun(p);var observed=fact(run,before(p,join,3));
    assertEquals(expected,observed.candidates().stream().map(Values.TextValue::value).collect(java.util.stream.Collectors.toCollection(TreeSet::new)));
    assertFalse(observed.modelValueRemainder());assertEquals(leaves,observed.candidateSupports().size());
    for(var candidate:observed.candidateSupports()) {
     String suffix=candidate.candidate().value().substring(1,3);
     assertEquals(Set.of("seed-a-"+suffix,"seed-x-"+suffix,"fan-in-concat"),candidate.producers().stream().map(producer->producer.evidence().localId()).collect(java.util.stream.Collectors.toSet()));
    }
    var metrics=run.solveMetrics();assertEquals(1L,metrics.get("scalarRelationGroups"));assertEquals(4L,metrics.get("scalarRelationCells"));assertEquals(4L,metrics.get("scalarRelationMaxWidth"));
    assertEquals(2L*leaves,metrics.get("scalarRelationProjectedRows"),"expression and copy each visit only the actual diagonal rows");
    assertTrue(metrics.get("scalarRelationInternedNodes")<=20L*leaves,"stable result retains only linear relation nodes");
    assertTrue(metrics.get("scalarRelationInternedEdges")<=32L*leaves,"stable result retains only linear relation edges");
    assertTrue(metrics.get("scalarRelationAllocatedEdges")<=8L*leaves*(1+Integer.numberOfTrailingZeros(leaves)),"balanced unions keep cumulative edge allocation within N log N for geometric fan-in");
    assertEquals(metrics.get("scalarRelationAllocatedNodes")-metrics.get("scalarRelationInternedNodes"),metrics.get("scalarRelationRetiredNodes"));
    assertEquals(metrics.get("scalarRelationAllocatedEdges")-metrics.get("scalarRelationInternedEdges"),metrics.get("scalarRelationRetiredEdges"));
    var stable=Map.of("rows",metrics.get("scalarRelationProjectedRows"),"liveNodes",metrics.get("scalarRelationInternedNodes"),"liveEdges",metrics.get("scalarRelationInternedEdges"),"allocatedEdges",metrics.get("scalarRelationAllocatedEdges"));
    if(baseline==null)baseline=stable;else assertEquals(baseline,stable,"physical successor order must not change relation work");
   }
   System.out.println("SCALAR_RELATIONS_SCALE leaves="+leaves+" "+baseline);
  }
 }
}
