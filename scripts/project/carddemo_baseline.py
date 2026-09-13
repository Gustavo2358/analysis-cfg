#!/usr/bin/env python3
"""One sequential, local/on-demand run of EVERY COBOL source at the pinned snapshot."""
import argparse
from collections import Counter, defaultdict
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import platform
import re
import signal
import subprocess
import sys
import time
import zipfile

from carddemo_setup import PINS, check_snapshot, require_local

STAGES = ('frontend', 'lower', 'cfg', 'dependency')
TIMING_KEYS = ('preprocessingFrontendMs', 'lowerMs', 'cfgMs', 'dependencyMs')
STATES = ('PASS', 'PARTIAL', 'BLOCKED', 'FAILED', 'TIMEOUT', 'NOT_REACHED')
CATEGORIES = ('CORPUS_INPUT_GAP', 'COPYBOOK_MISSING', 'PREPROCESSING_GAP', 'NORMALIZATION_GAP',
              'PARSER_FRONTEND_GAP', 'SEMANTIC_CAPABILITY_GAP', 'LOWERING_GAP', 'AIR_TRANSPORT_GAP',
              'CFG_ANALYSIS_GAP', 'DEPENDENCY_ANALYSIS_GAP', 'RESOURCE_LIMIT', 'TIMEOUT', 'INTERNAL_FAILURE')
SOURCE_EXTENSIONS = ('.cbl', '.cob', '.cl2')
LIBRARY_NAMES = ('cpy', 'cpy-bms', 'copybooks', 'copybook', 'dcl')


def elapsed(start):
    return (time.monotonic_ns() - start) / 1_000_000


def dump(path, value):
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2, allow_nan=False) + '\n')


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def snapshot_files(root):
    return sorted(p for p in root.rglob('*') if p.is_file() and '.git' not in p.relative_to(root).parts)


def discover(root):
    # Physical paths, including equal-content files, are all attempted. No success filter.
    result = [{'path': p.relative_to(root).as_posix(), 'sourceSha256': digest(p),
             'sourceBytes': p.stat().st_size, 'sourceLines': len(p.read_bytes().splitlines())}
            for p in snapshot_files(root) if p.suffix.lower() in SOURCE_EXTENSIONS]
    for archive in [p for p in snapshot_files(root) if p.suffix.lower() == '.zip']:
        archive_path = archive.relative_to(root).as_posix()
        with zipfile.ZipFile(archive) as z:
            names = set()
            for member in z.infolist():
                if member.filename in names: raise ValueError('ambiguous duplicate ZIP member: ' + member.filename)
                names.add(member.filename)
                if member.is_dir(): continue
                if Path(member.filename).suffix.lower() == '.zip': raise ValueError('nested ZIP requires explicit discovery support')
                data = z.read(member)
                if apple_metadata(member.filename, data): continue
                if Path(member.filename).suffix.lower() in SOURCE_EXTENSIONS:
                    result.append({'path': archive_path + '!/' + member.filename,
                                   'archive': {'path': archive_path, 'member': member.filename, 'sha256': digest(archive)},
                                   'sourceSha256': hashlib.sha256(data).hexdigest(), 'sourceBytes': len(data),
                                   'sourceLines': len(data.splitlines())})
    return sorted(result, key=lambda s: s['path'])


def apple_metadata(name, data):
    # AppleDouble resource-fork records are not COBOL sources, even with a .cbl suffix.
    return name.startswith('__MACOSX/') and Path(name).name.startswith('._') and data[:4] == bytes.fromhex('00051607')


def extract_archives(upstream, destination):
    audit = []
    for archive in [p for p in snapshot_files(upstream) if p.suffix.lower() == '.zip']:
        logical = archive.relative_to(upstream).as_posix()
        root = destination / (logical + '!')
        excluded, kept = [], []
        with zipfile.ZipFile(archive) as z:
            for member in z.infolist():
                if member.is_dir(): continue
                data = z.read(member)
                if apple_metadata(member.filename, data):
                    excluded.append({'member': member.filename, 'reason': 'AppleDouble metadata, magic 00051607'})
                    continue
                target = (root / member.filename).resolve()
                if not target.is_relative_to(root.resolve()): raise ValueError('unsafe archive member path')
                if target.exists(): raise ValueError('duplicate archive member path')
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_bytes(data)
                kept.append(member.filename)
        audit.append({'archive': logical, 'sha256': digest(archive), 'membersExtracted': len(kept),
                      'memberExtensionInventory': dict(Counter(Path(n).suffix.lower() or '<none>' for n in kept)),
                      'excludedNonSourceMetadata': excluded})
    return audit


def libraries(root, source, archive_root=None, source_logical=None):
    # .dcl is justified by actual upstream DCLGEN directories/files; these are dependencies.
    files = [p for p in snapshot_files(root) if p.suffix.lower() in ('.cpy', '.dcl')]
    roots = {p.parent.relative_to(root).as_posix(): p.parent for p in files}
    if archive_root is not None:
        for p in snapshot_files(archive_root):
            if p.suffix.lower() in ('.cpy', '.dcl') or p.parent.name.lower() in LIBRARY_NAMES:
                roots[p.parent.relative_to(archive_root).as_posix()] = p.parent
    parts = Path(source_logical).parent.parts if source_logical else source.parent.relative_to(root).parts
    def precedence(logical):
        shared = 0
        for a, b in zip(parts, Path(logical).parts):
            if a != b: break
            shared += 1
        return (-shared, logical)
    ordered = sorted(roots, key=precedence)
    if not ordered:
        raise ValueError('No upstream dependency roots discovered')
    names = defaultdict(list)
    for logical in ordered:
        for p in sorted(roots[logical].iterdir()):
            if p.is_file():
                names[p.stem.casefold()].append((logical + '/' + p.name, p))
    collisions = [{'name': key, 'pathsInRootOrder': [logical for logical, _ in ps],
                   'sameContent': len({digest(p) for _, p in ps}) == 1}
                  for key, ps in sorted(names.items()) if len(ps) > 1]
    return ordered, collisions, [roots[logical] for logical in ordered]


def classify_failure(stage, code, diagnostic):
    """Classify process diagnostics, never infer a missing COBOL capability from normalization."""
    if any(x in diagnostic for x in ('OutOfMemoryError', 'RESOURCE_LIMIT', 'IMPLEMENTATION_LIMIT', 'StackOverflowError')):
        return 'BLOCKED', 'RESOURCE_LIMIT', 'RESOURCE_LIMIT'
    if any(x in diagnostic for x in ('NullPointerException', 'AssertionError', 'ClassNotFoundException',
                                     'NoClassDefFoundError', 'LinkageError', 'Could not find or load main class')):
        return 'FAILED', 'INTERNAL_FAILURE', 'UNEXPECTED_RUNTIME_FAILURE'
    if 'COPY_NOT_FOUND' in diagnostic:
        return 'BLOCKED', 'COPYBOOK_MISSING', 'COPY_NOT_FOUND'
    if any(x in diagnostic for x in ('Unsupported tab in fixed-format', 'Unsupported fixed-format indicator',
                                      'Unsupported line separator', 'phase=NORMALIZATION', 'SourceNormalizer.')):
        reason = 'FIXED_FORMAT_TAB' if 'Unsupported tab' in diagnostic else 'NORMALIZATION_REJECTED'
        return 'BLOCKED', 'NORMALIZATION_GAP', reason
    if 'phase=SOURCE_READ' in diagnostic:
        return 'BLOCKED', 'CORPUS_INPUT_GAP', 'SOURCE_READ_FAILED'
    if 'phase=PREPROCESSING' in diagnostic:
        return 'BLOCKED', 'PREPROCESSING_GAP', 'PREPROCESSING_FAILED'
    if stage == 'frontend':
        return 'BLOCKED', 'PARSER_FRONTEND_GAP', 'FRONTEND_NO_USABLE_SP'
    if (stage == 'lower' and code == 5) or diagnostic.startswith(('AIR ', 'INPUT_CODEC')):
        return 'BLOCKED', 'AIR_TRANSPORT_GAP', 'AIR_CODEC_REJECTED'
    if stage == 'lower' and code in (3, 4):
        return 'BLOCKED', 'LOWERING_GAP', 'SP_INPUT_REJECTED' if code == 3 else 'LOWERING_REJECTED'
    if stage == 'cfg' and code in (3, 4, 5):
        return 'BLOCKED', 'CFG_ANALYSIS_GAP', 'CFG_REJECTED'
    if stage == 'dependency' and code in (3, 4, 5):
        return 'BLOCKED', 'DEPENDENCY_ANALYSIS_GAP', 'DEPENDENCY_REJECTED'
    return 'FAILED', 'INTERNAL_FAILURE', 'PROCESS_FAILED'


def execute_stage(stage, command, cwd, timeout):
    start = time.monotonic_ns()
    result = {'state': 'PASS', 'reasonCategory': None, 'reasonCode': None, 'diagnostic': None,
              'configuredTimeoutSeconds': timeout, 'command': command,
              'stdout': stage + '.stdout', 'stderr': stage + '.stderr'}
    with (cwd / result['stdout']).open('wb') as out, (cwd / result['stderr']).open('wb') as err:
        try:
            process = subprocess.Popen(command, cwd=cwd, stdout=out, stderr=err, start_new_session=True)
            try:
                result['exitCode'] = process.wait(timeout=timeout)
            except subprocess.TimeoutExpired:
                try:
                    os.killpg(process.pid, signal.SIGKILL)
                except ProcessLookupError:
                    pass  # Process exited between deadline and group termination.
                process.wait()
                result.update(state='TIMEOUT', reasonCategory='TIMEOUT', reasonCode='STAGE_TIMEOUT',
                              diagnostic=f'{stage} exceeded {timeout} seconds', exitCode=process.returncode)
        except OSError as error:
            result.update(state='FAILED', reasonCategory='INTERNAL_FAILURE', reasonCode='PROCESS_START_FAILED',
                          diagnostic=str(error), exitCode=None)
    result['elapsedMs'] = elapsed(start)
    if result['state'] == 'PASS' and result['exitCode'] != 0:
        diagnostic = (cwd / result['stderr']).read_text(errors='replace')
        state, category, reason = classify_failure(stage, result['exitCode'], diagnostic)
        result.update(state=state, reasonCategory=category, reasonCode=reason, diagnostic=diagnostic[-16000:])
    return result


def empty_program(source):
    return {**source, 'programName': None,
            'stages': {s: {'state': 'NOT_REACHED', 'reasonCategory': None, 'reasonCode': 'UPSTREAM_NOT_PRODUCED',
                           'diagnostic': None, 'elapsedMs': None, 'artifactProduced': False} for s in STAGES},
            'timings': {**dict.fromkeys(TIMING_KEYS), 'programElapsedMs': None},
            'preprocessedProduced': False, 'artifacts': {}}


def attempt_all(sources, attempt):
    records = []
    for source in sources:
        start = time.monotonic_ns()
        try:
            record = attempt(source)
        except Exception as error:
            # Catastrophic harness errors are data too; never silently drop a discovered path.
            record = empty_program(source)
            record['runnerFailure'] = {'reasonCategory': 'INTERNAL_FAILURE', 'reasonCode': 'RUNNER_EXCEPTION',
                                       'diagnostic': repr(error)}
            record['timings']['programElapsedMs'] = elapsed(start)
        records.append(record)
    return records


def attempt_program(source_record, upstream, work, config, timeout, jvm_args):
    start = time.monotonic_ns()
    record = empty_program(source_record)
    source = (work / 'archive-members' if source_record.get('archive') else upstream) / source_record['path']
    run_dir = work / 'programs' / source_record['path']
    run_dir.mkdir(parents=True, exist_ok=False)
    record['rawDirectory'] = run_dir.relative_to(work).as_posix()
    try:
        roots, collisions, physical_roots = libraries(upstream, source, work / 'archive-members', source_record['path'])
        record['copybookRoots'] = roots
        record['copybookCollisions'] = collisions
        # ExplorerMain's existing UI-export resources are required by its current CLI.
        web = run_dir / 'src/main/resources'
        web.mkdir(parents=True)
        (web / 'web').symlink_to(Path(config['checkouts']['proleap-poc']) / 'src/main/resources/web', target_is_directory=True)
        outputs = {'frontend': run_dir / 'sp/cobol-semantic-product.json', 'lower': run_dir / 'program.air.json',
                   'cfg': run_dir / 'cfg.json', 'dependency': run_dir / 'dependencies.json'}
        args = {'frontend': ['--source', str(source), '--copybooks', ','.join(str(r) for r in physical_roots),
                             '--output', str(run_dir / 'sp')],
                'lower': [str(outputs['frontend']), str(outputs['lower'])],
                'cfg': [str(outputs['lower']), str(outputs['cfg'])],
                'dependency': [str(outputs['lower']), str(outputs['dependency'])]}
        for stage, time_key in zip(STAGES, TIMING_KEYS):
            command = ['java', *jvm_args, '-cp', os.pathsep.join(config[stage]['classpath']), config[stage]['main'], *args[stage]]
            outcome = execute_stage(stage, command, run_dir, timeout)
            record['stages'][stage] = outcome
            record['timings'][time_key] = outcome['elapsedMs']
            path = outputs[stage]
            outcome['artifactProduced'] = path.is_file()
            if stage == 'frontend':
                record['preprocessedProduced'] = (run_dir / 'sp/preprocessed.cbl').is_file()
            if path.is_file():
                record['artifacts'][stage] = {'path': path.relative_to(work).as_posix(), 'sha256': digest(path), 'bytes': path.stat().st_size}
            # Nonzero exit remains a failure even if a stale/partial output exists; dirs are fresh.
            if outcome['state'] != 'PASS': break
            if not path.is_file():
                outcome.update(state='FAILED', reasonCategory='INTERNAL_FAILURE', reasonCode='OUTPUT_MISSING',
                               diagnostic='Process returned zero without producing its output')
                break
            try:
                data = json.loads(path.read_bytes())
                if stage == 'frontend':
                    if data['contractVersion'] != config['semanticProductVersion']:
                        raise ValueError('unexpected SP version')
                    record['programName'] = data['unit']['canonicalProgramName']
                    partial = data['gaps'] or any(data['coverage'][key] for key in ('partialStatements', 'unsupportedStatements', 'inputMissingStatements'))
                elif stage == 'lower':
                    partial = bool(data['publication'].get('uncertainties'))
                elif stage == 'dependency':
                    from dependency_wire import read
                    read(path)
                    partial = data['publicationInventory'] != 'COMPLETE' or any(
                        s['effectiveUnknownRemainder'] or s['openControlRemainder'] for s in data['sites'])
                else:
                    from cfg_wire_contract import verify
                    verify(path.read_bytes())
                    partial = record['stages']['lower']['state'] == 'PARTIAL'
                if partial:
                    outcome.update(state='PARTIAL', reasonCode='PRODUCT_WITH_EXPLICIT_GAPS',
                                   diagnostic='Output produced with explicit coverage gaps/uncertainty; not complete source semantics')
            except (ValueError, KeyError, TypeError) as error:
                outcome.update(state='FAILED', reasonCategory='INTERNAL_FAILURE', reasonCode='OUTPUT_CONTRACT_FAILED', diagnostic=str(error))
                break
    except Exception as error:
        record['runnerFailure'] = {'reasonCategory': 'INTERNAL_FAILURE', 'reasonCode': 'RUNNER_EXCEPTION', 'diagnostic': repr(error)}
    finally:
        record['timings']['programElapsedMs'] = elapsed(start)
        dump(run_dir / 'measurement.json', record)
    return record


def environment(jvm_args):
    def command(args):
        p = subprocess.run(args, capture_output=True, text=True, timeout=10)
        return (p.stdout + p.stderr).strip()
    cpu = next((line.split(':', 1)[1].strip() for line in Path('/proc/cpuinfo').read_text().splitlines()
                if line.startswith('model name')), None) if Path('/proc/cpuinfo').exists() else None
    memory = next((line.split(':', 1)[1].strip() for line in Path('/proc/meminfo').read_text().splitlines()
                   if line.startswith('MemTotal:')), None) if Path('/proc/meminfo').exists() else None
    return {'os': platform.platform(), 'pythonVersion': sys.version, 'javaAndJvmVersion': command(['java', '-version']),
            'availableProcessors': len(os.sched_getaffinity(0)) if hasattr(os, 'sched_getaffinity') else os.cpu_count(),
            'logicalProcessors': os.cpu_count(), 'cpuModel': cpu, 'totalMemory': memory, 'jvmArguments': jvm_args,
            'jvmEnvironment': {k: os.environ.get(k) for k in ('JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS', '_JAVA_OPTIONS')},
            'processReuse': False, 'timingLabel': 'OBSERVED BASELINE TIMING',
            'timingMethod': 'time.monotonic_ns(); timings include normal process startup/JVM behavior',
            'executionMode': 'SEQUENTIAL; one canonical measured run per source; no warmup or JIT correction'}


def run(upstream, work, runtime_path, timeout, jvm_args):
    require_local()
    if timeout <= 0: raise ValueError('stage timeout must be positive')
    work.mkdir(parents=True, exist_ok=False)
    pins, config = json.loads(PINS.read_text()), json.loads(runtime_path.read_text())
    if config['sources'] != pins['analysisRepositories']:
        raise ValueError('runtime pipeline snapshots differ from baseline pins')
    check_snapshot(upstream, pins['upstream']['commit'])
    for name, sha in config['sources'].items(): check_snapshot(Path(config['checkouts'][name]), sha)
    env = environment(jvm_args)
    started = time.monotonic_ns()
    files = snapshot_files(upstream)
    archives = extract_archives(upstream, work / 'archive-members')
    sources = discover(upstream)
    discovery_ms = elapsed(started)
    dump(work / 'discovery.json', sources)
    corpus_started = time.monotonic_ns()
    def attempt(source):
        record = attempt_program(source, upstream, work, config, timeout, jvm_args)
        print(f"{source['path']}: " + ', '.join(f"{s}={record['stages'][s]['state']}" for s in STAGES), flush=True)
        return record
    records = attempt_all(sources, attempt)
    raw = {'schemaVersion': 'carddemo-measurements-1.0.0', 'measuredAt': datetime.now(timezone.utc).isoformat(),
           'snapshot': pins, 'environment': env, 'programs': records,
           'corpus': {'programsDiscovered': len(sources), 'programsAttempted': len(records),
                      'discoveryRules': 'recursive .cbl/.cob/.cl2, case insensitive; include ZIP source members; exclude verified AppleDouble metadata; no whitelist',
                      'additionalExtensionJustification': '.cl2: UniKix migrated_app/cbl contains standalone COBOL IDENTIFICATION DIVISION and PROGRAM-ID, alongside .cbl programs',
                      'archiveInventory': archives,
                      'extensionInventory': dict(sorted(Counter(p.suffix.lower() or '<none>' for p in files).items())),
                      'physicalDuplicatesExcluded': [], 'discoveredSources': sources},
           'timings': {'corpusElapsedMs': elapsed(corpus_started), 'discoveryElapsedMs': discovery_ms,
                       'setupElapsedMs': config['setupElapsedMs'], 'stageTimeoutSeconds': timeout},
           'rawEvidenceDirectory': str(work), 'runtimeConfiguration': str(runtime_path)}
    dump(work / 'measurements.json', raw)
    # Immutability check after all attempts; no checkout or source mutation is permitted in the loop.
    check_snapshot(upstream, pins['upstream']['commit'])
    if discover(upstream) != sources: raise ValueError('source bytes changed during baseline')
    for source in sources:
        if source.get('archive') and digest(work / 'archive-members' / source['path']) != source['sourceSha256']:
            raise ValueError('extracted source bytes changed during baseline')
    for name, sha in config['sources'].items(): check_snapshot(Path(config['checkouts'][name]), sha)
    from carddemo_metrics import validate_enumeration
    validate_enumeration(sources, records)
    return raw


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--upstream', type=Path, required=True, help='clean checkout of the immutable upstream pin')
    parser.add_argument('--work', type=Path, required=True, help='new output directory; existing evidence is never overwritten')
    parser.add_argument('--runtime', type=Path, required=True, help='carddemo_setup.py runtime.json')
    parser.add_argument('--stage-timeout-seconds', type=float, default=120,
                        help='operational protection per process, default 120 seconds, not an analyzer SLA')
    parser.add_argument('--jvm-arg', action='append', default=None, help='repeatable JVM argument; default -Xmx2g')
    args = parser.parse_args()
    run(args.upstream.resolve(), args.work.resolve(), args.runtime.resolve(), args.stage_timeout_seconds, args.jvm_arg or ['-Xmx2g'])
