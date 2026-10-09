@echo off
rem One-time: create (or use the existing, empty) github.com/dennisGIonity/Ionity-Opensource-VPN, push main,
rem and publish release v0.2.0 with the APK + HTML attached.
rem Run from anywhere:  tools\publish-to-github.cmd [folder-with-release-files]
rem Needs: GitHub CLI (installed at C:\Program Files\GitHub CLI). It will ask you to log in once.
setlocal
cd /d "%~dp0.."
set REL=%~1
if "%REL%"=="" set REL=%~dp0..\..\..\release
set GH="C:\Program Files\GitHub CLI\gh.exe"

rem "workflow" scope is needed because the repo carries .github/workflows/ci.yml;
rem without it GitHub refuses the whole push.
%GH% auth status >nul 2>&1 || %GH% auth login --hostname github.com --git-protocol https --web --scopes workflow || exit /b 1
%GH% auth refresh --hostname github.com --scopes workflow
%GH% auth setup-git

rem Identity is set in this repo's own config, never per-commit.
git config user.name >nul || git config user.name "DennisIonity"
git config user.email >nul || git config user.email "dennis@ionitynetwork.onmicrosoft.com"

%GH% repo view dennisGIonity/Ionity-Opensource-VPN >nul 2>&1
if errorlevel 1 (
  %GH% repo create dennisGIonity/Ionity-Opensource-VPN --public --source . --remote origin --push ^
    --description "Ionity's Opensource VPN - per-device VPN regions for a Gate^Flame box. Android app + single-file web app. CC BY-NC-SA 4.0" ^
    --homepage https://www.ionity.today || exit /b 1
) else (
  git remote get-url origin >nul 2>&1 || git remote add origin https://github.com/dennisGIonity/Ionity-Opensource-VPN.git
  git push -u origin main || exit /b 1
)

if exist "%REL%\ionity-opensource-vpn-0.2.0-debug.apk" (
  %GH% release create v0.2.0 "%REL%\ionity-opensource-vpn-0.2.0-debug.apk" "%REL%\ionity-opensource-vpn-0.2.0.html" ^
    --repo dennisGIonity/Ionity-Opensource-VPN --title "v0.2.0" --notes-file CHANGELOG.md
) else (
  echo Release files not found in %REL% - skipped the GitHub release.
)
echo.
echo Done: https://github.com/dennisGIonity/Ionity-Opensource-VPN
git log --branches --not --remotes --oneline
