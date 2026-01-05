### OperationQueue Design and Principles

The `OperationQueue` is a core component of the BLE stack in the CORC project, designed to manage the sequential execution of GATT operations on a single `BluetoothGatt` instance. It now supports asynchronous operations using `CompletableFuture`.

#### Design Overview

BLE operations on Android are asynchronous and must be executed one at a time. Issuing a new GATT command before the previous one has completed often leads to undefined behavior or silent failures. `OperationQueue` solves this by:

1.  **Serialization**: It uses an `ArrayBlockingQueue` to store pending `BleOperation` objects.
2.  **State Management**: It maintains the GATT state in `BleConnectionContext` and ensures only one operation is active at any given time via the `inProgress` flag.
3.  **Future-based API**: Every operation returns a `CompletableFuture<T>`, which is completed when the GATT callback is triggered.
4.  **Timeout Protection**: Every operation is guarded by a watchdog timer. If an operation doesn't signal completion within the specified timeout, the queue completes the future exceptionally and **automatically disconnects** the GATT instance to prevent the BLE stack from hanging.
5.  **Error Handling (Non-Disconnecting)**: If an operation fails with a GATT error status (reported via `onOperationFailed`), the future is completed exceptionally, but the GATT connection is **maintained**, allowing the next operation to proceed.
6.  **Abstraction**: It decouples the *intent* (what to do) from the *execution* (how to do it) using the `OperationExecutor` interface.

#### Key Components

*   **BleOperation<T>**: An immutable data class describing the operation (READ, WRITE, ENABLE_NOTIFY, DISABLE_NOTIFY, REQUEST_MTU), the target UUID, and optional parameters. It holds the `CompletableFuture<T>`.
*   **OperationExecutor**: An interface responsible for performing the actual `BluetoothGatt` calls. `StandardGattOperationExecutor` is the default implementation.
*   **Scheduler**: An abstraction over the execution environment (e.g., Android's `Handler`) allowing for easier unit testing.
*   **BleCommandResponseManager**: Located in `core.protocol`, it manages the high-level CMD/RSP protocol framing, correlation, and result codes.
*   **BleRemoteException**: Thrown when the peripheral returns a non-zero result code (e.g., BUSY, UNSUPPORTED).

#### Operational Flow

1.  An operation is added via `enqueue()`.
2.  If the queue is not busy and the GATT client is in the `READY` state, the operation is polled from the queue.
3.  The `inProgress` flag is set to `true`.
4.  A timeout task is scheduled.
5.  The operation is passed to the `OperationExecutor`.
6.  When the `BluetoothGattCallback` signals completion (e.g., `onCharacteristicWrite`, `onMtuChanged`), `onOperationFinished(result)` or `onOperationFailed(throwable)` is called.
7.  The future associated with the operation is completed.
8.  `onOperationFinished()` clears the timeout, resets `inProgress` to `false`, and triggers the next operation in the queue.

#### Principles (SOLID)

*   **Single Responsibility Principle (SRP)**: The queue only manages the lifecycle and ordering of operations. Protocol logic is moved to `BleCommandResponseManager`.
*   **Open/Closed Principle (OCP)**: New types of GATT operations can be added by extending `BleOperation` and updating the `OperationExecutor` without changing the core queue logic.
*   **Dependency Inversion Principle (DIP)**: The queue depends on abstractions (`Scheduler`, `OperationExecutor`, `TimeoutProvider`) rather than concrete Android classes.

---

### Command/Response Protocol

For high-level commands, CORC uses a binary protocol over two characteristics:
*   **CMD**: Write With Response channel.
*   **RSP**: Notification channel.

The `BleCommandResponseManager` handles framing:
*   **Request**: `[Magic(0xC07C)] [RequestId] [Opcode] [Len] [Payload]`
*   **Response**: `[Magic(0xC07C)] [RequestId] [Opcode] [Result] [Len] [Payload]`

---

### Extending the Future

#### Implementing File Transfer
File transfer involves a sequence of operations:
1.  **Negotiate MTU**: Enqueue `BleOperation.requestMtu(512)` to increase throughput.
2.  **Enable Notifications**: Enqueue `BleOperation.enableNotify(RSP_UUID)`.
3.  **Start Command**: Use `gattClient.sendCommand(...)` to signal the start of transfer.
4.  **Chunk Handling**: The peripheral sends data chunks via notifications. These are handled by `BleCommandResponseManager` or a dedicated transaction coordinator.
5.  **Termination**: The transfer ends when a specific EOF opcode/result is received.
