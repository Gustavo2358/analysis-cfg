#!/usr/bin/env python3
"""Focused compiling mutants, independent JUnit launch and byte-exact restoration.
Run only in the campaign's isolated checkout, with no concurrent build/consumer.
"""
import argparse,os,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
RUNNER='''import java.io.PrintWriter;
import org.junit.platform.launcher.core.*;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
public class CicsChallengeRunner {
 public static void main(String[] args) {
  var listener=new SummaryGeneratingListener();var launcher=LauncherFactory.create();launcher.registerTestExecutionListeners(listener);
  var request=LauncherDiscoveryRequestBuilder.request();for(var name:args)request.selectors(selectClass(name));launcher.execute(request.build());
  var summary=listener.getSummary();summary.printTo(new PrintWriter(System.out,true));summary.printFailuresTo(new PrintWriter(System.out,true));
  if(summary.getTestsFoundCount()==0)System.exit(2);if(summary.getTotalFailureCount()>0)System.exit(1);
 }
}'''
MUTANTS=[
('drop-known-remainder','analysis-kernel','analysis-kernel/src/main/java/io/github/gustavo2358/analysis/structure/OpenControl.java',
 'instanceof Operations.Invoke i)return i.outcomes().remainder();','instanceof Operations.Invoke i && i.outcomes().known().isEmpty())return i.outcomes().remainder();'),
('borrow-cobol-name-policy','analysis-dependencies','analysis-dependencies/src/main/java/io/github/gustavo2358/analysis/dependencies/CicsNameInterpreter.java',
 'if(computed&&raw.length()!=8)', 'if(raw!=null)return CallNameInterpreter.interpret(raw,computed,Interactions.ExactName.INSTANCE);\n        if(computed&&raw.length()!=8)'),
('query-after-effects','analysis-dependencies','analysis-dependencies/src/main/java/io/github/gustavo2358/analysis/dependencies/CallDependencyPlan.java',
 'ProgramPoint.before(site.entry(),site.operationId()),object.object()', 'ProgramPoint.after(site.entry(),site.operationId()),object.object()')]

def run(a):
 a.work.mkdir(parents=True,exist_ok=False)
 jars=[]
 for artifact,version in [('junit-platform-launcher','1.12.2'),('junit-platform-engine','1.12.2'),('junit-platform-commons','1.12.2')]:jars+=list((a.m2/'org/junit/platform'/artifact/version).glob('*.jar'))
 for artifact in ['junit-jupiter-api','junit-jupiter-engine']:jars+=list((a.m2/'org/junit/jupiter'/artifact/'5.12.2').glob('*.jar'))
 jars+=list((a.m2/'org/opentest4j/opentest4j/1.3.0').glob('*.jar'))+list((a.m2/'org/apiguardian/apiguardian-api/1.1.2').glob('*.jar'))
 paths=[str(a.work)]+[str(p) for pattern in ['*/target/classes','*/target/test-classes'] for p in ROOT.glob(pattern)]+[str(j) for j in jars]+[(ROOT/'analysis-adapters/target/architecture-classpath.txt').read_text().strip()]
 cp=os.pathsep.join(paths);java=a.work/'CicsChallengeRunner.java';java.write_text(RUNNER)
 subprocess.run(['javac','-cp',cp,str(java)],check=True)
 command=['java','-cp',cp,'CicsChallengeRunner','io.github.gustavo2358.analysis.adapters.CicsInvokeRouteTest','io.github.gustavo2358.analysis.adapters.CicsTargetTimingTest']
 def execute(name,cmd):
  with (a.work/(name+'.log')).open('w') as log:return subprocess.run(cmd,cwd=ROOT,stdout=log,stderr=subprocess.STDOUT).returncode
 def compile(module,label):
  rc=execute(label,['mvn','-o','-B','-ntp','-Dmaven.repo.local='+str(a.m2),'-pl',module,'compile']);assert rc==0,'mutant must compile: '+label
 assert execute('first-green',command)==0,'first GREEN required'
 originals={path:(ROOT/path).read_bytes() for _,_,path,_,_ in MUTANTS}
 try:
  for name,module,path,old,new in MUTANTS:
   source=originals[path].decode();assert source.count(old)==1,'unique mutation site '+name
   (ROOT/path).write_text(source.replace(old,new));compile(module,name+'-compile')
   assert execute(name,command)==1,'mutation escaped '+name
   text=(a.work/(name+'.log')).read_text();assert 'Failures (' in text and 'ExceptionInInitializerError' not in text,'semantic test failure required'
   print('DETECTED',name,flush=True)
   (ROOT/path).write_bytes(originals[path]);compile(module,name+'-restore')
   assert (ROOT/path).read_bytes()==originals[path]
  assert execute('second-green',command)==0,'restored GREEN required'
  print('PASS focused mutations, exact restoration and second GREEN',flush=True)
 finally:
  for path,data in originals.items():
   if (ROOT/path).read_bytes()!=data:(ROOT/path).write_bytes(data)
if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('--work',type=Path,required=True);p.add_argument('--m2',type=Path,required=True);a=p.parse_args();a.work=a.work.resolve();a.m2=a.m2.resolve();run(a)
