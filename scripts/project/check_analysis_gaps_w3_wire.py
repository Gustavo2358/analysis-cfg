#!/usr/bin/env python3
"""Independent wire expectations for ConsumerCoverageTest's AIR fixtures."""
import argparse
from pathlib import Path
from dependency_wire import read, require


def check(folder):
    cases = {f'{command}-{shape}-{mode}': (command, mode) for command in ('LINK', 'XCTL', 'file')
             for shape in ('name', 'slice', 'choice')
             for mode in ('simple', 'multiple', 'unknown', 'partial')}
    cases.update({f'{command}-literal': (command, 'literal') for command in ('LINK', 'XCTL', 'file', 'native')})
    cases.update({'program-choice-reconciled': ('LINK', 'multiple'), 'native-computed-unadmitted': ('native', 'unadmitted')})
    cases.update({f'program-choice-{mode}': ('LINK', 'unadmitted') for mode in ('short-leaf', 'open')})
    for name, (command, mode) in cases.items():
        result = read(folder / (name + '.json'))
        files = command in ('file', 'native')
        site, = result['fileDependencies']['sites'] if files else result['sites']
        names = [] if mode in ('unknown', 'unadmitted') else ['CLIENTDD'] if command == 'native' else ['PROGA', 'PROGB'] if mode == 'multiple' else ['PROGA']
        require([c['referenceName'] for c in site['candidates']] == names, 'wire candidate set: ' + name)
        require(site['namespace'] == ('cics.file' if command == 'file' else 'cobol.external-file-name' if command == 'native' else 'cics.program'), 'namespace')
        require(site['unknownRemainder' if files else 'effectiveUnknownRemainder'] == (mode in ('unknown', 'partial', 'unadmitted') or command == 'XCTL'), 'wire unknown: ' + name)
        if not files:
            require(site['command'] == command and site['technology'] == 'CICS', 'command identity')
            require(site['targetStatus'] == ('UNSUPPORTED_TARGET_EXPRESSION' if mode == 'unadmitted' else 'OPEN_TARGET' if mode == 'unknown' else 'RESOLVED_CANDIDATES'), 'status')
        for candidate in site['candidates']:
            support, = candidate['supports']
            require(support['origin']['domain'] == 'origin' and isinstance(support['premises'], list), 'wire origin/premise contract')
            if mode != 'literal':
                require(support['kind'] == 'VALUE_PRODUCER' and support['producer']['localId'] == ('seed-a' if candidate['referenceName'] == 'PROGA' else 'seed-b'), 'no cross-associated wire supports')
            else:
                require(support['kind'] == ('FILE_LITERAL' if files else 'CICS_LITERAL'), 'literal support kind')
        require(not result['sites'] if files else not result['fileDependencies']['sites'], 'separate inventories')
    print(f'PASS W3 independent dependency wire: {len(cases)} cases')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--artifacts', type=Path, default=Path('analysis-adapters/target/analysis-gaps-w3'))
    check(parser.parse_args().artifacts)
