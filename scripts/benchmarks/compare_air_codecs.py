#!/usr/bin/env python3
"""Compile/run AirCodecProducts against two supplied classpaths; retain and compare full products."""
import argparse
import json
import statistics
import subprocess
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--baseline-classpath', required=True)
    parser.add_argument('--candidate-classpath', required=True)
    parser.add_argument('--java-home', type=Path, required=True)
    parser.add_argument('--input', type=Path, action='append', required=True,
                        help='Directory containing program.air.json and dependency-input.json; repeatable')
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--repetitions', type=int, default=3)
    parser.add_argument('--heap', default='2g')
    parser.add_argument('--modes', nargs='+', choices=['decode', 'products', 'bundle'],
                        default=['decode', 'products', 'bundle'])
    args = parser.parse_args()
    if args.repetitions < 1:
        parser.error('repetitions must be positive')
    args.output.mkdir(parents=True, exist_ok=False)
    classes = args.output / 'classes'
    classes.mkdir()
    source = Path(__file__).with_name('AirCodecProducts.java')
    subprocess.run([str(args.java_home / 'bin/javac'), '--release', '21', '-cp', args.baseline_classpath,
                    '-d', str(classes), str(source)], check=True)
    results = []
    for index, input_dir in enumerate(args.input):
        for mode in args.modes:
            for repetition in range(1 if mode == 'products' else args.repetitions):
                variants = ['baseline', 'candidate'] if repetition % 2 == 0 else ['candidate', 'baseline']
                pair = {}
                for variant in variants:
                    output = args.output / str(index) / mode / str(repetition) / variant
                    output.mkdir(parents=True)
                    classpath = getattr(args, variant + '_classpath')
                    command = [str(args.java_home / 'bin/java'), '-Xmx' + args.heap, '-cp',
                               str(classes) + ':' + classpath,
                               'io.github.gustavo2358.analysis.cfg.application.AirCodecProducts',
                               str(input_dir), str(output), mode, '1']
                    run = subprocess.run(command, capture_output=True, text=True)
                    (output / 'stdout.log').write_text(run.stdout)
                    (output / 'stderr.log').write_text(run.stderr)
                    if run.returncode:
                        raise RuntimeError(f'{variant} failed: {output / "stderr.log"}')
                    measured = json.loads(run.stdout.splitlines()[-1])
                    row = dict(input=str(input_dir), mode=mode, repetition=repetition,
                               variant=variant, command=command, measured=measured)
                    results.append(row)
                    (args.output / 'results.json').write_text(json.dumps(results, indent=2) + '\n')
                    pair[variant] = output
                    print(json.dumps(row), flush=True)
                baseline_products = {p.name for p in pair['baseline'].glob('*.json')}
                candidate_products = {p.name for p in pair['candidate'].glob('*.json')}
                if baseline_products != candidate_products:
                    raise AssertionError(f'Product inventory changed: {pair}')
                for name in sorted(baseline_products):
                    path = pair['baseline'] / name
                    other = pair['candidate'] / name
                    if path.read_bytes() != other.read_bytes():
                        raise AssertionError(f'Product changed: {path} vs {other}')
    summary = []
    for input_dir in args.input:
        for mode in args.modes:
            subset = [r for r in results if r['input'] == str(input_dir) and r['mode'] == mode]
            key = 'decodeMs' if mode == 'decode' else 'totalMs'
            times = {v: statistics.median(r['measured'][key] for r in subset if r['variant'] == v)
                     for v in ['baseline', 'candidate']}
            summary.append(dict(input=str(input_dir), mode=mode, metric=key, medianMs=times,
                                reductionPercent=100 * (1 - times['candidate'] / times['baseline'])))
    (args.output / 'summary.json').write_text(json.dumps(summary, indent=2) + '\n')
    print(json.dumps(summary, indent=2))


if __name__ == '__main__':
    main()
