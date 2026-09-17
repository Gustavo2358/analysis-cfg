"""Manual reporting laws: IDs may change; support, remainder and source loss may not hide."""
import copy
import unittest
from file_corpus_metrics import call_vectors, compare_vectors, source_locations

class FileCorpusMetricsTest(unittest.TestCase):
    def wire(self):
        return {'artifacts':[{'id':{'localId':'a'},'logicalName':'P.cbl'}],
                'origins':[{'id':{'localId':'o'},'kind':'WRITTEN','artifact':{'localId':'a'},'exact':True,'includes':[],
                            'location':{'kind':'LINE_COLUMNS','startLine':'7','startColumn':'11','endLine':'7','endColumn':'20'}},
                           {'id':{'localId':'d'},'kind':'DERIVED','inputs':[{'localId':'o'}]}],
                'sites':[{'caller':{'localId':'u'},'operation':{'localId':'x'},'siteOrigin':{'localId':'d'},'targetOrigin':{'localId':'o'},
                          'command':'CALL','targetKind':'LITERAL','reachability':'REACHABLE','modelValueRemainder':False,
                          'sourceValueRemainder':True,'effectiveUnknownRemainder':True,'openControlRemainder':False,
                          'interpretationUnknownRemainder':False,'candidates':[{'referenceName':'TARGET','rawValue':'TARGET',
                             'supports':[{'kind':'CALL_LITERAL','origin':{'localId':'o'},'producer':{'localId':'x'},'premises':[]}]}]}]}
    def test_source_identity_ignores_generated_ids(self):
        w=self.wire();a=call_vectors(w);w['sites'][0]['operation']['localId']='different';w['sites'][0]['caller']['localId']='different'
        self.assertEqual(compare_vectors(a,call_vectors(w))['changed'],[])
        self.assertEqual(len(source_locations(self.wire(),{'localId':'d'})),1)
    def test_support_and_remainder_differences_are_reported(self):
        w=self.wire();a=call_vectors(w);w['sites'][0]['candidates'][0]['supports']=[];w['sites'][0]['modelValueRemainder']=True
        d=compare_vectors(a,call_vectors(w));self.assertEqual(len(d['changed']),1)
        self.assertEqual(d['changed'][0]['dimensions'],['candidates','modelValueRemainder'])
    def test_lost_site_is_not_equal_empty_or_unmatched_success(self):
        self.assertEqual(len(compare_vectors(call_vectors(self.wire()),[])['removed']),1)
    def test_duplicate_source_key_preserves_multiplicity(self):
        w=self.wire();a=call_vectors(w);w['sites'].append(copy.deepcopy(w['sites'][0]));d=compare_vectors(a,call_vectors(w))
        self.assertEqual(len(d['added']),1)
    def test_missing_origin_refuses_untraceable_comparison(self):
        with self.assertRaises(ValueError):source_locations(self.wire(),{'localId':'absent'})
if __name__=='__main__':unittest.main()
