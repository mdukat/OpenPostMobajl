#!/bin/sh
#
# build-debug.sh - build the debug APK.
#
# No Gradle, no AndroidX, no Kotlin: resources are compiled with aapt2,
# Java with javac, bytecode with d8, and the APK is zipaligned + signed
# with the SDK build-tools.
#
# Written for Alpine's /bin/sh (busybox ash); no bashisms.
#
set -eu

ROOT="$(cd "$(dirname "$0")" && pwd)"
TOOLS="$ROOT/tools"
JAVA_HOME="$TOOLS/root/usr/lib/jvm/java-17-openjdk"
SDK="$TOOLS/android-sdk"
BT="$SDK/build-tools/34.0.0"
PLATFORM="$SDK/platforms/android-34/android.jar"
APP="$ROOT/app"
OUT="$ROOT/out/debug"
LIB="$ROOT/libs"
ZXING="$LIB/zxing-core-3.5.3.jar"

MIN_SDK=23
TARGET_SDK=34
VERSION_CODE="${VERSION_CODE:-2}"
VERSION_NAME="${VERSION_NAME:-1.1}"

export JAVA_HOME
export ANDROID_SDK_ROOT="$SDK"
PATH="$JAVA_HOME/bin:$PATH"
export PATH

# ---------------------------------------------------------------------------
# Sanity checks
# ---------------------------------------------------------------------------
for bin in "$JAVA_HOME/bin/javac" "$BT/d8" "$BT/apksigner" "$TOOLS/gcompat.sh" "$PLATFORM" "$ZXING"; do
    if [ ! -e "$bin" ]; then
        echo "Missing: $bin" >&2
        echo "Run ./bootstrap.sh first to provision the toolchain." >&2
        exit 1
    fi
done

rm -rf "$OUT"
mkdir -p "$OUT/res" "$OUT/gen" "$OUT/classes" "$OUT/dex" "$OUT/assets"
cp "$ROOT/LICENSE.md" "$OUT/assets/LICENSE.md"
cp "$ROOT/THIRD-PARTY-LICENSES.md" "$OUT/assets/THIRD-PARTY-LICENSES.md"

# ---------------------------------------------------------------------------
# 1. Compile resources
# ---------------------------------------------------------------------------
echo "[1/6] Compiling resources (aapt2 compile)..."
"$TOOLS/gcompat.sh" aapt2 compile \
    --dir "$APP/res" \
    -o "$OUT/res/resources.zip"

# ---------------------------------------------------------------------------
# 2. Link resources + manifest, generate R.java + base APK
# ---------------------------------------------------------------------------
echo "[2/6] Linking resources (aapt2 link, debug)..."
"$TOOLS/gcompat.sh" aapt2 link \
    -o "$OUT/base.apk" \
    -I "$PLATFORM" \
    --manifest "$APP/AndroidManifest.xml" \
    --java "$OUT/gen" \
    --min-sdk-version "$MIN_SDK" \
    --target-sdk-version "$TARGET_SDK" \
    --version-code "$VERSION_CODE" \
    --version-name "$VERSION_NAME" \
    --debug-mode \
    -A "$OUT/assets" \
    "$OUT/res/resources.zip"

# ---------------------------------------------------------------------------
# 3. Compile Java (app sources + generated R.java)
# ---------------------------------------------------------------------------
echo "[3/6] Compiling Java (javac)..."
find "$APP/src" "$OUT/gen" -name '*.java' > "$OUT/sources.txt"
javac -source 8 -target 8 -bootclasspath "$PLATFORM" -cp "$ZXING" \
    -d "$OUT/classes" "@$OUT/sources.txt"

# ---------------------------------------------------------------------------
# 4. Convert bytecode to Dalvik (d8)
# ---------------------------------------------------------------------------
echo "[4/6] Dexing (d8)..."
"$BT/d8" --lib "$PLATFORM" --min-api "$MIN_SDK" \
    --output "$OUT/dex" $(find "$OUT/classes" -name '*.class') "$ZXING"

# ---------------------------------------------------------------------------
# 5. Package: add classes.dex to the base APK
# ---------------------------------------------------------------------------
echo "[5/6] Packaging APK..."
(cd "$OUT/dex" && jar uf "$OUT/base.apk" classes.dex)

# ---------------------------------------------------------------------------
# 6. Align + sign
# ---------------------------------------------------------------------------
echo "[6/6] Aligning and signing (debug)..."
"$TOOLS/gcompat.sh" zipalign -f -p 4 "$OUT/base.apk" "$OUT/aligned.apk"

KEYSTORE="$TOOLS/debug.keystore"
if [ ! -f "$KEYSTORE" ]; then
    echo "      generating debug keystore..."
    keytool -genkeypair -v \
        -keystore "$KEYSTORE" \
        -storepass android -alias androiddebugkey -keypass android \
        -keyalg RSA -keysize 2048 -validity 10000 \
        -dname "CN=Android Debug,O=Android,C=US" >/dev/null 2>&1
fi

APK="$ROOT/out/openpostmobajl-debug.apk"
"$BT/apksigner" sign \
    --ks "$KEYSTORE" \
    --ks-pass pass:android \
    --key-pass pass:android \
    --ks-key-alias androiddebugkey \
    --out "$APK" "$OUT/aligned.apk"

"$BT/apksigner" verify "$APK"
echo
echo "Built: $APK"
ls -l "$APK"
