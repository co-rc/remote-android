package org.jbanaszczyk.corc.ble.core.protocol;

import android.util.Log;
import org.jbanaszczyk.corc.utils.Utils;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages the CMD/RSP protocol framing and request correlation.
 */
public final class BleCommandResponseManager {
    public static final int PROTOCOL_MAGIC = 0xC07C;
    private static final String LOG_TAG = "CORC:BleCommandRespMgr";
    private static final ByteOrder PROTOCOL_BYTE_ORDER = ByteOrder.LITTLE_ENDIAN;

    private static final int MAGIC_SIZE = Short.BYTES;
    private static final int REQUEST_ID_SIZE = Byte.BYTES;
    private static final int OPCODE_SIZE = Byte.BYTES;
    private static final int RESULT_VALUE_SIZE = Byte.BYTES;
    private static final int PAYLOAD_LEN_SIZE = Byte.BYTES;
    public static final int PAYLOAD_HEADER_SIZE = MAGIC_SIZE + REQUEST_ID_SIZE + OPCODE_SIZE + PAYLOAD_LEN_SIZE;
    public static final int MAX_PAYLOAD_SIZE = (1 << (Byte.SIZE * PAYLOAD_LEN_SIZE)) - 1;
    private static final int RESPONSE_HEADER_SIZE = PAYLOAD_HEADER_SIZE + RESULT_VALUE_SIZE;
    private final AtomicInteger requestIdCounter = new AtomicInteger(0);
    private final Utils.ByteSequence byteSequence = new Utils.ByteSequence(requestIdCounter::getAndIncrement);
    private CompletableFuture<byte[]> pendingResponseFuture;
    private byte pendingRequestId;
    private byte pendingOpcode;

    public CommandRequest createRequest(byte opcode, byte[] payload) {
        var safePayload = Utils.nonNullContainer(payload);
        var requestId = byteSequence.next();
        var payloadLen = safePayload.length;

        var buffer = ByteBuffer.allocate(PAYLOAD_HEADER_SIZE + payloadLen)
                .order(PROTOCOL_BYTE_ORDER)
                .putShort((short) PROTOCOL_MAGIC)
                .put(requestId)
                .put(opcode)
                .put((byte) payloadLen)
                .put(safePayload);

        return new CommandRequest(requestId, buffer.array());
    }

    public synchronized void setPendingResponse(byte requestId, byte opcode, CompletableFuture<byte[]> future) {
        this.pendingRequestId = requestId;
        this.pendingOpcode = opcode;
        this.pendingResponseFuture = future;
    }

    public synchronized void handleNotification(byte[] data) {
        if (pendingResponseFuture == null || pendingResponseFuture.isDone()) {
            Log.d(LOG_TAG, "handleNotification: No pending response future or already done");
            return;
        }

        if (data.length < RESPONSE_HEADER_SIZE) {
            Log.d(LOG_TAG, "handleNotification: Data too short (" + data.length + " < " + RESPONSE_HEADER_SIZE + ")");
            return;
        }

        var buffer = ByteBuffer
                .wrap(data)
                .order(PROTOCOL_BYTE_ORDER);

        var magic = Short.toUnsignedInt(buffer.getShort());
        if (magic != PROTOCOL_MAGIC) {
            Log.d(LOG_TAG, String.format("handleNotification: Magic mismatch (0x%04X != 0x%04X)", magic, PROTOCOL_MAGIC));
            return;
        }

        var requestId = buffer.get();
        var opcode = buffer.get();
        var resultValue = buffer.get();
        var len = Byte.toUnsignedInt(buffer.get());

        if (requestId != pendingRequestId || opcode != pendingOpcode) {
            Log.d(LOG_TAG, String.format("handleNotification: Correlation mismatch (reqId: %d != %d, opcode: %d != %d)", requestId, pendingRequestId, opcode, pendingOpcode));
            return;
        }

        if (data.length < RESPONSE_HEADER_SIZE + len) {
            Log.d(LOG_TAG, "handleNotification: Data length mismatch (length: " + data.length + " < " + (RESPONSE_HEADER_SIZE + len) + ")");
            return;
        }

        if (resultValue == BleResult.OK.getValue()) {
            var payload = new byte[len];
            buffer.get(payload);
            pendingResponseFuture.complete(payload);
        } else {
            pendingResponseFuture.completeExceptionally(new BleRemoteException(resultValue));
        }

        pendingResponseFuture = null;
    }

    public synchronized void cancelPendingResponse(Throwable throwable) {
        if (pendingResponseFuture != null && !pendingResponseFuture.isDone()) {
            pendingResponseFuture.completeExceptionally(throwable);
        }
        pendingResponseFuture = null;
    }

    public record CommandRequest(byte requestId, byte[] data) {
    }
}
