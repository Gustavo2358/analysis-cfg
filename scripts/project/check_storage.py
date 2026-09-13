#!/usr/bin/env python3
"""Storage campaign source/bytecode boundary; no source-language or solver rules in storage."""
import re
import struct
from pathlib import Path
from check_architecture import GateFailure
from check_w1 import command

PREFIX='io.github.gustavo2358.analysis.storage.'
NAMES=('StorageRange','StorageSubject','StorageIndex','StatementEffects','StoragePartition','SegmentMap')
STORAGE_SOURCES={f'analysis-kernel/src/main/java/io/github/gustavo2358/analysis/storage/{name}.java' for name in NAMES}
RD_PREFIX='io.github.gustavo2358.analysis.rd.'
RD_NAMES=('DefinitionEvent','DefinitionFact','ReachingDefinitions')
RD_SOURCES={f'analysis-kernel/src/main/java/io/github/gustavo2358/analysis/rd/{name}.java' for name in RD_NAMES}
SOURCES=STORAGE_SOURCES|RD_SOURCES
ALLOWED=('java.','io.github.gustavo2358.air.model.',PREFIX,'io.github.gustavo2358.analysis.structure.')
RD_ALLOWED=ALLOWED+(RD_PREFIX,'io.github.gustavo2358.analysis.solver.','io.github.gustavo2358.analysis.query.','io.github.gustavo2358.analysis.plan.','io.github.gustavo2358.analysis.cfg.domain.')
DENIED=('java.io.','java.nio.file.','java.net.','java.lang.reflect.','ServiceLoader','org.antlr.','cobolexplorer','lower.')

def verify_sources(root:Path):
    folder=root/'analysis-kernel/src/main/java/io/github/gustavo2358/analysis/storage'
    actual={p.relative_to(root).as_posix() for p in folder.rglob('*.java')}
    if actual!=STORAGE_SOURCES:raise GateFailure('storage source inventory drift')
    rd=root/'analysis-kernel/src/main/java/io/github/gustavo2358/analysis/rd'
    if {p.relative_to(root).as_posix() for p in rd.rglob('*.java')}!=RD_SOURCES:raise GateFailure('RD source inventory drift')
    for path in sorted(SOURCES):
        source=(root/path).read_text()
        for target in re.findall(r'^import (?:static )?([^;]+);',source,re.M):
            allowed=RD_ALLOWED if path in RD_SOURCES else ALLOWED
            if not target.startswith(allowed) or any(part in target for part in DENIED):
                raise GateFailure('storage import outside inward boundary: '+target)

def architecture(root:Path):
    verify_sources(root)
    from check_transport_architecture import dependencies_from_jdeps
    classes=root/'analysis-kernel/target/classes'
    cp=(root/'analysis-kernel/target/architecture-classpath.txt').read_text().strip()
    files=[p for pkg in ('storage','rd') for p in (classes/'io/github/gustavo2358/analysis'/pkg).rglob('*.class')]
    if not files:raise GateFailure('compiled storage classes missing')
    for file in files:
        if struct.unpack('>IHH',file.read_bytes()[:8])!=(0xcafebabe,0,65):raise GateFailure('storage Java 21/no-preview contract')
    output=command(root,['jdeps','--multi-release','21','-filter:none','-verbose:class','-cp',cp,str(classes)])
    for source,targets in dependencies_from_jdeps(output).items():
        if not source.startswith((PREFIX,RD_PREFIX)):continue
        allowed=RD_ALLOWED if source.startswith(RD_PREFIX) else ALLOWED
        for target in targets:
            if not target.startswith(allowed) or any(part in target for part in DENIED):
                raise GateFailure('storage bytecode outside inward boundary: '+source+' -> '+target)
    print('[storage-architecture] PASS: explicit sources and Java 21; storage -> AIR/session; RD -> storage/existing solver/query; no values/consumer/adapter')
