"""FAST: falsify evaluation population/scale contracts without running a corpus."""
import copy
import unittest
from carddemo_goto_depending import population, aggregate, vector, REMAINDERS
from goto_depending_fixtures import CARDINALITIES, fixtures


class ConditionalEvaluationTests(unittest.TestCase):
    def sample(self, n, typed=False):
        fact = dict(variant='OBSERVED', observedKind='GO_TO', observedShape='TYPED_GO_TO_DEPENDING_ON',
                    header=dict(provenance=dict(expanded=dict(startLine=2, startColumn=7, endLine=4))))
        if typed:
            fact.update(variant='GO_TO_DEPENDING_ON', destinations=[dict(ordinal=i) for i in range(n)], gapCodes=[])
        nodes = [dict(id=10, p=0, t='GoToStatement', l=2, c=7, e=4, a=dict(goToKind='DEPENDING_ON'))]
        # Identical names remain distinct references. Inventory order carries no ordinal meaning.
        nodes += [dict(id=11+i, p=10, t='ProcedureReference', a=dict(baseName='REPEATED')) for i in range(n)]
        return dict(statements=[fact]), nodes

    def test_hundreds_and_duplicates_count_occurrences(self):
        for n in (*CARDINALITIES, 256):
            sp, nodes = self.sample(n)
            self.assertEqual(population(sp, nodes)[1], [n])
            self.assertEqual(population(sp, list(reversed(nodes)))[1], [n])
            sp, nodes = self.sample(n, True)
            self.assertEqual(population(sp, nodes)[1], [n])

    def test_missing_or_truncated_occurrence_is_an_error(self):
        sp, nodes = self.sample(255, True)
        del sp['statements'][0]['destinations'][-1]
        with self.assertRaisesRegex(ValueError, 'destination occurrence lost'):
            population(sp, nodes)
        sp['statements'].clear()
        with self.assertRaisesRegex(ValueError, 'occurrence lost'):
            population(sp, nodes)

    def test_empty_population_is_valid_not_invented(self):
        result = aggregate({'empty.cbl': ([], [])})
        self.assertEqual(result['occurrences'], 0)
        self.assertEqual(result['targetCount'], dict(min=None, median=None, p95=None, max=None))
        self.assertEqual(result['programsAffected'], 0)

    def test_call_vector_retains_every_remainder(self):
        site = dict(classification='PARTIAL', reachability='REACHABLE', candidates=[], **{r: True for r in REMAINDERS})
        for remainder in REMAINDERS:
            changed = copy.deepcopy(site)
            changed[remainder] = False
            self.assertNotEqual(vector(site), vector(changed))
        for field in ('classification', 'reachability'):
            changed = copy.deepcopy(site)
            changed[field] = 'CHANGED'
            self.assertNotEqual(vector(site), vector(changed))

    def test_generated_scale_matrix_and_semantic_order(self):
        cases = fixtures()
        self.assertEqual(CARDINALITIES, (1, 2, 5, 40, 100, 200, 255))
        for n in CARDINALITIES:
            case = cases['capacity-' + str(n)]
            self.assertEqual(len(case['order']), n)
            self.assertFalse(case['partial'])
        self.assertEqual(cases['d3']['order'], ['TARGET-C', 'TARGET-A', 'TARGET-B'])
        self.assertEqual(cases['d4']['order'], ['TARGET-A', 'TARGET-B', 'TARGET-A', 'TARGET-C'])
        self.assertTrue(cases['capacity-256']['partial'])


if __name__ == '__main__':
    unittest.main()
