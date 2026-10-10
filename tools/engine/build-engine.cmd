@echo off
rem Builds the embedded tunnel engine: ics-openvpn v0.7.58 (GPLv2), skeleton + ovpn2 flavour, as an AAR,
rem and copies it to android\app\libs\. Needs: git, Python 3, JDK 17+, Android SDK with NDK 27.1 + CMake 3.22.
rem   tools\engine\build-engine.cmd [work-folder]      (default E:\iovx — keep it short; Windows path limits)
setlocal
set REPO=%~dp0..\..
set WORK=%~1
if "%WORK%"=="" set WORK=E:\iovx
if "%ANDROID_HOME%"=="" set ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk
if not exist "%WORK%" mkdir "%WORK%"
cd /d "%WORK%"
if not exist ics git -c core.longpaths=true clone --depth 1 --branch v0.7.58 --recurse-submodules --shallow-submodules https://github.com/schwabe/ics-openvpn.git ics || exit /b 1
if not exist venv\Scripts\swig.exe (python -m venv venv && venv\Scripts\python.exe -m pip install -q swig) || exit /b 1
cd ics
git diff --quiet -- main/build.gradle.kts && python "%~dp0patch_ics.py" "%CD%"
> local.properties echo sdk.dir=%ANDROID_HOME:\=\\%
set PATH=%WORK%\venv\Scripts;%PATH%
set SWIG=%WORK%\venv\Scripts\swig.exe
call gradlew.bat :main:assembleSkeletonOvpn2Release --console=plain || exit /b 1
copy /y main\build\outputs\aar\main-skeleton-ovpn2-release.aar "%REPO%\android\app\libs\ics-openvpn-0.7.58-skeleton-ovpn2.aar" || exit /b 1
echo ENGINE_OK
