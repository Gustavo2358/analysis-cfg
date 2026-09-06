#!/usr/bin/env python3
"""Build and inspect the Java kernel architecture without loading product classes."""

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
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable, Optional, Sequence


AIR_GROUP = "io.github.gustavo2358"
AIR_ARTIFACT = "air-java"
AIR_VERSION = "0.1.0-SNAPSHOT"
KERNEL_ARTIFACT = "cfg-kernel"
JUNIT_GROUP = "org.junit.jupiter"
JUNIT_ARTIFACT = "junit-jupiter"
KERNEL_PACKAGE = "io/github/gustavo2358/analysis/cfg/"
PREFLIGHT_CLASS = KERNEL_PACKAGE + "application/CfgPreflight"
PUBLICATION = "io/github/gustavo2358/air/model/Publication"
VALIDATION_RESULT = "io/github/gustavo2358/air/validation/ValidationResult"
AIR_VALIDATOR = "io/github/gustavo2358/air/validation/AirValidator"
PREFLIGHT_DESCRIPTOR = f"(L{PUBLICATION};)L{VALIDATION_RESULT};"
DEPENDENCY_PLUGIN = "org.apache.maven.plugins:maven-dependency-plugin:3.8.1"
AIR_REPOSITORY = "Gustavo2358/air-java"
AIR_SHARED_SIMPLE_NAMES = {
    "Publication",
    "Unit",
    "Entry",
    "Sequence",
    "Operation",
    "Instruction",
    "Terminator",
    "TypeRef",
    "Premise",
    "DomainProofScope",
    "AirValidator",
}

ACC_PUBLIC = 0x0001
ACC_PRIVATE = 0x0002
ACC_STATIC = 0x0008


class GateFailure(RuntimeError):
    """An architectural assertion was falsified."""


class GateConfigurationError(RuntimeError):
    """The gate could not obtain trustworthy evidence."""


@dataclass(frozen=True)
class MethodInfo:
    access: int
    name: str
    descriptor: str


@dataclass(frozen=True)
class ClassEvidence:
    path: Path
    minor: int
    major: int
    name: str
    referenced_classes: frozenset[str]
    descriptor_classes: frozenset[str]
    methods: tuple[MethodInfo, ...]

    @property
    def all_references(self) -> frozenset[str]:
        return self.referenced_classes | self.descriptor_classes


class ClassReader:
    """Small, strict classfile reader for names, descriptors and version evidence."""

    def __init__(self, data: bytes, path: Path):
        self.data = data
        self.path = path
        self.offset = 0

    def take(self, size: int) -> bytes:
        end = self.offset + size
        if end > len(self.data):
            raise GateConfigurationError(f"truncated classfile: {self.path}")
        value = self.data[self.offset:end]
        self.offset = end
        return value

    def u1(self) -> int:
        return self.take(1)[0]

    def u2(self) -> int:
        return struct.unpack(">H", self.take(2))[0]

    def u4(self) -> int:
        return struct.unpack(">I", self.take(4))[0]

    def skip_attributes(self, count: int) -> None:
        for _ in range(count):
            self.u2()
            self.take(self.u4())


def descriptor_names(values: Iterable[str]) -> frozenset[str]:
    names: set[str] = set()
    for value in values:
        # Covers field/method descriptors, generic Signature attributes and annotations.
        names.update(re.findall(r"L([A-Za-z_$][A-Za-z0-9_$/]*(?:\$[A-Za-z0-9_$]+)?)", value))
    return frozenset(names)


def read_class(path: Path) -> ClassEvidence:
    reader = ClassReader(path.read_bytes(), path)
    if reader.take(4) != b"\xca\xfe\xba\xbe":
        raise GateConfigurationError(f"invalid classfile magic: {path}")
    minor = reader.u2()
    major = reader.u2()
    pool_count = reader.u2()
    pool: list[tuple[int, object] | None] = [None] * pool_count
    index = 1
    while index < pool_count:
        tag = reader.u1()
        if tag == 1:  # CONSTANT_Utf8
            raw = reader.take(reader.u2())
            try:
                pool[index] = (tag, raw.decode("utf-8"))
            except UnicodeDecodeError as exc:
                raise GateConfigurationError(f"invalid UTF-8 in classfile {path}: {exc}") from exc
        elif tag in (3, 4):
            reader.take(4)
            pool[index] = (tag, None)
        elif tag in (5, 6):
            reader.take(8)
            pool[index] = (tag, None)
            index += 1
        elif tag in (7, 8, 16, 19, 20):
            pool[index] = (tag, reader.u2())
        elif tag in (9, 10, 11, 12, 17, 18):
            pool[index] = (tag, (reader.u2(), reader.u2()))
        elif tag == 15:
            pool[index] = (tag, (reader.u1(), reader.u2()))
        else:
            raise GateConfigurationError(f"unsupported constant-pool tag {tag} in {path}")
        index += 1

    def utf8(pool_index: int) -> str:
        try:
            item = pool[pool_index]
        except IndexError as exc:
            raise GateConfigurationError(f"bad constant-pool index in {path}") from exc
        if item is None or item[0] != 1 or not isinstance(item[1], str):
            raise GateConfigurationError(f"expected CONSTANT_Utf8 in {path}")
        return item[1]

    def class_name(pool_index: int) -> str:
        try:
            item = pool[pool_index]
        except IndexError as exc:
            raise GateConfigurationError(f"bad class index in {path}") from exc
        if item is None or item[0] != 7 or not isinstance(item[1], int):
            raise GateConfigurationError(f"expected CONSTANT_Class in {path}")
        return utf8(item[1])

    reader.u2()  # access_flags
    this_class = reader.u2()
    reader.u2()  # super_class
    for _ in range(reader.u2()):
        reader.u2()

    for _ in range(reader.u2()):
        reader.u2()  # access_flags
        reader.u2()  # name_index
        reader.u2()  # descriptor_index
        reader.skip_attributes(reader.u2())

    methods: list[MethodInfo] = []
    for _ in range(reader.u2()):
        access = reader.u2()
        name = utf8(reader.u2())
        descriptor = utf8(reader.u2())
        methods.append(MethodInfo(access, name, descriptor))
        reader.skip_attributes(reader.u2())

    utf8_values = [item[1] for item in pool if item and item[0] == 1 and isinstance(item[1], str)]
    class_names = {
        class_name(i)
        for i, item in enumerate(pool)
        if i and item is not None and item[0] == 7
    }
    return ClassEvidence(
        path=path,
        minor=minor,
        major=major,
        name=class_name(this_class),
        referenced_classes=frozenset(class_names),
        descriptor_classes=descriptor_names(utf8_values),
        methods=tuple(methods),
    )


def forbidden_references(evidence: ClassEvidence) -> list[str]:
    problems: list[str] = []
    simple = evidence.name.rsplit("/", 1)[-1].split("$", 1)[0]
    lower_name = evidence.name.lower()

    if evidence.name.startswith("io/github/gustavo2358/air/"):
        problems.append("local class occupies the shared air-java package")
    if simple in AIR_SHARED_SIMPLE_NAMES:
        problems.append("local class duplicates a shared AIR type name")
    if re.fullmatch(r"(?:Air|Ir|Cfg|Local)?Publication(?:Dto|Model)?", simple, re.IGNORECASE):
        problems.append("local Publication/AIR model class")
    if re.fullmatch(r"(?:Air|Ir).*(?:Dto|Model)", simple, re.IGNORECASE):
        problems.append("local AIR DTO/model class")
    if re.search(r"(?:air|ir).*validator|validator.*(?:air|ir)", simple, re.IGNORECASE):
        problems.append("local AIR validator class")
    if "/json/" in lower_name or "json" in simple.lower():
        problems.append("JSON-named production boundary/model class")
    if "/cli/" in lower_name:
        problems.append("CLI package in kernel")
    if any(segment in lower_name for segment in ("/adapter/", "/adapters/", "/launcher/")):
        problems.append("transport/composition package inside cfg-kernel")
    if ("/parser/" in lower_name or "cobol" in simple.lower()
            or "semanticproduct" in simple.lower() or simple.endswith(("Parser", "Lexer"))):
        problems.append("parser/COBOL production class in kernel")

    refs = evidence.all_references
    layer_forbidden: tuple[str, ...] = ()
    if evidence.name.startswith(KERNEL_PACKAGE + "domain/"):
        layer_forbidden = ("application/", "adapter/", "adapters/", "launcher/")
    elif evidence.name.startswith(KERNEL_PACKAGE + "application/"):
        layer_forbidden = ("adapter/", "adapters/", "launcher/")
    for reference in sorted(refs):
        if any(reference.startswith(KERNEL_PACKAGE + layer) for layer in layer_forbidden):
            problems.append(f"forbidden inward layer dependency: {reference}")

    forbidden_prefixes = {
        "com/fasterxml/jackson/": "Jackson",
        "com/google/gson/": "Gson",
        "jakarta/json/": "JSON-P",
        "javax/json/": "JSON-P",
        "org/json/": "org.json",
        "java/io/": "JDK I/O",
        "java/nio/file/": "JDK filesystem",
        "java/net/": "JDK networking",
        "picocli/": "CLI",
        "org/apache/commons/cli/": "CLI",
        "org/antlr/": "ANTLR",
        "io/proleap/": "ProLeap",
    }
    for reference in sorted(refs):
        normalized = reference.removeprefix("[")
        for prefix, label in forbidden_prefixes.items():
            if normalized.startswith(prefix):
                problems.append(f"forbidden {label} reference: {reference}")
        ref_lower = normalized.lower()
        ref_simple = normalized.rsplit("/", 1)[-1]
        if ("/cobol/" in ref_lower or "/parser/" in ref_lower
                or "semanticproduct" in ref_lower or "semantic_product" in ref_lower):
            problems.append(f"forbidden COBOL/parser reference: {reference}")
        if "json" in ref_simple.lower():
            problems.append(f"forbidden JSON type in production signature/reference: {reference}")

    for method in evidence.methods:
        if method.name == "main" and method.descriptor == "([Ljava/lang/String;)V" \
                and method.access & ACC_PUBLIC and method.access & ACC_STATIC:
            problems.append("public static CLI main method in kernel")
        parameters = method.descriptor.partition(")")[0]
        transport_shaped = any(
            transport_type in parameters
            for transport_type in ("Ljava/lang/String;", "[B", "[C", "Ljava/nio/ByteBuffer;")
        )
        looks_like_input_adapter = bool(re.search(r"json|parse|decode|read", method.name, re.IGNORECASE))
        if method.access & ACC_PUBLIC and transport_shaped \
                and (evidence.name == PREFLIGHT_CLASS or looks_like_input_adapter):
            problems.append(f"transport-shaped public boundary method: {method.name}{method.descriptor}")
    return problems


def internal_detector_self_test() -> None:
    def fixture(name: str, *references: str, methods: tuple[MethodInfo, ...] = ()) -> ClassEvidence:
        return ClassEvidence(Path(name + ".class"), 0, 65, name,
                             frozenset(references), frozenset(), methods)

    cases = {
        "local AIR package": fixture("io/github/gustavo2358/air/model/Publication"),
        "parallel model": fixture(KERNEL_PACKAGE + "model/LocalPublication"),
        "parallel validator": fixture(KERNEL_PACKAGE + "domain/AirContractValidator"),
        "AIR DTO": fixture(KERNEL_PACKAGE + "model/AirDto"),
        "shared Unit": fixture(KERNEL_PACKAGE + "model/Unit"),
        "shared Entry": fixture(KERNEL_PACKAGE + "model/Entry"),
        "shared Sequence": fixture(KERNEL_PACKAGE + "model/Sequence"),
        "shared Operation": fixture(KERNEL_PACKAGE + "model/Operation"),
        "shared Instruction": fixture(KERNEL_PACKAGE + "model/Instruction"),
        "shared Terminator": fixture(KERNEL_PACKAGE + "model/Terminator"),
        "shared TypeRef": fixture(KERNEL_PACKAGE + "model/TypeRef"),
        "shared Premise": fixture(KERNEL_PACKAGE + "model/Premise"),
        "shared proof scope": fixture(KERNEL_PACKAGE + "model/DomainProofScope"),
        "semantic product": fixture(KERNEL_PACKAGE + "application/SemanticProductInput"),
        "Jackson": fixture(PREFLIGHT_CLASS, "com/fasterxml/jackson/databind/JsonNode"),
        "Gson": fixture(PREFLIGHT_CLASS, "com/google/gson/JsonObject"),
        "filesystem": fixture(PREFLIGHT_CLASS, "java/nio/file/Path"),
        "JDK I/O": fixture(PREFLIGHT_CLASS, "java/io/InputStream"),
        "CLI dependency": fixture(PREFLIGHT_CLASS, "picocli/CommandLine"),
        "CLI entry point": fixture(
            PREFLIGHT_CLASS,
            methods=(MethodInfo(ACC_PUBLIC | ACC_STATIC, "main", "([Ljava/lang/String;)V"),),
        ),
        "ProLeap": fixture(PREFLIGHT_CLASS, "io/proleap/cobol/CobolParser"),
        "ANTLR": fixture(PREFLIGHT_CLASS, "org/antlr/v4/runtime/Parser"),
        "JSON boundary": fixture(PREFLIGHT_CLASS, KERNEL_PACKAGE + "adapter/JsonInput"),
        "string boundary": fixture(
            PREFLIGHT_CLASS,
            methods=(MethodInfo(ACC_PUBLIC, "fromJson", "(Ljava/lang/String;)Ljava/lang/Object;"),),
        ),
    }
    missed = [label for label, evidence in cases.items() if not forbidden_references(evidence)]
    if missed:
        raise GateConfigurationError("architecture detector self-test missed: " + ", ".join(missed))


def command_path(name: str) -> str:
    candidate = shutil.which(name)
    if candidate is None:
        raise GateConfigurationError(f"required command not found on PATH: {name}")
    return candidate


def local_repository_argument() -> list[str]:
    """Preserve a standard local-repository override even on Maven before MAVEN_ARGS."""
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


def assert_maven_runtime(maven: str, root: Path, repo_arg: list[str]) -> str:
    result = run([maven, *repo_arg, "--version"], root, capture=True)
    match = re.search(r"^Java version:\s*([^,\s]+)", result.stdout or "", re.MULTILINE)
    if not match:
        raise GateConfigurationError("could not determine the Java runtime used by Maven")
    version = match.group(1)
    major_text = version.split(".", 1)[0]
    if major_text == "1" and "." in version:
        major_text = version.split(".", 2)[1]
    try:
        major_match = re.match(r"\d+", major_text)
        if not major_match:
            raise ValueError(major_text)
        major = int(major_match.group(0))
    except ValueError as exc:
        raise GateConfigurationError(f"cannot parse Maven Java runtime version: {version}") from exc
    if major < 21:
        raise GateFailure(f"Maven must run on Java 21 or newer, observed {version}")
    return version


def count_tests(kernel: Path) -> tuple[int, int]:
    reports = sorted((kernel / "target/surefire-reports").glob("TEST-*.xml"))
    if not reports:
        raise GateFailure("no Surefire XML reports were produced")
    total = 0
    skipped = 0
    for report in reports:
        try:
            suite = ET.parse(report).getroot()
            total += int(suite.attrib.get("tests", "0"))
            skipped += int(suite.attrib.get("skipped", "0"))
            if int(suite.attrib.get("failures", "0")) or int(suite.attrib.get("errors", "0")):
                raise GateFailure(f"Surefire report contains failures/errors: {report}")
        except (ET.ParseError, ValueError) as exc:
            raise GateConfigurationError(f"invalid Surefire report {report}: {exc}") from exc
    if total - skipped <= 0:
        raise GateFailure("Maven completed with no non-skipped tests executed")
    return total, skipped


def parse_tgf(path: Path) -> list[str]:
    if not path.is_file():
        raise GateConfigurationError("Maven dependency tree did not produce its TGF evidence file")
    coordinates: list[str] = []
    before_edges = True
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if line == "#":
            before_edges = False
            continue
        if not line or not before_edges:
            continue
        match = re.match(r"^\d+\s+(.+)$", line)
        if match:
            coordinates.append(match.group(1).strip())
    if not coordinates:
        raise GateConfigurationError(f"could not parse Maven TGF dependency evidence: {path}")
    return coordinates


def verify_dependency_tree(coordinates: Sequence[str]) -> None:
    dependencies = [coordinate for coordinate in coordinates if f":{KERNEL_ARTIFACT}:" not in coordinate]
    expected = f"{AIR_GROUP}:{AIR_ARTIFACT}:jar:{AIR_VERSION}:compile"
    if dependencies != [expected]:
        rendered = ", ".join(dependencies) if dependencies else "<none>"
        raise GateFailure(f"kernel compile dependencies must be exactly {expected}; observed {rendered}")


def verify_direct_dependencies(kernel_pom: Path) -> None:
    try:
        project = ET.parse(kernel_pom).getroot()
    except ET.ParseError as exc:
        raise GateConfigurationError(f"invalid kernel POM {kernel_pom}: {exc}") from exc
    namespace = ""
    if project.tag.startswith("{"):
        namespace = project.tag.partition("}")[0] + "}"
    dependencies = project.find(namespace + "dependencies")
    declared: list[tuple[str, str, str]] = []
    if dependencies is not None:
        for dependency in dependencies.findall(namespace + "dependency"):
            def value(name: str, default: str = "") -> str:
                element = dependency.find(namespace + name)
                return element.text.strip() if element is not None and element.text else default

            scope = value("scope", "compile")
            declared.append((value("groupId"), value("artifactId"), scope))
    expected = [
        (AIR_GROUP, AIR_ARTIFACT, "compile"),
        (JUNIT_GROUP, JUNIT_ARTIFACT, "test"),
    ]
    if declared != expected:
        rendered = ", ".join(":".join(item) for item in declared) if declared else "<none>"
        raise GateFailure("cfg-kernel direct dependencies must contain only compile air-java and test JUnit; "
                          f"observed {rendered}")


def verify_reactor_inventory(root_pom: Path, root: Path) -> None:
    try:
        project = ET.parse(root_pom).getroot()
    except ET.ParseError as exc:
        raise GateConfigurationError(f"invalid reactor POM {root_pom}: {exc}") from exc
    namespace = ""
    if project.tag.startswith("{"):
        namespace = project.tag.partition("}")[0] + "}"
    modules_element = project.find(namespace + "modules")
    modules = [] if modules_element is None else [
        item.text.strip()
        for item in modules_element.findall(namespace + "module")
        if item.text and item.text.strip()
    ]
    if modules != [KERNEL_ARTIFACT]:
        raise GateFailure("foundation reactor must contain exactly the cfg-kernel module; observed "
                          + (", ".join(modules) or "<none>"))

    kernel_sources = (root / KERNEL_ARTIFACT / "src/main/java").resolve()
    escaped_sources: list[str] = []
    for path in root.rglob("*.java"):
        relative = path.relative_to(root)
        if any(part in {".git", "target"} for part in relative.parts):
            continue
        if "src/main/java" not in relative.as_posix():
            continue
        try:
            path.resolve().relative_to(kernel_sources)
        except ValueError:
            escaped_sources.append(relative.as_posix())
    if escaped_sources:
        raise GateFailure("production Java exists outside cfg-kernel: " + ", ".join(escaped_sources))


def read_classpath(path: Path) -> list[Path]:
    if not path.is_file():
        raise GateConfigurationError("Maven did not produce the resolved compile classpath")
    value = path.read_text(encoding="utf-8").strip()
    entries = [Path(item).resolve() for item in value.split(os.pathsep) if item]
    if len(entries) != 1:
        raise GateFailure(f"kernel compile classpath must contain only air-java; observed {len(entries)} entries")
    artifact = entries[0]
    if not artifact.is_file() or artifact.suffix != ".jar":
        raise GateConfigurationError(f"resolved air-java artifact is not a JAR: {artifact}")
    try:
        with zipfile.ZipFile(artifact) as jar:
            members = set(jar.namelist())
    except (OSError, zipfile.BadZipFile) as exc:
        raise GateConfigurationError(f"cannot inspect dependency JAR {artifact}: {exc}") from exc
    required = {PUBLICATION + ".class", VALIDATION_RESULT + ".class", AIR_VALIDATOR + ".class"}
    missing = sorted(required - members)
    if missing:
        raise GateFailure("resolved compile JAR is not the required air-java API; missing " + ", ".join(missing))
    return entries


def verify_classfiles(classes_dir: Path) -> tuple[list[ClassEvidence], ClassEvidence]:
    class_paths = sorted(classes_dir.rglob("*.class"))
    if not class_paths:
        raise GateFailure("kernel produced no production classfiles")
    evidence = [read_class(path) for path in class_paths]
    violations: list[str] = []
    preflight: Optional[ClassEvidence] = None
    for item in evidence:
        if item.major != 65:
            violations.append(f"{item.name}: class major {item.major}, expected 65 (Java 21)")
        if item.minor == 0xFFFF:
            violations.append(f"{item.name}: preview bytecode minor 65535")
        elif item.minor != 0:
            violations.append(f"{item.name}: unexpected class minor {item.minor}")
        violations.extend(f"{item.name}: {problem}" for problem in forbidden_references(item))
        if item.name == PREFLIGHT_CLASS:
            preflight = item
    if violations:
        raise GateFailure("forbidden kernel bytecode:\n  - " + "\n  - ".join(violations))
    observed_classes = {item.name for item in evidence}
    if observed_classes != {PREFLIGHT_CLASS}:
        raise GateFailure("foundation production class inventory must contain only CfgPreflight; observed "
                          + ", ".join(sorted(observed_classes)))
    if preflight is None:
        raise GateFailure(f"required boundary class is absent: {PREFLIGHT_CLASS}")
    constructors = [
        method for method in preflight.methods
        if method.name == "<init>" and method.descriptor == "()V"
        and method.access & ACC_PRIVATE
    ]
    matching = [
        method for method in preflight.methods
        if method.name == "validate" and method.descriptor == PREFLIGHT_DESCRIPTOR
        and method.access & ACC_PUBLIC and method.access & ACC_STATIC
    ]
    if len(preflight.methods) != 2 or len(constructors) != 1 or len(matching) != 1:
        raise GateFailure(
            "CfgPreflight must contain only a private constructor and public static ValidationResult "
            "validate(io.github.gustavo2358.air.model.Publication)"
        )
    for shared_type in (PUBLICATION, VALIDATION_RESULT, AIR_VALIDATOR):
        if shared_type not in preflight.all_references:
            raise GateFailure(f"CfgPreflight bytecode does not reference shared air-java type {shared_type}")
    return evidence, preflight


def reject_test_air_shadow(test_classes_dir: Path) -> None:
    shared_package = test_classes_dir / "io/github/gustavo2358/air"
    shadows = sorted(path.relative_to(test_classes_dir).as_posix()
                     for path in shared_package.rglob("*.class")) if shared_package.exists() else []
    if shadows:
        raise GateFailure("test classes shadow the shared air-java package: " + ", ".join(shadows))


def verify_javap(javap: str, root: Path, classes_dir: Path, classpath: Sequence[Path]) -> None:
    full_classpath = os.pathsep.join([str(classes_dir), *(str(path) for path in classpath)])
    result = run([javap, "-classpath", full_classpath, "-c", "-p", "-s",
                  PREFLIGHT_CLASS.replace("/", ".")], root, capture=True)
    output = result.stdout or ""
    direct_delegation = re.compile(
        r"public static .*? validate\(io\.github\.gustavo2358\.air\.model\.Publication\);\s+"
        + r"descriptor:\s+" + re.escape(PREFLIGHT_DESCRIPTOR) + r"\s+"
        + r"Code:\s+0:\s+aload_0\s+1:\s+invokestatic\s+#\d+\s+// Method "
        + re.escape(AIR_VALIDATOR + ".validate:" + PREFLIGHT_DESCRIPTOR)
        + r"\s+4:\s+areturn",
        re.DOTALL,
    )
    if not direct_delegation.search(output):
        raise GateFailure("CfgPreflight is not an exact direct delegation to AirValidator.validate(Publication)")


def verify_jdeps(jdeps: str, root: Path, classes_dir: Path, classpath: Sequence[Path]) -> None:
    cp = os.pathsep.join(str(path) for path in classpath)
    result = run(
        [jdeps, "--multi-release", "21", "--ignore-missing-deps", "--print-module-deps",
         "--class-path", cp, str(classes_dir)],
        root,
        capture=True,
    )
    modules = {item.strip() for item in (result.stdout or "").strip().split(",") if item.strip()}
    if modules != {"java.base"}:
        raise GateFailure("kernel JDK module dependencies must be exactly java.base; observed "
                          + (", ".join(sorted(modules)) or "<none>"))

    verbose = run(
        [jdeps, "--multi-release", "21", "--ignore-missing-deps", "-verbose:class",
         "--class-path", cp, str(classes_dir)],
        root,
        capture=True,
    ).stdout or ""
    for class_name in (
        PUBLICATION.replace("/", "."),
        VALIDATION_RESULT.replace("/", "."),
        AIR_VALIDATOR.replace("/", "."),
    ):
        if class_name not in verbose:
            raise GateFailure(f"jdeps did not observe CfgPreflight dependency on {class_name}")


def reject_preview_configuration(root: Path) -> None:
    candidates = list(root.glob("**/pom.xml")) + [root / ".mvn/jvm.config", root / ".mvn/maven.config"]
    configured = [path for path in candidates if path.is_file() and "--enable-preview" in path.read_text(encoding="utf-8")]
    if configured:
        raise GateFailure("preview is configured in: " + ", ".join(str(path.relative_to(root)) for path in configured))


def verify_snapshot_pin(root: Path) -> str:
    lock_path = root / "docs/sources/sources.lock.json"
    workflow_path = root / ".github/workflows/ci.yml"
    try:
        lock = json.loads(lock_path.read_text(encoding="utf-8"))
        air = lock["air_java"]
        coordinates = air["maven"]
    except (OSError, json.JSONDecodeError, KeyError, TypeError) as exc:
        raise GateConfigurationError(f"cannot read pinned air-java source metadata: {exc}") from exc

    sha = air.get("commit", "")
    if not re.fullmatch(r"[0-9a-f]{40}", sha):
        raise GateFailure("air-java source lock must contain an immutable 40-character SHA")
    expected_coordinates = {
        "group_id": AIR_GROUP,
        "artifact_id": AIR_ARTIFACT,
        "version": AIR_VERSION,
    }
    if air.get("repository") != AIR_REPOSITORY or coordinates != expected_coordinates:
        raise GateFailure("air-java source lock disagrees with the kernel dependency coordinates")

    try:
        workflow = workflow_path.read_text(encoding="utf-8")
    except OSError as exc:
        raise GateConfigurationError(f"cannot read CI workflow: {exc}") from exc
    repository_lines = re.findall(r"(?m)^\s*repository:\s*([^\s#]+)", workflow)
    ref_lines = re.findall(r"(?m)^\s*ref:\s*([^\s#]+)", workflow)
    if repository_lines.count(AIR_REPOSITORY) != 1 or ref_lines.count(sha) != 1:
        raise GateFailure("CI must check out air-java exactly once at the source-lock SHA")
    if re.search(r"(?m)^\s*ref:\s*(?:main|master)\s*$", workflow):
        raise GateFailure("CI must not resolve air-java from a mutable branch")
    if f'test "$(git rev-parse HEAD)" = "{sha}"' not in workflow:
        raise GateFailure("CI must verify the checked-out air-java HEAD before installation")
    return sha


def architecture_gate(root: Path) -> None:
    kernel = root / KERNEL_ARTIFACT
    if not (root / "pom.xml").is_file() or not (kernel / "pom.xml").is_file():
        raise GateConfigurationError("expected Maven reactor and cfg-kernel module POMs")

    internal_detector_self_test()
    reject_preview_configuration(root)
    air_sha = verify_snapshot_pin(root)
    verify_reactor_inventory(root / "pom.xml", root)
    verify_direct_dependencies(kernel / "pom.xml")
    maven = command_path("mvn")
    javap = command_path("javap")
    jdeps = command_path("jdeps")
    repo_arg = local_repository_argument()
    runtime_version = assert_maven_runtime(maven, root, repo_arg)
    run([maven, *repo_arg, "--batch-mode", "--no-transfer-progress", "clean", "test"], root)
    total, skipped = count_tests(kernel)

    with tempfile.TemporaryDirectory(prefix="analysis-cfg-architecture-") as temporary:
        temp = Path(temporary)
        tree_file = temp / "compile-dependencies.tgf"
        classpath_file = temp / "compile-classpath.txt"
        common = [maven, *repo_arg, "--batch-mode", "--no-transfer-progress", "-pl", f":{KERNEL_ARTIFACT}"]
        run([
            *common,
            f"{DEPENDENCY_PLUGIN}:tree",
            "-Dscope=compile",
            "-DoutputType=tgf",
            f"-DoutputFile={tree_file}",
        ], root)
        coordinates = parse_tgf(tree_file)
        verify_dependency_tree(coordinates)
        run([
            *common,
            f"{DEPENDENCY_PLUGIN}:build-classpath",
            "-DincludeScope=compile",
            f"-Dmdep.outputFile={classpath_file}",
        ], root)
        classpath = read_classpath(classpath_file)

        classes_dir = kernel / "target/classes"
        classfiles, _ = verify_classfiles(classes_dir)
        reject_test_air_shadow(kernel / "target/test-classes")
        verify_javap(javap, root, classes_dir, classpath)
        verify_jdeps(jdeps, root, classes_dir, classpath)

    print(f"[architecture] PASS: {total} tests ({skipped} skipped), "
          f"{len(classfiles)} production classfiles, Java class major 65/no preview "
          f"(Maven runtime {runtime_version})", flush=True)
    print(f"[architecture] PASS: compile dependency {AIR_GROUP}:{AIR_ARTIFACT}:{AIR_VERSION} "
          "and JDK module java.base only", flush=True)
    print(f"[architecture] PASS: CI source pin {AIR_REPOSITORY}@{air_sha}", flush=True)
    print("[architecture] PASS: shared Publication/ValidationResult boundary delegates to AirValidator", flush=True)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument("--self-test", action="store_true", help="run only detector fixtures")
    args = parser.parse_args()
    try:
        if args.self_test:
            internal_detector_self_test()
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
