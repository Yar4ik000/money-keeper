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
| `accounts` | `type: AccountType` — CARD/CASH/DEPOSIT/SAVINGS/INVESTMENT/OTHER; `iconName: String` — ключ иконки |
| `transactions` | `toAccountId` nullable — заполняется только для TRANSFER |
| `categories` | `parentId` nullable — двухуровневое дерево (Income/Expense/Transfer); `iconName: String` — ключ иконки |
| `deposits` | FK на `accounts`, `UNIQUE(accountId)` — один депозит на счёт |
| `budgets` | `categoryIds TEXT` nullable (null=все, "1,2,3"=конкретные); `accountIds TEXT` nullable (null=все); без FK |

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

## §5 — Transaction Management (`:feature:transactions`)

### Структура пакетов

```
feature/transactions/src/main/java/com/moneykeeper/feature/transactions/
├── navigation/
│   └── TransactionsNavigation.kt    — transactionsGraph() (5 routes)
├── domain/
│   └── TransactionSaver.kt          — UseCase: атомарный save/replace/delete/deleteMany
└── ui/
    ├── add/
    │   ├── AddTransactionScreen.kt  — stateless composable + AddTransactionRoute wrapper
    │   ├── AddTransactionViewModel.kt
    │   ├── AddTransactionUiState.kt — AddTxError / KeyboardKey sealed types
    │   └── DatePickerHelper.kt      — LocalDatePickerDialog (переиспользуется RecurringRuleSheet)
    ├── edit/
    │   ├── EditTransactionScreen.kt — EditTransactionRoute reuses AddTransactionScreen
    │   └── EditTransactionViewModel.kt
    ├── categories/
    │   ├── CategoriesScreen.kt      — SwipeToDismissBox + FAB
    │   ├── CategoriesViewModel.kt
    │   ├── EditCategoryScreen.kt
    │   ├── EditCategoryUiState.kt
    │   └── EditCategoryViewModel.kt
    └── components/
        ├── NumericKeyboard.kt       — onKey(KeyboardKey) + onOk()
        ├── TransactionTypeSelector.kt — FilterChip row (INCOME/EXPENSE/TRANSFER/SAVINGS)
        ├── CategoryPicker.kt        — ModalBottomSheet с expandable деревом, filter по типу
        ├── AccountPicker.kt         — ModalBottomSheet со списком счетов
        └── RecurringRuleSheet.kt    — ModalBottomSheet: частота, интервал, дата конца
```

### TransactionSaver — атомарность через TransactionRunner

`TransactionSaver` использует `TransactionRunner` (interface в `core:domain`) вместо прямой зависимости на `AppDatabase`. Это позволяет тестировать `TransactionSaver` в JVM-тестах с `FakeTransactionRunner { block() }`, не поднимая Room.

```
TransactionRunner (core:domain) ← реализация в DatabaseModule (core:database)
    │                                  db.withTransaction { block() }
    ▼
TransactionSaver (feature:transactions)
    ├── save(tx, rule?) → insert + applyBalanceEffect
    ├── replace(old, new, rule?) → reverseBalance(old) + upsert + applyBalance(new)
    ├── delete(tx) → delete + reverseBalance
    └── deleteMany(ids) → getByIds → аккумулировать per-account дельты → deleteByIds → adjustBalances
```

Баланс-эффект по типу:
- `INCOME` → `+amount` на `accountId`
- `EXPENSE`, `SAVINGS` → `-amount` на `accountId`
- `TRANSFER` → `-amount` на `accountId`, `+amount` на `toAccountId`

### Stateless composable для переиспользования в Edit

`AddTransactionScreen(uiState, onTypeChange, onKeyPress, ...)` — чисто stateless.
`AddTransactionRoute` и `EditTransactionRoute` оборачивают соответствующие ViewModel и вызывают одну и ту же composable. Это соблюдает Compose-паттерн "lift state up".

### CategoryPicker — дерево категорий

Фильтрация по `CategoryType`:
- `INCOME` → `CategoryType.INCOME`
- `EXPENSE`, `SAVINGS` → `CategoryType.EXPENSE`
- `TRANSFER` → `CategoryType.TRANSFER`

Раскрытие дочерних: `expandedId` local state — клик по родителю с детьми раскрывает/сворачивает. Кнопка `+` переходит в `CategoriesScreen` для добавления.

### Навигация (5 routes)

| Route | Экран |
|-------|-------|
| `transactions/add?accountId={accountId}` | Новая транзакция (accountId опционален) |
| `transactions/{transactionId}/edit` | Редактировать транзакцию |
| `transactions/categories` | Список категорий |
| `transactions/categories/add` | Новая категория |
| `transactions/categories/{categoryId}/edit` | Редактировать категорию |

### RecurringRule.lastGeneratedDate — источник истины

`lastGeneratedDate` — единственное поле, определяющее до какой даты уже сгенерированы транзакции. Генератор (`GenerateRecurringTransactionsUseCase`, реализуется в §8) читает его внутри `withTransaction` после перезахвата лока, что защищает от гонки двух параллельных вызовов (Worker + startup). Подробнее — в §8.

---

## §6 — Dashboard

### Структура файлов

```
feature/dashboard/
├── navigation/DashboardNavigation.kt   — dashboardGraph(navController)
└── ui/
    ├── DashboardUiState.kt             — DashboardUiState, DepositWithDaysLeft
    ├── DashboardViewModel.kt           — combine(5 flows)
    ├── DashboardScreen.kt              — DashboardRoute + DashboardScreen (stateless)
    └── components/
        ├── TotalBalanceCard.kt         — мульти-валютный баланс
        ├── AccountsCarousel.kt         — горизонтальный LazyRow
        ├── MonthlySummaryCard.kt       — доходы/расходы + LinearProgressIndicator
        ├── ExpiringDepositsWidget.kt   — вклады < 30 дней до окончания
        └── RecentTransactionsList.kt   — TransactionListItem + RecentTransactionsHeader
```

### Ключевые решения

**DepositCalculator перенесён в `core:domain`.**  
Ранее он жил в `feature:accounts`. Поскольку его использует и `feature:dashboard`, он перемещён в `core/domain/src/main/java/com/moneykeeper/core/domain/calculator/DepositCalculator.kt`. Это единственное место для расчёта простых и сложных процентов.

**Утилиты форматирования и иконок в `core:ui`.**  
`CurrencyFormatter.kt` — `BigDecimal.formatAsCurrency(currency)` с маппингом валюта→локаль. `ColorUtils.kt` — `parseHexColor(hex)` для перевода `#RRGGBB` строки в Compose `Color`.  
`AccountIconMapper.kt` — `ACCOUNT_ICON_OPTIONS: List<Pair<String, ImageVector>>` (18 иконок, ключи: `"bank"`, `"wallet"`, `"card"`, `"chart"`, `"business"`, `"store"`, `"safe"`, `"family"`, `"trophy"` и т.д.) и `accountIconVector(iconName)` с fallback на `AccountBalance`.  
`CategoryIconMapper.kt` — `CATEGORY_ICON_OPTIONS` (32 иконки, ключи: `"other"`, `"food"`, `"transport"`, `"bar"`, `"clothes"`, `"fuel"`, `"kids"`, `"music"`, `"cinema"`, `"beauty"`, `"pharmacy"`, `"internet"`, `"nature"`, `"charity"`, `"repair"`, `"subscriptions"`, `"moto"` и т.д.) и `categoryIconVector(iconName)` с fallback на `Category`.  
`CurrencyFormatter.kt` — `formatAsCurrency(currency)` и `currencySymbol(currency)`. Поддерживаемые коды: RUB, USD, EUR, GBP, CNY, KZT; каждому сопоставлена локаль через `localeFor()`, что даёт корректный символ (₽, $, €, £, ¥, ₸).  
Все feature-модули используют эти утилиты напрямую, не дублируя маппинг.

**DashboardViewModel — combine(5 flows).**  
Объединяет `observeActiveAccounts()`, `observeTotalsByCurrency()`, `observePeriodSummary(from, to)`, `observeRecent(10)`, `observeExpiringSoon(30)` в единый `StateFlow<DashboardUiState>` через `stateIn(WhileSubscribed(5s))`. Сортировка `MultiCurrencyTotal.entries` — defaultCurrency первой.

**TotalBalanceCard — стратегия C (no conversion).**  
Каждая валюта — отдельная строка. Первая строка (defaultCurrency) — `headlineLarge`, остальные — `titleMedium`. Красный цвет при отрицательном балансе.

**DashboardScreen имеет собственный FAB.**  
Глобальный FAB в `MoneyKeeperNavHost` отключён для маршрута `dashboard` (убран из `GLOBAL_FAB_ROUTES`). Сам `DashboardScreen` передаёт `onAddTransaction(null)` в `transactionsGraph`.

### Инварианты

- `ExpiringDepositsWidget` виден только когда `state.expiringDeposits.isNotEmpty()`.
- `TransactionListItem` показывает `categoryIconVector(meta.categoryIcon)` на залитом цветом круге. `categoryIcon` приходит из `TransactionWithMeta`, который собирается в `TransactionRepositoryImpl.combine()`.
- `currentMonthName()` форматирует через `AppLocale.current()` (не хардкод `ru`).

### Instrumented тесты (8 сценариев, `DashboardScreenTest.kt`)

| Тест | Что проверяет |
|------|---------------|
| `totalBalanceCard_showsZeroWhenNoAccounts` | `0,00 ₽` при пустом MultiCurrencyTotal |
| `totalBalanceCard_showsBalanceForSingleCurrency` | `150 000,00 ₽` |
| `accountsCarousel_showsAccountName` | имя счёта в карусели |
| `monthlySummaryCard_showsEmptyMessage_whenNoSummary` | строка «За этот месяц операций нет» |
| `monthlySummaryCard_showsIncomeAndExpense` | метки «Доходы» / «Расходы» |
| `expiringDepositsWidget_notShown_whenEmpty` | виджет скрыт |
| `expiringDepositsWidget_shown_whenHasDeposits` | виджет + имя вклада видны |
| `recentTransactions_showsCategoryName` | имя категории в списке операций |

---

## §7 — History & Analytics (`:feature:analytics`)

### Структура файлов

```
feature/analytics/src/main/java/com/moneykeeper/feature/analytics/
├── navigation/
│   └── AnalyticsNavigation.kt         — analyticsGraph(navController), 3 destinations
├── ui/
│   ├── history/
│   │   ├── HistoryUiState.kt           — HistoryFilter, HistoryUiState, TransactionGroup
│   │   ├── HistoryViewModel.kt         — flatMapLatest on filter + selection state
│   │   ├── HistoryScreen.kt            — LazyColumn + stickyHeader + selection mode
│   │   └── FilterBottomSheet.kt        — period/type/account/category chips
│   ├── analytics/
│   │   ├── AnalyticsUiState.kt         — CategoryExpense, MonthlyBarEntry (UI-level)
│   │   ├── AnalyticsViewModel.kt       — combine(period, currency, accounts).flatMapLatest
│   │   └── AnalyticsScreen.kt          — PieChart + BarChart + PeriodSelector + CurrencyChips
│   ├── category/
│   │   ├── CategoryAnalyticsUiState.kt — CategoryMonthlyEntry, CategoryAnalyticsUiState
│   │   ├── CategoryAnalyticsViewModel.kt — per-category trend from observe() grouped by month
│   │   └── CategoryAnalyticsScreen.kt
│   └── components/
│       ├── PeriodSelector.kt           — month navigation with disabled future arrow
│       ├── PieChart.kt                 — Canvas Arc donut (no Vico)
│       ├── BarChart.kt                 — MonthlyBarChart + CategoryTrendBarChart via Vico
│       └── TransactionGroupedList.kt   — TransactionGroupHeader + TransactionHistoryItem
```

### Ключевые решения

**`TransactionDeleter` interface в `core:domain`.**
`HistoryViewModel` нуждается в `deleteMany(ids)` с обратным пересчётом баланса, но `TransactionSaver` (который это реализует) находится в `feature:transactions`. Кросс-фиче зависимости запрещены. Решение: интерфейс `TransactionDeleter` в `core:domain/repository`, `TransactionSaver` его реализует, Hilt-binding `@Binds` в `feature:transactions/di/TransactionModule`. `HistoryViewModel` инжектирует интерфейс.

**HistoryViewModel — selection поверх derived state.**
`uiState` производится из `_filter.flatMapLatest { ... }`. Состояние выбора (`selectedIds`, `isSelectionMode`) хранится в отдельных `MutableStateFlow` и объединяется в финальный `StateFlow` через `combine(derivedTransactions, _selectedIds, _isSelectionMode)`. При изменении фильтра выбор сбрасывается автоматически.

`HistoryFilter` содержит: `from`/`to` (LocalDate), `accountIds`, `categoryIds`, `types` (Set), `query` (String), `minAmount`/`maxAmount` (BigDecimal?). DB-запрос фильтрует только по дате; остальное — `applyHistoryFilter()` в памяти (один месяц ≈ 50–300 строк для личных финансов, пагинация не нужна).

**MonthlyBarEntry — два уровня.**
В `core:domain/analytics` существует `MonthlyBarEntry(yearMonth: String, ...)` — DAO-ориентированная форма (ISO "2026-04"). В `analytics/ui/analytics` создан UI-уровневый `MonthlyBarEntry(month: YearMonth, ...)`. Маппинг `YearMonth.parse(entry.yearMonth)` происходит в `AnalyticsViewModel`.

**CategoryAnalyticsViewModel — тренд без отдельного DAO.**
`observeMonthlyTrendForCategory` не существует. Тренд вычисляется client-side: `observe(categoryId=..., from=6monthsAgo, to=today)` → группировка по `YearMonth.from(date)` в ViewModel.

**AnalyticsScreen — CurrencyChipRow скрыт при одной валюте.**
Если `availableCurrencies.size <= 1`, FlowRow с FilterChip-ами не рендерится.

**Цвета и иконки в разбивке.**
`CategoryExpense` использует `category.colorHex` и `category.iconName` напрямую через `categoryIconVector()`. `AccountBreakdown` несёт `accountColorHex` и `accountIconName` — ViewModel берёт их из объекта `Account` при сборке `expensesByAccount` / `incomeByAccount`. `ForecastEngine` аналогично строит `accountMap` для заполнения `accountColorHex`/`accountIconName` в каждом `TimelineEvent`.

**Batch delete с балансом.**
`TransactionSaver.deleteMany` аккумулирует per-account дельты из всех удаляемых транзакций, затем атомарно удаляет и применяет корректировки через `TransactionRunner.run { ... }`.

### Маршруты

| Route | Экран |
|-------|-------|
| `analytics` | AnalyticsScreen (доходы vs расходы, пирог) |
| `analytics/history` | HistoryScreen (группировка по дням, фильтр) |
| `analytics/category/{categoryId}` | CategoryAnalyticsScreen (детализация) |

### Instrumented тесты (7 сценариев, `HistoryScreenTest.kt`)

| Тест | Что проверяет |
|------|---------------|
| `historyScreen_showsLoadingIndicator_whenStateIsLoading` | TopAppBar виден при Loading |
| `historyScreen_showsEmptyMessage_whenNoTransactions` | «Операций нет» при пустом Success |
| `historyScreen_showsTransactionCategoryName` | Имя категории в списке |
| `historyScreen_showsPeriodTotals_whenPresent` | Итоги периода рендерятся |
| `tappingSearchIcon_showsSearchFieldInToolbar` | Лупа открывает инлайн-поиск в шапке |
| `typingInSearchField_passesQueryToFilterUpdate` | Набор текста передаёт запрос в фильтр |
| `closingSearchBar_hidesFieldAndClearsQuery` | Стрелка ← сбрасывает поиск |

---

## §8 — Financial Forecasting (`:feature:forecast`)

### Структура файлов

```
feature/forecast/src/main/java/com/moneykeeper/feature/forecast/
├── navigation/ForecastNavigation.kt        — forecastGraph(navController)
├── domain/ForecastEngine.kt                — чистая бизнес-логика прогноза
└── ui/
    ├── ForecastUiState.kt / ForecastViewModel.kt / ForecastScreen.kt
    └── components/
        ├── ForecastDatePicker.kt           — быстрые пресеты + MaterialDatePicker
        ├── ForecastSummaryTable.kt         — per-account и per-currency итоги
        └── EventTimeline.kt               — LazyListScope: sticky month headers + события
```

Доменные типы в `core/domain/src/main/java/com/moneykeeper/core/domain/forecast/`:

- `ForecastModels.kt` — `ForecastResult`, `AccountForecast`, `ForecastCurrencyTotal`, `TimelineEvent`
- `RecurringDates.kt` — `RecurringRule.expandDates(from, to): List<LocalDate>`, `LocalDate.advance(frequency, interval)`

### ForecastEngine — как считается прогноз

`ForecastEngine.calculate(accounts, deposits, recurringRules, targetDate)` работает в три шага:

1. **Начальные балансы**: `balances = accounts.associate { id → balance }`
2. **Регулярные правила**: `rule.expandDates(today+1, targetDate)` → для каждой даты: применяем delta к `balances`, добавляем `TimelineEvent`
3. **Вклады**:
   - Если `endDate != null && endDate ≤ targetDate` → матурация: `DepositCalculator.projectedBalance(deposit, endDate)`, выводим principal из `balances`, кредитуем `payoutAccountId`
   - Иначе (активный/накопительный) → начисляем накопленные проценты к `targetDate`

`TimelineEvent.description` берётся из `templateTransaction.note` (если не пустая), иначе из `categoryName`.

### Инварианты

- `endDate` у `Deposit` nullable — накопительные счета (SAVINGS) не имеют срока. `ForecastEngine` и все UI-компоненты обязаны обрабатывать `null`.
- `ForecastDatePicker` запрещает выбор прошедших дат через `SelectableDates`.
- Прогноз для накопительного счёта НЕ показывается в форме создания счёта (нет горизонта).

### JVM unit тесты (7 сценариев, `ForecastEngineTest.kt`)

Тестируют: нулевой прогноз на «сегодня», ежемесячный доход/расход, матурацию вклада, накопление процентов, группировку по валюте, порядок событий.

---

## §9 — Notifications & WorkManager (`:app`)

### Структура файлов

```
app/src/main/java/com/moneykeeper/app/
├── notification/
│   ├── NotificationChannels.kt   — создание 2 каналов при старте
│   └── NotificationHelper.kt     — showDepositExpiry() с deep-link PendingIntent
├── worker/
│   ├── DepositExpiryWorker.kt    — @HiltWorker, раз в сутки, в 08:00
│   ├── RecurringTransactionWorker.kt — @HiltWorker, раз в сутки
│   └── WorkerScheduler.kt        — enqueueUniquePeriodicWork при старте приложения
└── di/
    ├── WorkerModule.kt           — Configuration.Provider через Hilt
    └── PostUnlockModule.kt       — PostUnlockCallback: catchup + workers после unlock
```

### Инициализация

```
MoneyKeeperApp.onCreate()
  ├── NotificationChannels.createAll()   — регистрация каналов (Android 8+)
  └── WorkerScheduler.scheduleAll()      — periodic workers с задержкой до 08:00
```

`MoneyKeeperApp` реализует `Configuration.Provider` — WorkManager получает `HiltWorkerFactory` и умеет создавать `@HiltWorker`-классы с инжекцией.

### Гейтинг Workers на unlock

Оба Worker'а начинают с:
```kotlin
if (!masterKeyHolder.isSet()) return Result.retry()
```
Если Worker запустился до unlock (OS разбудила процесс для периодической задачи), он возвращает `Result.retry()` с backoff и ждёт. После unlock `PostUnlockCallback` запускает one-time catchup через `WorkManager.enqueueUniqueWork(..., REPLACE, ...)`.

### PostUnlockCallback — паттерн отложенной инициализации

`UnlockController` инжектирует `Set<@JvmSuppressWildcards PostUnlockCallback>` (Hilt multibinding). После успешного unlock вызывает `callback.onUnlocked()` для каждого.

**Критически важно**: `PostUnlockModule.providePostUnlockCallback` принимает `Provider<CatchUpRecurringTransactionsUseCase>` (не сам `use-case`). Это предотвращает eager-конструкцию цепочки DAO → `AppDatabase.require()` в момент инжекции `PostUnlockCallback` в `UnlockController` (который создаётся ДО unlock, во время показа `UnlockScreen`). `Provider.get()` вызывается только внутри `onUnlocked()`, когда БД уже открыта.

```
PostUnlockCallback провайдится как Singleton
     │  (захватывает Provider<CatchUp>, не сам CatchUp)
     │
UnlockController.notifyUnlocked() после databaseProvider.initialize()
     │
     └─► appScope.launch(IO) {
             catchUpProvider.get()()   ← только здесь создаётся CatchUp и его DAO-зависимости
             WorkManager.enqueueUniqueWork(DepositExpiry + "_catchup")
             WorkManager.enqueueUniqueWork(RecurringTx  + "_catchup")
         }
```

### GenerateRecurringTransactionsUseCase — идемпотентный догон

Живёт в `core:domain`. Читает `lastGeneratedDate` внутри `TransactionRunner.run { ... }` (= `db.withTransaction`), генерирует пропущенные транзакции, обновляет дату. Повторный вызов в тот же день — no-op. Параллельный вызов (Worker + catchup) — безопасен: Room-транзакция сериализует доступ.

### SettingsRepository

`AppSettings` (data class в `core:domain`) + `SettingsRepository` (interface) + `SettingsRepositoryImpl` (DataStore в `core:database`). Настройки: `depositNotificationsEnabled`, `recurringRemindersEnabled`, `defaultNotifyDaysBefore`, `themeMode`, `currencyCode`.

### Deep links из уведомлений

`DeepLinks` object в `core:ui` определяет схему `moneykeeper://accounts/{accountId}`. `AccountsNavigation.kt` добавляет `deepLinks = listOf(navDeepLink { uriPattern = DeepLinks.ACCOUNT_DETAIL_PATTERN })` к `AccountDetail` composable. `NotificationHelper` создаёт `PendingIntent` с этим URI. `MainActivity` с `launchMode=singleTop` обрабатывает входящий Intent через `onNewIntent`.

---

## §10 — Settings, Polish & Export

### Тема из настроек

`MainActivity` инжектирует `MainViewModel` (обёртка над `SettingsRepository`). `themeMode: String` ("system"/"light"/"dark") читается из DataStore и маппируется в `ThemeMode` enum из `core:ui`. `MoneyKeeperTheme` оборачивает всю `setContent`-ветку, включая auth-экраны, — тема меняется без перезапуска Activity.

### Онбординг

`AppSettings.onboardingCompleted: Boolean` хранится в DataStore. После успешного unlock `MainActivity` проверяет этот флаг: если `false` — показывает `OnboardingScreen` вместо `MoneyKeeperNavHost`. `OnboardingScreen` — 3 страницы через `HorizontalPager` (Compose Foundation), на третьей — запрос разрешения `POST_NOTIFICATIONS`. `OnboardingViewModel.completeOnboarding()` пишет `onboardingCompleted = true`, экран заменяется NavHost автоматически через State.

### CSV-экспорт

`ExportCsvUseCase` в `feature:settings/domain/`. Вызывает `transactionRepo.getAll()` (метод добавлен в интерфейс `TransactionRepository`, impl использует `observe(from=2000, to=2099).first()`). CSV: разделитель `;`, BOM `\uFEFF` для Excel, экранирование RFC 4180 (`CsvEscape.kt`). Запуск через SAF `ActivityResultContracts.CreateDocument("text/csv")`.

### Резервное копирование (зашифрованный бэкап)

**Интерфейсы в `core:domain`:**
- `MasterKeyProvider.requireKey()` — `MasterKeyHolder` реализует интерфейс; биндинг в `AuthBindingsModule`
- `KeyDerivation.derive(...)` — `MasterKeyDerivation` реализует интерфейс; биндинг в `AuthBindingsModule`
- `BackupRepository` — `createBackup(uri)`, `getBackupInfo(uri)`, `restoreBackup(uri, password)`, `restartProcess(activity)`

**`BackupRepositoryImpl` в `core:database`:**
- Backup: `masterKey` из `MasterKeyProvider` → WAL checkpoint → SQLCipher `cipher_export` plain-дамп → AES-GCM шифрование → zip с `manifest.json` + `database.enc`
- Restore: читает `manifest.json` → Argon2id из пароля пользователя → расшифровка `database.enc` → пишет в `*.restore-pending` → `Files.move(ATOMIC_MOVE)` → `exitProcess(0)`
- Совместимость: `manifest.databaseVersion > AppDatabase.VERSION` → `IncompatibleVersion` (отказ без изменения файлов)

**UI:** `BackupScreen` с тремя SAF-лаунчерами (CSV, backup, restore), диалог ввода пароля для restore, финальный диалог «Перезапустить».

### Управление повторяющимися правилами (v1.3.3)

**Настройки → Повторяющиеся операции** — `RecurringRulesScreen` + `RecurringRuleDetailScreen` в `feature:settings/ui/recurring/`.

`RecurringRulesViewModel` подписывается на `recurringRuleRepo.observeAllWithTemplates()` — это JOIN правила с его транзакцией-шаблоном. Список показывает иконку/имя категории, сумму, частоту, следующую дату генерации (`rule.expandDates(today, today+90).firstOrNull()`).

`RecurringRuleDetailViewModel.stop()` вызывает `recurringRuleRepo.delete(id)`. FK `SET NULL` сохраняет все уже сгенерированные транзакции — `recurringRuleId` в них становится `null`.

### Настройки безопасности (Смена пароля и биометрия)

Доступны через **Настройки → Безопасность**. Реализованы в двух модулях:

**`ChangePasswordScreen` / `ChangePasswordViewModel`** (`:feature:auth`):
- Три поля: текущий пароль, новый, подтверждение нового.
- Проверка старого пароля: Argon2id → сравнение с `MasterKeyHolder.require()`. Нельзя обойти фактом «я и так Unlocked».
- После успешной смены: `db_key` пере-шифровывается новым `master_key`, биометрия автоматически отключается, показывается диалог «Старые бэкапы расшифровываются предыдущим паролем».
- Навигация: маршрут `settings/change_password` зарегистрирован в `MoneyKeeperNavHost` (`:app`), а не в `SettingsNavigation` — это позволяет `:feature:settings` не иметь прямой зависимости на `ChangePasswordScreen`.

**`SecurityViewModel`** (`:feature:settings`):
- Инжектирует `BiometricEnrollment` из `:feature:auth` (единственное место, где `:feature:settings` зависит от `:feature:auth`).
- Экспонирует `isBiometricAvailable: Boolean` (константа, проверяется при создании) и `isBiometricEnrolled: StateFlow<Boolean>`.
- `enrollBiometric(activity)` запускает `BiometricEnrollment.enroll()` в `viewModelScope`, обновляет `isBiometricEnrolled` по результату.
- `disableBiometric()` — немедленное отключение без биометрического подтверждения.
- Ошибки (не зарегистрирован отпечаток в системе, устройство не поддерживает `BIOMETRIC_STRONG`) — `enrollError: StateFlow<String?>`, отображается через `SnackbarHost` в `SettingsScreen`.

Switch биометрии показывается только если `BiometricEnrollment.isAvailable()` вернул `true`.

### Бюджеты

`BudgetsViewModel` объединяет `BudgetRepository.observeAll()`, `CategoryRepository.observeByType(EXPENSE)`, `AccountRepository.observeActiveAccounts()`, `TransactionRepository.observe(currentMonth)` через Kotlin `combine`.

`Budget.categoryIds: Set<Long>` — пустой set означает «все категории»; хранится в БД как `TEXT?` (null = все, `"1,2,3"` = конкретные). Аналогично `accountIds`. FK на `categories` отсутствует — поэтому `CategoryDao` использует `@Upsert` (не `INSERT OR REPLACE`): `@Upsert` работает через `INSERT OR IGNORE + UPDATE`, не удаляет строку, и не триггерит `ON DELETE SET_NULL` на `transactions.categoryId`.

Экран: `LazyColumn` карточек с `LinearProgressIndicator` (красный при > 100%), FAB открывает `AlertDialog` с:
- мульти-чекбокс категорий (checkbox «Все категории» + список с иконками)
- мульти-чекбокс счетов
- поле суммы и выбор периода MONTHLY/WEEKLY

---

## Соглашения по коду

- **Все строки** в `strings.xml`. Никаких захардкоженных русских слов в `.kt`.
- **Форматирование чисел и дат** через `AppLocale.current()` (не `Locale("ru")`).
- **Ключи/пароли** — `CharArray`/`ByteArray`, затирать в `finally`.
- **No backup**: `android:allowBackup="false"`, ключи Keystore не переносятся между устройствами.
- **Release minify**: `isMinifyEnabled=true`, ProGuard-правила для Room/Hilt/SQLCipher/Argon2.
