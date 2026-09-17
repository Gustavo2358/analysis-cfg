#!/usr/bin/env python3
"""EP-R2 fault models; use the established semantic-failure/restoration harness."""
from pathlib import Path
import ep_policy_mutations as harness

VALUES = Path('analysis-values/src/main/java/io/github/gustavo2358/analysis/values')
REGIONAL = VALUES / 'RegionalValuesAnalysis.java'
STORAGE = Path('analysis-kernel/src/main/java/io/github/gustavo2358/analysis/storage')
RD = Path('analysis-kernel/src/main/java/io/github/gustavo2358/analysis/rd/ReachingDefinitions.java')
ADMIT = 'return new Admission(Status.ACCEPTED,null,Optional.of(new ReachingDefinitions(effects)));'

harness.MUTANTS = [
    ('external-fake-strong', STORAGE / 'EntryFacts.java',
     'kind==Kind.STRONG_LITERAL?StatementEffects.Strength.MUST',
     'kind!=Kind.POSSIBLE?StatementEffects.Strength.MUST', 'EpR2EntryTest'),
    ('cross-may-entry-conflict', RD, ADMIT,
     '''if(effects.storage().session().contexts().stream().anyMatch(ctx->ctx.entry().state().conditions().stream().anyMatch(c->
         c.value() instanceof Entries.ExternalUnknown && effects.targets(effects.storage().resolve(c.place()),StatementEffects.Strength.MAY).stream().anyMatch(t->!t.sourceApplicable()))))
         return new Admission(Status.UNSUPPORTED,"OVERLAPPING_INITIAL_CONDITIONS",Optional.empty());
        ''' + ADMIT, 'EpR2EntryTest'),
    ('permutation-admission', RD, ADMIT,
     '''if(effects.storage().session().contexts().stream().anyMatch(ctx->!ctx.entry().state().conditions().isEmpty() && ctx.entry().state().conditions().getFirst().value() instanceof Entries.ExternalUnknown))
         return new Admission(Status.UNSUPPORTED,"ORDER_DEPENDENT_ENTRY",Optional.empty());
        ''' + ADMIT, 'EpR2EntryTest'),
    ('possible-plus-unknown-drop', REGIONAL,
     'else supports.computeIfAbsent(value.text().get().value(),ignored->new HashSet<>()).addAll(value.producers());',
     'else if(session.context(state.entry).entry().state().conditions().stream().noneMatch(c->c.value() instanceof Entries.ExternalUnknown)) supports.computeIfAbsent(value.text().get().value(),ignored->new HashSet<>()).addAll(value.producers());',
     'EpR2EntryTest'),
    ('may-becomes-kill', REGIONAL,
     'for(var plan:plans)current=weak(current,captured,plan,logicalInputs);return current;',
     'for(var plan:plans)current=replace(current,captured,plan,logicalInputs);return current;',
     'EvidencePreservingPolicyTest'),
    ('factorized-weak-drops-old', REGIONAL,
     'return weakUnion(current,replace(current,captured,plan,logicalInputs));',
     'return replace(current,captured,plan,logicalInputs);', 'EvidencePreservingPolicyTest'),
    ('factorized-join-drops-candidate', REGIONAL,
     'relations.union(value(a,key),v)', 'value(a,key)', 'RegionalCompositionTest'),
    ('unknown-alias-becomes-disjoint', STORAGE / 'StorageIndex.java',
     'return !separationPremises(a,b).isEmpty();', 'return true;', 'EvidencePreservingPolicyTest'),
    ('query-time-entry-reseed', REGIONAL,
     'subject.apply(query.subject())),state));}',
     'subject.apply(query.subject())),engine.apply(state,initial.getOrDefault(state.entry,List.of()),false)));}',
     'EvidencePreservingPolicyTest'),
    ('must-cannot-kill', REGIONAL,
     'var authority=KillAuthority.selected(write,plan.target,execution);',
     'var authority=Optional.<KillAuthority.Permit>empty();', 'EvidencePreservingPolicyTest'),
    ('cartesian-unshared-suffixes', VALUES / 'FactorizedAlternatives.java',
     'var known=interned.get(key);', 'Node<T> known=null;',
     'EpR2FactorizationTest#connectedEntryFactorsGrowBySum'),
]

if __name__ == '__main__':
    harness.main()
