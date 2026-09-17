#!/usr/bin/env python3
"""Small W11 fault injections in private compiled overlays of the immutable consumer."""
import argparse,json,os,subprocess
from pathlib import Path
from carddemo_setup import check_snapshot

MUTANTS=[
 ('query-after','FileValueQuery','ProgramPoint.before(site.entry(),site.operationId())','ProgramPoint.after(site.entry(),site.operationId())','FileComputedOracleTest'),
 ('fuse-owner','FileDependencyAnalysis','d==null?null:d.owner()','p.units().getFirst().id()','FileScopeOracleTest'),
 ('lose-record-owner','FileDependencyAnalysis','d==null?List.of():d.objects()','List.of()','FileDependencyTest'),
 ('lose-sort-input','FileDependencyAnalysis','for(var use:d.uses()){','for(var use:d.uses()){if(use.role().equals("input"))continue;','FileDependencyTest'),
 ('assert-dd-binding','FileDependencyAnalysis','case "cobol.assignment-name"->"ASSIGNMENT_NAME"','case "cobol.assignment-name"->"DDNAME"','FileDependencyTest'),
]
RUNNER='''package io.github.gustavo2358.analysis.adapters;
public final class FocalOracleRunner {
 public static void main(String[] names) throws Exception {
  int count=0,failed=0;
  for(String name:names) {
   Class<?> c=Class.forName("io.github.gustavo2358.analysis.adapters."+name);
   var ctor=c.getDeclaredConstructor();ctor.setAccessible(true);Object o=ctor.newInstance();
   for(var m:c.getDeclaredMethods())if(m.isAnnotationPresent(org.junit.jupiter.api.Test.class)) {
    count++;m.setAccessible(true);
    try {m.invoke(o);System.out.println("PASS "+name+"."+m.getName());}
    catch(java.lang.reflect.InvocationTargetException e) {failed++;System.out.println("ORACLE_FAILURE "+name+"."+m.getName()+" "+e.getCause());}
   }
  }
  System.out.println("Executed "+count+"; failed "+failed);if(count==0||failed>0)System.exit(1);
 }
}
'''
def execute(command,cwd,log):
    with log.open('w') as out:return subprocess.run(command,cwd=cwd,stdout=out,stderr=subprocess.STDOUT).returncode

def run(work,runtime,maven_repo):
    work.mkdir(parents=True,exist_ok=False);c=json.loads(runtime.read_text());cfg=Path(c['checkouts']['analysis-cfg'])
    for repo,pin in c['sources'].items():check_snapshot(Path(c['checkouts'][repo]),pin)
    cp=c['dependency']['classpath']+[str(cfg/'analysis-adapters/target/test-classes')]
    cache=maven_repo
    for pattern in ('org/junit/jupiter/junit-jupiter-api/*/*.jar','org/junit/platform/junit-platform-commons/*/*.jar','org/opentest4j/opentest4j/*/*.jar','org/apiguardian/apiguardian-api/*/*.jar'):
        cp.extend(str(p) for p in sorted(cache.glob(pattern)))
    runner=work/'runner';runner.mkdir();src=runner/'FocalOracleRunner.java';src.write_text(RUNNER)
    test_source=Path(__file__).resolve().parents[2]/'analysis-adapters/src/test/java/io/github/gustavo2358/analysis/adapters/FileDependencyTest.java'
    assert execute(['javac','-cp',os.pathsep.join(cp),'-d',str(runner),str(src),str(test_source)],work,work/'runner-compile.log')==0
    cp=[str(runner),*cp];main='io.github.gustavo2358.analysis.adapters.FocalOracleRunner';classes=sorted({m[4] for m in MUTANTS})
    baseline=work/'baseline';baseline.mkdir();assert execute(['java','-cp',os.pathsep.join(cp),main,*classes],baseline,baseline/'oracle.log')==0,'unmutated oracles must pass first'
    results=[]
    for name,cls,old,new,test in MUTANTS:
        path=work/name;path.mkdir();text=(cfg/f'analysis-dependencies/src/main/java/io/github/gustavo2358/analysis/dependencies/{cls}.java').read_text()
        assert text.count(old)==1,(name,'mutation anchor must be unique');mutated=path/(cls+'.java');mutated.write_text(text.replace(old,new))
        rc=execute(['javac','-cp',os.pathsep.join(cp),'-d',str(path),str(mutated)],path,path/'compile.log');assert rc==0,(name,'compile failure is not a killed mutant')
        rc=execute(['java','-cp',os.pathsep.join([str(path),*cp]),main,test],path,path/'oracle.log')
        log=(path/'oracle.log').read_text();killed=rc==1 and 'ORACLE_FAILURE' in log and ('AssertionFailedError' in log or 'AssertionError' in log)
        results.append({'mutant':name,'test':test,'exitCode':rc,'result':'KILLED' if killed else 'SURVIVED_OR_INVALID','reason':[s for s in log.splitlines() if s.startswith('ORACLE_FAILURE')]})
        (work/'mutants.json').write_text(json.dumps(results,indent=2)+'\n');print(results[-1],flush=True);assert killed,name
    for repo,pin in c['sources'].items():check_snapshot(Path(c['checkouts'][repo]),pin)
if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--work',type=Path,required=True);p.add_argument('--runtime',type=Path,required=True);p.add_argument('--maven-repo',type=Path,required=True);a=p.parse_args();run(a.work.resolve(),a.runtime.resolve(),a.maven_repo.resolve())
