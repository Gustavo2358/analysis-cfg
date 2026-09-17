#!/usr/bin/env python3
"""Manual W2 synthetic probe. Compile tests first; use Java 21. No timing assertions.
--instrument compiles temporary source copies into an overlay, never edits production.
Do not compare instrumented runtime with uninstrumented runtime.
"""
import argparse
from collections import Counter
import json
import os
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[2]
PACKAGE = 'io.github.gustavo2358.analysis.values'
SOURCE = ROOT / 'analysis-values/src/main/java' / PACKAGE.replace('.', '/')


def replace_once(text, old, new):
    if text.count(old) != 1:
        raise RuntimeError('instrumentation anchor absent/ambiguous: ' + old)
    return text.replace(old, new)


def overlay(output, cp):
    destination = output / 'instrumented'; destination.mkdir(parents=True, exist_ok=True)
    files = []
    for name in ('FactorizedAlternatives', 'ByteImage', 'RegionalValuesAnalysis'):
        text = (SOURCE / (name + '.java')).read_text()
        if name == 'FactorizedAlternatives':
            text = replace_once(text, 'final class FactorizedAlternatives<T> {', '''final class FactorizedAlternatives<T> {
    static long w2NodeCalls,w2UnionCalls,w2UnionNontrivial,w2SizeCalls,w2SizeNanos,w2SizeNodeVisits,w2SizeEdgeVisits,w2Keys;
    static final Map<Pair<?>,Boolean> w2Pairs=new HashMap<>();''')
            text = replace_once(text, 'Node<T> node(int level,Map<T,Node<T>> edges) {', 'Node<T> node(int level,Map<T,Node<T>> edges) { w2NodeCalls++;')
            text = replace_once(text, 'var key=new Key<>', 'w2Keys++;var key=new Key<>')
            text = replace_once(text, 'Node<T> union(Node<T> a,Node<T> b) {', 'Node<T> union(Node<T> a,Node<T> b) { w2UnionCalls++;')
            text = replace_once(text, 'var memo=new HashMap<Pair<T>,Node<T>>();', 'w2UnionNontrivial++;w2Pairs.put(new Pair<>(a,b),true);var memo=new HashMap<Pair<T>,Node<T>>();')
            text = replace_once(text, 'static Size size(Collection<? extends Node<?>> roots) {', 'static Size size(Collection<? extends Node<?>> roots) { w2SizeCalls++;long w2Start=System.nanoTime();')
            text = replace_once(text, 'edges+=node.edges.size();', 'w2SizeNodeVisits++;w2SizeEdgeVisits+=node.edges.size();edges+=node.edges.size();')
            text = replace_once(text, 'return new Size(visited.size(),edges,components.values().stream().mapToLong(Set::size).max().orElse(0));', 'w2SizeNanos+=System.nanoTime()-w2Start;return new Size(visited.size(),edges,components.values().stream().mapToLong(Set::size).max().orElse(0));')
        elif name == 'ByteImage':
            text = replace_once(text, 'final class ByteImage {', 'final class ByteImage {\n    static long w2HashCalls;')
            text = replace_once(text, '@Override public int hashCode(){', '@Override public int hashCode(){w2HashCalls++;')
        else:
            text = replace_once(text, 'public final class RegionalValuesAnalysis {', 'public final class RegionalValuesAnalysis {\n    static long w2TrackCalls,w2TrackNanos;')
            text = replace_once(text, 'private State track(State state) {', 'private State track(State state) { w2TrackCalls++;long w2Start=System.nanoTime();')
            text = replace_once(text, 'maxComponentCardinality=Math.max(maxComponentCardinality,size.maxComponent());return state;', 'maxComponentCardinality=Math.max(maxComponentCardinality,size.maxComponent());w2TrackNanos+=System.nanoTime()-w2Start;return state;')
        file = destination / (name + '.java'); file.write_text(text); files.append(str(file))
    subprocess.run(['javac', '--release', '21', '-cp', cp, '-d', str(destination), *files], check=True)
    return str(destination) + os.pathsep + cp


def summarize_jfr(path):
    data = json.loads(subprocess.check_output(['jfr', 'print', '--json', '--stack-depth', '256', '--events',
        'jdk.ExecutionSample,jdk.ObjectAllocationSample', str(path)], text=True))
    buckets, inclusive, allocations = Counter(), Counter(), Counter()
    samples = 0
    for event in data['recording']['events']:
        values = event['values']
        if event['type'] == 'jdk.ObjectAllocationSample':
            allocations[values['objectClass']['name']] += values['weight']; continue
        frames = (values.get('stackTrace') or {}).get('frames', [])
        methods = [f['method']['type']['name'].replace('/', '.') + '.' + f['method']['name'] for f in frames]
        if not any(('RegionalValuesAnalysis' in m or 'FactorizedAlternatives' in m or 'ByteImage' in m) for m in methods):
            buckets['outside_regional'] += 1; continue
        samples += 1
        flags = {
            'metrics_size_track': any(('FactorizedAlternatives.size' in m or 'RegionalValuesAnalysis$State.size' in m or 'RegionalValuesAnalysis$Engine.track' in m) for m in methods),
            'hashing': any('.hashCode' in m for m in methods),
            'interning_node': any('FactorizedAlternatives.node' in m for m in methods),
            'union': any('FactorizedAlternatives.union' in m for m in methods),
            'representation_other': any(('FactorizedAlternatives.' in m or 'ByteImage.' in m) for m in methods),
        }
        for key, value in flags.items():
            if value: inclusive[key] += 1
        bucket = next((key for key, value in flags.items() if value), None)
        if bucket is None:
            bucket = 'transfer_other' if any('RegionalValuesAnalysis$Engine.' in m for m in methods) else 'solver_other'
        buckets[bucket] += 1
    result = {'regional_samples': samples, 'exclusive_samples': dict(buckets),
              'inclusive_samples_overlap': dict(inclusive), 'allocation_sample_weight_bytes_top': allocations.most_common(12)}
    path.with_suffix('.summary.json').write_text(json.dumps(result, indent=2) + '\n')
    print('W2_JFR ' + json.dumps(result, sort_keys=True))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--regions', type=int, default=8)
    parser.add_argument('--producers', type=int, default=50)
    parser.add_argument('--warmups', type=int, default=3)
    parser.add_argument('--repeats', type=int, default=5)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--jfr', action='store_true')
    parser.add_argument('--instrument', action='store_true')
    args = parser.parse_args(); args.output = args.output.resolve(); args.output.mkdir(parents=True, exist_ok=True)
    jars = ROOT / '.harness-results/build/m2'
    cp = os.pathsep.join([str(ROOT / module / 'target' / directory)
        for module in ('analysis-values', 'analysis-kernel', 'cfg-kernel') for directory in ('test-classes', 'classes')]
        + [str(p) for pattern in ('io/github/gustavo2358/air-java/*/*.jar', 'org/junit/jupiter/junit-jupiter-api/*/*.jar',
           'org/opentest4j/opentest4j/*/*.jar', 'org/apiguardian/apiguardian-api/*/*.jar', 'org/junit/platform/junit-platform-commons/*/*.jar') for p in jars.glob(pattern)])
    if args.instrument: cp = overlay(args.output, cp)
    command = ['java', '-Xms256m', '-Xmx1g', '-XX:FlightRecorderOptions=stackdepth=256', '-cp', cp, PACKAGE + '.RegionalCostProbe',
        str(args.regions), str(args.producers), str(args.warmups), str(args.repeats), str(args.output)]
    if args.jfr: command.append('jfr')
    result = subprocess.run(command, check=True, text=True, stdout=subprocess.PIPE)
    (args.output / 'run.log').write_text(result.stdout); print(result.stdout, end='')
    if args.jfr: summarize_jfr(args.output / 'solve.jfr')


if __name__ == '__main__':
    main()
