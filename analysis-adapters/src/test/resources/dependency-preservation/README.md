# Lower-produced dependency-preservation fixture

`redefines-unknown.air.json` is emitted by cobol-lower from its
`adapters/src/test/resources/sp/dependency-preservation/redefines.json` fixture.
The source is the frontend DependencyPreservationTest REDEFINES case: missing COPY,
A PIC X(8), B REDEFINES A, MOVE PROGA to B, MOVE PROGB to A, XCTL PROGRAM(B).

Regenerate through the pinned CobolLower CLI after building the producer; never edit
AIR uncertainty/header references to satisfy the consumer test. The original emitted
fixture made lowerAreaUncertaintyReachesSiteAndWire fail. The corrected lower adds
its area uncertainty to the Invoke header; candidates and remainder are unchanged.

This fixture intentionally has two UnknownBindings. It does not assert a physical
alias overwrite or complete enumeration of values.
