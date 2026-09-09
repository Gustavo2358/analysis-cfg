#!/usr/bin/env python3
"""Adversarial tests of harness guards only; no CFG algorithm is implemented here."""
from __future__ import annotations
import contextlib
import io
import json
import shutil
import sys
import tempfile
import unittest
from unittest.mock import patch
from subprocess import CompletedProcess
from pathlib import Path
from validate_docs import ROOT, validate, load_json
from run_gate import run
from cache_ir import cache, git_blob_sha1


class HarnessGuardTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='cfg-harness-test-')
        self.root = Path(self.temp.name) / 'repository'
        shutil.copytree(ROOT, self.root, ignore=shutil.ignore_patterns('.git', '.cache', '__pycache__', '.harness-results', 'target'))

    def tearDown(self):
        self.temp.cleanup()

    def edit_json(self, path, fn):
        target = self.root / path
        value = load_json(target)
        fn(value)
        target.write_text(json.dumps(value, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')

    def assert_guard(self, expected):
        errors = validate(self.root)
        self.assertTrue(any(expected in e for e in errors), '\n'.join(errors))

    def ensure_active_work(self):
        registry_path = self.root / 'docs/work/registry.json'
        registry = load_json(registry_path)
        if registry['active']:
            return registry['active'][0]['id']
        ident = 'WORK-CFG-999'
        folder = self.root / 'docs/work/active' / ident
        folder.mkdir(parents=True)
        manifest = {
            'id': ident, 'backlog_id': 'BACKLOG-CFG-002', 'title': 'Synthetic active work',
            'status': 'active', 'risk': 'low', 'goal': 'Exercise active-work guards',
            'authorization': 'discovery', 'authorization_evidence': 'synthetic harness test',
            'checkpoint': 'TEST', 'must_read': ['AGENTS.md'], 'related_decisions': [],
            'related_invariants': [], 'evals': [], 'source_scope': [], 'test_scope': [],
            'must_not_change': [], 'gates': ['fast'], 'stop_condition': 'end of test'
        }
        (folder / 'work-item.json').write_text(
            json.dumps(manifest, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
        documents = {
            'spec.md': '# spec\n\n## Problema\n\ntest\n\n## Objetivo\n\ntest\n\n## Fora de escopo\n\ntest\n',
            'plan.md': '# plan\n\n## Fatiamento\n\ntest\n\n## Dependências\n\ntest\n',
            'eval.md': '# eval\n\n## O que prova corretude\n\ntest\n\n## Casos adversariais\n\ntest\n',
            'state.md': '# state\n\n## Onde estamos\n\ntest\n\n## Verde conhecido\n\ntest\n\n## Restante\n\ntest\n\n## Descobertas que afetam o plano\n\ntest\n'
        }
        for name, content in documents.items():
            (folder / name).write_text(content, encoding='utf-8')
        registry['active'].append({
            'id': ident, 'path': 'docs/work/active/' + ident,
            'status': 'active', 'authorization': 'discovery'
        })
        registry_path.write_text(
            json.dumps(registry, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
        with (self.root / 'docs/work/index.md').open('a') as index:
            index.write('\n' + ident + '\n')
        return ident

    def test_01_valid_package(self):
        self.assertEqual([], validate(self.root))

    def test_02_broken_link(self):
        with (self.root / 'README.md').open('a') as f:
            f.write('\n[broken](missing-local.md)\n')
        self.assert_guard('Broken local link')

    def test_03_unknown_invariant(self):
        with (self.root / 'README.md').open('a') as f:
            f.write('\nINV-CFG-999\n')
        self.assert_guard('Unknown ID INV-CFG-999')

    def test_04_duplicate_invariant(self):
        with (self.root / 'docs/architecture/invariants.md').open('a') as f:
            f.write('\n## INV-CFG-001 — Duplicate\n')
        self.assert_guard('Duplicate invariant')

    def test_05_backlog_cycle(self):
        self.edit_json('docs/work/backlog.json', lambda x: x['items'][0]['dependencies'].append('BACKLOG-CFG-002'))
        self.assert_guard('dependency cycle')

    def test_06_missing_dependency(self):
        self.edit_json('docs/work/backlog.json', lambda x: x['items'][0]['dependencies'].append('BACKLOG-CFG-999'))
        self.assert_guard('Missing backlog dependency')

    def test_07_registry_drift(self):
        ident = self.ensure_active_work()
        self.edit_json('docs/work/registry.json', lambda x: next(
            item for item in x['active'] if item['id'] == ident).update(status='blocked'))
        self.assert_guard('Work registry mismatch')

    def test_08_completed_item_left_active(self):
        ident = self.ensure_active_work()
        self.edit_json('docs/work/registry.json', lambda x: next(
            item for item in x['active'] if item['id'] == ident).update(status='completed'))
        self.edit_json('docs/work/active/' + ident + '/work-item.json',
                       lambda x: x.update(status='completed'))
        self.assert_guard('Completed/invalid work in active')

    def test_09_extra_work_file(self):
        ident = self.ensure_active_work()
        (self.root / 'docs/work/active' / ident / 'duplicate-plan.md').write_text('# Duplicate\n')
        self.assert_guard('exactly five files')

    def test_10_java_forbidden(self):
        self.edit_json('docs/engineering/gate-state.json',
                       lambda x: x.update(phase='docs_only', runtime_authorization=None))
        # A filename-only sentinel tests the former docs-only packaging policy.
        (self.root / 'Sentinel.java').write_bytes(b'')
        self.assert_guard('Java/POM forbidden')

    def test_11_pom_forbidden(self):
        self.edit_json('docs/engineering/gate-state.json',
                       lambda x: x.update(phase='docs_only', runtime_authorization=None))
        (self.root / 'pom.xml').write_bytes(b'')
        self.assert_guard('Java/POM forbidden')

    def test_12_false_product_gate(self):
        self.edit_json('docs/engineering/gate-state.json', lambda x: x['product_gates']['semantic'].update(status='implemented', hook='scripts/project/absent.sh'))
        self.assert_guard('Invalid/missing product gate hook: semantic')

    def test_13_false_profile_claim(self):
        self.edit_json('docs/engineering/gate-state.json',
                       lambda x: x.update(phase='docs_only', runtime_authorization=None))
        self.edit_json('docs/evals/profile-obligations.json', lambda x: x['implemented_profiles'].append('AIR-STRUCTURE@2'))
        self.assert_guard('Implemented profile claimed')

    def test_14_profile_obligation_removed(self):
        self.edit_json('docs/evals/profile-obligations.json', lambda x: x['profiles'][0]['oracles'].pop())
        self.assert_guard('Profile obligations drift')

    def test_15_mutable_source_reference(self):
        self.edit_json('docs/sources/sources.lock.json', lambda x: x['analysis_ir'].update(commit='main'))
        self.assert_guard('IR commit must be immutable')

    def test_16_duplicate_json_key(self):
        p = self.root / 'docs/engineering/gate-state.json'
        p.write_text('{"schema_version":1,"schema_version":2}')
        self.assert_guard('duplicate JSON key')

    def test_17_phase_without_authorization(self):
        self.edit_json('docs/engineering/gate-state.json', lambda x: x.update(phase='implementation', runtime_authorization='WORK-CFG-001'))
        self.assert_guard('lacks linked implementation authorization')

    def test_18_missing_work_context(self):
        ident = self.ensure_active_work()
        self.edit_json('docs/work/active/' + ident + '/work-item.json',
                       lambda x: x['must_read'].append('missing-rule.md'))
        self.assert_guard('Missing/invalid work path')

    def test_19_unavailable_never_passes(self):
        # `full` invokes `fast`, which contains this harness suite; exercise it at
        # the shell level instead of recursively from its own test process.
        for gate in ('architecture', 'semantic', 'performance', 'integration'):
            # Materialize the unavailable condition instead of assuming today's project state.
            self.edit_json('docs/engineering/gate-state.json',
                           lambda state: state['product_gates'][gate].update(status='unavailable', hook=None))
            # Isolate routing: W1/W2 completed hooks are mocked here, never run Maven in fast.
            with self.subTest(gate=gate), contextlib.redirect_stdout(io.StringIO()) as out, \
                    patch('run_gate.subprocess.run', return_value=CompletedProcess([], 0)) as hooks:
                self.assertEqual(3, run(gate, self.root))
                self.assertIn('UNAVAILABLE', out.getvalue())
                self.assertEqual(2 if gate == 'performance' else 0, hooks.call_count)

    def test_20_unresolved_eval(self):
        self.edit_json('docs/work/backlog.json', lambda x: x['items'][0]['evals'].append('EVAL-CFG-999'))
        self.assert_guard('Missing eval reference')

    def test_21_link_cannot_escape_repository(self):
        with (self.root / 'README.md').open('a') as f:
            f.write('\n[escape](../outside.md)\n')
        self.assert_guard('Broken local link')


    def test_26_product_gate_cannot_bypass_authorization_preflight(self):
        hook = self.root / 'scripts/project/check-semantic.sh'
        hook.parent.mkdir(parents=True, exist_ok=True)
        hook.write_text('#!/usr/bin/env bash\nexit 0\n')
        self.edit_json('docs/engineering/gate-state.json',
                       lambda x: x.update(phase='implementation', runtime_authorization='WORK-CFG-001'))
        self.edit_json('docs/engineering/gate-state.json', lambda x: x['product_gates']['semantic'].update(status='implemented', hook='scripts/project/check-semantic.sh'))
        with contextlib.redirect_stdout(io.StringIO()), contextlib.redirect_stderr(io.StringIO()):
            self.assertEqual(1, run('semantic', self.root))

    def test_27_completed_backlog_requires_evidence(self):
        self.edit_json('docs/work/backlog.json', lambda x: next(
            item for item in x['items'] if item['status'] != 'completed').update(status='completed'))
        self.assert_guard('Completed backlog without evidence')

    def test_28_unknown_work_link(self):
        self.edit_json('docs/work/backlog.json', lambda x: x['items'][0].update(work_item='WORK-CFG-999'))
        self.assert_guard('Backlog points to unknown work item')

    def test_29_legacy_ir_profile_rejected(self):
        self.edit_json('docs/evals/profile-obligations.json', lambda x: x['profiles'][0].update(id='AIR-STRUCTURE@1'))
        self.assert_guard('Profile obligations drift')

    def test_30_legacy_ir_version_rejected(self):
        self.edit_json('docs/sources/sources.lock.json', lambda x: x['analysis_ir'].update(semantic_version='1.0.0'))
        self.assert_guard('Analysis IR 2.0.0')

    def test_31_nonexistent_oracle_projection_rejected(self):
        invalid_oracles = ('O-01-SCALAR', 'O-56-STRUCT', 'O-87-SCALAR', 'O-91-REGION')
        for oracle in invalid_oracles:
            with self.subTest(oracle=oracle):
                self.edit_json('docs/evals/catalog.json', lambda x: x['evals'][0]['upstream_oracles'].append(oracle))
                self.assert_guard('Invalid upstream oracle: ' + oracle)
                self.edit_json('docs/evals/catalog.json', lambda x: x['evals'][0]['upstream_oracles'].pop())

    def test_32_normative_oracle_projections_accepted(self):
        valid_oracles = ('O-86', 'O-69-SCALAR', 'O-81-REGION', 'O-88-REGION', 'O-91-STRUCT')
        self.edit_json('docs/evals/catalog.json', lambda x: x['evals'][0]['upstream_oracles'].extend(valid_oracles))
        self.assertEqual([], validate(self.root))

    def test_33_mutable_air_java_reference_rejected(self):
        self.edit_json('docs/sources/sources.lock.json',
                       lambda x: x['air_java'].update(commit='main'))
        self.assert_guard('air-java commit must be immutable')

    def test_34_air_java_ir_drift_rejected(self):
        self.edit_json('docs/sources/sources.lock.json',
                       lambda x: x['air_java']['analysis_ir'].update(commit='f' * 40))
        self.assert_guard('air-java and normative Analysis IR baseline disagree')

    def test_35_java_21_without_preview_required(self):
        self.edit_json('docs/sources/sources.lock.json',
                       lambda x: x['air_java']['java'].update(release=17))
        self.assert_guard('Java 21 without preview')

    def test_36_json_binding_owner_guarded(self):
        self.edit_json('docs/sources/sources.lock.json',
                       lambda x: x['analysis_ir']['json_binding'].update(owner='air-java'))
        self.assert_guard('JSON binding must be owned by analysis-ir')

    def test_39_json_binding_cannot_be_promoted_locally(self):
        self.edit_json('docs/sources/sources.lock.json',
                       lambda x: x['analysis_ir']['json_binding'].update(maturity='ACCEPTED'))
        self.assert_guard('JSON binding must remain DRAFT 1.0.0')

    def test_40_json_binding_version_cannot_drift(self):
        self.edit_json('docs/sources/sources.lock.json',
                       lambda x: x['analysis_ir']['json_binding'].update(version='2.0.0'))
        self.assert_guard('JSON binding must remain DRAFT 1.0.0')

    def test_41_json_binding_stays_out_of_consumer_code(self):
        self.edit_json('docs/sources/sources.lock.json',
                       lambda x: x['analysis_ir']['json_binding'].update(implemented_in_analysis_cfg=True))
        self.assert_guard('JSON binding must remain outside analysis-cfg code')

    def test_37_planned_lowerer_cannot_claim_api(self):
        self.edit_json('docs/sources/sources.lock.json',
                       lambda x: x['cobol_lower'].update(api='invented'))
        self.assert_guard('planned cobol-lower cannot claim commit or API')

    def test_38_proleap_main_evidence_cannot_drift(self):
        self.edit_json('docs/sources/sources.lock.json',
                       lambda x: x['proleap_poc'].update(main_commit='f' * 40))
        self.assert_guard('proleap-poc main evidence must match latest merged review')


    def test_42_integration_hook_is_required_when_implemented(self):
        (self.root / 'scripts/project/check-integration.sh').unlink()
        self.assert_guard('Invalid/missing product gate hook: integration')

    def test_43_integration_report_detector_rejects_missing_duplicate_and_skip(self):
        sys.path.insert(0, str(ROOT / 'scripts/project'))
        from check_integration import detector_self_test
        with contextlib.redirect_stdout(io.StringIO()):
            detector_self_test()

    def test_44_outer_bytecode_rejects_frontend_and_reverse_dependencies(self):
        sys.path.insert(0, str(ROOT / 'scripts/project'))
        from check_transport_architecture import inventory, verify_dependencies
        from check_architecture import GateFailure
        for module, entry in inventory(self.root).items():
            for forbidden in ('io.proleap.CobolParser', 'org.antlr.Parser', 'com.fasterxml.jackson.databind.ObjectMapper',
                              'io.github.gustavo2358.analysis.cfg.launcher.Unapproved', 'local.AirParser'):
                expected = entry['bytecode_dependencies']
                actual = {key: set(value) for key, value in expected.items()}
                actual[next(iter(actual))].add(forbidden)
                with self.subTest(module=module, forbidden=forbidden), self.assertRaises(GateFailure):
                    verify_dependencies(module, actual, expected)

    def test_45_outer_direct_dependencies_are_exact(self):
        sys.path.insert(0, str(ROOT / 'scripts/project'))
        from check_transport_architecture import verify_transport_shape
        from check_architecture import GateFailure
        path = self.root / 'cfg-adapters/pom.xml'
        path.write_text(path.read_text().replace('<artifactId>air-json</artifactId>', '<artifactId>frontend</artifactId>'))
        with self.assertRaises(GateFailure): verify_transport_shape(self.root)

    def test_46_reader_cannot_replace_shared_decode_with_local_parser(self):
        sys.path.insert(0, str(ROOT / 'scripts/project'))
        from check_transport_architecture import verify_transport_shape
        from check_architecture import GateFailure
        path = self.root / 'cfg-adapters/src/main/java/io/github/gustavo2358/analysis/cfg/adapters/AirJsonFileReader.java'
        path.write_text(path.read_text().replace('return codec.decode(bytes);', 'return localParser(bytes);'))
        with self.assertRaises(GateFailure): verify_transport_shape(self.root)

    def test_47_enum_name_cannot_define_wire(self):
        sys.path.insert(0, str(ROOT / 'scripts/project'))
        from check_transport_architecture import verify_transport_shape
        from check_architecture import GateFailure
        path = self.root / 'cfg-adapters/src/main/java/io/github/gustavo2358/analysis/cfg/adapters/CfgJsonWriter.java'
        path.write_text(path.read_text().replace('out.string(transitionKind(transition.kind()))', 'out.string(transition.kind().name())'))
        with self.assertRaises(GateFailure): verify_transport_shape(self.root)


class PinnedCacheTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='cfg-ir-cache-test-')
        self.root = Path(self.temp.name)
        self.source = self.root / 'source'
        self.source.mkdir()
        self.data = b'# Synthetic source used only for testing hash verification.\n'
        (self.source / 'README.md').write_bytes(self.data)
        self.air = {'repository':'Gustavo2358/analysis-ir', 'commit':'0'*40,
                    'files':[{'path':'README.md', 'git_blob_sha1':git_blob_sha1(self.data)}]}

    def tearDown(self):
        self.temp.cleanup()

    def test_22_import_verified_local_source(self):
        path = cache(self.air, self.root / 'cache', self.source)
        self.assertEqual(self.data, (path / 'README.md').read_bytes())
        self.assertEqual(path, cache(self.air, self.root / 'cache', self.source))

    def test_23_hash_mismatch_leaves_no_published_cache(self):
        (self.source / 'README.md').write_bytes(b'changed\n')
        with self.assertRaisesRegex(ValueError, 'hash mismatch'):
            cache(self.air, self.root / 'cache', self.source)
        self.assertFalse((self.root / 'cache' / ('0'*40)).exists())
        self.assertEqual([], list((self.root / 'cache').iterdir()))

    def test_24_unsafe_path(self):
        self.air['files'][0]['path'] = '../outside.md'
        with self.assertRaisesRegex(ValueError, 'unsafe'):
            cache(self.air, self.root / 'cache', self.source)

    def test_25_existing_cache_tampering(self):
        path = cache(self.air, self.root / 'cache', self.source)
        (path / 'README.md').write_bytes(b'modified\n')
        with self.assertRaisesRegex(ValueError, 'hash mismatch'):
            cache(self.air, self.root / 'cache', self.source)


if __name__ == '__main__':
    unittest.main(verbosity=2)
