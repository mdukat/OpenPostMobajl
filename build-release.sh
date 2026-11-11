#!/bin/sh
#
# build-release.sh - build a signed release APK.
#
# The release build is not debuggable and is signed with tools/release.keystore.
# If the keystore does not exist it is generated on first use. Override the
# defaults with:
#
#   RELEASE_STORE_PASSWORD=... RELEASE_KEY_PASSWORD=... RELEASE_KEY_ALIAS=...
#
# For a real production release, replace tools/release.keystore with your own
# keystore and keep it out of version control.
#
set -eu

ROOT="$(cd "$(dirname "$0")" && pwd)"
TOOLS="$ROOT/tools"
JAVA_HOME="$TOOLS/root/usr/lib/jvm/java-17-openjdk"
SDK="$TOOLS/android-sdk"
BT="$SDK/build-tools/34.0.0"
PLATFORM="$SDK/platforms/android-34/android.jar"
APP="$ROOT/app"
OUT="$ROOT/out/release"
LIB="$ROOT/libs"
ZXING="$LIB/zxing-core-3.5.3.jar"

MIN_SDK=23
TARGET_SDK=34
VERSION_CODE="${VERSION_CODE:-3}"
VERSION_NAME="${VERSION_NAME:-1.2}"

STORE_PASS="${RELEASE_STORE_PASSWORD:-openpostmobajl}"
KEY_PASS="${RELEASE_KEY_PASSWORD:-$STORE_PASS}"
KEY_ALIAS="${RELEASE_KEY_ALIAS:-openpostmobajl}"
KEYSTORE="$TOOLS/release.keystore"

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
echo "[2/6] Linking resources (aapt2 link, release)..."
"$TOOLS/gcompat.sh" aapt2 link \
    -o "$OUT/base.apk" \
    -I "$PLATFORM" \
    --manifest "$APP/AndroidManifest.xml" \
    --java "$OUT/gen" \
    --min-sdk-version "$MIN_SDK" \
    --target-sdk-version "$TARGET_SDK" \
    --version-code "$VERSION_CODE" \
    --version-name "$VERSION_NAME" \
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
echo "[6/6] Aligning and signing (release)..."
"$TOOLS/gcompat.sh" zipalign -f -p 4 "$OUT/base.apk" "$OUT/aligned.apk"

if [ ! -f "$KEYSTORE" ]; then
    echo "      generating release keystore: $KEYSTORE"
    keytool -genkeypair -v \
        -keystore "$KEYSTORE" \
        -storepass "$STORE_PASS" -alias "$KEY_ALIAS" -keypass "$KEY_PASS" \
        -keyalg RSA -keysize 2048 -validity 10000 \
        -dname "CN=OpenPostMobajl,O=OpenPostMobajl,C=PL" >/dev/null 2>&1
    echo "      store password: $STORE_PASS"
    echo "      key alias     : $KEY_ALIAS"
    echo "      key password  : $KEY_PASS"
    echo "      (override with RELEASE_STORE_PASSWORD / RELEASE_KEY_PASSWORD / RELEASE_KEY_ALIAS)"
fi

APK="$ROOT/out/openpostmobajl-release.apk"
"$BT/apksigner" sign \
    --ks "$KEYSTORE" \
    --ks-pass "pass:$STORE_PASS" \
    --key-pass "pass:$KEY_PASS" \
    --ks-key-alias "$KEY_ALIAS" \
    --out "$APK" "$OUT/aligned.apk"

"$BT/apksigner" verify "$APK"
echo
echo "Built: $APK"
ls -l "$APK"
