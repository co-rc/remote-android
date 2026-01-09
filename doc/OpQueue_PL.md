### Architektura i zasady działania OperationQueue

`OperationQueue` jest kluczowym komponentem stosu BLE w projekcie CORC, zaprojektowanym do zarządzania sekwencyjnym wykonywaniem operacji GATT na wielu instancjach `BluetoothGatt`. Zapewnia on bezpieczeństwo wątkowe, serializację oraz solidną obsługę błędów.

#### Przegląd projektu

Operacje BLE na Androidzie są asynchroniczne i muszą być wykonywane jedna po drugiej dla każdego urządzenia. Wydanie nowej komendy GATT przed zakończeniem poprzedniej często prowadzi do nieprzewidywalnego zachowania lub cichych błędów. `OperationQueue` rozwiązuje ten problem poprzez:

1.  **Serializację**: Wykorzystuje wewnętrzną kolejkę do przechowywania rekordów `EnqueuedOperation`, które grupują `BleOperation`, docelową instancję `BluetoothGatt` oraz konkretny `OperationExecutor`.
2.  **Bezpieczeństwo wielourządzeniowe**: Przechowując kontekst GATT wraz z każdą operacją, jedna kolejka może zarządzać operacjami dla wielu połączonych urządzeń bez ryzyka ich pomieszania.
3.  **Zarządzanie stanem**: Utrzymuje stan GATT w `BleConnectionContext` i zapewnia, że tylko jedna operacja jest aktywna w danym czasie dzięki fladze `inProgress`.
4.  **API oparte na Future**: Każda operacja zwraca `CompletableFuture<T>`, który jest kompletowany w momencie nadejścia callbacku GATT.
5.  **Ochronę przed timeoutem**: Każda operacja jest strzeżona przez timer (watchdog). Jeśli operacja nie zasygnalizuje zakończenia w określonym czasie, kolejka kończy Future z błędem i **automatycznie rozłącza** instancję GATT, aby zapobiec zawieszeniu stosu BLE.
6.  **Obsługa błędów (bez rozłączania)**: Jeśli operacja zakończy się błędem GATT lub błędem synchronicznym (np. `gatt.writeCharacteristic()` zwróci `false`), błąd jest przechwytywany, Future kończony wyjątkiem, ale połączenie GATT **zostaje utrzymane** (chyba że był to timeout), umożliwiając wykonanie kolejnych operacji.
7.  **Abstrakcję**: Oddziela *intencję* (co zrobić) od *wykonania* (jak to zrobić) za pomocą interfejsu `OperationExecutor`.

#### Kluczowe komponenty

*   **BleOperation<T>**: Niemutowalna klasa danych opisująca operację (READ, WRITE, ENABLE_NOTIFY, DISABLE_NOTIFY, REQUEST_MTU), docelowy UUID oraz opcjonalne parametry. Przechowuje obiekt `CompletableFuture<T>`.
*   **EnqueuedOperation**: Wewnętrzny rekord parujący `BleOperation` z docelowym `BluetoothGatt` i `OperationExecutor`.
*   **OperationExecutor**: Interfejs odpowiedzialny za wykonywanie rzeczywistych wywołań `BluetoothGatt`. `StandardGattOperationExecutor` jest domyślną implementacją.
*   **StandardGattOperationExecutor**: Implementuje synchroniczne wykrywanie błędów. Jeśli metoda GATT zwróci `false` (oznaczając niepowodzenie startu), rzuca `RuntimeException`, który jest przechwytywany przez kolejkę.
*   **Scheduler**: Abstrakcja nad środowiskiem wykonawczym (np. `Handler` w Androidzie), ułatwiająca testy jednostkowe.
*   **BleCommandResponseManager**: Zlokalizowany w `core.protocol`, zarządza ramkowaniem protokołu CMD/RSP, korelacją żądań i kodami wyników.

#### Przepływ operacji

1.  Operacja jest dodawana przez `enqueue()`.
2.  Jeśli kolejka nie jest zajęta, operacja jest pobierana.
3.  Flaga `inProgress` jest ustawiana na `true`.
4.  Planowane jest zadanie timeoutu.
5.  Operacja jest przekazywana do `OperationExecutor`.
6.  Gdy `BluetoothGattCallback` zasygnalizuje zakończenie (np. `onCharacteristicWrite`, `onMtuChanged`), wywoływane jest `onOperationFinished(result)` lub `onOperationFailed(throwable)`.
7.  Obiekt Future powiązany z operacją zostaje zakończony.
8.  `onOperationFinished()` czyści timeout, resetuje `inProgress` na `false` i wyzwala kolejną operację z kolejki.

#### Zasady (SOLID)

*   **Zasada jednej odpowiedzialności (SRP)**: Kolejka zarządza wyłącznie cyklem życia i kolejnością operacji. Logika protokołu została przeniesiona do `BleCommandResponseManager`, a logika wykonawcza do `OperationExecutor`.
*   **Zasada otwarte/zamknięte (OCP)**: Nowe typy operacji GATT można dodawać poprzez rozszerzenie `BleOperation` i aktualizację `OperationExecutor` bez zmiany głównej logiki kolejki.
*   **Zasada odwrócenia zależności (DIP)**: Kolejka zależy od abstrakcji (`Scheduler`, `OperationExecutor`, `TimeoutProvider`), a nie od konkretnych klas Androida.

---

### Negocjacja MTU

Aby zmaksymalizować przepustowość i zapewnić kompatybilność z protokołem `BleCommandResponseManager`, projekt implementuje automatyczną negocjację MTU:

*   **Docelowe MTU**: `263` bajty.
    *   `255` bajtów payloadu aplikacji.
    *   `5` bajtów nagłówka protokołu.
    *   `3` bajty narzutu GATT.
*   **Przepływ**:
    1.  Po osiągnięciu stanu `STATE_CONNECTED`, `BleGattClient` dodaje do kolejki operację `REQUEST_MTU`.
    2.  `discoverServices()` jest wywoływane dopiero **po** zakończeniu negocjacji MTU (sukcesem lub błędem).
    3.  Wynegocjowane MTU jest zapisywane w `BleConnectionContext`.

---

### Protokół Komenda/Odpowiedź (CMD/RSP)

Dla wysokopoziomowych komend, CORC używa protokołu binarnego opartego na dwóch charakterystykach:
*   **CMD**: Kanał zapisu z odpowiedzią (Write With Response).
*   **RSP**: Kanał notyfikacji.

`BleCommandResponseManager` zajmuje się ramkowaniem:
*   **Żądanie (Request)**: `[Magic(0xC07C)] [RequestId] [Opcode] [Len] [Payload]`
*   **Odpowiedź (Response)**: `[Magic(0xC07C)] [RequestId] [Opcode] [Result] [Len] [Payload]`

---

### Rozszerzanie przepływu

#### Implementacja transferu plików
Transfer plików wiąże się z sekwencją operacji:
1.  **Negocjacja MTU**: Dodawana automatycznie (docelowo `263`).
2.  **Włączenie notyfikacji**: Dodaj do kolejki `BleOperation.enableNotify(RSP_UUID)`.
3.  **Komenda startu**: Użyj `gattClient.sendCommand(...)`, aby zasygnalizować początek transferu.
4.  **Obsługa fragmentów**: Urządzenie przesyła fragmenty danych przez notyfikacje. Są one obsługiwane przez `BleCommandResponseManager` lub dedykowany koordynator transakcji.
5.  **Zakończenie**: Transfer kończy się w momencie odebrania specyficznego opcode'u lub rezultatu EOF.
