package org.jbanaszczyk.corc.ble.core.protocol;

public final class BleOpcode {
    public static final byte PING = 0x01;
    public static final byte VERSION = 0x02;
    public static final byte GET_DATA_MAX_LEN = 0x03;

    private BleOpcode() {
        throw new AssertionError("Constants only");
    }
}
