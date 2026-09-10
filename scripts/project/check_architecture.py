#!/usr/bin/env python3
"""Build and inspect the exact Java core CFG surface with standard JDK/Maven evidence."""

from __future__ import annotations

import argparse
import json
import os
import re
import shlex
import shutil
import struct
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path
from typing import Iterable, Optional, Sequence


AIR_GROUP = "io.github.gustavo2358"
AIR_ARTIFACT = "air-java"
AIR_VERSION = "0.1.0-SNAPSHOT"
AIR_REPOSITORY = "Gustavo2358/air-java"
KERNEL_ARTIFACT = "cfg-kernel"
PREFLIGHT_CLASS = "io.github.gustavo2358.analysis.cfg.application.CfgPreflight"
PREFLIGHT_PATH = PREFLIGHT_CLASS.replace(".", "/")
BUILD_CFG_CLASS = "io.github.gustavo2358.analysis.cfg.application.BuildCfg"
BUILD_CFG_PATH = BUILD_CFG_CLASS.replace(".", "/")
BUILD_OPTIONS_CLASS = "io.github.gustavo2358.analysis.cfg.application.BuildOptions"
BUILD_OPTIONS_PATH = BUILD_OPTIONS_CLASS.replace(".", "/")
BUILD_RESULT_CLASS = "io.github.gustavo2358.analysis.cfg.application.CfgBuildResult"
BUILD_RESULT_PATH = BUILD_RESULT_CLASS.replace(".", "/")
COORDINATOR_CLASS = "io.github.gustavo2358.analysis.cfg.application.CfgBuildCoordinator"
COORDINATOR_PATH = COORDINATOR_CLASS.replace(".", "/")
INTERPRETER_CLASS = "io.github.gustavo2358.analysis.cfg.extension.SemanticInterpreter"
INTERPRETER_PATH = INTERPRETER_CLASS.replace(".", "/")
REGISTRY_CLASS = "io.github.gustavo2358.analysis.cfg.extension.SemanticInterpreterRegistry"
REGISTRY_PATH = REGISTRY_CLASS.replace(".", "/")
PUBLICATION = "io.github.gustavo2358.air.model.Publication"
VALIDATION_RESULT = "io.github.gustavo2358.air.validation.ValidationResult"
VALIDATION_OPTIONS = "io.github.gustavo2358.air.validation.ValidationOptions"
AIR_VALIDATOR = "io.github.gustavo2358.air.validation.AirValidator"
PREFLIGHT_DESCRIPTOR = (
    "(Lio/github/gustavo2358/air/model/Publication;)"
    "Lio/github/gustavo2358/air/validation/ValidationResult;"
)
PREFLIGHT_OPTIONS_DESCRIPTOR = (
    "(Lio/github/gustavo2358/air/model/Publication;"
    "Lio/github/gustavo2358/air/validation/ValidationOptions;)"
    "Lio/github/gustavo2358/air/validation/ValidationResult;"
)
BUILD_DESCRIPTOR = (
    "(Lio/github/gustavo2358/air/model/Publication;"
    "Lio/github/gustavo2358/analysis/cfg/application/BuildOptions;)"
    "Lio/github/gustavo2358/analysis/cfg/application/CfgBuildResult;"
)
DEPENDENCY_PLUGIN = "org.apache.maven.plugins:maven-dependency-plugin:3.8.1"
DOMAIN_CLASS = "io.github.gustavo2358.analysis.cfg.domain."
SOURCE_ROOT = "cfg-kernel/src/main/java/io/github/gustavo2358/analysis/cfg/"
EXPECTED_PRODUCTION_IMPORTS = {
    SOURCE_ROOT + "application/BuildCfg.java": {PUBLICATION},
    SOURCE_ROOT + "application/BuildOptions.java": {
        VALIDATION_OPTIONS,
        DOMAIN_CLASS + "ProjectionPolicy",
        "java.util.Objects",
    },
    SOURCE_ROOT + "application/CfgBuildCoordinator.java": {
        "io.github.gustavo2358.air.model.Capabilities",
        PUBLICATION,
        "io.github.gustavo2358.air.validation.ValidationIssue",
        VALIDATION_RESULT,
        REGISTRY_CLASS,
        "java.util.Comparator",
        "java.util.List",
        "java.util.Objects",
    },
    SOURCE_ROOT + "application/CfgBuildResult.java": {
        "io.github.gustavo2358.air.model.Capabilities",
        "io.github.gustavo2358.air.model.Ids.PublicationId",
        "io.github.gustavo2358.air.model.SemanticVersion",
        VALIDATION_RESULT,
        "java.util.List",
        "java.util.Objects",
    },
    SOURCE_ROOT + "application/CfgPreflight.java": {
        PUBLICATION,
        AIR_VALIDATOR,
        VALIDATION_OPTIONS,
        VALIDATION_RESULT,
    },
    SOURCE_ROOT + "extension/SemanticInterpreter.java": {
        "io.github.gustavo2358.air.model.Capabilities",
    },
    SOURCE_ROOT + "extension/SemanticInterpreterRegistry.java": {
        "io.github.gustavo2358.air.model.Capabilities",
        "java.util.Collection",
        "java.util.Collections",
        "java.util.Comparator",
        "java.util.List",
        "java.util.Map",
        "java.util.Objects",
        "java.util.Optional",
        "java.util.TreeMap",
    },
}
# Deliberately enumerated core CFG additions; never discover/allow arbitrary sources.
EXPECTED_PRODUCTION_IMPORTS[SOURCE_ROOT + "application/CfgBuildCoordinator.java"].update({
    DOMAIN_CLASS + "CoreCfgProjection", DOMAIN_CLASS + "CfgGraph", DOMAIN_CLASS + "CfgProjectionIssue",
    "java.util.Optional",
})
EXPECTED_PRODUCTION_IMPORTS[SOURCE_ROOT + "application/CfgBuildResult.java"].update({
    DOMAIN_CLASS + "CfgGraph", DOMAIN_CLASS + "CfgProjectionIssue", "java.util.Optional",
})
EXPECTED_PRODUCTION_IMPORTS.update({
    SOURCE_ROOT + "domain/CfgNodeId.java": {
        "io.github.gustavo2358.air.model.Ids.PublicationId", "java.util.Objects",
    },
    SOURCE_ROOT + "domain/CfgNode.java": {
        "io.github.gustavo2358.air.model.Entries", "io.github.gustavo2358.air.model.Ids.EntryId",
        "io.github.gustavo2358.air.model.Ids.PublicationId", "io.github.gustavo2358.air.model.Ids.UnitId",
        "io.github.gustavo2358.air.model.Sequence", "java.util.Objects",
    },
    SOURCE_ROOT + "domain/CfgTransition.java": {
        "io.github.gustavo2358.air.model.Ids.EntryId", "java.util.Objects",
    },
    SOURCE_ROOT + "domain/CfgGraph.java": {
        "io.github.gustavo2358.air.model.Operations", PUBLICATION, "java.util.HashMap",
        "java.util.HashSet", "java.util.List", "java.util.Map", "java.util.Objects", "java.util.ArrayList",
    },
    SOURCE_ROOT + "domain/CfgProjectionIssue.java": {
        "io.github.gustavo2358.air.model.Ids.Id", "java.util.Objects",
    },
    SOURCE_ROOT + "domain/ProjectionPolicy.java": {"io.github.gustavo2358.air.model.Evidence"},
    SOURCE_ROOT + "domain/CoreCfgProjection.java": {
        "io.github.gustavo2358.air.model.Entries",
        "io.github.gustavo2358.air.model.Ids.LabelId", "io.github.gustavo2358.air.model.Operations",
        PUBLICATION, "io.github.gustavo2358.air.model.Sequence", "io.github.gustavo2358.air.model.Unit",
        "java.util.ArrayList", "java.util.Comparator", "java.util.HashMap", "java.util.List", "java.util.Map",
        "java.util.Objects",
    },
})
EXPECTED_PRODUCTION_IMPORTS[SOURCE_ROOT + "domain/CfgNode.java"].add(
    "io.github.gustavo2358.air.model.Operations")
EXPECTED_PRODUCTION_IMPORTS[SOURCE_ROOT + "domain/CfgGraph.java"].update({
    "io.github.gustavo2358.air.model.Capabilities", "io.github.gustavo2358.air.model.Ids.EntryId",
})
EXPECTED_PRODUCTION_IMPORTS[SOURCE_ROOT + "domain/CoreCfgProjection.java"].add(
    "io.github.gustavo2358.air.model.Capabilities")
CFG_CLASS_NAMES = {
    "CfgNodeId", "CfgNode", "CfgNode$EntryNode", "CfgNode$SequenceNode", "CfgNode$NormalExit", "CfgNode$HaltExit",
    "CfgTransition", "CfgTransition$Kind", "CfgGraph", "CfgGraph$1",
    "CfgProjectionIssue", "CfgProjectionIssue$Code", "CoreCfgProjection", "ProjectionPolicy",
}
EXPECTED_CLASSFILES = {
    BUILD_CFG_PATH + ".class",
    BUILD_OPTIONS_PATH + ".class",
    COORDINATOR_PATH + ".class",
    BUILD_RESULT_PATH + ".class",
    BUILD_RESULT_PATH + "$Status.class",
    PREFLIGHT_PATH + ".class",
    INTERPRETER_PATH + ".class",
    REGISTRY_PATH + ".class",
}
EXPECTED_CLASSFILES.update((DOMAIN_CLASS + name).replace(".", "/") + ".class" for name in CFG_CLASS_NAMES)
EXPECTED_TEST_CASES = {
    "io.github.gustavo2358.analysis.cfg.application.BuildCfgContractTest": 4,
    "io.github.gustavo2358.analysis.cfg.application.CfgBuildCoordinatorTest": 12,
    "io.github.gustavo2358.analysis.cfg.application.CfgPreflightTest": 4,
    "io.github.gustavo2358.analysis.cfg.extension.SemanticInterpreterRegistryTest": 4,
    DOMAIN_CLASS + "EvalCfg025Test": 17,
    DOMAIN_CLASS + "EvalCfg028Test": 22,
    DOMAIN_CLASS + "EvalCfg029Test": 25,
    DOMAIN_CLASS + "EvalCfg030Test": 20,
}
EXPECTED_PREFLIGHT_JDEPS_TARGETS = {
    PUBLICATION,
    VALIDATION_RESULT,
    VALIDATION_OPTIONS,
    AIR_VALIDATOR,
    "java.lang.Object",
}
ALLOWED_BYTECODE_PREFIXES = (
    "io.github.gustavo2358.air.model.",
    "io.github.gustavo2358.air.validation.",
    "io.github.gustavo2358.analysis.cfg.application.",
    "io.github.gustavo2358.analysis.cfg.extension.",
    DOMAIN_CLASS,
    "java.lang.",
    "java.util.",
)
FORBIDDEN_BYTECODE_PREFIXES = (
    "java.io.",
    "java.net.",
    "java.nio.",
    "java.lang.reflect.",
    "java.util.ServiceLoader",
)
FORBIDDEN_BYTECODE_TYPES = {
    "java.lang.ClassLoader",
    "java.lang.System",
}
# Exact inventory for the authorized structural slice; no wildcard operation support.
ALLOWED_OPERATION_TYPES = {
    "io.github.gustavo2358.air.model.Operations$Return",
    "io.github.gustavo2358.air.model.Operations$Jump",
    "io.github.gustavo2358.air.model.Operations$Branch",
    "io.github.gustavo2358.air.model.Operations$Halt",
    "io.github.gustavo2358.air.model.Operations$HaltKind",
    "io.github.gustavo2358.air.model.Operations$Assign",
    "io.github.gustavo2358.air.model.Operations$HavocMust",
    "io.github.gustavo2358.air.model.Operations$HavocMay",
    "io.github.gustavo2358.air.model.Operations$Nop",
    "io.github.gustavo2358.air.model.Operations$CopyBytes",
    "io.github.gustavo2358.air.model.Operations$Header",
}
IMPORT_PATTERN = re.compile(
    r"(?m)^\s*import\s+(?:static\s+)?([A-Za-z_$][\w$]*(?:\.[\w$*]+)+)\s*;"
)
FORBIDDEN_SOURCE_PATTERNS = {
    "reflection/discovery": re.compile(
        r"\b(?:Class\.forName|ServiceLoader|java\.lang\.reflect|"
        r"getDeclaredConstructor|getDeclaredMethod|getDeclaredMethods)\b"
    ),
    "process/environment": re.compile(r"\bSystem\.(?:exit|getenv|getProperties|getProperty)\b"),
}


class GateFailure(RuntimeError):
    """An architectural assertion was falsified."""


class GateConfigurationError(RuntimeError):
    """The gate could not obtain trustworthy evidence."""


def exact(observed: Iterable[str], expected: Iterable[str], label: str) -> None:
    actual = set(observed)
    wanted = set(expected)
    if actual != wanted:
        raise GateFailure(
            f"{label} mismatch; expected {sorted(wanted)}, observed {sorted(actual)}"
        )


def detector_self_test() -> None:
    forbidden_dependencies = {
        "io.github.gustavo2358.air.json.AirJson",
        "io.github.gustavo2358.analysis.cfg.adapters.AirJsonFileReader",
        "java.io.File",
        "java.net.URI",
        "java.nio.file.Path",
        "java.lang.reflect.Method",
        "java.util.ServiceLoader",
        "com.fasterxml.jackson.databind.JsonNode",
        "com.google.gson.JsonObject",
        "io.proleap.cobol.CobolParser",
        "org.antlr.v4.runtime.Parser",
        "org.springframework.context.ApplicationContext",
        "picocli.CommandLine",
        "example.SemanticProductInput",
        "local.BuildCfgInput",
        "io.github.gustavo2358.air.model.Operations$Dispatch",
        "io.github.gustavo2358.air.model.Operations$Invoke",
        "io.github.gustavo2358.air.model.Operations$Raise",
        "io.github.gustavo2358.air.model.Operations$Opaque",
        "io.github.gustavo2358.air.model.Operations$LocalInvoke",
        "io.github.gustavo2358.air.model.Operations$LocalBoundary",
        "io.github.gustavo2358.air.model.Operations$LocalResume",
        "io.github.gustavo2358.air.model.Operations$LocalUnwind",
        "io.github.gustavo2358.air.model.Operations$IndirectJump",
    }
    verify_bytecode_dependencies({DOMAIN_CLASS + "CoreCfgProjection": {
        "io.github.gustavo2358.air.model.Operations$Branch"}})
    for dependency in forbidden_dependencies:
        try:
            verify_bytecode_dependencies({BUILD_CFG_CLASS: {PUBLICATION, dependency}})
        except GateFailure:
            pass
        else:
            raise GateConfigurationError(f"bytecode detector self-test accepted {dependency}")
    try:
        verify_bytecode_dependencies({DOMAIN_CLASS + "CfgGraph": {BUILD_CFG_CLASS}})
    except GateFailure:
        pass
    else:
        raise GateConfigurationError("detector accepted domain -> application")
    try:
        exact(EXPECTED_CLASSFILES | {"local/Publication.class"},
              EXPECTED_CLASSFILES, "synthetic class inventory")
    except GateFailure:
        pass
    else:
        raise GateConfigurationError("detector self-test accepted a parallel model class")
    try:
        require_descriptor(
            "descriptor: (Llocal/BuildCfgInput;)L" + BUILD_RESULT_PATH + ";",
            BUILD_DESCRIPTOR,
            "synthetic BuildCfg port",
        )
    except GateFailure:
        pass
    else:
        raise GateConfigurationError("detector self-test accepted a local BuildCfg DTO")


def require_descriptor(output: str, descriptor: str, label: str) -> None:
    if "descriptor: " + descriptor not in output:
        raise GateFailure(f"{label} descriptor mismatch; expected {descriptor}")


def verify_bytecode_dependencies(observed: dict[str, set[str]]) -> None:
    for source, targets in observed.items():
        for target in targets:
            if ((target.startswith("io.github.gustavo2358.air.model.Operations$")
                     and target not in ALLOWED_OPERATION_TYPES)
                    or (source.startswith(DOMAIN_CLASS) and target.startswith((
                        "io.github.gustavo2358.analysis.cfg.application.",
                        "io.github.gustavo2358.analysis.cfg.extension.")))
                    or target in FORBIDDEN_BYTECODE_TYPES
                    or target.startswith(FORBIDDEN_BYTECODE_PREFIXES)
                    or not target.startswith(ALLOWED_BYTECODE_PREFIXES)):
                raise GateFailure(f"forbidden bytecode dependency {source} -> {target}")

def command_path(name: str) -> str:
    candidate = shutil.which(name)
    if candidate is None:
        raise GateConfigurationError(f"required command not found on PATH: {name}")
    return candidate


def local_repository_argument() -> list[str]:
    selected: Optional[str] = None
    for variable in ("MAVEN_OPTS", "MAVEN_ARGS"):
        try:
            tokens = shlex.split(os.environ.get(variable, ""))
        except ValueError as exc:
            raise GateConfigurationError(f"cannot parse {variable}: {exc}") from exc
        for index, token in enumerate(tokens):
            if token.startswith("-Dmaven.repo.local="):
                selected = token
            elif token == "-Dmaven.repo.local" and index + 1 < len(tokens):
                selected = token + "=" + tokens[index + 1]
    if selected == "-Dmaven.repo.local=":
        raise GateConfigurationError("maven.repo.local override is empty")
    return [selected] if selected else []


def run(command: Sequence[str], root: Path, *, capture: bool = False) -> subprocess.CompletedProcess[str]:
    display = shlex.join(command)
    print(f"[architecture] RUN: {display}", flush=True)
    completed = subprocess.run(
        list(command),
        cwd=root,
        text=True,
        stdout=subprocess.PIPE if capture else None,
        stderr=subprocess.STDOUT if capture else None,
        check=False,
    )
    if capture and completed.stdout:
        print(completed.stdout, end="" if completed.stdout.endswith("\n") else "\n", flush=True)
    if completed.returncode:
        raise GateFailure(f"command failed with exit {completed.returncode}: {display}")
    return completed


def xml_root(path: Path) -> tuple[ET.Element, str]:
    try:
        root = ET.parse(path).getroot()
    except (OSError, ET.ParseError) as exc:
        raise GateConfigurationError(f"invalid XML {path}: {exc}") from exc
    namespace = root.tag.partition("}")[0] + "}" if root.tag.startswith("{") else ""
    return root, namespace


def child_text(element: ET.Element, namespace: str, name: str, default: str = "") -> str:
    child = element.find(namespace + name)
    return child.text.strip() if child is not None and child.text else default


def verify_project_shape(root: Path) -> None:
    project, namespace = xml_root(root / "pom.xml")
    modules_element = project.find(namespace + "modules")
    modules = [] if modules_element is None else [
        item.text.strip()
        for item in modules_element.findall(namespace + "module")
        if item.text and item.text.strip()
    ]
    if modules != [KERNEL_ARTIFACT, "analysis-kernel", "analysis-values", "cfg-adapters", "cfg-launcher", "analysis-dataflow", "analysis-adapters", "analysis-launcher"]:
        raise GateFailure("W3 reactor must contain exactly cfg-kernel, analysis-kernel, analysis-values, cfg-adapters, cfg-launcher")

    properties = project.find(namespace + "properties")
    release = None if properties is None else properties.find(namespace + "maven.compiler.release")
    if release is None or release.text is None or release.text.strip() != "21":
        raise GateFailure("maven.compiler.release must be exactly 21")

    kernel, kernel_namespace = xml_root(root / KERNEL_ARTIFACT / "pom.xml")
    dependencies = kernel.find(kernel_namespace + "dependencies")
    declared: list[tuple[str, str, str]] = []
    if dependencies is not None:
        for dependency in dependencies.findall(kernel_namespace + "dependency"):
            declared.append((
                child_text(dependency, kernel_namespace, "groupId"),
                child_text(dependency, kernel_namespace, "artifactId"),
                child_text(dependency, kernel_namespace, "scope", "compile"),
            ))
    expected = [
        (AIR_GROUP, AIR_ARTIFACT, "compile"),
        ("org.junit.jupiter", "junit-jupiter", "test"),
    ]
    if declared != expected:
        raise GateFailure("cfg-kernel dependencies must be exactly compile air-java and test JUnit")

    production_paths = sorted(
        path
        for path in root.rglob("*.java")
        if "target" not in path.relative_to(root).parts
        and "/src/main/java/" in "/" + path.relative_to(root).as_posix()
    )
    production_sources = {path.relative_to(root).as_posix() for path in production_paths}
    from check_transport_architecture import transport_source_inventory, verify_transport_shape
    from check_w1 import source_inventory, verify_sources
    from check_w2 import SOURCES as SOLVER_SOURCES, verify_sources as verify_solver_sources
    from check_w3 import QUERY_SOURCES, VALUE_SOURCES, verify_sources as verify_value_sources
    from check_w4 import SOURCES as PLANNING_SOURCES, verify_sources as verify_planning_sources
    from check_w5 import SOURCES as COMPOSITION_SOURCES, verify_sources as verify_composition_sources
    verify_composition_sources(root)
    analysis_sources = COMPOSITION_SOURCES | source_inventory(root) | SOLVER_SOURCES | QUERY_SOURCES | VALUE_SOURCES | PLANNING_SOURCES
    verify_planning_sources(root)
    verify_value_sources(root)
    verify_solver_sources(root)
    verify_sources(root)
    transport_sources = transport_source_inventory(root)
    verify_transport_shape(root)
    if production_sources != set(EXPECTED_PRODUCTION_IMPORTS) | transport_sources | analysis_sources:
        raise GateFailure(
            "production source inventory mismatch; expected "
            + repr(sorted(EXPECTED_PRODUCTION_IMPORTS))
            + ", observed " + repr(sorted(production_sources))
        )
    for path in production_paths:
        relative = path.relative_to(root).as_posix()
        if relative in transport_sources | analysis_sources:
            continue  # separately inventoried and verified at the permitted outer boundary
        source = path.read_text(encoding="utf-8")
        imports = IMPORT_PATTERN.findall(source)
        exact(imports, EXPECTED_PRODUCTION_IMPORTS[relative], relative + " imports")
        for label, pattern in FORBIDDEN_SOURCE_PATTERNS.items():
            if pattern.search(source):
                raise GateFailure(f"{relative} contains forbidden {label}")

    preview_files = list(root.glob("**/pom.xml")) + [
        root / ".mvn/jvm.config",
        root / ".mvn/maven.config",
    ]
    configured = [
        path.relative_to(root).as_posix()
        for path in preview_files
        if path.is_file() and "--enable-preview" in path.read_text(encoding="utf-8")
    ]
    if configured:
        raise GateFailure("preview is configured in: " + ", ".join(configured))


def verify_snapshot_pin(root: Path) -> str:
    try:
        lock = json.loads((root / "docs/sources/sources.lock.json").read_text(encoding="utf-8"))
        air = lock["air_java"]
    except (OSError, json.JSONDecodeError, KeyError, TypeError) as exc:
        raise GateConfigurationError(f"cannot read air-java source lock: {exc}") from exc
    sha = air.get("commit", "")
    expected_coordinates = {
        "group_id": AIR_GROUP,
        "artifact_id": AIR_ARTIFACT,
        "version": AIR_VERSION,
    }
    if (not re.fullmatch(r"[0-9a-f]{40}", sha)
            or air.get("repository") != AIR_REPOSITORY
            or air.get("maven") != expected_coordinates):
        raise GateFailure("air-java source lock disagrees with the kernel dependency")

    try:
        workflow = (root / ".github/workflows/ci.yml").read_text(encoding="utf-8")
    except OSError as exc:
        raise GateConfigurationError(f"cannot read CI workflow: {exc}") from exc
    repositories = re.findall(r"(?m)^\s*repository:\s*([^\s#]+)", workflow)
    refs = re.findall(r"(?m)^\s*ref:\s*([^\s#]+)", workflow)
    if repositories.count(AIR_REPOSITORY) != 1 or refs.count(sha) != 1:
        raise GateFailure("CI must check out air-java exactly once at the source-lock SHA")
    if re.search(r"(?m)^\s*ref:\s*(?:main|master)\s*$", workflow):
        raise GateFailure("CI must not resolve air-java from a mutable branch")
    if f'test "$(git rev-parse HEAD)" = "{sha}"' not in workflow:
        raise GateFailure("CI must verify air-java HEAD before installation")
    if workflow.count("MAVEN_OPTS: -Dmaven.repo.local=${{ runner.temp }}/analysis-cfg-m2") != 11:
        raise GateFailure("CI must share one isolated Maven repository across upstream and consumer")
    if 'scripts/harness/check-full.sh' not in workflow or 'scripts/project/record_air_dependency.py' not in workflow:
        raise GateFailure("CI must run full regression and record exact upstream tree/JAR provenance")
    for wave in (1, 2, 3, 4, 5):
        if f"scripts/project/check_cp5_gate.py performance --wave {wave}" not in workflow:
            raise GateFailure(f"CI must execute CP5 Wave {wave} product probes")
    if 'distribution: temurin' not in workflow or 'java-version: "21"' not in workflow:
        raise GateFailure("CI must use Temurin 21")
    for gate in ("fast", "architecture", "semantic", "integration"):
        if f"bash scripts/harness/check-{gate}.sh" not in workflow:
            raise GateFailure("CI must execute " + gate)
    return sha


def assert_maven_runtime(maven: str, root: Path, repository: list[str]) -> str:
    result = run([maven, *repository, "--version"], root, capture=True)
    match = re.search(r"^Java version:\s*([^,\s]+)", result.stdout or "", re.MULTILINE)
    if not match:
        raise GateConfigurationError("could not determine Maven Java runtime")
    version = match.group(1)
    major_text = version.split(".", 1)[0]
    if major_text == "1":
        major_text = version.split(".", 2)[1]
    major_match = re.match(r"\d+", major_text)
    if not major_match:
        raise GateConfigurationError(f"cannot parse Java runtime version: {version}")
    if int(major_match.group(0)) < 21:
        raise GateFailure(f"Maven must run on Java 21 or newer, observed {version}")
    return version


def count_tests(kernel: Path) -> tuple[int, int]:
    reports = sorted((kernel / "target/surefire-reports").glob("TEST-*.xml"))
    if not reports:
        raise GateFailure("no Surefire XML reports were produced")
    total = skipped = 0
    observed: dict[str, int] = {}
    for report in reports:
        suite, _ = xml_root(report)
        try:
            suite_total = int(suite.attrib.get("tests", "0"))
            suite_skipped = int(suite.attrib.get("skipped", "0"))
            total += suite_total
            skipped += suite_skipped
            failures = int(suite.attrib.get("failures", "0"))
            errors = int(suite.attrib.get("errors", "0"))
        except ValueError as exc:
            raise GateConfigurationError(f"invalid Surefire counts in {report}") from exc
        if failures or errors:
            raise GateFailure(f"Surefire report contains failures/errors: {report}")
        suite_name = suite.attrib.get("name", "")
        if not suite_name or suite_name in observed:
            raise GateFailure(f"invalid/duplicate Surefire suite: {suite_name!r}")
        observed[suite_name] = suite_total
    if total - skipped <= 0:
        raise GateFailure("Maven completed with no non-skipped tests executed")
    if skipped:
        raise GateFailure(f"required architecture tests cannot be skipped; observed {skipped}")
    if observed != EXPECTED_TEST_CASES:
        raise GateFailure(
            f"required test inventory mismatch; expected {EXPECTED_TEST_CASES}, observed {observed}")
    return total, skipped


def parse_tgf(path: Path) -> list[str]:
    if not path.is_file():
        raise GateConfigurationError("Maven did not produce dependency-tree evidence")
    coordinates: list[str] = []
    for line in path.read_text(encoding="utf-8").splitlines():
        if line.strip() == "#":
            break
        match = re.match(r"^\d+\s+(.+)$", line.strip())
        if match:
            coordinates.append(match.group(1).strip())
    return coordinates


def verify_dependency_tree(coordinates: Sequence[str]) -> None:
    dependencies = [item for item in coordinates if f":{KERNEL_ARTIFACT}:" not in item]
    expected = f"{AIR_GROUP}:{AIR_ARTIFACT}:jar:{AIR_VERSION}:compile"
    if dependencies != [expected]:
        raise GateFailure(f"kernel compile dependency must be exactly {expected}; observed {dependencies}")


def read_air_classpath(path: Path) -> Path:
    if not path.is_file():
        raise GateConfigurationError("Maven did not produce compile classpath evidence")
    entries = [Path(item).resolve() for item in path.read_text(encoding="utf-8").strip().split(os.pathsep) if item]
    if len(entries) != 1 or not entries[0].is_file() or entries[0].suffix != ".jar":
        raise GateFailure(f"kernel compile classpath must contain one JAR; observed {entries}")
    artifact = entries[0]
    try:
        with zipfile.ZipFile(artifact) as jar:
            members = set(jar.namelist())
    except (OSError, zipfile.BadZipFile) as exc:
        raise GateConfigurationError(f"cannot inspect air-java JAR: {exc}") from exc
    required = {
        PUBLICATION.replace(".", "/") + ".class",
        VALIDATION_RESULT.replace(".", "/") + ".class",
        AIR_VALIDATOR.replace(".", "/") + ".class",
    }
    if not required <= members:
        raise GateFailure("resolved JAR is missing required air-java classes")
    return artifact


def verify_class_inventory(kernel: Path) -> Path:
    classes = kernel / "target/classes"
    relative_classes = {path.relative_to(classes).as_posix() for path in classes.rglob("*.class")}
    if relative_classes != EXPECTED_CLASSFILES:
        raise GateFailure(
            "production class inventory mismatch; expected "
            + repr(sorted(EXPECTED_CLASSFILES))
            + ", observed " + repr(sorted(relative_classes))
        )
    shadows_root = kernel / "target/test-classes/io/github/gustavo2358/air"
    shadows = sorted(path.name for path in shadows_root.rglob("*.class")) if shadows_root.exists() else []
    if shadows:
        raise GateFailure("test classes shadow the shared air-java package: " + ", ".join(shadows))
    for relative in sorted(relative_classes):
        try:
            header = (classes / relative).read_bytes()[:8]
            magic, minor, major = struct.unpack(">IHH", header)
        except (OSError, struct.error) as exc:
            raise GateConfigurationError(f"cannot read classfile header: {relative}: {exc}") from exc
        if magic != 0xCAFEBABE or major != 65 or minor != 0:
            raise GateFailure(
                f"{relative} must be Java 21/no preview; observed major={major}, minor={minor}")
    return classes


def verify_javap(javap: str, root: Path, classes: Path, air_jar: Path) -> None:
    classpath = os.pathsep.join((str(classes), str(air_jar)))
    port = run(
        [javap, "-classpath", classpath, "-verbose", "-p", "-s", BUILD_CFG_CLASS],
        root,
        capture=True,
    ).stdout or ""
    if "minor version: 0" not in port or "major version: 65" not in port:
        raise GateFailure("javap did not confirm BuildCfg Java 21 bytecode without preview")
    if "public interface " + BUILD_CFG_CLASS not in port or "interfaces: 0, fields: 0, methods: 1" not in port:
        raise GateFailure("BuildCfg must be a single-method public input port")
    require_descriptor(port, BUILD_DESCRIPTOR, "BuildCfg")

    coordinator = run(
        [javap, "-classpath", classpath, "-p", "-s", COORDINATOR_CLASS],
        root,
        capture=True,
    ).stdout or ""
    if "implements " + BUILD_CFG_CLASS not in coordinator:
        raise GateFailure("CfgBuildCoordinator must implement BuildCfg")
    require_descriptor(coordinator, BUILD_DESCRIPTOR, "CfgBuildCoordinator.build")

    interpreter = run(
        [javap, "-classpath", classpath, "-p", "-s", INTERPRETER_CLASS],
        root,
        capture=True,
    ).stdout or ""
    if "public interface " + INTERPRETER_CLASS not in interpreter:
        raise GateFailure("SemanticInterpreter must remain an explicit interface")
    require_descriptor(
        interpreter,
        "()Lio/github/gustavo2358/air/model/Capabilities$Capability;",
        "SemanticInterpreter.capability",
    )

    registry = run(
        [javap, "-classpath", classpath, "-p", "-s", REGISTRY_CLASS],
        root,
        capture=True,
    ).stdout or ""
    require_descriptor(
        registry,
        "(Lio/github/gustavo2358/air/model/Capabilities$Capability;)Ljava/util/Optional;",
        "SemanticInterpreterRegistry.find",
    )

    result = run(
        [javap, "-classpath", classpath, "-p", "-s", BUILD_RESULT_CLASS],
        root,
        capture=True,
    ).stdout or ""
    require_descriptor(result, "()Ljava/util/Optional;", "CfgBuildResult.graph")
    if "java.util.Optional<" + DOMAIN_CLASS + "CfgGraph> graph()" not in result:
        raise GateFailure("CfgBuildResult must expose the real typed optional CFG product")

    halt = run(
        [javap, "-classpath", classpath, "-p", "-s", DOMAIN_CLASS + "CfgNode$HaltExit"],
        root, capture=True,
    ).stdout or ""
    require_descriptor(halt, "()Lio/github/gustavo2358/air/model/Operations$Halt;", "HaltExit.source")

    graph = run(
        [javap, "-classpath", classpath, "-p", "-s", DOMAIN_CLASS + "CfgGraph"],
        root, capture=True,
    ).stdout or ""
    for role, method in (("EntryNode", "entries"), ("NormalExit", "normalExits"), ("HaltExit", "haltExits")):
        if "java.util.List<" + DOMAIN_CLASS + "CfgNode$" + role + "> " + method + "()" not in graph:
            raise GateFailure("CfgGraph must expose its typed " + method + " inventory")
    if "java.util.List<io.github.gustavo2358.air.model.Capabilities$Capability> preciseControlCapabilities()" not in graph:
        raise GateFailure("CfgGraph must declare capability consumption scoped to precise control")

    preflight = run(
        [javap, "-classpath", classpath, "-verbose", "-c", "-p", "-s", PREFLIGHT_CLASS],
        root,
        capture=True,
    ).stdout or ""
    for descriptor in (PREFLIGHT_DESCRIPTOR, PREFLIGHT_OPTIONS_DESCRIPTOR):
        require_descriptor(preflight, descriptor, "CfgPreflight.validate")
        invocation = AIR_VALIDATOR.replace(".", "/") + ".validate:" + descriptor
        if invocation not in preflight:
            raise GateFailure("CfgPreflight is not a direct AirValidator delegation for " + descriptor)


def verify_jdeps(jdeps: str, root: Path, classes: Path, air_jar: Path) -> None:
    modules = run(
        [jdeps, "--multi-release", "21", "--ignore-missing-deps", "--print-module-deps",
         "--class-path", str(air_jar), str(classes)],
        root,
        capture=True,
    ).stdout or ""
    exact((item.strip() for item in modules.strip().split(",") if item.strip()), {"java.base"}, "JDK modules")

    verbose = run(
        [jdeps, "--multi-release", "21", "--ignore-missing-deps", "-verbose:class",
         "--class-path", str(air_jar), str(classes)],
        root,
        capture=True,
    ).stdout or ""
    dependencies: dict[str, set[str]] = {}
    for source, target in re.findall(
            r"(?m)^\s+(io\.github\.gustavo2358\.analysis\.cfg\.\S+)\s+->\s+(\S+)",
            verbose):
        dependencies.setdefault(source, set()).add(target)
    expected_sources = {
        BUILD_CFG_CLASS,
        BUILD_OPTIONS_CLASS,
        COORDINATOR_CLASS,
        BUILD_RESULT_CLASS,
        BUILD_RESULT_CLASS + "$Status",
        PREFLIGHT_CLASS,
        INTERPRETER_CLASS,
        REGISTRY_CLASS,
    }
    expected_sources.update(DOMAIN_CLASS + name for name in CFG_CLASS_NAMES)
    if set(dependencies) != expected_sources:
        raise GateFailure(
            f"jdeps production class inventory mismatch; expected {sorted(expected_sources)}, "
            f"observed {sorted(dependencies)}")
    verify_bytecode_dependencies(dependencies)
    exact(
        dependencies[PREFLIGHT_CLASS],
        EXPECTED_PREFLIGHT_JDEPS_TARGETS,
        "CfgPreflight bytecode dependencies",
    )


def architecture_gate(root: Path) -> None:
    detector_self_test()
    verify_project_shape(root)
    air_sha = verify_snapshot_pin(root)
    maven = command_path("mvn")
    javap = command_path("javap")
    jdeps = command_path("jdeps")
    repository = local_repository_argument()
    runtime = assert_maven_runtime(maven, root, repository)
    run([maven, *repository, "--batch-mode", "--no-transfer-progress", "clean", "test"], root)
    kernel = root / KERNEL_ARTIFACT
    total, skipped = count_tests(kernel)

    with tempfile.TemporaryDirectory(prefix="analysis-cfg-architecture-") as temporary:
        evidence = Path(temporary)
        tree = evidence / "compile-dependencies.tgf"
        classpath = evidence / "compile-classpath.txt"
        common = [maven, *repository, "--batch-mode", "--no-transfer-progress", "-pl", f":{KERNEL_ARTIFACT}"]
        run([*common, f"{DEPENDENCY_PLUGIN}:tree", "-Dscope=compile", "-DoutputType=tgf",
             f"-DoutputFile={tree}"], root)
        verify_dependency_tree(parse_tgf(tree))
        run([*common, f"{DEPENDENCY_PLUGIN}:build-classpath", "-DincludeScope=compile",
             f"-Dmdep.outputFile={classpath}"], root)
        air_jar = read_air_classpath(classpath)
        classes = verify_class_inventory(kernel)
        verify_javap(javap, root, classes, air_jar)
        verify_jdeps(jdeps, root, classes, air_jar)

    from check_transport_architecture import transport_gate
    transport_gate(root, maven, repository, javap, jdeps, air_jar)
    from check_w1 import architecture
    architecture(root)
    from check_w2 import architecture as solver_architecture
    solver_architecture(root)
    from check_w3 import architecture as values_architecture
    values_architecture(root)
    from check_w4 import architecture as planning_architecture
    planning_architecture(root)
    from check_w5 import architecture as composition_architecture
    composition_architecture(root)

    print(f"[architecture] PASS: {total} kernel tests ({skipped} skipped), "
          f"{len(EXPECTED_CLASSFILES)} production classfiles, "
          f"Java class major 65/no preview (Maven runtime {runtime})", flush=True)
    print(f"[architecture] PASS: compile dependency {AIR_GROUP}:{AIR_ARTIFACT}:{AIR_VERSION} "
          "and JDK module java.base only", flush=True)
    print(f"[architecture] PASS: CI source pin {AIR_REPOSITORY}@{air_sha}", flush=True)
    print("[architecture] PASS: BuildCfg(Publication, BuildOptions) -> CfgBuildResult and direct "
          "AirValidator preflight", flush=True)
    print("[architecture] PASS: explicit capability/version registry; no transport, reflection, "
          "frontend, AIR shadow, or control primitives beyond Jump/Branch/Return/Halt", flush=True)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    try:
        if args.self_test:
            detector_self_test()
            print("[architecture] PASS: detector fixtures", flush=True)
        else:
            architecture_gate(args.root.resolve())
        return 0
    except GateFailure as exc:
        print(f"[architecture] FAIL: {exc}", file=sys.stderr, flush=True)
        return 1
    except (GateConfigurationError, OSError) as exc:
        print(f"[architecture] ERROR: {exc}", file=sys.stderr, flush=True)
        return 2


if __name__ == "__main__":
    sys.exit(main())
