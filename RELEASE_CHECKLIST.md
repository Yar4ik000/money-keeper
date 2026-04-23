# Чек-лист релиза MoneyKeeper

Ставь галочки перед каждой раздачей APK друзьям. Один пропущенный пункт = потеря данных
у пользователя или невозможность установить обновление.

---

## Перед сборкой

- [ ] **`AppDatabase.VERSION` поднят?**
  Если изменён хоть один `@Entity` (добавлено/удалено/переименовано поле, изменён тип,
  индекс или FK) — поднять обязательно.

- [ ] **Миграция добавлена?**
  Для каждого bump VERSION нужен либо `AutoMigration(from=N-1, to=N)` в аннотации
  `@Database`, либо ручной `Migration(N-1, N)` в `AppDatabase.MIGRATIONS`.

- [ ] **Добавили новую логику?**
  Надо добавить соответствующие обычные тесты и если логика нетривиальная то runtime

- [ ] **`MigrationsTest` зелёный?**
  ```
  ./gradlew connectedAndroidTest
  ```
  Запустить на реальном устройстве или эмуляторе. Эмулятор всегда подключен, можно запускать в любой момент

- [ ] **Все unit-тесты зелёные?**
  ```
  ./gradlew test
  ```

- [ ] **Схема БД закоммичена?**
  `core/database/schemas/com.moneykeeper.core.database.AppDatabase/<N>.json`
  создаётся автоматически при сборке — не забыть добавить в коммит.

- [ ] **`versionCode` поднят на +1?**
  Текущий: `app/build.gradle.kts` → `defaultConfig.versionCode`.
  Android отказывает в установке поверх, если `versionCode` не вырос.

- [ ] **`versionName` обновлён?**
  Семантика: `MAJOR.MINOR.PATCH` — например `1.1.0` для новой фичи, `1.0.1` для патча.

- [ ] **Добавлена новая логика?**
  Надо добавить соответствующие пункты в docs/
---

## Специальные случаи

- [ ] **Менял KDF-параметры или формат EncryptedSharedPreferences?**
  Написать миграционный код в `DatabaseKeyStorage` (бамп суффикса ключей `_v1` → `_v2`).
  Без этого `AEADBadTagException` → `AuthState.DataCorrupted` у друга после апдейта.

- [ ] **Менял формат `BackupManifest`?**
  Обновить `BackupCompatibilityChecker`. Старые бэкапы должны либо восстанавливаться,
  либо давать понятную ошибку «бэкап создан несовместимой версией».

- [ ] **v1.3+ — миграция пользователей с master-password на PIN (first-run after update)?**
  Убедиться что `DatabaseKeyStorage.isInitialized()` (v1.2 поля) возвращает `true` у
  пользователя с v1.2 данными, и что `computeInitialState()` правильно переходит в
  `PinSetupRequired`. Проверить на реальном устройстве с v1.2 данными перед раздачей.
  Если после обновления появляется `DataCorrupted` — это критическая потеря данных.

- [ ] **Менял `Worker`'ов в WorkManager (сигнатуру Data, периодичность или `WORK_NAME`)?**
  Поставить `ExistingPeriodicWorkPolicy.UPDATE` на один релиз (или переименовать `WORK_NAME`).

---

## Сборка и проверка

- [ ] **Собрать release-APK:**
  ```
  ./gradlew :app:assembleRelease
  ```
  APK: `app/build/outputs/apk/release/app-release.apk`

- [ ] **Проверить подпись:**
  ```
  jarsigner -verify -verbose -certs app/build/outputs/apk/release/app-release.apk
  ```
  Должно быть `jar verified` с `CN=` из твоего keystore.

- [ ] **Поставить поверх на тестовом устройстве** (у себя, не у друга):
  - APK устанавливается без ошибок
  - Unlock работает со старым паролем
  - Все транзакции, счета, вклады на месте
  - Миграции прошли без `DataCorruptedScreen`

---

## Публикация

- [ ] **Git tag на коммите релиза:**
  ```
  git tag v1.X.Y
  git push origin v1.X.Y
  ```
  Позволяет пересобрать идентичный APK в будущем.

- [ ] **Отправить друзьям** файл `app-release.apk` + инструкцию из `docs/INSTALL_GUIDE.md`.

---

## Таблица истории релизов

| versionCode | versionName | Дата | Что изменилось |
|-------------|-------------|------|----------------|
| 100 | 1.0.0 | 2026-04-21 | Первый релиз |
| 101 | 1.1.0 | 2026-04-21 | «Блокировать скриншоты», бюджеты по валютам, процент и фикс прогресс-бара |
| 102 | 1.2.0 | 2026-04-21 | Динамический «Прирост/Убыток»; системные настройки уведомлений; autofill для паролей; блокировка начального баланса при редактировании; подсказка при правке recurring; архив/восстановление счетов; перестановка счетов; трёхцветные пороги бюджетов + бейдж на навигации; корректный прогноз для не-капитализированных вкладов |
| 103 | 1.3.0 | 2026-04-21 | Ежедневный PIN/биометрия отделён от пароля резервных копий; Keystore-хранение master_key; миграция с мастер-пароля (v1.2 → v1.3); `ChangePinScreen`; `ProtectedActionGate` для чувствительных настроек; workers гейтятся на `DatabaseProvider.State` |
| 104 | 1.3.1 | 2026-04-22 | Security hardening: Argon2id 3/16384/1, CharArray PIN buffer, StrongBox, lockout clock-rollback fix, crash recovery для restore-pending; PIN-экраны Box-layout, subtitle центрирован, биометрия под клавиатурой; нейтральный фон |
| 105 | 1.3.2 | 2026-04-23 | Honest recurring toggle + scope-aware delete + management screen; inline «+ new» в пикерах; long-press selection в категориях; контекстные подсказки по вкладкам; zero-balance для карт/наличных; скрытие UI уведомлений до v1.6 (AlarmManager); терминология «Накопительный счёт» / «Среднесуточный» |
