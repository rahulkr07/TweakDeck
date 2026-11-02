@echo off
REM Install TweakDeck and unlock full settings control. Run from the project folder.
REM Usage: scripts\setup.bat [path-to-apk]
set APK=%1
if "%APK%"=="" set APK=app\build\outputs\apk\debug\app-debug.apk
set PKG=com.tweakdeck.app

adb devices
adb install -r "%APK%"
if errorlevel 1 goto fail

echo Granting WRITE_SECURE_SETTINGS...
adb shell pm grant %PKG% android.permission.WRITE_SECURE_SETTINGS
if errorlevel 1 (
  echo.
  echo Grant failed. On Realme UI open Settings ^> Additional settings ^> Developer options and turn on:
  echo   - USB debugging ^(Security settings^)
  echo   - Disable permission monitoring
  echo then run this script again.
  goto fail
)
echo Granting WRITE_SETTINGS...
adb shell appops set %PKG% WRITE_SETTINGS allow
echo Done. Open TweakDeck on the phone.
goto end
:fail
echo Setup did not finish.
:end
