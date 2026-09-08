package io.github.gustavo2358.analysis.cfg.adapters;

import java.io.ByteArrayOutputStream;

/** CFG-only output primitives. No parser, AIR mapping, reflection or generic object serialization. */
final class CfgJsonBytes {
    private static final String HEX = "0123456789abcdef";
    private final ByteArrayOutputStream output;
    private final int maximumBytes;

    CfgJsonBytes(int maximumBytes) {
        if (maximumBytes < 1) throw new IllegalArgumentException("positive maximumBytes required");
        this.maximumBytes = maximumBytes;
        output = new ByteArrayOutputStream(Math.min(maximumBytes, 8192));
    }

    private void octet(int value) throws CfgJsonException {
        if (output.size() == maximumBytes) throw new CfgJsonException("CFG JSON exceeds maximumBytes=" + maximumBytes);
        output.write(value);
    }

    /** Only schema punctuation and fixed ASCII property names/tokens may use this method. */
    void raw(String text) throws CfgJsonException {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c > 127) throw new IllegalArgumentException("raw CFG syntax must be ASCII");
            octet(c);
        }
    }

    void string(String text) throws CfgJsonException {
        octet('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"' || c == '\\') { octet('\\'); octet(c); }
            else if (c < 32) { raw("\\u00"); octet(HEX.charAt(c >> 4)); octet(HEX.charAt(c & 15)); }
            else {
                int scalar = c;
                if (Character.isHighSurrogate(c)) {
                    if (++i >= text.length() || !Character.isLowSurrogate(text.charAt(i)))
                        throw new CfgJsonException("unpaired UTF-16 surrogate in CFG correlation");
                    scalar = Character.toCodePoint(c, text.charAt(i));
                } else if (Character.isLowSurrogate(c)) {
                    throw new CfgJsonException("unpaired UTF-16 surrogate in CFG correlation");
                }
                if (scalar < 128) octet(scalar);
                else if (scalar < 2048) { octet(0xc0 | scalar >> 6); octet(0x80 | scalar & 63); }
                else if (scalar < 65536) {
                    octet(0xe0 | scalar >> 12); octet(0x80 | scalar >> 6 & 63); octet(0x80 | scalar & 63);
                } else {
                    octet(0xf0 | scalar >> 18); octet(0x80 | scalar >> 12 & 63);
                    octet(0x80 | scalar >> 6 & 63); octet(0x80 | scalar & 63);
                }
            }
        }
        octet('"');
    }

    byte[] bytes() { return output.toByteArray(); }
}
