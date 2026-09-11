#!/usr/bin/env python3
"""Focal W1D semantic/wire gate; checks real nominal test execution, never absence as PASS."""
import json,subprocess,sys,xml.etree.ElementTree as ET
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
SELECTOR='BuildCfgContractTest,StructureTest,ValuesTest,CompositionTest,NameInterpreterTest,W1d*Test,DependencyCliTest'
EXPECTED={'analysis-adapters':{'W1dBoundaryTest':3,'W1dEffectsTest':8,'W1dDependencyTest':3,'W1dAdversarialTest':9,'W1dModelTest':4},'analysis-launcher':{'DependencyCliTest':4},'analysis-dependencies':{'NameInterpreterTest':2}}
def reports():
    total=0
    for module,suites in EXPECTED.items():
        for name,count in suites.items():
            paths=list((ROOT/module/'target/surefire-reports').glob('TEST-*.'+name+'.xml'))
            if len(paths)!=1:raise ValueError('missing nominal W1D report: '+name)
            doc=ET.parse(paths[0]).getroot();cases=doc.findall('testcase')
            if len(cases)!=count or int(doc.attrib['tests'])!=count or any(int(doc.attrib[k]) for k in ('errors','failures','skipped')):raise ValueError('failed/missing/skipped W1D test: '+name)
            total+=count
    return total
def run():
    subprocess.run(['mvn','-B','-ntp','-pl','cfg-adapters','-am','test','-Dtest=BuildCfgContractTest,TransportTest,W1dInvokeWireTest'],cwd=ROOT,check=True)
    subprocess.run(['mvn','-B','-ntp','-pl','analysis-launcher','-am','clean','test','-Dtest='+SELECTOR,'org.apache.maven.plugins:maven-dependency-plugin:3.8.1:build-classpath','-DincludeScope=runtime','-Dmdep.outputFile=target/runtime-classpath.txt'],cwd=ROOT,check=True)
    from check_w1d_boundary import check
    check(ROOT)
    count=reports();subprocess.run([sys.executable,'-B','scripts/project/test_dependency_wire.py'],cwd=ROOT,check=True)
    wire=ET.parse(ROOT/'cfg-adapters/target/surefire-reports/TEST-io.github.gustavo2358.analysis.cfg.adapters.W1dInvokeWireTest.xml').getroot()
    if int(wire.attrib['tests'])!=5 or any(int(wire.attrib[k]) for k in ('failures','errors','skipped')):raise ValueError('CFG Invoke wire test missing/failed')
    print('PASS: W1D '+str(count+5)+' nominal tests; CFG, effects/fixpoint, values, consumer, planning, scale, CLI and strict independent wire')
if __name__=='__main__':run()
