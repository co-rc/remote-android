package org.jbanaszczyk.corc.ble.exceptions;

import androidx.annotation.NonNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serial;

public class InvalidAddressException extends Exception {

    @Serial
    private static final long serialVersionUID = 1L;
    private static final String DEFAULT_MESSAGE_PREFIX = "Invalid address";

    @Nullable
    private final String address;

    public InvalidAddressException(@Nullable String address) {
        super(defaultMessage(address));
        this.address = address;
    }

    public InvalidAddressException(@Nullable String address, String message) {
        super(message);
        this.address = address;
    }

    public InvalidAddressException(@Nullable String address, String message, Throwable cause) {
        super(message, cause);
        this.address = address;
    }

    public InvalidAddressException(@Nullable String address, Throwable cause) {
        super(defaultMessage(address), cause);
        this.address = address;
    }

    private static String defaultMessage(String address) {
        return address == null
                ? DEFAULT_MESSAGE_PREFIX + " (null)"
                : DEFAULT_MESSAGE_PREFIX + ": " + address;
    }

    @Nullable
    public String getAddress() {
        return address;
    }

    @NonNull
    @Override
    public String toString() {
        final String base = super.toString();
        return base + " [address=" + address + ']';
    }
}
