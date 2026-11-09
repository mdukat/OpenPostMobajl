#!/bin/sh
#
# build.sh - convenience wrapper for the debug build.
#
exec "$(cd "$(dirname "$0")" && pwd)/build-debug.sh" "$@"
