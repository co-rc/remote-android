# BleDeviceRegistry vs. OperationQueue

Ten dokument porównuje i wyjaśnia relację między dwoma kluczowymi komponentami stosu BLE w projekcie CORC:
`BleDeviceRegistry` oraz `OperationQueue`.

### Przegląd

Mimo że oba komponenty są kluczowe dla zarządzania operacjami BLE, działają one na różnych warstwach architektury i mają
odmienne obowiązki w zakresie zarządzania stanem i wykonania.

### 1. BleDeviceRegistry: Globalny stan i tożsamość

`BleDeviceRegistry` służy jako **Globalne Źródło Prawdy (Source of Truth)** dla tożsamości urządzeń i wysokopoziomowego
stanu połączenia.

- **Rola**: Przechowuje mapowanie wszystkich znanych urządzeń (`BleDevice`) i powiązanych z nimi kontekstów połączeń ( `BleConnectionContext`).
- **Zasięg**: Cała aplikacja (cykl życia zbliżony do singletona, zarządzany przez `BleController`).
- **Trwałość (Persistence)**: Połączony z bazą danych Room poprzez `RoomBleDeviceRepository`. Zapewnia, że dane urządzeń (usługi, konfiguracja) przetrwają restart aplikacji.
- **Granularność**: Na poziomie urządzenia. Rozróżnia urządzenia na podstawie ich adresu MAC (`BleDeviceAddress`).
- **Stan**: Śledzi "kim" są urządzenia i jaki jest ich aktualny status połączenia (np. `CONNECTING`, `READY`).

### 2. OperationQueue: Przejściowa serializacja operacji

`OperationQueue` to **Lokalny Serializator** dla niskopoziomowych operacji GATT.

- **Rola**: Zapewnia, że operacje GATT (odczyt, zapis, żądanie MTU) są wykonywane sekwencyjnie, co pozwala utrzymać stabilność stosu BLE.
- **Zasięg**: Przejściowy (transient). Zarządza bieżącym przepływem wykonania.
- **Trwałość**: Brak. Operacje są krótkotrwałe i są usuwane po zakończeniu, niepowodzeniu lub rozłączeniu urządzenia.
- **Granularność**: Świadomy wielu urządzeń. Choć jest to wspólna kolejka, śledzi ona, do którego urządzenia należy każda operacja za pomocą `BleDeviceAddress`.
- **Stan**: Śledzi "co" jest aktualnie wykonywane i zarządza limitami czasu (timeouts) dla poszczególnych operacji.

### Tabela porównawcza

| Cecha              | `BleDeviceRegistry`                   | `OperationQueue`                    |
|:-------------------|:--------------------------------------|:------------------------------------|
| **Główny cel**     | Śledzenie urządzeń i stanu            | Serializacja i czas operacji        |
| **Granularność**   | Na urządzenie (mapa adresów)          | Wiele urządzeń (operacje z adresem) |
| **Trwałość**       | Tak (przez bazę Room)                 | Nie (stan przejściowy)              |
| **Cykl życia**     | Globalny / Długotrwały                | Związany z wykonaniem / Krótki      |
| **Źródło prawdy**  | Tożsamość i usługi                    | Bieżący stan wykonania GATT         |
| **Współbieżność**  | Bezpieczny wątkowo magazyn            | Bezpieczne wykonanie (szeregowe)    |
| **Obsługa błędów** | Aktualizacja stanu (np. DISCONNECTED) | Timeouty i zakończenie Future       |

### Przepływ interakcji

1. **Odkrywanie**: `BleController` znajduje urządzenie, a `BleDeviceRegistry` zapewnia istnienie obiektów `BleDevice` i `BleConnectionContext`.
2. **Odczyt/Zapis**: Gdy aplikacja chce odczytać charakterystykę, `BleController` tworzy obiekt `BleOperation` ( zawierający adres urządzenia) i umieszcza go w `OperationQueue`.
3. **Wykonanie**: `OperationQueue` sprawdza, czy jakaś operacja jest już w toku. Jeśli nie, pobiera następną operację i zleca `BleGattClient` jej wykonanie.
4. **Zakończenie**: Po otrzymaniu wywołania zwrotnego GATT (np. `onCharacteristicRead`), `BleGattClient` powiadamia `OperationQueue`, która kończy odpowiedni `CompletableFuture` i uruchamia następną zakolejkowaną
   operację.
5. **Rozłączenie**: Jeśli urządzenie się rozłączy, `BleGattClient` wywołuje `operationQueue.clear(address)`. Kolejka usuwa wszystkie operacje należące do tego konkretnego adresu, nie wpływając na operacje innych
   urządzeń.
