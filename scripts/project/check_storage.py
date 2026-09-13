#!/usr/bin/env python3
"""Storage campaign source/bytecode boundary; no source-language or solver rules in storage."""
import re
import struct
from pathlib import Path
from check_architecture import GateFailure
from check_w1 import command

PREFIX='io.github.gustavo2358.analysis.storage.'
NAMES=('StorageRange','StorageIndex','StatementEffects')
SOURCES={f'analysis-kernel/src/main/java/io/github/gustavo2358/analysis/storage/{name}.java' for name in NAMES}
ALLOWED=('java.','io.github.gustavo2358.air.model.',PREFIX,'io.github.gustavo2358.analysis.structure.')
DENIED=('java.io.','java.nio.file.','java.net.','java.lang.reflect.','ServiceLoader','org.antlr.','cobolexplorer','lower.')

def verify_sources(root:Path):
    folder=root/'analysis-kernel/src/main/java/io/github/gustavo2358/analysis/storage'
    actual={p.relative_to(root).as_posix() for p in folder.rglob('*.java')}
    if actual!=SOURCES:raise GateFailure('storage source inventory drift')
    for path in sorted(SOURCES):
        source=(root/path).read_text()
        for target in re.findall(r'^import (?:static )?([^;]+);',source,re.M):
            if not target.startswith(ALLOWED) or any(part in target for part in DENIED):
                raise GateFailure('storage import outside inward boundary: '+target)

def architecture(root:Path):
    verify_sources(root)
    from check_transport_architecture import dependencies_from_jdeps
    classes=root/'analysis-kernel/target/classes'
    cp=(root/'analysis-kernel/target/architecture-classpath.txt').read_text().strip()
    files=list((classes/'io/github/gustavo2358/analysis/storage').rglob('*.class'))
    if not files:raise GateFailure('compiled storage classes missing')
    for file in files:
        if struct.unpack('>IHH',file.read_bytes()[:8])!=(0xcafebabe,0,65):raise GateFailure('storage Java 21/no-preview contract')
    output=command(root,['jdeps','--multi-release','21','-filter:none','-verbose:class','-cp',cp,str(classes)])
    for source,targets in dependencies_from_jdeps(output).items():
        if not source.startswith(PREFIX):continue
        for target in targets:
            if not target.startswith(ALLOWED) or any(part in target for part in DENIED):
                raise GateFailure('storage bytecode outside inward boundary: '+source+' -> '+target)
    print('[storage-architecture] PASS: explicit sources and compiled Java 21; AIR/session only, no solver/values/consumer/adapter')
