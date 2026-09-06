#!/usr/bin/env python3
"""Execute the explicitly inventoried EVAL-CFG-025 oracle; absent/skipped tests fail."""

from __future__ import annotations

import argparse
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

from check_architecture import GateConfigurationError, GateFailure, command_path, local_repository_argument


SUITE = "io.github.gustavo2358.analysis.cfg.domain.EvalCfg025Test"
# Manual obligations: do not infer this inventory from production code, test source, or reports.
EXPECTED_METHODS = {
    "entryReturnAndNormalExitMatchIndependentOracle",
    "missingInitialLabelIsInvalidIrAndIsNotRepaired",
    "sequenceCannotBeConstructedWithoutTerminator",
    "returnNeverFallsThroughToPhysicalNextSequence",
    "physicalSequencePermutationPreservesCorrelatedControlAndCfgIds",
    "orphanRemainsInventoriedAndCorrelatedWithoutArtificialPredecessor",
    "sharedReturnPreservesBothActivationEntriesAndTheirExits",
    "unitsWithSameLocalIdsRemainDistinctAndOrderIndependent",
    "cfgIdsHaveTheirOwnDomainEvenWhenAirLocalIdsCollide",
    "graphIsImmutableAndRetainsTheOriginalAirObjectsWithoutDeepCopy",
    "navigationReusesMaterializedImmutableInventories",
    "haltIsOutsideTheSliceIncludingInAnOrphan",
    "jumpIsOutsideTheSliceEvenWithAValidExplicitTarget",
    "instructionsAreExplicitlyOutsideThisSlice",
    "unknownRequiredCapabilityCannotBecomeSuccessfulCfg",
    "returnOperandsAndOriginsRemainSharedAndOrdered",
    "entryCollectionOrderCannotChooseTheActivationOrItsInitialLabel",
    "unavailableInventoryIsNotACompleteEmptyGraph",
    "unavailableUnitBodyIsNotInvented",
    "registeredCapabilityIdentityAloneDoesNotImplementItsSemantics",
}


def verify_suite(suite: ET.Element) -> None:
    if suite.tag != "testsuite" or suite.get("name") != SUITE:
        raise GateFailure("expected exactly the EVAL-CFG-025 suite")
    cases = suite.findall("testcase")
    names = [case.get("name") for case in cases]
    if set(names) != EXPECTED_METHODS or len(names) != len(EXPECTED_METHODS):
        raise GateFailure("EVAL-CFG-025 method inventory mismatch (missing, duplicate or zero tests)")
    try:
        if int(suite.get("tests", "0")) != len(EXPECTED_METHODS):
            raise GateFailure("EVAL-CFG-025 report count disagrees with required cases")
        if any(int(suite.get(key, "0")) for key in ("failures", "errors", "skipped")):
            raise GateFailure("EVAL-CFG-025 contains failed, errored or skipped tests")
    except ValueError as exc:
        raise GateFailure("invalid semantic report counts") from exc
    if any(case.get("classname") != SUITE or len(case.findall("failure"))
           or len(case.findall("error")) or len(case.findall("skipped")) for case in cases):
        raise GateFailure("EVAL-CFG-025 testcase is foreign, failed or skipped")


def verify_reports(directory: Path) -> None:
    reports = list(directory.glob("TEST-*.xml"))
    if len(reports) != 1 or reports[0].name != "TEST-" + SUITE + ".xml":
        raise GateFailure("semantic gate must produce exactly the selected EVAL-CFG-025 report")
    try:
        verify_suite(ET.parse(reports[0]).getroot())
    except (ET.ParseError, OSError) as exc:
        raise GateFailure("cannot read semantic report") from exc


def detector_self_test() -> None:
    def valid() -> ET.Element:
        suite = ET.Element("testsuite", name=SUITE, tests=str(len(EXPECTED_METHODS)),
                           failures="0", errors="0", skipped="0")
        for name in sorted(EXPECTED_METHODS):
            ET.SubElement(suite, "testcase", name=name, classname=SUITE)
        return suite

    verify_suite(valid())
    mutants = []
    zero = valid()
    zero.clear()
    zero.set("name", SUITE)
    zero.set("tests", "0")
    mutants.append(zero)
    for name in EXPECTED_METHODS:
        missing = valid()
        missing.remove(next(case for case in missing if case.get("name") == name))
        missing.set("tests", str(len(EXPECTED_METHODS) - 1))
        mutants.append(missing)
    for tag in ("skipped", "failure", "error"):
        bad_case = valid()
        ET.SubElement(bad_case[0], tag)
        mutants.append(bad_case)
    duplicate = valid()
    duplicate[0].set("name", duplicate[1].get("name", ""))
    mutants.append(duplicate)
    wrong_suite = valid()
    wrong_suite.set("name", "NoSuchSemanticTest")
    mutants.append(wrong_suite)
    for mutant in mutants:
        try:
            verify_suite(mutant)
        except GateFailure:
            continue
        raise GateConfigurationError("semantic detector accepted an incomplete/invalid report")
    print(f"[semantic] detector rejected {len(mutants)} invalid report fixtures", flush=True)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    try:
        detector_self_test()
        if args.self_test:
            return 0
        command = [command_path("mvn"), *local_repository_argument(), "-B", "-ntp",
                   "-pl", ":cfg-kernel", "-Dtest=" + SUITE, "clean", "test"]
        print("[semantic] RUN: " + " ".join(command), flush=True)
        result = subprocess.run(command, cwd=args.root, check=False)
        if result.returncode:
            raise GateFailure(f"selected semantic suite exited {result.returncode}")
        verify_reports(args.root / "cfg-kernel/target/surefire-reports")
        print(f"[semantic] PASS: EVAL-CFG-025, {len(EXPECTED_METHODS)} required tests, zero skipped; "
              "CFG-FIRST only, no complete AIR profile claim", flush=True)
        return 0
    except GateFailure as exc:
        print(f"[semantic] FAIL: {exc}", file=sys.stderr, flush=True)
        return 1
    except (GateConfigurationError, OSError) as exc:
        print(f"[semantic] ERROR: {exc}", file=sys.stderr, flush=True)
        return 2


if __name__ == "__main__":
    sys.exit(main())
