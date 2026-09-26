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
    elif mutate=='newer-version':x['version']='2.7.0'
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
