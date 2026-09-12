#!/usr/bin/env python3
"""Compiling semantic mutants, exact restoration and a second GREEN. Never accepts compiler failures."""
import argparse,hashlib,json,subprocess,xml.etree.ElementTree as ET
from pathlib import Path
from check_w1d import SELECTOR,EXPECTED
ROOT=Path(__file__).resolve().parents[2]
CFG='cfg-kernel/src/main/java/io/github/gustavo2358/analysis/cfg/domain/CoreCfgProjection.java'
VAL='analysis-values/src/main/java/io/github/gustavo2358/analysis/values/'
DEP='analysis-dependencies/src/main/java/io/github/gustavo2358/analysis/dependencies/'
MUTANTS=[
 ('invoke-unsupported',CFG,'return invoke.outcomes().known().size() == 1','return false && invoke.outcomes().known().size() == 1'),
 ('invoke-generic-jump',CFG,'CfgTransition.Kind.INVOKE_NORMAL, entry.id()','CfgTransition.Kind.JUMP, entry.id()'),
 ('physical-next-sequence',CFG,'sequences.get(normal.label()).id()','sequences.get(unit.sequences().get((unit.sequences().indexOf(sequence)+1)%unit.sequences().size()).label()).id()'),
 ('fabricated-callee-entry-edge',CFG,'var normal = (Control.Normal) invoke.outcomes().known().getFirst();','var normal = (Control.Normal) invoke.outcomes().known().getFirst(); transitions.add(new CfgTransition(from, entryNode.id(), CfgTransition.Kind.INVOKE_NORMAL, entry.id()));'),
 ('effect-as-nop',VAL+'TextProfile.java','if(effect!=null)return effect.apply(state,work);','if(effect!=null)return state;'),
 ('may-write-strong-kill',VAL+'ForeignEffectTransfer.java','previous.withOpen(work)','Candidates.UNKNOWN'),
 ('effect-only-in-replay',VAL+'PossibleValuesAnalysis.java','return profile.transferOperation(state,node.source().terminator(),work);','return state;'),
 ('before-to-after',DEP+'CallDependencyPlan.java','ProgramPoint.before(site.entry(),site.operationId()),object.object()','ProgramPoint.after(site.entry(),site.operationId()),object.object()'),
 ('dynamic-as-literal',DEP+'CallDependencyConsumer.java','computed?TargetKind.COMPUTED:TargetKind.LITERAL','TargetKind.LITERAL'),
 ('wrong-object',DEP+'CallDependencyPlan.java','site.operationId()),object.object()','site.operationId()),new ObjectId(object.object().unit(),"object-0")'),
 ('lost-raw-padding',DEP+'CallDependencyConsumer.java','new RawCandidate(candidate.candidate().value(),','new RawCandidate(candidate.candidate().value().stripTrailing(),'),
 ('values-canonicalizes-name',VAL+'TextProfile.java','universe.supported(text,assign.header().id()','universe.supported(new Values.TextValue(text.value().stripTrailing()),assign.header().id()'),
 ('globalized-candidate-support',DEP+'CallDependencyConsumer.java','candidate.producers().stream().map(s->new Support','value.candidateSupports().stream().flatMap(c->c.producers().stream()).map(s->new Support'),
 ('literal-starts-values',DEP+'CallDependencyPlan.java','var interest=new SiteInterest','if(group==0)keys.add(values.analysisKey()); var interest=new SiteInterest'),
 ('unreachable-produces-edge',DEP+'ReachabilityProvider.java','new Fact(reached.contains(q.subject()),sourceOpen)','new Fact(true,sourceOpen)'),
 ('source-remainder-dropped',DEP+'CallDependencyConsumer.java','source=value.sourceUnknownRemainder();','source=false;'),
 ('interpretation-remainder-dropped',DEP+'CallDependencyConsumer.java','model,source,interpretation,Boolean.TRUE.equals(model)||source||interpretation','model,source,false,Boolean.TRUE.equals(model)||source'),
 ('old-profile-admits-invoke',VAL+'PossibleValuesAnalysis.java','new TextProfile(session,EFFECTS_PROFILE.equals(profile))','new TextProfile(session,true)'),
]
SCOPE=[('default-value-plan-call-logic','analysis-dataflow/src/main/java/io/github/gustavo2358/analysis/dataflow/DefaultValuePlan.java','\n    private static boolean forbiddenCallLogic(io.github.gustavo2358.air.model.Operation op) { return op instanceof io.github.gustavo2358.air.model.Operations.Invoke; }\n'),('solver-source-change','analysis-kernel/src/main/java/io/github/gustavo2358/analysis/solver/DataflowSolver.java','\n// Unauthorized solver production delta.\n')]
def sha(data):return hashlib.sha256(data).hexdigest()
def execute(work,name,args):
    p=subprocess.run(args,cwd=ROOT,stdout=subprocess.PIPE,stderr=subprocess.STDOUT);path=work/(name+'.log');path.write_bytes(p.stdout)
    return p.returncode,p.stdout.decode(errors='replace'),dict(argv=args,exitCode=p.returncode,log=path.name,sha256=sha(p.stdout))
def run(work,selected=None):
    work.mkdir(parents=True,exist_ok=False);all_paths={row[1] for row in MUTANTS+SCOPE};original={p:(ROOT/p).read_bytes() for p in all_paths};results=[]
    command=['mvn','-B','-ntp','-pl','analysis-launcher','-am','test','-Dtest='+SELECTOR]
    try:
        rc,_,green=execute(work,'first-green',command)
        if rc:raise ValueError('first GREEN failed')
        for name,path,before,after in MUTANTS:
            if selected and name not in selected:continue
            text=original[path].decode()
            if text.count(before)!=1:raise ValueError('non-unique mutation site: '+name)
            mutant=text.replace(before,after).encode();(ROOT/path).write_bytes(mutant)
            for module in EXPECTED:
                for report in (ROOT/module/'target/surefire-reports').glob('TEST-*.xml'):report.unlink()
            rc,output,receipt=execute(work,name,command)
            failed=[]
            for module,suites in EXPECTED.items():
                for suite in suites:
                    for report in (ROOT/module/'target/surefire-reports').glob('TEST-*.'+suite+'.xml'):
                        root=ET.parse(report).getroot()
                        for case in root.findall('testcase'):
                            finding=case.find('failure')
                            if finding is None:finding=case.find('error')
                            if finding is not None:failed.append(dict(suite=suite,test=case.attrib['name'],type=finding.attrib.get('type'),message=finding.attrib.get('message','')[:1000]))
                        if any(int(root.attrib[k]) for k in ('failures','errors')):(work/report.name.replace('TEST-',name+'-')).write_bytes(report.read_bytes())
            (ROOT/path).write_bytes(original[path]);restored=(ROOT/path).read_bytes()==original[path]
            row=dict(name=name,path=path,originalSha256=sha(original[path]),mutatedSha256=sha(mutant),byteExactRestore=restored,execution=receipt,semanticFailures=failed)
            results.append(row);(work/'progress.json').write_text(json.dumps(results,indent=2)+'\n')
            if not rc or 'COMPILATION ERROR' in output or not failed or not restored:raise ValueError('mutant NOT semantically detected: '+name)
            print('DETECTED '+name+'; compiling semantic test failure; byte-exact restore',flush=True)
        for name,path,addition in SCOPE:
            if selected and name not in selected:continue
            changed=original[path].rstrip();changed=changed[:-1]+addition.encode()+b'}\n' if name=='default-value-plan-call-logic' else original[path]+addition.encode();(ROOT/path).write_bytes(changed);rc,output,receipt=execute(work,name,['python3','-B','scripts/project/check_w1d_boundary.py' if name=='default-value-plan-call-logic' else 'scripts/project/w1d_scope.py']);(ROOT/path).write_bytes(original[path])
            if rc==0 or 'protected source' not in output:raise ValueError('scope mutation escaped: '+name)
            results.append(dict(name=name,path=path,byteExactRestore=(ROOT/path).read_bytes()==original[path],execution=receipt,detection='SCOPE_GATE'))
        rc,_,second=execute(work,'second-green',command)
        if rc:raise ValueError('second GREEN failed')
        from check_w1d import reports
        count=reports();receipt=dict(schema='w1d-mutations@1',firstGreen=green,mutants=results,secondGreen=second,secondGreenNominalTests=count,compilerFailuresAccepted=False,restoration={p:sha((ROOT/p).read_bytes()) for p in sorted(original)})
        (work/'receipt.json').write_text(json.dumps(receipt,indent=2)+'\n');print('PASS: '+str(len(results))+' mutations; exact restoration; second GREEN',flush=True)
    finally:
        for p,data in original.items():(ROOT/p).write_bytes(data)
if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--work',type=Path,required=True);p.add_argument('--only',nargs='+');a=p.parse_args();run(a.work.resolve(),a.only)
