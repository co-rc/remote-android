package org.jbanaszczyk.corc.ble.core.protocol;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class BleResultTest {

    @Test
    public void testToString() {
        assertEquals("OK (0x00)", BleResult.OK.toString());
        assertEquals("Unsupported (0x11)", BleResult.UNSUPPORTED.toString());
        assertEquals("Bad parameter (0x12)", BleResult.BAD_PARAM.toString());
        assertEquals("Invalid state (0x13)", BleResult.INVALID_STATE.toString());
        assertEquals("Busy (0x14)", BleResult.BUSY.toString());
        assertEquals("Insufficient authorization (0x08)", BleResult.INSUFFICIENT_AUTHORIZATION.toString());
        assertEquals("General failure (0xFF)", BleResult.FAILURE.toString());
    }

    @Test
    public void testFormatUnknown() {
        assertEquals("Unknown (0x07)", BleResult.formatUnknown((byte) 0x07));
        assertEquals("Unknown (0xA5)", BleResult.formatUnknown((byte) 0xA5));
    }
}
