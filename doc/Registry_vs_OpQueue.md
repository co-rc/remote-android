# BleDeviceRegistry vs. OperationQueue

This document compares and explains the relationship between two core components of the CORC BLE stack: `BleDeviceRegistry` and `OperationQueue`.

### Overview

While both components are central to managing BLE operations, they operate at different architectural layers and have distinct responsibilities regarding state management and execution.

### 1. BleDeviceRegistry: Global State and Identity

The `BleDeviceRegistry` serves as the **Global Source of Truth** for device identity and high-level connection state.

- **Role**: Maintains a mapping of all known devices (`BleDevice`) and their associated connection contexts (`BleConnectionContext`).
- **Scope**: Application-wide (singleton-like lifecycle managed by `BleController`).
- **Persistence**: Bridged with the Room Database via `RoomBleDeviceRepository`. It ensures that device data (services, configuration) survives application restarts.
- **Granularity**: Per-Device. It distinguishes devices by their MAC address (`BleDeviceAddress`).
- **State**: Tracks "who" the devices are and "what" their current connection status is (e.g., `CONNECTING`, `READY`).

### 2. OperationQueue: Transient Operation Serialization

The `OperationQueue` is a **Local Serializer** for low-level GATT operations.

- **Role**: Ensures that GATT operations (read, write, MTU request) are executed sequentially to maintain BLE stack stability.
- **Scope**: Transient. It manages the immediate execution flow.
- **Persistence**: None. Operations are short-lived and discarded once completed, failed, or if the device disconnects.
- **Granularity**: Multi-device aware. While it is a shared queue, it tracks which device each operation belongs to using `BleDeviceAddress`.
- **State**: Tracks "what" is being done right now and manages timeouts for individual operations.

### Comparison Table

| Feature             | `BleDeviceRegistry`                | `OperationQueue`                 |
|:--------------------|:-----------------------------------|:---------------------------------|
| **Primary Goal**    | Device & State Tracking            | Operation Serialization & Timing |
| **Granularity**     | Per-Device (Mapped by address)     | Multi-device (Scoped operations) |
| **Persistence**     | Yes (via Room Database)            | No (Transient)                   |
| **Lifecycle**       | Global / Long-lived                | Execution-bound / Short-lived    |
| **Source of Truth** | Identity and Services              | Immediate GATT execution state   |
| **Concurrency**     | Thread-safe storage                | Thread-safe execution (serial)   |
| **Error Handling**  | State updates (e.g., DISCONNECTED) | Timeouts and Future completion   |

### Interaction Flow

1. **Discovery**: `BleController` finds a device, and `BleDeviceRegistry` ensures a `BleDevice` and `BleConnectionContext` exist.
2. **Read/Write**: When the app wants to read a characteristic, `BleController` creates a `BleOperation` (containing the device address) and enqueues it in the `OperationQueue`.
3. **Execution**: `OperationQueue` checks if an operation is already in progress. If not, it picks the next operation and tells the `BleGattClient` to execute it.
4. **Completion**: When the GATT callback is received (e.g., `onCharacteristicRead`), `BleGattClient` notifies the `OperationQueue`, which completes the corresponding `CompletableFuture` and triggers the next queued
   operation.
5. **Disconnection**: If a device disconnects, `BleGattClient` calls `operationQueue.clear(address)`. The queue removes all operations belonging to that specific address without affecting operations for other devices.
