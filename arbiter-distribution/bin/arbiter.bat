@echo off
rem Arbiter CLI launcher. Requires Java 17 or newer.
setlocal EnableDelayedExpansion
set "SCRIPT_DIR=%~dp0"
for %%I in ("%SCRIPT_DIR%.") do set "ARBITER_SCRIPT_DIR=%%~fI"

if defined ARBITER_HOME (
  for %%I in ("%ARBITER_HOME%") do set "ARBITER_HOME=%%~fI"
) else (
  for %%I in ("%ARBITER_SCRIPT_DIR%\..") do set "ARBITER_HOME=%%~fI"
)
if not defined ARBITER_HOME (
  echo arbiter: unable to resolve distribution root 1>&2
  exit /b 2
)
if not exist "%ARBITER_HOME%\lib" (
  echo arbiter: invalid distribution root ^(lib not found^): "%ARBITER_HOME%" 1>&2
  exit /b 2
)

if defined ARBITER_MAIN_JAR_OVERRIDE (
  set "ARBITER_MAIN_JAR=%ARBITER_MAIN_JAR_OVERRIDE%"
) else (
  set "ARBITER_MAIN_JAR=%ARBITER_HOME%\lib\cli\arbiter-cli-launcher.jar"
)
for %%I in ("%ARBITER_MAIN_JAR%") do set "ARBITER_MAIN_JAR=%%~fI"
if not exist "%ARBITER_MAIN_JAR%" (
  echo arbiter: CLI JAR not found: "%ARBITER_MAIN_JAR%" 1>&2
  exit /b 2
)
if not exist "%ARBITER_HOME%\lib\arbiter-api.jar" (
  echo arbiter: required runtime JAR not found: "%ARBITER_HOME%\lib\arbiter-api.jar" 1>&2
  exit /b 2
)

if defined ARBITER_JAVA (
  set "ARBITER_JAVA_EXE=%ARBITER_JAVA%"
) else if defined JAVA_HOME (
  set "ARBITER_JAVA_EXE=%JAVA_HOME%\bin\java.exe"
) else (
  set "ARBITER_JAVA_EXE=java"
)

"%ARBITER_JAVA_EXE%" -version >nul 2>nul
if errorlevel 1 (
  echo arbiter: Java executable not found or not runnable: "%ARBITER_JAVA_EXE%" 1>&2
  exit /b 2
)

rem JVM options come from conf\cli-jvm.options and are only applied on Java 17+;
rem older JVMs reject options such as --enable-native-access.
set "ARBITER_JVM_FILE_OPTS="
if not exist "%SCRIPT_DIR%_arbiter-common.bat" (
  echo arbiter: missing launcher support file: "%SCRIPT_DIR%_arbiter-common.bat" 1>&2
  exit /b 2
)
call "%SCRIPT_DIR%_arbiter-common.bat" :java_major
if not errorlevel 1 (
  if defined ARBITER_JAVA_MAJOR (
    if !ARBITER_JAVA_MAJOR! GEQ 17 (
      call "%SCRIPT_DIR%_arbiter-common.bat" :read_jvm_options "%ARBITER_HOME%\conf\cli-jvm.options"
    )
  )
)

set "ARBITER_CLASSPATH=%ARBITER_MAIN_JAR%"
for %%J in ("%ARBITER_HOME%\lib\cli\*.jar") do (
  if /i not "%%~fJ"=="%ARBITER_MAIN_JAR%" set "ARBITER_CLASSPATH=!ARBITER_CLASSPATH!;%%~fJ"
)
set "ARBITER_CLASSPATH=%ARBITER_CLASSPATH%;%ARBITER_HOME%\lib\arbiter-api.jar"
for %%J in ("%ARBITER_HOME%\lib\3rd\jackson-*.jar") do (
  if exist "%%~fJ" set "ARBITER_CLASSPATH=!ARBITER_CLASSPATH!;%%~fJ"
)
if exist "%ARBITER_HOME%\tools" set "ARBITER_CLASSPATH=%ARBITER_CLASSPATH%;%ARBITER_HOME%\tools\*"
if exist "%ARBITER_HOME%\plugins" set "ARBITER_CLASSPATH=%ARBITER_CLASSPATH%;%ARBITER_HOME%\plugins\*"

if defined ARBITER_DEBUG_LAUNCH (
  echo ARBITER_HOME=%ARBITER_HOME%
  echo ARBITER_MAIN_JAR=%ARBITER_MAIN_JAR%
  echo ARBITER_CLASSPATH=%ARBITER_CLASSPATH%
  echo ARBITER_JVM_FILE_OPTS=%ARBITER_JVM_FILE_OPTS%
)

"%ARBITER_JAVA_EXE%" %ARBITER_JVM_FILE_OPTS% %ARBITER_JAVA_OPTS% -Darbiter.home="%ARBITER_HOME%" -cp "%ARBITER_CLASSPATH%" io.github.arbiter.cli.launch.CLIMainDelegateLauncher %*
set "ARBITER_EXIT=%ERRORLEVEL%"
exit /b %ARBITER_EXIT%
