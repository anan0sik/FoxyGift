# FoxyGift 🎁

**FoxyGift** is an end-to-end gift card management and redemption platform built around NFC technology (NXP NTAG213/215/216 chips). The system provides offline-resilient card issuing, balance replenishment, card prolongation, and instant redemption with cryptographic verification (HMAC-SHA256 and PWD_AUTH).

---

## 🏗 Architecture & Components

```
FoxyGift/
├── android/
│   └── FoxyGiftPOS/                  # Android POS terminal (Kotlin, Jetpack Compose, Room, Hilt)
├── web/
│   ├── admin_provisioning_tool.html  # Desktop card pre-initialization conveyor
│   ├── client_analytics_dashboard.html # Client analytics dashboard with Telegram Bot Cloud Sync
│   └── bridge/                       # High-speed PC/SC USB SmartCard Bridge (Java)
└── GEMINI.md                         # Technical specifications and architecture guidelines
```

### 1. Android POS Terminal (`android/FoxyGiftPOS`)
- **Technology Stack**: Kotlin, Jetpack Compose, Material3, Dagger Hilt, Room DB, EncryptedSharedPreferences.
- **NFC Engine**: Direct low-level APDU interaction (`NfcA` / `MifareUltralight`) with tearing protection (`TearOffRecovery`).
- **Security**: Progressive PIN lockout (anti-brute force), silent alarm triggering, HMAC-SHA256 signature verification.
- **Shift & Reporting**: Generates automated Z-Reports and transaction CSV exports dispatched securely to Telegram channels.
- **Printing**: Built-in ESC/POS receipt generation for Bluetooth thermal printers with Baltic charset support.

### 2. Desktop Card Pre-Initialization (`web/admin_provisioning_tool.html`)
- Standalone HTML5 web application for conveyor-style mass card provisioning using USB smart card readers (e.g. ACS ACR1581U DualBoost II, ACR122U).
- Configures card memory layout, writes magic bytes, sets merchant hashes, and provisions cryptographic write protection (`AUTH0`, `PROT`, `PWD`, `PACK`) in 100–150ms per card.

### 3. PC/SC SmartCard Bridge (`web/bridge/FoxyGiftBridge.java`)
- Lightweight HTTP micro-service running on `127.0.0.1:8989` that bridges browser JavaScript to Windows WinSCard / CCID hardware.

### 4. Client Analytics Dashboard (`web/client_analytics_dashboard.html`)
- Comprehensive offline-first dashboard powered by IndexedDB.
- Real-time card ledger, transaction journal, revenue metrics, turnover analytics, and Z-report archives.
- **Telegram Bot Cloud Sync**: Direct browser-to-Telegram Bot API polling for automated transaction log ingestion from field POS terminals.

---

## 🔒 NFC Card Memory Map

| Page | Description | Content |
|:---:|:---|:---|
| `04` | Magic Header | `FOXY` (`0x464F5859`) |
| `05` | Merchant ID Hash | 4-byte merchant identity identifier |
| `06 - 07` | Nominal Value | Big-endian int32 (cents) |
| `08 - 09` | Current Balance | Big-endian int32 (cents) |
| `10 - 11` | Expiry Date | Unix epoch timestamp (seconds) |
| `12` | Status Byte | `0x01` ACTIVE, `0x02` EXHAUSTED, `0x03` PRE_INIT, `0x04` PROLONGED |
| `13 - 16` | HMAC Signature | 16-byte truncated HMAC-SHA256 |
| `CFG` | Security Configuration | `AUTH0`, `PROT`, PWD (4 bytes), PACK (2 bytes) |

---

## 🚀 Getting Started

### Building the Android POS App
Prerequisites: Android Studio Jellyfish / Koala (JDK 17+).
```bash
cd android/FoxyGiftPOS
./gradlew assembleDebug
```

### Running the Card Pre-Init Bridge
Ensure an ACR1581U or ACR122U reader is connected:
```cmd
cd web\bridge
run_bridge.bat
```
Then open `web/admin_provisioning_tool.html` in any modern web browser.

---

## 📄 License
This project is licensed under the MIT License.
