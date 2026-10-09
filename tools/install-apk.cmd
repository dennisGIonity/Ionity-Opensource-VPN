@echo off
rem Installs the newest debug APK on the phone connected over adb (USB or wireless debugging).
rem   tools\install-apk.cmd            -> installs android\app\build\outputs\apk\debug\app-debug.apk
rem   tools\install-apk.cmd path.apk   -> installs that file
setlocal
set ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe
if not exist "%ADB%" set ADB=adb
set APK=%~1
if "%APK%"=="" set APK=%~dp0..\android\app\build\outputs\apk\debug\app-debug.apk
"%ADB%" devices -l
"%ADB%" install -r "%APK%" || (echo Install failed. Is the phone listed above and authorised? & exit /b 1)
"%ADB%" shell monkey -p today.ionity.opensourcevpn -c android.intent.category.LAUNCHER 1 >nul
echo Installed and launched. Live logs:  "%ADB%" logcat -s Capacitor:* chromium:*
