package org.jbanaszczyk.corc.utils;
import org.jspecify.annotations.NonNull;

import java.util.HexFormat;

import java.util.function.IntSupplier;

public final class Utils {

    private static final byte[] EMPTY_BYTES = new byte[0];

    private Utils() {
        throw new AssertionError("Utility class");
    }

    public static byte @NonNull [] nonNullContainer(byte[] payload) {
        return payload != null
                ? payload
                : EMPTY_BYTES;
    }


    public static class ByteSequence {

        private final IntSupplier seq;

        public ByteSequence(IntSupplier seq) {
            this.seq = seq;
        }

        public byte next() {
            return (byte) seq.getAsInt();
        }
    }

    public static final class Hexes {
        private static final HexFormat HEX = HexFormat.of().withUpperCase().withPrefix("0x");

        public static String toHex(byte value) {
            return HEX.formatHex(new byte[]{value});
        }

        public static String toHex(int value) {
            return "0x" + Integer.toHexString(value).toUpperCase();
        }

        public static String toHex(long value) {
            return "0x" + Long.toHexString(value).toUpperCase();
        }
    }

}
