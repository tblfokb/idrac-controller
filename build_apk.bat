@echo off
setlocal EnableDelayedExpansion

set PROJECT_DIR=C:\Users\huc_o\AndroidStudioProjects\iDracController
set BUILD_TOOLS=C:\Android\build-tools\35.0.0
set PLATFORM=C:\Android\platforms\android-35
set LIBS=%PROJECT_DIR%\app\libs
set SRC=%PROJECT_DIR%\app\src\main
set OUT=%PROJECT_DIR%\out

set AAPT=%BUILD_TOOLS%\aapt.exe
set JAVAC=C:\Program Files\Java\jdk-26.0.1\bin\javac.exe
set APKSIGNER=%BUILD_TOOLS%\apksigner.bat

echo ==========================================
echo   iDRAC Controller - Manual APK Build
echo ==========================================

REM Step 1: Generate R.java
echo.
echo [Step 1/5] Generating R.java...
if not exist "%OUT%\gen" mkdir "%OUT%\gen"
"%AAPT%" p -f -m -J "%OUT%\gen" -S "%SRC%\res" -I "%PLATFORM%\android.jar" -M "%SRC%\AndroidManifest.xml"
if errorlevel 1 goto :error
echo R.java generated successfully!

REM Step 2: Compile Java sources
echo.
echo [Step 2/5] Compiling Java sources...
if not exist "%OUT%\classes" mkdir "%OUT%\classes"

REM Build classpath
set CP="%PLATFORM%\android.jar";"%LIBS%\gson-2.10.1.jar";"%LIBS%\jsch-0.1.55.jar";"%LIBS%\okhttp-4.12.0.jar";"%LIBS%\okio-3.6.0.jar"

REM Find all Java source files
set SRC_FILES=
for /r "%SRC%\java" %%f in (*.java) do call :addfile "%%f"
for /r "%OUT%\gen" %%f in (*.java) do call :addfile "%%f"

echo Compiling !SRC_FILES!
"%JAVAC%" -d "%OUT%\classes" -cp %CP% !SRC_FILES!
if errorlevel 1 goto :error
echo Java compilation successful!

REM Step 3: Convert to DEX
echo.
echo [Step 3/5] Converting to DEX format...
if not exist "%OUT%\dex" mkdir "%OUT%\dex"
cd "%OUT%\classes"
call "%BUILD_TOOLS%\d8.bat" --output "%OUT%\dex\classes.dex" *.class
if errorlevel 1 goto :error
echo DEX conversion successful!

REM Step 4: Package APK
echo.
echo [Step 4/5] Packaging APK...
if not exist "%OUT%\apk" mkdir "%OUT%\apk"

REM Create APK with resources
"%AAPT%" package -f -F "%OUT%\apk\app-debug-unsigned.apk" -M "%SRC%\AndroidManifest.xml" -S "%SRC%\res" -I "%PLATFORM%\android.jar"
if errorlevel 1 goto :error

REM Add classes.dex to APK
"%AAPT%" add "%OUT%\apk\app-debug-unsigned.apk" "%OUT%\dex\classes.dex"
if errorlevel 1 goto :error
echo APK packaged successfully!

REM Step 5: Sign APK
echo.
echo [Step 5/5] Signing APK...
set KEYSTORE=%PROJECT_DIR%\debug.keystore

REM Create debug keystore if not exists
if not exist "%KEYSTORE%" (
    echo Creating debug keystore...
    "C:\Program Files\Java\jdk-26.0.1\bin\keytool.exe" -genkeypair -v -keystore "%KEYSTORE%" -storepass android -keypass android -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
)

set APK_SIGNED=%OUT%\apk\app-debug.apk
call "%APKSIGNER%" sign --ks "%KEYSTORE%" --ks-pass pass:android --out "%APK_SIGNED%" "%OUT%\apk\app-debug-unsigned.apk"
if errorlevel 1 goto :error

echo.
echo ==========================================
echo   BUILD SUCCESSFUL!
echo ==========================================
echo APK location: %APK_SIGNED%
dir "%APK_SIGNED%" | find "app-debug.apk"
echo.
echo Install with: adb install "%APK_SIGNED%"
goto :end

:error
echo.
echo ERROR: Build failed!
exit /b 1

:addfile
set SRC_FILES=!SRC_FILES! %1
goto :eof

:end
endlocal
