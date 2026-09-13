#!/usr/bin/env python3
"""Generate a historical baseline from preserved measurements; never rerun the corpus."""
import argparse
from collections import Counter
import json
from pathlib import Path
import time

from carddemo_metrics import POLICY, build_report_data, intern_provenance, validate_baseline


def seconds(ms):
    return '—' if ms is None else f'{ms / 1000:.3f}s'


def source_label(path):
    return ('ZIP: ' if '!/' in path else 'checkout: ') + Path(path).name


def proportion(value):
    n, d, p = value['numerator'], value['denominator'], value['percent']
    return f'{n}/{d}' + (f' ({p:.1f}%)' if p is not None else ' (not applicable)')


def markdown(data):
    a, corpus = data['aggregate'], data['corpus']
    coverage, timing = a['callCoverage'], a['timing']
    entry = [b for b in a['pipelineBlockers'] if b['reasonCode'] == 'ENTRY_START_UNAVAILABLE']
    missing_entry = [b for b in entry if 'INPUT_MISSING' in b['entryAvailability']]
    other_entry = [b for b in entry if b not in missing_entry]
    no_sp = sum(p['statements'] is None for p in data['programs'])
    archived = sum(bool(p.get('archive')) for p in data['programs'])
    missing_programs = sum(any(g.get('requestedDependency') for g in p['gaps']) for p in data['programs'])
    shapes = Counter(s.get('observedShape') for p in data['programs'] if p['statements'] for s in p['statements']['inventory'])
    collisions = {c['name'] for p in data['programs'] for c in p['copybookCollisions']}
    source_groups = Counter('ZIP members' if p.get('archive') else 'checkout files' for p in data['programs'])
    sites = [s for p in data['programs'] for s in p['callSites']]
    missing = Counter(g['requestedDependency'] for p in data['programs'] for g in p['gaps'] if g.get('requestedDependency'))
    lines = ['# Full CardDemo baseline', '', '**IMPLEMENTED / AWAITING HUMAN REVIEW.** Historical, observational evaluation; no product capability or performance change.', '',
             '## Corpus', '',
             f"Universe: [`{data['snapshot']['upstream']['repository']}`](https://github.com/{data['snapshot']['upstream']['repository']}/tree/{data['snapshot']['upstream']['commit']}) at `{data['snapshot']['upstream']['commit']}`.",
             f"**{corpus['programsDiscovered']} discovered; {corpus['programsAttempted']} attempted; every source path represented exactly once.** Recursive `.cbl`/`.cob`/`.cl2`, case insensitive, including ZIP members; no whitelist, source edits, dependency stubs or excluded failures.", '',
             f"Discovery includes **{source_groups['checkout files']} checkout files and {archived} archived source variants**. `.cl2` is included because the UniKix `migrated_app/cbl` members contain COBOL IDENTIFICATION DIVISION/PROGRAM-ID alongside `.cbl` programs. Archive paths use `archive.zip!/member`. AppleDouble resource-fork metadata is excluded only with its binary magic verified; every exclusion is recorded. `.cpy`, `.dcl` and extensionless library files are dependencies; BMS, assembler, JCL, runtime binaries, compiler listings, data and marker files are not standalone COBOL programs. No source duplicate was excluded.", '',
             f"All dependency roots in the checkout and ZIP are available, including DCLGENs and extensionless archived copybooks. Root order prefers the longest shared directory prefix, then lexical order: native programs prefer native roots; archive variants prefer their own roots. **{len(collisions)} dependency-name collisions** are recorded with ordered paths and equal/different-content flags. Sources are never rewritten to select a dependency.", '',
             'The selected ten programs in `proleap-poc/corpus/carddemo` remain the **small pinned regression/sentinel corpus**. This full temporary checkout is the **exploratory product baseline**; it is not vendored.', '',
             'Fetch completed; clean `main == origin/main` and the real merge parents were verified before the evaluation branch. Isolated builds used these exact commits throughout:', '',
             '| Product | Commit |', '| --- | --- |']
    lines += [f'| {name} | `{sha}` |' for name, sha in data['snapshot']['analysisRepositories'].items()]
    lines += ['', '## Pipeline coverage', '',
              '| Stage/output | Programs | Previous stage | Corpus |', '| --- | ---: | ---: | ---: |']
    lines += [f"| {f['stage']} | {f['programs']} | {proportion(f['ofPrevious'])} | {proportion(f['ofCorpus'])} |" for f in a['pipelineFunnel']]
    lines += ['', '`normalizationPreprocessingUsable` means a normalized/preprocessed artifact was delivered to the frontend, with input gaps retained. It does not assert that all COPYs resolved. Frontend timing combines normalization, preprocessing, parsing, SP and the current CLI exports.', '',
              'All produced SP/AIR/CFG/dependency stages in this run are **PARTIAL**, not complete semantic coverage. Failed/blocked processes retain their measured time; subsequent stages are NOT_REACHED. No timeout or resource-limit event occurred.', '',
              '| Actual stopping condition | Programs | Observed SP CALLs behind it |', '| --- | ---: | ---: |']
    buckets = {}
    for b in a['pipelineBlockers']:
        bucket = buckets.setdefault(b['reasonCode'], [0, 0]); bucket[0] += 1; bucket[1] += b['observedSPCallsNotAnalyzed']
    lines += [f'| {name} | {v[0]} | {v[1] if name == "ENTRY_START_UNAVAILABLE" else "unknown: no SP"} |' for name, v in sorted(buckets.items())]
    lines += ['', f"The {len(entry)} ENTRY_START refusals split into **{len(missing_entry)} INPUT_MISSING entries / {sum(b['observedSPCallsNotAnalyzed'] for b in missing_entry)} observed CALLs**, and **{len(entry)-len(missing_entry)} unavailable starts without INPUT_MISSING / {sum(b['observedSPCallsNotAnalyzed'] for b in entry if b not in missing_entry)} observed CALLs**. Sources without INPUT_MISSING: {', '.join(b['program'] for b in other_entry)}. SP publishes `EXECUTABLE_START_NOT_AVAILABLE`; the lower requires an explicit start. No entry is invented.", '',
              'Missing dependencies, as reported by preprocessing: ' + ', '.join(f'`{name}` ({n} occurrences)' for name, n in sorted(missing.items())) + f'. These gaps occur in {missing_programs} programs. Explicit incomplete-entry input is counted separately above. These are input/dependency gaps, not statement capability gaps.', '',
              '## CALL coverage', '',
              f"**{coverage['sitesObservedInSP']} typed CALLs among {coverage['programsWithSP']} SP-producing programs; {coverage['sitesInDependencyResults']} dependency sites among {coverage['programsWithDependencyResult']} completed pipelines.** The remaining observed CALLs have no dependency result and are not classified as unresolved sites. CALLs in the {no_sp} no-SP programs are unknown.", '',
              '| Category | Analyzed dependency sites |', '| --- | ---: |']
    lines += [f'| {name} | {proportion(value)} |' for name, value in coverage['categories'].items()]
    lines += ['', f"Closed resolution among analyzed reachable sites: **{proportion(coverage['closedAmongAnalyzedReachable'])}**. Known candidates with closed model-value reasoning: **{proportion(coverage['modelClosedWithKnownCandidates'])}**. Analyzed target kinds: {dict(Counter(s['targetKind'] for s in sites))}. Remainders remain independent; an open/unknown site is never promoted to CLOSED.", '',
              f"Known dependency projection: **{len(a['knownDependencies'])} distinct caller-name → reference-name pairs**, with site associations and supports retained; **{len(a['knownReferenceNames'])} distinct reference names**: " + ', '.join('`' + n + '`' for n in a['knownReferenceNames']) + '. These are supported known name candidates, not certified runtime linkage or a complete call graph.', '',
              '| Caller | Known reference names |', '| --- | --- |']
    callers = {}
    for pair in a['knownDependencies']: callers.setdefault(pair['callerName'], []).append(pair['referenceName'])
    lines += [f"| {caller} | {', '.join(targets)} |" for caller, targets in callers.items()]
    s = a['statements']
    lines += ['', '## Unsupported/gap distribution', '',
              f"Authority: SP typed/observed facts in **{s['programsWithSP']} programs**. Statements: **{s['totalStatements']} total; {s['modeledStatements']} modeled; {s['partialStatements']} partial; {s['unsupportedStatements']} unsupported; {s['inputMissingStatements']} input-missing**. Zero input-missing statements does not mean zero input gaps: the entry inventory and preprocessing separately expose missing dependencies.", '',
              '| Observed family | Statements | Modeled | Partial | Unsupported |', '| --- | ---: | ---: | ---: | ---: |']
    for name, c in s['byFamily'].items():
        lines.append(f"| {name} | {c['total']} | {c['coverage'].get('MODELED',0)} | {c['coverage'].get('PARTIAL',0)} | {c['coverage'].get('UNSUPPORTED',0)} |")
    lines += ['', 'The generic MODELED_STATEMENT/PRESERVED_STATEMENT buckets do not identify READ/WRITE/SEARCH/DISPLAY and other subfamilies. No source regex expands those buckets. Embedded-language shapes: ' + ', '.join(f'{n} {shape.removeprefix("OPAQUE_")}' for shape, n in sorted(shapes.items(), key=lambda x: str(x[0])) if shape and shape.startswith('OPAQUE_')) + '; these counts do not cover programs blocked before SP.', '',
              'Most frequent SP gap-code/family combinations (multiple gaps may describe one statement):', '',
              '| Gap code | Family | Occurrences | Programs |', '| --- | --- | ---: | ---: |']
    lines += [f"| {g['gapCode']} | {g['observedKind']} | {g['occurrences']} | {g['programsAffected']} |" for g in a['gaps'][:10]]
    lines += ['', '## CALL-site impact', '',
              'Frequency is not causality. Potential impact below means an AIR unit-label control envelope contains an analyzed site with open-control remainder. It is a conservative **region bound**, without a claim that this particular gap caused the remainder or that its implementation will close the site. Bounds overlap; columns must not be summed across rows. Unknown attribution stays explicit in JSON.', '',
              '| Capability/gap dimension | Occurrences | Programs | Direct CALLs | Potential CALLs | Partial | Open | Pipeline blockers |',
              '| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |']
    for d in a['callImpactDimensions']:
        lines.append(f"| {d['capability']} | {d['occurrences']} | {d['programsAffected']} | {d['directlyAffectedCallSites']} | {d['potentiallyAffectedCallSites']} | {d['partialResolvedCallSites']} | {d['openUnresolvedCallSites']} | {d['pipelineBlockedPrograms']} |")
    lines += ['', 'Occurrences above deduplicate gap codes on the same source statement. Pipeline blockers require a process refusal or an explicit SP entry/input gap joined to that refusal. Mere presence in a blocked program is recorded separately as `presentInBlockedPrograms`. LOW means a site stays closed; MEDIUM means known candidates survive with partiality; HIGH means open/unresolved; PIPELINE_BLOCKER means the pipeline stops. UNKNOWN is retained when there is no attributed site.', '',
              'Direct site-local AIR evidence is separate from COBOL-family frequency:', '',
              '| AIR uncertainty | Occurrences | Programs | Direct sites |', '| --- | ---: | ---: | ---: |']
    lines += [f"| {d['gapCode']} | {d['occurrences']} | {d['programsAffected']} | {d['directlyAffectedCallSites']} |" for d in a['callBoundaryUncertainties']]
    lines += ['', '## Observed timing', '', '**OBSERVED BASELINE TIMING** — one sequential measured run per source, `time.monotonic_ns()`. Timings include normal process startup/JVM behavior. A new JVM is used for each stage; no warmup, reuse or JIT correction. This is not a scientific benchmark, SLA or production throughput claim.', '',
              f"Corpus wall time: **{seconds(timing['corpusElapsedMs'])}**. Setup/build: {seconds(timing['setupElapsedMs'])}; discovery: {seconds(timing['discoveryElapsedMs'])}, both outside corpus time. Measured stages total {seconds(timing['sumMeasuredStageMs'])}; observed runner overhead {seconds(timing['runnerOverheadMs'])}. JSON parsing/validation, filesystem and per-program exports explain the expected difference. Clone/download time is outside these timers.", '',
              '| Population | n | Total program time | Mean | Median | Min | Max | p95 |', '| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |']
    for name in ('allAttempted', 'completedDependencyPipeline'):
        t = timing[name]
        lines.append(f"| {name} | {t['count']} | {seconds(t['totalMs'])} | {seconds(t['meanMs'])} | {seconds(t['medianMs'])} | {seconds(t['minMs'])} | {seconds(t['maxMs'])} | {seconds(t['p95Ms'])} |")
    for name in ('allAttempted', 'completedDependencyPipeline'):
        t = timing[name]
        lines += ['', f"{name}: fastest **{source_label(t['fastestProgram'])} — {seconds(t['fastestProgramMs'])}**; slowest **{source_label(t['slowestProgram'])} — {seconds(t['slowestProgramMs'])}**."]
    lines += ['', 'The fastest all-attempted program fails normalization. The slowest all-attempted program is blocked in lower. Neither is excluded. p95 uses nearest rank and is omitted for n < 20.', '',
              '| Stage | Executions | Total | Mean | Median | Min | Max | p95 | Slowest program |', '| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | --- |']
    for name, t in timing['byStage'].items():
        lines.append(f"| {name} | {t['executions']} | {seconds(t['totalMs'])} | {seconds(t['meanMs'])} | {seconds(t['medianMs'])} | {seconds(t['minMs'])} | {seconds(t['maxMs'])} | {seconds(t['p95Ms'])} | {source_label(t['slowestProgram'])} |")
    lines += ['', 'Frontend has the largest aggregate measured time. The dependency CLI reads AIR and constructs its own analysis context/CFG; the separate CFG stage publishes the CFG artifact. These are real process boundaries, not isolated internal algorithm costs.', '',
              '| Slowest programs | Total | Frontend | Lower | CFG | Dependency | Final stage/status |', '| --- | ---: | ---: | ---: | ---: | ---: | --- |']
    for p in sorted(data['programs'], key=lambda p: -p['timings']['programElapsedMs'])[:10]:
        final = next((s for s in reversed(p['stages']) if p['stages'][s]['state'] != 'NOT_REACHED'))
        values = ' | '.join(seconds(p['stages'][s]['elapsedMs']) for s in ('frontend','lower','cfg','dependency'))
        lines.append(f"| {source_label(p['path'])} | {seconds(p['timings']['programElapsedMs'])} | {values} | {final}/{p['stages'][final]['state']} |")
    e = data['environment']
    lines += ['', f"Environment: `{e['os']}`; Python `{e['pythonVersion'].split()[0]}`; `{e['javaAndJvmVersion'].replace(chr(10), '; ')}`; {e['availableProcessors']} available processors; CPU `{e['cpuModel']}`; memory `{e['totalMemory']}`; JVM args `{' '.join(e['jvmArguments'])}`. Source bytes/lines, SP statement/CALL counts and per-program time are paired in `aggregate.exploratorySizeTiming`; these observations do not establish a hotspot or a causal size/performance relationship.", '',
              '## Advisory capability suggestions', '', '**NON-NORMATIVE — decisionPolicy: HUMAN; rankingAuthority: ADVISORY_ONLY.**', '', POLICY, '']
    for observation in data['advisory']['observations']:
        lines.append(f"- **{observation['topic']}**: {observation['observation']} {observation['limit']}")
    lines += ['', f"The strongest measured opportunity to investigate is input/entry completeness: {len(entry)} programs and {sum(b['observedSPCallsNotAnalyzed'] for b in entry)} already-observed CALLs stop there. This is a ceiling on currently hidden analysis, not a forecast of recovered closed sites. The corpus does **not** establish a reliable causal ranking of GO TO versus EVALUATE versus READ or other semantic families. No capability is selected.", '',
              'Engineering considerations: effort, architectural risk, deadlines, demo goals, strategy and human preference were not measured and are not included in a score. A human may choose any next activity independently of frequency.', '',
              '## Corpus limitations', '',
              f"One upstream commit and one environment. The {no_sp} no-SP programs hide an unknown statement/CALL population. Embedded CICS/SQL operations are not equated with typed COBOL CALLs. Missing DFH/MQ resources stay absent; existing DCLGENs are available, while availability alone does not implement SQL preprocessing. The CLI publishes one primary program unit per source, so this evaluates every source file, not a certified count of all possible nested compilation units. Native and archived migrated variants are distinct sources; these are not unique PROGRAM-ID counts.", '',
              '## Interpretation limits', '',
              'SP counts, entry availability, lowering success, model closure and runtime dependency closure are different facts. Known literal targets do not close open runtime-name/contract policies. Potential impact measures envelope membership, not textual proximity, proven temporal reachability from the region or counterfactual causal gain. Most gap-to-site attribution is UNKNOWN; conservative bounds are deliberately broad.', '',
              'This JSON/Markdown pair is the historical baseline at the recorded source/pipeline snapshots. Do not silently overwrite it after a capability change. Produce a separately named comparison using the stable source/site keys and delta fields; timing deltas are not automatic gates.', '',
              '## Reproduction and validation', '',
              'Full corpus is **LOCAL / ON-DEMAND**. Remote CI remains **FAST ONLY** and tests runner/aggregation/schema logic with synthetic processes/data; it does not download or run CardDemo. See [runner instructions](carddemo-full-runner.md).', '',
              f"Raw logs and SP/AIR/CFG/dependency files: `{data['rawEvidenceDirectory']}`. Output hashes and commands are retained in JSON. The report can be regenerated from these measurements without rerunning a JVM. Only result artifacts and pins are versioned.", '',
              '## Per-program stage matrix', '',
              'Times in seconds; F=frontend (normalization/preprocessing included), L=lower, C=CFG, D=dependency. Every source path appears once. Full statements/gaps, source locations, CALL operations, candidate supports and remainders are in [the machine-readable baseline](carddemo-full-baseline.json).', '',
              '| Upstream source path | F | L | C | D | Total | SP statements / CALLs / analyzed CALLs |', '| --- | --- | --- | --- | --- | ---: | --- |']
    for p in data['programs']:
        stages = ' | '.join(f"{p['stages'][s]['state']} {seconds(p['stages'][s]['elapsedMs'])}" for s in ('frontend','lower','cfg','dependency'))
        counts = f"{p['statements']['totalStatements']} / {p['callMetrics']['sourceSitesObserved']} / {p['callMetrics']['dependencySitesProduced']}" if p['statements'] else 'not observed'
        lines.append(f"| {p['path']} | {stages} | {seconds(p['timings']['programElapsedMs'])} | {counts} |")
    return '\n'.join(lines) + '\n'


def write_report(raw, destination):
    if destination.with_suffix('.json').exists() or destination.with_suffix('.md').exists():
        raise ValueError('historical reports are immutable: choose a new output name')
    start = time.monotonic_ns()
    data = build_report_data(raw)
    report = markdown(data)
    intern_provenance(data)
    data['reportElapsedMs'] = (time.monotonic_ns() - start) / 1_000_000
    data['reportTimingScope'] = 'read/hash/parse/aggregate/validate/render text and lossless interning; final output writes excluded'
    validate_baseline(data)
    destination.parent.mkdir(parents=True, exist_ok=True)
    # Compact data is lossless; avoid tens of megabytes of repeated indentation in the full corpus artifact.
    destination.with_suffix('.json').write_text(json.dumps(data, ensure_ascii=False, separators=(',', ':'), allow_nan=False) + '\n')
    destination.with_suffix('.md').write_text(report)
    print('Wrote ' + str(destination) + '.{json,md}')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--measurements', type=Path, required=True)
    parser.add_argument('--output-prefix', type=Path, required=True)
    args = parser.parse_args()
    write_report(args.measurements.resolve(), args.output_prefix.resolve())
