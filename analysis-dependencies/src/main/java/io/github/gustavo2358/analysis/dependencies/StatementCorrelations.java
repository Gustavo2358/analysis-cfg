package io.github.gustavo2358.analysis.dependencies;

/** Borrowed immutable correlation data, not an admission certificate. The
 * legitimate correlation factory still checks every complete SOURCE/AIR tuple,
 * label, origin ancestry and target law. No wire/token or I/O knowledge enters
 * this port. All text is exact UTF-16; paths preserve every ordered coordinate.
 * The input and authentic native program must both remain open throughout use.
 * Destinations are caller-owned/funded; no detached String/List is returned. */
public interface StatementCorrelations {
    enum Field {
        SOURCE_COMPILATION_UNIT, SOURCE_CANONICAL_PROGRAM_NAME, SOURCE_HANDLE,
        OPERATION_PUBLICATION, OPERATION_UNIT, OPERATION_LOCAL_ID,
        LABEL_PUBLICATION, LABEL_UNIT, LABEL_LOCAL_ID,
        ORIGIN_PUBLICATION, ORIGIN_LOCAL_ID
    }
    SnapshotProgram program();
    long size();
    long characterCount(long row,Field field);
    void readCharacters(long row,Field field,long offset,char[] destination,int at,int count);
    long structuralPathCount(long row);
    int structuralPath(long row,long coordinate);
}
