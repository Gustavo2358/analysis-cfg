"""Derived, non-normative CardDemo metrics. Can re-report immutable raw runs without rerunning JVMs."""
from collections import Counter, defaultdict
import json
import math
from pathlib import Path
import statistics
import copy
import re
import time

POLICY = ('CardDemo evidence provides advisory implementation suggestions. It does not select or mandate '
          'the next capability. Human engineering/product judgment remains authoritative.')
CLASSES = ('CLOSED_RESOLVED', 'PARTIAL_RESOLVED', 'OPEN_UNRESOLVED', 'UNREACHABLE_IN_MODEL', 'OTHER')


def validate_enumeration(sources, programs):
    expected, actual = [p['path'] for p in sources], [p['path'] for p in programs]
    if len(actual) != len(set(actual)) or sorted(expected) != sorted(actual):
        raise ValueError('FULL CORPUS ENUMERATION: every discovered path must appear exactly once')


def ratio(numerator, denominator):
    return {'numerator': numerator, 'denominator': denominator,
            'percent': 100 * numerator / denominator if denominator else None}


def timing_stats(values):
    ordered = sorted(values, key=lambda item: (item[1], item[0]))
    times = [t for _, t in ordered]
    return {'count': len(times), 'totalMs': sum(times), 'meanMs': statistics.mean(times) if times else None,
            'medianMs': statistics.median(times) if times else None, 'minMs': min(times) if times else None,
            'maxMs': max(times) if times else None,
            'p95Ms': times[math.ceil(0.95 * len(times)) - 1] if len(times) >= 20 else None,
            'p95Method': 'nearest rank; published for n >= 20',
            'fastestProgram': ordered[0][0] if times else None, 'fastestProgramMs': ordered[0][1] if times else None,
            'slowestProgram': ordered[-1][0] if times else None, 'slowestProgramMs': ordered[-1][1] if times else None}


def classify_site(site):
    if site['reachability'] == 'UNREACHABLE_IN_MODEL': return 'UNREACHABLE_IN_MODEL'
    unknown = any(site.get(k) for k in ('modelValueRemainder', 'sourceValueRemainder',
                 'interpretationUnknownRemainder', 'effectiveUnknownRemainder', 'openControlRemainder', 'uncertaintyRefs'))
    if site['candidates']:
        return 'PARTIAL_RESOLVED' if unknown or site.get('modelValueRemainder') is not False else 'CLOSED_RESOLVED'
    return 'OPEN_UNRESOLVED' if unknown else 'OTHER'


def known_dependencies(sites):
    pairs = {}
    for s in sites:
        if s['reachability'] != 'REACHABLE': continue
        for candidate in s['candidates']:
            key = (s['callerName'], candidate['referenceName'])
            pair = pairs.setdefault(key, {'callerName': key[0], 'referenceName': key[1], 'sites': []})
            ref = {'program': s['sourceProgram'], 'operation': s['operation']}
            if ref not in pair['sites']: pair['sites'].append(ref)
    return [pairs[key] for key in sorted(pairs)]


def advisory(dimensions):
    result = {'decisionPolicy': 'HUMAN', 'rankingAuthority': 'ADVISORY_ONLY', 'normativity': 'NON_NORMATIVE',
              'policy': POLICY, 'suggestions': dimensions,
              'selectionRule': 'No automatic next capability. Raw dimensions remain separate from engineering cost/risk.',
              'causalRanking': 'No per-capability causal ranking unless individual wire evidence supports it.'}
    validate_advisory(result)
    return result


def advisory_observations(aggregated):
    blockers = aggregated['pipelineBlockers']
    entry = [b for b in blockers if b['reasonCode'] == 'ENTRY_START_UNAVAILABLE']
    front = [b for b in blockers if b['stage'] == 'frontend']
    return [
        {'topic': 'Input and executable-entry completeness',
         'observation': 'Investigate the explicit SP entry/input gaps behind the lower ENTRY_START refusal.',
         'programsBlocked': len(entry), 'observedSPCallsBehindBlocker': sum(b['observedSPCallsNotAnalyzed'] for b in entry),
         'inputMissingPrograms': sum('INPUT_MISSING' in b['entryAvailability'] for b in entry),
         'limit': 'No missing copybook stubs. Supplying real dependencies or extending a supported entry surface requires separate human scope; gain is not guaranteed.'},
        {'topic': 'Frontend preprocessing and normalization acceptance',
         'programsBlocked': len(front), 'reasonCodes': dict(Counter(b['reasonCode'] for b in front)),
         'observation': 'These are distinct input/frontend acceptance blockers, not evidence that a COBOL statement capability should be implemented.',
         'hiddenCallSites': None, 'limit': 'CALLs in programs without SP were never counted.'},
        {'topic': 'CALL boundary and runtime interpretation',
         'partialSites': aggregated['callCoverage']['categories']['PARTIAL_RESOLVED']['numerator'],
         'analyzedSites': aggregated['callCoverage']['sitesInDependencyResults'],
         'observation': 'Known targets can remain model-closed while source, interpretation and control remainders stay open.',
         'supportingDimensions': aggregated['callBoundaryUncertainties'],
         'limit': 'Site-local uncertainty proves partiality, not that implementing one language feature will close it.'},
        {'topic': 'Semantic-family candidates for human comparison',
         'observation': 'Compare observed MOVE, IF, PERFORM, EVALUATE and GO_TO dimensions with generic-kind inventory limits.',
         'limit': 'Potential bounds overlap; no reliable per-capability causal CALL-impact ranking or automatic next implementation.'}
    ]


def validate_advisory(value):
    if value['decisionPolicy'] != 'HUMAN' or value['rankingAuthority'] != 'ADVISORY_ONLY':
        raise ValueError('ranking cannot become normative roadmap')
    def visit(item):
        if isinstance(item, dict):
            if {'nextFeature', 'mandatoryNext', 'selectedCapability'} & item.keys():
                raise ValueError('automatic roadmap selection forbidden')
            for child in item.values(): visit(child)
        elif isinstance(item, list):
            for child in item: visit(child)
    visit(value)


def key(value):
    return json.dumps(value, sort_keys=True, separators=(',', ':'))


def family(statement):
    return statement.get('observedKind', statement['variant'])


def capability(statement):
    # Only SP facts: an opaque embedded-language shape can identify its language.
    kind, shape = family(statement), statement.get('observedShape', '')
    return shape.removeprefix('OPAQUE_') if kind == 'EMBEDDED_LANGUAGE' and shape.startswith('OPAQUE_') else kind


def statement_inventory(sp):
    counts = {'totalStatements': len(sp['statements']), **{k: sp['coverage'][k] for k in
              ('modeledStatements', 'partialStatements', 'unsupportedStatements', 'inputMissingStatements')}}
    if counts['totalStatements'] != sp['coverage']['observedStatements'] or counts['totalStatements'] != sum(
            counts[k] for k in ('modeledStatements', 'partialStatements', 'unsupportedStatements', 'inputMissingStatements')):
        raise ValueError('SP statement count partition differs from published inventory')
    families = defaultdict(Counter)
    for statement in sp['statements']:
        families[family(statement)][statement['header']['coverage']] += 1
    return {**counts, 'inventoryStatus': sp['coverage']['inventoryStatus'],
            'authority': 'SP typed/observed facts; no source regex classification',
            'byFamily': {f: {'total': sum(c.values()), 'coverage': dict(c)} for f, c in sorted(families.items())},
            'inventory': [{'statement': s['header']['id'], 'programPoint': s['header']['programPoint'],
                           'variant': s['variant'], 'observedKind': family(s), 'observedShape': s.get('observedShape'),
                           'coverage': s['header']['coverage'], 'provenance': s['header']['provenance']}
                          for s in sp['statements']]}


def gap_category(code, scope):
    if 'COPY_NOT_FOUND' in code: return 'COPYBOOK_MISSING'
    if scope in ('INPUT', 'ANALYSIS_INPUT') or 'INPUT_MISSING' in code or 'INPUT_INCOMPLETE' in code: return 'CORPUS_INPUT_GAP'
    return 'SEMANTIC_CAPABILITY_GAP'


def sp_gaps(sp, program):
    facts = {s['header']['id']: s for s in sp['statements']}
    result = []
    for gap in sp['gaps']:
        fact = facts[gap['statement']]
        result.append({'program': program, 'statement': gap['statement'], 'programPoint': fact['header']['programPoint'],
                       'observedKind': family(fact), 'capability': capability(fact), 'gapCode': gap['code'],
                       'scope': gap['scope'], 'provenance': gap['provenance'], 'diagnostic': gap['detail'],
                       'reasonCategory': gap_category(gap['code'], gap['scope']), 'authority': 'SP'})
    for entry in sp['entryInventory']['entries']:
        for gap in entry['gaps']:
            result.append({'program': program, 'statement': None, 'entry': entry['id'], 'observedKind': None,
                           'capability': 'ENTRY', 'gapCode': gap['code'], 'scope': gap['scope'],
                           'provenance': gap['provenance'], 'diagnostic': gap['detail'],
                           'reasonCategory': gap_category(gap['code'], gap['scope']), 'authority': 'SP_ENTRY'})
    for code in sp['entryInventory']['gapCodes']:
        result.append({'program': program, 'statement': None, 'observedKind': None, 'capability': 'ENTRY_INVENTORY',
                       'gapCode': code, 'scope': 'ENTRY_INVENTORY', 'provenance': None,
                       'reasonCategory': 'SEMANTIC_CAPABILITY_GAP', 'authority': 'SP_ENTRY'})
    return result


def diagnostic_gaps(directory, record):
    gaps = []
    tree_path = directory / 'sp/tree-data.js'
    if tree_path.exists():
        tree = json.loads(tree_path.read_text().split('=', 1)[1].strip().rstrip(';'))
        record['frontendDiagnostics'] = {'meta': tree['meta'], 'diagnostics': tree['diagnostics']}
        for d in tree['diagnostics']:
            phase, message = d['phase'], d['message']
            missing = re.search(r"COPY '([^']+)' could not be found", message)
            category = ('COPYBOOK_MISSING' if missing else 'PARSER_FRONTEND_GAP' if phase in ('LEXER', 'PARSER') else 'PREPROCESSING_GAP')
            gaps.append({'program': record['path'], 'statement': None, 'observedKind': None, 'capability': category,
                         'gapCode': 'COPY_NOT_FOUND' if missing else phase + '_DIAGNOSTIC', 'scope': 'INPUT',
                         'provenance': {'frontendDiagnosticLine': d.get('line'), 'coordinateSystem': 'frontend diagnostic; not a typed statement'},
                         'reasonCategory': category, 'diagnostic': message, 'requestedDependency': missing.group(1) if missing else None,
                         'authority': 'FRONTEND_DIAGNOSTIC'})
    for stage, outcome in record['stages'].items():
        if outcome['state'] in ('BLOCKED', 'FAILED', 'TIMEOUT'):
            code = outcome['reasonCode']
            # Refine reason codes from real CLI diagnostics, without changing measured outcome/time.
            diagnostic = outcome['diagnostic'] or ''
            if 'Preprocessor grammar construct has no explicit policy: EXEC' in diagnostic: code = 'PREPROCESSOR_EXEC_POLICY_MISSING'
            elif 'ENTRY_START entry:' in diagnostic: code = 'ENTRY_START_UNAVAILABLE'
            outcome['reasonCode'] = code
            gaps.append({'program': record['path'], 'statement': None, 'observedKind': None,
                         'capability': outcome['reasonCategory'], 'gapCode': code, 'scope': 'PIPELINE_STAGE',
                         'stage': stage, 'provenance': {'stderr': record['rawDirectory'] + '/' + stage + '.stderr'},
                         'reasonCategory': outcome['reasonCategory'], 'diagnostic': diagnostic,
                         'authority': 'PROCESS_DIAGNOSTIC'})
    if record.get('runnerFailure'):
        gaps.append({'program': record['path'], 'statement': None, 'observedKind': None, 'capability': 'INTERNAL_FAILURE',
                     'gapCode': record['runnerFailure']['reasonCode'], 'scope': 'RUNNER', 'provenance': None,
                     'reasonCategory': 'INTERNAL_FAILURE', 'diagnostic': record['runnerFailure']['diagnostic'], 'authority': 'RUNNER'})
    return gaps


def bind_sites(sp, air, dependency, program):
    facts = {s['header']['id']: s for s in sp['statements']}
    by_op = defaultdict(set)
    for item in air.get('coverage', {}).get('items', []):
        statement = item['sourceKey'].rsplit('/', 1)[-1]
        if statement in facts:
            for output in item['outputs']:
                if output['domain'] == 'operation': by_op[key(output)].add(statement)
    result = []
    for raw in dependency['sites']:
        statements = [facts[s] for s in sorted(by_op[key(raw['operation'])]) if facts[s]['variant'] == 'CALL']
        if len(statements) != 1: raise ValueError('dependency site must join exactly one typed SP CALL')
        fact = statements[0]
        result.append({**raw, 'classification': classify_site(raw), 'sourceProgram': program,
                       'callerName': sp['unit']['canonicalProgramName'], 'sourceStatement': fact['header']['id'],
                       'sourceProgramPoint': fact['header']['programPoint'], 'sourceProvenance': fact['header']['provenance']})
    return result


def control_bound_contains(operation, site):
    """Scope membership only; not a causal or runtime reachability proof."""
    if operation['kind'] != 'opaque' or not site['openControlRemainder']: return False
    remainder = operation['envelope']['control']['remainder']
    if remainder['kind'] != 'within': return False
    scope = remainder['scope']
    if scope['kind'] == 'unit':
        return scope.get('labels') is True and scope['unit'] == site['caller']
    return False  # Unrecognized bounds are UNKNOWN, never guessed from source order.


def attach_impacts(record, sp, air):
    sites = record['callSites']
    operations = {key(o['header']['id']): o for unit in air.get('units', []) for seq in unit['sequences']
                  for o in [*seq['instructions'], seq['terminator']]}
    coverage = defaultdict(list)
    uncertainties_by_code = defaultdict(list)
    for uncertainty in air.get('uncertainties', []):
        uncertainties_by_code[uncertainty['code'].removeprefix('cobol-lower:')].append(uncertainty)
    for item in air.get('coverage', {}).get('items', []):
        coverage[item['sourceKey'].rsplit('/', 1)[-1]].append(item)
    for gap in record['gaps']:
        direct, potential, evidence = [], [], []
        statement = gap['statement']
        for item in coverage[statement]:
            for output in item['outputs']:
                op = operations.get(key(output))
                if not op: continue
                bounded_sites = []
                for site in sites:
                    if control_bound_contains(op, site) and output != site['operation']:
                        if site['operation'] not in potential: potential.append(site['operation'])
                        bounded_sites.append(site['operation'])
                if bounded_sites:
                    evidence.append({'kind': 'CONTROL_ENVELOPE_MEMBERSHIP', 'regionOperation': output,
                                     'siteOperations': bounded_sites, 'scope': op['envelope']['control']['remainder'],
                                     'causality': 'UNKNOWN; region bound admits these sites, individual gap causality not separable'})
        # Direct attribution only from an exact uncertainty-code match with site references.
        region_operations = [o for item in coverage[statement] for o in item['outputs'] if o['domain'] == 'operation']
        for uncertainty in uncertainties_by_code[gap['gapCode']]:
            for site in sites:
                if (site['operation'] in region_operations and uncertainty['id'] in site['uncertaintyRefs']
                        and site['operation'] in uncertainty['scope'].get('entities', [])):
                    direct.append(site['operation'])
                    evidence.append({'kind': 'EXACT_CODE_SITE_UNCERTAINTY', 'uncertainty': uncertainty['id'], 'siteOperation': site['operation']})
        gap['impact'] = {'attribution': 'DIRECT' if direct else 'POTENTIAL' if potential else 'UNKNOWN',
                         'directCallSites': direct, 'potentialCallSites': [p for p in potential if p not in direct], 'evidence': evidence}
    # Preserve site-local AIR evidence separately: no pretending it is a COBOL family frequency.
    record['callBoundaryUncertainties'] = []
    for uncertainty in air.get('uncertainties', []):
        affected = [s for s in sites if uncertainty['id'] in s['uncertaintyRefs'] and
                    s['operation'] in uncertainty['scope'].get('entities', []) and
                    set(uncertainty['dimensions']) & {'DEPENDENCIES', 'VALUES', 'CONTROL'}]
        if affected:
            record['callBoundaryUncertainties'].append({**uncertainty, 'directCallSites': [s['operation'] for s in affected],
                                                       'attribution': 'DIRECT_SITE_UNCERTAINTY; not a counterfactual implementation gain'})


def enrich(raw, root):
    programs = copy.deepcopy(raw['programs'])
    for record in programs:
        directory = root / record['rawDirectory']
        record['gaps'] = diagnostic_gaps(directory, record)
        record['statements'] = None
        record['sourceCallInventory'] = []
        record['callSites'] = []
        record['callBoundaryUncertainties'] = []
        sp, air = None, {}
        if record['stages']['frontend']['artifactProduced']:
            sp = json.loads((root / record['artifacts']['frontend']['path']).read_text())
            record['statements'] = statement_inventory(sp)
            record['gaps'] += sp_gaps(sp, record['path'])
            record['entryInventory'] = sp['entryInventory']
            record['sourceCallInventory'] = [{'statement': s['header']['id'], 'programPoint': s['header']['programPoint'],
                                             'provenance': s['header']['provenance'], 'syntax': s['syntax'],
                                             'target': s['target'], 'surface': s['surface'], 'operationIds': []}
                                            for s in sp['statements'] if s['variant'] == 'CALL']
        if record['stages']['lower']['artifactProduced']:
            air = json.loads((root / record['artifacts']['lower']['path']).read_text())['publication']
        if record['stages']['dependency']['state'] in ('PASS', 'PARTIAL'):
            from dependency_wire import read
            dep = read(root / record['artifacts']['dependency']['path'])
            record['callSites'] = bind_sites(sp, air, dep, record['path'])
            record['dependencyMetrics'] = dep['metrics']
            record['dependencyProvenance'] = relevant_provenance(dep)
            uncertainty_ids = {key(u) for s in dep['sites'] for u in s['uncertaintyRefs']}
            record['callUncertaintyCatalog'] = [u for u in air.get('uncertainties', []) if key(u['id']) in uncertainty_ids]
            for call in record['sourceCallInventory']:
                call['operationIds'] = [s['operation'] for s in record['callSites'] if s['sourceStatement'] == call['statement']]
            # No dropped CALL at the SP -> dependency boundary in a completed pipeline.
            if any(not c['operationIds'] for c in record['sourceCallInventory']):
                raise ValueError('typed SP CALL has no dependency site in completed pipeline: ' + record['path'])
        attach_impacts(record, sp, air)
        record['callMetrics'] = {'sourceSitesObserved': len(record['sourceCallInventory']),
                                 'dependencySitesProduced': len(record['callSites']),
                                 'categories': {c: sum(s['classification'] == c for s in record['callSites']) for c in CLASSES}}
    return programs


def relevant_provenance(dependency):
    """Keep the full origin DAG needed by every site/support, not unrelated operation origins."""
    origins = {key(o['id']): o for o in dependency['origins']}
    pending = [ref for s in dependency['sites'] for ref in [s['siteOrigin'], s['targetOrigin'], *s['provenance']]]
    seen = set()
    while pending:
        ref = pending.pop(); ident = key(ref)
        if ident in seen: continue
        seen.add(ident)
        origin = origins[ident]
        if origin['kind'] == 'DERIVED': pending.extend(origin['inputs'])
    kept = [o for o in dependency['origins'] if key(o['id']) in seen]
    artifact_ids = {key(o['artifact']) for o in kept if o['kind'] == 'WRITTEN'}
    for o in kept:
        for frame in o.get('includes', []): artifact_ids.update((key(frame['including']), key(frame['included'])))
    return {'origins': kept, 'artifacts': [a for a in dependency['artifacts'] if key(a['id']) in artifact_ids],
            'sourceUncertaintyRefs': dependency['sourceUncertaintyRefs'],
            'scope': 'complete transitive provenance for CALL sites/supports; unrelated origins remain in hashed raw product'}


def gap_aggregates(programs):
    buckets = defaultdict(list)
    for program in programs:
        for gap in program['gaps']:
            buckets[(gap['reasonCategory'], gap['gapCode'], gap['observedKind'], gap['authority'])].append(gap)
    return [{'reasonCategory': k[0], 'gapCode': k[1], 'observedKind': k[2], 'authority': k[3],
             'occurrences': len(v), 'programsAffected': len({g['program'] for g in v})}
            for k, v in sorted(buckets.items(), key=lambda kv: (-len(kv[1]), str(kv[0])))]


def impact_dimensions(programs):
    buckets = {}
    for program in programs:
        complete = program['stages']['dependency']['state'] in ('PASS', 'PARTIAL')
        for gap in program['gaps']:
            name = gap['capability']
            b = buckets.setdefault(name, {'occurrences': set(), 'programs': set(), 'direct': set(), 'potential': set(),
                                          'partial': set(), 'open': set(), 'closed': set(), 'blocked': set(), 'unknown': set(),
                                          'presentBlocked': set(), 'unanalysedCalls': set()})
            # Capability frequency: unique statement/diagnostic locations, not number of gap codes on one statement.
            b['occurrences'].add((program['path'], gap['statement'] or key([gap['gapCode'], gap['provenance']])))
            b['programs'].add(program['path'])
            if not complete:
                b['presentBlocked'].add(program['path'])
                entry_input = any(g['code'] == 'ENTRY_INPUT_INCOMPLETE'
                                  for e in program.get('entryInventory', {}).get('entries', []) for g in e['gaps'])
                entry_block = program['stages']['lower']['reasonCode'] == 'ENTRY_START_UNAVAILABLE'
                attributable = (gap['authority'] == 'PROCESS_DIAGNOSTIC'
                                or entry_block and gap['gapCode'] == 'EXECUTABLE_START_NOT_AVAILABLE'
                                or entry_block and entry_input and gap['reasonCategory'] in ('COPYBOOK_MISSING', 'CORPUS_INPUT_GAP'))
                if attributable:
                    b['blocked'].add(program['path'])
                    b['unanalysedCalls'].update((program['path'], c['statement']) for c in program['sourceCallInventory'])
            impact = gap['impact']
            if 'ref' in impact: impact = program['impactCatalog'][impact['ref']]
            if impact['attribution'] == 'UNKNOWN': b['unknown'].add(program['path'])
            for scope in ('direct', 'potential'):
                b[scope].update((program['path'], key(op)) for op in impact[scope + 'CallSites'])
            for site in program['callSites']:
                if site['operation'] in impact['directCallSites'] + impact['potentialCallSites']:
                    group = {'PARTIAL_RESOLVED': 'partial', 'OPEN_UNRESOLVED': 'open', 'CLOSED_RESOLVED': 'closed'}.get(site['classification'])
                    if group: b[group].add((program['path'], key(site['operation'])))
    return [{'capability': name, 'occurrences': len(b['occurrences']), 'programsAffected': len(b['programs']),
             'directlyAffectedCallSites': len(b['direct']), 'potentiallyAffectedCallSites': len(b['potential'] - b['direct']),
             'partialResolvedCallSites': len(b['partial']), 'openUnresolvedCallSites': len(b['open']),
             'closedCallSitesWithinBound': len(b['closed']), 'pipelineBlockedPrograms': len(b['blocked']),
             'presentInBlockedPrograms': len(b['presentBlocked']), 'observedSPCallsBehindPipelineBlocker': len(b['unanalysedCalls']),
             'unknownAttributionPrograms': len(b['unknown']),
             'severity': 'PIPELINE_BLOCKER' if b['blocked'] else 'HIGH' if b['open'] else 'MEDIUM' if b['partial'] else 'LOW' if b['closed'] else 'UNKNOWN',
             'pipelineBlockerMeaning': 'failing stage or explicit SP entry/input gap linked to ENTRY_START refusal; overlapping dimensions, no guaranteed counterfactual gain'}
            for name, b in sorted(buckets.items(), key=lambda kv: (-len(kv[1]['blocked']), -len(kv[1]['open']), -len(kv[1]['partial']), -len(kv[1]['occurrences']), kv[0]))]


def aggregate(programs, timings):
    stages = ('frontend', 'lower', 'cfg', 'dependency')
    reached = lambda p, stage: p['stages'][stage]['state'] in ('PASS', 'PARTIAL')
    completed = [p for p in programs if reached(p, 'dependency')]
    populations = [('discovered', len(programs)), ('normalizationPreprocessingUsable', sum(p['preprocessedProduced'] for p in programs)),
                   ('spProduced', sum(reached(p, 'frontend') for p in programs)), ('airProduced', sum(reached(p, 'lower') for p in programs)),
                   ('cfgProduced', sum(reached(p, 'cfg') for p in programs)), ('dependencyProduced', len(completed))]
    funnel, previous = [], len(programs)
    for name, count in populations:
        funnel.append({'stage': name, 'programs': count, 'ofPrevious': ratio(count, previous), 'ofCorpus': ratio(count, len(programs))})
        previous = count
    sites = [s for p in programs for s in p['callSites']]
    reachable = [s for s in sites if s['reachability'] == 'REACHABLE']
    inventory = [p for p in programs if p['statements'] is not None]
    totals = {k: sum(p['statements'][k] for p in inventory) for k in
              ('totalStatements', 'modeledStatements', 'partialStatements', 'unsupportedStatements', 'inputMissingStatements')}
    families = defaultdict(Counter)
    for p in inventory:
        for name, counts in p['statements']['byFamily'].items(): families[name].update(counts['coverage'])
    stage_timing = {}
    for stage in stages:
        stats = timing_stats([(p['path'], p['stages'][stage]['elapsedMs']) for p in programs if p['stages'][stage]['elapsedMs'] is not None])
        stage_timing[stage] = {'executions': stats['count'], **stats}
    measured_ms = sum(s['totalMs'] for s in stage_timing.values())
    boundary = defaultdict(lambda: {'occurrences': 0, 'programs': set(), 'sites': set()})
    for p in programs:
        for u in p['callBoundaryUncertainties']:
            b = boundary[u['code']]; b['occurrences'] += 1; b['programs'].add(p['path'])
            b['sites'].update((p['path'], key(op)) for op in u['directCallSites'])
    blockers = []
    for p in programs:
        for stage, outcome in p['stages'].items():
            if outcome['state'] in ('BLOCKED', 'FAILED', 'TIMEOUT'):
                entries = p.get('entryInventory', {}).get('entries', [])
                blockers.append({'program': p['path'], 'stage': stage, 'state': outcome['state'],
                                 'reasonCategory': outcome['reasonCategory'], 'reasonCode': outcome['reasonCode'],
                                 'observedSPCallsNotAnalyzed': len(p['sourceCallInventory']),
                                 'entryAvailability': [e['availability'] for e in entries],
                                 'entryGapCodes': sorted({g['code'] for e in entries for g in e['gaps']}),
                                 'missingDependencies': sorted({g['requestedDependency'] for g in p['gaps'] if g.get('requestedDependency')})})
    return {'pipelineFunnel': funnel, 'stageStates': {s: dict(Counter(p['stages'][s]['state'] for p in programs)) for s in stages},
            'pipelineBlockers': blockers,
            'statements': {**totals, 'programsWithSP': len(inventory),
                           'byFamily': {f: {'total': sum(c.values()), 'coverage': dict(c)} for f, c in sorted(families.items())}},
            'gaps': gap_aggregates(programs), 'callImpactDimensions': impact_dimensions(programs),
            'callBoundaryUncertainties': [{'gapCode': k, 'occurrences': v['occurrences'], 'programsAffected': len(v['programs']),
                                           'directlyAffectedCallSites': len(v['sites'])} for k, v in sorted(boundary.items())],
            'callCoverage': {'programsWithSP': len(inventory), 'programsWithDependencyResult': len(completed),
                             'sitesObservedInSP': sum(len(p['sourceCallInventory']) for p in programs),
                             'sitesInDependencyResults': len(sites), 'analyzedReachableSites': len(reachable),
                             'categories': {c: ratio(sum(s['classification'] == c for s in sites), len(sites)) for c in CLASSES},
                             'closedAmongAnalyzedReachable': ratio(sum(s['classification'] == 'CLOSED_RESOLVED' for s in reachable), len(reachable)),
                             'knownCandidatesAmongAnalyzedReachable': ratio(sum(bool(s['candidates']) for s in reachable), len(reachable)),
                             'modelClosedWithKnownCandidates': ratio(sum(bool(s['candidates']) and s['modelValueRemainder'] is False for s in reachable), len(reachable)),
                             'remaindersAmongAnalyzedReachable': {r: ratio(sum(s[r] is True for s in reachable), len(reachable)) for r in
                                 ('modelValueRemainder', 'sourceValueRemainder', 'interpretationUnknownRemainder', 'effectiveUnknownRemainder', 'openControlRemainder')}},
            'knownDependencies': known_dependencies(sites),
            'knownReferenceNames': sorted({c['referenceName'] for s in reachable for c in s['candidates']}),
            'timing': {**timings, 'allAttempted': timing_stats([(p['path'], p['timings']['programElapsedMs']) for p in programs]),
                       'completedDependencyPipeline': timing_stats([(p['path'], p['timings']['programElapsedMs']) for p in completed]),
                       'byStage': stage_timing, 'sumMeasuredStageMs': measured_ms,
                       'runnerOverheadMs': timings['corpusElapsedMs'] - measured_ms,
                       'overheadScope': 'loop, filesystem, dependency root discovery, JSON parsing/validation and per-program measurement export; setup/discovery/report are outside corpus time'},
            'exploratorySizeTiming': [{'program': p['path'], 'sourceBytes': p['sourceBytes'], 'sourceLines': p['sourceLines'],
                                       'statements': p['statements']['totalStatements'] if p['statements'] else None,
                                       'observedSPCalls': len(p['sourceCallInventory']) if p['statements'] else None,
                                       'programElapsedMs': p['timings']['programElapsedMs']} for p in programs]}


def validate_baseline(value):
    from carddemo_baseline import STATES, CATEGORIES
    if value['schemaVersion'] != 'carddemo-full-baseline-1.0.0': raise ValueError('baseline schema version')
    corpus, programs = value['corpus'], value['programs']
    validate_enumeration(corpus['discoveredSources'], programs)
    if corpus['programsDiscovered'] != len(programs) or corpus['programsAttempted'] != len(programs):
        raise ValueError('corpus denominator mismatch')
    validate_advisory(value['advisory'])
    for program in programs:
        if set(program['stages']) != {'frontend', 'lower', 'cfg', 'dependency'}: raise ValueError('stage set')
        for stage in program['stages'].values():
            if stage['state'] not in STATES: raise ValueError('stage state')
            if stage['reasonCategory'] is not None and stage['reasonCategory'] not in CATEGORIES: raise ValueError('reason category')
            if stage['state'] != 'NOT_REACHED' and not isinstance(stage['elapsedMs'], (int, float)): raise ValueError('missing timing')
            if stage['elapsedMs'] is not None and stage['elapsedMs'] < 0: raise ValueError('negative timing')
        if program['timings']['programElapsedMs'] < 0: raise ValueError('negative program time')
        for gap in program['gaps']:
            provenance = gap['provenance']
            if isinstance(provenance, dict) and 'ref' in provenance and provenance['ref'] not in program['provenanceCatalog']:
                raise ValueError('unresolved provenance reference')
            if 'ref' in gap['impact'] and gap['impact']['ref'] not in program['impactCatalog']:
                raise ValueError('unresolved impact reference')
    if value['aggregate'] != aggregate(programs, value['timings']): raise ValueError('aggregate differs from per-program evidence')
    return value


def build_report_data(raw_path):
    raw = json.loads(raw_path.read_text())
    root = raw_path.parent
    # Verify every product digest before deriving metrics; raw measurement bytes remain untouched.
    import hashlib
    for p in raw['programs']:
        for artifact in p['artifacts'].values():
            if hashlib.sha256((root / artifact['path']).read_bytes()).hexdigest() != artifact['sha256']:
                raise ValueError('raw product changed: ' + artifact['path'])
    programs = enrich(raw, root)
    result = {**{k: v for k, v in raw.items() if k not in ('schemaVersion', 'programs')},
              'schemaVersion': 'carddemo-full-baseline-1.0.0', 'programs': programs,
              'comparisonKey': {'upstreamRepository': raw['snapshot']['upstream']['repository'],
                                'upstreamCommit': raw['snapshot']['upstream']['commit'],
                                'programIdentity': 'upstream-relative source path + source SHA256',
                                'sourceCallIdentity': 'program path + SP statement + ProgramPoint; keep full AIR site identity within each run',
                                'deltaFields': ['programsReachingSP', 'programsReachingDependency', 'closedSites', 'partialSites', 'openSites', 'corpusElapsedMs', 'meanProgramMs'],
                                'performanceDeltaIsAutomaticGate': False},
              'interpretation': {'statementFamilyAuthority': 'SP typed/observed facts only',
                                  'genericObservedKindsAreNotExpandedBySourceScanning': True,
                                  'preprocessingUsable': 'normalized/preprocessed artifact delivered to frontend; may contain explicit unresolved COPY gaps',
                                  'callClosure': 'conservative: known candidate, false model remainder, no other remainder or unclassified uncertainty reference',
                                  'potentialImpact': 'unit control envelope contains site with open-control remainder; no source-text proximity or individual causal claim',
                                  'knownDependencies': 'known program reference candidates with supports; not proven runtime linkage or a complete call graph',
                                  'timing': 'OBSERVED BASELINE TIMING; single sequential run includes process startup/JVM behavior; no SLA or scientific benchmark'}}
    result['aggregate'] = aggregate(programs, result['timings'])
    result['advisory'] = advisory(result['aggregate']['callImpactDimensions'])
    result['advisory']['observations'] = advisory_observations(result['aggregate'])
    validate_baseline(result)
    return result


def intern_provenance(result):
    """Local lossless dictionaries keep the full baseline reviewable without repeated provenance."""
    for program in result['programs']:
        catalog, lookup = {}, {}
        def intern(value):
            if value is None: return None
            encoded = key(value)
            if encoded not in lookup:
                ident = 'p' + str(len(lookup)); lookup[encoded] = ident; catalog[ident] = value
            return {'ref': lookup[encoded]}
        for gap in program['gaps']: gap['provenance'] = intern(gap['provenance'])
        if program['statements']:
            for statement in program['statements']['inventory']: statement['provenance'] = intern(statement['provenance'])
        program['provenanceCatalog'] = catalog
        impact_catalog, impact_lookup = {}, {}
        for gap in program['gaps']:
            impact = gap['impact']
            encoded = key(impact)
            if encoded not in impact_lookup:
                ident = 'i' + str(len(impact_lookup)); impact_lookup[encoded] = ident; impact_catalog[ident] = impact
            gap['impact'] = {'ref': impact_lookup[encoded]}
        program['impactCatalog'] = impact_catalog
    result['encoding'] = {'provenance': 'gap/statement provenance.ref resolves in that program.provenanceCatalog',
                          'impact': 'gap.impact.ref resolves in that program.impactCatalog (attribution, sites and evidence)',
                          'lossless': True}
    # Counts are unchanged by lossless interning; only identity equality is used for deduplication.
    validate_baseline(result)
    return result
