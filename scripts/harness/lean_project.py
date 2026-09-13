"""Preserve the existing fixed Fast unit/contract and architecture profile."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
from lean import validate_pins, require_local, git

LOCK = 'docs/sources/sources.lock.json'
PINS = ('Gustavo2358/air-java', 'Gustavo2358/analysis-ir', 'Gustavo2358/proleap-poc', 'Gustavo2358/cobol-lower')


def pin_errors(root):
    return validate_pins(root, LOCK, PINS)


def copy_pin_fixture(source, destination):
    target = destination / LOCK
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(source / LOCK, target)


def break_pin_fixture(root):
    (root / LOCK).write_text('{}')


def prepare(root):
    build = Path(os.environ.get('CFG_BUILD_ROOT', str(root / '.harness-results/build'))).resolve()
    build.mkdir(parents=True, exist_ok=True)
    air = build / 'air-java'
    lock = json.loads((root / LOCK).read_text())['air_java']
    def run(command, cwd=root):
        subprocess.run(command, cwd=cwd, check=True)
    if not air.exists():
        run(['git', 'clone', '--no-checkout', 'https://github.com/' + lock['repository'] + '.git', str(air)])
        run(['git', 'checkout', '--detach', lock['commit']], air)
    if git(air, 'rev-parse', 'HEAD') != lock['commit'] or git(air, 'status', '--porcelain'):
        raise RuntimeError('air-java source differs from immutable pin')
    os.environ['AIR_JAVA_CHECKOUT'] = str(air)
    if '-Dmaven.repo.local=' not in os.environ.get('MAVEN_OPTS', ''):
        os.environ['MAVEN_OPTS'] = os.environ.get('MAVEN_OPTS', '') + ' -Dmaven.repo.local=' + str(build / 'm2')
    from upstream_fast import compile_dependency
    compile_dependency(air, build, run, lambda *args: ['mvn', '-B', '-ntp', *args])


def technical_fast(root):
    prepare(root)
    for command in ([sys.executable, '-B', 'scripts/project/check_architecture.py', '--test-profile', 'fast'],
                    [sys.executable, '-B', 'scripts/project/test_dependency_wire.py'],
                    [sys.executable, '-B', 'scripts/project/test_lean_boundary.py'],
                    [sys.executable, '-B', 'scripts/project/test_carddemo_baseline.py'],
                    [sys.executable, '-B', 'scripts/project/test_validation.py']):
        subprocess.run(command, cwd=root, check=True)


def full_local(root):
    require_local()
    prepare(root)
    # Entire Maven test suite and every architecture boundary; additional probes below.
    subprocess.run([sys.executable, '-B', 'scripts/project/check_architecture.py'], cwd=root, check=True)
    # W5 E2E consumes real producer outputs; prepare their pinned isolated builds locally.
    build = Path(os.environ.get('CFG_BUILD_ROOT', str(root / '.harness-results/build'))).resolve()
    producers = Path(tempfile.mkdtemp(prefix='full-', dir=build)) / 'w5-producers'
    subprocess.run([sys.executable, '-B', 'scripts/project/prepare_w5_producers.py', '--work', str(producers)], cwd=root, check=True)
    os.environ['W5_PRODUCERS'] = str(producers / 'producers.json')
    for gate in ('semantic', 'performance', 'integration'):
        subprocess.run(['bash', 'scripts/project/check-' + gate + '.sh'], cwd=root, check=True)
    # W2D is a real, local-only cross-repo qualification; remote Fast uses in-memory AIR.
    w2d = Path(tempfile.mkdtemp(prefix='w2d-', dir=build))
    os.environ.setdefault('W2D_MAVEN_REPO', str(build / 'm2'))
    subprocess.run([sys.executable, '-B', 'scripts/project/prepare_w2d_producers.py',
                    '--work', str(w2d / 'producers')], cwd=root, check=True)
    subprocess.run(['mvn', '-B', '-ntp', '-DskipTests', 'package',
                    'org.apache.maven.plugins:maven-dependency-plugin:3.8.1:build-classpath',
                    '-DincludeScope=runtime', '-Dmdep.outputFile=target/runtime-classpath.txt'], cwd=root, check=True)
    subprocess.run([sys.executable, '-B', 'scripts/project/e2e_w2d.py', '--work', str(w2d / 'e2e'),
                    '--producers', str(w2d / 'producers/producers.json')], cwd=root, check=True)
    subprocess.run([sys.executable, '-B', 'scripts/project/e2e_move_data.py', '--work', str(w2d / 'move-data'),
                    '--producers', str(w2d / 'producers/producers.json')], cwd=root, check=True)
    subprocess.run([sys.executable, '-B', 'scripts/project/e2e_perform_basic.py', '--work', str(w2d / 'perform-basic'),
                    '--producers', str(w2d / 'producers/producers.json')], cwd=root, check=True)
    subprocess.run([sys.executable, '-B', 'scripts/project/e2e_multi_call.py', '--work', str(w2d / 'multi-call'),
                    '--producers', str(w2d / 'producers/producers.json')], cwd=root, check=True)
    subprocess.run([sys.executable, '-B', 'scripts/project/e2e_partial.py', '--work', str(w2d / 'partial'),
                    '--producers', str(w2d / 'producers/producers.json')], cwd=root, check=True)
