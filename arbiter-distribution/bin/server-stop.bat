@echo off
rem Arbiter stop launcher: Java 8-compatible; does not load Java 17 server classes.
setlocal
set "SCRIPT_DIR=%~dp0"
for %%I in ("%SCRIPT_DIR%.") do set "ARBITER_SCRIPT_DIR=%%~fI"

if defined ARBITER_HOME (
  for %%I in ("%ARBITER_HOME%") do set "ARBITER_HOME=%%~fI"
) else (
  for %%I in ("%ARBITER_SCRIPT_DIR%\..") do set "ARBITER_HOME=%%~fI"
)
if not defined ARBITER_HOME (
  echo server-stop: unable to resolve distribution root 1>&2
  exit /b 2
)
if not exist "%ARBITER_HOME%\lib" (
  echo server-stop: invalid distribution root ^(lib not found^): "%ARBITER_HOME%" 1>&2
  exit /b 2
)
if not exist "%ARBITER_HOME%\lib\bootstrap\arbiter-control.jar" (
  echo server-stop: control JAR not found: "%ARBITER_HOME%\lib\bootstrap\arbiter-control.jar" 1>&2
  exit /b 2
)

if defined ARBITER_SERVER_JAVA (
  set "ARBITER_JAVA_EXE=%ARBITER_SERVER_JAVA%"
) else if defined JAVA_HOME (
  set "ARBITER_JAVA_EXE=%JAVA_HOME%\bin\java.exe"
) else (
  set "ARBITER_JAVA_EXE=java"
)

"%ARBITER_JAVA_EXE%" -version >nul 2>nul
if errorlevel 1 (
  echo server-stop: Java executable not found or not runnable: "%ARBITER_JAVA_EXE%" 1>&2
  exit /b 2
)

"%ARBITER_JAVA_EXE%" -Darbiter.home="%ARBITER_HOME%" -cp "%ARBITER_HOME%\lib\bootstrap\arbiter-control.jar" io.github.arbiter.server.control.ServerControl --stop
exit /b %ERRORLEVEL%
