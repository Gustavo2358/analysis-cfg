"""Version/pin guard adversaries for the current real-producer integration gate."""
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import e2e_w2d


class LockedSourceContractTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.lock = self.root / 'docs/sources/sources.lock.json'
        self.lock.parent.mkdir(parents=True)
        self.pin('2.50.0')
        self.patch = patch.object(e2e_w2d, 'ROOT', self.root)
        self.patch.start()
        self.addCleanup(self.patch.stop)

    def pin(self, version):
        self.lock.write_text(json.dumps({'proleap_poc': {'semantic_product_version': version}}))

    def sample(self, version='2.47.0'):
        return {'contractVersion': version, 'sourceDependencies': {'availability': 'KNOWN'}, 'statements': []}

    def test_current_pin_admits_earlier_feature_selected_and_current_publications(self):
        for version in ('2.31.0', '2.38.0', '2.47.0', '2.50.0'):
            with self.subTest(version=version):
                e2e_w2d.locked_sp(self.sample(version))

    def test_feature_floors_are_independent_of_the_pinned_ceiling(self):
        features = [({'statements': [{'variant': 'IF', 'condition': {'textPredicate': {'kind': 'EQUAL_TEXT'}}}]}, '2.46.0'),
                    ({'storage': {'logicalExactViews': [{}]}}, '2.35.0'),
                    ({'statements': [{'variant': 'PROCEDURE_PERFORM', 'publicationKind': 'STRUCTURAL_FACTS'}]}, '2.36.0'),
                    ({'ordinaryContinuations': [{}]}, '2.37.0'),
                    ({'statements': [{'variant': 'MOVE', 'logicalTransfers': [{}]}]}, '2.38.0')]
        for extra, version in features:
            with self.subTest(version=version):
                sp = self.sample(version) | extra
                e2e_w2d.locked_sp(sp)
                sp['contractVersion'] = f'2.{int(version.split(".")[1])-1}.0'
                with self.assertRaises(ValueError):
                    e2e_w2d.locked_sp(sp)

    def test_future_old_and_malformed_publications_are_rejected(self):
        for version in ('2.30.0', '2.50.1', '2.51.0', '3.0.0', '2.47', '2.47.0.1', '2.x.0'):
            with self.subTest(version=version), self.assertRaises(ValueError):
                e2e_w2d.locked_sp(self.sample(version))

    def test_lock_is_authoritative_and_not_an_unbounded_allowlist(self):
        self.pin('2.38.0')
        e2e_w2d.locked_sp(self.sample('2.38.0'))
        with self.assertRaises(ValueError):
            e2e_w2d.locked_sp(self.sample('2.47.0'))
        for version in ('3.0.0', '2.30.0', '2.50', 'bogus'):
            self.pin(version)
            with self.subTest(version=version), self.assertRaises(ValueError):
                e2e_w2d.locked_sp(self.sample('2.31.0'))

    def test_absent_source_evidence_and_out_of_scope_facts_still_fail(self):
        for extra in ({'sourceDependencies': None},
                      {'statements': [{'variant': 'OBSERVED', 'copySemantics': 'POSSIBLE_TEXT'}]},
                      {'statements': [{'variant': 'CICS_RETURN'}]}):
            with self.subTest(extra=extra), self.assertRaises(ValueError):
                e2e_w2d.locked_sp(self.sample() | extra)


class TextPredicateOracleTest(unittest.TestCase):
    def predicate(self):
        def fit(value):
            return {'kind': 'fit_text', 'length': '1', 'pad': ' ',
                    'header': {'role': 'VALUE_READ'}, 'value': value}
        return {'kind': 'binary', 'operator': 'eq', 'header': {'role': 'PREDICATE'},
                'left': fit({'kind': 'read', 'place': {'kind': 'object', 'object': 'flag'}}),
                'right': fit({'kind': 'literal', 'value': {'kind': 'text', 'value': 'Y'}})}

    def test_actual_text_equality_is_required_without_accepting_unknown_or_wrong_operands(self):
        e2e_w2d.text_equality_predicate(self.predicate(), 'flag', 'Y', 1)
        for mutate in (lambda p: p.update(kind='unknown'), lambda p: p.update(operator='ne'),
                       lambda p: p['header'].update(role='VALUE_READ'),
                       lambda p: p['left'].update(length='8'),
                       lambda p: p['right'].update(pad='X'),
                       lambda p: p['left']['value']['place'].update(object='target'),
                       lambda p: p['right']['value']['value'].update(value='N')):
            predicate = self.predicate(); mutate(predicate)
            with self.assertRaises(ValueError):
                e2e_w2d.text_equality_predicate(predicate, 'flag', 'Y', 1)




class SourceCoverageOracleTest(unittest.TestCase):
    def test_object_owner_is_profile_independent_but_unique(self):
        obj = {'domain': 'object', 'localId': 'flag'}
        item = {'sourceKey': 'storage@1/STORAGE_DECLARATION_UNKNOWN/data:0', 'outputs': [obj]}
        publication = {'coverage': {'items': [item]}}
        self.assertEqual(obj, e2e_w2d.declaration_object(publication, 'data:0'))
        for items in ([], [item, item], [item | {'outputs': [obj, obj]}],
                      [item | {'sourceKey': 'storage@1/data:01'}],
                      [item | {'outputs': [{'domain': 'operation', 'localId': 'flag'}]}]):
            with self.subTest(items=items), self.assertRaises(ValueError):
                e2e_w2d.declaration_object({'coverage': {'items': items}}, 'data:0')

    def test_move_accepts_only_its_own_explicit_completion(self):
        assign = {'kind': 'assign'}
        jump = {'kind': 'jump'}
        seq = {'instructions': [assign], 'terminator': jump}
        operations = {'assign': (seq, 0, assign), 'jump': (seq, 1, jump)}
        def publication(ids):
            return {'coverage': {'items': [{'sourceKey': 'sp-partial@1/' + ident + '/statement:0',
                     'outputs': [{'domain': 'operation', 'localId': ident}]} for ident in ids]}}
        for ids in (['assign'], ['assign', 'jump']):
            self.assertEqual(operations['assign'], e2e_w2d.statement_operation(
                publication(ids), operations, 'statement:0', 'assign'))
        for ids in ([], ['jump'], ['assign', 'assign'], ['assign', 'jump', 'jump']):
            with self.subTest(ids=ids), self.assertRaises(ValueError):
                e2e_w2d.statement_operation(publication(ids), operations, 'statement:0', 'assign')
        for extra in ((seq, 0, jump), (seq, 1, {'kind': 'opaque'}),
                      ({'instructions': [], 'terminator': jump}, 0, jump)):
            with self.subTest(extra=extra), self.assertRaises(ValueError):
                e2e_w2d.statement_operation(publication(['assign', 'jump']),
                    operations | {'jump': extra}, 'statement:0', 'assign')
        with self.assertRaises(ValueError):
            e2e_w2d.statement_operation(publication(['assign', 'jump']), operations,
                                      'statement:0', 'jump')


    def test_guarded_perform_requires_complete_activation_and_return(self):
        def model():
            op = {'kind': 'local.invoke', 'entry': {'localId': 'phase'},
                  'reentryGuard': {'activationKey': 'binding', 'destination': {'localId': 'frontier'}}}
            terms = {'activation': op, 'frontier': {'kind': 'opaque'},
                     'phase': {'kind': 'jump', 'destination': {'localId': 'body'}},
                     'body': {'kind': 'local.invoke', 'completionPorts': [], 'resume': {'localId': 'resume'}},
                     'resume': {'kind': 'local.resume'}}
            operations = {name: ({'label': {'localId': name}, 'instructions': [], 'terminator': term}, 0, term)
                          for name, term in terms.items()}
            publication = {'coverage': {'items': [{'sourceKey': 'sp-partial@1/' + name + '/statement:0',
                          'outputs': [{'domain': 'operation', 'localId': name}]} for name in terms]}}
            return publication, operations
        pub, ops = model()
        self.assertEqual(ops['activation'], e2e_w2d.statement_operation(pub, ops, 'statement:0', 'local.invoke'))
        for mutation in ('guard', 'body', 'return', 'extra', 'missing', 'entry'):
            pub, ops = model()
            if mutation == 'guard': del ops['activation'][2]['reentryGuard']
            elif mutation == 'body': ops['body'][2]['kind'] = 'jump'
            elif mutation == 'return': ops['body'][2]['resume']['localId'] = 'phase'
            elif mutation == 'extra': ops['phase'][0]['instructions'].append({'kind': 'assign'})
            elif mutation == 'missing': pub['coverage']['items'].pop()
            elif mutation == 'entry': ops['activation'][2]['entry']['localId'] = 'frontier'
            with self.subTest(mutation=mutation), self.assertRaises((ValueError, KeyError)):
                e2e_w2d.statement_operation(pub, ops, 'statement:0', 'local.invoke')


if __name__ == '__main__':
    unittest.main()
