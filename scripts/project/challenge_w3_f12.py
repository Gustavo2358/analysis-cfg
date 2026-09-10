#!/usr/bin/env python3
"""Focal F1/F2 production mutants, using the existing compile/RED/restore/GREEN ritual."""
import challenge_w3 as campaign
V=campaign.V
P=V+'TextProfile.java';A=V+'PossibleValuesAnalysis.java';C=V+'Candidates.java';U=V+'ValueUniverse.java'
PRODUCER='SupportSourceTest#producerSurvivesBlocksAndStrongUpdateKillsOldSupport'
SEED='SupportSourceTest#entrySeedPremisesFollowValueAndAreKilledByAssignment'
ENTRY='SupportSourceTest#entryUncertaintyOpensOnlyThatEntryWithoutChangingModel'
ALIAS='SupportSourceTest#relevantAliasSourceGapCannotDisappearByQueryingExactAlias'
ASSOCIATION='SupportSourceTest#candidateSupportsStayAssociatedWithTheirValue'
LEAK='SupportSourceTest#sourceGapDoesNotLeakFromOtherCellOrDependencyDimension'
campaign.MUTATIONS={
'drop-producer-support':(U,[('return singleton.supportedBy(ordinal,work);','return singleton;')],PRODUCER,'W3-F1 producing Assign'),
'query-operation-as-evidence':(A,[('evidence.add(producer.evidence());','evidence.add(query.point().operation());')],PRODUCER,'W3-F1 producing Assign'),
'drop-producer-origin':(A,[('provenance.add(producer.origin());','')],PRODUCER,'W3-F1 producer origin'),
'drop-seed-premises':(P,[('condition.premises(),preparation','List.of(),preparation')],SEED,'W3-F1 EntryState premises survive'),
'substitute-seed-origin':(P,[('condition.origin(),condition.premises()','context.entry().origin(),condition.premises()')],SEED,'W3-F1 seed origin'),
'ignore-support-in-equivalence':(C,[('&&supports.equivalent(b.supports)','')],'SupportSourceTest#supportLatticeLawsAndStrongUpdatesRemainFinite','support-only change is semantic change'),
'join-discards-new-support':(C,[('var support=supports.join(b.supports,w);','var support=supports;')],'SupportSourceTest#equalCandidateDiamondUnionsBothProducers','W3-F1 union support'),
'weak-support-update':(P,[('state.assign(write.location().ordinal(),write.value(),work)','state.assign(write.location().ordinal(),state.value(write.location().ordinal(),work).join(write.value(),work),work)')],PRODUCER,'expected:'),
'drop-simultaneous-seed-support':(P,[('if(previous!=null)value=seed.value(location.ordinal(),preparation).join(value,preparation);','')],'SupportSourceTest#sameCellInitialConditionsRetainBothPremises','expected: <2> but was: <1>'),
'candidate-support-cross-association':(U,[('selected.getOrDefault(candidate,List.of()).stream()','selected.values().stream().flatMap(List::stream)')],ASSOCIATION,'no cross-candidate or orphan pool support'),
'pool-inventory-as-support':(U,[('i<candidates.supports.size()','i<producers.size()'),('producers.get(candidates.supports.at(i))','producers.get(i)')],ASSOCIATION,'no cross-candidate or orphan pool support'),
'support-cardinality-cap':(V+'SupportSet.java',[('Arrays.copyOf(merged,k)','Arrays.copyOf(merged,Math.min(k,8))')],'SupportSourceTest#supportCardinalityPreservesAllEqualValueProducers','no support cap'),
'ignore-entry-state-uncertainties':(P,[('sourceOpenEntries.contains(entry)','false')],ENTRY,'W3-F2 EntryState uncertainty'),
'entry-gap-leaks-to-other-entry':(P,[('sourceOpenEntries.contains(entry)','!sourceOpenEntries.isEmpty()')],ENTRY,'W3-F2 EntryState uncertainty'),
'only-queried-alias-precision':(P,[('sourceOpenCells.contains(subjects.get(subject).ordinal())','open(session.index().object(subject).precision().storage())||open(session.index().object(subject).precision().values())')],ALIAS,'W3-F2 same-Cell alias value gap'),
'alias-gap-leaks-to-other-cell':(P,[('sourceOpenCells.contains(subjects.get(subject).ordinal())','!sourceOpenCells.isEmpty()')],LEAK,'expected: <false> but was: <true>'),
'dependency-gap-opens-values':(P,[('||open(object.precision().values()))','||open(object.precision().values())||open(object.precision().dependencies()))')],LEAK,'expected: <false> but was: <true>'),
'source-entry-gap-opens-model':(A,[('boolean model=state.isReached()&&state.value(cell.ordinal(),replayWork).open();','boolean model=source||state.isReached()&&state.value(cell.ordinal(),replayWork).open();')],ENTRY,'expected: <false> but was: <true>'),
}
if __name__=='__main__':campaign.main()
