#!/usr/bin/env python3
"""手动打包 APK - iDRAC 控制器（自动读取版本号）"""

import os
import sys
import subprocess
import shutil
import zipfile
import tempfile
import re

# ===== 自动读取版本号 =====
def get_version():
    manifest = os.path.join(PROJECT_DIR, "app", "src", "main", "AndroidManifest.xml")
    with open(manifest, "r", encoding="utf-8") as f:
        content = f.read()
    vc = re.search(r'android:versionCode="(\d+)"', content)
    vn = re.search(r'android:versionName="([^"]+)"', content)
    version_code = vc.group(1) if vc else "0"
    version_name = vn.group(1) if vn else "unknown"
    return version_code, version_name

# ===== 路径配置 =====
PROJECT_DIR = r"C:\Users\huc_o\WorkBuddy\Claw"
BUILD_TOOLS = r"C:\Android\build-tools\35.0.0"
PLATFORM = r"C:\Android\platforms\android-35"
LIBS_DIR = os.path.join(PROJECT_DIR, "app", "libs")
SRC_DIR = os.path.join(PROJECT_DIR, "app", "src", "main")
OUT_DIR = os.path.join(PROJECT_DIR, "out")

AAPT = os.path.join(BUILD_TOOLS, "aapt.exe")
JAVAC = r"C:\Program Files\Java\jdk-26.0.1\bin\javac.exe"
D8 = os.path.join(BUILD_TOOLS, "d8.bat")
APKSIGNER = os.path.join(BUILD_TOOLS, "apksigner.bat")

def run(cmd, check=True, cwd=None):
    print(f">>> {cmd}")
    result = subprocess.run(cmd, shell=True, capture_output=True, cwd=cwd)
    for stream, label in [(result.stdout, "STDOUT"), (result.stderr, "STDERR")]:
        if stream:
            try:
                text = stream.decode("utf-8")
            except:
                text = stream.decode("gbk", errors="ignore")
            for line in text.strip().splitlines():
                if line.strip():
                    print(f"  [{label}] {line.strip()}")
    if check and result.returncode != 0:
        print(f"ERROR: 命令失败，退出码 {result.returncode}")
        sys.exit(1)
    return result

def main():
    print("=" * 50)
    print("  iDRAC 控制器 v1.9.0 - 手动 APK 打包 (移除优雅关机/重启+添加操作确认弹窗)")
    print("=" * 50)

    # 清理并创建输出目录
    if os.path.exists(OUT_DIR):
        shutil.rmtree(OUT_DIR)
    gen_dir = os.path.join(OUT_DIR, "gen")
    classes_dir = os.path.join(OUT_DIR, "classes")
    dex_dir = os.path.join(OUT_DIR, "dex")
    apk_dir = os.path.join(OUT_DIR, "apk")
    for d in [gen_dir, classes_dir, dex_dir, apk_dir]:
        os.makedirs(d, exist_ok=True)

    android_jar = os.path.join(PLATFORM, "android.jar")
    res_dir = os.path.join(SRC_DIR, "res")
    manifest = os.path.join(SRC_DIR, "AndroidManifest.xml")

    # ===== Step 1: 生成 R.java =====
    print("\n[Step 1/6] 生成 R.java ...")
    cmd = f'"{AAPT}" p -f -m -J "{gen_dir}" -S "{res_dir}" -I "{android_jar}" -M "{manifest}"'
    run(cmd)
    r_java_files = []
    for root, dirs, files in os.walk(gen_dir):
        for f in files:
            if f.endswith(".java"):
                r_java_files.append(os.path.join(root, f))
    print(f"  生成了 {len(r_java_files)} 个 R.java 文件")

    # ===== Step 2: 编译 Java =====
    print("\n[Step 2/6] 编译 Java 源码 ...")
    cp_parts = [android_jar]
    if os.path.exists(LIBS_DIR):
        for jar in sorted(os.listdir(LIBS_DIR)):
            if jar.endswith(".jar"):
                cp_parts.append(os.path.join(LIBS_DIR, jar))
    cp = ";".join(cp_parts)

    src_files = []
    java_src_dir = os.path.join(SRC_DIR, "java")
    for root, dirs, files in os.walk(java_src_dir):
        for f in files:
            if f.endswith(".java"):
                src_files.append(os.path.join(root, f))

    all_files = src_files + r_java_files
    file_list_txt = os.path.join(OUT_DIR, "filelist.txt")
    with open(file_list_txt, "w", encoding="utf-8") as f:
        for fn in all_files:
            f.write(fn + "\n")

    cmd = f'"{JAVAC}" -d "{classes_dir}" -cp "{cp}" --release 17 @{file_list_txt}'
    run(cmd)
    print("  Java 编译成功！")

    # ===== Step 3: 转 DEX =====
    print("\n[Step 3/6] 转换为 DEX 格式 ...")
    classes_jar = os.path.join(OUT_DIR, "classes.jar")
    with zipfile.ZipFile(classes_jar, "w", zipfile.ZIP_DEFLATED) as zf:
        for root, dirs, files in os.walk(classes_dir):
            for f in files:
                if f.endswith(".class"):
                    full = os.path.join(root, f)
                    arc = os.path.relpath(full, classes_dir)
                    zf.write(full, arc)
    print(f"  已打包为 {classes_jar}")

    # 把依赖 jar 也加入 d8 一起转 dex
    jar_list = [f'"{classes_jar}"']
    if os.path.exists(LIBS_DIR):
        for jar in sorted(os.listdir(LIBS_DIR)):
            if jar.endswith(".jar"):
                jar_list.append(f'"{os.path.join(LIBS_DIR, jar)}"')
    all_jars = " ".join(jar_list)
    print(f"  输入 jar: {len(jar_list)} 个")

    cmd = f'"{BUILD_TOOLS}\\d8.bat" --output "{dex_dir}" {all_jars}'
    run(cmd)
    print("  DEX 转换成功！")

    # ===== Step 4: 打包 APK (用 zipfile 手动构造) =====
    print("\n[Step 4/6] 打包 APK ...")
    apk_unsigned = os.path.join(apk_dir, "app-debug-unsigned.apk")

    tmp_apk = os.path.join(OUT_DIR, "tmp.apk")
    cmd = f'"{AAPT}" p -f -F "{tmp_apk}" -M "{manifest}" -S "{res_dir}" -I "{android_jar}"'
    run(cmd)

    dex_file = None
    for f in os.listdir(dex_dir):
        if f.endswith(".dex"):
            dex_file = os.path.join(dex_dir, f)
            break
    if not dex_file:
        print("ERROR: 找不到 .dex 文件")
        sys.exit(1)

    print(f"  添加 {dex_file} 到 APK ...")
    with zipfile.ZipFile(tmp_apk, "a", zipfile.ZIP_DEFLATED) as zf:
        zf.write(dex_file, "classes.dex")
    shutil.move(tmp_apk, apk_unsigned)
    print(f"  APK 打包完成: {apk_unsigned}")

    # ===== Step 5: 对齐 APK =====
    print("\n[Step 5/6] 对齐 APK ...")
    apk_aligned = os.path.join(apk_dir, "app-debug-aligned.apk")
    zipalign = os.path.join(BUILD_TOOLS, "zipalign.exe")
    if os.path.exists(zipalign):
        cmd = f'"{zipalign}" -f -v 4 "{apk_unsigned}" "{apk_aligned}"'
        run(cmd)
        aligned_path = apk_aligned
    else:
        print("  zipalign 不存在，跳过对齐")
        aligned_path = apk_unsigned

    # ===== Step 6: 签名 APK =====
    print("\n[Step 6/6] 签名 APK ...")
    keystore = os.path.join(PROJECT_DIR, "debug.keystore")
    apk_signed = os.path.join(apk_dir, "app-debug.apk")

    if not os.path.exists(keystore):
        print("  创建 debug keystore ...")
        keytool = r"C:\Program Files\Java\jdk-26.0.1\bin\keytool.exe"
        cmd = (
            f'"{keytool}" -genkeypair -v '
            f'-keystore "{keystore}" '
            f'-storepass android -keypass android '
            f'-alias androiddebugkey -keyalg RSA '
            f'-keysize 2048 -validity 10000 '
            f'-dname "CN=Android Debug,O=Android,C=US"'
        )
        run(cmd)

    cmd = (
        f'"{BUILD_TOOLS}\\apksigner.bat" sign '
        f'--ks "{keystore}" '
        f'--ks-pass pass:android '
        f'--out "{apk_signed}" "{aligned_path}"'
    )
    run(cmd)

    print("\n  验证签名 ...")
    cmd = f'"{BUILD_TOOLS}\\apksigner.bat" verify "{apk_signed}"'
    run(cmd, check=False)

    # 复制到项目根目录（用版本号命名）
    _, version_name = get_version()
    apk_final = os.path.join(PROJECT_DIR, f"iDRAC控制器_v{version_name}.apk")
    shutil.copy2(apk_signed, apk_final)

    print("\n" + "=" * 50)
    print("  BUILD SUCCESSFUL!")
    print("=" * 50)
    print(f"APK 位置: {apk_final}")
    size_kb = os.path.getsize(apk_final) / 1024
    print(f"文件大小: {size_kb:.1f} KB")
    return 0

if __name__ == "__main__":
    sys.exit(main())
