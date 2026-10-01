import copy,json,unittest
from cfg_local_paths import Paths
from local_context_oracle import evaluate

class LocalOracleTest(unittest.TestCase):
    def test_cfg_returns_match_and_empty_resume_is_exceptional(self):
        ref=lambda n:dict(publication='p',ordinal=str(n))
        op=lambda n:dict(publication='p',unit='u',localId=n)
        cfg=dict(nodes=[dict(id=ref(n),kind='SEQUENCE') for n in range(5)]+[dict(id=ref(5),kind='OUTCOME_EXIT')],transitions=[],localControl=[
            dict(source=ref(0),operation=op('a'),kind='LOCAL_INVOKE',entry=ref(2),resume=ref(1),ports=[]),
            dict(source=ref(1),operation=op('b'),kind='LOCAL_INVOKE',entry=ref(2),resume=ref(3),ports=[]),
            dict(source=ref(2),operation=op('body'),kind='LOCAL_RESUME',invalidExit=ref(5))])
        paths=Paths(cfg);at=('p','0');stack=();trace=[]
        while True:
            trace.append(at[1]);nexts=paths.successors(at,stack)
            if not nexts:break
            self.assertEqual(1,len(nexts));at,stack=nexts[0]
        self.assertEqual(['0','2','1','2','3'],trace)
        self.assertEqual({('p','2'),('p','5')},paths.reachable([('p','2')]))

    def test_scalar_interpreter_keeps_values_per_frame_and_rejects_unknown_writes(self):
        ref=lambda n:dict(publication='p',unit='u',localId=n)
        h=lambda n:dict(id=ref(n),origin=ref('origin'))
        lit=lambda v:dict(kind='literal',value=dict(kind='text',value=v))
        assign=lambda v:dict(kind='assign',destination=dict(kind='object',object=ref('x')),value=lit(v))
        call=lambda n,resume:dict(kind='local.invoke',header=h(n),entry=ref('body'),resume=ref(resume),completionPorts=[])
        native=dict(kind='invoke',header=h('call'),target=dict(kind='computed',name=dict(kind='read',place=dict(kind='object',object=ref('x')))),effectBound=dict(perOutcome=[],otherwise=dict(writes=dict(kind='none'),mustOverwrite=[])),outcomes=dict(known=[dict(kind='normal',label=ref('end'))],remainder=dict(kind='none')))
        seq=lambda n,ops,t:dict(label=ref(n),instructions=ops,terminator=t)
        air=dict(publication=dict(origins=[dict(id=ref('origin'),kind='unavailable')],artifacts=[],units=[dict(entries=[dict(id=ref('entry'),initialLabel=ref('a'),state=dict(conditions=[]))],sequences=[seq('a',[assign('A')],call('a','b')),seq('b',[assign('B')],call('b','done')),seq('body',[],native),seq('end',[],dict(kind='local.resume')),seq('done',[],dict(kind='return'))])]))
        observed=evaluate(air,'fixture.cbl')
        self.assertEqual({'a':['A'],'b':['B']},{r['frame']['localId']:r['values'] for r in observed})
        bad=copy.deepcopy(air);bad['publication']['units'][0]['sequences'][2]['terminator']['effectBound']['otherwise']['writes']['kind']='all'
        with self.assertRaisesRegex(ValueError,'invoke write'):evaluate(bad,'fixture.cbl')

if __name__=='__main__':unittest.main()
