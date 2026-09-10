#!/usr/bin/env python3
"""W1 focal compilable mutants: baseline GREEN, semantic/architecture RED, exact restore, second GREEN."""
from __future__ import annotations
import argparse
import difflib
import gzip
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import time
from check_w1 import ROOT

SOURCE='analysis-kernel/src/main/java/io/github/gustavo2358/analysis/structure/'
B=SOURCE+'IndexBuilder.java'; C=SOURCE+'ContextView.java'; P=SOURCE+'ProgramIndex.java'
MUTATIONS={
 'missing-required-edge':(B,'valid(count.edges == expectedEdges, "missing required contextual edge");','// mutant omits edge completeness','AdmissionTest#missingBranchEdgeIsInvalid'),
 'missing-required-sequence':(B,'valid(sequenceNodes.size() == sequences.size(), "missing required SequenceNode");','if (sequenceNodes.size() != sequences.size()) expectedEdges = result.graph().orElseThrow().transitions().size();','AdmissionTest#missingOrphanIsInvalid'),
 'foreign-sequence-source':(B,'sequences.get(n.source().label()) == n.source()','Objects.equals(sequences.get(n.source().label()), n.source())','AdmissionTest#equalLookingReplacementSequenceIsInvalid'),
 'foreign-publication-entry':(B,'graph.publication() == snapshot','graph.publication().equals(snapshot)','AdmissionTest#equalLookingForeignSnapshotIsInvalid'),
 'duplicated-wrong-contextual-edge':(B,'valid((roles & bit) == 0, "duplicate semantic contextual edge");','// mutant accepts repeated semantic role','AdmissionTest#duplicateAndWrongContextEdgesAreInvalid'),
 'identity-by-display-name':(P,'public Memory.ObjectDeclaration object(ObjectId id) { return objects.get(id); }','public Memory.ObjectDeclaration object(ObjectId id) { var requested = objects.get(id); if (requested == null) return null; for (var object : objects.values()) if (object.displayName().equals(requested.displayName())) return object; return null; }','StructureTest#fullOwnersAndDisplayRenamingDoNotCollide'),
 'ignore-activation-entry':(C,'ordinal = index.entryOrdinals.get(entry.id());','ordinal = 0;','StructureTest#multipleEntriesKeepEntryReturnAndContextSeparate'),
 'scan-objects-per-assign':(B,'Memory.ObjectDeclaration declaration = objects.get(id);','Memory.ObjectDeclaration declaration = null; for (var candidate : objects.values()) { count.visit("objects.referenceScan"); if (candidate.id().equals(id)) declaration = candidate; }','ScaleTest#s2WideDeclarations'),
 'scan-global-successors':(C,'''int head = (forward ? index.forwardHeads : index.backwardHeads).get(key);
        return new EdgeCursor(index, forward ? index.forwardNext : index.backwardNext, head);''','''int head = -1; long examined = 0;
        for (int i = index.edges.length - 1; i >= 0; i--) {
            examined = Math.incrementExact(examined);
            if (index.entry[i] == ordinal && (forward ? index.from[i] : index.to[i]) == node.ordinal) head = i;
        }
        EdgeCursor cursor = new EdgeCursor(index, forward ? index.forwardNext : index.backwardNext, head);
        cursor.edgesVisited = examined;
        return cursor;''','ScaleTest#s4AdjacencyIsLinear'),
 'transitive-air-only':('cfg-launcher/pom.xml','''    <dependency>
      <groupId>io.github.gustavo2358</groupId>
      <artifactId>air-java</artifactId>
    </dependency>
''','',None),
}
for name,expression in {
 'size-cap-rejects-valid-program':'snapshot.units().getFirst().sequences().size()',
 'max-nodes-admission':'graph.nodes().size()',
 'max-edges-admission':'graph.transitions().size()',
 'max-operations-admission':'graph.publication().units().getFirst().sequences().getLast().instructions().size()',
 'max-objects-admission':'snapshot.units().getFirst().objects().size()',
}.items():
    anchor='valid(graph.publication() == snapshot, "foreign Publication instance");'
    MUTATIONS[name]=(B,anchor,anchor+'\n        supported('+expression+' <= 1500, "mutant capacity refusal");','ScaleTest#s16EverySizeOfSameProfileIsAdmitted')

def hashes(root):
    return {str(p.relative_to(root)):hashlib.sha256(p.read_bytes()).hexdigest() for p in root.rglob('*') if p.is_file() and (p.suffix=='.java' or p.name=='pom.xml') and not any(part in {'.git','target','.harness-results','.cache'} for part in p.relative_to(root).parts)}

def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--only',choices=sorted(MUTATIONS));args=parser.parse_args()
    out=ROOT/'.harness-results/WORK-CFG-028/wave-1'/('challenge-'+args.only if args.only else 'challenges')
    out.mkdir(parents=True,exist_ok=False)
    receipt={'source_head':subprocess.check_output(['git','rev-parse','HEAD'],cwd=ROOT,text=True).strip(),'source_tree':subprocess.check_output(['git','rev-parse','HEAD^{tree}'],cwd=ROOT,text=True).strip(),'working_source_hashes':hashes(ROOT),'campaign':[]}
    def run(name,cmd):
        start=time.monotonic();p=subprocess.run(cmd,cwd=ROOT,stdout=subprocess.PIPE,stderr=subprocess.STDOUT)
        raw=p.stdout;(out/(name+'.log.gz')).write_bytes(gzip.compress(raw,mtime=0))
        return {'command':cmd,'exit':p.returncode,'elapsed_seconds':time.monotonic()-start,'log':name+'.log.gz','sha256':hashlib.sha256(raw).hexdigest()},raw.decode(errors='replace')
    maven=['mvn','-B','-ntp']
    baseline,log=run('baseline',maven+['clean','test']);receipt['baseline']=baseline
    (out/'receipt.json').write_text(json.dumps(receipt,indent=2)+'\n')
    if baseline['exit']: raise RuntimeError('baseline not GREEN')
    for name in ([args.only] if args.only else MUTATIONS):
        target,old,new,test=MUTATIONS[name];path=ROOT/target;original=path.read_bytes();before=hashes(ROOT)
        row={'id':name,'target':target,'hashes_before':before};receipt['campaign'].append(row)
        try:
            source=original.decode();assert source.count(old)==1,(name,source.count(old));mutant=source.replace(old,new)
            row['diff']=''.join(difflib.unified_diff(source.splitlines(True),mutant.splitlines(True),fromfile=target,tofile=target));path.write_text(mutant)
            compile_log,_=run(name+'-compile',maven+['-DskipTests','clean','test-compile']);row['compile']=compile_log
            if compile_log['exit']: raise RuntimeError('invalid attempt: compilation failed '+name)
            if test:
                red,output=run(name+'-red',maven+['-pl','analysis-kernel','-am','test','-Dtest=BuildCfgContractTest,'+test,'-Dsurefire.failIfNoSpecifiedTests=false'])
                legitimate=red['exit']!=0 and 'org.opentest4j.AssertionFailedError' in output and test.split('#')[1] in output
            else:
                red,output=run(name+'-red',[sys.executable,'scripts/project/check_analysis_architecture.py'])
                legitimate=red['exit']!=0 and 'AIR imports require direct compile air-java dependency: cfg-launcher/pom.xml' in output
            row['red']=red;row['expected_red']=legitimate
            if not legitimate: raise RuntimeError('mutant survived or non-nominal RED: '+name)
        finally:
            path.write_bytes(original);row['hashes_after']=hashes(ROOT);row['byte_exact_restore']=before==row['hashes_after']
            (out/'receipt.json').write_text(json.dumps(receipt,indent=2)+'\n')
        if not row['byte_exact_restore']: raise RuntimeError('restore differs: '+name)
        second,_=run(name+'-second-green',maven+['-pl','analysis-kernel','-am','test','-Dtest=BuildCfgContractTest,'+test,'-Dsurefire.failIfNoSpecifiedTests=false'] if test else [sys.executable,'scripts/project/check_analysis_architecture.py'])
        row['second_green']=second
        (out/'receipt.json').write_text(json.dumps(receipt,indent=2)+'\n')
        if second['exit']: raise RuntimeError('second GREEN failed: '+name)
        print('[w1-challenge] '+name+' compile OK / expected RED / byte-exact restore / second GREEN',flush=True)
    final,_=run('final-green',maven+['clean','test']);receipt['final_green']=final
    (out/'receipt.json').write_text(json.dumps(receipt,indent=2)+'\n')
    if final['exit']: raise RuntimeError('final GREEN failed')
    print('[w1-challenge] PASS: '+str(len(receipt['campaign']))+' real compilable mutants',flush=True)

if __name__=='__main__': main()
