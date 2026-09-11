#!/usr/bin/env python3
"""W5 production gates: nominal execution/report inventories, compiled boundaries and real S10."""
from __future__ import annotations
import argparse, hashlib, json, os, re, struct, subprocess, sys, tempfile
import xml.etree.ElementTree as ET
from pathlib import Path
from check_w1 import Failure, command
from resource_limit_scope import allows_change, boundaries

ROOT=Path(__file__).resolve().parents[2]
MODULES=['analysis-dataflow','analysis-adapters','analysis-launcher']
NAMES={'analysis-dataflow':['AnalysisDataflow','DefaultValuePlan','ObservedValueFact','PreparedDataflowResult'],
       'analysis-adapters':['DataflowAirReader','DeliveryReceipt','JsonOutput','LocalResultWriter','ReceiptJson','ResultJson','WireIds'],
       'analysis-launcher':['AnalysisDataflow']}
SOURCES={m+'/src/main/java/io/github/gustavo2358/analysis/'+m.removeprefix('analysis-').replace('launcher','launcher')+'/'+n+'.java' for m,names in NAMES.items() for n in names}
TESTS={
 'analysis-dataflow':{'CompositionTest':set('genericOverwriteUsesRealPipelineAndLastProducer noWritesProducesCompleteWithoutInventedStableRun everyTerminatorAndOrphanAreObservedBefore resourcePreflightNeverProducesSemanticResultAndRecovers incompleteAndLegacyPreparationFailuresRemainDistinct'.split())},
 'analysis-adapters':{'WireTest':{'realValueSupportAndSourceRemainderReachWire'},'DeliveryTest':set('completeReceiptBindsExactFinalBytesAndReplacesExistingAtomically controlledFailuresPreservePreparedResultAndExistingDestination resourceExhaustionIsNotACompletedOrSemanticDeliveryOutcome'.split()),'WideResultTest':set('scaleKeepsAllQueriesFactsAndDetachedResult resultBeyondLegacy64MiBHasNoOutputCapacityPolicy'.split()),'WireAdversarialTest':set('unavailableBatchRetainsItsExplicitDependencyReason distinctCandidatesKeepTheirOwnProducerSupport permutedInputRegistrationQueriesCandidatesSupportsAndFactsAreByteStable fullIdentitySeparatesOwnersEvenWhenLocalIdsCollide unsupportedAndUnreachableHaveDifferentExplicitNullSemantics invalidUnicodeIsEncodingFailureAndDoesNotCertifyDelivery'.split())},
 'analysis-launcher':{'DataflowCliTest':set('fileRouteMatchesInMemoryAndReceiptIsSeparate usageMalformedMissingAndOutputFailuresAreDistinct defaultCodecAcceptsBeyondHistoricalCapWithoutLocalReadAdmission realCodecResourceLimitStopsBeforeDeliveryAndRecovers'.split())}}
DENIED=('java.lang.reflect','java.util.ServiceLoader','cobolexplorer','org.antlr','lower.adapters','CallResolver','FileResolver','Db2Resolver','CicsResolver','GrbeResolver','ProgramDependency','CfgJsonWriter','CfgJsonBytes')
POM_ADDITION=b'    <module>analysis-dataflow</module>\n    <module>analysis-adapters</module>\n    <module>analysis-launcher</module>\n'
def original_pom(data):
    if data.count(POM_ADDITION)!=1:raise Failure('W5 parent POM only exact additive modules allowed')
    return data.replace(POM_ADDITION,b'',1)
def verify_sources(root):
    actual={p.relative_to(root).as_posix() for m in MODULES for p in (root/m/'src/main/java').rglob('*.java')}
    if actual!=SOURCES:raise Failure('W5 exact production source inventory')
    for path in SOURCES:
        s=(root/path).read_text()
        if any(x in s for x in DENIED):raise Failure('W5 forbidden dependency: '+path)
        if any(x in s for x in ('"PROGA"','"WS-PGM"','maximumDocumentBytes','maximumBytes','maxQueries','maxFacts','maxConsumers','maxCandidates')):raise Failure('W5 fixture/capacity policy: '+path)
        if path.startswith('analysis-dataflow/') and any(x in s for x in ('java.io','java.nio.file','analysis.adapters','analysis.launcher','air.json')):raise Failure('W5 inner composition knows transport')
    from check_analysis_architecture import check_direct_air
    if check_direct_air(root):raise Failure('W5 direct AIR Maven dependency')
    prior=json.loads((root/'docs/evals/cp5/w4-source-inventory.json').read_text())['files']
    for path,sha in prior.items():
        data=(root/path).read_bytes()
        if path=='pom.xml':data=original_pom(data)
        if hashlib.sha256(data).hexdigest()!=sha and not allows_change(root,path,sha):raise Failure('W5 changed approved W1–W4 or legacy source: '+path)
def capture(root,args):
    p=subprocess.run(args,cwd=root,stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True)
    if p.returncode:
        raise Failure('command failed (exit '+str(p.returncode)+'): '+repr(args)
                      +'\nstdout:\n'+p.stdout+'\nstderr:\n'+p.stderr)
    # Tool diagnostics remain observable but are never part of its product.
    if p.stderr:print(p.stderr,end='',file=sys.stderr)
    return p.stdout
def architecture(root,update=False):
    from check_transport_architecture import dependencies_from_jdeps
    from check_architecture import parse_tgf
    verify_sources(root);boundaries(root);actual={}
    for module in MODULES:
        classes=root/module/'target/classes';cp=(root/module/'target/architecture-classpath.txt').read_text().strip()
        paths=sorted(p.relative_to(classes).as_posix() for p in classes.rglob('*.class'))
        if not paths:raise Failure('W5 classfiles absent')
        for path in paths:
            if struct.unpack('>IHH',(classes/path).read_bytes()[:8])!=(0xcafebabe,0,65):raise Failure('W5 bytecode major/preview')
        edges=dependencies_from_jdeps(capture(root,['jdeps','--multi-release','21','-filter:none','-verbose:class','-cp',cp,str(classes)]))
        for source,targets in edges.items():
            for target in targets:
                if any(x in target for x in DENIED):raise Failure('W5 compiled forbidden dependency: '+target)
                if module=='analysis-dataflow' and any(x in target for x in ('java.io','java.nio.file','analysis.adapters','analysis.launcher','air.json')):raise Failure('W5 compiled inner boundary: '+target)
        descriptors={p:capture(root,['javap','-classpath',str(classes)+os.pathsep+cp,'-public','-s',p[:-6].replace('/','.')]) for p in paths}
        actual[module]=dict(sources=sorted(p for p in SOURCES if p.startswith(module+'/')),classfiles=paths,jdeps_edges={k:sorted(v) for k,v in sorted(edges.items())},javap_descriptors=descriptors,effective_maven=sorted(parse_tgf(root/module/'target/architecture-dependencies.tgf')))
    # Inner artifacts must have no compiled dependency on the new outer layers.
    for module in ('cfg-kernel','analysis-kernel','analysis-values'):
        cp=(root/module/'target/architecture-classpath.txt').read_text().strip()
        raw=capture(root,['jdeps','--multi-release','21','-filter:none','-verbose:class','-cp',cp,str(root/module/'target/classes')])
        if any('io.github.gustavo2358.analysis.'+p in raw for p in ('dataflow.','adapters.','launcher.')):raise Failure('W5 dependency inverted into '+module)
    path=root/'docs/evals/resource-limit-w5-inventory.json'
    if update:path.write_text(json.dumps(actual,indent=2)+'\n')
    elif load(path)!=json.loads(json.dumps(actual)):raise Failure('W5 compiled inventory drift')
    print('[w5-architecture] PASS: explicit source/classfile/javap/jdeps/Maven inventories; inner direction preserved')
def load(path):return json.loads(Path(path).read_text())
def test_inventory(category):return {m:{k:v for k,v in suites.items() if category=='performance' or k!='WideResultTest'} for m,suites in TESTS.items()}
def verify_reports(root,expected):
    for module,suites in expected.items():
        prefix='io.github.gustavo2358.analysis.'+module.removeprefix('analysis-')+'.';paths=list((root/module/'target/surefire-reports').glob('TEST-*.xml'))
        if {p.name for p in paths}!={'TEST-'+prefix+s+'.xml' for s in suites}:raise Failure('W5 nominal report inventory mismatch: '+module)
        for p in paths:
            doc=ET.parse(p).getroot();name=doc.attrib['name'].removeprefix(prefix);cases=doc.findall('testcase')
            if len(cases)!=len(suites[name]) or {c.attrib['name'] for c in cases}!=suites[name]:raise Failure('W5 nominal method inventory mismatch: '+name)
            if any(c.attrib['classname']!=prefix+name or any(c.find(k) is not None for k in ('failure','error','skipped')) for c in cases):raise Failure('W5 failed/skipped/foreign test')
            if int(doc.attrib['tests'])!=len(cases) or any(int(doc.attrib.get(k,'0')) for k in ('failures','errors','skipped')):raise Failure('W5 report counters mismatch')
def verify_metrics(output):
    rows=[json.loads(s) for s in re.findall(r'^W5_METRICS (\{.*\})$',output,re.M)]
    if len(rows)!=3 or {r['N'] for r in rows}!={1000,2000,4000}:raise Failure('W5 N/2N/4N metrics absent/duplicate')
    for r in rows:
        n=r['N']
        for k in ('compositionRuns','cfgBuilds','analysisRuns','deliveryAttempts','deliveryComplete','resultSha256Computed','candidateCardinality'):
            if r[k]!=1:raise Failure('W5 real event count: '+k)
        for k in ('defaultPlanDestinations','defaultPlanAssignVisits','defaultPlanQueries','queryRequests','uniqueQueries','queriesAnswered','factsEmitted','closedInModelResults','producerOccurrences'):
            if r[k]!=n:raise Failure('W5 growing quality/work: '+k)
        for k in ('deliveryFailures','encodingFailures','writeFailures','finalizationFailures','unsupportedQueries','sourceOpenResults','modelOpenResults','effectiveOpenResults','consumerFailures','publicationFailures'):
            if r[k]!=0:raise Failure('W5 scale classification: '+k)
        if r['resultBytesWritten']<=0 or r['elapsedNanos']<=0 or r['retainedLogicalObjects']<=0:raise Failure('W5 measured bytes/time/retention absent')
    large=[int(s) for s in re.findall(r'^W5_LARGE_BYTES (\d+)$',output,re.M)]
    if len(large)!=1 or large[0]<=64*1024*1024:raise Failure('W5 legacy-size contracase absent')
    return dict(measurements=rows,resultBeyondLegacy64MiB=large[0],role='OBSERVATION_ONLY',retention='identity-deduplicated logical objects; not bytes or RSS')
def run(root,category,update=False):
    verify_sources(root)
    if category=='architecture':
        command(root,['mvn','-B','-ntp','-DskipTests','package','org.apache.maven.plugins:maven-dependency-plugin:3.8.1:tree','-Dscope=compile','-DoutputType=tgf','-DoutputFile=target/architecture-dependencies.tgf','org.apache.maven.plugins:maven-dependency-plugin:3.8.1:build-classpath','-DincludeScope=compile','-Dmdep.outputFile=target/architecture-classpath.txt']);architecture(root,update);return
    expected=test_inventory(category);selector=','.join(['BuildCfgContractTest','StructureTest','ValuesTest',*(t for suites in expected.values() for t in suites)])
    output=command(root,['mvn','-B','-ntp','-pl','analysis-launcher','-am','clean','package','-Dtest='+selector,'org.apache.maven.plugins:maven-dependency-plugin:3.8.1:build-classpath','-DincludeScope=runtime','-Dmdep.outputFile=target/runtime-classpath.txt']);verify_reports(root,expected)
    command(root,[sys.executable,'-B','scripts/project/test_result_wire.py'])
    command(root,[sys.executable,'-B','scripts/project/result_wire.py','analysis-adapters/target/w5-cases/unsupported-profile.json'])
    if category=='performance':
        p=root/'.harness-results/w5-performance.json';p.parent.mkdir(exist_ok=True);p.write_text(json.dumps(verify_metrics(output),indent=2)+'\n')
    if category=='integration':
        from e2e_w5 import run as e2e
        config=os.environ.get('W5_PRODUCERS')
        if not config:raise Failure('S10 requires W5_PRODUCERS build receipt; run prepare_w5_producers.py first')
        parent=root/'.harness-results';parent.mkdir(exist_ok=True)
        directory=Path(tempfile.mkdtemp(prefix='w5-s10-',dir=parent));directory.rmdir()
        e2e(root,directory,load(config));(parent/'w5-s10-latest.txt').write_text(str(directory)+'\n')
    print('[w5-'+category+'] PASS: nominal W5 production/delivery/reader tests; S1–S9 additionally composed by global W1–W4 gates')
if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('category',choices=['architecture','semantic','performance','integration']);p.add_argument('--root',type=Path,default=ROOT);p.add_argument('--update-inventory',action='store_true');a=p.parse_args()
    try:run(a.root.resolve(),a.category,a.update_inventory)
    except (Failure,OSError,ValueError,KeyError,ET.ParseError) as e:print('[w5] FAIL: '+str(e),file=sys.stderr);sys.exit(1)
