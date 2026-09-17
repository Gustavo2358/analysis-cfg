package io.github.gustavo2358.analysis.values;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import java.util.*;
import java.math.BigInteger;

/** Small synthetic models extracted from EP-LAB-01 LabV2; no confidential source. */
public final class EpR2Fixtures {
 private EpR2Fixtures() {}
 public static final PublicationId P=new PublicationId("ep-lab-01");static final UnitId U=new UnitId(P,"unit");static final OriginId O=new OriginId(P,"origin");static final EntryId E=new EntryId(U,"entry");static final UncertaintyId GAP=new UncertaintyId(P,"entry-open");
 public static ObjectId obj(int i){return new ObjectId(U,"object-"+i);}static StorageId base(int i){return new StorageId(P,"base-"+i);}static LabelId label(String s){return new LabelId(U,s);}
 public static Evidence.Coverage cov(Scopes.FactScope s){return new Evidence.Coverage(Evidence.InventoryStatus.COMPLETE,s,List.of(),List.of());}
 public static Evidence.Precision precision(){var c=new Evidence.Claim(new Scopes.UnitScope(U),Evidence.PrecisionStatus.EXACT,List.of());return new Evidence.Precision(c,c,c,c,c);}
 public static Operations.Header header(String s){return new Operations.Header(new OperationId(U,s),O,Evidence.CoverageStatus.MODELED,precision(),List.of());}
 public static Operand.Header operand(OperandOwner owner,String s,Operand.Role role){return new Operand.Header(new OperandId(owner,s),role,O);}
 public static Operand.Header op(Operations.Header h,String s,Operand.Role r){return operand(new OperationOwner(h.id()),s,r);}
 public static Interactions.Signature signature(){return new Interactions.Signature(new Interactions.ParameterInventory(List.of(),Interactions.NoRemainder.INSTANCE),new Interactions.ResultInventory(List.of(),Interactions.NoRemainder.INSTANCE),O);}
 public static Sequence seq(String s,List<Instruction> ins,Terminator t){return new Sequence(label(s),ins,t,O);}
 public static Operations.Jump jump(String id,String next){return new Operations.Jump(header(id),label(next));}
 public static Operations.Invoke call(int i,int object){var h=header("call-"+i);return new Operations.Invoke(h,"call",new Interactions.ComputedTarget("program","cobol.program",new Expressions.Read(op(h,"read",Operand.Role.CALL_TARGET),new Places.ObjectPlace(op(h,"place",Operand.Role.VALUE_READ),obj(object))),Interactions.ExactName.INSTANCE,O),List.of(),List.of(),new Interactions.ExternalSignature(signature()),List.of(),new Interactions.EffectBound(new Interactions.ForeignEffects(Scopes.NoMemory.INSTANCE,Scopes.NoMemory.INSTANCE,List.of()),List.of()),new Control.InvocationOutcomes(List.of(new Control.Normal(label(i==3?"exit":"call-"+(i+1)))),Scopes.NoControl.INSTANCE),new Interactions.KnownContract(new Interactions.ContractRef("LAB","1",List.of(O))));}
 public static Operations.Assign copy(int i,int n){var h=header("copy-"+i);return new Operations.Assign(h,new Places.ObjectPlace(op(h,"dest",Operand.Role.VALUE_WRITE),obj((i+1)%n)),new Expressions.FitText(op(h,"fit",Operand.Role.VALUE_READ),new Expressions.Read(op(h,"read",Operand.Role.VALUE_READ),new Places.ObjectPlace(op(h,"src",Operand.Role.VALUE_READ),obj(i%n))),BigInteger.valueOf(8)," "));}
 public static Publication fixture(int n,int possible,int external,int literal,int ops,boolean proof,boolean unknown,String topology,boolean contradiction){
  int count=Math.max(n,possible+external+literal);var vis=unknown?Memory.Visibility.UNKNOWN:Memory.Visibility.PRIVATE;
  var storage=new ArrayList<Memory.Storage>();var objects=new ArrayList<Memory.ObjectDeclaration>();
  for(int i=0;i<n;i++)storage.add(new Memory.Region(new Memory.StorageHeader(base(i),Optional.of(U),Memory.Lifetime.PERSISTENT,vis,O),Optional.of(BigInteger.valueOf(8L*((count+n-1)/n))),Optional.empty()));
  for(int i=0;i<count;i++)objects.add(new Memory.ObjectDeclaration(obj(i),Optional.empty(),Types.known(Types.Builtin.TEXT),new Memory.ViewBinding(base(i%n),BigInteger.valueOf(8L*(i/n)),BigInteger.valueOf(8),new Memory.ExtensionCodec("text.ebcdic.ibm1047","1",Types.known(Types.Builtin.TEXT))),vis,O,Evidence.CoverageStatus.MODELED,precision()));
  var conditions=new ArrayList<Entries.InitialCondition>();
  for(int i=0;i<possible+external+literal;i++){
   var owner=new EntryOwner(E);var place=new Places.ObjectPlace(operand(owner,"place-"+i,Operand.Role.VALUE_WRITE),obj(contradiction?0:i));var value=new Expressions.Literal(operand(owner,"literal-"+i,Operand.Role.VALUE_READ),new Values.TextValue(String.format("PROG%04d",i)));
   Entries.InitialValue iv=i<possible?new Entries.PossibleLiterals(List.of(value),GAP):i<possible+external?new Entries.ExternalUnknown(GAP):new Entries.LiteralInitial(value);
   conditions.add(new Entries.InitialCondition(place,iv,O,List.of()));
  }
  var sequences=new ArrayList<Sequence>();var instructions=new ArrayList<Instruction>();
  for(int i=0;i<(topology.equals("diamonds")?Math.min(ops,7):topology.equals("control")?0:ops);i++)instructions.add(topology.equals("copies")||topology.equals("diamonds")?copy(i,count):new Operations.Nop(header("nop-"+i)));
  var bh=header("branch");sequences.add(seq("start",instructions,new Operations.Branch(bh,new Expressions.Unknown(op(bh,"predicate",Operand.Role.PREDICATE),Types.known(Types.Builtin.BOOL),List.of(),Scopes.NoMemory.INSTANCE,GAP),label("left"),label("right"))));
  String next=(topology.equals("diamonds")||topology.equals("control"))&&ops>=100?"d0":"call-0";
  sequences.add(seq("left",List.of(),jump("jump-left",next)));sequences.add(seq("right",List.of(),jump("jump-right",next)));
  if(!next.equals("call-0"))for(int k=0;k<330;k++){
   var h=header("diamond-"+k);String after=k==329?"call-0":"d"+(k+1);
   sequences.add(seq("d"+k,List.of(),new Operations.Branch(h,new Expressions.Unknown(op(h,"condition",Operand.Role.PREDICATE),Types.known(Types.Builtin.BOOL),List.of(),Scopes.NoMemory.INSTANCE,GAP),label("a"+k),label("b"+k))));
   sequences.add(seq("a"+k,List.of(),jump("ja"+k,after)));sequences.add(seq("b"+k,List.of(),jump("jb"+k,after)));
  }
  for(int i=0;i<4;i++)sequences.add(seq("call-"+i,List.of(),call(i,i%Math.max(1,possible+literal))));
  sequences.add(seq("exit",List.of(),new Operations.Return(header("return"),List.of())));
  var entry=new Entries.Entry(E,Optional.of(label("start")),signature(),new Entries.EntryState(conditions,List.of()),O);
  var unit=new Unit(U,Optional.empty(),objects,List.of(),List.of(entry),sequences,List.of(),Unit.BodyAvailability.AVAILABLE,Optional.empty(),cov(new Scopes.UnitScope(U)),O);
  var premises=proof&&n>1?List.of(new Proofs.Premise(new PremiseId(P,"separation"),"EP-LAB-01 synthetic model","Independent allocations explicitly stipulated in the synthetic model",O,new Proofs.DisjointStorage(storage.stream().map(s->s.header().id()).toList()))):List.<Proofs.Premise>of();
  return new Publication(P,SemanticVersion.AIR_2_0_0,new Capabilities.Manifest(possible>0?List.of(Capabilities.MEMORY_REGIONS,Capabilities.IBM1047,Capabilities.ENTRY_POSSIBILITIES_V2):List.of(Capabilities.MEMORY_REGIONS,Capabilities.IBM1047),List.of()),List.of(new Origins.Artifact(new ArtifactId(P,"model"),"EP-LAB-01 synthetic model",Optional.empty())),List.of(unit),storage,List.of(),List.of(),List.of(new Origins.Written(O,new ArtifactId(P,"model"),Optional.empty(),List.of(),true)),cov(new Scopes.PublicationScope(P)),List.of(new Evidence.Uncertainty(GAP,"LAB_ENTRY_OPEN",List.of(Evidence.Dimension.VALUES),new Scopes.UnitScope(U),"unknown entry lifecycle",O)),premises);
 }
}
