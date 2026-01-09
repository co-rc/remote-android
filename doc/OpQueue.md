### OperationQueue Design and Principles

The `OperationQueue` is a core component of the BLE stack in the CORC project, designed to manage the sequential execution of GATT operations across multiple `BluetoothGatt` instances. It ensures thread-safety, serialization, and robust error handling.

#### Design Overview

BLE operations on Android are asynchronous and must be executed one at a time per device. Issuing a new GATT command before the previous one has completed often leads to undefined behavior or silent failures. `OperationQueue` solves this by:

1.  **Serialization**: It uses an internal queue to store `EnqueuedOperation` records, which bundle the `BleOperation`, the target `BluetoothGatt` instance, and the specific `OperationExecutor`.
2.  **Multi-Device Safety**: By storing the GATT context with each operation, a single queue can manage operations for multiple connected devices without cross-talk.
3.  **State Management**: It maintains the GATT state in `BleConnectionContext` and ensures only one operation is active at any given time via the `inProgress` flag.
4.  **Future-based API**: Every operation returns a `CompletableFuture<T>`, which is completed when the GATT callback is triggered.
5.  **Timeout Protection**: Every operation is guarded by a watchdog timer. If an operation doesn't signal completion within the specified timeout, the queue completes the future exceptionally and **automatically disconnects** the GATT instance to prevent the BLE stack from hanging.
6.  **Error Handling (Non-Disconnecting)**: If an operation fails with a GATT error status or a synchronous failure (e.g., `gatt.writeCharacteristic()` returning `false`), the error is caught, the future is completed exceptionally, but the GATT connection is **maintained** (unless it was a timeout), allowing the next operation to proceed.
7.  **Abstraction**: It decouples the *intent* (what to do) from the *execution* (how to do it) using the `OperationExecutor` interface.

#### Key Components

*   **BleOperation<T>**: An immutable data class describing the operation (READ, WRITE, ENABLE_NOTIFY, DISABLE_NOTIFY, REQUEST_MTU), the target UUID, and optional parameters. It holds the `CompletableFuture<T>`.
*   **EnqueuedOperation**: An internal record that pairs a `BleOperation` with its target `BluetoothGatt` and `OperationExecutor`.
*   **OperationExecutor**: An interface responsible for performing the actual `BluetoothGatt` calls. `StandardGattOperationExecutor` is the default implementation.
*   **StandardGattOperationExecutor**: Implements synchronous failure detection. If a GATT method returns `false` (indicating it failed to start), it throws a `RuntimeException`, which is caught by the queue.
*   **Scheduler**: An abstraction over the execution environment (e.g., Android's `Handler`) allowing for easier unit testing.
*   **BleCommandResponseManager**: Located in `core.protocol`, it manages the high-level CMD/RSP protocol framing, correlation, and result codes.

#### Operational Flow

1.  An operation is added via `enqueue()`.
2.  If the queue is not busy, the operation is polled.
3.  The `inProgress` flag is set to `true`.
4.  A timeout task is scheduled.
5.  The operation is passed to the `OperationExecutor`.
6.  When the `BluetoothGattCallback` signals completion (e.g., `onCharacteristicWrite`, `onMtuChanged`), `onOperationFinished(result)` or `onOperationFailed(throwable)` is called.
7.  The future associated with the operation is completed.
8.  `onOperationFinished()` clears the timeout, resets `inProgress` to `false`, and triggers the next operation in the queue.

#### Principles (SOLID)

*   **Single Responsibility Principle (SRP)**: The queue only manages the lifecycle and ordering of operations. Protocol logic is moved to `BleCommandResponseManager`, and execution logic to `OperationExecutor`.
*   **Open/Closed Principle (OCP)**: New types of GATT operations can be added by extending `BleOperation` and updating the `OperationExecutor` without changing the core queue logic.
*   **Dependency Inversion Principle (DIP)**: The queue depends on abstractions (`Scheduler`, `OperationExecutor`, `TimeoutProvider`) rather than concrete Android classes.

---

### MTU Negotiation

To maximize throughput and ensure compatibility with the `BleCommandResponseManager` protocol, the project implements automatic MTU negotiation:

*   **Target MTU**: `263` bytes.
    *   `255` bytes application payload.
    *   `5` bytes protocol header.
    *   `3` bytes GATT overhead.
*   **Flow**:
    1.  Upon `STATE_CONNECTED`, `BleGattClient` enqueues a `REQUEST_MTU` operation.
    2.  `discoverServices()` is called only **after** MTU negotiation completes (successfully or not).
    3.  The negotiated MTU is stored in `BleConnectionContext`.

---

### Command/Response Protocol

For high-level commands, CORC uses a binary protocol over two characteristics:
*   **CMD**: Write With Response channel.
*   **RSP**: Notification channel.

The `BleCommandResponseManager` handles framing:
*   **Request**: `[Magic(0xC07C)] [RequestId] [Opcode] [Len] [Payload]`
*   **Response**: `[Magic(0xC07C)] [RequestId] [Opcode] [Result] [Len] [Payload]`

---

### Extending the Flow

#### Implementing File Transfer
File transfer involves a sequence of operations:
1.  **Negotiate MTU**: Enqueued automatically (target `263`).
2.  **Enable Notifications**: Enqueue `BleOperation.enableNotify(RSP_UUID)`.
3.  **Start Command**: Use `gattClient.sendCommand(...)` to signal the start of transfer.
4.  **Chunk Handling**: The peripheral sends data chunks via notifications. These are handled by `BleCommandResponseManager` or a dedicated transaction coordinator.
5.  **Termination**: The transfer ends when a specific EOF opcode/result is received.
