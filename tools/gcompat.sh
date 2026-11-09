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
