### OperationQueue Design and Principles

The `OperationQueue` is a core component of the BLE stack in the CORC project, designed to manage the sequential execution of GATT operations on a single `BluetoothGatt` instance.

#### Design Overview

BLE operations on Android are asynchronous and must be executed one at a time. Issuing a new GATT command before the previous one has completed often leads to undefined behavior or silent failures. `OperationQueue` solves this by:

1.  **Serialization**: It uses an `ArrayBlockingQueue` to store pending `BleOperation` objects.
2.  **State Management**: It maintains an `inProgress` flag to ensure only one operation is active at any given time.
3.  **Timeout Protection**: Every operation is guarded by a watchdog timer. If an operation doesn't signal completion within the specified timeout, the queue automatically disconnects the GATT instance to prevent the stack from hanging.
4.  **Abstraction**: It decouples the *intent* (what to do) from the *execution* (how to do it) using the `OperationExecutor` interface.

#### Key Components

*   **BleOperation**: An immutable data class describing the operation (READ, WRITE, ENABLE_NOTIFY, etc.), the target UUID, and optional payload.
*   **OperationExecutor**: An interface responsible for performing the actual `BluetoothGatt` calls. `StandardGattOperationExecutor` is the default implementation.
*   **Scheduler**: An abstraction over the execution environment (e.g., Android's `Handler`) allowing for easier unit testing.
*   **TimeoutProvider**: A functional interface that supplies the timeout duration, allowing for dynamic or per-device configuration.

#### Operational Flow

1.  An operation is added via `enqueue()`.
2.  If the queue is not busy and the GATT client is in the `READY` state, the operation is polled from the queue.
3.  The `inProgress` flag is set to `true`.
4.  A timeout task is scheduled.
5.  The operation is passed to the `OperationExecutor`.
6.  When the `BluetoothGattCallback` signals completion (e.g., `onCharacteristicWrite`), `onOperationFinished()` is called.
7.  `onOperationFinished()` clears the timeout, resets `inProgress` to `false`, and triggers the next operation in the queue.

#### Principles (SOLID)

*   **Single Responsibility Principle (SRP)**: The queue only manages the lifecycle and ordering of operations. It doesn't know *how* to write to a characteristic or *what* the data means.
*   **Open/Closed Principle (OCP)**: New types of GATT operations can be added by extending `BleOperation` and updating the `OperationExecutor` without changing the core queue logic.
*   **Dependency Inversion Principle (DIP)**: The queue depends on abstractions (`Scheduler`, `OperationExecutor`, `TimeoutProvider`) rather than concrete Android classes.

---

### Extending the Future

#### Adding MTU Negotiation
To add MTU negotiation, follow these steps:
1.  Add `REQUEST_MTU` to the `BleOperationType` enum.
2.  Add a field for the requested MTU value in the `BleOperation` class and a factory method `BleOperation.requestMtu(int)`.
3.  Update `StandardGattOperationExecutor` to handle the `REQUEST_MTU` type by calling `gatt.requestMtu(mtu)`.
4.  In `BleGattClient`, implement the `onMtuChanged` callback and call `operationQueue.onOperationFinished()`.

#### Implementing File Transfer
File transfer usually involves a sequence of operations and asynchronous notifications:
1.  **Define a Transaction**: Create a high-level class (e.g., `FileDownloadTransaction`) that coordinates the steps.
2.  **Enqueue Sequence**: The transaction enqueues:
    *   `BleOperation.requestMtu(...)` (to maximize throughput).
    *   `BleOperation.enableNotify(...)` (to listen for data chunks).
    *   `BleOperation.write(...)` (to send a "start transfer" command).
3.  **Handle Notifications**: Add a listener in `BleGattClient` for `onCharacteristicChanged`. This callback is *external* to the `OperationQueue` (notifications are pushed by the peripheral).
4.  **Reassemble Data**: The transaction class collects incoming chunks until an End-of-File (EOF) marker is received.
