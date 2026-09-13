#!/usr/bin/env python3
"""Manual semantic golden and adversarial challenges, independent of Java encoders."""
import copy,json,unittest
from pathlib import Path
from regional_result_wire import validate,canonical,read,WireError,load
ROOT=Path(__file__).resolve().parents[2]
PRODUCT=ROOT/'analysis-adapters/target/regional-wire/manual.result.json'

def actual_projection(r):
    result=[]
    for o in r['observations']:
        row={'operation':o['point']['operationId']['localId'],'position':o['point']['position'],'status':o['values']['status'],'reason':o['values']['reason']}
        f=o['values']['fact'];d=o['rd']['fact']
        if f is not None:
            row.update(reachability=f['reachability'],candidates=f['candidates'],model=f['modelValueRemainder'],source=f['sourceUnknownRemainder'],effective=f['effectiveUnknownRemainder'])
            row['definitions']=[{'event':v['definition']['operationId']['localId'],'kind':v['definition']['kind'],'ranges':[(l['storageId']['localId'],l['range']['start'],l['range']['end']) for l in v['contributedRanges']]} for v in d['definitions']]
            row['fragments']=[]
            for a in f['alternatives']:
                for part in a['fragments']:
                    p=part['producer'];row['fragments'].append({'range':part['location']['range'],'bytes':part['bytes'],'producer':p['definition']['operationId']['localId'],'original':(p['contributedRange']['storageId']['localId'],p['contributedRange']['range']['start'],p['contributedRange']['range']['end']),
                        'captures':[(c['definition']['operationId']['localId'],c['before']['position'],c['sourceRange']['range'],c['destinationRange']['range'],c['sourceContribution']['range'],c['destinationContribution']['range']) for c in part['captures']]})
        result.append(row)
    return result

def interval(a,b):return {'unit':'OCTET','start':str(a),'end':str(b)}
def golden():
    # Handwritten oracle: old ABCDEFGH; prefix WXYZ; whole copy; source overwritten.
    fragments=[]
    for start,end,text,producer in [(0,4,'WXYZ','prefix'),(4,8,'EFGH','old')]:
        fragments.append({'range':interval(start,end),'bytes':list(text.encode('ascii')),'producer':producer,'original':('region',str(start),str(end)),
            'captures':[('copy','BEFORE',interval(0,8),interval(0,8),interval(start,end),interval(start,end))]})
    return [dict(operation='return-body',position='BEFORE',status='VALUE',reason=None,reachability='REACHABLE',candidates=['WXYZEFGH'],model=False,source=False,effective=False,
        definitions=[dict(event='copy',kind='COPY',ranges=[('copy-region','0','8')])],fragments=fragments),
        dict(operation='return-body',position='AFTER',status='UNSUPPORTED_POINT',reason='AFTER_TERMINATOR'),
        dict(operation='return-dead',position='BEFORE',status='VALUE',reason=None,reachability='UNREACHABLE_IN_MODEL',candidates=None,model=None,source=False,effective=False,definitions=[],fragments=[])]

def field(path,value):
    def mutate(r):
        target=r
        for k in path[:-1]:target=target[k]
        target[path[-1]]=value
    return mutate

class RegionalWireContract(unittest.TestCase):
    @classmethod
    def setUpClass(cls):cls.result=read(PRODUCT)
    def test_manual_composition_and_capture_golden(self):self.assertEqual(golden(),actual_projection(self.result))
    def test_roundtrip_is_byte_identical(self):self.assertEqual(PRODUCT.read_bytes(),canonical(self.result))
    def test_real_cli_outputs_and_large_sparse_wire(self):
        for name in ('known','unsupported'):
            path=ROOT/f'analysis-launcher/target/regional-cli/{name}.result.json';result=read(path);self.assertEqual(path.read_bytes(),canonical(result))
        r=read(PRODUCT.parent/'limits.result.json');self.assertEqual(4,len(r['observations']))
        by_kind={o['subject']['objectId']['localId'] if o['subject']['kind']=='NAMED_OBJECT' else o['subject']['storageId']['localId']+':'+o['subject']['range']['start']:o for o in r['observations']}
        huge=by_kind['huge:'+str(2**90)]['values']['fact'];self.assertTrue(huge['modelValueRemainder']);self.assertEqual([],huge['candidates'])
        zero=by_kind['huge:0']['values']['fact'];self.assertFalse(zero['modelValueRemainder']);self.assertEqual([''],zero['candidates']);self.assertEqual([],zero['alternatives'][0]['fragments'])
        tail=by_kind['open:0']['values']['fact'];self.assertIsNone(tail['alternatives'][0]['fragments'][0]['location']['range']['end'])
        cell=by_kind['logical']['values']['fact'];self.assertEqual('WHOLE_CELL',cell['interpretations'][0]['location']['kind']);self.assertIsNone(cell['interpretations'][0]['location']['range'])
        self.assertEqual(str(2**100),next(b['extent'] for b in r['inventory']['storages'] if b['storageId']['localId']=='huge'))
    def test_closed_shape_identity_ranges_and_provenance_mutations(self):
        v=['observations',0,'values','fact'];f=v+['alternatives',0,'fragments',0];e=f+['producer','definition'];c=f+['captures',0]
        mutations={
            'unknown-top-field':lambda r:r.update(unexpected=True),
            'wrong-version':field(['version'],'2.0.0'),
            'path-witness':field(['pathWitness'],'PROVIDED'),
            'missing-remainder':lambda r:r['observations'][0]['values']['fact'].pop('modelValueRemainder'),
            'effective-remainder':field(v+['effectiveUnknownRemainder'],True),
            'unknown-fragment-candidate':field(f+['kind'],'UNKNOWN_BYTES'),
            'lost-support':field(v+['candidateSupports',0,'producers'],[]),
            'lost-capture':field(f+['captures'],[]),
            'lost-capture-evidence':lambda r:r['observations'][0]['values']['fact']['evidenceRefs'].pop(0),
            'producer-is-copy':field(e+['kind'],'COPY'),
            'capture-after':field(c+['before','position'],'AFTER'),
            'capture-offset':field(c+['sourceContribution','range','start'],'1'),
            'wrong-byte':field(f+['bytes',0],65),
            'oversize-octet':field(f+['bytes',0],256),
            'boolean-octet':field(f+['bytes',0],True),
            'out-of-bounds':field(f+['location','range','end'],'9'),
            'leading-zero':field(f+['location','range','start'],'00'),
            'negative-bound':field(f+['location','range','start'],'-1'),
            'truncated-integer':field(f+['location','range','end'],4),
            'dangling-producer':field(e+['operationId','localId'],'absent'),
            'wrong-owner':field(e+['destination','owner','localId'],'old'),
            'unknown-event-kind':field(e+['kind'],'PHI'),
            'wrong-codec':field(['observations',0,'subject','codec'],{'kind':'IDENTITY_BYTES'}),
            'wrong-codec-field':lambda r:r['observations'][0]['subject']['codec'].update(defaultCharset='UTF-8'),
            'unreachable-closed-empty':field(['observations',2,'values','fact','candidates'],[]),
            'unsupported-with-fact':field(['observations',1,'values','fact'],self.result['observations'][0]['values']['fact']),
            'boolean-metric':field(['statistics','composition','rdRuns'],True),
        }
        for name,mutate in mutations.items():
            with self.subTest(name=name):
                r=copy.deepcopy(self.result);mutate(r)
                with self.assertRaises(WireError):validate(r)
        print('ST_REGIONAL_WIRE_MUTANTS '+json.dumps({'killed':len(mutations),'total':len(mutations),'compileErrors':0},sort_keys=True))
    def test_manual_oracle_rejects_consistently_fabricated_bytes(self):
        r=copy.deepcopy(self.result);v=r['observations'][0]['values']['fact'];v['candidates']=['ABCD1234'];v['candidateSupports'][0]['candidate']='ABCD1234';v['alternatives'][0]['candidate']='ABCD1234'
        for p,text in zip(v['alternatives'][0]['fragments'],['ABCD','1234']):p['bytes']=list(text.encode('ascii'))
        validate(r) # Shape/decoder agreement is not enough to prove source semantics.
        self.assertNotEqual(golden(),actual_projection(r))

if __name__=='__main__':unittest.main()
