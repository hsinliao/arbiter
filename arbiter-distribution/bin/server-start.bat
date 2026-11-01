@echo off
rem Arbiter server launcher: background start with a fixed run directory.
setlocal EnableDelayedExpansion
set "SCRIPT_DIR=%~dp0"
for %%I in ("%SCRIPT_DIR%.") do set "ARBITER_SCRIPT_DIR=%%~fI"

if defined ARBITER_HOME (
  for %%I in ("%ARBITER_HOME%") do set "ARBITER_HOME=%%~fI"
) else (
  for %%I in ("%ARBITER_SCRIPT_DIR%\..") do set "ARBITER_HOME=%%~fI"
)
if not defined ARBITER_HOME (
  echo server-start: unable to resolve distribution root 1>&2
  exit /b 2
)
if not exist "%ARBITER_HOME%\lib" (
  echo server-start: invalid distribution root ^(lib not found^): "%ARBITER_HOME%" 1>&2
  exit /b 2
)

if not exist "%ARBITER_HOME%\lib\bootstrap\arbiter-control.jar" (
  echo server-start: control JAR not found: "%ARBITER_HOME%\lib\bootstrap\arbiter-control.jar" 1>&2
  exit /b 2
)
set "ARBITER_LOG_DIR=%ARBITER_HOME%\logs"
set "ARBITER_LOG=%ARBITER_LOG_DIR%\server.log"
if not exist "%ARBITER_LOG_DIR%" mkdir "%ARBITER_LOG_DIR%"

if defined ARBITER_SERVER_JAVA (
  set "ARBITER_JAVA_EXE=%ARBITER_SERVER_JAVA%"
) else if defined JAVA_HOME (
  set "ARBITER_JAVA_EXE=%JAVA_HOME%\bin\java.exe"
) else (
  set "ARBITER_JAVA_EXE=java"
)

"%ARBITER_JAVA_EXE%" -version >nul 2>nul
if errorlevel 1 (
  echo server-start: Java executable not found or not runnable: "%ARBITER_JAVA_EXE%" 1>&2
  exit /b 2
)

if not exist "%SCRIPT_DIR%_arbiter-common.bat" (
  echo server-start: missing launcher support file: "%SCRIPT_DIR%_arbiter-common.bat" 1>&2
  exit /b 2
)
call "%SCRIPT_DIR%_arbiter-common.bat" :java_major
if errorlevel 1 (
  echo server-start: cannot determine the Java version of "%ARBITER_JAVA_EXE%" 1>&2
  exit /b 2
)
if %ARBITER_JAVA_MAJOR% LSS 17 (
  echo server-start: Java 17 or newer is required ^(found %ARBITER_JAVA_SPEC%^). 1>&2
  exit /b 2
)

set "ARBITER_SERVER_JAR=%ARBITER_HOME%\lib\bootstrap\arbiter-console.jar"
set "ARBITER_CONTROL_JAR=%ARBITER_HOME%\lib\bootstrap\arbiter-control.jar"
set "ARBITER_CONTROL_CLASSPATH=%ARBITER_CONTROL_JAR%"
set "ARBITER_CLASSPATH=%ARBITER_CONTROL_JAR%;%ARBITER_HOME%\lib\bootstrap\arbiter-console.jar;%ARBITER_HOME%\lib\*;%ARBITER_HOME%\lib\3rd\*"
if exist "%ARBITER_HOME%\tools" set "ARBITER_CLASSPATH=%ARBITER_CLASSPATH%;%ARBITER_HOME%\tools\*;%ARBITER_HOME%\tools"
if exist "%ARBITER_HOME%\plugins" set "ARBITER_CLASSPATH=%ARBITER_CLASSPATH%;%ARBITER_HOME%\plugins\*;%ARBITER_HOME%\plugins"

rem --debug is consumed by the launcher; control flags use ServerControl and normal startup uses ArbiterServerApplication.
set "ARBITER_SERVER_ARGS="
set "ARBITER_DEBUG="
set "ARBITER_DEBUG_OPTS="
set "ARBITER_DEBUG_ADDRESS=127.0.0.1:5005"
for %%A in (%*) do (
  set "ARBITER_ARG=%%~A"
  if /i "!ARBITER_ARG!"=="--debug" (
    set "ARBITER_DEBUG=1"
  ) else (
    if /i "!ARBITER_ARG:~0,8!"=="--debug=" (
      set "ARBITER_DEBUG=1"
      set "ARBITER_DEBUG_ADDRESS=!ARBITER_ARG:~8!"
    ) else (
      set "ARBITER_SERVER_ARGS=!ARBITER_SERVER_ARGS! %%~A"
    )
  )
)
if defined ARBITER_DEBUG (
  if "!ARBITER_DEBUG_ADDRESS!"=="!ARBITER_DEBUG_ADDRESS::=!" (
    echo server-start: --debug requires ADDRESS:PORT 1>&2
    exit /b 2
  )
  set "ARBITER_DEBUG_OPTS=-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=!ARBITER_DEBUG_ADDRESS!"
)

rem --help and --version are honored anywhere in the argument list.
set "ARBITER_FOREGROUND="
if defined ARBITER_SERVER_ARGS (
  for %%A in (%ARBITER_SERVER_ARGS%) do (
    if /i "%%~A"=="--help" set "ARBITER_FOREGROUND=1"
    if /i "%%~A"=="-h" set "ARBITER_FOREGROUND=1"
    if /i "%%~A"=="--version" set "ARBITER_FOREGROUND=1"
    if /i "%%~A"=="-V" set "ARBITER_FOREGROUND=1"
    if /i "%%~A"=="--check" set "ARBITER_FOREGROUND=1"
  )
)
if defined ARBITER_FOREGROUND goto :run_server_foreground

if not exist "%ARBITER_HOME%\lib\bootstrap\arbiter-console.jar" (
  echo server-start: server JAR not found: "%ARBITER_HOME%\lib\bootstrap\arbiter-console.jar" 1>&2
  exit /b 2
)
if not exist "%ARBITER_HOME%\lib\arbiter-api.jar" (
  echo server-start: required runtime JAR not found: "%ARBITER_HOME%\lib\arbiter-api.jar" 1>&2
  exit /b 2
)

set "ARBITER_START_TIMEOUT=%ARBITER_SERVER_START_TIMEOUT%"
if not defined ARBITER_START_TIMEOUT set "ARBITER_START_TIMEOUT=10"
set /a ARBITER_START_TIMEOUT_NUM=%ARBITER_START_TIMEOUT% 2>nul
if not "%ARBITER_START_TIMEOUT_NUM%"=="%ARBITER_START_TIMEOUT%" (
  echo server-start: ARBITER_SERVER_START_TIMEOUT must be a positive integer 1>&2
  exit /b 2
)
if %ARBITER_START_TIMEOUT% LSS 1 (
  echo server-start: ARBITER_SERVER_START_TIMEOUT must be a positive integer 1>&2
  exit /b 2
)


"%ARBITER_JAVA_EXE%" -Darbiter.home="%ARBITER_HOME%" -cp "%ARBITER_CONTROL_CLASSPATH%" io.github.arbiter.server.control.ServerControl --check >nul 2>&1
if not errorlevel 1 (
  echo server-start: already running 1>&2
  exit /b 0
)

rem Keep exactly one previous log so repeated restarts cannot grow it forever.
if exist "%ARBITER_LOG%" move /y "%ARBITER_LOG%" "%ARBITER_LOG%.1" >nul 2>&1

start "arbiter-server" /b "%ARBITER_JAVA_EXE%" %ARBITER_DEBUG_OPTS% %ARBITER_SERVER_JAVA_OPTS% -Darbiter.home="%ARBITER_HOME%" -cp "%ARBITER_CLASSPATH%" io.github.arbiter.server.ArbiterServerApplication %ARBITER_SERVER_ARGS% 1>>"%ARBITER_LOG%" 2>&1

set "ARBITER_STARTED="
set "ARBITER_SERVER_PID="
for /L %%I in (1,1,%ARBITER_START_TIMEOUT%) do (
  if not defined ARBITER_STARTED (
    "%ARBITER_JAVA_EXE%" -Darbiter.home="%ARBITER_HOME%" -cp "%ARBITER_CONTROL_CLASSPATH%" io.github.arbiter.server.control.ServerControl --check >nul 2>&1
    if not errorlevel 1 (
      set "ARBITER_SERVER_PID="
      if exist "%ARBITER_HOME%\run\.server.pid" set /p "ARBITER_SERVER_PID="<"%ARBITER_HOME%\run\.server.pid"
      if defined ARBITER_SERVER_PID set "ARBITER_STARTED=1"
    )
    if not defined ARBITER_STARTED ping -n 2 127.0.0.1 >nul 2>&1
  )
)
if not defined ARBITER_STARTED (
  echo server-start: server did not start; see "%ARBITER_LOG%" 1>&2
  exit /b 1
)

if defined ARBITER_SERVER_PID (
  echo arbiter-server started ^(PID %ARBITER_SERVER_PID%^)
) else (
  echo arbiter-server started
)
if defined ARBITER_DEBUG_OPTS (
  echo Debug agent: %ARBITER_DEBUG_ADDRESS% ^(JDWP, suspend=n^)
)
echo Log file: "%ARBITER_LOG%"
exit /b 0

:run_server_foreground
"%ARBITER_JAVA_EXE%" -Darbiter.home="%ARBITER_HOME%" -cp "%ARBITER_CONTROL_CLASSPATH%" io.github.arbiter.server.control.ServerControl %ARBITER_SERVER_ARGS%
exit /b %ERRORLEVEL%
