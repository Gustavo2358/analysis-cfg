#!/usr/bin/env python3
"""Ten focal implementation mutations in a disposable source copy. Local only."""
import argparse
import os
from pathlib import Path
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'scripts/harness'))
from lean import require_local

BASE = 'analysis-dependencies/src/main/java/io/github/gustavo2358/analysis/dependencies/'
CONSUMER = BASE + 'CallDependencyConsumer.java'
APPLICATION = BASE + 'DependencyAnalysis.java'
CFG = 'cfg-kernel/src/main/java/io/github/gustavo2358/analysis/cfg/domain/CoreCfgProjection.java'
PROBES = {
    'W2dModelTest': ['closedUnknownBooleanJoinKeepsTwoPaddedCandidatesAndSpecificSupports',
                   'openUnknownBooleanJoinKeepsCandidateAndNaturalRemainder',
                   'unreachableAssignmentCannotContaminateEitherJoin',
                   'physicalSequencePermutationPreservesBothResults',
                   'cfgUsesExplicitEntryAndExistingLabeledEdges',
                   'w1SingletonAndLiteralKeepTheirCandidatesAndSupports'],
    'W1dDependencyTest': ['rawValueExistsButDependencyMustBeProduced',
                         'literalHasNoPossibleValuesPreparationOrRun',
                         'noMoveIsReachableOpenWithoutInventedProgram'],
}

# Inject corruptions at the AIR application boundary in the disposable implementation.
# The original public AIR fixtures and their expected results remain unchanged.
FAULT = '''
    private static Publication fault(Publication p) {
        var units=new ArrayList<io.github.gustavo2358.air.model.Unit>();
        for(var u:p.units()) {
            var seq=new ArrayList<Sequence>();
            boolean orphan=u.sequences().stream().anyMatch(s->s.label().localId().equals("orphan"));
            for(var s:u.sequences()) {
                var instructions=s.instructions();var terminator=s.terminator();
                MUTATION
                seq.add(new Sequence(s.label(),instructions,terminator,s.origin()));
            }
            units.add(new io.github.gustavo2358.air.model.Unit(u.id(),u.containingUnit(),u.objects(),u.visibleObjects(),u.entries(),seq,u.completionPorts(),u.body(),u.bodyUnavailable(),u.coverage(),u.origin()));
        }
        return new Publication(p.id(),p.airVersion(),p.capabilities(),p.artifacts(),units,p.storage(),p.resources(),p.artifactRelations(),p.origins(),p.coverage(),p.uncertainties(),p.premises());
    }
'''


def boundary(mutation):
    source = (ROOT / APPLICATION).read_text()
    return source.replace('Objects.requireNonNull(publication);', 'Objects.requireNonNull(publication);publication=fault(publication);')\
        .replace('    public enum Kind', FAULT.replace('MUTATION', mutation) + '    public enum Kind')


def replace(path, old, new):
    source = (ROOT / path).read_text()
    if source.count(old) != 1:
        raise RuntimeError('mutation anchor drift: ' + path)
    return source.replace(old, new)


def mutations():
    before = '            status=candidates.isEmpty()?TargetStatus.OPEN_TARGET:TargetStatus.RESOLVED_CANDIDATES;'
    return [
        ('01-swap-branch-kinds', CFG, replace(CFG,
            'CfgTransition.Kind.BRANCH_TRUE, entry.id()));', 'CfgTransition.Kind.BRANCH_FALSE, entry.id()));').replace(
                'sequences.get(branch.falseDestination()).id(),\n                                CfgTransition.Kind.BRANCH_FALSE',
                'sequences.get(branch.falseDestination()).id(),\n                                CfgTransition.Kind.BRANCH_TRUE')),
        ('02-drop-progb-assign', APPLICATION, boundary('''instructions=instructions.stream().filter(i->!(i instanceof Operations.Assign a && a.value() instanceof Expressions.Literal l && l.value().equals(new Values.TextValue("PROGB   ")))).toList();''')),
        ('03-open-false-artificial-block', APPLICATION, boundary('''
                if(terminator instanceof Operations.Branch b && u.sequences().stream().anyMatch(t->t.label().equals(b.falseDestination())&&t.terminator() instanceof Operations.Invoke)) {
                    var label=new io.github.gustavo2358.air.model.Ids.LabelId(u.id(),"artificial");var h=b.header();
                    var jumpHeader=new Operations.Header(new io.github.gustavo2358.air.model.Ids.OperationId(u.id(),"artificial-jump"),h.origin(),h.coverage(),h.precision(),h.uncertainties());
                    seq.add(new Sequence(label,List.of(),new Operations.Jump(jumpHeader,b.trueDestination()),s.origin()));
                    terminator=new Operations.Branch(h,b.predicate(),b.trueDestination(),label);
                }''')),
        ('04-unreachable-contaminates-join', APPLICATION, boundary('''
                if(orphan && terminator instanceof Operations.Branch b)
                    terminator=new Operations.Branch(b.header(),b.predicate(),b.trueDestination(),new io.github.gustavo2358.air.model.Ids.LabelId(u.id(),"orphan"));''')),
        ('05-cross-candidate-supports', CONSUMER, replace(CONSUMER, '            for(var candidate:raw) {',
            '            if(raw.size()>1){var a=raw.get(0);var b=raw.get(1);raw.set(0,new RawCandidate(a.rawValue(),b.supports()));raw.set(1,new RawCandidate(b.rawValue(),a.supports()));}\n            for(var candidate:raw) {')),
        ('06-force-closed-remainder', CONSUMER, replace(CONSUMER, 'model=value.modelValueRemainder();', 'model=true;')),
        ('07-erase-open-remainder', CONSUMER, replace(CONSUMER, 'model=value.modelValueRemainder();', 'model=false;')),
        ('08-drop-known-when-open', CONSUMER, replace(CONSUMER, before, '            if(Boolean.TRUE.equals(model))candidates.clear();\n' + before)),
        ('09-invent-open-candidate', CONSUMER, replace(CONSUMER, before,
            '            if(Boolean.TRUE.equals(model)&&!candidates.isEmpty())candidates.add(new Candidate("INVENTED","INVENTED",candidates.getFirst().supports()));\n' + before)),
        ('10-break-w1-singleton', CONSUMER, replace(CONSUMER, before,
            '            if(computed&&Boolean.FALSE.equals(model)&&candidates.size()==1)candidates.clear();\n' + before)),
    ]


def run(work):
    require_local(); work.mkdir(parents=True, exist_ok=False)
    tree = work / 'source'; tree.mkdir(); shutil.copyfile(ROOT / 'pom.xml', tree / 'pom.xml')
    for module in ROOT.glob('*/pom.xml'):
        shutil.copytree(module.parent, tree / module.parent.name, ignore=shutil.ignore_patterns('target'))
    command = ['mvn', '-B', '-ntp', '-pl', 'analysis-adapters', '-am', '-DskipTests', 'package',
               'org.apache.maven.plugins:maven-dependency-plugin:3.8.1:build-classpath',
               '-DincludeScope=test', '-Dmdep.outputFile=target/mutation-classpath.txt']
    with (work / 'compile.log').open('wb') as log:
        subprocess.run(command, cwd=tree, stdout=log, stderr=subprocess.STDOUT, check=True)
    cp = os.pathsep.join([str(p) for p in sorted(tree.glob('*/target/classes'))] +
                        [str(tree / 'analysis-adapters/target/test-classes'),
                         (tree / 'analysis-adapters/target/mutation-classpath.txt').read_text().strip()])
    # Call the same JUnit assertion methods explicitly, so an earlier reactor test cannot
    # mask the W2D oracle. No reflection, discovery, skips or upstream tests per mutation.
    calls = []
    for suite, methods in PROBES.items():
        for method in methods:
            calls.append('try { new ' + suite + '().' + method + '(); } '
                         'catch (AssertionError e) { failures++;System.out.println("REJECTED ' + method + ': "+e); }')
    driver = tree / 'ProbeMain.java'
    driver.write_text('package io.github.gustavo2358.analysis.adapters;\n'
                      'public final class ProbeMain { public static void main(String[] args) throws Exception {\n'
                      'int failures=0;\n' + '\n'.join(calls) + '\nSystem.exit(failures==0?0:1);\n}}\n')
    driver_classes = work / 'driver'; driver_classes.mkdir()
    subprocess.run(['javac', '--release', '21', '-cp', cp, '-d', str(driver_classes), str(driver)], check=True)
    cp = str(driver_classes) + os.pathsep + cp
    def execute(label, classpath):
        with (work / (label + '.log')).open('wb') as log:
            return subprocess.run(['java', '-cp', classpath, 'io.github.gustavo2358.analysis.adapters.ProbeMain'],
                                  cwd=tree, stdout=log, stderr=subprocess.STDOUT).returncode
    if execute('baseline', cp):
        raise RuntimeError('mutation baseline failed: ' + str(work / 'baseline.log'))
    for name, path, mutant in mutations():
        directory = work / name; directory.mkdir()
        source = directory / Path(path).name; source.write_text(mutant)
        classes = directory / 'classes'; classes.mkdir()
        with (directory / 'compile.log').open('wb') as log:
            subprocess.run(['javac', '--release', '21', '-cp', cp, '-d', str(classes), str(source)],
                           stdout=log, stderr=subprocess.STDOUT, check=True)
        rc = execute(name, str(classes) + os.pathsep + cp)
        failures = [line for line in (work / (name + '.log')).read_text().splitlines() if line.startswith('REJECTED ')]
        if rc == 0 or not failures:
            raise RuntimeError(name + ' survived or failed without focal assertion: ' + str(work / (name + '.log')))
        print('KILLED ' + name + ': ' + failures[0], flush=True)
    if execute('restored', cp):
        raise RuntimeError('restored baseline failed')
    print('PASS: 10/10 focal implementation mutants killed; restored baseline PASS; solver/lattice untouched', flush=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--work', required=True, type=Path)
    args = parser.parse_args()
    run(args.work.resolve())
