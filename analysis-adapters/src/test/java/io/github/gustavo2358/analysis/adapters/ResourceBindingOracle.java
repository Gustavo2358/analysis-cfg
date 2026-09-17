package io.github.gustavo2358.analysis.adapters;

import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.model.Ids.*;
import io.github.gustavo2358.air.model.Unit;
import java.util.*;

/** A1/A2/A3/A4/A6: manual facts, independent of any frontend/lower or codec. */
final class ResourceBindingOracle {
    private ResourceBindingOracle() {}
    static final PublicationId PUB=new PublicationId("fd-w1-manual");
    static final OriginId ORIGIN=new OriginId(PUB,"source");
    static final UncertaintyId SIGNATURE=new UncertaintyId(PUB,"signature");
    static final UncertaintyId GAP=new UncertaintyId(PUB,"facts");
    static UnitId unit(String name){return new UnitId(PUB,name);}
    static ObjectId object(String u,String n){return new ObjectId(unit(u),n);}
    static OperationId operation(String u,int n){return new OperationId(unit(u),"io-"+n);}
    static Evidence.Coverage coverage(){return new Evidence.Coverage(Evidence.InventoryStatus.PARTIAL,new Scopes.PublicationScope(PUB),List.of(),List.of(GAP));}
    static Evidence.Precision precision(){var c=new Evidence.Claim(new Scopes.PublicationScope(PUB),Evidence.PrecisionStatus.OPEN,List.of(GAP));return new Evidence.Precision(c,c,c,c,c);}
    static Operations.Header header(OperationId id){return new Operations.Header(id,ORIGIN,Evidence.CoverageStatus.ABSTRACTED,precision(),List.of(GAP));}
    static Interactions.Signature signature(){return new Interactions.Signature(new Interactions.ParameterInventory(List.of(),Interactions.NoRemainder.INSTANCE),new Interactions.ResultInventory(List.of(),Interactions.NoRemainder.INSTANCE),ORIGIN);}
    static Interactions.LiteralTarget target(String n){return new Interactions.LiteralTarget("file","cobol.external-file-name",n,Interactions.ExactName.INSTANCE,ORIGIN);}
    static Interactions.Resource declaration(String id,String u,String name,Interactions.ResourceDescription description,String classification,List<Interactions.ResourceUse> uses){
        return new Interactions.Resource(new ResourceId(PUB,id),description,ORIGIN,Optional.of(new Interactions.ResourceDeclaration(unit(u),name,classification,description instanceof Interactions.LocalResource?"cobol.sort-comment":"cobol.assignment-name",List.of(new Interactions.ResourceObject(object(u,"record"),"record")),uses)));
    }
    static Publication publication(String example){
        var units=new ArrayList<Unit>();var storage=new ArrayList<Memory.Storage>();var resources=new ArrayList<Interactions.Resource>();
        for(String name:example.equals("A3")?List.of("U1","U2"):List.of("U1")){
            var uid=unit(name);var objects=new ArrayList<Memory.ObjectDeclaration>();
            for(String obj:List.of("record","from")){
                var sid=new StorageId(PUB,name+"-"+obj);var type=Types.known(Types.Builtin.TEXT);
                storage.add(new Memory.Cell(new Memory.StorageHeader(sid,Optional.of(uid),Memory.Lifetime.ACTIVATION,Memory.Visibility.PRIVATE,ORIGIN),type));
                objects.add(new Memory.ObjectDeclaration(object(name,obj),Optional.of(obj),type,new Memory.CellBinding(sid),Memory.Visibility.PRIVATE,ORIGIN,Evidence.CoverageStatus.MODELED,precision()));
            }
            int count=switch(example){case "A2","A4"->1;case "A6"->3;default->0;};
            var sequences=new ArrayList<Sequence>();
            for(int n=0;n<count;n++){
                var op=operation(name,n);Interactions.Target t=target(example.equals("A6")?List.of("INA","INB","OUTC").get(n):"CLIENTDD");
                if(example.equals("A4")){
                    var own=new OperationOwner(op);
                    var read=new Expressions.Read(new Operand.Header(new OperandId(own,"name"),Operand.Role.CALL_TARGET,ORIGIN),new Places.ObjectPlace(new Operand.Header(new OperandId(own,"place"),Operand.Role.VALUE_READ,ORIGIN),object(name,"from")));
                    t=new Interactions.ComputedTarget("file","cics.file",read,Interactions.ExactName.INSTANCE,ORIGIN);
                }
                var args=new ArrayList<Interactions.Argument>();
                if(example.equals("A2"))args.add(new Interactions.ValueArgument(new Expressions.Read(new Operand.Header(new OperandId(new OperationOwner(op),"from-read"),Operand.Role.ARGUMENT_VALUE,ORIGIN),new Places.ObjectPlace(new Operand.Header(new OperandId(new OperationOwner(op),"from-place"),Operand.Role.VALUE_READ,ORIGIN),object(name,"from")))));
                var sig=example.equals("A2")?new Interactions.Signature(new Interactions.ParameterInventory(List.of(),new Interactions.UnknownRemainder(SIGNATURE)),signature().results(),ORIGIN):signature();
                var invoke=new Operations.Invoke(header(op),example.equals("A2")||n==2?"write":"read",t,args,List.of(),new Interactions.ExternalSignature(sig),List.of(),new Interactions.EffectBound(new Interactions.ForeignEffects(new Scopes.WithinMemory(new Scopes.VisibleMemory(uid,true)),new Scopes.WithinMemory(new Scopes.VisibleMemory(uid,true)),List.of()),List.of()),new Control.InvocationOutcomes(List.of(new Control.Normal(new LabelId(uid,"s"+(n+1)))),new Scopes.WithinControl(new Scopes.UnitControl(uid,true,true,true,true,true,true))),new Interactions.UnknownContract(GAP));
                sequences.add(new Sequence(new LabelId(uid,"s"+n),List.of(),invoke,ORIGIN));
            }
            sequences.add(new Sequence(new LabelId(uid,"s"+count),List.of(),new Operations.Return(header(new OperationId(uid,"return")),List.of()),ORIGIN));
            var entry=new Entries.Entry(new EntryId(uid,"entry"),Optional.of(new LabelId(uid,"s0")),signature(),new Entries.EntryState(List.of(),List.of(GAP)),ORIGIN);
            units.add(new Unit(uid,Optional.empty(),objects,List.of(),List.of(entry),sequences,List.of(),Unit.BodyAvailability.AVAILABLE,Optional.empty(),coverage(),ORIGIN));
            if(!example.equals("A4")&&!example.equals("A6"))resources.add(declaration(name+"-F",name,"F",target("CLIENTDD"),"cobol.fd",count==0?List.of():List.of(new Interactions.ResourceUse(operation(name,0),"output",ORIGIN))));
            if(example.equals("A2")) resources.add(new Interactions.Resource(new ResourceId(PUB,"U1-G"),target("OTHERDD"),ORIGIN,Optional.of(new Interactions.ResourceDeclaration(uid,"G","cobol.fd","cobol.assignment-name",List.of(new Interactions.ResourceObject(object(name,"from"),"record")),List.of()))));
            if(example.equals("A6")){
                for(int n=0;n<3;n++)resources.add(declaration("resource-"+n,name,List.of("A","B","C").get(n),target(List.of("INA","INB","OUTC").get(n)),"cobol.fd",List.of(new Interactions.ResourceUse(operation(name,n),n==2?"output":"input",ORIGIN))));
                resources.add(declaration("sort-work",name,"S",new Interactions.LocalResource("file"),"cobol.sd",List.of(new Interactions.ResourceUse(operation(name,0),"work",ORIGIN))));
            }
        }
        var artifact=new ArtifactId(PUB,"manual");
        return new Publication(PUB,SemanticVersion.AIR_2_0_0,new Capabilities.Manifest(List.of(Capabilities.RESOURCE_BINDINGS),List.of()),List.of(new Origins.Artifact(artifact,"manual",Optional.empty())),units,storage,resources,List.of(),List.of(new Origins.Written(ORIGIN,artifact,Optional.empty(),List.of(),false)),coverage(),List.of(new Evidence.Uncertainty(GAP,"CONTRACT_UNKNOWN",List.of(Evidence.Dimension.values()),new Scopes.PublicationScope(PUB),"Effects/control not established; names remain exact",ORIGIN),new Evidence.Uncertainty(SIGNATURE,"SIGNATURE_UNKNOWN",List.of(Evidence.Dimension.VALUES),new Scopes.PublicationScope(PUB),"Signature remainder deliberately open",ORIGIN)),List.of());
    }
}
