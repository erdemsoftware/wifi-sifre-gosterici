import os
import subprocess
import shutil

PROJECT_DIR = r"C:\Users\muham\.gemini\antigravity\scratch\wifi_app"
JDK_BIN = r"C:\Program Files\Unity\Hub\Editor\2022.3.62f3\Editor\Data\PlaybackEngines\AndroidPlayer\OpenJDK\bin"
BUILD_TOOLS = r"C:\Program Files\Unity\Hub\Editor\2022.3.62f3\Editor\Data\PlaybackEngines\AndroidPlayer\SDK\build-tools\34.0.0"
ANDROID_JAR = r"C:\Program Files\Unity\Hub\Editor\2022.3.62f3\Editor\Data\PlaybackEngines\AndroidPlayer\SDK\platforms\android-34\android.jar"

AAPT2 = os.path.join(BUILD_TOOLS, "aapt2.exe")
D8 = os.path.join(BUILD_TOOLS, "d8.bat")
APKSIGNER = os.path.join(BUILD_TOOLS, "apksigner.bat")
ZIPALIGN = os.path.join(BUILD_TOOLS, "zipalign.exe")
JAVAC = os.path.join(JDK_BIN, "javac.exe")
KEYTOOL = os.path.join(JDK_BIN, "keytool.exe")

env = os.environ.copy()
env["JAVA_HOME"] = os.path.dirname(JDK_BIN)
env["PATH"] = JDK_BIN + ";" + env.get("PATH", "")

RES_DIR = os.path.join(PROJECT_DIR, "res")
BUILD_DIR = os.path.join(PROJECT_DIR, "build")
COMPILED_RES = os.path.join(BUILD_DIR, "compiled_res.zip")
GEN_SRC = os.path.join(BUILD_DIR, "gen")
OBJ_DIR = os.path.join(BUILD_DIR, "obj")
BIN_DIR = os.path.join(BUILD_DIR, "bin")
KEYSTORE = os.path.join(BUILD_DIR, "debug.keystore")

for d in [GEN_SRC, OBJ_DIR, BIN_DIR]:
    os.makedirs(d, exist_ok=True)

# 1. Compile resources with aapt2
print("1. Compiling resources...")
cmd = [AAPT2, "compile", "--dir", RES_DIR, "-o", COMPILED_RES]
res = subprocess.run(cmd, capture_output=True, text=True)
if res.returncode != 0:
    print("aapt2 compile error:", res.stderr)
    exit(1)

# 2. Link resources and generate R.java + unaligned APK
print("2. Linking resources...")
UNALIGNED_APK = os.path.join(BIN_DIR, "app-unaligned.apk")
cmd = [
    AAPT2, "link",
    "-I", ANDROID_JAR,
    "--manifest", os.path.join(PROJECT_DIR, "AndroidManifest.xml"),
    "--java", GEN_SRC,
    "-o", UNALIGNED_APK,
    COMPILED_RES,
    "--auto-add-overlay"
]
res = subprocess.run(cmd, capture_output=True, text=True)
if res.returncode != 0:
    print("aapt2 link error:", res.stderr)
    exit(1)

# 3. Compile Java sources
print("3. Compiling Java...")
java_files = []
for root, _, files in os.walk(PROJECT_DIR):
    for f in files:
        if f.endswith(".java"):
            java_files.append(os.path.join(root, f))
for root, _, files in os.walk(GEN_SRC):
    for f in files:
        if f.endswith(".java"):
            java_files.append(os.path.join(root, f))

cmd = [
    JAVAC,
    "-encoding", "UTF-8",
    "-cp", ANDROID_JAR,
    "-d", OBJ_DIR,
    "-source", "1.8",
    "-target", "1.8"
] + java_files
res = subprocess.run(cmd, capture_output=True, text=True, env=env)
if res.returncode != 0:
    print("javac error:", res.stderr)
    exit(1)

# 4. Convert classes to DEX with d8
print("4. Converting to DEX with d8...")
class_files = []
for root, _, files in os.walk(OBJ_DIR):
    for f in files:
        if f.endswith(".class"):
            class_files.append(os.path.join(root, f))

cmd = [
    D8,
    "--lib", ANDROID_JAR,
    "--output", BIN_DIR
] + class_files
res = subprocess.run(cmd, capture_output=True, text=True, shell=True, env=env)
if res.returncode != 0:
    print("d8 error:", res.stderr, res.stdout)
    exit(1)

# 5. Add classes.dex into unaligned apk
print("5. Packaging DEX into APK...")
classes_dex = os.path.join(BIN_DIR, "classes.dex")
# Use zip or jar
import zipfile
with zipfile.ZipFile(UNALIGNED_APK, "a") as z:
    z.write(classes_dex, "classes.dex")

# 6. Zipalign
print("6. Aligning APK...")
ALIGNED_APK = os.path.join(BIN_DIR, "app-aligned.apk")
if os.path.exists(ALIGNED_APK):
    os.remove(ALIGNED_APK)
cmd = [ZIPALIGN, "-p", "-f", "4", UNALIGNED_APK, ALIGNED_APK]
res = subprocess.run(cmd, capture_output=True, text=True)
if res.returncode != 0:
    print("zipalign error:", res.stderr)
    exit(1)

# 7. Generate debug keystore if not exists
if not os.path.exists(KEYSTORE):
    print("7. Generating debug keystore...")
    cmd = [
        KEYTOOL, "-genkeypair", "-v",
        "-keystore", KEYSTORE,
        "-alias", "androiddebugkey",
        "-keyalg", "RSA", "-keysize", "2048", "-validity", "10000",
        "-storepass", "android", "-keypass", "android",
        "-dname", "CN=Android Debug,O=Android,C=US"
    ]
    res = subprocess.run(cmd, capture_output=True, text=True, env=env)
    if res.returncode != 0:
        print("keytool error:", res.stderr)
        exit(1)

# 8. Sign APK with apksigner
print("8. Signing APK...")
FINAL_APK = os.path.join(PROJECT_DIR, "WiFi_Sifreleri.apk")
cmd = [
    APKSIGNER, "sign",
    "--ks", KEYSTORE,
    "--ks-pass", "pass:android",
    "--key-pass", "pass:android",
    "--ks-key-alias", "androiddebugkey",
    "--out", FINAL_APK,
    ALIGNED_APK
]
res = subprocess.run(cmd, capture_output=True, text=True, shell=True, env=env)
if res.returncode != 0:
    print("apksigner error:", res.stderr, res.stdout)
    exit(1)

print("SUCCESS! Final APK created at:", FINAL_APK)
