package io.github.gustavo2358.analysis.dependencies.source;

import java.util.Objects;
import java.util.function.Supplier;

/** Closed typed catalogue of the existing qualified-source 1.0--1.6 fields, not AIR. */
public enum SourceShape {
    TEXT(1), INTEGER(2), BOOLEAN(3), LIST(4),
    ROOT(5,f("schema",()->SourceShape.forTag(1)),f("version",()->SourceShape.forTag(1)),f("producer",()->SourceShape.forTag(1)),f("source",()->SourceShape.forTag(6)),l("air",()->SourceShape.forTag(7)),l("units",()->SourceShape.forTag(31))),
    DOCUMENT(6,f("schema",()->SourceShape.forTag(1)),f("version",()->SourceShape.forTag(1)),f("sha256",()->SourceShape.forTag(1))),
    AIR_CORRELATION(7,f("publication",()->SourceShape.forTag(1)),f("sha256",()->SourceShape.forTag(1))),
    UNIT_ID(8,f("compilationUnitId",()->SourceShape.forTag(1)),l("structuralPath",()->SourceShape.forTag(2)),f("canonicalProgramName",()->SourceShape.forTag(1))),
    STATEMENT_ID(9,f("unit",()->SourceShape.forTag(8)),f("handle",()->SourceShape.forTag(1))),
    OPERAND_ID(10,f("statement",()->SourceShape.forTag(9)),f("handle",()->SourceShape.forTag(1))),
    LOCATION(11,f("file",()->SourceShape.forTag(1)),f("startLine",()->SourceShape.forTag(2)),f("startColumn",()->SourceShape.forTag(2)),f("endLine",()->SourceShape.forTag(2)),f("endColumn",()->SourceShape.forTag(2))),
    INCLUDE(12,f("includingFile",()->SourceShape.forTag(1)),f("requestedName",()->SourceShape.forTag(1)),f("includedFile",()->SourceShape.forTag(1)),f("includeLine",()->SourceShape.forTag(2))),
    PROVENANCE(13,f("expanded",()->SourceShape.forTag(11)),f("original",()->SourceShape.forTag(11)),l("includeChain",()->SourceShape.forTag(12)),f("exact",()->SourceShape.forTag(3))),
    STATEMENT(14,f("id",()->SourceShape.forTag(9)),f("provenance",()->SourceShape.forTag(13))),
    OPERAND(15,f("id",()->SourceShape.forTag(10)),f("provenance",()->SourceShape.forTag(13))),
    VALUE(16,f("logicalDomain",()->SourceShape.forTag(1)),f("value",()->SourceShape.forTag(1)),f("logicalExtent",()->SourceShape.forTag(2))),
    OCCURRENCE(17,f("id",()->SourceShape.forTag(9)),f("technology",()->SourceShape.forTag(1)),f("command",()->SourceShape.forTag(1)),f("namespace",()->SourceShape.forTag(1)),f("nameProfile",()->SourceShape.forTag(1)),f("targetKind",()->SourceShape.forTag(1)),l("operands",()->SourceShape.forTag(15)),l("values",()->SourceShape.forTag(16)),f("valueRemainder",()->SourceShape.forTag(3)),l("qualifications",()->SourceShape.forTag(1))),
    CONDITION_STATE(18,f("condition",()->SourceShape.forTag(1)),f("registration",()->SourceShape.forTag(9)),f("uncertain",()->SourceShape.forTag(3)),l("proofs",()->SourceShape.forTag(1))),
    SUPPORT(19,f("kind",()->SourceShape.forTag(1)),l("target",()->SourceShape.forTag(1)),l("activation",()->SourceShape.forTag(9)),f("cause",()->SourceShape.forTag(1)),ol("conditions",()->SourceShape.forTag(18))),
    NODE(20,f("id",()->SourceShape.forTag(1)),f("context",()->SourceShape.forTag(1)),f("location",()->SourceShape.forTag(1)),f("support",()->SourceShape.forTag(19))),
    REGISTRATION(21,f("statement",()->SourceShape.forTag(9)),f("statementOrigin",()->SourceShape.forTag(13)),f("operandOrigin",()->SourceShape.forTag(13))),
    TARGET(22,f("id",()->SourceShape.forTag(1)),f("form",()->SourceShape.forTag(1)),l("entry",()->SourceShape.forTag(9)),l("registrations",()->SourceShape.forTag(21)),l("programOperands",()->SourceShape.forTag(15)),l("programValues",()->SourceShape.forTag(16))),
    PROOF(23,f("id",()->SourceShape.forTag(1)),f("kind",()->SourceShape.forTag(1)),f("rule",()->SourceShape.forTag(1)),f("provenance",()->SourceShape.forTag(13)),l("dependencies",()->SourceShape.forTag(1))),
    GUARD(24,f("id",()->SourceShape.forTag(1)),f("event",()->SourceShape.forTag(1)),f("kind",()->SourceShape.forTag(1))),
    EVENT(25,f("id",()->SourceShape.forTag(1)),f("statement",()->SourceShape.forTag(9)),f("origin",()->SourceShape.forTag(1)),f("disposition",()->SourceShape.forTag(1)),f("eligibility",()->SourceShape.forTag(1)),f("scope",()->SourceShape.forTag(1)),f("runtimeIdentity",()->SourceShape.forTag(1)),l("guards",()->SourceShape.forTag(1)),l("proofs",()->SourceShape.forTag(1))),
    SELECTION(26,f("id",()->SourceShape.forTag(1)),f("event",()->SourceShape.forTag(1)),f("source",()->SourceShape.forTag(1)),l("target",()->SourceShape.forTag(1)),l("stateOnEntry",()->SourceShape.forTag(19)),l("localEntry",()->SourceShape.forTag(1)),l("guards",()->SourceShape.forTag(1)),l("proofs",()->SourceShape.forTag(1)),f("unknownLocalRemainder",()->SourceShape.forTag(3)),f("localInactivePossible",()->SourceShape.forTag(3)),f("outerLevelRemainder",()->SourceShape.forTag(3)),f("bypassed",()->SourceShape.forTag(3))),
    DERIVATION(27,f("id",()->SourceShape.forTag(1)),l("source",()->SourceShape.forTag(1)),f("destination",()->SourceShape.forTag(1)),l("callerPremise",()->SourceShape.forTag(1)),f("authority",()->SourceShape.forTag(1)),l("proofs",()->SourceShape.forTag(1)),l("selection",()->SourceShape.forTag(1))),
    FRONTIER(28,f("source",()->SourceShape.forTag(1)),f("authority",()->SourceShape.forTag(1)),f("reference",()->SourceShape.forTag(1)),l("proofs",()->SourceShape.forTag(1))),
    NATIVE_FILE_NAME(29,f("declaration",()->SourceShape.forTag(1)),f("owner",()->SourceShape.forTag(8)),f("logicalFile",()->SourceShape.forTag(1)),f("rawValue",()->SourceShape.forTag(1)),l("declarationOrigins",()->SourceShape.forTag(13))),
    NATIVE_FILE_USE(30,f("statement",()->SourceShape.forTag(9)),f("ordinal",()->SourceShape.forTag(2)),f("controlLocation",()->SourceShape.forTag(1)),f("command",()->SourceShape.forTag(1)),f("local",()->SourceShape.forTag(3)),l("names",()->SourceShape.forTag(29)),f("provenance",()->SourceShape.forTag(13)),l("qualifications",()->SourceShape.forTag(1)),l("gaps",()->SourceShape.forTag(1))),
    UNIT_EVIDENCE(31,f("unit",()->SourceShape.forTag(8)),f("controlAvailable",()->SourceShape.forTag(3)),l("statements",()->SourceShape.forTag(14)),l("occurrences",()->SourceShape.forTag(17)),l("targets",()->SourceShape.forTag(22)),l("nodes",()->SourceShape.forTag(20)),l("derivations",()->SourceShape.forTag(27)),l("selections",()->SourceShape.forTag(26)),l("events",()->SourceShape.forTag(25)),l("guards",()->SourceShape.forTag(24)),l("proofs",()->SourceShape.forTag(23)),l("frontiers",()->SourceShape.forTag(28)),o("nominalValues",()->SourceShape.forTag(32)),ol("nativeFiles",()->SourceShape.forTag(30))),
    NOMINAL_VALUE_EVIDENCE(32,f("facts",()->SourceShape.forTag(37)),l("declarations",()->SourceShape.forTag(33)),l("seeds",()->SourceShape.forTag(34)),l("branches",()->SourceShape.forTag(35)),l("uncertainties",()->SourceShape.forTag(36))),
    DECLARATION(33,f("node",()->SourceShape.forTag(1)),f("provenance",()->SourceShape.forTag(13))),
    SEED(34,f("node",()->SourceShape.forTag(1)),f("value",()->SourceShape.forTag(1)),f("authority",()->SourceShape.forTag(1)),f("provenance",()->SourceShape.forTag(13))),
    BRANCH(35,f("derivation",()->SourceShape.forTag(1)),f("whenTrue",()->SourceShape.forTag(3))),
    UNCERTAINTY(36,f("id",()->SourceShape.forTag(1)),f("kind",()->SourceShape.forTag(1)),f("provenance",()->SourceShape.forTag(13))),
    NOMINAL_VALUES(37,f("authority",()->SourceShape.forTag(1)),l("symbols",()->SourceShape.forTag(38)),l("assignments",()->SourceShape.forTag(40)),l("conditions",()->SourceShape.forTag(42)),l("queries",()->SourceShape.forTag(43)),ol("tableFields",()->SourceShape.forTag(44))),
    SYMBOL(38,f("node",()->SourceShape.forTag(1)),f("extent",()->SourceShape.forTag(2)),o("modelAssumed",()->SourceShape.forTag(3))),
    TERM(39,f("kind",()->SourceShape.forTag(1)),f("value",()->SourceShape.forTag(1)),ol("arguments",()->SourceShape.forTag(39))),
    ASSIGNMENT(40,f("statement",()->SourceShape.forTag(1)),f("target",()->SourceShape.forTag(1)),f("source",()->SourceShape.forTag(39))),
    PREDICATE(41,f("kind",()->SourceShape.forTag(1)),l("terms",()->SourceShape.forTag(39)),l("children",()->SourceShape.forTag(41))),
    CONDITION(42,f("statement",()->SourceShape.forTag(1)),f("predicate",()->SourceShape.forTag(41))),
    QUERY(43,f("statement",()->SourceShape.forTag(1)),f("node",()->SourceShape.forTag(1))),
    TABLE_FIELD(44,f("node",()->SourceShape.forTag(1)),l("initial",()->SourceShape.forTag(45))),
    INITIAL(45,f("origin",()->SourceShape.forTag(1)),f("value",()->SourceShape.forTag(1)));

    /** Optional is physical presence, not permission to bypass semantic admission. */
    public record Field(String name,Supplier<SourceShape> type,Supplier<SourceShape> elementType,boolean optional) {
        public Field {Objects.requireNonNull(name);Objects.requireNonNull(type);}
        public SourceShape shape(){return type.get();}
        public SourceShape element(){if(elementType==null)throw new IllegalStateException("not a list field");return elementType.get();}
    }
    private final int tag;
    private final Field[] fields;
    private static final SourceShape[] CATALOGUE=values();
    SourceShape(int tag,Field... fields){this.tag=tag;this.fields=fields;}
    public int tag(){return tag;}
    public int fieldCount(){return fields.length;}
    public Field field(int ordinal){return fields[Objects.checkIndex(ordinal,fields.length)];}
    public boolean record(){return fields.length!=0;}
    public static SourceShape forTag(int tag){for(var shape:CATALOGUE)if(shape.tag==tag)return shape;throw new IllegalArgumentException("unknown source shape");}
    private static Field f(String name,Supplier<SourceShape> type){return new Field(name,type,null,false);}
    private static Field o(String name,Supplier<SourceShape> type){return new Field(name,type,null,true);}
    private static Field l(String name,Supplier<SourceShape> element){return new Field(name,()->SourceShape.forTag(4),element,false);}
    private static Field ol(String name,Supplier<SourceShape> element){return new Field(name,()->SourceShape.forTag(4),element,true);}
}
