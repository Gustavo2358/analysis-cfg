# R9 producer compatibility fixtures

Synthetic lower-produced contract 1.0.0 documents, paired with the reviewed R9
producer fixtures (`cobol-lower/adapters/src/test/resources/qualified-source-r9`).
`ordinary.dependencies.json` additionally fixes the 2.6 wire shape for the
independent Python oracle. Expected semantic assertions live in the Java/Python
tests; fixture regeneration is not an oracle. AIR digests bind the original
synthetic producer outputs. No real-corpus source is versioned here.
