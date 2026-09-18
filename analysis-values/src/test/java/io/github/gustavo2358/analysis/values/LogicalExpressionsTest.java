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

 private static class E {
  final Operations.Header h;int n;E(UnitId u,String key){h=header(u,key);}
  Operand.Header next(){return operand(h.id(),"e"+(n++),Operand.Role.VALUE_READ);}
  Expression read(ObjectId o){return new Expressions.Read(next(),new Places.ObjectPlace(next(),o));}
  Expression text(String s){return new Expressions.Literal(next(),new Values.TextValue(s));}
  Expression integer(int n){return new Expressions.Literal(next(),new Values.IntValue(BigInteger.valueOf(n)));}
  Expression slice(Expression e,int start,int length){return new Expressions.SliceText(next(),e,integer(start),integer(length));}
  Expression concat(Expression a,Expression b){return new Expressions.Binary(next(),Expressions.BinaryOperator.CONCAT,a,b);}
  Instruction set(ObjectId o,Expression e,int size){return new Operations.Assign(h,new Places.ObjectPlace(operand(h.id(),"dst",Operand.Role.VALUE_WRITE),o),new Expressions.FitText(next(),e,BigInteger.valueOf(size)," "));}
 }
 @Test void partialRootSnapshotAndDemand(){
  var p=graph(new String[]{null},new int[][]{{}},20,true,true);var u=p.units().getFirst();var root=u.objects().get(0).id();var dest=u.objects().get(1).id();var s=u.sequences().getFirst();
  var a=new E(u.id(),"partial");var b=new E(u.id(),"capture");
  var instructions=List.of(a.set(root,a.concat(a.slice(a.read(root),0,4),a.text("PROGA   ")),12),b.set(dest,b.slice(b.read(root),4,8),8),assign(u.id(),"later",root,"CHANGED"));
  var q=replace(p,List.of(unit(u.id(),u.entries(),List.of(new Sequence(s.label(),instructions,s.terminator(),s.origin())),u.objects())),p.coverage(),p.uncertainties(),p.premises());
  var admission=PossibleValuesAnalysis.prepare(partialSession(q),PossibleValuesAnalysis.EFFECTS_PROFILE,Set.of(dest));assertEquals(PossibleValuesAnalysis.Status.ACCEPTED,admission.status(),admission.reason());
  var run=admission.analysis().orElseThrow().execute();expected(fact(run,before(q,0,1)),false,"PROGA   ");assertEquals(2L,run.preparationMetrics().get("demandCellsPrepared"));
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
}
