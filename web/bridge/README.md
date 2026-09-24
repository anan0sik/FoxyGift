# FoxyGift ACR1581U SmartCard PC/SC Bridge

This directory contains the desktop PC/SC bridge server that connects **`web/admin_provisioning_tool.html`** with physical USB smart card readers, specifically the **ACS ACR1581U DualBoost II** (as well as ACR122U and ACR1252U).

---

## Why is a Bridge Needed?

Standard web browsers (Chrome, Edge, Firefox) sandbox direct access to USB CCID / PC/SC smart card readers for security reasons (`navigator.usb` prohibits claiming USB Class `0x0B` CCID interfaces). 

The FoxyGift SmartCard Bridge runs a local, lightweight HTTP service on `http://127.0.0.1:8989` that interfaces directly with Windows Smart Card subsystem (`WinSCard.dll` / `javax.smartcardio`), allowing the web provisioning tool to:
1. Detect reader attachment and card presence in real time.
2. Read the factory immutable 7-byte UID.
3. Automatically determine chip model (NTAG213, NTAG215, NTAG216) from Capability Container (CC).
4. Write FoxyGift memory structures (FOXY magic, merchant hash, nominal/balance/expiry, digital HMAC signature).
5. Program hardware password protection (PWD, PACK, AUTH0=0x04, PROT=1).
6. Perform verification read and password authentication (`PWD_AUTH`).

---

## How to Run

### Windows (Recommended)
Simply double-click:
```cmd
run_bridge.bat
```
This automatically finds Java (including Android Studio's bundled JBR runtime) and starts the bridge server.

### PowerShell Fallback
If Java is not present, you can run:
```powershell
powershell -ExecutionPolicy Bypass -File foxygift_bridge.ps1
```

---

## Conveyor (Batch) Mode Workflow

1. Open `web/admin_provisioning_tool.html` in your browser.
2. Navigate to the **💳 Card Pre-Init (ACR1581U)** tab.
3. Verify the status pill shows **🟢 Bridge Connected** with your `ACS ACR1581 1S Dual Reader PICC 0` reader.
4. Select the target **Merchant**.
5. Click **⚡ Start Auto-Conveyor (Авто-конвейер)**.
6. Simply tap blank NTAG cards one by one on the reader surface:
   - Each card is programmed and locked in ~100–150ms.
   - An audio chime confirms successful programming.
   - Lift the card; the reader immediately rearms for the next card.
