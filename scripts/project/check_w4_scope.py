"""Byte-exact W1-W3 preservation with one exact additive W1 context-selection method."""
import hashlib
from pathlib import Path

SESSION = 'analysis-kernel/src/main/java/io/github/gustavo2358/analysis/structure/AnalysisSession.java'
ADDITION = '\n\n    /**\n     * A narrower explicit context selection sharing this already admitted index. No AIR/CFG traversal,\n     * validation or projection is repeated. Only Entries selected by this owner can be selected again.\n     * The new view has its own contextual handles; it does not mutate this session or any stable run.\n     */\n    public AnalysisSession selectEntries(Collection<EntryId> selectedEntries) {\n        var selected = new ArrayList<Entries.Entry>();\n        for (var id : selectedEntries) {\n            var context = contexts.get(Objects.requireNonNull(id));\n            if (context == null) throw new IllegalArgumentException("Entry not selected by owning session");\n            selected.add(context.entry());\n        }\n        return new AnalysisSession(index, selected);\n    }\n'

def preserved_digest(root: Path, path: str) -> str:
    data = (root / path).read_bytes()
    if path == 'pom.xml':
        from check_w5 import original_pom
        data = original_pom(data)
    if path == SESSION:
        addition = ADDITION.encode()
        if data.count(addition) != 1:
            return 'invalid additive session selection'
        data = data.replace(addition, b'', 1)
    return hashlib.sha256(data).hexdigest()
