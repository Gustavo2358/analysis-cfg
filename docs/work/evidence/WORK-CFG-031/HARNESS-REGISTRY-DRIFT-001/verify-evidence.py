from pathlib import Path
import gzip,hashlib,json,tarfile
w=Path('/home/gustavo/workspace/teste-e2e');e=w/'.worktrees/analysis-cfg-w5/docs/work/evidence/WORK-CFG-031/HARNESS-REGISTRY-DRIFT-001'
def sha(raw):return hashlib.sha256(raw).hexdigest()
index=json.loads((e/'sha256.json').read_text())
for name,digest in index.items():assert sha((e/name).read_bytes())==digest,name
for p in (e/'validation').rglob('result.json'):
 d=json.loads(p.read_text());assert sha(gzip.decompress((p.parent/'run.log.gz').read_bytes()))==d['log_sha256'],str(p)
ritual=json.loads((e/'ritual.json').read_text())
for name,d in ritual['logs'].items():assert sha((e/(name+'.log')).read_bytes())==d['log_sha256'],name
assert sha((e/'test_harness.after.py').read_bytes())==ritual['restored_sha256']
assert sha((e/'test_harness.old-candidate.py').read_bytes())==json.loads((e/'old-blocked/receipt.json').read_text())['test_source_sha256']
with tarfile.open(e/'frozen-sync-snapshot.tar.gz') as t:
 raw=t.extractfile('sync-snapshot/sha256.json').read();assert sha(raw)=='c88ac70d20be2eb1707d95e3a4535d9b999f7663b7a743e9d16bf0f05ed9cb4d'
 for name,digest in json.loads(raw).items():assert sha(t.extractfile('sync-snapshot/'+name).read())==digest,name
print('PASS: evidence file hashes, raw log hashes, restored test bytes and frozen snapshot')
