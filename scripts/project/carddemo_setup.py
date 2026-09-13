"""Local, isolated builds for the historical CardDemo evaluation (no qualification)."""
import contextlib
import json
import os
from pathlib import Path
import subprocess
import time

from prepare_w2d_producers import ROOT, build, checkout, git, require_local

PINS = ROOT / 'docs/evals/cp6/carddemo-full-pins.json'
MODULES = ('cfg-kernel', 'cfg-adapters', 'cfg-launcher', 'analysis-kernel',
           'analysis-values', 'analysis-dependencies', 'analysis-dataflow',
           'analysis-adapters', 'analysis-launcher')


def check_snapshot(path, pin):
    if git(path, 'rev-parse', 'HEAD') != pin or git(path, 'status', '--porcelain'):
        raise ValueError('snapshot must be clean at exact pin: ' + str(path))


def prepare(work, maven_repo=None):
    require_local()
    started = time.monotonic_ns()
    work.mkdir(parents=True, exist_ok=False)
    pins = json.loads(PINS.read_text())
    lock = {'proleap_poc': {'semantic_product_version': pins['semanticProductVersion']}}
    for name, sha in pins['analysisRepositories'].items():
        lock.setdefault(name.replace('-', '_'), {}).update(repository='Gustavo2358/' + name, commit=sha)
    lock_path = work / 'build-lock.json'
    lock_path.write_text(json.dumps(lock, indent=2) + '\n')
    if maven_repo:
        os.environ['W2D_MAVEN_REPO'] = str(maven_repo.resolve())
    with (work / 'setup.log').open('w') as log:
        # Existing producer build helper; subprocess output goes to this same raw log.
        with contextlib.redirect_stdout(log), contextlib.redirect_stderr(log):
            saved_out, saved_err = os.dup(1), os.dup(2)
            try:
                os.dup2(log.fileno(), 1); os.dup2(log.fileno(), 2)
                producers = build(work / 'producers', lock_path)
                cfg = work / 'analysis-cfg'
                checkout(ROOT, 'analysis-cfg', lock['analysis_cfg'], cfg)
                repo = os.environ.get('W2D_MAVEN_REPO', str(work / 'producers/m2'))
                subprocess.run(['mvn', '-B', '-ntp', '-Dmaven.repo.local=' + repo,
                                'clean', 'package', '-DskipTests',
                                'org.apache.maven.plugins:maven-dependency-plugin:3.8.1:build-classpath',
                                '-DincludeScope=runtime', '-Dmdep.outputFile=target/runtime-classpath.txt'],
                               cwd=cfg, check=True)
            finally:
                os.dup2(saved_out, 1); os.dup2(saved_err, 2)
                os.close(saved_out); os.close(saved_err)
    # Put exact AIR artifacts before any cache entries, as in existing E2E tooling.
    air = [work / 'producers/fast-upstream' / module / (name + '-0.1.0-SNAPSHOT.jar')
           for module, name in [('air-model', 'air-java'), ('air-json', 'air-json')]]
    cp = [str(cfg / m / 'target/classes') for m in MODULES] + [str(p) for p in air]
    # Exclude product cache jars altogether; only pinned classes and AIR jars supply products.
    cached = (cfg / 'analysis-launcher/target/runtime-classpath.txt').read_text().strip().split(os.pathsep)
    cp += [p for p in cached if not Path(p).name.startswith(('air-java-', 'air-json-', 'analysis-', 'cfg-'))]
    config = {**producers, 'sources': pins['analysisRepositories'],
              'checkouts': {name: str(work / ('analysis-cfg' if name == 'analysis-cfg' else 'producers/' + name))
                            for name in pins['analysisRepositories']},
              'cfg': {'main': 'io.github.gustavo2358.analysis.cfg.launcher.AnalysisCfg', 'classpath': cp},
              'dependency': {'main': 'io.github.gustavo2358.analysis.launcher.AnalysisDependencies', 'classpath': cp},
              'setupElapsedMs': (time.monotonic_ns() - started) / 1_000_000,
              'qualification': 'NOT_RUN; build only for observation'}
    for name, sha in config['sources'].items():
        check_snapshot(Path(config['checkouts'][name]), sha)
    (work / 'runtime.json').write_text(json.dumps(config, indent=2) + '\n')
    return config


if __name__ == '__main__':
    import argparse
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--work', type=Path, required=True)
    parser.add_argument('--maven-repo', type=Path)
    args = parser.parse_args()
    prepare(args.work.resolve(), args.maven_repo)
    print('Prepared exact pipeline snapshots; see ' + str(args.work / 'setup.log'))
