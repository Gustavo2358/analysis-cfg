#!/usr/bin/env python3
"""Audit frozen W1 A/B outputs, not a solver or a golden generator.

Every differing output field is retained with its old/new values and a cause label.
Site matching uses written source spans/include chains plus caller/entry identity.
Timing, raw/canonical candidates and full supports are checked independently.
Origin matching is NOT proof of equal transfer semantics: changed producer bodies
are retained in supportMappings and labelled projection changes for human review.
Unexplained changes and one-sided failures cause exit 1; neither is called PASS.
"""
import argparse
import collections
import copy
import hashlib
import json
from pathlib import Path


def enc(value):
    return json.dumps(value, sort_keys=True, separators=(',', ':'), ensure_ascii=False)


def load(path):
    return json.loads(path.read_text())


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def walk(value):
    if isinstance(value, dict):
        yield value
        for child in value.values():
            yield from walk(child)
    elif isinstance(value, list):
        for child in value:
            yield from walk(child)


def local(value):
    """Publication IDs may change with the producer projection; keep all other IDs."""
    if isinstance(value, dict):
        return {k: local(v) for k, v in value.items() if k != 'publication'}
    if isinstance(value, list):
        return [local(v) for v in value]
    return value


class View:
    def __init__(self, directory):
        self.directory = directory
        self.output = load(directory / 'dependencies.json')
        self.air = load(directory / 'air.json')['publication']
        self.sp = load(directory / 'sp/cobol-semantic-product.json')
        self.origins = {o['id']['localId']: o for o in self.output['origins']}
        self.artifacts = {a['id']['localId']: a for a in self.output['artifacts']}
        self.nodes = {}
        for node in walk(self.air):
            header = node.get('header', node)
            ref = header.get('id')
            if isinstance(ref, dict) and ref.get('domain'):
                self.nodes.setdefault((ref['domain'], ref.get('unit'), ref['localId']), node)
        self.premises = {p['id']['localId']: p for p in self.air['premises']}
        self.validate_edges()

    def validate_edges(self):
        for section in (self.output, self.output.get('fileDependencies', {})):
            expected = collections.Counter(enc((s['operation'], s['entry'], c))
                                           for s in section.get('sites', []) for c in s['candidates'])
            edges = collections.Counter(enc((e['site'], e['entry'], e['candidate'])) for e in section.get('edges', []))
            if expected != edges:
                raise ValueError('edge candidate/support inventory disagrees with its site facts')

    def source(self, ref, active=()):
        if ref is None:
            return []
        ident = ref['localId']
        if ident in active:
            raise ValueError('cyclic origin ' + ident)
        o = self.origins.get(ident)
        if o is None:
            raise ValueError('missing origin ' + ident)
        if o['kind'] == 'DERIVED':
            leaves = [leaf for r in o['inputs'] for leaf in self.source(r, active + (ident,))]
            return [json.loads(s) for s in sorted(set(map(enc, leaves)))]
        leaf = {k: local(v) for k, v in o.items() if k not in ('id', 'artifact')}
        if 'artifact' in o:
            a = self.artifacts[o['artifact']['localId']]
            leaf['artifact'] = {k: v for k, v in a.items() if k != 'id'}
        return [leaf]

    def node(self, ref):
        if ref is None:
            return None
        return self.nodes.get((ref['domain'], ref.get('unit'), ref['localId']))

    def witness(self, support):
        node = self.node(support.get('producer'))
        if support.get('producer') is not None and node is None:
            raise ValueError('missing producer AIR node ' + enc(support['producer']))
        semantic_premises = []
        for ref in support.get('premises', []):
            premise = self.premises.get(ref['localId'])
            if premise is None:
                raise ValueError('missing premise ' + enc(ref))
            if premise.get('assertion', {}).get('kind') != 'disjoint_storage':
                semantic_premises.append(local(premise))
        origin = node.get('header', node).get('origin') if node else None
        return {'kind': support['kind'], 'source': self.source(support['origin']),
                'producerSource': self.source(origin) if origin else [],
                'semanticPremises': sorted(semantic_premises, key=enc),
                'extraFields': {k: local(v) for k, v in support.items()
                                if k not in ('producer', 'origin', 'premises', 'kind')}}

    def site_key(self, site):
        return enc({'caller': local(site['caller']), 'entry': local(site['entry']),
                    'command': site['command'], 'targetKind': site['targetKind'],
                    'siteSource': self.source(site['siteOrigin'])})

    def separation_only(self, refs):
        return bool(refs) and all(self.premises.get(r['localId'], {}).get('assertion', {}).get('kind') == 'disjoint_storage' for r in refs)

    def mechanism_evidence(self):
        operations = [o for u in self.air['units'] for s in u['sequences']
                      for o in s['instructions'] + [s['terminator']]]
        return {'logicalViews': self.logical_views(), 'storageKinds': dict(collections.Counter(s['kind'] for s in self.air['storage'])),
                'operationKinds': dict(collections.Counter(o['kind'] for o in operations)),
                'invocations': [{'id':o['header']['id'], 'source':self.source(o['header']['origin']),
                                 'effectBound':o.get('effectBound'), 'outcomes':o.get('outcomes'),
                                 'targetNamePolicy':o.get('target',{}).get('namePolicy')}
                                for o in operations if o['kind']=='invoke']}

    def logical_views(self):
        return len(self.sp['storage'].get('logicalTextViews', []))


def candidate_names(site, field):
    return sorted(enc({k: v for k, v in c.items() if k != 'supports'}) for c in site[field])


def support_witnesses(view, site, field):
    return sorted(enc({'candidate': {k: v for k, v in c.items() if k != 'supports'},
                       'supports': sorted((view.witness(s) for s in c['supports']), key=enc)})
                  for c in site[field])


def compare_case(a, b):
    deltas = []
    def add(path, before, after, category, cause):
        if before != after:
            deltas.append({'path': path, 'category': category, 'cause': cause, 'before': before, 'after': after})
    sites_a = {a.site_key(s): s for s in a.output['sites']}
    sites_b = {b.site_key(s): s for s in b.output['sites']}
    if len(sites_a) != len(a.output['sites']) or len(sites_b) != len(b.output['sites']):
        raise ValueError('ambiguous source site mapping; no ordinal matching allowed')
    mappings = []
    changed_openness = set()
    projection = a.logical_views() < b.logical_views()
    for anchor in sorted(sites_a.keys() | sites_b.keys()):
        old, new = sites_a.get(anchor), sites_b.get(anchor)
        path = '/sites/source=' + anchor
        if old is None or new is None:
            add(path, old, new, 'UNEXPLAINED', 'site inventory changed; requires explicit reachability/product oracle')
            continue
        for key in ('sourceValueRemainder', 'modelValueRemainder', 'effectiveUnknownRemainder'):
            if key not in old or key not in new:
                raise ValueError('missing required wire field ' + key)
        names = all(candidate_names(old, f) == candidate_names(new, f) for f in ('candidates', 'rawCandidates'))
        timing = local(old['valuePoint']) == local(new['valuePoint'])
        witnesses = all(support_witnesses(a, old, f) == support_witnesses(b, new, f)
                        for f in ('candidates', 'rawCandidates'))
        mappings.append({'sourceAnchor': json.loads(anchor), 'oldOperation': old['operation'], 'newOperation': new['operation'],
                         'candidateValuesPreserved': names, 'timingPreserved': timing,
                         'sourceSupportWitnessesPreserved': witnesses,
                         'oldSupports': [{'support': s, 'witness': a.witness(s), 'producerAir': a.node(s.get('producer'))}
                                         for c in old['candidates'] for s in c['supports']],
                         'newSupports': [{'support': s, 'witness': b.witness(s), 'producerAir': b.node(s.get('producer'))}
                                         for c in new['candidates'] for s in c['supports']]})
        if any(old.get(f) != new.get(f) for f in ('effectiveUnknownRemainder', 'openControlRemainder', 'analysisStatus', 'reachability')):
            changed_openness.add(enc(local([old['operation'], old['entry']])))
        for field in sorted(old.keys() | new.keys()):
            x, y = old.get(field), new.get(field)
            category, cause = 'UNEXPLAINED', 'no demonstrated W1 rule for this difference'
            if field in ('uncertaintyRefs', 'sourceValueRemainder'):
                category, cause = 'DIAGNOSTIC_CONTRACT', 'source coverage inventory remains separate from executable evidence'
            elif local(x) == local(y):
                category, cause = 'IDENTITY_REPUBLICATION', 'same scoped semantic IDs; publication identity changed'
            elif field == 'premises' and not y and a.separation_only(x):
                category, cause = 'REMOVED_COMPENSATION', 'positive bases no longer require redundant DisjointStorage proof'
            elif field == 'openControlRemainder' and x is True and y is False and names and timing:
                category, cause = 'REMOVED_COMPENSATION', 'source coverage/omitted CALL effects no longer open modeled control; candidate and timing witness retained'
            elif field == 'modelValueRemainder' and x is True and y is False and names and witnesses and timing:
                category, cause = 'SUPPORTED_PROJECTION_GAIN' if projection else 'REMOVED_COMPENSATION', 'known values and source supports retained; represented topology or omitted foreign effects no longer add unknown content'
            elif field == 'effectiveUnknownRemainder' and x is True and y is False and new['modelValueRemainder'] is False:
                category, cause = 'REMOVED_COMPENSATION', 'effective semantic openness excludes diagnostic source coverage'
            elif field in ('analysisStatus', 'analysisReasons') and projection and names and witnesses and timing:
                category, cause = 'SUPPORTED_PROJECTION_GAIN', 'new logical views admit known textual transfer without requiring physical execution'
            elif field in ('candidates', 'rawCandidates', 'evidence', 'subject', 'targetOrigin', 'provenance') and projection and names and witnesses and timing:
                category, cause = 'SUPPORT_REPRESENTATION_CHANGE', 'same written source witnesses and BEFORE query; new root/member projection replaces legacy nominal producer (full AIR bodies retained for review)'
            elif field == 'provenance' and {enc(z) for r in (y or []) for z in b.source(r)} <= {enc(z) for r in (x or []) for z in a.source(r)} and names and witnesses:
                category, cause = 'DIAGNOSTIC_CONTRACT', 'no new source support; obsolete premise/coverage provenance removed (exact IDs retained in ledger)'
            add(path + '/' + field, x, y, category, cause)
    # Compare every other top-level field. Whole FILE/source records are retained,
    # including access/operation, qualification, occurrence, resolution and supports.
    for field in sorted(a.output.keys() | b.output.keys()):
        if field == 'sites':
            continue
        x, y = a.output.get(field), b.output.get(field)
        if field in ('fileDependencies', 'sourceDependencies') and isinstance(x,dict) and isinstance(y,dict):
            for child in sorted(x.keys() | y.keys()):
                add('/'+field+'/'+child,x.get(child),y.get(child),
                    'COST_CHANGE' if child=='metrics' else 'UNEXPLAINED',
                    'resource work counters only' if child=='metrics' else 'full resource record changed: inspect declaration/action/access/qualification/occurrence/support/timing')
            continue
        category, cause = 'UNEXPLAINED', 'unclassified output field change'
        if field == 'metrics':
            category, cause = 'COST_CHANGE', 'reported work counters; not a semantic equivalence claim'
        elif field in ('origins', 'artifacts', 'sourceUncertaintyRefs'):
            category, cause = 'DIAGNOSTIC_CONTRACT', 'publication support/coverage inventory; reachable support associations checked independently above'
        elif field == 'publication':
            category, cause = 'IDENTITY_REPUBLICATION', 'publication hash changes with the supported projection'
        elif field == 'edges' and all(m['candidateValuesPreserved'] and m['timingPreserved'] and m['sourceSupportWitnessesPreserved'] for m in mappings):
            # Edges contain duplicated candidate/support data; no unexamined field may change.
            def edge_relation(e):
                ignore = {'candidate'}
                if enc(local([e['site'], e['entry']])) in changed_openness:
                    ignore.add('openSite')
                return local({k:v for k,v in e.items() if k not in ignore})
            left = [edge_relation(e) for e in x]
            right = [edge_relation(e) for e in y]
            if left == right and len(x) == len(y):
                category, cause = 'SUPPORT_REPRESENTATION_CHANGE', 'edge inventory/site relation preserved; candidate support/open-site changes audited at corresponding sites'
        elif field in ('analysisStatus','analysisReasons') and projection and all(m['candidateValuesPreserved'] and m['timingPreserved'] and m['sourceSupportWitnessesPreserved'] for m in mappings):
            category, cause = 'SUPPORTED_PROJECTION_GAIN', 'global status reflects admitted supported logical views'
        elif field in ('fileDependencies','sourceDependencies') and x == y:
            category, cause = 'PRESERVED', 'full records equal'
        add('/' + field, x, y, category, cause)
    unknown = [d['path'] for d in deltas if d['category'] == 'UNEXPLAINED']
    return {'status': 'REQUIRES_REVIEW' if unknown else 'CLASSIFIED_DELTA' if deltas else 'PRESERVED',
            'unknownPaths': unknown, 'deltas': deltas, 'supportMappings': mappings,
            'fileDependenciesExact': a.output.get('fileDependencies') == b.output.get('fileDependencies'),
            'sourceDependenciesExact': a.output.get('sourceDependencies') == b.output.get('sourceDependencies'),
            'fileDependenciesSemanticPreserved': {k:v for k,v in a.output.get('fileDependencies',{}).items() if k!='metrics'} == {k:v for k,v in b.output.get('fileDependencies',{}).items() if k!='metrics'},
            'oldLogicalViews': a.logical_views(), 'newLogicalViews': b.logical_views(),
            'airMechanismsBefore': a.mechanism_evidence(), 'airMechanismsAfter': b.mechanism_evidence()}


def compare(before, after):
    rows = []
    a_results = {r['case']:r for r in load(before/'results.json')}
    b_results = {r['case']:r for r in load(after/'results.json')}
    for case in sorted(a_results.keys() | b_results.keys()):
        a, b = before/case, after/case
        paths = [a/'dependencies.json', b/'dependencies.json']
        if not all(p.exists() for p in paths):
            failed = lambda row: {s:p['exitCode'] for s,p in row.get('phases',{}).items() if p['exitCode'] != 0}
            left, right = failed(a_results.get(case,{})), failed(b_results.get(case,{}))
            rows.append({'case':case, 'status':'FAILURE_BOTH' if left and right else 'PIPELINE_DELTA',
                         'beforeFailure':left, 'afterFailure':right, 'cause':'failed executions retained; no supported-output equivalence claim',
                         'logHashesBefore':{p.name:digest(p) for p in a.glob('*.log')},
                         'logHashesAfter':{p.name:digest(p) for p in b.glob('*.log')}})
            continue
        try:
            result = compare_case(View(a), View(b))
        except (ValueError,KeyError,TypeError) as error:
            result = {'status':'MAPPING_FAILURE','error':str(error)}
        result.update(case=case, hashesBefore={p:digest(a/p) for p in ('dependencies.json','air.json','sp/cobol-semantic-product.json')},
                      hashesAfter={p:digest(b/p) for p in ('dependencies.json','air.json','sp/cobol-semantic-product.json')})
        rows.append(result)
    return {'before':str(before), 'after':str(after), 'policy':'classification is review evidence, not automatic approval or full language equivalence',
            'counts':dict(collections.Counter(r['status'] for r in rows)),
            'deltaCategories':dict(collections.Counter(d['category'] for r in rows for d in r.get('deltas',[]))), 'cases':rows}


def self_check(directory):
    """In-memory mutants of synthetic outputs; never rewrites frozen evidence."""
    call = View(directory / 'logical--w1-a')
    resource = View(directory / 'source-dependencies-w3-db2--db2-composition')
    results = []

    def check(name, base, mutate, expected='REQUIRES_REVIEW'):
        changed = copy.deepcopy(base)
        mutate(changed)
        try:
            changed.validate_edges()
            result = compare_case(base, changed)
        except (ValueError, KeyError, TypeError) as error:
            result = {'status': 'MAPPING_FAILURE', 'error': str(error)}
        results.append({'mutant': name, 'expected': expected, 'observed': result['status'],
                        'refuted': result['status'] == expected,
                        'unknownPaths': result.get('unknownPaths', []), 'error': result.get('error')})

    def all_call_candidates(view):
        return ([c for site in view.output['sites'] for field in ('candidates', 'rawCandidates')
                 for c in site[field]] + [edge['candidate'] for edge in view.output['edges']])

    def candidate_change(view):
        for candidate in all_call_candidates(view):
            candidate['rawValue'] = 'MUTATED '
            if 'referenceName' in candidate:
                candidate['referenceName'] = 'MUTATED'

    def support_loss(view):
        for candidate in all_call_candidates(view):
            candidate['supports'] = []

    def missing_producer(view):
        for candidate in all_call_candidates(view):
            candidate['supports'][0]['producer']['localId'] = 'missing-producer-mutant'

    check('unchanged complete synthetic output', call, lambda view: None, 'PRESERVED')
    check('candidate change consistently duplicated in edges', call, candidate_change)
    check('support loss consistently duplicated in edges', call, support_loss)
    check('raw candidate support loss alone', call,
          lambda view: view.output['sites'][0]['rawCandidates'][0].update(supports=[]))
    check('BEFORE becomes AFTER', call,
          lambda view: view.output['sites'][0]['valuePoint'].update(position='AFTER'))
    check('missing producer identity', call, missing_producer, 'MAPPING_FAILURE')
    check('edge-only support loss', call,
          lambda view: view.output['edges'][0]['candidate'].update(supports=[]), 'MAPPING_FAILURE')
    check('edge-only openness change', call,
          lambda view: view.output['edges'][0].update(openSite=not view.output['edges'][0]['openSite']))
    check('FILE declaration loss', resource,
          lambda view: view.output['fileDependencies']['declarations'].pop())
    check('source resource access change', resource,
          lambda view: view.output['sourceDependencies']['dependencies'][0]['supports'][0].update(access='WRITE'))
    return results


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--before',required=True,type=Path);p.add_argument('--after',required=True,type=Path);p.add_argument('--out',required=True,type=Path)
    p.add_argument('--self-check', action='store_true', help='also refute in-memory synthetic candidate/support/timing/resource mutants')
    args = p.parse_args();result = compare(args.before.resolve(),args.after.resolve())
    if args.self_check:
        result['classifierSelfChecks'] = self_check(args.after.resolve())
    args.out.write_text(json.dumps(result,indent=2,ensure_ascii=False)+'\n')
    print(json.dumps({'counts':result['counts'],'deltaCategories':result['deltaCategories']},sort_keys=True))
    return int(any(not check['refuted'] for check in result.get('classifierSelfChecks', [])) or any(r['status'] in ('REQUIRES_REVIEW','MAPPING_FAILURE','PIPELINE_DELTA') for r in result['cases']))

if __name__ == '__main__':
    raise SystemExit(main())
