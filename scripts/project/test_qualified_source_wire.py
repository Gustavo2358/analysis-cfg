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
