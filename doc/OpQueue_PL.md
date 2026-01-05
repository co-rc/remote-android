### Architektura i zasady działania OperationQueue

`OperationQueue` jest kluczowym komponentem stosu BLE w projekcie CORC, zaprojektowanym do zarządzania sekwencyjnym wykonywaniem operacji GATT na pojedynczej instancji `BluetoothGatt`. Obecnie wspiera operacje asynchroniczne z wykorzystaniem `CompletableFuture`.

#### Przegląd projektu

Operacje BLE na Androidzie są asynchroniczne i muszą być wykonywane jedna po drugiej. Wydanie nowej komendy GATT przed zakończeniem poprzedniej często prowadzi do nieprzewidywalnego zachowania lub cichych błędów. `OperationQueue` rozwiązuje ten problem poprzez:

1.  **Serializację**: Wykorzystuje `ArrayBlockingQueue` do przechowywania oczekujących obiektów `BleOperation`.
2.  **Zarządzanie stanem**: Utrzymuje stan GATT w `BleConnectionContext` i zapewnia, że tylko jedna operacja jest aktywna w danym czasie dzięki fladze `inProgress`.
3.  **API oparte na Future**: Każda operacja zwraca `CompletableFuture<T>`, który jest kompletowany w momencie nadejścia callbacku GATT.
4.  **Ochronę przed timeoutem**: Każda operacja jest strzeżona przez timer (watchdog). Jeśli operacja nie zasygnalizuje zakończenia w określonym czasie, kolejka kończy Future z błędem i **automatycznie rozłącza** instancję GATT, aby zapobiec zawieszeniu stosu BLE.
5.  **Obsługa błędów (bez rozłączania)**: Jeśli operacja zakończy się błędem GATT (przez `onOperationFailed`), Future jest kończony wyjątkiem, ale połączenie GATT **zostaje utrzymane**, umożliwiając wykonanie kolejnych operacji.
6.  **Abstrakcję**: Oddziela *intencję* (co zrobić) od *wykonania* (jak to zrobić) za pomocą interfejsu `OperationExecutor`.

#### Kluczowe komponenty

*   **BleOperation<T>**: Niemutowalna klasa danych opisująca operację (READ, WRITE, ENABLE_NOTIFY, DISABLE_NOTIFY, REQUEST_MTU), docelowy UUID oraz opcjonalne parametry. Przechowuje obiekt `CompletableFuture<T>`.
*   **OperationExecutor**: Interfejs odpowiedzialny za wykonywanie rzeczywistych wywołań `BluetoothGatt`. `StandardGattOperationExecutor` jest domyślną implementacją.
*   **Scheduler**: Abstrakcja nad środowiskiem wykonawczym (np. `Handler` w Androidzie), ułatwiająca testy jednostkowe.
*   **BleCommandResponseManager**: Zlokalizowany w `core.protocol`, zarządza ramkowaniem protokołu CMD/RSP, korelacją żądań i kodami wyników.
*   **BleRemoteException**: Wyjątek rzucany, gdy urządzenie peryferyjne zwróci niezerowy kod wyniku (np. BUSY, UNSUPPORTED).

#### Przepływ operacji

1.  Operacja jest dodawana przez `enqueue()`.
2.  Jeśli kolejka nie jest zajęta, a klient GATT jest w stanie `READY`, operacja jest pobierana z kolejki.
3.  Flaga `inProgress` jest ustawiana na `true`.
4.  Planowane jest zadanie timeoutu.
5.  Operacja jest przekazywana do `OperationExecutor`.
6.  Gdy `BluetoothGattCallback` zasygnalizuje zakończenie (np. `onCharacteristicWrite`, `onMtuChanged`), wywoływane jest `onOperationFinished(result)` lub `onOperationFailed(throwable)`.
7.  Obiekt Future powiązany z operacją zostaje zakończony.
8.  `onOperationFinished()` czyści timeout, resetuje `inProgress` na `false` i wyzwala kolejną operację z kolejki.

#### Zasady (SOLID)

*   **Zasada jednej odpowiedzialności (SRP)**: Kolejka zarządza wyłącznie cyklem życia i kolejnością operacji. Logika protokołu została przeniesiona do `BleCommandResponseManager`.
*   **Zasada otwarte/zamknięte (OCP)**: Nowe typy operacji GATT można dodawać poprzez rozszerzenie `BleOperation` i aktualizację `OperationExecutor` bez zmiany głównej logiki kolejki.
*   **Zasada odwrócenia zależności (DIP)**: Kolejka zależy od abstrakcji (`Scheduler`, `OperationExecutor`, `TimeoutProvider`), a nie od konkretnych klas Androida.

---

### Protokół Komenda/Odpowiedź (CMD/RSP)

Dla wysokopoziomowych komend, CORC używa protokołu binarnego opartego na dwóch charakterystykach:
*   **CMD**: Kanał zapisu z odpowiedzią (Write With Response).
*   **RSP**: Kanał notyfikacji.

`BleCommandResponseManager` zajmuje się ramkowaniem:
*   **Żądanie (Request)**: `[Magic(0xC07C)] [RequestId] [Opcode] [Len] [Payload]`
*   **Odpowiedź (Response)**: `[Magic(0xC07C)] [RequestId] [Opcode] [Result] [Len] [Payload]`

---

### Rozszerzanie w przyszłości

#### Implementacja transferu plików
Transfer plików wiąże się z sekwencją operacji:
1.  **Negocjacja MTU**: Dodaj do kolejki `BleOperation.requestMtu(512)`, aby zwiększyć przepustowość.
2.  **Włączenie notyfikacji**: Dodaj do kolejki `BleOperation.enableNotify(RSP_UUID)`.
3.  **Komenda startu**: Użyj `gattClient.sendCommand(...)`, aby zasygnalizować początek transferu.
4.  **Obsługa fragmentów**: Urządzenie przesyła fragmenty danych przez notyfikacje. Są one obsługiwane przez `BleCommandResponseManager` lub dedykowany koordynator transakcji.
5.  **Zakończenie**: Transfer kończy się w momencie odebrania specyficznego opcode'u lub rezultatu EOF.
