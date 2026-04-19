# MoneyKeeper — Архитектура и устройство кода

Это живой документ: он растёт вместе с реализацией.

---

## Структура модулей

```
MoneyKeeper/
├── app/                        — точка входа, один Activity
├── core/
│   ├── database/               — Room-сущности, DAO, БД, шифрование
│   ├── domain/                 — чистые Kotlin-модели и интерфейсы (без Android)
│   └── ui/                     — Material 3 тема, общие компоненты
└── feature/
    ├── auth/                   — экраны пароля, логика unlock
    ├── dashboard/              — главный экран
    ├── accounts/               — счета
    ├── transactions/           — операции
    ├── analytics/              — аналитика и история
    ├── forecast/               — прогнозы
    └── settings/               — настройки
```

**Правило**: `feature:*` зависит от `core:*`, но не от других `feature:*`. Это предотвращает «спагетти»-зависимости.

---

## §1 — Скелет приложения

### `app/` — один Activity

```kotlin
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val authGateViewModel: AuthGateViewModel by viewModels()

    override fun onCreate(...) {
        setContent {
            MoneyKeeperTheme(...) {
                when (authState) {
                    Uninitialized -> SetupPasswordScreen(...)
                    Locked        -> UnlockScreen(...)
                    Unlocked      -> MoneyKeeperNavHost()
                    DataCorrupted -> DataCorruptedScreen(...)
                }
            }
        }
    }
}
```

Приложение имеет **один Activity** (`MainActivity`) и один `NavHost`. Всё, что видит пользователь — Composable-функции внутри `setContent {}`. Auth-гейт находится здесь: `MoneyKeeperNavHost` рисуется только после успешного unlock.

### `MoneyKeeperNavHost` — навигация

```kotlin
@Composable
fun MoneyKeeperNavHost() {
    val navController = rememberNavController()
    Scaffold(
        bottomBar = { BottomNavigationBar(navController) },
        floatingActionButton = { AddTransactionFab(navController) }
    ) {
        NavHost(navController, startDestination = Screen.Dashboard) {
            composable<Screen.Dashboard> { StubScreen("Dashboard") }
            // ...
        }
    }
}
```

`Screen` — sealed class с маршрутами. `@Serializable data object Dashboard : Screen()` позволяет Navigation Compose автоматически сериализовать маршрут без шаблонного кода.

### `MoneyKeeperTheme`

Обёртка вокруг Material 3 `MaterialTheme`. Принимает `ThemeMode` (SYSTEM/LIGHT/DARK). В v1 всегда `SYSTEM`; пользователь сможет изменить в §9 (Settings).

---

## §2 — Data layer (`:core:database`, `:core:domain`)

### Цепочка слоёв

```
UI (Composable) 
  → ViewModel 
    → UseCase / Repository interface (core:domain)
      → RepositoryImpl (core:database)
        → DAO (Room)
          → SQLite (зашифрован SQLCipher)
```

`:core:domain` **не знает** об Android, Room или SQLCipher. Это позволяет тестировать бизнес-логику на JVM без эмулятора.

### Сущности базы данных

| Таблица | Ключевая особенность |
|---------|----------------------|
| `accounts` | `type: AccountType` — CARD/CASH/DEPOSIT/SAVINGS/INVESTMENT/OTHER |
| `transactions` | `toAccountId` nullable — заполняется только для TRANSFER |
| `categories` | `parentId` nullable — двухуровневое дерево (Income/Expense/Transfer) |
| `deposits` | FK на `accounts`, `UNIQUE(accountId)` — один депозит на счёт |
| `budgets` | месячные лимиты по категориям |

### TypeConverters — как Room хранит нестандартные типы

Room умеет хранить только примитивы (Int, Long, String, ByteArray). Для остальных типов нужен конвертер:

```kotlin
class Converters {
    @TypeConverter fun fromBigDecimal(v: BigDecimal?): String? = v?.toPlainString()
    // toPlainString() важно: BigDecimal("1.5E+5").toString() даст "1.5E+5",
    // а toPlainString() даст "150000" — читаемо и без потерь точности в SQL.

    @TypeConverter fun fromLocalDate(v: LocalDate?): String? = v?.toString()
    // LocalDate.toString() даёт ISO 8601: "2026-04-19"
    // Это позволяет сортировать даты как строки в SQL-запросах (substr, ORDER BY date DESC).
}
```

### DatabaseProvider — почему нельзя `@Provides @Singleton`

Стандартная инициализация `@Provides @Singleton` создаёт объект при первом обращении. Но `AppDatabase` требует `db_key` — ключ шифрования, которого нет до ввода пароля. Решение:

```kotlin
@Singleton
class DatabaseProvider @Inject constructor(...) {
    @Volatile private var db: AppDatabase? = null
    private val _state = MutableStateFlow<State>(State.Idle)

    @Synchronized
    fun initialize(dbKey: ByteArray) {  // вызывается ПОСЛЕ unlock
        db = Room.databaseBuilder(...)
            .openHelperFactory(SupportOpenHelperFactory(dbKey))
            .build()
        _state.value = State.Initialized
    }

    fun require(): AppDatabase = db ?: error("Not initialized!")
}
```

DAO-интерфейсы не `@Provides`-ятся напрямую: `viewModel.require().transactionDao()`.

### SQLCipher — шифрование "под капотом"

SQLCipher — это патченная версия SQLite, которая шифрует весь файл базы данных с помощью AES-256. Для Room мы просто передаём другой `openHelperFactory`:

```kotlin
val factory = SupportOpenHelperFactory(dbKey)  // dbKey — 32-байтный ключ
Room.databaseBuilder(context, AppDatabase::class.java, "moneykeeper.db")
    .openHelperFactory(factory)
    .build()
```

Без правильного `dbKey` файл `.db` — просто шифрованный мусор.

### `combine()` — реактивное объединение трёх потоков

```kotlin
// TransactionRepositoryImpl.kt
override fun observe(...): Flow<List<TransactionWithMeta>> = combine(
    txDao.observe(...),      // Flow<List<TransactionEntity>>
    accountDao.observeAll(), // Flow<List<AccountEntity>>
    categoryDao.observeAll() // Flow<List<CategoryEntity>>
) { txs, accounts, categories ->
    // собираем словари для быстрого lookup
    val accMap  = accounts.associateBy { it.id }
    val catMap  = categories.associateBy { it.id }
    txs.map { tx ->
        TransactionWithMeta(
            entity        = tx,
            accountName   = accMap[tx.accountId]?.name ?: "?",
            categoryName  = catMap[tx.categoryId]?.name,
            // ...
        )
    }
}
```

Зачем `combine`? Если пользователь переименовал счёт, список транзакций тоже обновится автоматически — без перезагрузки страницы.

---

## §3 — Auth & App Lock (`:feature:auth`)

### Цепочка безопасности

```
мастер-пароль пользователя
        │
        ▼ Argon2id (медленный KDF, 300-500 мс)
  master_key (32 байта, в памяти в MasterKeyHolder)
        │
        ▼ AES-GCM encrypt
  encrypted_db_key (хранится в EncryptedSharedPreferences)
        │
        ▼ AES-GCM decrypt при unlock
    db_key (32 байта, передаётся в SQLCipher)
        │
        ▼
  AppDatabase (зашифрованный SQLite файл)
```

**Почему два ключа?** Если хранить пароль→Argon2→db_key напрямую, то при смене пароля нужно пере-расшифровать всю БД. Вместо этого: `db_key` случайный и не меняется; при смене пароля мы только пере-шифруем маленький `db_key` новым `master_key`. Это быстро.

### AuthState — три состояния приложения

```kotlin
sealed interface AuthState {
    data object Uninitialized : AuthState  // первый запуск, пароля нет
    data object Locked        : AuthState  // пароль есть, ключ не в памяти
    data object Unlocked      : AuthState  // ключ в памяти, БД открыта
    data class  DataCorrupted(val message: String) : AuthState
}
```

`AuthGateViewModel` читает `keyStorage.isInitialized()` и `masterKeyHolder.isSet()` при старте и решает, какое состояние начальное.

### MasterKeyDerivation — Argon2id

```kotlin
class MasterKeyDerivation @Inject constructor() {
    fun derive(password: CharArray, salt: ByteArray,
               iterations: Int, memoryKb: Int, parallelism: Int): ByteArray {
        val argon2 = Argon2Factory.createAdvanced(ARGON2id, saltLen=16, hashLen=32)
        return argon2.rawHash(iterations, memoryKb, parallelism, password, UTF_8, salt)
    }
}
```

Параметры по умолчанию: `iterations=3, memoryKb=32768 (32 МБ), parallelism=2`. При bump-е параметров старые хранятся в `DatabaseKeyStorage.readKdfParams()` — совместимость не ломается.

Пароль передаётся как `CharArray`, а не `String`, потому что `String` в JVM неизменяема и висит в heap до GC. `CharArray` можно затереть вручную: `password.fill(0.toChar())`.

### MasterKeyHolder — ключ в памяти

```kotlin
@Singleton
class MasterKeyHolder @Inject constructor() {
    @Volatile private var key: ByteArray? = null

    @Synchronized
    fun set(freshKey: ByteArray) {
        key?.fill(0)      // затираем старый
        key = freshKey.copyOf()  // сохраняем копию
    }

    fun require(): ByteArray = key?.copyOf() ?: error("Not unlocked!")
}
```

`require()` возвращает **копию** — вызывающий код должен затереть её после использования. Если бы возвращался прямой доступ к `key`, случайная утечка ссылки оставила бы ключ в памяти навсегда.

### UnlockController — центральный класс unlock

Объединяет два пути входа: через пароль и через биометрию. Оба пути:
1. Получают `master_key` (Argon2id или Keystore unwrap)
2. Расшифровывают `db_key` через AES-GCM
3. Кладут `master_key` в `MasterKeyHolder`
4. Вызывают `databaseProvider.initialize(dbKey)`

Если AEAD-тег не совпадает (`AEADBadTagException`) — пароль неверный: `WrongPassword`.

### BiometricEnrollment vs BiometricAuthenticator

| Класс | Что делает |
|-------|-----------|
| `BiometricEnrollment` | Создаёт Keystore-ключ, показывает промпт для **записи** биометрии, шифрует `master_key`, сохраняет обёрнутый ключ |
| `BiometricAuthenticator` | Показывает промпт для **входа**, расшифровывает `master_key` через Keystore |

Keystore-ключ создаётся с `setInvalidatedByBiometricEnrollment(true)` — при добавлении нового отпечатка в систему ключ автоматически уничтожается. Это защита от «кто-то добавил свой палец в телефоне».

После смены пароля (`ChangePasswordViewModel`) биометрия **автоматически отключается**: старый `master_key` в Keystore-обёртке уже не соответствует новому `encrypted_db_key`. Пользователь включает заново в Settings.

### Структура файлов `:feature:auth`

```
feature/auth/src/main/java/com/moneykeeper/feature/auth/
├── di/
│   └── AuthModule.kt          — Hilt: предоставляет MasterKeyDerivation
├── domain/
│   ├── AesGcm.kt              — aesGcmEncrypt / aesGcmDecrypt (internal)
│   ├── BiometricAuthenticator.kt — показ BiometricPrompt + unwrap
│   ├── BiometricEnrollment.kt — включение/отключение биометрии
│   ├── MasterKeyDerivation.kt — Argon2id обёртка
│   ├── MasterKeyHolder.kt     — in-memory хранилище master_key
│   └── UnlockController.kt    — unlock через пароль или биометрию
├── state/
│   ├── AuthGateViewModel.kt   — Uninitialized/Locked/Unlocked/DataCorrupted
│   └── AuthState.kt           — sealed interface
└── ui/
    ├── change/                — смена пароля (Settings → Безопасность)
    ├── corrupted/             — экран при повреждении данных
    ├── setup/                 — первый запуск, установка пароля
    └── unlock/                — экран разблокировки
```

---

## §4 — Account Management (`:feature:accounts`)

### Структура пакетов

```
feature/accounts/src/main/java/com/moneykeeper/feature/accounts/
├── navigation/
│   └── AccountsNavigation.kt    — accountsGraph() extension на NavGraphBuilder
├── domain/
│   └── DepositCalculator.kt     — simpleInterest / compoundInterest / projectedBalance
└── ui/
    ├── list/
    │   ├── AccountsScreen.kt    — LazyColumn с группировкой по типу, прогресс-бар вкладов
    │   ├── AccountsViewModel.kt — combine() трёх потоков (accounts, totals, deposits)
    │   └── AccountsUiState.kt
    ├── edit/
    │   ├── EditAccountScreen.kt — форма: имя, тип-чипы, валюта, цвет, баланс
    │   ├── EditAccountViewModel.kt
    │   ├── EditAccountUiState.kt
    │   └── DepositSection.kt    — вложенная секция вклада с live-прогнозом (derivedStateOf)
    ├── detail/
    │   ├── AccountDetailScreen.kt
    │   └── AccountDetailViewModel.kt
    └── transfer/
        ├── TransferScreen.kt
        └── TransferViewModel.kt
```

### Инвариант DEPOSIT-счёта

`account.balance` DEPOSIT-счёта всегда равен `deposit.initialAmount`. Поле «Начальный
баланс» скрыто в форме — `EditAccountViewModel.save()` форсит `balance = deposit.initialAmount`
перед записью. Для не-DEPOSIT существующего счёта баланс перечитывается из БД перед
сохранением, чтобы не затереть изменения от фоновых воркеров.

### DepositCalculator

Чистый `object` без Android-зависимостей. Алгоритм:

- **Простые проценты**: `A = P × r × days / 365`, знаменатель всегда 365.
- **Сложные проценты**: итерация по календарным периодам через `plusMonths`/`plusYears`
  (не через фиксированное число дней), капитализация на каждом полном периоде, хвост —
  простые проценты без капитализации.
- `projectedBalance(deposit, atDate)` — обрезает `atDate` до `endDate`, вычисляет нарастающим итогом.

Live-превью в `DepositSection` считается через `derivedStateOf` прямо в Composable — без
отдельного ViewModel.

### Навигация

`accountsGraph(navController)` регистрирует 4 destination в `NavHost`:

| Route | Экран |
|-------|-------|
| `accounts` | Список счетов |
| `accounts/{accountId}` | Детали счёта |
| `accounts/{accountId}/edit` | Форма создания/редактирования (`-1` = новый) |
| `accounts/transfer` | Перевод между счетами |

### Переводы

`TransferViewModel.transfer()` последовательно:
1. Создаёт `Transaction(type=TRANSFER, accountId=from, toAccountId=to)`
2. `accountRepo.adjustBalance(fromId, -amount)`
3. `accountRepo.adjustBalance(toId, +amount)`

### Валидация формы

`EditAccountViewModel.save()` проверяет все обязательные поля перед записью:

| Ошибка | Условие | Отображение |
|--------|---------|-------------|
| `NameEmpty` | `name.isBlank()` | `supportingText` под полем «Название» |
| `DepositAmountInvalid` | `initialAmount ≤ 0` | `supportingText` под «Суммой вклада» |
| `DepositRateInvalid` | `interestRate ≤ 0` | `supportingText` под «Ставкой» |
| `DepositDateInvalid` | `endDate ≤ startDate` | `supportingText` под «Датой окончания» |
| `DepositParamsMissing` | `deposit == null` при типе DEPOSIT | Snackbar |
| `Domain` | ошибка из репозитория | Snackbar |

Поле-специфичные ошибки (`NameEmpty`, `Deposit*`) очищаются автоматически: `onNameChange`
и `onDepositChange` всегда сбрасывают `state.error = null`. Snackbar-ошибки обрабатываются
в `LaunchedEffect(state.error)` в `EditAccountScreen`.

`DepositSection` получает `error: EditAccountError?` как явный параметр (не читает ViewModel
напрямую) — это позволяет тестировать секцию в изоляции с `createComposeRule()`.

### SQLCipher — loadLibrary()

`DatabaseProvider.initialize()` вызывает `System.loadLibrary("sqlcipher")` перед
созданием `SupportOpenHelperFactory`. Это обязательно: в `net.zetetic:sqlcipher-android`
4.5+ метод `SQLiteDatabase.loadLibs()` удалён, а нативная `.so` не загружается
автоматически до первого вызова `nativeOpen`. Вызов идемпотентен — повторная загрузка
уже подгруженной библиотеки — no-op.

---

## Соглашения по коду

- **Все строки** в `strings.xml`. Никаких захардкоженных русских слов в `.kt`.
- **Форматирование чисел и дат** через `AppLocale.current()` (не `Locale("ru")`).
- **Ключи/пароли** — `CharArray`/`ByteArray`, затирать в `finally`.
- **No backup**: `android:allowBackup="false"`, ключи Keystore не переносятся между устройствами.
- **Release minify**: `isMinifyEnabled=true`, ProGuard-правила для Room/Hilt/SQLCipher/Argon2.
