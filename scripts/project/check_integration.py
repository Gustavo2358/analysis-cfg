#!/usr/bin/env python3
"""Execute real AIR-file/codec/kernel/CFG-file oracles and reject missing, duplicate or skipped tests."""
from __future__ import annotations
import argparse
import copy
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET
from pathlib import Path
from check_architecture import GateFailure, GateConfigurationError, command_path, local_repository_argument

# Reviewed nominal obligations. Never derive this inventory from reports or Java source at runtime.
SUITES = {
    "cfg-adapters": {
        "io.github.gustavo2358.analysis.cfg.adapters.W1dInvokeWireTest": {
            "realInvokeRetainsDistinctTerminatorAndTransitionInExistingCfgWire",
            "historicalGoldensRemainExactlyV1",
            "allLegacyKindsAndMisleadingIdsStillSelectV1",
            "invokeNodeRequiresV2EvenWithoutTransitions",
            "onlyProjectedDomainSelectsVersionAndWriterHasNoStickyState",
        },
        "io.github.gustavo2358.analysis.cfg.adapters.ScalarAssignTest": {
            "sequencePayloadAndObjectCellIdentitySurviveRealReaderAndBuild",
            "topologyIsExactlyEntrySequenceReturnAndPartialKnowledgeIsPreserved",
            "manualCfgGoldenRemainsTopologyOnlyAndDeterministic",
            "defaultBudgetsAreRepresentationalAndExplicitPhysicalBudgetStillApplies",
            "manyAssignsRemainOneSequenceNodeWithoutCopyingPayload",
        },
        "io.github.gustavo2358.analysis.cfg.adapters.TransportTest": {
            "fileDecodePreservesExpectedAirFacts", "realBuildHasExactManualTopologyAndCorrelations",
            "writerMatchesIndependentGoldenBytesAndIsDeterministic", "readerPhysicalBoundAcceptsExactSizeAndRejectsOneExtra",
            "codecFailurePreservesCodePathAndIssues", "outputLimitPreservesExistingDestinationBeforeAnyTemp",
            "nonBuiltResultCannotBeSerializedOrPublished", "resourceLimitPreservesTypedCodecIssues",
            "unsupportedCapabilityRemainsACodecFailure",
        },
        "io.github.gustavo2358.analysis.cfg.adapters.WriterDomainTest": {
            "memoryAndFileHaveEquivalentControlCoverageWire", "writerCoversEveryCurrentKindWithExactContextualTransitions",
            "utf8EscapingAndByteLimitAreExact", "invalidUnicodeIsRejectedByOutputPrimitiveAndAirModel",
            "nonAtomicFallbackIsExplicitAndMovesCompleteBytes", "failedMoveCleansTemporaryAndDoesNotReportSuccess",
        },
    },
    "cfg-launcher": {
        "io.github.gustavo2358.analysis.cfg.launcher.ScalarAssignCliTest": {
            "twoRealCliProcessesMatchScalarManualGoldenByteForByte",
        },
        "io.github.gustavo2358.analysis.cfg.launcher.EvalCfg031Test": {
            "fileThroughRealCliMatchesManualGolden", "twoIndependentExecutionsProduceIdenticalBytes",
            "missingAirFileCannotPublish", "bomIsTypedAirFailureWithoutOutput", "malformedUtf8IsTypedAirFailureWithoutOutput",
            "wrongBindingVersionPreservesCodecPath", "unsupportedCodecFormNeverBecomesCfgUnsupportedInput",
            "unavailableInventoryReachesRealKernelAndIsRejected", "allNonBuiltStatusesReturnFourWithoutOutput",
            "outputFilesystemFailureIsSixAndCleansTemp", "missingOutputParentIsSix",
            "serializationFailureIsFiveAndPreservesDestination", "usageNeverCallsBuild", "unexpectedBuildBugPropagatesUnchanged",
            "mainProcessUsesRealExitCodesAndFilePipeline", "oversizedPhysicalInputReturnsThreeBeforeCodec",
        },
    },
}


def verify_suite(suite: ET.Element, name: str, required: set[str]) -> None:
    if suite.tag != "testsuite" or suite.get("name") != name:
        raise GateFailure("integration suite identity mismatch: " + name)
    cases = suite.findall("testcase")
    methods = [case.get("name") for case in cases]
    if set(methods) != required or len(methods) != len(required):
        raise GateFailure("integration method inventory missing/duplicate/foreign: " + name)
    if suite.get("tests") != str(len(required)) or any(suite.get(k) != "0" for k in ("failures", "errors", "skipped")):
        raise GateFailure("integration report counts failed/skipped/invalid: " + name)
    if any(c.get("classname") != name or any(c.find(tag) is not None for tag in ("skipped", "failure", "error")) for c in cases):
        raise GateFailure("integration testcase foreign/failed/skipped: " + name)


def verify_reports(root: Path) -> None:
    for module, suites in SUITES.items():
        directory = root / module / "target/surefire-reports"
        if {p.name for p in directory.glob("TEST-*.xml")} != {"TEST-" + name + ".xml" for name in suites}:
            raise GateFailure("integration reports missing/extra/duplicate: " + module)
        for name, required in suites.items():
            try:
                suite = ET.parse(directory / ("TEST-" + name + ".xml")).getroot()
            except (OSError, ET.ParseError) as exc:
                raise GateFailure("unreadable integration report: " + name) from exc
            verify_suite(suite, name, required)


def detector_self_test() -> None:
    count = 0
    def rejected(action):
        nonlocal count
        try: action()
        except GateFailure: count += 1
        else: raise GateConfigurationError("integration detector accepted invalid report")
    with tempfile.TemporaryDirectory(prefix="cfg-integration-detector-") as directory:
        root = Path(directory)
        for module, suites in SUITES.items():
            reports = root / module / "target/surefire-reports"; reports.mkdir(parents=True)
            for name, required in suites.items():
                good = ET.Element("testsuite", name=name, tests=str(len(required)), failures="0", errors="0", skipped="0")
                for method in sorted(required): ET.SubElement(good, "testcase", name=method, classname=name)
                verify_suite(good, name, required)
                mutants = []
                for child in list(good):
                    mutant = copy.deepcopy(good); mutant.remove(next(c for c in mutant if c.get("name") == child.get("name")))
                    mutant.set("tests", str(len(required) - 1)); mutants.append(mutant)
                for tag in ("skipped", "failure", "error"):
                    mutant = copy.deepcopy(good); ET.SubElement(mutant[0], tag); mutants.append(mutant)
                for attr, value in (("tests", "0"), ("skipped", "1"), ("errors", "1"), ("failures", "1"), ("tests", "bad"), ("name", "Foreign")):
                    mutant = copy.deepcopy(good); mutant.set(attr, value); mutants.append(mutant)
                mutant = copy.deepcopy(good)
                mutant[0].set("name", mutant[1].get("name") if len(mutant) > 1 else "foreignMethod")
                mutants.append(mutant)
                mutant = copy.deepcopy(good); mutant[0].set("classname", "Foreign"); mutants.append(mutant)
                mutant = copy.deepcopy(good); mutant.append(copy.deepcopy(mutant[0])); mutants.append(mutant)
                for mutant in mutants: rejected(lambda: verify_suite(mutant, name, required))
                ET.ElementTree(good).write(reports / ("TEST-" + name + ".xml"))
        verify_reports(root)
        for report in sorted(root.rglob("TEST-*.xml")):
            data = report.read_bytes(); report.unlink(); rejected(lambda: verify_reports(root)); report.write_bytes(data)
            report.write_text("<broken"); rejected(lambda: verify_reports(root)); report.write_bytes(data)
        foreign = root / "cfg-launcher/target/surefire-reports/TEST-Foreign.xml"
        foreign.write_text("<testsuite/>"); rejected(lambda: verify_reports(root))
    print(f"[integration] detector rejected {count} adversarial reports", flush=True)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    try:
        detector_self_test()
        if args.self_test: return 0
        from check_scalar_contract import verify_scalar_contract
        verify_scalar_contract(args.root)
        # Full clean reactor retains kernel regression execution and prevents stale reports/classes.
        command = [command_path("mvn"), *local_repository_argument(), "-B", "-ntp", "clean", "test"]
        print("[integration] RUN: " + " ".join(command), flush=True)
        if subprocess.run(command, cwd=args.root, check=False).returncode:
            raise GateFailure("real integration execution failed")
        verify_reports(args.root)
        total = sum(len(methods) for suites in SUITES.values() for methods in suites.values())
        print(f"[integration] PASS: EVAL-CFG-031/032 and W1D Invoke wire, {sum(len(suites) for suites in SUITES.values())} nominal suites / {total} methods; real files, shared AirJson, BuildCfg, CLI/process, golden and memory equivalence", flush=True)
        return 0
    except GateFailure as exc:
        print("[integration] FAIL: " + str(exc), file=sys.stderr); return 1
    except (OSError, GateConfigurationError) as exc:
        print("[integration] ERROR: " + str(exc), file=sys.stderr); return 2

if __name__ == "__main__": sys.exit(main())
