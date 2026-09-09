#!/usr/bin/env python3
"""Explicit outer-module inventory; inner kernel checks remain unchanged and independently enforced."""
from __future__ import annotations
import json
import os
import re
import struct
from pathlib import Path
from check_architecture import (GateFailure, GateConfigurationError, IMPORT_PATTERN, DEPENDENCY_PLUGIN,
                                exact, run, xml_root, child_text, parse_tgf)

PREFIX = "io.github.gustavo2358.analysis.cfg."
AIR = "io.github.gustavo2358:air-java:jar:0.1.0-SNAPSHOT:compile"
JSON = "io.github.gustavo2358:air-json:jar:0.1.0-SNAPSHOT:compile"
KERNEL = "io.github.gustavo2358.analysis:cfg-kernel:jar:0.1.0-SNAPSHOT:compile"
ADAPTERS = "io.github.gustavo2358.analysis:cfg-adapters:jar:0.1.0-SNAPSHOT:compile"
DIRECT_DEPENDENCIES = {
    "cfg-adapters": [("io.github.gustavo2358.analysis", "cfg-kernel", "compile"),
                     ("io.github.gustavo2358", "air-java", "compile"),
                     ("io.github.gustavo2358", "air-json", "compile"),
                     ("org.junit.jupiter", "junit-jupiter", "test")],
    "cfg-launcher": [("io.github.gustavo2358.analysis", "cfg-adapters", "compile"),
                     ("io.github.gustavo2358.analysis", "cfg-kernel", "compile"),
                     ("io.github.gustavo2358", "air-java", "compile"),
                     ("io.github.gustavo2358", "air-json", "compile"),
                     ("org.junit.jupiter", "junit-jupiter", "test")],
}


def inventory(root: Path) -> dict:
    return json.loads((root / "scripts/project/transport-inventory.json").read_text(encoding="utf-8"))


def transport_source_inventory(root: Path) -> set[str]:
    return {source for module in inventory(root).values() for source in module["sources"]}


def verify_transport_shape(root: Path) -> None:
    reviewed = inventory(root)
    exact(reviewed, DIRECT_DEPENDENCIES, "transport modules")
    for module, expected in DIRECT_DEPENDENCIES.items():
        pom, ns = xml_root(root / module / "pom.xml")
        declared = [(child_text(d, ns, "groupId"), child_text(d, ns, "artifactId"), child_text(d, ns, "scope", "compile"))
                    for d in pom.findall(ns + "dependencies/" + ns + "dependency")]
        if declared != expected:
            raise GateFailure("transport dependency direction/ownership drift: " + module)
        for path, imports in reviewed[module]["sources"].items():
            source = (root / path).read_text(encoding="utf-8")
            exact(IMPORT_PATTERN.findall(source), imports, path + " imports")
            if re.search(r"\b(?:Class\.forName|ServiceLoader|getDeclaredMethod|getSimpleName|getClass)\b", source):
                raise GateFailure("transport reflection/discovery is not authorized")
            if module == "cfg-adapters" and re.search(r"\bSystem\.(?:getenv|getProperties|getProperty|exit)|\bProcessBuilder\b", source):
                raise GateFailure("adapters cannot depend on environment/process")
            if path.endswith("CfgJsonWriter.java") and re.search(r"\.name\s*\(|(?<!Long)\.toString\s*\(", source):
                raise GateFailure("runtime enum/object rendering cannot govern CFG wire")
    reader = root / "cfg-adapters/src/main/java/io/github/gustavo2358/analysis/cfg/adapters/AirJsonFileReader.java"
    text = reader.read_text(encoding="utf-8")
    if "return codec.decode(bytes);" not in text or "readNBytes(limits.maximumDocumentBytes())" not in text:
        raise GateFailure("AIR file input must be bounded and delegated directly to shared AirJson.decode")
    if re.search(r"readAllBytes|ObjectMapper|JsonParser|BindingReader|\.encode\(", text):
        raise GateFailure("parallel/unbounded AIR reader forbidden")


def dependencies_from_jdeps(output: str) -> dict[str, set[str]]:
    result: dict[str, set[str]] = {}
    for line in output.splitlines():
        match = re.match(r"^\s+(\S+)\s+->\s+(\S+)\s+(.+)$", line)
        if match:
            source, target, location = match.groups()
            if location.strip() == "not found": raise GateFailure("unresolved bytecode dependency: " + line)
            result.setdefault(source, set()).add(target)
    return result


def verify_dependencies(module: str, actual: dict[str, set[str]], expected: dict[str, list[str]]) -> None:
    exact(actual, expected, module + " jdeps source inventory")
    for source, targets in actual.items():
        exact(targets, expected[source], source + " bytecode targets")
        for target in targets:
            if (target.startswith(("io.proleap.", "org.antlr.", "com.fasterxml.", "com.google.gson.",
                                   "java.net.", "java.lang.reflect."))
                    or (module == "cfg-adapters" and target.startswith(PREFIX + "launcher."))):
                raise GateFailure("forbidden transport dependency " + source + " -> " + target)


def transport_gate(root: Path, maven: str, repository: list[str], javap: str, jdeps: str, air_jar: Path) -> None:
    from check_integration import verify_reports
    verify_reports(root)  # architecture also rejects skipped/absent transport tests
    reviewed = inventory(root)
    # Package before dependency plugins so the reactor resolves current internal jars, never stale installed snapshots.
    run([maven, *repository, "-B", "-ntp", "-DskipTests", "package", f"{DEPENDENCY_PLUGIN}:tree",
         "-Dscope=compile", "-DoutputType=tgf", "-DoutputFile=target/architecture-dependencies.tgf",
         f"{DEPENDENCY_PLUGIN}:build-classpath", "-DincludeScope=compile", "-Dmdep.outputFile=target/architecture-classpath.txt"], root)
    for module in reviewed:
        classes = root / module / "target/classes"
        paths = {p.relative_to(classes).as_posix(): p for p in classes.rglob("*.class")}
        expected = reviewed[module]
        exact(paths, expected["classfiles"], module + " exact classfiles")
        for path in paths.values():
            if struct.unpack(">IHH", path.read_bytes()[:8]) != (0xcafebabe, 0, 65):
                raise GateFailure("transport must be Java 21 without preview")
        if list((root / module / "target/test-classes/io/github/gustavo2358/air").rglob("*.class")):
            raise GateFailure("tests cannot shadow AIR classes")
        tree = parse_tgf(root / module / "target/architecture-dependencies.tgf")
        actual_deps = [c for c in tree if not c.startswith("io.github.gustavo2358.analysis:" + module + ":")]
        expected_deps = {AIR, JSON, KERNEL} | ({ADAPTERS} if module == "cfg-launcher" else set())
        exact(actual_deps, expected_deps, module + " effective compile dependency graph")
        cp = (root / module / "target/architecture-classpath.txt").read_text().strip()
        full_cp = str(classes) + os.pathsep + cp
        output = run([jdeps, "--multi-release", "21", "-filter:none", "-verbose:class", "-cp", cp, str(classes)], root, capture=True).stdout
        actual = dependencies_from_jdeps(output)
        verify_dependencies(module, actual, expected["bytecode_dependencies"])
        if module == "cfg-adapters":
            reader = run([javap, "-classpath", full_cp, "-c", "-p", PREFIX + "adapters.AirJsonFileReader"], root, capture=True).stdout
            if "air/json/AirJson.decode:([B)Lio/github/gustavo2358/air/model/Publication;" not in reader:
                raise GateFailure("compiled file reader does not invoke shared AirJson.decode(byte[])")
            if "air/json/AirJson.encode" in reader:
                raise GateFailure("AIR reader must not synthesize input with shared encoder")
    # Model itself must not point back to CFG, codec, frontend or I/O; only standard JDK inspection.
    output = run([jdeps, "--multi-release", "21", "-filter:none", "-verbose:class", str(air_jar)], root, capture=True).stdout
    model = dependencies_from_jdeps(output)
    if not model: raise GateFailure("missing AIR model bytecode evidence")
    for targets in model.values():
        for target in targets:
            if target.startswith((PREFIX, "io.github.gustavo2358.air.json.", "io.proleap.", "org.antlr.",
                                  "com.fasterxml.", "com.google.gson.", "java.nio.file.")):
                raise GateFailure("AIR model outward dependency: " + target)
    print("[architecture] PASS: adapters/launcher exact sources, classfiles, bytecode edges and effective Maven DAG; shared decoder call; model has no CFG back-edge", flush=True)
