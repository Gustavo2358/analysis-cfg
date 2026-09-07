"""Read-only provenance oracle; temporary in-memory lock mutants never modify the checkout."""
from pathlib import Path
import hashlib,json,re,subprocess,sys,xml.etree.ElementTree as ET
ROOT=Path(sys.argv[1]).resolve(); UP=Path(sys.argv[2]).resolve(); BASE=sys.argv[3]
NEW='b78f4068d8a479f48eb048b8d76fa60a0997dc4a'; IR='122ce54e1b9ef9b00646f93ece409ca8b63bc933'
LOWER=ROOT.name=='cobol-lower'
def git(root,*a): return subprocess.check_output(['git',*a],cwd=root,stderr=subprocess.PIPE)
def air(lock): return next(s for s in lock['sources'] if s['id']=='SRC-AIR-JAVA') if LOWER else lock['air_java']
lock=json.loads((ROOT/'docs/sources/sources.lock.json').read_bytes()); baseline=json.loads(git(ROOT,'show',BASE+':docs/sources/sources.lock.json')); OLD=air(baseline)['commit']
paths_key='paths' if LOWER else 'read_paths'
def verify(a):
    assert a['commit']==NEW,'wrong active pin'
    paths=a[paths_key]
    assert len(paths)==len(set(paths)) and paths,'empty/duplicate paths'
    for p in paths:
        assert git(UP,'cat-file','-t',NEW+':'+p).strip()==b'blob','not a pinned file'
    if LOWER:
        assert a['file_urls']==[f'https://github.com/Gustavo2358/air-java/blob/{NEW}/{p}' for p in paths],'URL/pin/path mismatch'
    for module,artifact in [('pom.xml','air-java-parent'),('air-model/pom.xml','air-java'),('air-json/pom.xml','air-json')]:
        tree=ET.fromstring(git(UP,'show',NEW+':'+module))
        assert tree.find('{http://maven.apache.org/POM/4.0.0}artifactId').text==artifact,'module ownership mismatch'
    assert json.loads(git(UP,'show',NEW+':docs/sources.lock.json'))['analysis_ir']['ref']==IR,'upstream normative pin drift'
verify(air(lock)); print('PASS exact merge pin, pinned blobs, URL inventory and module ownership')
if LOWER:
    assert [s for s in lock['sources'] if s['id']!='SRC-AIR-JAVA']==[s for s in baseline['sources'] if s['id']!='SRC-AIR-JAVA']
    assert lock['local_sources']==baseline['local_sources']
else:
    assert {k:v for k,v in lock.items() if k!='air_java'}=={k:v for k,v in baseline.items() if k!='air_java'}
    assert lock['analysis_ir']['commit']==lock['air_java']['analysis_ir']['commit']==IR
print('PASS normative analysis-ir block and other source pins identical to baseline')
tracked=git(ROOT,'ls-files','-z').decode().split('\0'); history=[]; active_urls=0
for name in filter(None,tracked):
    path=ROOT/name
    if not path.is_file(): continue
    historical=name.startswith(('docs/work/history/','docs/sources/history/','docs/quality/WORK-LOWER-001/'))
    if historical:
        assert path.read_bytes()==git(ROOT,'show',BASE+':'+name),'historical bytes changed: '+name
        history.append(name)
        continue
    try: text=path.read_text()
    except UnicodeDecodeError: continue
    if OLD in text:
        assert name=='docs/architecture/decisions/ADR-0007.md' and text.count(OLD)==1 and 'Evidência histórica da decisão em 06/09/2026:' in text.split(OLD)[0],'old active reference: '+name
    for sha,p in re.findall(r'https://github.com/Gustavo2358/air-java/blob/([0-9a-f]{40})/([^\s)"`]+)',text):
        assert sha==NEW,'old active URL: '+name
        assert git(UP,'cat-file','-t',sha+':'+p).strip()==b'blob','URL target missing'
        active_urls+=1
changed=git(ROOT,'diff','--name-only',BASE).decode().splitlines()
fixture_scope={'scripts/harness/challenge.py','scripts/harness/tests/lifecycle_fixture.py','scripts/harness/tests/test_closure.py'} if LOWER else set()
for name in changed:
    if name in fixture_scope: continue
    assert not name.endswith('.java') and not name.endswith('pom.xml') and not name.startswith('scripts/'),'production/POM/harness changed: '+name
    assert name.startswith('docs/') or (not LOWER and name=='.github/workflows/ci.yml'),'scope drift: '+name
if not LOWER:
    workflow=(ROOT/'.github/workflows/ci.yml').read_text()
    assert f'ref: {NEW}' in workflow and f'test "$(git rev-parse HEAD)" = "{NEW}"' in workflow
    assert 'working-directory: air-java' in workflow and 'mvn -B -ntp clean install' in workflow
print(f'PASS {active_urls} active blob URLs; {len(history)} historical files unchanged; product/POM/gate-checker scope preserved')
# FALSIFY: wrong pin, obsolete topology, nonexistent path and wrong URL where present.
data=json.dumps(air(lock),sort_keys=True).encode(); digest=hashlib.sha256(data).hexdigest()
cases=['pin','topology','missing']+(['url'] if LOWER else [])
for case in cases:
    mutant=json.loads(data)
    if case=='pin': mutant['commit']=OLD
    elif case=='topology':
        i=next(i for i,p in enumerate(mutant[paths_key]) if p.startswith('air-model/src/'))
        mutant[paths_key][i]=mutant[paths_key][i].removeprefix('air-model/')
    elif case=='missing': mutant[paths_key].append('air-model/src/main/java/Absent.java')
    elif case=='url': mutant['file_urls'][0]=mutant['file_urls'][0].replace(NEW,OLD)
    try: verify(mutant)
    except (AssertionError,subprocess.CalledProcessError): print('EXPECTED_RED '+case+' exit=1')
    else: raise AssertionError('mutant survived: '+case)
verify(air(lock)); assert hashlib.sha256(json.dumps(air(lock),sort_keys=True).encode()).hexdigest()==digest
print('PASS restoration/second GREEN digest='+digest)
print('PATH_EVIDENCE path | git_blob_sha1 | sha256')
for p in air(lock)[paths_key]:
    content=git(UP,'show',NEW+':'+p)
    print(p+' | '+git(UP,'rev-parse',NEW+':'+p).decode().strip()+' | '+hashlib.sha256(content).hexdigest())
