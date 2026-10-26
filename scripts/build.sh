#!/usr/bin/env bash
# Build del navegador TV (com.omp.tvweb) y del lanzador (com.omp.tvbrowser).
# Requiere: Android SDK build-tools 35.0.0, JDK 17+.
#
#   ./build.sh            # compila tvweb.apk
#   ./build.sh install    # compila e instala por ADB en el TV
set -euo pipefail

SDK="${ANDROID_SDK:-$HOME/Android/Sdk}"
BT="$SDK/build-tools/35.0.0"
JAR="$SDK/platforms/android-35/android.jar"
export PATH="$BT:$PATH"

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$ROOT/src"
OUT="$ROOT/build"
APK="$ROOT/apk/tvweb.apk"

rm -rf "$OUT"
mkdir -p "$OUT/classes" "$OUT/dex" "$ROOT/apk"

cd "$SRC"

echo "[1/6] recursos"
aapt2 compile --dir res -o "$OUT/res.zip"

echo "[2/6] enlazando manifiesto"
aapt2 link -o "$OUT/base.apk" \
    -I "$JAR" \
    --manifest AndroidManifest.xml \
    -R "$OUT/res.zip" \
    --java "$OUT/gen" \
    --auto-add-overlay \
    --min-sdk-version 21 \
    --target-sdk-version 34

echo "[3/6] javac"
javac -source 8 -target 8 \
    -classpath "$JAR" \
    -d "$OUT/classes" \
    "$OUT/gen/com/omp/tvweb/R.java" src/com/omp/tvweb/*.java 2>&1 | grep -viE 'warning|options|bootstrap|deprecat|Note:' || true

echo "[4/6] dex"
d8 --lib "$JAR" --min-api 21 --output "$OUT/dex" "$OUT"/classes/com/omp/tvweb/*.class

echo "[5/6] empaquetando"
cp "$OUT/base.apk" "$OUT/unsigned.apk"
( cd "$OUT/dex" && zip -q "$OUT/unsigned.apk" classes.dex )
# aapt2 link does not copy a plain assets/ tree, so add it explicitly
if [ -d "$SRC/assets" ]; then
    ( cd "$SRC" && zip -q -r "$OUT/unsigned.apk" assets -x '.*' )
fi
zipalign -f 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"

echo "[6/6] firmando"
apksigner sign \
    --ks ks.jks --ks-pass pass:omp1234 --key-pass pass:omp1234 \
    --out "$APK" "$OUT/aligned.apk"
apksigner verify "$APK"

echo
echo "OK -> $APK"
ls -l "$APK"

if [ "${1:-}" = "install" ]; then
    adb -s "${TV_ADDR:-192.168.1.74:5555}" install -r "$APK"
fi
