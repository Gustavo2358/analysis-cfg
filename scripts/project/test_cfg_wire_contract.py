#!/usr/bin/env python3
import json
from pathlib import Path
import unittest
from cfg_wire_contract import verify

ROOT = Path(__file__).resolve().parents[2]


class CfgWireContractTest(unittest.TestCase):
    def document(self, version, terminator='INVOKE', transition='INVOKE_NORMAL'):
        return json.dumps(dict(schema='analysis-cfg-json', schemaVersion=version,
                               nodes=[dict(kind='SEQUENCE', terminator=dict(kind=terminator))],
                               transitions=[dict(kind=transition)]))

    def test_historical_bytes_use_closed_v1(self):
        for name in ('goback', 'scalar-assign'):
            data = (ROOT / ('cfg-adapters/src/test/resources/cfg/' + name + '.manual.json')).read_bytes()
            self.assertEqual('1.0.0', verify(data)['schemaVersion'])

    def test_each_new_token_requires_v2_independently(self):
        for terminator, transition in (('INVOKE', 'INVOKE_NORMAL'), ('INVOKE', 'ENTRY'), ('RETURN', 'INVOKE_NORMAL')):
            self.assertEqual('2.0.0', verify(self.document('2.0.0', terminator, transition))['schemaVersion'])
            with self.assertRaisesRegex(ValueError, 'incompatible'):
                verify(self.document('1.0.0', terminator, transition))

    def test_each_conservative_token_requires_v3_independently(self):
        for term, edge in (('OPAQUE','ENTRY'),('RETURN','OPAQUE_JUMP'),('RETURN','OPAQUE_RETURN')):
            self.assertEqual('3.0.0', verify(self.document('3.0.0',term,edge))['schemaVersion'])
            for version in ('1.0.0','2.0.0'):
                with self.assertRaisesRegex(ValueError,'incompatible'):
                    verify(self.document(version,term,edge))

    def test_each_exception_token_requires_v4_independently(self):
        for edge in ('EXCEPTION','CONTROL_EXIT'):
            self.assertEqual('4.0.0',verify(self.document('4.0.0','RETURN',edge))['schemaVersion'])
            for version in ('1.0.0','2.0.0','3.0.0'):
                with self.assertRaisesRegex(ValueError,'incompatible'):
                    verify(self.document(version,'RETURN',edge))
        for outcome in ('HALT','EXCEPTION','ANY_EXCEPTION'):
            doc=json.loads(self.document('4.0.0','RETURN','RETURN'))
            node=dict(kind='OUTCOME_EXIT',outcome=outcome)
            if outcome=='EXCEPTION':node['tag']='vendor-specific'
            doc['nodes'].append(node)
            self.assertEqual('4.0.0',verify(json.dumps(doc))['schemaVersion'])
            doc['schemaVersion']='3.0.0'
            with self.assertRaisesRegex(ValueError,'incompatible'):verify(json.dumps(doc))
        for outcome in ('RETURN','UNKNOWN'):
            doc=json.loads(self.document('4.0.0','RETURN','CONTROL_EXIT'))
            doc['nodes'].append(dict(kind='OUTCOME_EXIT',outcome=outcome))
            with self.assertRaisesRegex(ValueError,'invalid outside outcome'):verify(json.dumps(doc))

    def local_document(self):
        cfg=lambda n:dict(publication='p',ordinal=str(n))
        op=lambda n:dict(publication='p',unit='u',localId=n)
        return dict(schema='analysis-cfg-json',schemaVersion='5.0.0',nodes=[
            dict(id=cfg(0),kind='SEQUENCE',terminator=dict(kind='LOCAL_INVOKE',operation=op('call'))),
            dict(id=cfg(1),kind='SEQUENCE',terminator=dict(kind='LOCAL_RESUME',operation=op('done'))),
            dict(id=cfg(2),kind='SEQUENCE',terminator=dict(kind='RETURN',operation=op('return'))),
            dict(id=cfg(3),kind='OUTCOME_EXIT',outcome='EXCEPTION',tag='invalid_local_return',operation=op('done'))],
            transitions=[],localControl=[dict(source=cfg(0),operation=op('call'),kind='LOCAL_INVOKE',entry=cfg(1),resume=cfg(2),ports=[]),
                dict(source=cfg(1),operation=op('done'),kind='LOCAL_RESUME',invalidExit=cfg(3))])

    def test_local_rules_require_v5_and_are_not_unconditional_edges(self):
        d=self.local_document();self.assertEqual('5.0.0',verify(json.dumps(d))['schemaVersion'])
        d['transitions']=[dict(kind='JUMP',**{'from':d['nodes'][0]['id'],'to':d['nodes'][2]['id']})]
        with self.assertRaisesRegex(ValueError,'unconditional'):verify(json.dumps(d))
        for mutation in ('missing','duplicate','foreign','correlation','version'):
            d=self.local_document()
            if mutation=='missing':d['localControl'].pop()
            elif mutation=='duplicate':d['localControl'].append(d['localControl'][0])
            elif mutation=='foreign':d['localControl'][0]['entry']['publication']='other'
            elif mutation=='correlation':d['localControl'][0]['operation']['localId']='other'
            else:d['schemaVersion']='4.0.0'
            with self.assertRaises(ValueError,msg=mutation):verify(json.dumps(d))

    def test_guard_requires_v6_and_closed_destination(self):
        d=self.local_document();d['schemaVersion']='6.0.0'
        d['localControl'][0]['reentryGuard']=dict(activationKey='binding-A',destination=d['nodes'][2]['id'])
        self.assertEqual('6.0.0',verify(json.dumps(d))['schemaVersion'])
        for mutation in ('version','blank','foreign','extra','null'):
            import copy
            bad=copy.deepcopy(d);guard=bad['localControl'][0]['reentryGuard']
            if mutation=='version':bad['schemaVersion']='5.0.0'
            elif mutation=='blank':guard['activationKey']=''
            elif mutation=='foreign':guard['destination']['publication']='other'
            elif mutation=='extra':guard['extra']=True
            else:bad['localControl'][0]['reentryGuard']=None
            with self.assertRaises(ValueError,msg=mutation):verify(json.dumps(bad))

    def test_selected_routes_require_v7_and_unique_closed_keys(self):
        import copy
        d=self.local_document();d['schemaVersion']='7.0.0'
        route=dict(key='state-B',destination=d['nodes'][2]['id'])
        d['localControl'][0]['resumeRoutes']=[route];d['localControl'][1]['resumeKey']='state-B'
        self.assertEqual('7.0.0',verify(json.dumps(d))['schemaVersion'])
        for mutation in ('version','duplicate','blank','null','foreign','extra','bad-key'):
            bad=copy.deepcopy(d);routes=bad['localControl'][0]['resumeRoutes']
            if mutation=='version':bad['schemaVersion']='6.0.0'
            elif mutation=='duplicate':routes.append(copy.deepcopy(routes[0]))
            elif mutation=='blank':routes[0]['key']=' '
            elif mutation=='null':bad['localControl'][0]['resumeRoutes']=None
            elif mutation=='foreign':routes[0]['destination']['publication']='foreign'
            elif mutation=='extra':routes[0]['extra']=False
            else:bad['localControl'][1]['resumeKey']=''
            with self.assertRaises(ValueError,msg=mutation):verify(json.dumps(bad))

    def test_writer_local_product_matches_independent_wire_oracle(self):
        path=ROOT/'cfg-adapters/target/local-control-wire/cfg.json'
        self.assertTrue(path.exists(),'run LocalControlWireTest first')
        data=path.read_bytes();self.assertEqual('5.0.0',verify(data)['schemaVersion'])
        guarded=ROOT/'cfg-adapters/target/local-control-wire/guarded-cfg.json'
        self.assertEqual('6.0.0',verify(guarded.read_bytes())['schemaVersion'])
        selected=ROOT/'cfg-adapters/target/local-control-wire/selected-cfg.json'
        self.assertEqual('7.0.0',verify(selected.read_bytes())['schemaVersion'])
        for value in (False,'true',1,None):
            bad=json.loads(selected.read_bytes())
            for rule in bad['localControl']:
                if rule['kind']=='LOCAL_UNWIND':rule['all']=value
            with self.assertRaises(ValueError):verify(json.dumps(bad))
        for value in ('-1','01',1):
            d=json.loads(data)
            for rule in d['localControl']:
                if rule['kind']=='LOCAL_UNWIND':rule['count']=value
            with self.assertRaisesRegex(ValueError,'count'):verify(json.dumps(d))

    def test_selected_boundary_product_preserves_top_port_and_key_semantics(self):
        self.check_selected_boundary_products(ROOT/'cfg-adapters/target/local-control-wire')

    def check_selected_boundary_products(self,folder):
        from cfg_local_paths import Paths,vertex
        import copy
        documents={name:json.loads((folder/('boundary-'+name+'.json')).read_bytes())
                   for name in ('A','B','missing','legacy','key-only')}
        def shared(doc):
            node=next(n for n in doc['nodes'] if n.get('label',{}).get('localId')=='shared')
            return next(r for r in doc['localControl'] if r['source']==node['id'])
        a,b=documents['A'],documents['B']
        stripped=copy.deepcopy(b);shared(stripped)['resumeKey']='A'
        self.assertEqual(a,stripped,'only the key changes in the complete product')
        self.assertNotEqual(a,b)
        for name,doc in documents.items():
            self.assertEqual('7.0.0',verify(json.dumps(doc))['schemaVersion'])
            paths=Paths(doc);boundary=shared(doc)
            call=next(r for r in doc['localControl'] if r['kind']=='LOCAL_INVOKE' and r['operation']['localId']=='call-a')
            _,stack=paths.successors(vertex(call['source']),())[0]
            at=vertex(boundary['source']);result=paths.successors(at,stack)[0]
            if name in ('missing','key-only'):
                self.assertEqual((vertex(boundary['invalidExit']),()),result)
            elif name=='legacy':
                self.assertNotIn('resumeKey',boundary);self.assertNotIn('invalidExit',boundary)
                self.assertEqual((vertex(call['resume']),()),result)
            else:
                label='ordinary' if name=='A' else 'resume'
                destination=next(n['id'] for n in doc['nodes'] if n.get('label',{}).get('localId')==label)
                self.assertEqual((vertex(destination),()),result)
            # A matching ancestor may not supply a missing key on the top frame.
            if name=='missing':
                ancestor=stack[0][:4]+((('missing',vertex(call['resume'])),),)
                self.assertEqual((vertex(boundary['invalidExit']),()),paths.successors(at,(ancestor,)+stack)[0])
            default=vertex(boundary['defaultDestination'])
            self.assertEqual((default,()),paths.successors(at,())[0])
            other=copy.deepcopy(call);other['ports']=[]
            paths.rules[vertex(call['source'])]=other
            _,mismatch=paths.successors(vertex(call['source']),())[0]
            nested=stack+mismatch
            self.assertEqual((default,nested),paths.successors(at,nested)[0],'incompatible top port leaves all frames unchanged')

    def test_selected_boundary_contract_rejects_missing_or_foreign_invalid_exit(self):
        import copy
        doc=self.local_document();doc['schemaVersion']='7.0.0'
        boundary=doc['localControl'][1];boundary.update(kind='LOCAL_BOUNDARY',port=dict(publication='p',unit='u',localId='end'),
            defaultDestination=doc['nodes'][2]['id'],resumeKey='A')
        doc['nodes'][1]['terminator']['kind']='LOCAL_BOUNDARY'
        self.assertEqual('7.0.0',verify(json.dumps(doc))['schemaVersion'])
        for mutation in ('version','missing-exit','missing-key','blank','foreign','tag','operation'):
            bad=copy.deepcopy(doc);r=bad['localControl'][1]
            if mutation=='version':bad['schemaVersion']='6.0.0'
            elif mutation=='missing-exit':del r['invalidExit']
            elif mutation=='missing-key':del r['resumeKey']
            elif mutation=='blank':r['resumeKey']=' '
            elif mutation=='foreign':r['invalidExit']['publication']='foreign'
            elif mutation=='tag':bad['nodes'][3]['tag']='invalid_local_unwind'
            else:bad['nodes'][3]['operation']['localId']='other'
            with self.assertRaises(ValueError,msg=mutation):verify(json.dumps(bad))

    def test_rejects_unknown_versions_tokens_and_unnecessary_upgrade(self):
        for version, term, edge in (('1.1.0', 'INVOKE', 'INVOKE_NORMAL'), ('2.0.0', 'CALL', 'INVOKE_NORMAL'),
                                    ('2.0.0', 'INVOKE', 'CALL'), ('2.0.0', 'RETURN', 'RETURN')):
            with self.assertRaises(ValueError):
                verify(self.document(version, term, edge))

    def test_duplicate_version_cannot_hide_incompatible_contract(self):
        data = self.document('2.0.0').replace('"schemaVersion": "2.0.0"', '"schemaVersion":"1.0.0","schemaVersion":"2.0.0"')
        with self.assertRaisesRegex(ValueError, 'duplicate'):
            verify(data)


if __name__ == '__main__':
    unittest.main()
