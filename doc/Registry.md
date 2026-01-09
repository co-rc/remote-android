### BLE Device Registry and Persistence

This document describes the design and usage of device management and persistence in the CORC project.

### Concept

The project distinguishes between two types of device storage:

1. **BleDeviceRegistry (In-Memory)**: A runtime registry that manages active `BleDevice` instances and their associated `BleConnectionContext`.
2. **Room Database (Persistent)**: A local database that stores known devices and their configuration across application restarts.

#### 1. BleDeviceRegistry

The `BleDeviceRegistry` is the "Source of Truth" for device instances during the application's lifecycle.

* **Identity**: It uses `BleDeviceAddress` as a unique key.
* **Instance Management**: It ensures that only one `BleDevice` object exists for a specific physical device. This prevents state inconsistency when multiple components interact with the same device.
* **Connection State**: It maintains `BleConnectionContext` for each registered device. The context stores transient data like the `BluetoothGatt` instance, discovered services, and active operation queues.
* **Usage**:
    * `ensure(address)`: Returns an existing instance or creates a new one.
    * `registerPersistedDevices(collection)`: Populates the registry with devices loaded from the database.

#### 2. Persistence (Room Database)

Persistence is implemented using the Android Room library. It allows the app to "remember" devices even after it is closed.

* **Repository Pattern**: `RoomBleDeviceRepository` abstracts the database operations. It handles the mapping between the domain model (`BleDevice`) and the database entity (`BleDevicePersistent`).
* **Asynchronous Operations**: All database writes are performed on a dedicated background thread (`corc-db-exec`) to avoid blocking the Main thread.
* **Data Stored**:
    * Device MAC Address (Primary Key).
    * Device Configuration (JSON string).

### Usage Flow

1. **Application Startup**:
    * `BleController` initializes.
    * `RoomBleDeviceRepository.loadAll()` is called to fetch known devices from the database.
    * These devices are passed to `BleDeviceRegistry.registerPersistedDevices()`.
2. **Device Discovery**:
    * When a device is scanned, `BleDeviceRegistry.ensure()` provides the `BleDevice` instance.
3. **Updating Data**:
    * When services are discovered or configuration changes, the `BleDevice` instance is updated.
    * Services are kept in `BleConnectionContext` (runtime only).
    * `BleDeviceRepository.save(device)` is called to persist the configuration change.

### Key Classes

* `org.jbanaszczyk.corc.ble.BleDeviceRegistry`: In-memory singleton-like manager (managed by `BleController`).
* `org.jbanaszczyk.corc.ble.repo.RoomBleDeviceRepository`: Implementation of `BleDeviceRepository` using Room.
* `org.jbanaszczyk.corc.db.CorcDatabase`: Room database definition.
* `org.jbanaszczyk.corc.ble.internal.BleDevicePersistent`: The Room Entity representing a device in the database.
