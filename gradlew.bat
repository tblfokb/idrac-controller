@echo off
set DIR=%~dp0
set DISTRIBUTION_URL=https://mirrors.cloud.tencent.com/gradle/gradle-8.9-bin.zip
set WRAPPER_JAR=%DIR%gradle\wrapper\gradle-wrapper.jar
set PROPERTIES=%DIR%gradle\wrapper\gradle-wrapper.properties

if not exist "%WRAPPER_JAR%" (
    echo Downloading Gradle Wrapper...
    powershell -Command "& { [Net.ServicePointManager]::SecurityProtocol = 'Tls12'; Invoke-WebRequest -Uri 'https://mirrors.cloud.tencent.com/gradle/gradle-8.9-wrapper.jar' -OutFile '%WRAPPER_JAR%' }"
)

if not exist "%WRAPPER_JAR%" (
    echo ERROR: Could not download gradle-wrapper.jar
    echo Please download it manually or open this project in Android Studio.
    exit /b 1
)

set GRADLE_USER_HOME=%USERPROFILE%\.gradle
set JAVA_HOME=C:\Program Files\Java\jdk-26.0.1

"%JAVA_HOME%\bin\java.exe" -classpath "%WRAPPER_JAR%" org.gradle.wrapper.GradleWrapperMain %*
