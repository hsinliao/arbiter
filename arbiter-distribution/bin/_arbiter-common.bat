@echo off
rem Shared batch helpers for the Arbiter launchers; never executed directly.
if "%~1"=="" exit /b 2
goto %~1

:java_major
set "ARBITER_JAVA_SPEC="
rem CALL lets FOR /F capture output from a quoted executable path, including
rem paths that contain spaces.
for /f "tokens=3" %%V in ('call "%ARBITER_JAVA_EXE%" -XshowSettings:properties -version 2^>^&1 ^| findstr /c:"java.specification.version"') do set "ARBITER_JAVA_SPEC=%%V"
if not defined ARBITER_JAVA_SPEC exit /b 1
set "ARBITER_JAVA_MAJOR=%ARBITER_JAVA_SPEC%"
if "%ARBITER_JAVA_MAJOR:~0,2%"=="1." set "ARBITER_JAVA_MAJOR=%ARBITER_JAVA_MAJOR:~2%"
for /f "delims=." %%M in ("%ARBITER_JAVA_MAJOR%") do set "ARBITER_JAVA_MAJOR=%%M"
if not defined ARBITER_JAVA_MAJOR exit /b 1
exit /b 0

:read_jvm_options
set "ARBITER_JVM_FILE_OPTS="
if not exist "%~2" exit /b 0
for /f "usebackq tokens=*" %%L in ("%~2") do (
  set "ARBITER_JVM_LINE=%%L"
  if not "!ARBITER_JVM_LINE:~0,1!"=="#" (
    set "ARBITER_JVM_FILE_OPTS=!ARBITER_JVM_FILE_OPTS! "%%L""
  )
)
exit /b 0
