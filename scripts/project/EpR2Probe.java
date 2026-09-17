import io.github.gustavo2358.air.model.*;
import io.github.gustavo2358.air.json.AirJson;
import io.github.gustavo2358.air.validation.*;
import io.github.gustavo2358.analysis.cfg.application.*;
import io.github.gustavo2358.analysis.cfg.extension.*;
import io.github.gustavo2358.analysis.structure.*;
import io.github.gustavo2358.analysis.storage.*;
import io.github.gustavo2358.analysis.rd.*;
import io.github.gustavo2358.analysis.values.*;
import io.github.gustavo2358.analysis.dependencies.*;
import io.github.gustavo2358.analysis.adapters.*;
import io.github.gustavo2358.analysis.launcher.AnalysisDependencies;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;
import static io.github.gustavo2358.analysis.values.EpR2Fixtures.*;

/** Selected synthetic LAB verticals, exercising the actual AIR codec and dependency CLI. */
public final class EpR2Probe {
    private EpR2Probe() { }
    static Publication replace(Publication p,List<Entries.InitialCondition> conditions,List<Sequence> sequences) {
        var u=p.units().getFirst();var e=u.entries().getFirst();
        var ee=new Entries.Entry(e.id(),e.initialLabel(),e.signature(),new Entries.EntryState(conditions,e.state().uncertainties()),e.origin());
        var uu=new Unit(u.id(),u.containingUnit(),u.objects(),u.visibleObjects(),List.of(ee),sequences,u.completionPorts(),u.body(),u.bodyUnavailable(),u.coverage(),u.origin());
        return new Publication(p.id(),p.airVersion(),p.capabilities(),p.artifacts(),List.of(uu),p.storage(),p.resources(),p.artifactRelations(),p.origins(),p.coverage(),p.uncertainties(),p.premises());
    }
    static Publication model(String name) {
        if(name.equals("F1"))return fixture(2,0,2,0,0,false,true,"control",false);
        if(name.equals("F5"))return fixture(2,0,0,2,0,false,true,"control",true);
        if(name.startsWith("F-order-")) {
            var p=fixture(3,1,2,0,0,false,true,"control",false);var u=p.units().getFirst();var c=u.entries().getFirst().state().conditions();
            var order=name.substring("F-order-".length()).chars().map(i->i-'0').mapToObj(c::get).toList();
            return replace(p,order,u.sequences());
        }
        if(name.equals("H2")) {
            var p=fixture(3,0,2,1,0,false,true,"control",false);var u=p.units().getFirst();
            var sequences=u.sequences().stream().map(s->s.terminator() instanceof Operations.Invoke i?seq(s.label().localId(),s.instructions(),call(Integer.parseInt(i.header().id().localId().substring(5)),2)):s).toList();
            return replace(p,u.entries().getFirst().state().conditions(),sequences);
        }
        if(name.startsWith("G-"))return fixture(8,Integer.parseInt(name.substring(2)),0,0,7,false,true,"diamonds",false);
        if(name.equals("I"))return fixture(32,0,0,0,32,false,true,"copies",false);
        if(name.equals("E1")||name.equals("E3"))return fixture(8,7,0,0,1000,name.equals("E1"),name.equals("E3"),"diamonds",false);
        if(name.equals("MUST")) {
            var p=fixture(1,1,0,0,0,true,false,"control",false);var u=p.units().getFirst();var h=header("overwrite");
            var write=new Operations.Assign(h,new Places.ObjectPlace(op(h,"dest",Operand.Role.VALUE_WRITE),obj(0)),new Expressions.Literal(op(h,"value",Operand.Role.VALUE_READ),new Values.TextValue("NEWPROG1")));
            var sequences=new ArrayList<>(u.sequences());var first=sequences.getFirst();sequences.set(0,new Sequence(first.label(),List.of(write),first.terminator(),first.origin()));
            return replace(p,u.entries().getFirst().state().conditions(),sequences);
        }
        throw new IllegalArgumentException(name);
    }
    static void require(boolean value,String reason){if(!value)throw new AssertionError(reason);}
    static Map<String,Object> record(Object value)throws ReflectiveOperationException {
        var result=new TreeMap<String,Object>();for(var c:value.getClass().getRecordComponents())result.put(c.getName(),c.getAccessor().invoke(value));return result;
    }
    public static void main(String[] args)throws Exception {
        var json=new ObjectMapper();var dir=Path.of(args[0]);Files.createDirectories(dir);String name=args[1];var p=model(name);
        var metrics=new TreeMap<String,Object>();metrics.put("case",name);long start=System.nanoTime();
        var validation=AirValidator.validate(p);metrics.put("validation",validation.status().name());
        if(name.equals("F5")) {
            require(validation.status()==ValidationResult.Status.INVALID_IR&&validation.issues().toString().contains("I-17"),"real contradiction must remain I-17");
            metrics.put("issues",validation.issues().toString());metrics.put("qualified",true);json.writerWithDefaultPrettyPrinter().writeValue(dir.resolve("metrics.json").toFile(),metrics);return;
        }
        require(validation.status()==ValidationResult.Status.STRUCTURALLY_VALID,validation.issues().toString());
        var codec=new AirJson();Files.write(dir.resolve("air.json"),codec.encodeForPartialAnalysis(p).bytes());
        p=codec.decodeForPartialAnalysis(Files.readAllBytes(dir.resolve("air.json"))).publication();
        var options=BuildOptions.defaults();var cfg=new CfgBuildCoordinator(SemanticInterpreterRegistry.empty()).build(p,options);
        var session=AnalysisSession.open(cfg,p,options.projectionPolicy(),p.units().getFirst().entries()).session().orElseThrow();
        metrics.put("airCfgMs",(System.nanoTime()-start)/1e6);start=System.nanoTime();
        var effects=new StatementEffects(new StorageIndex(session));var rd=ReachingDefinitions.prepare(effects);
        metrics.put("rdPrepareMs",(System.nanoTime()-start)/1e6);metrics.put("rdStatus",rd.status().name());require(rd.analysis().isPresent(),rd.reason());start=System.nanoTime();
        var definitions=rd.analysis().orElseThrow().execute();metrics.put("rdSolveMs",(System.nanoTime()-start)/1e6);metrics.put("rdSolver",record(definitions.dataflow().metrics()));start=System.nanoTime();
        var rv=RegionalValuesAnalysis.prepare(session);metrics.put("rvPrepareMs",(System.nanoTime()-start)/1e6);metrics.put("rvStatus",rv.status().name());require(rv.analysis().isPresent(),rv.reason());start=System.nanoTime();
        var values=rv.analysis().orElseThrow().execute();metrics.put("rvSolveMs",(System.nanoTime()-start)/1e6);metrics.put("rvSolver",record(values.dataflow().metrics()));metrics.put("domain",values.solveMetrics());metrics.put("preparation",values.preparationMetrics());start=System.nanoTime();
        var product=new DependencyAnalysis().prepare(p);metrics.put("productMs",(System.nanoTime()-start)/1e6);
        require(!product.partial(),"synthetic supported pipeline unexpectedly partial: "+product.analysisReasons());require(product.sites().size()==4,"four sites");
        String expected=name.equals("H2")?"PROG0002":name.equals("MUST")?"NEWPROG1":"PROG0000";
        boolean empty=name.equals("F1")||name.equals("I");
        var sites=new ArrayList<Map<String,Object>>();
        for(var site:product.sites()) {
            var names=site.candidates().stream().map(DependencySiteFact.Candidate::referenceName).toList();
            require(empty?names.isEmpty():names.equals(List.of(expected)),name+" candidates "+names);
            require(site.effectiveUnknownRemainder()!=name.equals("MUST"),name+" remainder");
            require(site.candidates().stream().allMatch(c->!c.supports().isEmpty()),"unsupported candidate");
            require(site.valuePoint().kind()==io.github.gustavo2358.analysis.query.ProgramPoint.Kind.BEFORE,"BEFORE");
            var row=new TreeMap<String,Object>();row.put("operation",site.operation().localId());row.put("candidates",names);row.put("remainder",site.effectiveUnknownRemainder());row.put("supports",site.candidates().stream().map(c->c.supports().stream().map(s->s.producer().localId()).sorted().toList()).toList());sites.add(row);
        }
        metrics.put("sites",sites);
        new DependencyFileWriter().write(product,dir.resolve("dependencies-memory.json"));
        require(AnalysisDependencies.run(new String[]{dir.resolve("air.json").toString(),dir.resolve("dependencies.json").toString()},System.err)==0,"CLI failed");
        require(Arrays.equals(Files.readAllBytes(dir.resolve("dependencies-memory.json")),Files.readAllBytes(dir.resolve("dependencies.json"))),"memory/CLI wire mismatch");
        metrics.put("qualified",true);json.writerWithDefaultPrettyPrinter().writeValue(dir.resolve("metrics.json").toFile(),metrics);
        System.out.println(name+" QUALIFIED "+values.solveMetrics());
    }
}
