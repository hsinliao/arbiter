@echo off
setlocal EnableDelayedExpansion
set "ROOT=%~dp0.."
for %%I in ("%ROOT%") do set "ROOT=%%~fI"
cd /d "%ROOT%" || exit /b 1

set "BUILD_ID="
set "FORCE_COMMIT=0"
set "EXTRA="

:parse
if "%~1"=="" goto resolve
if /i "%~1"=="--build-id" (
  set "BUILD_ID=%~2"
  shift
  shift
  goto parse
)
if /i "%~1"=="--commit" (
  set "FORCE_COMMIT=1"
  shift
  goto parse
)
if /i "%~1"=="-h" goto usage
if /i "%~1"=="--help" goto usage
if "%~1"=="--" (
  shift
  set "EXTRA=%*"
  goto resolve
)
echo build: unknown option: %~1 1>&2
exit /b 2

:usage
echo Usage: scripts\build.bat [--build-id ID^|--commit] [-- maven-args]
exit /b 0

:resolve
if defined BUILD_ID goto have_id
if "%FORCE_COMMIT%"=="1" goto commit_id
if defined ARBITER_BUILD_ID (
  set "BUILD_ID=%ARBITER_BUILD_ID%"
  goto have_id
)
if defined BUILD_NUMBER (
  set "BUILD_ID=%BUILD_NUMBER%"
  goto have_id
)
if defined GITHUB_RUN_NUMBER (
  set "BUILD_ID=%GITHUB_RUN_NUMBER%"
  goto have_id
)
if defined CI_PIPELINE_IID (
  set "BUILD_ID=%CI_PIPELINE_IID%"
  goto have_id
)
if defined CIRCLE_BUILD_NUM (
  set "BUILD_ID=%CIRCLE_BUILD_NUM%"
  goto have_id
)

:commit_id
set "COUNT="
for /f "delims=" %%I in ('git rev-list --count HEAD 2^>nul') do set "COUNT=%%I"
if defined COUNT (
  set "BUILD_ID=!COUNT!"
  goto have_id
)

:fallback
for /f "tokens=2 delims=<>" %%I in ('findstr /c:"<build.id>" pom.xml') do set "BUILD_ID=%%I"

:have_id
if not defined BUILD_ID (
  echo build: build.id is missing from pom.xml 1>&2
  exit /b 2
)

for /f "tokens=2 delims=<>" %%I in ('findstr /c:"<version.major>" pom.xml') do set "MAJOR=%%I"
for /f "tokens=2 delims=<>" %%I in ('findstr /c:"<version.minor>" pom.xml') do set "MINOR=%%I"
for /f "tokens=2 delims=<>" %%I in ('findstr /c:"<version.patch>" pom.xml') do set "PATCH=%%I"
if not defined MAJOR set "MAJOR=1"
if not defined MINOR set "MINOR=0"
if not defined PATCH set "PATCH=0"
set "BASE_VERSION=!MAJOR!.!MINOR!.!PATCH!"
set "DISPLAY_VERSION=%BUILD_ID%.%BASE_VERSION%"

set "MVN_CMD=%MVN%"
if not defined MVN_CMD set "MVN_CMD=mvn"
if defined ARBITER_MAVEN_REPO set "MVN_CMD=%MVN_CMD% -Dmaven.repo.local=%ARBITER_MAVEN_REPO%"

echo build: arbiter version %DISPLAY_VERSION%
%MVN_CMD% -Dbuild.id=%BUILD_ID% clean verify %EXTRA%
exit /b %ERRORLEVEL%
