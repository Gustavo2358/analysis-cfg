package io.github.gustavo2358.analysis.values;

import java.util.Map;
import java.util.LinkedHashMap;

/** Per-phase counters. States never retain this mutable observer. */
final class ValuesWork {
    long roots, stateNodes, rootsReused, bindingLookups, bindingUpdates, joinEntries, compareEntries;
    long candidateSets, candidateElements, unionEntries, strongAssignments, unknownDefaults, maxBindings, maxCandidates;
    long candidateArrayBytes;
    void array(int length){candidateArrayBytes=Math.addExact(candidateArrayBytes,Math.multiplyExact(8L,Math.floorDiv(Math.addExact(23L,Math.multiplyExact(4L,length)),8)));}
    void root(int bindings) { roots=Math.incrementExact(roots); maxBindings=Math.max(maxBindings,bindings); }
    void candidate(int size) { candidateSets=Math.incrementExact(candidateSets);candidateElements=Math.addExact(candidateElements,size);maxCandidates=Math.max(maxCandidates,size); }
    Map<String,Long> snapshot() {
        var m=new LinkedHashMap<String,Long>();
        m.put("stateAllocations",Math.addExact(roots,stateNodes));m.put("stateRootsCreated",roots);m.put("persistentNodesAllocated",stateNodes);
        m.put("stateRootsReused",rootsReused);m.put("bindingLookups",bindingLookups);m.put("bindingUpdates",bindingUpdates);
        m.put("joinEntriesVisited",joinEntries);m.put("stateCompareEntries",compareEntries);m.put("maxSparseBindings",maxBindings);
        m.put("candidateSetsCreated",candidateSets);m.put("candidateElementsAllocated",candidateElements);m.put("candidateUnionEntriesVisited",unionEntries);
        m.put("strongAssignments",strongAssignments);m.put("unknownDefaultLookups",unknownDefaults);m.put("candidateCardinality",maxCandidates);
        m.put("stateBytesAllocatedEstimate",Math.addExact(Math.addExact(Math.multiplyExact(24L,roots),Math.multiplyExact(40L,stateNodes)),Math.addExact(Math.multiplyExact(24L,candidateSets),candidateArrayBytes)));
        return Map.copyOf(m);
    }
}
