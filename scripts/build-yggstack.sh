#!/bin/bash
set -e

YGGSTACK_DIR="$(cd "$(dirname "$0")/../vendor/yggstack" && pwd)"
OUT_DIR="$(cd "$(dirname "$0")/../libs" && pwd)"

echo "Building yggstack AAR (arm64 only)..."
cd "$YGGSTACK_DIR"

export PATH="$(go env GOPATH)/bin:$PATH"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"

mkdir -p android-build

gomobile bind \
    -target=android/arm64 \
    -androidapi=21 \
    -javapkg=link.yggdrasil.yggstack \
    -ldflags="-checklinkname=0" \
    -o=android-build/yggstack.aar \
    ./mobile/

cp "$YGGSTACK_DIR/android-build/yggstack.aar" "$OUT_DIR/yggstack.aar"
echo "Copied yggstack.aar to $OUT_DIR"
