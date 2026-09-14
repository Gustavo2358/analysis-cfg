# CICS Program Control

Session-authorized CICS-W0–W4, no merge. AIR normative pin remains
31893d1f4d203d19a61a750e2c4220120d9dab84, §§4.7, 5 and 9.

W0 uses the existing generic Invoke: action call for LINK, execute for XCTL,
namespace cics.program, ExtensionName cics-ts.program@1. Name capabilities are
structurally checked upstream and do not change CFG control. Unknown policies
must keep an interpretation remainder in the dependency consumer.

Named exception edges are outside the current CFG surface. The selected lean
route preserves local command conditions as a bounded control remainder: union
of explicit local labels and external unit control. It never calls an error a
normal return. LINK has an explicit normal continuation plus its remainder.
XCTL successful transfer has external control, no normal exit, halt or invented
return. With unknown handlers the local remainder stays conservative.

OpenControl must consult Invoke remainder with both empty and nonempty known
outcomes. The old known-nonempty guard reproduced false unreachability in the
real dependency/value query. Membership stays lazy; no dense graph or new solver.
The cost is unchanged per membership query; union visits its declared members.

W0 probes: CicsInvokeRouteTest (JSON/queries/names) and MultiCallModelTest (CALL).
They prove explicit external scope excludes local labels, whereas local error
labels and known-plus-local-remainder preserve BEFORE candidates and supports.

The shared dependency planner registers CICS and COBOL sites in the same prepared
batches. The CICS consumer independently verifies an eight-byte IBM1047 physical
area before interpreting computed values. The minimal naming subset accepts
A-Z, digits, $, @ and #, with trailing spaces removed; raw spelling is retained.
Lowercase or other names remain observed with interpretation uncertainty. This
profile does not uppercase names or resolve installation/catalog/runtime links.
IBM authority: PROGRAM resource attributes and PROGRAM operand rules for CICS TS.

Dependency JSON 1.1.0 carries technology, command, namespace and nameProfile per
site; literal support distinguishes CICS_LITERAL from CALL_LITERAL. The strict
independent reader checks those fields. Existing source/value/control remainders
remain separate; signature uncertainty is not mistaken for zero arguments.
CicsTargetTimingTest proves real physical RD/value facts before the Invoke and
unknown writes at its local successor, including overlapping COMMAREA.

Local W4 acceptance uses `scripts/project/e2e_cics_program_control.py` with prepared
frontend/lower runtime classpaths and an isolated Maven repository. Its 27 source
cases traverse all production CLIs and independently check C01–C18 together with
the parser, version/capability and query tests. `e2e_cics_cohort.py` checks the four
unchanged CardDemo sources at the documented upstream SHA; source incompleteness
and stage blockers are reported separately. It does not promise closed targets.

`challenge_cics_program_control.py` executes compiling mutations of remainder
handling, the name interpreter and BEFORE timing. Each mutant must cause an
actual semantic test failure; source bytes are restored exactly and a second
GREEN is required. Use an isolated checkout with no concurrent build or consumer.
The other control challenges use the independent E2E expectations: LINK retains
normal return, XCTL has no success return, RESP/NOHANDLE remains reachable, default
entry with its explicit premise is unreachable, and unknown handlers remain open.
