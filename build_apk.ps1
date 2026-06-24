# Build APK manually using Android SDK tools
$ErrorActionPreference = "Stop"

$projectDir = "C:\Users\huc_o\AndroidStudioProjects\iDracController"
$buildTools = "C:\Android\build-tools\35.0.0"
$platform = "C:\Android\platforms\android-35"
$libs = "$projectDir\app\libs"
$src = "$projectDir\app\src\main"
$out = "$projectDir\out"

$aapt = "$buildTools\aapt.exe"
$javac = "C:\Program Files\Java\jdk-26.0.1\bin\javac.exe"
$d8 = "$buildTools\d8.bat"
$apksigner = "$buildTools\apksigner.bat"

Write-Output "=========================================="
Write-Output "  iDRAC Controller - Manual APK Build"
Write-Output "=========================================="

# Step 1: Generate R.java
Write-Output "`n[Step 1/6] Generating R.java..."
$genDir = "$out\gen"
$resDir = "$src\res"
$manifest = "$src\AndroidManifest.xml"
$androidJar = "$platform\android.jar"

cmd.exe /c "`"$aapt`" p -f -m -J `"$genDir`" -S `"$resDir`" -I `"$androidJar`" -M `"$manifest`"" 2>&1
if ($LASTEXITCODE -ne 0) { Write-Output "ERROR: aapt failed"; exit 1 }
Write-Output "R.java generated successfully!"

# Step 2: Compile Java sources
Write-Output "`n[Step 2/6] Compiling Java sources..."
$classesDir = "$out\classes"
$cp = "`"$androidJar`";`"$libs\gson-2.10.1.jar`";`"$libs\jsch-0.1.55.jar`";`"$libs\okhttp-4.12.0.jar`";`"$libs\okio-3.6.0.jar`""

# Find all Java source files
$srcFiles = Get-ChildItem "$src\java" -Recurse -Filter "*.java" | ForEach-Object { $_.FullName }
$genFiles = Get-ChildItem "$genDir" -Recurse -Filter "*.java" | ForEach-Object { $_.FullName }
$allFiles = $srcFiles + $genFiles

cmd.exe /c "`"$javac`" -d `"$classesDir`" -cp $cp $($allFiles -join ' ')" 2>&1
if ($LASTEXITCODE -ne 0) { Write-Output "ERROR: javac failed"; exit 1 }
Write-Output "Java compilation successful!"

# Step 3: Convert to DEX
Write-Output "`n[Step 3/6] Converting to DEX format..."
$dexDir = "$out\dex"
cmd.exe /c "cd /d `"$classesDir`" && `"$d8`" --output `"$dexDir\classes.dex`" *.class" 2>&1
if ($LASTEXITCODE -ne 0) { Write-Output "ERROR: d8 failed"; exit 1 }
Write-Output "DEX conversion successful!"

# Step 4: Package APK (without resources first - simplified)
Write-Output "`n[Step 4/6] Packaging APK..."
$apkUnaligned = "$out\apk\unaligned.apk"
# Create empty APK structure - this is complex, skip for now
# Actually, let's use aapt to package everything together

# Step 5: We need to package resources and classes.dex together
# This is getting complex - let's use a simpler approach with apksigner directly
Write-Output "`n[Step 5/6] Creating unsigned APK..."
# For simplicity, let's just create a debug APK using the standard approach
# We'll use aapt to create the APK with resources, then add classes.dex

$apkWithResources = "$out\apk\app-debug-unsigned.apk"
cmd.exe /c "`"$aapt`" package -f -F `"$apkWithResources`" -M `"$manifest`" -S `"$resDir`" -I `"$androidJar`" -F `"$apkWithResources`"" 2>&1
if ($LASTEXITCODE -ne 0) { Write-Output "ERROR: aapt package failed"; exit 1 }

# Add classes.dex to APK
Write-Output "Adding classes.dex to APK..."
cmd.exe /c "cd /d `"$dexDir`" && `"$aapt`" add `"$apkWithResources`" classes.dex" 2>&1
if ($LASTEXITCODE -ne 0) { Write-Output "ERROR: aapt add failed"; exit 1 }

# Step 6: Sign APK
Write-Output "`n[Step 6/6] Signing APK..."
# Create debug keystore if not exists
$keystore = "$projectDir\debug.keystore"
if (-not (Test-Path $keystore)) {
    $keytool = "C:\Program Files\Java\jdk-26.0.1\bin\keytool.exe"
    cmd.exe /c "`"$keytool`" -genkeypair -v -keystore `"$keystore`" -storepass android -keypass android -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 -dname `"CN=Android Debug,O=Android,C=US`"" 2>&1
}

$apkSigned = "$out\apk\app-debug.apk"
cmd.exe /c "`"$apksigner`" sign --ks `"$keystore`" --ks-pass pass:android --out `"$apkSigned`" `"$apkWithResources`"" 2>&1
if ($LASTEXITCODE -ne 0) { Write-Output "ERROR: apksigner failed"; exit 1 }

Write-Output "`n=========================================="
Write-Output "  BUILD SUCCESSFUL!"
Write-Output "=========================================="
Write-Output "APK location: $apkSigned"
Write-Output "Size: $((Get-Item $apkSigned).Length / 1KB) KB"
Write-Output "`nInstall with: adb install $apkSigned"
