#!/usr/bin/env python3
"""手动打包 APK - iDRAC 控制器（aapt2 完整流程，Android 11+ 兼容）"""

import os
import re
import sys
import subprocess
import shutil
import zipfile

# ===== 路径配置 =====
PROJECT_DIR  = r"C:\Users\huc_o\WorkBuddy\Claw"
BUILD_TOOLS = r"C:\Android\build-tools\35.0.0"
PLATFORM    = r"C:\Android\platforms\android-35"
LIBS_DIR    = os.path.join(PROJECT_DIR, "app", "libs")
SRC_DIR     = os.path.join(PROJECT_DIR, "app", "src", "main")
OUT_DIR     = os.path.join(PROJECT_DIR, "out")

AAPT2    = os.path.join(BUILD_TOOLS, "aapt2.exe")
JAVAC    = r"C:\Program Files\Java\jdk-26.0.1\bin\javac.exe"
D8       = os.path.join(BUILD_TOOLS, "d8.bat")
APKSIGNER = os.path.join(BUILD_TOOLS, "apksigner.bat")
ZIPALIGN  = os.path.join(BUILD_TOOLS, "zipalign.exe")


def get_version():
    manifest = os.path.join(SRC_DIR, "AndroidManifest.xml")
    with open(manifest, "r", encoding="utf-8") as f:
        content = f.read()
    version_code = re.search(r'android:versionCode="(\d+)"', content)
    version_name = re.search(r'android:versionName="([^"]+)"', content)
    return (
        version_code.group(1) if version_code else "0",
        version_name.group(1) if version_name else "unknown",
    )


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
    version_code, version_name = get_version()
    print("=" * 60)
    print(f"  iDRAC 控制器 v{version_name} (aapt2) - 手动 APK 打包")
    print("=" * 60)

    # 清理输出目录
    if os.path.exists(OUT_DIR):
        shutil.rmtree(OUT_DIR)
    gen_dir    = os.path.join(OUT_DIR, "gen")       # R.java 输出目录
    classes_dir = os.path.join(OUT_DIR, "classes")
    dex_dir    = os.path.join(OUT_DIR, "dex")
    apk_dir    = os.path.join(OUT_DIR, "apk")
    compiled_dir = os.path.join(OUT_DIR, "compiled_res")
    for d in [gen_dir, classes_dir, dex_dir, apk_dir, compiled_dir]:
        os.makedirs(d, exist_ok=True)

    android_jar = os.path.join(PLATFORM, "android.jar")
    res_dir     = os.path.join(SRC_DIR, "res")
    manifest    = os.path.join(SRC_DIR, "AndroidManifest.xml")

    # ===== Step 1: aapt2 compile 所有资源 =====
    print("\n[Step 1/7] aapt2 compile 资源 ...")
    res_files = []
    for root, dirs, files in os.walk(res_dir):
        for fname in files:
            res_files.append(os.path.join(root, fname))

    print(f"  共 {len(res_files)} 个资源文件")
    for i, res_file in enumerate(res_files):
        if i % 20 == 0:
            print(f"    [{i+1}/{len(res_files)}] {os.path.relpath(res_file, res_dir)}")
        cmd = f'"{AAPT2}" compile -o "{compiled_dir}" "{res_file}"'
        run(cmd, check=False)  # 单个文件失败不中断

    flat_files = [os.path.join(compiled_dir, f) for f in os.listdir(compiled_dir) if f.endswith(".flat")]
    print(f"  生成了 {len(flat_files)} 个 .flat 文件")

    if not flat_files:
        print("ERROR: 没有生成任何 .flat 文件")
        sys.exit(1)

    # ===== Step 2: aapt2 link 生成 APK + R.java =====
    print("\n[Step 2/7] aapt2 link 生成 APK 和 R.java ...")
    tmp_apk = os.path.join(OUT_DIR, "tmp.apk")
    flat_args = " ".join(f'"{f}"' for f in flat_files)
    cmd = (
        f'"{AAPT2}" link '
        f'-o "{tmp_apk}" '
        f'-I "{android_jar}" '
        f'--manifest "{manifest}" '
        f'--java "{gen_dir}" '
        f'--auto-add-overlay '
        f'{flat_args}'
    )
    run(cmd)
    print(f"  APK 中间文件: {tmp_apk}")
    print(f"  R.java 已生成到: {gen_dir}")

    # 验证 R.java 已生成
    r_java_files = []
    for root, dirs, files in os.walk(gen_dir):
        for f in files:
            if f == "R.java":
                r_java_files.append(os.path.join(root, f))
    print(f"  找到 {len(r_java_files)} 个 R.java 文件")

    # ===== Step 3: 编译 Java 源码（使用 aapt2 link 生成的 R.java）=====
    print("\n[Step 3/7] 编译 Java 源码 ...")
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

    # ===== Step 4: 转 DEX =====
    print("\n[Step 4/7] 转换为 DEX 格式 ...")
    classes_jar = os.path.join(OUT_DIR, "classes.jar")
    with zipfile.ZipFile(classes_jar, "w", zipfile.ZIP_DEFLATED) as zf:
        for root, dirs, files in os.walk(classes_dir):
            for f in files:
                if f.endswith(".class"):
                    full = os.path.join(root, f)
                    arc = os.path.relpath(full, classes_dir)
                    zf.write(full, arc)
    print(f"  已打包为 {classes_jar}")

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

    # ===== Step 5: 将 classes.dex 加入 APK =====
    print("\n[Step 5/7] 将 classes.dex 加入 APK ...")
    apk_unsigned = os.path.join(apk_dir, "app-debug-unsigned.apk")

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
    print(f"  未签名 APK: {apk_unsigned}")

    # 清理 compiled_res 目录
    if os.path.exists(compiled_dir):
        shutil.rmtree(compiled_dir, ignore_errors=True)
        print("  编译资源目录已清理")

    # ===== Step 6: 对齐 APK =====
    print("\n[Step 6/7] 对齐 APK ...")
    apk_aligned = os.path.join(apk_dir, "app-debug-aligned.apk")
    if os.path.exists(ZIPALIGN):
        cmd = f'"{ZIPALIGN}" -f -v 4 "{apk_unsigned}" "{apk_aligned}"'
        run(cmd)
        aligned_path = apk_aligned
    else:
        print("  zipalign 不存在，跳过对齐")
        aligned_path = apk_unsigned

    # ===== Step 7: 签名 APK =====
    print("\n[Step 7/7] 签名 APK ...")
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

    # 复制到项目目录（使用动态版本号）
    apk_final = os.path.join(PROJECT_DIR, f"iDRAC控制器_v{version_name}.apk")
    shutil.copy2(apk_signed, apk_final)

    print("\n" + "=" * 60)
    print("  BUILD SUCCESSFUL! (aapt2)")
    print("=" * 60)
    print(f"APK 位置: {apk_final}")
    size_kb = os.path.getsize(apk_final) / 1024
    print(f"文件大小: {size_kb:.1f} KB")

    # 验证 resources.arsc 压缩状态
    print("\n  验证 resources.arsc 压缩状态 ...")
    with zipfile.ZipFile(apk_final, "r") as zf:
        for info in zf.infolist():
            if info.filename == "resources.arsc":
                is_compressed = info.compress_type != zipfile.ZIP_STORED
                status = "压缩 (问题!)" if is_compressed else "未压缩 (正确)"
                print(f"    resources.arsc: {status}")
                break

    return 0


if __name__ == "__main__":
    sys.exit(main())
