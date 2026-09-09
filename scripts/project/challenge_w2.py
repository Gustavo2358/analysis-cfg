#!/usr/bin/env python3
"""Compilable W2 mutants with nominal RED, byte-exact restore and a second GREEN."""
from __future__ import annotations
import argparse
import difflib
import gzip
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import time
from challenge_w1 import hashes
from check_w2 import ROOT

SOURCE='analysis-kernel/src/main/java/io/github/gustavo2358/analysis/solver/'
TEST='analysis-kernel/src/test/java/io/github/gustavo2358/analysis/solver/'
S=SOURCE+'DataflowSolver.java'; T=SOURCE+'SolverTopology.java'; O=TEST+'OracleTest.java'; A=TEST+'SyntheticAnalyses.java'
POP='            run.transfers = Math.incrementExact(run.transfers);'
MUTATIONS={
 'predecessor-recomputation':(S,POP,POP+'''
            if (forward) {
                S recomposed = definition.bottom();
                for (var boundary : definition.boundaries(session))
                    if (graph.require(boundary.context(), boundary.node()).ordinal == p)
                        recomposed = definition.joinInto(recomposed, boundary.state(), work).state();
                for (int r = graph.backwardHead[p]; r != -1; r = graph.backwardNext[r]) {
                    int source = graph.from[r];
                    S value = definition.transferEdge(graph.points[source], graph.edges[r], run.publication(source, true), work);
                    recomposed = definition.joinInto(recomposed, value, work).state();
                }
                run.anchors[p] = recomposed;
            }''','SolverScaleTest#s4bStaggeredFanInHasLinearDeliveriesAndQuadraticRecompositionReads','S4b no production predecessor recomposition'),
 'propagate-unchanged-out':(S,'if (!first && definition.equivalent(', 'if (false && !first && definition.equivalent(','SolverPropertiesTest#equivalentFreshRootsStopPropagationAndKeepPublishedRoot','fixture must reevaluate equivalent allocated roots'),
 'enqueue-unchanged-join':(S,'run.accumulatorUnchanged = Math.incrementExact(run.accumulatorUnchanged);','run.accumulatorUnchanged = Math.incrementExact(run.accumulatorUnchanged); run.enqueueIfAbsent(destination);','SolverPropertiesTest#coalescedArrivalsSuppressDuplicateEnqueueAndSubsumedJoins','unchanged join never enqueues'),
 'lose-self-loop-reenqueue':(S,'            run.queued[p] = false;','            // mutant leaves popped membership set','SolverPropertiesTest#selfLoopReenqueuesAfterMembershipIsCleared','self loop reaches finite height'),
 'reapply-entry-seed':(S,POP,POP+'''
            for (var boundary : definition.boundaries(session)) {
                int seeded = graph.require(boundary.context(), boundary.node()).ordinal;
                run.boundaryJoins = Math.incrementExact(run.boundaryJoins);
                run.anchors[seeded] = definition.joinInto(run.anchor(seeded), boundary.state(), work).state();
            }''','SolverPropertiesTest#multipleBoundariesJoinOnceOnCorrectSideInBothDirections','boundaries joined once, never per pop'),
 'backward-only-at-exits':(S,'            run.initializationAttempts = Math.incrementExact(run.initializationAttempts);','            if (!forward && graph.forwardHead[p] != -1) continue;\n            run.initializationAttempts = Math.incrementExact(run.initializationAttempts);','SolverPropertiesTest#firstBottomPublicationAndExitlessSccAreProcessed','first bottom publication must deliver'),
 'identity-only-equality':(S,'definition.equivalent(run.publication(p, false), candidate, work)','run.publication(p, false) == candidate','SolverPropertiesTest#equivalentFreshRootsStopPropagationAndKeepPublishedRoot','fixture must reevaluate equivalent allocated roots'),
 'solver-concrete-domain-dependency':(S,'    private DataflowSolver() { }','    private DataflowSolver() { }\n    public static Class<?> concreteDomain() { return io.github.gustavo2358.air.model.Operations.Assign.class; }',None,'W2 forbidden solver/SPI dependency'),
 'backward-replay-from-wrong-anchor':(O,'long state=result.out(c,n);','long state=result.in(c,n);','OracleTest#backwardAnchorsAndReverseOperationOrderAreObservable','array contents differ'),
 'backward-replay-forward-order':(O,'for(int i=ops.length-1;i>=0;i--) {','for(int i=0;i<ops.length;i++) {','OracleTest#backwardAnchorsAndReverseOperationOrderAreObservable','array contents differ'),
 'shared-wrong-transfer-agreement':(A,'case SET_X -> v|1;','case SET_X -> v|2;','OracleTest#validForwardTransferPassesConcreteAndPrecisionOracle','concrete inclusion OUT'),
 'max-visits-aborts-analysis':(S,POP,POP+'\n            if (run.pops > 1500) throw new IllegalStateException("mutant visit budget exhausted");','SolverScaleTest#s4AndS16LinearAndCycleSizesRemainStableInBothDirections','mutant visit budget exhausted'),
 'resource-budget-changes-semantic-result':(S,POP,POP+'\n            if (run.queue.size() > 1500) break;','SolverScaleTest#s4AndS16LinearAndCycleSizesRemainStableInBothDirections','scale exact forward fact'),
 'missing-first-publication':(S,'if (!first && definition.equivalent(', 'if (definition.equivalent(','SolverPropertiesTest#firstBottomPublicationAndExitlessSccAreProcessed','first bottom publication must deliver'),
 'skip-edge-transfer':(S,'definition.transferEdge(graph.points[p], graph.edges[e], candidate, work)','candidate','SolverTest#forwardBoundaryBlockAndEdgeHaveProgramOrderMeaning','expected:'),
 'skip-block-transfer':(S,'definition.transferBlock(graph.points[p], run.anchor(p), work)','run.anchor(p)','SolverTest#forwardBoundaryBlockAndEdgeHaveProgramOrderMeaning','expected:'),
 'apply-block-twice':(S,'definition.transferBlock(graph.points[p], run.anchor(p), work)','definition.transferBlock(graph.points[p], definition.transferBlock(graph.points[p], run.anchor(p), work), work)','OracleTest#finiteDomainLawsAndNonIdempotentBlockTransfer','apply block exactly once'),
 'block-uses-wrong-state':(S,'definition.transferBlock(graph.points[p], run.anchor(p), work)','definition.transferBlock(graph.points[p], run.publication(p, false), work)','SolverTest#forwardBoundaryBlockAndEdgeHaveProgramOrderMeaning','expected:'),
 'boundary-omitted':(S,'if (joined.changed()) run.anchors[p] = joined.state();','// mutant omits boundary contribution','SolverPropertiesTest#multipleBoundariesJoinOnceOnCorrectSideInBothDirections','boundary anchor side'),
 'wrong-boundary-side':(S,'if (joined.changed()) run.anchors[p] = joined.state();','if (joined.changed()) run.publications[p] = joined.state();','SolverPropertiesTest#multipleBoundariesJoinOnceOnCorrectSideInBothDirections','boundary anchor side'),
 'ignore-self-loop':(S,'int destination = destinations[e];','int destination = destinations[e]; if (destination == p) continue;','SolverPropertiesTest#selfLoopReenqueuesAfterMembershipIsCleared','self loop reaches finite height'),
 'ignore-context-state':(T,'var nodes = new IdentityHashMap<ProgramIndex.Node, AnalysisPoint>();','var nodes = lookup.isEmpty() ? new IdentityHashMap<ProgramIndex.Node, AnalysisPoint>() : lookup.values().iterator().next();','OracleTest#distinctContextsRemainIsolatedAndUnselectedEntriesCreateNoPoints','expected:'),
 'duplicate-enqueue':(S,'if (queued[point]) { duplicates', 'if (false && queued[point]) { duplicates','SolverPropertiesTest#coalescedArrivalsSuppressDuplicateEnqueueAndSubsumedJoins','coalesced diamond enqueues each point once'),
 'wrong-backward-direction':(S,'int[] destinations = forward ? graph.to : graph.from;','int[] destinations = graph.to;','OracleTest#backwardAnchorsAndReverseOperationOrderAreObservable','backward stable OUT anchor'),
 'wrong-backward-publication':(S,'definition.transferEdge(graph.points[p], graph.edges[e], candidate, work)','definition.transferEdge(graph.points[p], graph.edges[e], forward ? candidate : run.anchor(p), work)','OracleTest#backwardAnchorsAndReverseOperationOrderAreObservable','backward propagates IN to predecessor OUT'),
 'max-iterations':(S,POP,POP+'\n            if (run.pops > 1500) break;','SolverScaleTest#s4AndS16LinearAndCycleSizesRemainStableInBothDirections','scale exact backward fact'),
}

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--only',choices=sorted(MUTATIONS));parser.add_argument('--output',type=Path);args=parser.parse_args()
    out=args.output or ROOT/'.harness-results/WORK-CFG-028/wave-2'/('challenge-'+args.only if args.only else 'challenges')
    out.mkdir(parents=True,exist_ok=False)
    receipt={'source_head':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),'head_tree':subprocess.check_output(['git','rev-parse','HEAD^{tree}'],cwd=ROOT,text=True).strip(),'working_source_hashes':hashes(ROOT),'campaign':[]}
    def save(): (out/'receipt.json').write_text(json.dumps(receipt,indent=2)+'\n')
    def run(name,cmd):
        started=time.monotonic();p=subprocess.run(cmd,cwd=ROOT,stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
        raw=p.stdout;(out/(name+'.log.gz')).write_bytes(gzip.compress(raw,mtime=0))
        return {'command':cmd,'exit':p.returncode,'elapsed_seconds':time.monotonic()-started,'log':name+'.log.gz','sha256':hashlib.sha256(raw).hexdigest()},raw.decode(errors='replace')
    maven=['mvn','-B','-ntp'];tests='BuildCfgContractTest,SolverTest,OracleTest,SolverPropertiesTest,SolverScaleTest'
    receipt['baseline'],_=run('baseline',maven+['-pl','analysis-kernel','-am','clean','test','-Dtest='+tests,'-Dsurefire.failIfNoSpecifiedTests=false']);save()
    if receipt['baseline']['exit']: raise RuntimeError('baseline not GREEN')
    for name in ([args.only] if args.only else MUTATIONS):
        target,old,new,test,diagnostic=MUTATIONS[name];path=ROOT/target;original=path.read_bytes();before=hashes(ROOT)
        row={'id':name,'target':target,'role':'TEST_ONLY_ORACLE' if target.startswith(TEST) else 'PRODUCTION','hashes_before':before,'expected_diagnostic':diagnostic};receipt['campaign'].append(row)
        try:
            source=original.decode();assert source.count(old)==1,(name,source.count(old));mutant=source.replace(old,new)
            row['diff']=''.join(difflib.unified_diff(source.splitlines(True),mutant.splitlines(True),fromfile=target,tofile=target));path.write_text(mutant)
            row['compile'],_=run(name+'-compile',maven+['-pl','analysis-kernel','-am','-DskipTests','clean','test-compile'])
            if row['compile']['exit']: raise RuntimeError('invalid attempt: compilation failed '+name)
            gate=maven+['-pl','analysis-kernel','-am','test','-Dtest=BuildCfgContractTest,'+test,'-Dsurefire.failIfNoSpecifiedTests=false'] if test else [sys.executable,'-c','from pathlib import Path; import sys; sys.path.insert(0,"scripts/project"); from check_w2 import verify_sources; verify_sources(Path("."))']
            row['red'],output=run(name+'-red',gate)
            row['expected_red']=row['red']['exit']!=0 and diagnostic in output and (test is None or test.split('#')[1] in output) and ('COMPILATION ERROR' not in output)
            if not row['expected_red']: raise RuntimeError('mutant survived or non-nominal RED: '+name)
        finally:
            path.write_bytes(original);row['hashes_after']=hashes(ROOT);row['byte_exact_restore']=before==row['hashes_after'];save()
        if not row['byte_exact_restore']: raise RuntimeError('restore differs: '+name)
        row['second_green'],_=run(name+'-second-green',gate);save()
        if row['second_green']['exit']: raise RuntimeError('second GREEN failed: '+name)
        print('[w2-challenge] '+name+' compile / nominal RED / byte-exact restore / second GREEN',flush=True)
    receipt['final_green'],_=run('final-green',maven+['-pl','analysis-kernel','-am','clean','test','-Dtest='+tests,'-Dsurefire.failIfNoSpecifiedTests=false']);save()
    if receipt['final_green']['exit']: raise RuntimeError('final GREEN failed')
    print('[w2-challenge] PASS: '+str(len(receipt['campaign']))+' compiled mutants',flush=True)
if __name__=='__main__':main()
