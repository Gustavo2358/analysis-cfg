#!/usr/bin/env python3
"""Adversarial focal authorization and no-semantic-result boundary checks."""
import json,sys,tempfile,unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'scripts/project'))
from resource_limit_scope import PRODUCTION,CONTRACT,allows_change,boundaries,sha
class ResourceHarnessTest(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory();self.root=Path(self.temp.name)
        for path in PRODUCTION|{CONTRACT}:
            p=self.root/path;p.parent.mkdir(parents=True,exist_ok=True);p.write_bytes((ROOT/path).read_bytes())
    def tearDown(self):self.temp.cleanup()
    def flow(self,old,new):
        p=self.root/'analysis-dataflow/src/main/java/io/github/gustavo2358/analysis/dataflow/AnalysisDataflow.java'
        p.write_text(p.read_text().replace(old,new))
    def test_nominal_boundary(self):boundaries(self.root)
    def test_session_before_classification_is_rejected(self):
        self.flow('        requireBuilt(cfg);','        AnalysisSession.open(cfg,publication,options.projectionPolicy(),List.of());\n        requireBuilt(cfg);')
        with self.assertRaisesRegex(ValueError,'starts analysis'):boundaries(self.root)
    def test_source_open_is_not_operational_outcome(self):
        self.flow('throw new PreparationException(Failure.EXTERNAL_RESOURCE_LIMIT,','throw new PreparationException(Failure.UNSUPPORTED_PROFILE,')
        with self.assertRaisesRegex(ValueError,'no semantic/source-open'):boundaries(self.root)
    def test_focal_exception_requires_both_exact_hashes(self):
        path='cfg-kernel/src/main/java/io/github/gustavo2358/analysis/cfg/application/CfgBuildCoordinator.java'
        c=json.loads((self.root/CONTRACT).read_text());before=c['baseline_sha256'][path]
        self.assertTrue(allows_change(self.root,path,before))
        self.assertFalse(allows_change(self.root,path,'0'*64))
        p=self.root/path;p.write_text(p.read_text()+'\n')
        self.assertFalse(allows_change(self.root,path,before))
    def test_unknown_production_cannot_extend_focal_allowlist(self):
        path='analysis-kernel/src/main/java/Unexpected.java';p=self.root/path;p.parent.mkdir(parents=True);p.write_text('class Unexpected {}')
        c=json.loads((self.root/CONTRACT).read_text());digest=sha(p.read_bytes());c['changed_tests'].append(path);c['baseline_sha256'][path]=digest;c['current_sha256'][path]=digest;(self.root/CONTRACT).write_text(json.dumps(c))
        self.assertFalse(allows_change(self.root,path,digest))
if __name__=='__main__':unittest.main(verbosity=2)
