#!/usr/bin/env python3
"""Build real producers from the source lock in isolated checkouts. Local/on-demand."""
import argparse
import json
import os
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'scripts/harness'))
from lean import require_local, git
from upstream_fast import compile_dependency


def checkout(root, name, source, destination):
    sibling = Path(os.environ.get('W2D_SOURCE_ROOT', str(root.parent))) / name
    remote = 'https://github.com/' + source['repository'] + '.git'
    subprocess.run(['git', 'clone', '--no-hardlinks', '--no-checkout',
                    str(sibling) if sibling.is_dir() else remote, str(destination)], check=True)
    pin = source.get('commit', source.get('main_commit'))
    if subprocess.run(['git', '-C', str(destination), 'cat-file', '-e', pin + '^{commit}'],
                      stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode:
        subprocess.run(['git', '-C', str(destination), 'fetch', remote, pin], check=True)
    subprocess.run(['git', '-C', str(destination), 'checkout', '--detach', pin], check=True)
    if git(destination, 'rev-parse', 'HEAD') != pin or git(destination, 'status', '--porcelain'):
        raise RuntimeError('source differs from pin: ' + name)


def build(work, lock_path=ROOT / 'docs/sources/sources.lock.json'):
    require_local()
    work.mkdir(parents=True, exist_ok=False)
    lock = json.loads(lock_path.read_text())
    sources = {}
    for name, key in (('air-java', 'air_java'), ('proleap-poc', 'proleap_poc'), ('cobol-lower', 'cobol_lower')):
        checkout(ROOT, name, lock[key], work / name)
        sources[name] = lock[key].get('commit', lock[key].get('main_commit'))
    # The Maven cache supplies third-party dependencies only. Rebuild/install locked AIR first.
    repository = Path(os.environ.get('W2D_MAVEN_REPO', str(work / 'm2'))).resolve()
    def maven(*args):
        return ['mvn', '-B', '-ntp', '-Dmaven.repo.local=' + str(repository), *args]
    def execute(command, cwd=work):
        print('+ ' + ' '.join(command), flush=True)
        subprocess.run(command, cwd=cwd, check=True)
    compile_dependency(work / 'air-java', work, execute, maven)
    execute(maven('clean', 'compile', 'org.apache.maven.plugins:maven-jar-plugin:3.4.2:jar',
                  'org.apache.maven.plugins:maven-dependency-plugin:3.8.1:build-classpath',
                  '-DincludeScope=runtime', '-Dmdep.outputFile=' + str(work / 'front-classpath.txt')), work / 'proleap-poc')
    execute(maven('clean', 'install', '-DskipTests', '-Dexec.skip=true'), work / 'cobol-lower')
    execute(maven('-pl', 'adapters', 'org.apache.maven.plugins:maven-dependency-plugin:3.8.1:build-classpath',
                  '-DincludeScope=runtime', '-Dmdep.outputFile=' + str(work / 'lower-classpath.txt')), work / 'cobol-lower')
    config = {'sources': sources, 'semanticProductVersion': lock['proleap_poc']['semantic_product_version']}
    for name, jar, cp, main in (
            ('frontend', 'proleap-poc/target/antlr-parse-tree-explorer-1.0.0-SNAPSHOT.jar', 'front-classpath.txt',
             'io.github.gustavo2358.cobolexplorer.ExplorerMain'),
            ('lower', 'cobol-lower/adapters/target/cobol-lower-adapters-0.1.0-SNAPSHOT.jar', 'lower-classpath.txt',
             'io.github.gustavo2358.lower.adapters.cli.CobolLower')):
        pinned_jars = {
            'air-java-0.1.0-SNAPSHOT.jar': work / 'fast-upstream/air-model/air-java-0.1.0-SNAPSHOT.jar',
            'air-json-0.1.0-SNAPSHOT.jar': work / 'fast-upstream/air-json/air-json-0.1.0-SNAPSHOT.jar',
            'cobol-lower-core-0.1.0-SNAPSHOT.jar': work / 'cobol-lower/core/target/cobol-lower-core-0.1.0-SNAPSHOT.jar',
        }
        dependencies = [str(pinned_jars.get(Path(p).name, Path(p))) for p in (work / cp).read_text().strip().split(os.pathsep)]
        config[name] = {'main': main, 'classpath': [str(work / jar), *dependencies]}
    for name, pin in sources.items():
        if git(work / name, 'rev-parse', 'HEAD') != pin or git(work / name, 'status', '--porcelain'):
            raise RuntimeError('build changed producer source: ' + name)
    # Runtime locations for the next command, not a qualification receipt.
    (work / 'producers.json').write_text(json.dumps(config, indent=2) + '\n')
    print('PASS producer builds at exact pins: ' + json.dumps(sources), flush=True)
    return config


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--work', required=True, type=Path)
    parser.add_argument('--lock', type=Path, default=ROOT / 'docs/sources/sources.lock.json')
    args = parser.parse_args()
    build(args.work.resolve(), args.lock.resolve())
