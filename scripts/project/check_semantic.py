#!/usr/bin/env python3
"""Execute the explicitly inventoried EVAL-CFG-025, EVAL-CFG-028, EVAL-CFG-029 and EVAL-CFG-030 oracles; absent/skipped tests fail."""

from __future__ import annotations

import argparse
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ET
from pathlib import Path

from check_architecture import GateConfigurationError, GateFailure, command_path, local_repository_argument


SUITE_025 = "io.github.gustavo2358.analysis.cfg.domain.EvalCfg025Test"
SUITE_028 = "io.github.gustavo2358.analysis.cfg.domain.EvalCfg028Test"
SUITE_029 = "io.github.gustavo2358.analysis.cfg.domain.EvalCfg029Test"
SUITE_030 = "io.github.gustavo2358.analysis.cfg.domain.EvalCfg030Test"
# Manual obligations: do not infer this inventory from production code, test source, or reports.
EXPECTED_METHODS_025 = {
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
    "unknownRequiredCapabilityCannotBecomeSuccessfulCfg",
    "returnOperandsAndOriginsRemainSharedAndOrdered",
    "entryCollectionOrderCannotChooseTheActivationOrItsInitialLabel",
    "unavailableInventoryIsNotACompleteEmptyGraph",
    "unavailableUnitBodyIsNotInvented",
    "registeredCapabilityIdentityAloneDoesNotImplementItsSemantics",
}


EXPECTED_METHODS_028 = {
    "m1MatchesManualControlAndOrderedOccurrences",
    "allInstructionKindsRetainOperandsHeadersOriginsPrecisionAndGaps",
    "jumpUsesExplicitForwardTargetInsteadOfPhysicalOrSortedNeighbor",
    "backwardTargetAndPhysicalPermutationPreserveControlAndCfgIds",
    "alphaRenamePreservesControlUnderExplicitDomainCorrelation",
    "explicitSplitPreservesOriginalOperationOrderAndCorrelatedPoints",
    "displayAndOriginPresentationCannotChooseControlAndAreRetained",
    "missingJumpLabelIsInvalidIrWithoutRepairOrExternalTarget",
    "explicitSelfLoopIsPreservedWithoutInventedCompletion",
    "orphanJumpAndInstructionsRemainWithoutArtificialPredecessor",
    "haltDiffersFromReturnAndNeverFallsThroughOrReturnsNormally",
    "haltKindsAndSeparateOrphanOccurrencesKeepTheirOwnCorrelation",
    "jumpPreservesEveryActivationWithoutReachabilityFiltering",
    "sharedHaltPreservesBothActivationContextsAndDeterministicInventory",
    "jumpAndHaltNeverFuseHomonymousUnitNamespaces",
    "unsupportedTerminatorsInOrphansAreAllDiagnosedWithoutPartialGraph",
    "instructionCardinalityIsNotLimitedByCapabilityOrIds",
    "memoryRegionsConsumptionIsDeclaredPreciselyForControlOnly",
    "missingMemoryManifestStillFailsAirValidation",
    "anotherMemoryCapabilityVersionIsNotInvented",
    "memorySupportDoesNotAbsorbControlOrUnknownCapabilities",
    "haltNavigationReusesMaterializedImmutableInventory",
}
EXPECTED_METHODS_029 = {
    "diamondMatchesManualOracleWithoutSiblingOrImplicitJoinEdges",
    "literalTrueDoesNotPruneFalseAlternative",
    "literalFalseDoesNotPruneTrueAlternative",
    "unknownBooleanRetainsPredicateDependenciesReasonTypeAndOriginByIdentity",
    "unknownTypePredicateIsInvalidWithoutBooleanInference",
    "incorrectPredicateRoleIsRejectedByPreflight",
    "missingTrueTargetIsInvalidWithoutRepair",
    "missingFalseTargetIsInvalidWithoutRepair",
    "targetInAnotherUnitIsInvalidEvenWithSameLocalLabel",
    "emptyFalseArmUsesExplicitJoinWithoutSyntheticNodesOrOperations",
    "nestedBranchesUseTheirOwnExplicitDestinations",
    "terminatingHaltArmNeverReconvergesOrFallsThrough",
    "terminatingReturnArmNeverReconvergesOrFallsThrough",
    "sameDestinationPreservesTwoAlternativesAndThePredicate",
    "physicalPermutationPreservesCorrelatedControlAndCfgIds",
    "alphaRenamePreservesControlOperandsAndOriginsUnderExplicitCorrelation",
    "displayAndOriginPresentationCannotSelectTargets",
    "sequenceSplitPreservesBranchContinuationAndOriginalPoints",
    "multipleEntriesPreserveEveryBranchActivationWithoutReachabilityFiltering",
    "orphanBranchKeepsBothRulesWithoutArtificialPredecessor",
    "unsupportedOrphanStillBlocksAnOtherwiseValidBranchGraph",
    "all258BranchOccurrencesRetainBothExplicitAlternatives",
    "graphRejectsWrongBranchArmsTargetsAndEndpointKinds",
    "graphRejectsForeignOrMissingBranchActivationEntries",
    "sameDestinationTransitionEqualityKeepsArmsAndRejectsExactDuplicates",
}
EXPECTED_METHODS_030 = {
    "defaultProjectsBothPartialInventoriesWithExactReturnOracle",
    "allPublicDefaultsSelectKnownSubsetAndNullPolicyIsRejected",
    "knownSubsetCompleteBuildsManualReturnGraph",
    "strictCompleteBuildsManualReturnGraph",
    "knownSubsetAllowsPartialAtEitherScopeIndependently",
    "strictRejectsPartialPublicationWithTypedSubject",
    "strictRejectsPartialUnitWithTypedSubject",
    "strictReportsBothIncompleteScopesInStableOrder",
    "unavailableInventoryIsRejectedAtEitherScopeUnderBothPolicies",
    "emptyPartialInventoryRemainsPartialWithoutInventedNodes",
    "unsupportedOrphanTerminatorIsNeverSilentlyOmitted",
    "unsupportedCapabilityStillBlocksBeforeProjectionPolicy",
    "registeredKnownCapabilityStillRequiresImplementedSemantics",
    "incompleteValidationCannotBeOverriddenByInventoryPolicy",
    "invalidReferencesAndUnexplainedPartialRemainInvalidAir",
    "unavailableBodyStillBlocksKnownControlProjection",
    "validationLimitsStillBlockBeforeProjection",
    "partialCoverageItemsPremisesAndDimensionalEvidenceRemainOriginal",
    "gapCodesReasonsAndOriginTextCannotDecideAdmissionOrControl",
    "partialMixedControlKeepsAllArmsContextsOrphansAndOrdering",
}
EXPECTED_SUITES = {
    SUITE_025: EXPECTED_METHODS_025,
    SUITE_028: EXPECTED_METHODS_028,
    SUITE_029: EXPECTED_METHODS_029,
    SUITE_030: EXPECTED_METHODS_030,
}


def verify_suite(suite: ET.Element, expected_suite: str) -> None:
    if suite.tag != "testsuite" or suite.get("name") != expected_suite:
        raise GateFailure("expected exactly the selected semantic suite: " + expected_suite)
    required = EXPECTED_SUITES[expected_suite]
    cases = suite.findall("testcase")
    names = [case.get("name") for case in cases]
    if set(names) != required or len(names) != len(required):
        raise GateFailure(expected_suite + " method inventory mismatch (missing, duplicate, foreign or zero tests)")
    try:
        if int(suite.get("tests", "0")) != len(required):
            raise GateFailure("semantic report count disagrees with required cases")
        if any(int(suite.get(key, "-1")) != 0 for key in ("failures", "errors", "skipped")):
            raise GateFailure("semantic report has failed, errored, skipped or missing counts")
    except ValueError as exc:
        raise GateFailure("invalid semantic report counts") from exc
    if any(case.get("classname") != expected_suite or len(case.findall("failure"))
           or len(case.findall("error")) or len(case.findall("skipped")) for case in cases):
        raise GateFailure("semantic testcase is foreign, failed or skipped")


def verify_reports(directory: Path) -> None:
    reports = list(directory.glob("TEST-*.xml"))
    expected = {"TEST-" + name + ".xml" for name in EXPECTED_SUITES}
    if {p.name for p in reports} != expected:
        raise GateFailure("semantic gate must produce exactly the selected EVAL-CFG-025/028/029/030 reports")
    try:
        for name in EXPECTED_SUITES:
            verify_suite(ET.parse(directory / ("TEST-" + name + ".xml")).getroot(), name)
    except (ET.ParseError, OSError) as exc:
        raise GateFailure("cannot read semantic report") from exc


def detector_self_test() -> None:
    def valid(name: str) -> ET.Element:
        methods = EXPECTED_SUITES[name]
        suite = ET.Element("testsuite", name=name, tests=str(len(methods)),
                           failures="0", errors="0", skipped="0")
        for method in sorted(methods):
            ET.SubElement(suite, "testcase", name=method, classname=name)
        return suite

    count = 0
    for name, methods in EXPECTED_SUITES.items():
        verify_suite(valid(name), name)
        mutants = []
        zero = valid(name)
        zero.clear()
        zero.set("name", name)
        zero.set("tests", "0")
        mutants.append(zero)
        for method in methods:
            missing = valid(name)
            missing.remove(next(case for case in missing if case.get("name") == method))
            missing.set("tests", str(len(methods) - 1))
            mutants.append(missing)
        for tag in ("skipped", "failure", "error"):
            bad_case = valid(name)
            ET.SubElement(bad_case[0], tag)
            mutants.append(bad_case)
        duplicate = valid(name)
        duplicate[0].set("name", duplicate[1].get("name", ""))
        mutants.append(duplicate)
        foreign = valid(name)
        foreign[0].set("classname", "ForeignTest")
        mutants.append(foreign)
        unknown = valid(name)
        unknown[0].set("name", "unregisteredNewTest")
        mutants.append(unknown)
        extra = valid(name)
        ET.SubElement(extra, "testcase", name="unregisteredExtraTest", classname=name)
        extra.set("tests", str(len(methods) + 1))
        mutants.append(extra)
        wrong_suite = valid(name)
        wrong_suite.set("name", "NoSuchSemanticTest")
        mutants.append(wrong_suite)
        for mutant in mutants:
            try:
                verify_suite(mutant, name)
            except GateFailure:
                count += 1
                continue
            raise GateConfigurationError("semantic detector accepted an incomplete/invalid report")

    with tempfile.TemporaryDirectory(prefix="cfg-semantic-detector-") as temporary:
        directory = Path(temporary)
        for name in EXPECTED_SUITES:
            ET.ElementTree(valid(name)).write(directory / ("TEST-" + name + ".xml"))
        verify_reports(directory)
        for name in EXPECTED_SUITES:
            path = directory / ("TEST-" + name + ".xml")
            original = path.read_bytes()
            path.unlink()
            try:
                verify_reports(directory)
            except GateFailure:
                count += 1
            else:
                raise GateConfigurationError("semantic detector accepted a missing suite")
            finally:
                path.write_bytes(original)
        foreign = directory / "TEST-Foreign.xml"
        ET.ElementTree(valid(SUITE_025)).write(foreign)
        try:
            verify_reports(directory)
        except GateFailure:
            count += 1
        else:
            raise GateConfigurationError("semantic detector accepted a duplicate/foreign suite report")
    print(f"[semantic] detector rejected {count} invalid report fixtures", flush=True)


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
                   "-pl", ":cfg-kernel", "-Dtest=" + ",".join(EXPECTED_SUITES), "clean", "test"]
        print("[semantic] RUN: " + " ".join(command), flush=True)
        result = subprocess.run(command, cwd=args.root, check=False)
        if result.returncode:
            raise GateFailure(f"selected semantic suite exited {result.returncode}")
        verify_reports(args.root / "cfg-kernel/target/surefire-reports")
        print(f"[semantic] PASS: EVAL-CFG-025/028/029/030, {sum(map(len, EXPECTED_SUITES.values()))} required tests, "
              "zero skipped; CFG-FIRST + linear/Jump/Branch/Halt + inventory policy, no complete AIR profile claim", flush=True)
        return 0
    except GateFailure as exc:
        print(f"[semantic] FAIL: {exc}", file=sys.stderr, flush=True)
        return 1
    except (GateConfigurationError, OSError) as exc:
        print(f"[semantic] ERROR: {exc}", file=sys.stderr, flush=True)
        return 2


if __name__ == "__main__":
    sys.exit(main())
