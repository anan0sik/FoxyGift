# FoxyGift — Система подарочных карт на NFC

Система для выдачи, погашения и управления подарочными картами на базе NFC-чипов NTAG213/215/216.
Карты защищены паролем (PWD_AUTH) и подписью HMAC-SHA256.

---

## Компоненты проекта

```
FoxyGift/
├── android/FoxyGiftPOS/        # Android POS-терминал (Kotlin + Jetpack Compose)
├── web/
│   ├── bridge/                 # PC/SC bridge — HTTP-сервер для Desktop (Java)
│   ├── admin_provisioning_tool.html   # Веб-инструмент преинициализации карт
│   └── client_analytics_dashboard.html
└── scratch/                    # Временные файлы
```

---

## Android — FoxyGiftPOS

### Стек
- **Язык**: Kotlin
- **UI**: Jetpack Compose + Material3
- **DI**: Hilt
- **БД**: Room (SQLite локально на устройстве)
- **Безопасность**: EncryptedSharedPreferences (AES256-GCM)
- **NFC**: `MifareUltralight` + `NfcA` (android.nfc)
- **Камера/QR**: CameraX + ML Kit Barcode Scanning
- **Сеть**: только для Telegram-алертов
- **Coroutines**: kotlinx.coroutines
- `compileSdk = 35`, `minSdk = 26`, `targetSdk = 35`, `jvmTarget = 17`
- `applicationId = "com.foxygift.pos"`

### Структура пакетов
```
com.foxygift.pos/
├── core/
│   ├── nfc/
│   │   ├── NtagDriver.kt           # Низкоуровневый драйвер NTAG (чтение/запись/активация)
│   │   ├── HmacSha256Engine.kt     # HMAC-SHA256, усечённый до 16 байт
│   │   ├── DecimalUidConverter.kt  # UID 7 байт → десятичная строка
│   │   └── TearOffRecovery.kt      # Восстановление после tear-off
│   ├── printer/
│   │   ├── EscPosDriver.kt         # ESC/POS для Bluetooth термопринтера
│   │   ├── ReceiptBuilder.kt       # Построение чека
│   │   └── BalticCharsetRenderer.kt
│   ├── provisioning/
│   │   └── QrProvisioningScanner.kt # QR-сканер для провизии терминала
│   ├── security/
│   │   ├── PinSecurityManager.kt   # Анти-брут PIN (прогрессивные блокировки до permanent lock)
│   │   ├── PinHasher.kt
│   │   └── TelegramAlarmClient.kt  # Telegram Silent Alarm при взломе
│   └── kiosk/
│       └── BootReceiver.kt         # Автозапуск при загрузке (kiosk-режим)
├── data/
│   ├── db/
│   │   ├── FoxyGiftDatabase.kt
│   │   ├── CardDao.kt
│   │   ├── TransactionDao.kt
│   │   └── entities/
│   │       ├── CardEntity.kt       # cards: card_number_dec (PK), nominal/balance cents, status
│   │       └── TransactionEntity.kt
│   └── repository/
│       ├── CardRepository.kt
│       └── ProvisionRepository.kt
├── di/
│   └── AppModule.kt               # Hilt модули
└── ui/
    ├── navigation/
    │   └── FoxyNavigation.kt      # Routes: PIN → HOME → {ISSUE,REDEEM,PROLONG,NFC_READ,SETTINGS,Z_REPORT,PROVISION}
    ├── screens/
    │   ├── pin/                   # PinScreen + PinViewModel (вход в терминал)
    │   ├── home/                  # HomeScreen — главное меню
    │   ├── nfc/                   # NfcReadScreen + NfcOperationViewModel (NFC overlay)
    │   ├── issue/                 # IssueScreen — выдача новой карты
    │   ├── redeem/                # RedeemScreen — списание/погашение
    │   ├── prolong/               # ProlongScreen — продление срока
    │   ├── provision/             # ProvisionScreen + ProvisionViewModel — QR-провизия терминала
    │   ├── settings/              # SettingsScreen — язык, принтер, PIN смена
    │   └── zreport/               # ZReportScreen — Z-отчёт / экспорт
    └── theme/
        ├── AppStrings.kt          # i18n строки (без XML ресурсов)
        ├── FoxyGiftTheme.kt / Theme.kt / Color.kt / Typography.kt
        └── LocaleManager.kt
```

### NFC — Структура памяти карты NTAG
| Page | Содержимое |
|------|-----------|
| 04 | Magic bytes `FOXY` (46 4F 58 59) |
| 05 | MERCHANT_ID_HASH (4 байта) |
| 06-07 | Nominal value, cents, big-endian int32 |
| 08-09 | Current balance, cents, big-endian int32 |
| 10-11 | Expiry Unix epoch (seconds), big-endian int32 |
| 12 | Status byte: `0x01`=ACTIVE, `0x02`=EXHAUSTED, `0x03`=PRE_INIT, `0x04`=PROLONGED |
| 13-16 | HMAC-SHA256 усечённый (16 байт = 4 pages × 4 байта) |
| CFG | AUTH0=04, PROT=1, PWD (4 байта), PACK (2 байта) |

- Аутентификация: `PWD_AUTH` команда `0x1B` + 4-байтный PWD
- UID хранится как **десятичная строка** (`card_number_dec`), никогда не HEX
- `NtagDriver` — `object` (singleton), все операции через `suspend fun` на `Dispatchers.IO`

### PIN Security
- Попытки 1-3: предупреждения
- Попытка 4: блокировка 30 сек
- Попытка 5: блокировка 1 мин + Telegram alert
- Попытки 6-9: блокировки 5-15 мин
- Попытка 10+: **permanent lock** — разблокировка только QR-провизией
- Хранится в `EncryptedSharedPreferences` (переживает reboot)

### Манифест — ключевые разрешения
- `NFC` (required)
- `BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN` (принтер)
- `CAMERA` (QR-сканирование)
- `INTERNET` (Telegram alerts only)
- `RECEIVE_BOOT_COMPLETED` (kiosk autostart)
- `launchMode = singleTask`, `HOME` категория (kiosk)

---

## Web — Admin Provisioning Tool

**Файл**: `web/admin_provisioning_tool.html` (standalone HTML, ~165 KB)

### Назначение
Массовая преинициализация NTAG-карт через USB-ридер ACR1581U (Desktop).

### Workflow «конвейер»
1. Запустить Bridge (`run_bridge.bat` или `foxygift_bridge.ps1`)
2. Открыть `admin_provisioning_tool.html` в браузере
3. Вкладка «💳 Card Pre-Init (ACR1581U)»
4. Выбрать Merchant → «⚡ Start Auto-Conveyor»
5. Прикладывать чистые NTAG карты → каждая программируется за ~100-150ms → аудиосигнал

---

## Web — PC/SC Bridge (FoxyGiftBridge.java)

**Файл**: `web/bridge/FoxyGiftBridge.java` (~66 KB)

### Назначение
Мост между браузером и Windows WinSCard (SmartCard subsystem).  
Браузеры запрещают прямой доступ к USB CCID (class 0x0B).

### Запуск
```cmd
run_bridge.bat       # Windows (рекомендуется, ищет Java автоматически)
foxygift_bridge.ps1  # PowerShell fallback
```

### HTTP API (порт 8989, localhost only)
| Endpoint | Handler | Описание |
|---------|---------|----------|
| `/status` | StatusHandler | Статус bridge |
| `/readers` | ReadersHandler | Список PC/SC ридеров |
| `/card-status` | CardStatusHandler | Присутствие карты на ридере |
| `/pre-init` | PreInitHandler | Программирование NTAG (magic+merchant+PWD) |
| `/transmit` | TransmitHandler | Передача raw APDU |
| `/balance` | ReadBalanceHandler | Чтение баланса |
| `/diagnose` | DiagnoseHandler | Диагностика |
| `/*` | StaticFileHandler | Статические файлы |

### Поддерживаемые ридеры
- **ACS ACR1581U DualBoost II** (основной)
- ACS ACR122U
- ACS ACR1252U

---

## Конвенции разработки

### Общие
- Комментарии в коде — **английский** язык
- Переменные, классы — camelCase/PascalCase по Kotlin conventions
- `sealed class` для Result-типов (ReadResult, WriteResult, PinCheckResult)
- `object` для stateless утилит (NtagDriver, HmacSha256Engine)
- `@Singleton` + `@Inject` для сервисов через Hilt

### NFC / Карты
- UID **всегда** как десятичная строка (`card_number_dec`)
- Все суммы в **центах** (Int), не в рублях/копейках
- Время истечения — **Unix epoch seconds** (Int)
- Данные карты: big-endian ByteBuffer
- После каждой записи — обязательная верификационная перечитка

### Безопасность
- Секреты (masterKey, PWD, PACK) — **никогда** в логах
- PIN — только через `PinHasher`, хранится зашифрованным
- Telegram alerts — fire-and-forget (не блокируют основной поток)

### Android Build
- Не трогать `.gradle/`, `.idea/`, `build/` директории
- `java_pid*.hprof` — heap dump файлы, не коммитить
- Release: minify + shrinkResources включены

---

## Что НЕ делать
- Не хранить UID карт в HEX формате — только decimal
- Не сбрасывать PIN lockout без QR-провизии (нарушение security policy)
- Не добавлять сетевые вызовы кроме Telegram alerts
- Не менять структуру памяти NTAG без синхронизации с Bridge и Web tool
- Не запускать Bridge публично (только `127.0.0.1:8989`)
