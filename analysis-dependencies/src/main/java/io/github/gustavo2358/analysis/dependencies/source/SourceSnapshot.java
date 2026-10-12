package io.github.gustavo2358.analysis.dependencies.source;

/**
 * Typed, immutable, owner-bound source storage. It is NOT an admission certificate.
 * Inventory lists preserve every ordinal/duplicate even when equal payloads share.
 * Primitive handles are local to this access owner. No detached List/Pojo escapes.
 */
public interface SourceSnapshot extends AutoCloseable {
    long root();
    SourceShape shape(long reference);
    long field(long reference,SourceShape expected,int ordinal);
    long size(long list,SourceShape element);
    long element(long list,SourceShape element,long ordinal);
    int integer(long reference);
    boolean bool(long reference);
    long characterCount(long reference);
    void readCharacters(long reference,long offset,char[] destination,int at,int count);
    boolean matches(long reference,String value);
    /** Explicit resident projection. Caller must not retain its String past the scope. */
    TextProjection project(long reference);
    interface TextProjection extends AutoCloseable {
        String value();
        @Override void close();
    }
    @Override void close();
}
