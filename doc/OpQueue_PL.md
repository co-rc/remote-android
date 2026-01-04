### Architektura i zasady działania OperationQueue

`OperationQueue` jest kluczowym komponentem stosu BLE w projekcie CORC, zaprojektowanym do zarządzania sekwencyjnym wykonywaniem operacji GATT na pojedynczej instancji `BluetoothGatt`.

#### Przegląd projektu

Operacje BLE na Androidzie są asynchroniczne i muszą być wykonywane jedna po drugiej. Wydanie nowej komendy GATT przed zakończeniem poprzedniej często prowadzi do nieprzewidywalnego zachowania lub cichych błędów. `OperationQueue` rozwiązuje ten problem poprzez:

1.  **Serializację**: Wykorzystuje `ArrayBlockingQueue` do przechowywania oczekujących obiektów `BleOperation`.
2.  **Zarządzanie stanem**: Utrzymuje flagę `inProgress`, aby zapewnić, że tylko jedna operacja jest aktywna w danym czasie.
3.  **Ochronę przed timeoutem**: Każda operacja jest strzeżona przez timer (watchdog). Jeśli operacja nie zasygnalizuje zakończenia w określonym czasie, kolejka automatycznie rozłącza instancję GATT, aby zapobiec zawieszeniu stosu.
4.  **Abstrakcję**: Oddziela *intencję* (co zrobić) od *wykonania* (jak to zrobić) za pomocą interfejsu `OperationExecutor`.

#### Kluczowe komponenty

*   **BleOperation**: Niemutowalna klasa danych opisująca operację (READ, WRITE, ENABLE_NOTIFY itp.), docelowy UUID oraz opcjonalny ładunek (payload).
*   **OperationExecutor**: Interfejs odpowiedzialny za wykonywanie rzeczywistych wywołań `BluetoothGatt`. `StandardGattOperationExecutor` jest domyślną implementacją.
*   **Scheduler**: Abstrakcja nad środowiskiem wykonawczym (np. `Handler` w Androidzie), ułatwiająca testy jednostkowe.
*   **TimeoutProvider**: Interfejs funkcyjny dostarczający czas timeoutu, co pozwala na dynamiczną konfigurację (np. zależną od urządzenia).

#### Przepływ operacji

1.  Operacja jest dodawana przez `enqueue()`.
2.  Jeśli kolejka nie jest zajęta, a klient GATT jest w stanie `READY`, operacja jest pobierana z kolejki.
3.  Flaga `inProgress` jest ustawiana na `true`.
4.  Planowane jest zadanie timeoutu.
5.  Operacja jest przekazywana do `OperationExecutor`.
6.  Gdy `BluetoothGattCallback` zasygnalizuje zakończenie (np. `onCharacteristicWrite`), wywoływane jest `onOperationFinished()`.
7.  `onOperationFinished()` czyści timeout, resetuje `inProgress` na `false` i wyzwala kolejną operację z kolejki.

#### Zasady (SOLID)

*   **Zasada jednej odpowiedzialności (SRP)**: Kolejka zarządza wyłącznie cyklem życia i kolejnością operacji. Nie wie, *jak* zapisać dane do charakterystyki ani *co* te dane oznaczają.
*   **Zasada otwarte/zamknięte (OCP)**: Nowe typy operacji GATT można dodawać poprzez rozszerzenie `BleOperation` i aktualizację `OperationExecutor` bez zmiany głównej logiki kolejki.
*   **Zasada odwrócenia zależności (DIP)**: Kolejka zależy od abstrakcji (`Scheduler`, `OperationExecutor`, `TimeoutProvider`), a nie od konkretnych klas Androida.

---

### Rozszerzanie w przyszłości

#### Dodawanie negocjacji MTU
Aby dodać negocjację MTU, wykonaj następujące kroki:
1.  Dodaj `REQUEST_MTU` do enum `BleOperationType`.
2.  Dodaj pole dla żądanej wartości MTU w klasie `BleOperation` oraz metodę fabryczną `BleOperation.requestMtu(int)`.
3.  Zaktualizuj `StandardGattOperationExecutor`, aby obsługiwał typ `REQUEST_MTU` poprzez wywołanie `gatt.requestMtu(mtu)`.
4.  W `BleGattClient` zaimplementuj callback `onMtuChanged` i wywołaj `operationQueue.onOperationFinished()`.

#### Implementacja transferu plików
Transfer plików zazwyczaj wiąże się z sekwencją operacji i asynchronicznymi notyfikacjami:
1.  **Zdefiniuj transakcję**: Stwórz wysokopoziomową klasę (np. `FileDownloadTransaction`), która koordynuje kroki.
2.  **Kolejkuj sekwencję**: Transakcja dodaje do kolejki:
    *   `BleOperation.requestMtu(...)` (aby zmaksymalizować przepustowość).
    *   `BleOperation.enableNotify(...)` (aby nasłuchiwać fragmentów danych).
    *   `BleOperation.write(...)` (aby wysłać komendę "start transfer").
3.  **Obsługuj notyfikacje**: Dodaj listener w `BleGattClient` dla `onCharacteristicChanged`. Ten callback jest *zewnętrzny* względem `OperationQueue` (notyfikacje są wysyłane przez urządzenie peryferyjne).
4.  **Składaj dane**: Klasa transakcji zbiera przychodzące fragmenty, aż do otrzymania znacznika końca pliku (EOF).
