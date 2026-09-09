#!/usr/bin/env python3
"""Focal W3 mutants: compile, nominal RED, byte-exact restore and second GREEN."""
import argparse,difflib,gzip,hashlib,json,subprocess,sys,time
from pathlib import Path
from challenge_w1 import hashes
from check_w3 import ROOT
V='analysis-values/src/main/java/io/github/gustavo2358/analysis/values/'
Q='analysis-kernel/src/main/java/io/github/gustavo2358/analysis/query/'
S=V+'PossibleValuesState.java';C=V+'Candidates.java';P=V+'TextProfile.java';A=V+'PossibleValuesAnalysis.java';R=Q+'BatchReplayer.java'
DOMAIN='DomainTest#missingStrongUpdateAndJoinHaveIndependentExpected'
LINEAR='ValuesTest#realVerticalMixedBatchHasOneObservationPerLogicalQuery'
PARTIAL='ValuesTest#partialSourceIsSeparateFromExactModelAndUnknownWitness'
BACKWARD='ReplayTest#backwardReplayUsesStableOutAndReverseSuffixOnce'
MUTATIONS={
'full-state-clone':(S,[('var next=PersistentBindings.put(root,cell,value,w);','''var copied=new Object(){PersistentBindings.Node node=root;};
        if(PersistentBindings.get(root,cell,w)!=null){copied.node=null;PersistentBindings.each(root,(key,v)->copied.node=PersistentBindings.put(copied.node,key,v,w));}
        var next=PersistentBindings.put(copied.node,cell,value,w);''')],'DomainTest#persistentUpdatesShareAndRetainNoHistory','bounded update must not clone state'),
'missing-key-as-bottom':(S,[('return Candidates.UNKNOWN;','throw new IllegalStateException("missing binding incorrectly treated as bottom");')],DOMAIN,'missing binding incorrectly treated as bottom'),
'non-convergent-semantic-domain':(C,[('if(this==b)return this;','if(this==b)return new Candidates(singleton,many,!open);')],'DomainTest#allFiniteCandidatesSurviveAndUnionConverges','finite fixed point'),
'silent-truncation':(C,[('Arrays.copyOf(merged,k)','Arrays.copyOf(merged,Math.min(k,8))')],'DomainTest#allFiniteCandidatesSurviveAndUnionConverges','all finite candidates preserved'),
'unknown-effect-as-nop':(P,[('throw new Refusal(false,"UNSUPPORTED_EFFECT_PROFILE");','admitted.add(operation);')],'ValuesTest#unsupportedReadAndIndirectStorageAreNotIdentity','unmodeled write cannot be identity'),
'replay-per-query-site':(R,[('                for(var q:selected) {\n                    while(cursor!=q.boundary()) {','''                S originalAnchor=state;int originalCursor=cursor;
                for(var q:selected) {
                    state=originalAnchor;cursor=originalCursor;
                    while(cursor!=q.boundary()) {''')],'ValuesScaleTest#denseDuplicateQueriesShareSingleReplayAndNoStateRoots','one union replay, not replay per query'),
'partial-promoted-to-exact':(A,[('boolean source=profile.sourceOpen(query.subject(),query.point().entry());','boolean source=false;')],PARTIAL,'expected: <true> but was: <false>'),
'mutate-shared-root':(V+'PersistentBindings.java',[('final Candidates value;','Candidates value;'),('if(key==n.key)return n.value.equivalent(value)?n:new Node(key,value,n.left,n.right,w);','if(key==n.key){n.value=value;return n;}')],DOMAIN,'array contents differ'),
'storage-id-implies-disjoint':(P,[('if(cells.size()>1) {','if(false) {')],'ValuesTest#sameCellAliasesAndDisjointnessAreSemanticAdmission','storage IDs do not prove disjointness'),
'unsupported-query-aborts-batch':(R,[('if(reason!=null) { answers.put','if(reason!=null) { throw new ObservationException("mutant aborts mixed batch"); }\n                if(reason!=null) { answers.put')],LINEAR,'expected: <COMPLETE> but was: <FAILED>'),
'unsupported-query-disappears':(R,[('var answer=Objects.requireNonNull(answers.get(q));output.add(answer);','var answer=Objects.requireNonNull(answers.get(q));if(answer.status()==QueryStatus.UNSUPPORTED_POINT)continue;output.add(answer);')],LINEAR,'mixed batch complete query coverage'),
'stable-analysis-observation-failure-reported-as-full-success':(R,[('new ObservationBatch<>(Status.FAILED,"OBSERVATION_ERROR",List.of(),','new ObservationBatch<>(Status.COMPLETE,null,List.of(),')],'ReplayTest#controlledObservationFailureIsAtomicAndPreservesStableRun','failed observation is not complete'),
'performance-optimization-by-lost-candidates':(C,[('boolean remainder=open||b.open;','if(k>1)return this;\n        boolean remainder=open||b.open;')],'ValuesTest#diamondsMissingPathsStrongUpdatesAndOtherCells','expected:'),
'performance-optimization-by-unsupported-everything':(R,[('if(reason==null&&!projection.supports(q))','if(reason==null)')],LINEAR,'expected: not <null>'),
'candidate-count-causes-top':(C,[('boolean remainder=open||b.open;','boolean remainder=open||b.open||k>8;')],'DomainTest#allFiniteCandidatesSurviveAndUnionConverges','expected: <false> but was: <true>'),
'weak-instead-of-strong-assignment':(P,[('state.assign(write.location().ordinal(),write.value(),work)','state.assign(write.location().ordinal(),state.value(write.location().ordinal(),work).join(write.value(),work),work)')],'ValuesTest#diamondsMissingPathsStrongUpdatesAndOtherCells','expected:'),
'join-missing-path-closed':(S,[('a.withOpen(w)','a')],DOMAIN,'expected: <true> but was: <false>'),
'source-partial-opens-model':(A,[('boolean model=state.isReached()&&state.value(cell.ordinal(),replayWork).open();','boolean model=source||state.isReached()&&state.value(cell.ordinal(),replayWork).open();')],PARTIAL,'expected: <false> but was: <true>'),
'forward-replay-from-out':(R,[('S state=forward?result.in(context,node):result.out(context,node);','S state=result.out(context,node);')],LINEAR,'expected:'),
'backward-replay-from-in':(R,[('S state=forward?result.in(context,node):result.out(context,node);','S state=result.in(context,node);')],BACKWARD,'backward point anchor/order'),
'backward-replay-forward-order':(R,[('int offset=forward?cursor:cursor-1;','int offset=forward?cursor:sequence.instructions().size()+1-cursor;')],BACKWARD,'backward point anchor/order'),
'forward-replay-reverse-order':(R,[('int offset=forward?cursor:cursor-1;','int offset=forward?sequence.instructions().size()-cursor:cursor-1;')],LINEAR,'expected:'),
'after-terminator-value':(R,[('if(site.isTerminator())reason=PointReason.AFTER_TERMINATOR;','if(site.isTerminator()&&false)reason=PointReason.AFTER_TERMINATOR;'),('else if(p.outcome()!=Control.NormalOutcome.INSTANCE)reason=PointReason.OUTCOME_UNAVAILABLE;','else if(!site.isTerminator()&&p.outcome()!=Control.NormalOutcome.INSTANCE)reason=PointReason.OUTCOME_UNAVAILABLE;')],LINEAR,'after terminator unsupported'),
'duplicate-query-observation':(R,[('var queries=new LinkedHashSet<PointQuery<T>>();','var queries=new ArrayList<PointQuery<T>>();')],LINEAR,'expected: <null>'),
'foreign-stable-result':(R,[('if(!result.contains(context,context.entryNode()))','if(false)')],'ReplayTest#foreignStableRunCannotMasqueradeAsUnreachableInAnotherSession','foreign stable result must be rejected'),
'call-specific-dependency':(A,[('public static final String PROFILE=','public static Class<?> CallResolver(){return Object.class;}\n    public static final String PROFILE=')],None,'W3 forbidden dependency'),
'solver-rerun-per-batch':(A,[('            var replayWork=new ValuesWork();','            DataflowSolver.solve(profile.session,new PossibleValuesAnalysis(profile));\n            var replayWork=new ValuesWork();')],None,'W3 observation cannot rerun solver'),
'values-transitive-air-only':('analysis-values/pom.xml',[('''    <dependency>
      <groupId>io.github.gustavo2358</groupId>
      <artifactId>air-java</artifactId>
    </dependency>''','')],None,'W3 requires direct air-java dependency'),
}
# The unknown-write mutant removes the guard and skips preparing that unsupported assignment,
# keeping a valid Java pattern-variable scope and preserving compilation.
MUTATIONS['unknown-effect-as-nop']=(P,[('throw new Refusal(false,"UNSUPPORTED_EFFECT_PROFILE");','{ admitted.add(operation);return; }')],'ValuesTest#unsupportedReadAndIndirectStorageAreNotIdentity','unmodeled write cannot be identity')

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--only',choices=sorted(MUTATIONS));parser.add_argument('--output',type=Path,required=True);args=parser.parse_args();out=args.output;out.mkdir(parents=True,exist_ok=False)
    receipt={'source_head':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),'head_tree':subprocess.check_output(['git','rev-parse','HEAD^{tree}'],cwd=ROOT,text=True).strip(),'working_source_hashes':hashes(ROOT),'campaign':[]}
    def save():(out/'receipt.json').write_text(json.dumps(receipt,indent=2)+'\n')
    def run(name,cmd):
        start=time.monotonic();p=subprocess.run(cmd,cwd=ROOT,stdout=subprocess.PIPE,stderr=subprocess.STDOUT);raw=p.stdout;(out/(name+'.log.gz')).write_bytes(gzip.compress(raw,mtime=0))
        return dict(command=cmd,exit=p.returncode,elapsed_seconds=time.monotonic()-start,log=name+'.log.gz',sha256=hashlib.sha256(raw).hexdigest()),raw.decode(errors='replace')
    maven=['mvn','-B','-ntp','-pl','analysis-values','-am'];tests='BuildCfgContractTest,StructureTest,DomainTest,ValuesTest,ReplayTest,ValueOracleTest,ValuesScaleTest'
    receipt['baseline'],_=run('baseline',maven+['clean','test','-Dtest='+tests]);save()
    if receipt['baseline']['exit']:raise RuntimeError('baseline not GREEN')
    for name in ([args.only] if args.only else MUTATIONS):
        target,changes,test,diagnostic=MUTATIONS[name];path=ROOT/target;original=path.read_bytes();before=hashes(ROOT);row=dict(id=name,target=target,role='PRODUCTION',hashes_before=before,expected_diagnostic=diagnostic);receipt['campaign'].append(row)
        try:
            source=original.decode();mutant=source
            for old,new in changes:
                count=mutant.count(old)
                assert count==1 or (name=='unknown-effect-as-nop' and count==2),(name,count)
                mutant=mutant.replace(old,new)
            row['diff']=''.join(difflib.unified_diff(source.splitlines(True),mutant.splitlines(True),fromfile=target,tofile=target));path.write_text(mutant)
            row['compile'],_=run(name+'-compile',maven+['-DskipTests','clean','test-compile'])
            if row['compile']['exit']:raise RuntimeError('invalid attempt: compilation failed '+name)
            gate=maven+['test','-Dtest=BuildCfgContractTest,StructureTest,'+test] if test else [sys.executable,'-c','from pathlib import Path;import sys;sys.path.insert(0,"scripts/project");from check_w3 import verify_sources;verify_sources(Path("."))']
            row['red'],output=run(name+'-red',gate)
            row['expected_red']=row['red']['exit']!=0 and diagnostic in output and (test is None or test.split('#')[1] in output) and 'COMPILATION ERROR' not in output
            if not row['expected_red']:raise RuntimeError('mutant survived or non-nominal RED: '+name)
        finally:
            path.write_bytes(original);row['hashes_after']=hashes(ROOT);row['byte_exact_restore']=before==row['hashes_after'];save()
        if not row['byte_exact_restore']:raise RuntimeError('restore differs')
        row['second_green'],_=run(name+'-second-green',gate);save()
        if row['second_green']['exit']:raise RuntimeError('second GREEN failed: '+name)
        print('[w3-challenge] '+name+' compile / nominal RED / byte-exact restore / second GREEN',flush=True)
    receipt['final_green'],_=run('final-green',maven+['clean','test','-Dtest='+tests]);save()
    if receipt['final_green']['exit']:raise RuntimeError('final GREEN failed')
    print('[w3-challenge] PASS: '+str(len(receipt['campaign']))+' compiled mutants',flush=True)
if __name__=='__main__':main()
