#!/usr/bin/env python3
"""Focal compiled mutations, nominal RED, byte-exact restoration and individual second GREEN."""
import gzip,hashlib,json,os,subprocess,sys
from pathlib import Path
from resource_limit_scope import PRODUCTION
ROOT=Path(__file__).resolve().parents[2]
COORD='cfg-kernel/src/main/java/io/github/gustavo2358/analysis/cfg/application/CfgBuildCoordinator.java'
FLOW='analysis-dataflow/src/main/java/io/github/gustavo2358/analysis/dataflow/AnalysisDataflow.java'
CLI='analysis-launcher/src/main/java/io/github/gustavo2358/analysis/launcher/AnalysisDataflow.java'
ASSIGN='status = CfgBuildResult.Status.RESOURCE_LIMIT;'
MUTATIONS={
'generic-fallthrough':(COORD,ASSIGN,'status = CfgBuildResult.Status.INCOMPLETE_VALIDATION;','expected: <RESOURCE_LIMIT>'),
'unsupported':(COORD,ASSIGN,'status = CfgBuildResult.Status.UNSUPPORTED_CAPABILITY;','expected: <RESOURCE_LIMIT>'),
'invalid':(COORD,ASSIGN,'status = CfgBuildResult.Status.INVALID_IR;','expected: <RESOURCE_LIMIT>'),
'collapse-validation-limit':(COORD,ASSIGN,'status = CfgBuildResult.Status.VALIDATION_LIMIT;','expected: <RESOURCE_LIMIT>'),
'allows-cfg-product':(COORD,ASSIGN,'graph = Optional.of(CoreCfgProjection.project(publication)); status = CfgBuildResult.Status.CFG_BUILT;','CFG product disagrees with preflight'),
'generic-incomplete-as-resource':(COORD,'status = CfgBuildResult.Status.INCOMPLETE_VALIDATION;',ASSIGN,'expected: <INCOMPLETE_VALIDATION>'),
'starts-session':(FLOW,'        requireBuilt(cfg);','        AnalysisSession.open(cfg,publication,options.projectionPolicy(),List.of());\n        requireBuilt(cfg);','resource failure starts analysis'),
'source-open-result':(FLOW,'        requireBuilt(cfg);','''        if(cfg.status()==CfgBuildResult.Status.RESOURCE_LIMIT) {
            var fallback=prepare(publication,resultId,BuildOptions.defaults());
            var open=new HashMap<io.github.gustavo2358.air.model.Ids.EntryId,PreparedDataflowResult.SourceScope>();
            fallback.sourceScopes().forEach((id,scope)->open.put(id,new PreparedDataflowResult.SourceScope(
                    Evidence.InventoryStatus.PARTIAL,Evidence.InventoryStatus.PARTIAL,scope.entryUncertaintyRefs())));
            return new PreparedDataflowResult(fallback.result(),open,fallback.subjectCells(),fallback.compositionMetrics());
        }
        requireBuilt(cfg);''','Expected io.github.gustavo2358.analysis.dataflow.AnalysisDataflow.PreparationException'),
'cli-unsupported':(CLI,'case RESOURCE_LIMIT->"EXTERNAL_RESOURCE_LIMIT"','case RESOURCE_LIMIT->"UNSUPPORTED_PROFILE"','operational category'),
}
def sha(data):return hashlib.sha256(data).hexdigest()
def campaign(out):
    out.mkdir(parents=True,exist_ok=False)
    originals={p:(ROOT/p).read_bytes() for p in PRODUCTION}
    record={'schema':'resource-limit-mutations','source_head':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),'original_sha256':{p:sha(b) for p,b in originals.items()},'attempts':[]}
    selector='CfgBuildCoordinatorTest,EvalCfg030Test,CompositionTest,DataflowCliTest,BuildCfgContractTest,StructureTest,ValuesTest,NameInterpreterTest,WireTest'
    maven=['mvn','-B','-ntp','-pl','analysis-launcher','-am']
    def execute(folder,phase,argv):
        run=subprocess.run(argv,cwd=ROOT,stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
        log=folder/(phase+'.log.gz');log.write_bytes(gzip.compress(run.stdout,mtime=0))
        return {'argv':argv,'exitCode':run.returncode,'log':str(log.relative_to(out)),'sha256':sha(run.stdout)},run.stdout.decode(errors='replace')
    def nominal(folder,phase):
        return execute(folder,phase,maven+['test','-Dtest='+selector])
    def save():(out/'receipt.json').write_text(json.dumps(record,indent=2)+'\n')
    for name,(target,old,new,diagnostic) in MUTATIONS.items():
        folder=out/name;folder.mkdir();row={'name':name,'target':target,'expectedDiagnostic':diagnostic};record['attempts'].append(row)
        row['baseline'],_=nominal(folder,'baseline-green');save()
        if row['baseline']['exitCode']:raise RuntimeError('baseline failed: '+name)
        data=originals[target].decode()
        if data.count(old)!=1:raise RuntimeError('mutation anchor not unique: '+name)
        try:
            mutated=data.replace(old,new,1).encode();(ROOT/target).write_bytes(mutated);row['mutant_sha256']=sha(mutated)
            row['compile'],_=execute(folder,'compile',maven+['clean','package','-DskipTests']);save()
            if row['compile']['exitCode']:row['valid']=False;raise RuntimeError('invalid noncompiling mutant: '+name)
            row['valid']=True
            if name=='starts-session':row['red'],output=execute(folder,'nominal-red',[sys.executable,'-B','scripts/project/resource_limit_scope.py'])
            else:row['red'],output=nominal(folder,'nominal-red')
            if row['red']['exitCode']==0 or diagnostic not in output:raise RuntimeError('mutant survived or wrong RED: '+name)
            row['killed']=True
        finally:
            (ROOT/target).write_bytes(originals[target]);row['restore_byte_exact']=all((ROOT/p).read_bytes()==b for p,b in originals.items());save()
        row['second_green'],_=nominal(folder,'second-green');save()
        if not row['restore_byte_exact'] or row['second_green']['exitCode']:raise RuntimeError('restoration failed: '+name)
        print('[resource mutation] '+name+': compiled / nominal RED / exact restore / second GREEN',flush=True)
    record['status']='PASS';save()
if __name__=='__main__':campaign(Path(sys.argv[1]).resolve())
