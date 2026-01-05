package org.jbanaszczyk.corc.ble.core.protocol;

import java.util.Optional;

/**
 * Exception thrown when the remote device returns a non-zero result code in the CMD/RSP protocol.
 */
public final class BleRemoteException extends RuntimeException {
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private final Optional<BleResult> resultCode;
    private final byte rawValue;

    public BleRemoteException(byte rawValue) {
        super(buildMessage(rawValue));
        this.rawValue = rawValue;
        this.resultCode = BleResult.tryFromByte(rawValue);
    }

    private static String buildMessage(byte rawValue) {
        String formatted = BleResult.tryFromByte(rawValue)
                .map(BleResult::toString)
                .orElse(BleResult.formatUnknown(rawValue));
        return "Remote device returned error code: " + formatted;
    }

    public Optional<BleResult> getResultCode() {
        return resultCode;
    }

    public byte getRawValue() {
        return rawValue;
    }
}
