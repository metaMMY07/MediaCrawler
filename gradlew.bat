@rem
@rem Copyright 2015 the original author or authors.
@rem
@rem Licensed under the Apache License, Version 2.0 (the "License");
@rem you may not use this file except in compliance with the License.
@rem You may obtain a copy of the License at
@rem
@rem      https://www.apache.org/licenses/LICENSE-2.0
@rem
@rem Unless required by applicable law or agreed to in writing, software
@rem distributed under the License is distributed on an "AS IS" BASIS,
@rem WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
@rem See the License for the specific language governing permissions and
@rem limitations under the License.
@rem
@rem SPDX-License-Identifier: Apache-2.0
@rem

@if "%DEBUG%"=="" @echo off
@rem ##########################################################################
@rem
@rem  Gradle startup script for Windows
@rem
@rem ##########################################################################

@rem Set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" setlocal

set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
@rem This is normally unused
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%

@rem Resolve any "." and ".." in APP_HOME to make it shorter.
for %%i in ("%APP_HOME%") do set APP_HOME=%%~fi

@rem Add default JVM options here. You can also use JAVA_OPTS and GRADLE_OPTS to pass JVM options to this script.
set DEFAULT_JVM_OPTS="-Xmx64m" "-Xms64m"

@rem Find java.exe
if defined JAVA_HOME goto findJavaFromJavaHome

set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if %ERRORLEVEL% equ 0 goto execute

echo. 1>&2
echo ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH. 1>&2
echo. 1>&2
echo Please set the JAVA_HOME variable in your environment to match the 1>&2
echo location of your Java installation. 1>&2

goto fail

:findJavaFromJavaHome
set JAVA_HOME=%JAVA_HOME:"=%
set JAVA_EXE=%JAVA_HOME%/bin/java.exe

if exist "%JAVA_EXE%" goto execute

echo. 1>&2
echo ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME% 1>&2
echo. 1>&2
echo Please set the JAVA_HOME variable in your environment to match the 1>&2
echo location of your Java installation. 1>&2

goto fail

:execute
@rem Setup the command line

@rem ---------------------------------------------------------------------------
@rem LOCAL PATCH for this project (rationale in docs/build-setup.md).
@rem This checkout lives under a path containing characters outside the active
@rem OEM code page. cmd.exe corrupts such absolute paths while it assembles the
@rem java.exe command line, so an absolute -classpath makes the wrapper abort
@rem with "ClassNotFoundException: org.gradle.wrapper.GradleWrapperMain".
@rem Fix: cd into the project dir and pass an ASCII-only relative classpath.
@rem ---------------------------------------------------------------------------
pushd "%~dp0." 2>nul
if errorlevel 1 (
  echo ERROR: cannot enter project directory "%APP_HOME%" 1>&2
  goto fail
)
set CLASSPATH=gradle\wrapper\gradle-wrapper.jar

@rem Gradle state lives durably in <project>\.gradle-user-home, but Gradle is pointed at
@rem an ASCII-only directory *junction* to it. The daemon is forked with
@rem "-javaagent:<GRADLE_USER_HOME>\..." on its command line, and the JDK decodes
@rem command-line arguments through the ANSI code page -- this project's non-ASCII path
@rem would arrive as "????" and the daemon would die before starting. A junction gives an
@rem ASCII path to the same files: nothing is copied, nothing leaves the project.
@rem (8.3 short names do NOT work: the JVM rejects a short path in -javaagent with
@rem  "Unexpected error (103) returned by AddToSystemClassLoaderSearch".)
set "APP_HOME_NB=%APP_HOME%"
if "%APP_HOME_NB:~-1%"=="\" set "APP_HOME_NB=%APP_HOME_NB:~0,-1%"
set "GRADLE_HOME_STORE=%APP_HOME_NB%\.gradle-user-home"
set "GRADLE_HOME_LINK=%TEMP%\mediasearch-gradle-home"
if not exist "%GRADLE_HOME_STORE%\" mkdir "%GRADLE_HOME_STORE%" >nul 2>&1
if not exist "%GRADLE_HOME_LINK%\" mklink /J "%GRADLE_HOME_LINK%" "%GRADLE_HOME_STORE%" >nul 2>&1
if "%GRADLE_USER_HOME%"=="" if exist "%GRADLE_HOME_LINK%\" set "GRADLE_USER_HOME=%GRADLE_HOME_LINK%"
if "%GRADLE_USER_HOME%"=="" set "GRADLE_USER_HOME=%GRADLE_HOME_STORE%"

@rem The JDK decodes the environment block and the working directory through
@rem sun.jnu.encoding, which on this machine is Cp1252. Non-ASCII paths (this project's)
@rem would then decode as "????". Forcing UTF-8 keeps absolute paths intact.
@rem NOTE: command-line ARGUMENTS cannot be fixed this way, which is exactly why the
@rem Gradle daemon is disabled in gradle.properties -- the daemon is forked with
@rem "-javaagent:<path>" through argv and would still see "????".
@rem The build therefore runs in this launcher JVM, so it also carries the heap settings.
set "DEFAULT_JVM_OPTS=%DEFAULT_JVM_OPTS% -Xmx3072m -XX:MaxMetaspaceSize=1024m -Dfile.encoding=UTF-8 -Dsun.jnu.encoding=UTF-8"

@rem Execute Gradle
"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% "-Dorg.gradle.appname=%APP_BASE_NAME%" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
set "DSH_WRAPPER_EXIT=%ERRORLEVEL%"
popd
exit /b %DSH_WRAPPER_EXIT%

:end
@rem End local scope for the variables with windows NT shell
if %ERRORLEVEL% equ 0 goto mainEnd

:fail
rem Set variable GRADLE_EXIT_CONSOLE if you need the _script_ return code instead of
rem the _cmd.exe /c_ return code!
set EXIT_CODE=%ERRORLEVEL%
if %EXIT_CODE% equ 0 set EXIT_CODE=1
if not ""=="%GRADLE_EXIT_CONSOLE%" exit %EXIT_CODE%
exit /b %EXIT_CODE%

:mainEnd
if "%OS%"=="Windows_NT" endlocal

:omega
