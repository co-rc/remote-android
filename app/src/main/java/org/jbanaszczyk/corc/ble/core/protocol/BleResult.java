package org.jbanaszczyk.corc.ble.core.protocol;

import androidx.annotation.NonNull;
import org.jbanaszczyk.corc.utils.Utils;

import java.util.Optional;

/**
 * Result codes for the CMD/RSP protocol.
 */
public enum BleResult {
    OK((byte) 0x00, "OK"),

    // codes intentionally similar to GAT errors, no semantic meaning
    READ_NOT_PERMITTED((byte) 0x02, "Read not permitted"),
    WRITE_NOT_PERMITTED((byte) 0x03, "Write not permitted"),
    INSUFFICIENT_AUTHENTICATION((byte) 0x05, "Insufficient authentication"),
    REQUEST_NOT_SUPPORTED((byte) 0x06, "Request not supported"),
    INVALID_OFFSET((byte) 0x07, "Invalid offset"),
    INSUFFICIENT_AUTHORIZATION((byte) 0x08, "Insufficient authorization"),
    INVALID_ATTRIBUTE_LENGTH((byte) 0x0D, "Invalid attribute length"),
    INSUFFICIENT_ENCRYPTION((byte) 0x0F, "Insufficient encryption"),
    CONNECTION_CONGESTED((byte) 0x8F, "Connection congested"),
    FAILURE((byte) 0xFF, "General failure"),

    // codes not used yet
    UNSUPPORTED((byte) 0x11, "Unsupported"),
    BAD_PARAM((byte) 0x12, "Bad parameter"),
    INVALID_STATE((byte) 0x13, "Invalid state"),
    BUSY((byte) 0x14, "Busy");

    private final byte value;
    private final String description;

    BleResult(byte value, String description) {
        this.value = value;
        this.description = description;
    }

    public static Optional<BleResult> tryFromByte(byte value) {
        for (var result : values()) {
            if (result.value == value) {
                return Optional.of(result);
            }
        }
        return Optional.empty();
    }

    public static String formatUnknown(byte value) {
        return "Unknown (" + Utils.Hexes.toHex(value) + ")";
    }

    public byte getValue() {
        return value;
    }

    @NonNull
    @Override
    public String toString() {
        return description + " (" + Utils.Hexes.toHex(value) + ")";
    }

}
