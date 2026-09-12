#!/usr/bin/env python3
"""Falsify 2B guards with focused temporary mutations; restore exact bytes even on failed challenges."""
from __future__ import annotations
import argparse
import hashlib
import json
import os
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ADAPTER = "cfg-adapters/src/main/java/io/github/gustavo2358/analysis/cfg/adapters/"
LAUNCHER = "cfg-launcher/src/main/java/io/github/gustavo2358/analysis/cfg/launcher/AnalysisCfg.java"
KERNEL = "cfg-kernel/src/main/java/io/github/gustavo2358/analysis/cfg/application/BuildCfg.java"
SUITE = "cfg-launcher/src/test/java/io/github/gustavo2358/analysis/cfg/launcher/EvalCfg031Test.java"


def digest(data: bytes) -> str: return hashlib.sha256(data).hexdigest()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--evidence", type=Path, required=True, help="log/result directory outside tracked sources")
    args = parser.parse_args()
    evidence = args.evidence.resolve(); evidence.mkdir(parents=True, exist_ok=True)
    if str(evidence).startswith(str(ROOT) + os.sep) and not str(evidence).startswith(str(ROOT / '.harness-results') + os.sep):
        parser.error("evidence must be outside source tree or under .harness-results")
    shape = [sys.executable, "-c", "import sys;from pathlib import Path;sys.path.insert(0,'scripts/project');from check_architecture import verify_project_shape;verify_project_shape(Path.cwd())"]
    integration = ["bash", "scripts/harness/check-integration.sh"]
    architecture = ["bash", "scripts/harness/check-architecture.sh"]
    tests = ["mvn", "-B", "-ntp", "test"]
    cases = [
        ("01-local-air-parser", ADAPTER + "AirJsonFileReader.java", "return codec.decode(bytes);",
         'return java.util.regex.Pattern.compile("publication").matcher(new String(bytes, java.nio.charset.StandardCharsets.UTF_8)).find() ? null : null;', shape, "shared AirJson.decode"),
        ("02-kernel-air-json", "cfg-kernel/pom.xml", "  <dependencies>",
         '  <dependencies>\n    <dependency><groupId>io.github.gustavo2358</groupId><artifactId>air-json</artifactId><version>0.1.0-SNAPSHOT</version></dependency>', shape, "cfg-kernel dependencies"),
        ("03a-kernel-filesystem-bytecode", KERNEL.replace("BuildCfg.java", "CfgPreflight.java"), "public final class CfgPreflight {",
         'public final class CfgPreflight {\n    private static java.nio.file.Path forbiddenFilesystem() { return java.nio.file.Path.of("x"); }', architecture, "forbidden bytecode dependency"),
        ("03b-kernel-json-import", KERNEL, "import io.github.gustavo2358.air.model.Publication;",
         'import io.github.gustavo2358.air.model.Publication;\nimport com.fasterxml.jackson.databind.ObjectMapper;', shape, "imports mismatch"),
        ("04-non-built-publishes", LAUNCHER, 'err.println("CFG " + result.status());',
         'err.println("CFG " + result.status());\n            try { java.nio.file.Files.writeString(output, "{\\\"buildStatus\\\":\\\"CFG_BUILT\\\"}"); } catch (IOException e) { throw new java.io.UncheckedIOException(e); }', tests, "Failures:"),
        ("05-partial-promoted", ADAPTER + "CfgJsonWriter.java", 'case PARTIAL -> "PARTIAL";', 'case PARTIAL -> "COMPLETE";', tests, "Failures:"),
        ("06a-drop-entry", ADAPTER + "CfgJsonWriter.java", 'for (var transition : graph.transitions()) {',
         'for (var transition : graph.transitions().stream().filter(t -> t.kind() != CfgTransition.Kind.ENTRY).toList()) {', tests, "Failures:"),
        ("06b-drop-return", ADAPTER + "CfgJsonWriter.java", 'for (var transition : graph.transitions()) {',
         'for (var transition : graph.transitions().stream().filter(t -> t.kind() != CfgTransition.Kind.RETURN).toList()) {', tests, "Failures:"),
        ("07-activation-swapped", ADAPTER + "CfgJsonWriter.java", 'airId(out, transition.activationEntry());',
         'airId(out, new Ids.EntryId(transition.activationEntry().unit(), "wrong-entry"));', tests, "Failures:"),
        ("08a-enum-name-wire", ADAPTER + "CfgJsonWriter.java", 'out.string(transitionKind(transition.kind()).token)',
         'out.string(transition.kind().name())', shape, "runtime enum/object rendering"),
        ("08b-enum-to-string-wire", ADAPTER + "CfgJsonWriter.java", 'out.string(transitionKind(transition.kind()).token)',
         'out.string(transition.kind().toString())', shape, "runtime enum/object rendering"),
        ("09-machine-metadata", ADAPTER + "CfgJsonWriter.java", 'out.raw("]}");\n        return out.bytes();',
         'out.raw("],\\\"timestamp\\\":\\\"machine-time\\\"}");\n        return out.bytes();', tests, "Failures:"),
        ("10a-suite-removed", SUITE, None, None, integration, "No tests were executed!"),
        ("10b-suite-skipped", SUITE, 'class EvalCfg031Test {', '@org.junit.jupiter.api.Disabled\nclass EvalCfg031Test {', integration, "integration report counts"),
        ("11-air-failure-exit-zero", LAUNCHER, 'return 3;', 'return 0;', tests, "Failures:"),
        ("12-build-failure-exit-zero", LAUNCHER, 'return 4;', 'return 0;', tests, "Failures:"),
        ("13-publish-before-complete-bytes", ADAPTER + "CfgJsonWriter.java", 'byte[] bytes = encode(result);',
         'Files.write(destination, new byte[0]);\n        byte[] bytes = encode(result);', tests, "Failures:"),
    ]
    originals = {path: (ROOT / path).read_bytes() for _, path, *_ in cases}
    results = []
    try:
        for name, relative, old, new, command, expected in cases:
            path = ROOT / relative; original = originals[relative]
            try:
                if old is None: path.unlink()
                else:
                    text = original.decode(); occurrences = text.count(old)
                    if occurrences == 0: raise RuntimeError("mutation anchor absent: " + name)
                    if name != "11-air-failure-exit-zero" and occurrences != 1:
                        raise RuntimeError("mutation anchor ambiguous: " + name)
                    path.write_text(text.replace(old, new), encoding="utf-8")
                with (evidence / (name + ".log")).open("w") as log:
                    completed = subprocess.run(command, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT, check=False)
                output = (evidence / (name + ".log")).read_text()
                # A compiler or configuration failure must not masquerade as a behavioral assertion failure.
                observed = completed.returncode == 1 and expected in output
                if command == tests:
                    import re
                    observed = observed and bool(re.search(r"Tests run: \d+, Failures: [1-9]\d*, Errors: 0, Skipped: 0", output))
                    observed = observed and "COMPILATION ERROR" not in output
                results.append({"challenge": name, "command": command, "exit": completed.returncode,
                                "detected": observed, "restored_sha256": digest(original)})
                print(f"{name}: {'RED' if observed else 'NOT PROVED'} exit={completed.returncode}", flush=True)
                if not observed: raise RuntimeError("challenge not detected by intended guard: " + name)
            finally:
                path.write_bytes(original)
                if path.read_bytes() != original: raise RuntimeError("byte restoration failed: " + relative)
        print("All mutations restored byte-for-byte; run second GREEN gates now.", flush=True)
        return 0
    finally:
        for path, original in originals.items():
            if (ROOT / path).read_bytes() != original: raise RuntimeError("unrestored source: " + path)
        (evidence / "results.json").write_text(json.dumps(results, indent=2) + "\n")

if __name__ == "__main__": sys.exit(main())
