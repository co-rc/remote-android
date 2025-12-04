# CORC BLE Remote

## Overview
Android application acting as a BLE (Bluetooth Low Energy) remote/client for CORC devices (e.g., Raspberry Pi Pico W running CORC firmware). The app discovers, connects, and communicates with a CORC device over GATT.

## Status
- ✅ BLE connectivity with automatic device scanning and connection
- ✅ Persistent configuration storage using Room database
- ✅ Configurable button interface
- ✅ Command sending via BLE GATT write operations
- ✅ Device address memory for quick reconnection

## Features
- BLE connection management: scanning and GATT connection handling
- Permission handling: runtime Bluetooth permissions (Nearby devices)
- Auto-reconnect: remembers the last connected device (if supported by firmware flow)

## Requirements
- Android device with BLE support
- Android 16 (API level 36)
- Bluetooth permissions (Nearby devices): BLUETOOTH_SCAN, BLUETOOTH_CONNECT
- CORC device (e.g., Raspberry Pi Pico W with CORC firmware)

## BLE GATT Details
The app connects to the CORC GATT service exposed by the device and writes UTF‑8 commands to its RX characteristic.

- Service UUID: `B13A1000-9F2A-4F3B-9C8E-A7D4E3C8B125`
- RX Characteristic UUID: `B13A1001-9F2A-4F3B-9C8E-A7D4E3C8B125`
- Write Type: Write Without Response
- Data Format: UTF‑8 text strings

## Architecture

### Components
```
MainActivity
├── BLE connection management
│   ├── Device scanning (BluetoothLeScanner)
│   ├── GATT connection (BluetoothGatt)
│   └── Command sending
└── UI management
    ├── Connection status display
    └── Logs
```

### Database Schema

**DeviceConfig** (device_config table)
- `id` (PrimaryKey): "main"
- `deviceAddress`: BLE MAC address

**ButtonConfig** (buttons table)
- `buttonId` (PrimaryKey): 0-11
- `command`: Command string to send
- `label`: Button label text
- `color`: Text color (Android color int)
- `backgroundColor`: Background color (Android color int)
- `enabled`: Button enabled state

### Key Classes
- `MainActivity`: Activity managing BLE connection and UI
- `BleController`: BLE scan/connect/send logic
- `BleDevice`: Simple wrapper for device connection state

## Installation

### Building from Source
1. Clone the repository
2. Open the project in Android Studio
3. Sync Gradle dependencies
4. Build and run on your Android device

### Installing APK
1. Download the APK file from releases
2. Enable "Install from Unknown Sources" in Android settings
3. Install the APK on your device
4. Grant Bluetooth and Location permissions when prompted

## Usage

### First Time Setup
1. Launch the app
2. Grant Bluetooth permissions (Nearby devices) when prompted
3. Ensure your CORC device firmware is running and advertising
4. By default, the app scans for devices advertising name "CORC"
5. Once found, it will connect automatically

### Sending Commands
1. Ensure the device is connected (status shows "Connected")
2. Use the UI actions to send commands as exposed by the current version
3. Monitor logs to see BLE status and operations

### Customizing Buttons
Currently, button customization requires database modification. Future versions may include an in-app configuration UI.

### Reconnecting
- The app remembers the last connected device address
- On next launch, it attempts to connect directly without scanning
- If direct connection fails, it falls back to scanning mode

### Clearing Configuration
Use the "Clear Config" button in the menu to:
- Clear stored device address
- Disconnect from current device
- Reset to scanning mode

## Command Protocol
The app sends UTF‑8 text strings to the RX characteristic. The concrete command set depends on the connected CORC device firmware.

## Permissions
The app requires the following permissions on Android 16 (API 36):
- `BLUETOOTH_SCAN` (declared with `usesPermissionFlags="neverForLocation"`)
- `BLUETOOTH_CONNECT`

## Firmware Compatibility
This app is designed to work with CORC firmware running on Raspberry Pi Pico W (or other CORC devices). The firmware should:
- Advertise as "CORC" (default; configurable per your deployment)
- Expose the CORC GATT service UUID and RX characteristic as listed above
- Accept UTF‑8 text commands via Write Without Response

Refer to the firmware repository for setup instructions.

## Project Structure
```
/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/org/jbanaszczyk/corc/
│   │   │   │   ├── MainActivity.java
│   │   │   │   ├── CorcApp.java
│   │   │   │   └── ble/
│   │   │   │       ├── BleController.java
│   │   │   │       └── BleDevice.java
│   │   │   ├── res/
│   │   │   └── AndroidManifest.xml
│   │   ├── androidTest/
│   │   └── test/
│   └── build.gradle.kts
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

## Future Enhancements
- In-app button configuration UI
- Support for TX characteristic (receiving data from device)
- Multiple device profiles
- Button layout customization
- Export/import configuration

## Repository
- GitHub: https://github.com/co-rc/remote-android

## Related Projects
- CORC firmware for Raspberry Pi Pico W (see respective repository)

## License
This project is released under the Apache License 2.0. See LICENSE for details.
