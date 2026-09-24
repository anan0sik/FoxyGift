@echo off
title FoxyGift ACR1581U SmartCard Bridge
cls
echo ========================================================
echo     FoxyGift ACR1581U SmartCard PC/SC Bridge Server     
echo ========================================================
echo.

set JAVA_EXE=
if defined JAVA_HOME (
    if exist "%JAVA_HOME%\bin\java.exe" set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
)
if not defined JAVA_EXE (
    if exist "C:\Program Files\Android\Android Studio\jbr\bin\java.exe" set "JAVA_EXE=C:\Program Files\Android\Android Studio\jbr\bin\java.exe"
)
if not defined JAVA_EXE (
    where java >nul 2>nul
    if %errorlevel% equ 0 set "JAVA_EXE=java"
)

if not defined JAVA_EXE (
    echo [ERROR] Java was not found!
    echo Please install Java or Android Studio to run the bridge.
    pause
    exit /b 1
)

echo [INFO] Using Java: "%JAVA_EXE%"
echo [INFO] Starting ACR1581U Bridge on http://127.0.0.1:8989 ...
echo [INFO] Leave this window open while pre-initializing cards in the browser.
echo.

"%JAVA_EXE%" --add-opens java.smartcardio/sun.security.smartcardio=ALL-UNNAMED -Dfile.encoding=UTF-8 "%~dp0FoxyGiftBridge.java"

if %errorlevel% neq 0 (
    echo.
    echo [BRIDGE STOPPED]
    pause
)
