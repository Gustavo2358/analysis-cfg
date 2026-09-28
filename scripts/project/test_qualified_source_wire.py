import copy
import json
from pathlib import Path
from dependency_wire import validate as result, read
from qualified_source_wire import validate

FIX=Path(__file__).resolve().parents[2]/'analysis-adapters/src/test/resources/qualified-source-r9'
for path in FIX.glob('*.source.json'):validate(json.loads(path.read_text()))
d=read(FIX/'ordinary.dependencies.json')
for mutate in ['old-version','newer-version','dangling-node','operand','guards','proof','candidate','fake-air']:
    x=copy.deepcopy(d)
    if mutate=='old-version':x['version']='2.5.0'
    elif mutate=='newer-version':x['version']='2.8.0'
    elif mutate=='candidate':x['sourceQualifiedDependencies']['occurrences'][0]['candidates'][0]['qualifications']=[]
    else:
        u=x['sourceQualifiedDependencies']['evidence']['units'][0]
        if mutate=='dangling-node':u['occurrences'][0]['qualifications']=['missing']
        elif mutate=='operand':u['occurrences'][0]['operands'][0]['id']['statement']['handle']='foreign'
        elif mutate=='proof':u['derivations'][0]['proofs']=['missing']
        elif mutate=='guards':u['events']=[{'invented':'event'}]
        elif mutate=='fake-air':u['occurrences'][0]['operationId']='fake'
    try:result(x)
    except (ValueError,KeyError,TypeError):pass
    else:raise AssertionError(mutate)
print('R9_SOURCE_WIRE: 11 producer fixtures + 2.6 publication + 8 rejections PASS')

# Produced by the synthetic conditional-source contract test in the mandatory FAST inventory.
conditional=read(FIX.parents[3]/'target/conditional-source/dependencies.json')
result(conditional)
def conditional_candidate(document):
    return next(c for p in document['dependencies']['programs'] for c in p['candidates'] if 'conditionalSupports' in c)
for mutation in ('closed','no-assumption','foreign-origin','foreign-null-origin','missing-query','unknown-field','invalid-node'):
    x=copy.deepcopy(conditional); c=conditional_candidate(x)
    if mutation=='closed': next(p for p in x['dependencies']['programs'] if c in p['candidates'])['valueRemainder']=False
    elif mutation=='no-assumption':c['conditionalSupports'][0]['assumptions']=[]
    elif mutation=='foreign-origin':c['conditionalSupports'][0]['evidence'][0]['reference']='foreign'
    elif mutation=='foreign-null-origin':c['conditionalSupports'][0]['evidence'][0].update(reference='foreign',provenance=None)
    elif mutation=='missing-query':x['sourceQualifiedDependencies']['evidence']['units'][0]['nominalValues']['facts']['queries']=[]
    elif mutation=='unknown-field':x['sourceQualifiedDependencies']['evidence']['units'][0]['nominalValues']['extra']=True
    elif mutation=='invalid-node':x['sourceQualifiedDependencies']['evidence']['units'][0]['nominalValues']['facts']['queries'][0]['node']='foreign'
    try:result(x)
    except (ValueError,KeyError,TypeError):pass
    else:raise AssertionError('conditional '+mutation)
print('CONDITIONAL_SOURCE_WIRE: producer result + seven rejections PASS')

empty=read(FIX.parents[3]/'target/conditional-source/empty-executable.dependencies.json')
result(empty)
wrong=copy.deepcopy(empty);wrong['modelScope']='STRUCTURAL_AIR_OCCURRENCES'
try:result(wrong)
except ValueError:pass
else:raise AssertionError('source uncertainty changed executable scope')
print('CONDITIONAL_SOURCE_SCOPE: independent PARTIAL and executable scope PASS')

# Authority determines the closed symbol shape; model confidence cannot disappear.
from qualified_source_wire import shape
facts=dict(authority='NOMINAL_TEXT_SOURCE_V2',symbols=[dict(node='node',extent=1,modelAssumed=True)],assignments=[],conditions=[],queries=[])
shape(facts,{'$ref':'#/$defs/NominalValues'})
for mutation in ('missing-confidence','v1-with-confidence','unknown-authority','wrong-confidence'):
    x=copy.deepcopy(facts)
    if mutation=='missing-confidence':del x['symbols'][0]['modelAssumed']
    elif mutation=='v1-with-confidence':x['authority']='NOMINAL_TEXT_SOURCE_V1'
    elif mutation=='unknown-authority':x['authority']='NOMINAL_TEXT_SOURCE_V3'
    else:x['symbols'][0]['modelAssumed']='true'
    try:shape(x,{'$ref':'#/$defs/NominalValues'})
    except ValueError:pass
    else:raise AssertionError(mutation)
print('MODEL_SOURCE_WIRE: V2 confidence and four rejections PASS')

from dependency_wire import conditional_assumptions
base=['NOMINAL_DECLARATIONS_PRESERVE_MEANING','NO_UNMODELED_STORAGE_INTERFERENCE']
model=base+['SYNTHETIC_MODEL_IS_NOT_KILL_PROOF']
conditional_assumptions(model,facts)
for assumptions,f in [(model+['INVENTED'],facts),(model+model,facts),(model,{**facts,'authority':'NOMINAL_TEXT_SOURCE_V1'}),(model,{**facts,'symbols':[]})]:
    try:conditional_assumptions(assumptions,f)
    except ValueError:pass
    else:raise AssertionError('unproved or malformed model assumption')
print('MODEL_ASSUMPTIONS_WIRE: producer assumption and four rejections PASS')

# W1 producer outputs retain uncertainty and declaration evidence independently of AIR.
W1=FIX.parent/'source-possibility'
for path in W1.glob('*.dependencies.json'):result(json.loads(path.read_text()))
base=json.loads((W1/'sql-update.dependencies.json').read_text())
for mutation in ('old-version','old-source-version','closed-control','false-status','lost-reason','false-authority'):
    x=copy.deepcopy(base);p=x['dependencies']['programs'][0]
    if mutation=='old-version':x['version']='2.6.0'
    elif mutation=='old-source-version':x['sourceQualifiedDependencies']['evidence']['version']='1.0.0'
    elif mutation=='closed-control':p.pop('controlRemainder')
    elif mutation=='false-status':x['sourceQualifiedDependencies']['occurrences'][0]['status']='QUALIFIED_POSSIBLE'
    elif mutation=='lost-reason':p['analysisReasons']=[]
    elif mutation=='false-authority':p['authorities']=['SOURCE_QUALIFIED']
    try:result(x)
    except (ValueError,KeyError,TypeError):pass
    else:raise AssertionError(mutation)
native=json.loads((W1/'unknown-native.dependencies.json').read_text())
for mutation in ('lost-use','fake-name','lost-origin','wrong-point','false-status','closed-remainder'):
    x=copy.deepcopy(native);f=x['dependencies']['files'][0]
    if mutation=='lost-use':x['dependencies']['files'].pop()
    elif mutation=='fake-name':f['candidates'][0]['referenceName']='INVENTED'
    elif mutation=='lost-origin':f['source']['names'][0]['declarationOrigins']=[]
    elif mutation=='wrong-point':f['source']['controlLocation']='wrong'
    elif mutation=='false-status':f['status']='QUALIFIED_POSSIBLE'
    else:f['remainder']=False
    try:result(x)
    except (ValueError,KeyError,TypeError):pass
    else:raise AssertionError(mutation)
print('SOURCE_POSSIBILITY_WIRE: fourteen producer outputs + twelve rejections PASS')

for name,expected in [('unknown-branch-query',{'FIRST','SECOND'}),('unknown-branch-kill',{'FINAL'}),('unknown-copy-kill',{'FIRST','SECOND'})]:
    d=read(W1/(name+'.dependencies.json'));rows=d['dependencies']['programs']
    assert {c['referenceName'] for p in rows for c in p['candidates']}==expected,name
    assert all(p['controlRemainder'] for p in rows),name
print('SOURCE_JOIN_VALUES: open branch survives closed executable query; exact overwrite retained PASS')

# W7 uses conditional source summaries; source-undefined effects are not executable returns.
reentry=FIX.parent/'perform-reentry'
for path in reentry.glob('*.source.json'):
    value=json.loads(path.read_text());validate(value)
    if value['version']=='1.2.0':
        for version in ('1.0.0','1.1.0','1.3.0'):
            changed=copy.deepcopy(value);changed['version']=version
            try:validate(changed)
            except (ValueError,KeyError,TypeError):pass
            else:raise AssertionError('reentry downgrade/future '+version)
        changed=copy.deepcopy(value)
        support=next(n['support'] for u in changed['units'] for n in u['nodes'] if n['support']['cause']=='SOURCE_REENTRY_UNDEFINED')
        support['cause']='INVENTED_CAUSE'
        try:validate(changed)
        except ValueError:pass
        else:raise AssertionError('unknown support cause')
assert len(list(reentry.glob('*.source.json')))==20
print('REENTRY_SOURCE_WIRE: 20 producer outputs and version/cause rejection PASS')
