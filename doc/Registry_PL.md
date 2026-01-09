### Rejestr Urządzeń BLE i Trwałość Danych

Niniejszy dokument opisuje projekt i sposób użycia mechanizmów zarządzania urządzeniami oraz ich trwałego przechowywania w projekcie CORC.

### Koncepcja

Projekt rozróżnia dwa rodzaje przechowywania danych o urządzeniach:

1. **BleDeviceRegistry (In-Memory)**: Rejestr działający w pamięci operacyjnej, zarządzający aktywnymi instancjami `BleDevice` oraz powiązanymi z nimi kontekstami połączeń `BleConnectionContext`.
2. **Baza danych Room (Persistent)**: Lokalna baza danych, która przechowuje znane urządzenia i konfigurację pomiędzy restartami aplikacji.

#### 1. BleDeviceRegistry

`BleDeviceRegistry` pełni rolę "Source of Truth" (źródła prawdy) dla instancji urządzeń w trakcie działania aplikacji.

* **Tożsamość**: Jako unikalny klucz wykorzystuje `BleDeviceAddress`.
* **Zarządzanie instancjami**: Gwarantuje, że dla konkretnego urządzenia fizycznego istnieje tylko jeden obiekt `BleDevice`. Zapobiega to niespójności stanów, gdy wiele komponentów wchodzi w interakcję z tym samym
  urządzeniem.
* **Stan połączenia**: Utrzymuje `BleConnectionContext` dla każdego zarejestrowanego urządzenia. Kontekst przechowuje dane tymczasowe, takie jak instancja `BluetoothGatt`, wykryte usługi oraz kolejki aktywnych operacji.
* **Użycie**:
    * `ensure(address)`: Zwraca istniejącą instancję lub tworzy nową.
    * `registerPersistedDevices(collection)`: Wypełnia rejestr urządzeniami wczytanymi z bazy danych.

#### 2. Trwałość danych (Baza danych Room)

Trwałość danych jest zrealizowana przy użyciu biblioteki Android Room. Pozwala to aplikacji "pamiętać" urządzenia nawet po jej zamknięciu.

* **Wzorzec Repository**: `RoomBleDeviceRepository` abstrahuje operacje na bazie danych. Odpowiada za mapowanie pomiędzy modelem domenowym (`BleDevice`) a encją bazy danych (`BleDevicePersistent`).
* **Operacje asynchroniczne**: Wszystkie zapisy do bazy danych są wykonywane na dedykowanym wątku tła (`corc-db-exec`), aby uniknąć blokowania głównego wątku interfejsu użytkownika (Main thread).
* **Przechowywane dane**:
    * Adres MAC urządzenia (Klucz główny).
    * Konfiguracja urządzenia (ciąg znaków JSON).

### Przepływ pracy (Usage Flow)

1. **Start aplikacji**:
    * Inicjalizacja `BleController`.
    * Wywołanie `RoomBleDeviceRepository.loadAll()` w celu pobrania znanych urządzeń z bazy.
    * Przekazanie tych urządzeń do `BleDeviceRegistry.registerPersistedDevices()`.
2. **Wykrywanie urządzeń**:
    * Podczas skanowania, `BleDeviceRegistry.ensure()` dostarcza instancję `BleDevice`.
3. **Aktualizacja danych**:
    * Po wykryciu usług lub zmianie konfiguracji, instancja `BleDevice` jest aktualizowana.
    * Usługi są przechowywane w `BleConnectionContext` (tylko w pamięci operacyjnej).
    * Wywoływane jest `BleDeviceRepository.save(device)` w celu utrwalenia zmiany konfiguracji.

### Kluczowe klasy

* `org.jbanaszczyk.corc.ble.BleDeviceRegistry`: Menedżer w pamięci (zarządzany przez `BleController`).
* `org.jbanaszczyk.corc.ble.repo.RoomBleDeviceRepository`: Implementacja `BleDeviceRepository` korzystająca z Room.
* `org.jbanaszczyk.corc.db.CorcDatabase`: Definicja bazy danych Room.
* `org.jbanaszczyk.corc.ble.internal.BleDevicePersistent`: Encja Room reprezentująca urządzenie w bazie danych.
