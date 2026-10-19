@echo off
setlocal
set DIR=%~dp0
if "%JAVA_HOME%"=="" (
  set JAVA_CMD=java
) else (
  set JAVA_CMD=%JAVA_HOME%\bin\java.exe
)
"%JAVA_CMD%" -classpath "%DIR%gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
