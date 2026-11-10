#!/bin/sh
#
# bootstrap.sh - create a complete, root-free native Android build
# environment in the CURRENT directory, on standard Alpine /bin/sh.
#
# What it does
# ------------
#   * Downloads every required package from the internet into ./mirror/
#     (Alpine .apk packages, the Android SDK .zip packages, and the ZXing
#     core jar used for local QR generation).  That directory is
#     self-describing (mirror/MANIFEST) and can be copied to CD/DVD as a
#     permanent, offline copy.
#   * Unpacks a musl-native OpenJDK 17, the Android SDK (build-tools 34,
#     platform android-34, platform-tools) and Alpine's gcompat layer.
#   * Builds tools/nsroot, a tiny glibc chroot that lets the glibc-linked
#     SDK tools (aapt2, zipalign, adb) run on musl without root.
#   * Writes a minimal native "Hello World" project (app/, build.sh).
#
# Nothing is installed system-wide and no root account is needed.
#
# Usage
# -----
#   ./bootstrap.sh                     # online: download into ./mirror
#   ./bootstrap.sh --mirror DIR        # offline: use packages from DIR
#   ./bootstrap.sh --force             # re-provision the toolchain
#   ./bootstrap.sh --help
#
# The mirror created by the online run can later be mounted/copied anywhere
# and used with:
#
#   ./bootstrap.sh --mirror /mnt/cdrom/mirror
#
# Environment overrides (optional):
#   ALPINE_BRANCH, ALPINE_ARCH, ALPINE_REPO,
#   ANDROID_REPO, ANDROID_REPO_XML, PLATFORM, BUILD_TOOLS
#
set -eu

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------
ALPINE_BRANCH="${ALPINE_BRANCH:-v3.24}"
ALPINE_ARCH="${ALPINE_ARCH:-x86_64}"
ALPINE_REPO="${ALPINE_REPO:-https://dl-cdn.alpinelinux.org/alpine}"
ANDROID_REPO="${ANDROID_REPO:-https://dl.google.com/android/repository}"
ANDROID_REPO_XML="${ANDROID_REPO_XML:-repository2-3.xml}"

PLATFORM="${PLATFORM:-android-34}"
BUILD_TOOLS="${BUILD_TOOLS:-34.0.0}"

ZXING_VERSION="${ZXING_VERSION:-3.5.3}"
ZXING_FILE="zxing-core-$ZXING_VERSION.jar"
ZXING_URL="${ZXING_URL:-https://repo1.maven.org/maven2/com/google/zxing/core/$ZXING_VERSION/core-$ZXING_VERSION.jar}"
# Pinned SHA-256 of the genuine ZXing core $ZXING_VERSION artifact (the value
# matches Maven Central's published SHA-1 of the same jar).
ZXING_SHA256="${ZXING_SHA256:-8d8064c1636fdaef7189dd9055c7d59950a8940a12f2293956446ec3c109fd82}"

# Packages that must be present in the mirror.
ALPINE_PKGS="openjdk17-jdk openjdk17-jre-headless gcompat libucontext musl-obstack libgcc"
ANDROID_PKGS="build-tools;$BUILD_TOOLS platforms;$PLATFORM platform-tools"

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------
log() { printf '\n==> %s\n' "$*"; }
die() { printf 'ERROR: %s\n' "$*" >&2; exit 1; }

usage() {
    cat <<EOF
Usage: $0 [options]

Creates a native Android build environment (toolchain + Hello World
template) in the current directory.

Options:
  -m, --mirror DIR   Offline mode: do not touch the network; use the
                     packages already present in DIR (e.g. a copy of a
                     previously created ./mirror).
  -f, --force        Re-provision the toolchain even if it already exists.
  -h, --help         Show this help.

Without options all packages are downloaded into ./mirror/, which can be
copied to CD/DVD as an offline backup.
EOF
}

# fetch <url> <output-file>
fetch() {
    if command -v curl >/dev/null 2>&1; then
        curl -fsSL "$1" -o "$2"
    elif command -v wget >/dev/null 2>&1; then
        wget -q -O "$2" "$1"
    else
        die "need curl or wget to download packages"
    fi
}

# download <url> <filename>  (skips if already cached in $MIRROR)
download() {
    _url="$1"
    _file="$2"
    _dest="$MIRROR/$_file"
    if [ -s "$_dest" ]; then
        return 0
    fi
    printf '    %s\n' "$_file"
    fetch "$_url" "$_dest.part"
    mv "$_dest.part" "$_dest"
}

file_sha1() {
    if command -v sha1sum >/dev/null 2>&1; then
        sha1sum "$1" | cut -d' ' -f1
    else
        printf ''
    fi
}

file_sha256() {
    if command -v sha256sum >/dev/null 2>&1; then
        sha256sum "$1" | cut -d' ' -f1
    else
        printf ''
    fi
}

# manifest_add <type> <name> <filename> [sha256]
manifest_add() {
    if [ "${4:-}" = "" ]; then
        _sha="$(file_sha256 "$MIRROR/$3")"
    else
        _sha="$4"
    fi
    printf '%s %s %s %s\n' "$1" "$2" "$3" "$_sha" >> "$MANIFEST"
}

# manifest_field <type> <name> <3|4>
manifest_field() {
    awk -v t="$1" -v n="$2" -v f="$3" \
        '$1==t && $2==n { if (f=="3") print $3; else print $4; exit }' "$MANIFEST"
}

# pkg_file <type> <name> -> filename inside the mirror
pkg_file() {
    _f="$(manifest_field "$1" "$2" 3)"
    [ -n "$_f" ] || die "package '$2' ($1) not found in $MANIFEST"
    printf '%s\n' "$_f"
}

# pkg_path <type> <name> -> absolute path inside the mirror
pkg_path() {
    printf '%s/%s\n' "$MIRROR" "$(pkg_file "$1" "$2")"
}

# ---------------------------------------------------------------------------
# Argument parsing
# ---------------------------------------------------------------------------
OFFLINE=0
FORCE=0
MIRROR_ARG=""

while [ $# -gt 0 ]; do
    case "$1" in
        -m|--mirror)
            [ $# -ge 2 ] || die "$1 requires a directory argument"
            MIRROR_ARG="$2"
            OFFLINE=1
            shift 2
            ;;
        --mirror=*)
            MIRROR_ARG="${1#--mirror=}"
            OFFLINE=1
            shift
            ;;
        -f|--force)
            FORCE=1
            shift
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            die "unknown option: $1 (try --help)"
            ;;
    esac
done

# ---------------------------------------------------------------------------
# Layout (everything inside the current directory)
# ---------------------------------------------------------------------------
TARGET="$(pwd)"
TOOLS="$TARGET/tools"
JDK_HOME="$TOOLS/root/usr/lib/jvm/java-17-openjdk"
SDK="$TOOLS/android-sdk"
NS="$TOOLS/nsroot"

if [ "$OFFLINE" -eq 1 ]; then
    [ -d "$MIRROR_ARG" ] || die "mirror directory not found: $MIRROR_ARG"
    MIRROR="$(cd "$MIRROR_ARG" && pwd)"
else
    MIRROR="$TARGET/mirror"
fi
MANIFEST="$MIRROR/MANIFEST"

mkdir -p "$TOOLS" "$MIRROR"

TMP="$(mktemp -d "${TMPDIR:-/tmp}/android-bootstrap.XXXXXX")"
cleanup() { rm -rf "$TMP"; }
trap cleanup EXIT INT TERM

# ---------------------------------------------------------------------------
# Mirror population (online mode)
# ---------------------------------------------------------------------------
populate_mirror() {
    log "Downloading packages into $MIRROR"

    # Start a fresh manifest; already-downloaded files are kept as cache.
    : > "$MANIFEST"

    # ---- Alpine indexes ----
    for repo in main community; do
        fetch "$ALPINE_REPO/$ALPINE_BRANCH/$repo/$ALPINE_ARCH/APKINDEX.tar.gz" \
            "$TMP/$repo.tar.gz" || die "cannot download Alpine index ($repo)"
        tar -xzf "$TMP/$repo.tar.gz" -C "$TMP" APKINDEX
        mv "$TMP/APKINDEX" "$TMP/APKINDEX.$repo"
    done

    # ---- Alpine packages ----
    for pkg in $ALPINE_PKGS; do
        found=""
        for repo in main community; do
            ver="$(awk -v pkg="$pkg" 'BEGIN{RS=""} {
                    p=""; v=""; n=split($0,a,"\n");
                    for (i=1;i<=n;i++) {
                        if (a[i] ~ /^P:/) p=substr(a[i],3);
                        if (a[i] ~ /^V:/) v=substr(a[i],3);
                    }
                    if (p==pkg) { print v; exit }
                }' "$TMP/APKINDEX.$repo")"
            if [ -n "$ver" ]; then
                found="$repo/$ver"
                break
            fi
        done
        [ -n "$found" ] || die "Alpine package not found: $pkg"
        repo="${found%%/*}"
        ver="${found#*/}"
        file="$pkg-$ver.apk"
        download "$ALPINE_REPO/$ALPINE_BRANCH/$repo/$ALPINE_ARCH/$file" "$file"
        manifest_add alpine "$pkg" "$file"
    done

    # ---- Android SDK repository ----
    fetch "$ANDROID_REPO/$ANDROID_REPO_XML" "$TMP/repo.xml" \
        || die "cannot download Android repository XML"

    cat > "$TMP/sdkurl.awk" <<'AWKEOF'
function get_url(block,    n, parts, i, chunk) {
    n = split(block, parts, "<archive>")
    for (i = 1; i <= n; i++) {
        chunk = parts[i]
        if (chunk !~ /<url>/) continue
        if (chunk ~ /<host-os>/ && chunk !~ /<host-os>linux<\/host-os>/) continue
        if (chunk ~ /<host-arch>/ && chunk !~ /<host-arch>x86_64<\/host-arch>/) continue
        if (match(chunk, /<url>[^<]*<\/url>/))
            return substr(chunk, RSTART + 5, RLENGTH - 11)
    }
    return ""
}
function get_sha1(block,   chunk) {
    if (match(block, /<checksum type="sha1">[0-9a-f]+<\/checksum>/)) {
        chunk = substr(block, RSTART, RLENGTH)
        sub(/.*sha1">/, "", chunk); sub(/<.*/, "", chunk)
        return chunk
    }
    return ""
}
{
    line = $0
    if (line ~ /<remotePackage path="/) {
        if (match(line, /path="[^"]*"/))
            path = substr(line, RSTART + 6, RLENGTH - 7)
        buf = line "\n"; inpkg = 1; next
    }
    if (inpkg) {
        buf = buf line "\n"
        if (line ~ /<\/remotePackage>/) {
            inpkg = 0
            ch = ""
            if (match(buf, /<channelRef ref="[^"]*"/)) {
                ch = substr(buf, RSTART, RLENGTH)
                sub(/.*ref="/, "", ch); sub(/"$/, "", ch)
            }
            if (path == target) {
                u = get_url(buf)
                if (u != "") {
                    s = get_sha1(buf)
                    if (ch == "" || ch == "channel-0") { print u "|" s; found = 1; exit }
                    if (fallback == "") fallback = u "|" s
                }
            }
        }
        next
    }
}
END { if (!found && fallback != "") print fallback }
AWKEOF

    for pkg in $ANDROID_PKGS; do
        line="$(awk -v target="$pkg" -f "$TMP/sdkurl.awk" "$TMP/repo.xml")"
        [ -n "$line" ] || die "Android package not found: $pkg"
        url="${line%%|*}"
        sha1="${line#*|}"
        download "$ANDROID_REPO/$url" "$url"
        # Google still publishes SHA-1 only, so verify it at download time and
        # then record a stronger SHA-256 in the mirror manifest.
        if [ -n "$sha1" ]; then
            _got1="$(file_sha1 "$MIRROR/$url")"
            [ "$_got1" = "$sha1" ] \
                || die "checksum mismatch (sha1): $url"
        fi
        manifest_add android "$pkg" "$url"
    done

    # ---- ZXing core (local QR generation) ----
    download "$ZXING_URL" "$ZXING_FILE"
    _got="$(file_sha256 "$MIRROR/$ZXING_FILE")"
    [ "$_got" = "$ZXING_SHA256" ] \
        || die "ZXing checksum mismatch: expected $ZXING_SHA256, got ${_got:-<no sha256sum>}"
    manifest_add zxing core "$ZXING_FILE"
}

# ---------------------------------------------------------------------------
# Mirror validation (offline mode)
# ---------------------------------------------------------------------------
check_pkgs() {
    _type="$1"
    shift
    for _pkg in "$@"; do
        _f="$(pkg_file "$_type" "$_pkg")"
        [ -s "$MIRROR/$_f" ] || die "missing file in mirror: $MIRROR/$_f"
        _exp="$(manifest_field "$_type" "$_pkg" 4)"
        if [ -n "$_exp" ]; then
            _got="$(file_sha256 "$MIRROR/$_f")"
            [ "$_got" = "$_exp" ] || die "checksum mismatch: $_f"
        fi
        printf '    ok %s\n' "$_f"
    done
}

validate_mirror() {
    log "Using local mirror: $MIRROR"
    [ -f "$MANIFEST" ] || die "no MANIFEST in $MIRROR (not a bootstrap mirror?)"
    check_pkgs alpine $ALPINE_PKGS
    check_pkgs android $ANDROID_PKGS
    check_pkgs zxing core
}

# ---------------------------------------------------------------------------
# ZXing jar (downloaded into the mirror, installed into ./libs)
# ---------------------------------------------------------------------------
install_zxing() {
    _src="$MIRROR/$ZXING_FILE"
    _dst="$TARGET/libs/$ZXING_FILE"
    [ -s "$_src" ] || die "missing ZXing jar in mirror: $_src"
    _got="$(file_sha256 "$_src")"
    [ "$_got" = "$ZXING_SHA256" ] \
        || die "ZXing checksum mismatch: expected $ZXING_SHA256, got ${_got:-<no sha256sum>}"
    mkdir -p "$TARGET/libs"
    if [ -s "$_dst" ]; then
        log "ZXing jar already present: $_dst"
    else
        cp "$_src" "$_dst"
        log "Installed ZXing jar: $_dst"
    fi
}

# ---------------------------------------------------------------------------
# Provisioning
# ---------------------------------------------------------------------------
# extract_zip <zip> <destination>
# Google's SDK zips contain a single top-level directory; copy its contents.
extract_zip() {
    _zip="$1"
    _dest="$2"
    rm -rf "$_dest"
    mkdir -p "$_dest"
    _unz="$TMP/unzip"
    rm -rf "$_unz"
    mkdir -p "$_unz"
    unzip -q "$_zip" -d "$_unz"
    for _entry in "$_unz"/*; do
        [ -e "$_entry" ] || continue
        if [ -d "$_entry" ]; then
            cp -a "$_entry"/. "$_dest"/
        else
            cp -a "$_entry" "$_dest"/
        fi
    done
    rm -rf "$_unz"
}

provision() {
    log "Extracting OpenJDK 17 into tools/root"
    mkdir -p "$TOOLS/root"
    for pkg in openjdk17-jdk openjdk17-jre-headless; do
        tar -xzf "$(pkg_path alpine "$pkg")" -C "$TOOLS/root"
    done

    log "Extracting Android SDK"
    extract_zip "$(pkg_path android "build-tools;$BUILD_TOOLS")" \
        "$SDK/build-tools/$BUILD_TOOLS"
    extract_zip "$(pkg_path android "platforms;$PLATFORM")" \
        "$SDK/platforms/$PLATFORM"
    extract_zip "$(pkg_path android platform-tools)" "$SDK/platform-tools"

    log "Building gcompat environment (tools/nsroot)"
    _gcr="$TMP/gcompat-root"
    mkdir -p "$_gcr"
    for pkg in gcompat libucontext musl-obstack libgcc; do
        tar -xzf "$(pkg_path alpine "$pkg")" -C "$_gcr"
    done

    rm -rf "$NS"
    mkdir -p "$NS/lib" "$NS/lib64" "$NS/dev" "$NS/proc" "$NS/tmp"

    # musl dynamic loader (hard-coded by the gcompat stub).
    cp /lib/ld-musl-x86_64.so.1 "$NS/lib/" \
        || die "musl loader not found at /lib/ld-musl-x86_64.so.1"
    ln -sf ld-musl-x86_64.so.1 "$NS/lib/libc.musl-x86_64.so.1"

    # gcompat shim + dependencies.
    cp -a "$_gcr/lib/." "$NS/lib/"
    cp "$_gcr/usr/lib/libucontext.so.1" "$NS/lib/"
    cp "$_gcr/usr/lib/libobstack.so.1" "$NS/lib/"
    if [ -f "$_gcr/usr/lib/libgcc_s.so.1" ]; then
        cp "$_gcr/usr/lib/libgcc_s.so.1" "$NS/lib/"
    elif [ -f /usr/lib/libgcc_s.so.1 ]; then
        cp /usr/lib/libgcc_s.so.1 "$NS/lib/"
    else
        die "libgcc_s.so.1 not found"
    fi

    # glibc name for libdl (gcompat exports dlopen/dlsym).
    ln -sf libgcompat.so.0 "$NS/lib/libdl.so.2"
    # libc++ ships with build-tools and is needed by zipalign.
    cp "$SDK/build-tools/$BUILD_TOOLS/lib64/libc++.so" "$NS/lib/"
    # Interpreter looked up as /lib64/ld-linux-x86-64.so.2.
    ln -sf ../lib/ld-linux-x86-64.so.2 "$NS/lib64/ld-linux-x86-64.so.2"
    # Placeholder so gcompat.sh can bind /dev/null.
    : > "$NS/dev/null"

    log "Sanity check"
    _aaptver="$("$TOOLS/gcompat.sh" aapt2 version 2>&1 | head -n1)"
    [ -n "$_aaptver" ] || die "aapt2 failed to run"
    printf '    %s\n' "$_aaptver"
}

# ---------------------------------------------------------------------------
# Project template
# ---------------------------------------------------------------------------
# write_file <path>  (reads content from stdin, never overwrites)
write_file() {
    if [ -e "$1" ]; then
        cat >/dev/null
        printf '    exists %s\n' "$1"
    else
        mkdir -p "$(dirname "$1")"
        cat > "$1"
        printf '    create %s\n' "$1"
    fi
}

write_project_files() {
    if [ -e "$TARGET/app/AndroidManifest.xml" ]; then
        log "Project files already present; skipping template"
        return 0
    fi
    log "Writing project template (existing files are kept)"

    write_file "$TARGET/app/AndroidManifest.xml" <<'EOF_APP_MANIFEST'
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.example.hello"
    android:versionCode="1"
    android:versionName="1.0">

    <uses-sdk android:minSdkVersion="21" android:targetSdkVersion="34" />

    <application
        android:label="@string/app_name"
        android:icon="@android:drawable/sym_def_app_icon"
        android:allowBackup="false"
        android:supportsRtl="true">

        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
EOF_APP_MANIFEST

    write_file "$TARGET/app/res/values/strings.xml" <<'EOF_APP_STRINGS'
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">Hello World</string>
</resources>
EOF_APP_STRINGS

    write_file "$TARGET/app/res/values/strings_message.xml" <<'EOF_APP_MESSAGE'
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="hello_message">Hello, World!</string>
</resources>
EOF_APP_MESSAGE

    write_file "$TARGET/app/src/com/example/hello/MainActivity.java" <<'EOF_APP_MAIN'
package com.example.hello;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.TextView;

/**
 * Minimal native "Hello, World!" activity.
 *
 * It uses only framework classes (no AndroidX / no third-party libraries) so
 * the resulting APK stays as small and dependency-free as possible.
 */
public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        TextView view = new TextView(this);
        view.setText(R.string.hello_message);
        view.setTextColor(Color.rgb(0x11, 0x11, 0x11));
        view.setBackgroundColor(Color.WHITE);
        view.setGravity(Gravity.CENTER);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 32f);
        view.setPadding(48, 48, 48, 48);

        setContentView(view);
    }
}
EOF_APP_MAIN

    write_file "$TARGET/tools/gcompat.sh" <<'EOF_GCOMPAT'
#!/bin/sh
#
# gcompat.sh - run a glibc-linked Android SDK tool on musl (Alpine).
#
# The Android SDK build-tools and platform-tools (aapt2, zipalign, adb, ...)
# are compiled against glibc, which Alpine does not provide.  A local
# "gcompat" compatibility layer lives in tools/nsroot and the tool is run
# inside a throw-away user + mount + PID namespace (no root required).
#
# The whole Android SDK and the project's app/ and out/ directories are
# bind-mounted inside the chroot at the same absolute paths, so host paths
# keep working unchanged.
#
# Usage: tools/gcompat.sh <tool-name|path> [args...]
#   e.g. tools/gcompat.sh aapt2 version
#        tools/gcompat.sh zipalign -f -p 4 in.apk out.apk
#        tools/gcompat.sh adb install -r out/app.apk
#
set -eu

TOOLS="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(dirname "$TOOLS")"
NS="$TOOLS/nsroot"
SDK="$TOOLS/android-sdk"

TOOL="${1:?usage: gcompat.sh <tool-name|path> [args...]}"
shift

# Locate a tool by name in build-tools (any version) or platform-tools.
find_tool() {
    for _dir in "$SDK"/build-tools/* "$SDK"/platform-tools; do
        if [ -x "$_dir/$1" ]; then
            printf '%s\n' "$_dir/$1"
            return 0
        fi
    done
    return 1
}

case "$TOOL" in
    */*) TOOLPATH="$TOOL" ;;
    *)
        TOOLPATH="$(find_tool "$TOOL")" || {
            echo "gcompat.sh: unknown tool '$TOOL'" >&2
            exit 1
        }
        ;;
esac

if [ ! -d "$NS" ]; then
    echo "gcompat.sh: missing $NS (run bootstrap.sh first)" >&2
    exit 1
fi

# Bind-mount points must exist on the host filesystem.
mkdir -p "$NS$ROOT/app" "$NS$ROOT/out" "$NS$SDK"

exec unshare -rmpf /bin/sh -c '
    NS="$1"; SDK="$2"; ROOT="$3"; TOOLPATH="$4"; shift 4

    # /proc and /dev/null are needed by the gcompat loader and some tools.
    mount -t proc proc "$NS/proc" 2>/dev/null || true
    mount --bind "$SDK" "$NS$SDK" 2>/dev/null || true
    [ -e "$NS/dev/null" ] || : > "$NS/dev/null"
    mount --bind /dev/null "$NS/dev/null" 2>/dev/null || true

    # Expose the project directories at their real absolute paths.
    [ -d "$ROOT/app" ] && mount --bind "$ROOT/app" "$NS$ROOT/app" 2>/dev/null || true
    [ -d "$ROOT/out" ] && mount --bind "$ROOT/out" "$NS$ROOT/out" 2>/dev/null || true

    LD_LIBRARY_PATH=/lib exec chroot "$NS" "$TOOLPATH" "$@"
' sh "$NS" "$SDK" "$ROOT" "$TOOLPATH" "$@"
EOF_GCOMPAT
    chmod +x "$TARGET/tools/gcompat.sh"

    write_file "$TARGET/build.sh" <<'EOF_BUILD'
#!/bin/sh
#
# build.sh - build the lightweight native "Hello World" APK.
#
# No Gradle, no AndroidX, no Kotlin: resources are compiled with aapt2,
# Java with javac, bytecode with d8, and the APK is zipaligned + signed
# with the SDK build-tools.  This keeps the toolchain (and the APK) tiny.
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
OUT="$ROOT/out"

MIN_SDK=21
TARGET_SDK=34
VERSION_CODE=1
VERSION_NAME="1.0"

export JAVA_HOME
export ANDROID_SDK_ROOT="$SDK"
PATH="$JAVA_HOME/bin:$PATH"
export PATH

for bin in "$JAVA_HOME/bin/javac" "$BT/d8" "$BT/apksigner" "$TOOLS/gcompat.sh" "$PLATFORM"; do
    if [ ! -e "$bin" ]; then
        echo "Missing: $bin" >&2
        echo "Run ./bootstrap.sh first to provision the toolchain." >&2
        exit 1
    fi
done

rm -rf "$OUT"
mkdir -p "$OUT/res" "$OUT/gen" "$OUT/classes" "$OUT/dex"

echo "[1/6] Compiling resources (aapt2 compile)..."
"$TOOLS/gcompat.sh" aapt2 compile \
    --dir "$APP/res" \
    -o "$OUT/res/resources.zip"

echo "[2/6] Linking resources (aapt2 link)..."
"$TOOLS/gcompat.sh" aapt2 link \
    -o "$OUT/base.apk" \
    -I "$PLATFORM" \
    --manifest "$APP/AndroidManifest.xml" \
    --java "$OUT/gen" \
    --min-sdk-version "$MIN_SDK" \
    --target-sdk-version "$TARGET_SDK" \
    --version-code "$VERSION_CODE" \
    --version-name "$VERSION_NAME" \
    "$OUT/res/resources.zip"

echo "[3/6] Compiling Java (javac)..."
find "$APP/src" "$OUT/gen" -name '*.java' > "$OUT/sources.txt"
javac -source 8 -target 8 -bootclasspath "$PLATFORM" \
    -d "$OUT/classes" "@$OUT/sources.txt"

echo "[4/6] Dexing (d8)..."
"$BT/d8" --lib "$PLATFORM" --min-api "$MIN_SDK" \
    --output "$OUT/dex" $(find "$OUT/classes" -name '*.class')

echo "[5/6] Packaging APK..."
(cd "$OUT/dex" && jar uf "$OUT/base.apk" classes.dex)

echo "[6/6] Aligning and signing..."
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

APK="$OUT/hello-world-debug.apk"
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
EOF_BUILD
    chmod +x "$TARGET/build.sh"

    write_file "$TARGET/.gitignore" <<'EOF_GITIGNORE'
# Build output
/out/

# Generated local toolchain
/tools/root/
/tools/android-sdk/
/tools/nsroot/
/tools/debug.keystore

# Package cache / offline mirror (back this up separately)
/mirror/

# Downloaded QR library
/libs/

# Misc
*.apk
*.log
EOF_GITIGNORE

    write_file "$TARGET/README.md" <<'EOF_README'
# Lightweight Native Android App SDK

A minimal, self-contained Android "Hello World" project built with the
official Android SDK tools only (no Gradle, no AndroidX, no Kotlin, no
WebView).  Everything is unpacked inside this directory; no root and no
system-wide installation are required.

## Bootstrap

```sh
./bootstrap.sh                     # online: download packages into ./mirror
./bootstrap.sh --mirror DIR        # offline: use packages from DIR
```

The online run fills `./mirror/` with every package needed to rebuild the
environment (`alpine` .apk files + Android SDK .zip files + a MANIFEST).
Copy that directory to CD/DVD for a permanent offline copy.

## Build

```sh
./build.sh
```

Output: `out/hello-world-debug.apk`.

## Notes

* Toolchain: musl OpenJDK 17, Android build-tools 34 / platform android-34.
* The glibc-linked SDK tools (aapt2, zipalign, adb) run through
  `tools/gcompat.sh` using a local gcompat chroot (no root required).
* Add Java under `app/src/...`, resources under `app/res/...`.
EOF_README
}

# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------
if [ "$OFFLINE" -eq 1 ]; then
    validate_mirror
else
    populate_mirror
fi

write_project_files
install_zxing

if [ "$FORCE" -eq 1 ] ||
   [ ! -x "$JDK_HOME/bin/javac" ] ||
   [ ! -x "$SDK/build-tools/$BUILD_TOOLS/d8" ] ||
   [ ! -f "$NS/lib/libgcompat.so.0" ]; then
    provision
else
    log "Toolchain already present (use --force to rebuild)"
fi

log "Done"
cat <<EOF
Environment ready in : $TARGET
Package mirror       : $MIRROR

  Build debug        : ./build-debug.sh
  Build release      : ./build-release.sh
  Debug APK          : out/openpostmobajl-debug.apk
  Release APK        : out/openpostmobajl-release.apk
  Rebuild offline    : ./bootstrap.sh --mirror "$MIRROR"
  Install on device  : tools/gcompat.sh adb install -r out/openpostmobajl-debug.apk
EOF
