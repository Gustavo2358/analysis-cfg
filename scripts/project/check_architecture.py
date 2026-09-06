#!/usr/bin/env python3
"""Build and inspect the narrow Java foundation with standard JDK/Maven evidence."""

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
PUBLICATION = "io.github.gustavo2358.air.model.Publication"
VALIDATION_RESULT = "io.github.gustavo2358.air.validation.ValidationResult"
AIR_VALIDATOR = "io.github.gustavo2358.air.validation.AirValidator"
PREFLIGHT_DESCRIPTOR = (
    "(Lio/github/gustavo2358/air/model/Publication;)"
    "Lio/github/gustavo2358/air/validation/ValidationResult;"
)
DEPENDENCY_PLUGIN = "org.apache.maven.plugins:maven-dependency-plugin:3.8.1"
EXPECTED_PRODUCTION_SOURCE = (
    "cfg-kernel/src/main/java/"
    "io/github/gustavo2358/analysis/cfg/application/CfgPreflight.java"
)
EXPECTED_JDEPS_TARGETS = {
    PUBLICATION,
    VALIDATION_RESULT,
    AIR_VALIDATOR,
    "java.lang.Object",
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
        "java.nio.file.Path",
        "com.fasterxml.jackson.databind.JsonNode",
        "com.google.gson.JsonObject",
        "io.proleap.cobol.CobolParser",
        "org.antlr.v4.runtime.Parser",
        "example.SemanticProductInput",
    }
    for dependency in forbidden_dependencies:
        try:
            exact(EXPECTED_JDEPS_TARGETS | {dependency}, EXPECTED_JDEPS_TARGETS,
                  "synthetic dependency")
        except GateFailure:
            continue
        raise GateConfigurationError(f"detector self-test accepted {dependency}")
    try:
        exact({PREFLIGHT_PATH + ".class", "local/Publication.class"},
              {PREFLIGHT_PATH + ".class"}, "synthetic class inventory")
    except GateFailure:
        return
    raise GateConfigurationError("detector self-test accepted a parallel model class")


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
    if modules != [KERNEL_ARTIFACT]:
        raise GateFailure("foundation reactor must contain exactly cfg-kernel")

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

    production_sources = sorted(
        path.relative_to(root).as_posix()
        for path in root.rglob("*.java")
        if "target" not in path.relative_to(root).parts
        and "/src/main/java/" in "/" + path.relative_to(root).as_posix()
    )
    if production_sources != [EXPECTED_PRODUCTION_SOURCE]:
        raise GateFailure(
            "foundation production source inventory must contain only CfgPreflight; observed "
            + repr(production_sources)
        )

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
    if workflow.count("MAVEN_OPTS: -Dmaven.repo.local=${{ runner.temp }}/analysis-cfg-m2") != 2:
        raise GateFailure("CI must share one isolated Maven repository across upstream and consumer")
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
    for report in reports:
        suite, _ = xml_root(report)
        try:
            total += int(suite.attrib.get("tests", "0"))
            skipped += int(suite.attrib.get("skipped", "0"))
            failures = int(suite.attrib.get("failures", "0"))
            errors = int(suite.attrib.get("errors", "0"))
        except ValueError as exc:
            raise GateConfigurationError(f"invalid Surefire counts in {report}") from exc
        if failures or errors:
            raise GateFailure(f"Surefire report contains failures/errors: {report}")
    if total - skipped <= 0:
        raise GateFailure("Maven completed with no non-skipped tests executed")
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
    relative_classes = sorted(path.relative_to(classes).as_posix() for path in classes.rglob("*.class"))
    expected = PREFLIGHT_PATH + ".class"
    if relative_classes != [expected]:
        raise GateFailure(
            "foundation production class inventory must contain only CfgPreflight; observed "
            + repr(relative_classes)
        )
    shadows_root = kernel / "target/test-classes/io/github/gustavo2358/air"
    shadows = sorted(path.name for path in shadows_root.rglob("*.class")) if shadows_root.exists() else []
    if shadows:
        raise GateFailure("test classes shadow the shared air-java package: " + ", ".join(shadows))
    classfile = classes / expected
    try:
        header = classfile.read_bytes()[:8]
        magic, minor, major = struct.unpack(">IHH", header)
    except (OSError, struct.error) as exc:
        raise GateConfigurationError(f"cannot read classfile header: {exc}") from exc
    if magic != 0xCAFEBABE or major != 65 or minor != 0:
        raise GateFailure(f"CfgPreflight bytecode must be Java 21/no preview; observed major={major}, minor={minor}")
    return classes


def verify_javap(javap: str, root: Path, classes: Path, air_jar: Path) -> None:
    classpath = os.pathsep.join((str(classes), str(air_jar)))
    output = run(
        [javap, "-classpath", classpath, "-verbose", "-c", "-p", "-s", PREFLIGHT_CLASS],
        root,
        capture=True,
    ).stdout or ""
    if "minor version: 0" not in output or "major version: 65" not in output:
        raise GateFailure("javap did not confirm Java 21 bytecode without preview")
    if "interfaces: 0, fields: 0, methods: 2" not in output:
        raise GateFailure("CfgPreflight must contain zero fields and exactly two methods")
    members = [line.strip() for line in re.findall(
        r"(?m)^  (?:public|protected|private).+;$", output
    )]
    expected_members = [
        f"private {PREFLIGHT_CLASS}();",
        f"public static {VALIDATION_RESULT} validate({PUBLICATION});",
    ]
    if members != expected_members:
        raise GateFailure(f"CfgPreflight member inventory drifted: {members}")
    direct = re.compile(
        r"public static .*? validate\(.*?Publication\);.*?"
        + r"descriptor:\s+" + re.escape(PREFLIGHT_DESCRIPTOR) + r".*?"
        + r"0:\s+aload_0\s+1:\s+invokestatic\s+#\d+\s+// Method "
        + re.escape(AIR_VALIDATOR.replace(".", "/") + ".validate:" + PREFLIGHT_DESCRIPTOR)
        + r"\s+4:\s+areturn",
        re.DOTALL,
    )
    if not direct.search(output):
        raise GateFailure("CfgPreflight is not an exact direct AirValidator delegation")


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
    targets = set(re.findall(
        rf"(?m)^\s+{re.escape(PREFLIGHT_CLASS)}\s+->\s+(\S+)",
        verbose,
    ))
    exact(targets, EXPECTED_JDEPS_TARGETS, "CfgPreflight bytecode dependencies")


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

    print(f"[architecture] PASS: {total} tests ({skipped} skipped), 1 production classfile, "
          f"Java class major 65/no preview (Maven runtime {runtime})", flush=True)
    print(f"[architecture] PASS: compile dependency {AIR_GROUP}:{AIR_ARTIFACT}:{AIR_VERSION} "
          "and JDK module java.base only", flush=True)
    print(f"[architecture] PASS: CI source pin {AIR_REPOSITORY}@{air_sha}", flush=True)
    print("[architecture] PASS: shared Publication/ValidationResult boundary delegates to AirValidator",
          flush=True)


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
