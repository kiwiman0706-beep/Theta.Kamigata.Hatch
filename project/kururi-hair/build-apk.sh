#!/usr/bin/env bash
set -euo pipefail
PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
SDK_DIR="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [[ -z "$SDK_DIR" ]]; then
  echo "ANDROID_SDK_ROOT を Android SDK のパスに設定してください。" >&2
  exit 1
fi
TOOLS_DIR="$SDK_DIR/build-tools/${BUILD_TOOLS_VERSION:-35.0.0}"
ANDROID_JAR="$SDK_DIR/platforms/android-35/android.jar"
BUILD_DIR="$PROJECT_DIR/build"
OUTPUT_DIR="$PROJECT_DIR/dist"
VERSION_NAME="1.1.0"
VERSION_CODE="10100"
FINAL_APK="$OUTPUT_DIR/kururi-hair-$VERSION_NAME.apk"
mkdir -p "$BUILD_DIR/gen" "$BUILD_DIR/classes" "$BUILD_DIR/dex" "$OUTPUT_DIR"
"$TOOLS_DIR/aapt2" compile --dir "$PROJECT_DIR/app/src/main/res" -o "$BUILD_DIR/resources.zip"
"$TOOLS_DIR/aapt2" link -o "$BUILD_DIR/base.apk" --manifest "$PROJECT_DIR/app/src/main/AndroidManifest.xml" -I "$ANDROID_JAR" \
  --min-sdk-version 26 --target-sdk-version 35 --version-code "$VERSION_CODE" --version-name "$VERSION_NAME" --java "$BUILD_DIR/gen" -A "$PROJECT_DIR/app/src/main/assets" "$BUILD_DIR/resources.zip"
find "$PROJECT_DIR/app/src/main/java" "$BUILD_DIR/gen" -name '*.java' -print > "$BUILD_DIR/sources.txt"
java -m jdk.compiler/com.sun.tools.javac.Main -encoding UTF-8 -source 8 -target 8 -classpath "$ANDROID_JAR" -d "$BUILD_DIR/classes" @"$BUILD_DIR/sources.txt"
java -m jdk.jartool/sun.tools.jar.Main --create --file "$BUILD_DIR/classes.jar" -C "$BUILD_DIR/classes" .
if [[ -n "${KURURI_D8_JAR:-}" ]]; then
  java -cp "$KURURI_D8_JAR" com.android.tools.r8.D8 --min-api 26 --lib "$ANDROID_JAR" --output "$BUILD_DIR/dex" "$BUILD_DIR/classes.jar"
else
  "$TOOLS_DIR/d8" --min-api 26 --lib "$ANDROID_JAR" --output "$BUILD_DIR/dex" "$BUILD_DIR/classes.jar"
fi
cp "$BUILD_DIR/base.apk" "$BUILD_DIR/unsigned.apk"
(cd "$BUILD_DIR/dex" && zip -q "$BUILD_DIR/unsigned.apk" classes.dex)
"$TOOLS_DIR/zipalign" -P 16 -f 4 "$BUILD_DIR/unsigned.apk" "$BUILD_DIR/aligned.apk"
SIGNING_KEY="${KURURI_SIGNING_KEY:-$PROJECT_DIR/signing/kururi-personal.jks}"
SIGNING_PASS="${KURURI_SIGNING_PASSWORD:-}"
if [[ -z "$SIGNING_PASS" ]]; then
  echo "KURURI_SIGNING_PASSWORD を設定してください（値は非公開の SIGNING_INFO-ja.txt）。" >&2
  exit 1
fi
mkdir -p "$(dirname "$SIGNING_KEY")"
if [[ ! -f "$SIGNING_KEY" ]]; then
  if [[ "${KURURI_ALLOW_NEW_KEY:-}" != "1" ]]; then
    echo "署名鍵がありません: $SIGNING_KEY（新しい鍵では既存アプリを上書き更新できません。意図的なら KURURI_ALLOW_NEW_KEY=1）" >&2
    exit 1
  fi
  java -m java.base/sun.security.tools.keytool.Main -genkeypair -noprompt -keystore "$SIGNING_KEY" -storepass "$SIGNING_PASS" -keypass "$SIGNING_PASS" -alias kururi \
    -keyalg RSA -keysize 3072 -validity 10000 -dname "CN=Kururi Hair Personal App,O=Personal,C=JP"
fi
"$TOOLS_DIR/apksigner" sign --ks "$SIGNING_KEY" --ks-key-alias kururi --ks-pass "pass:$SIGNING_PASS" --key-pass "pass:$SIGNING_PASS" --out "$FINAL_APK" "$BUILD_DIR/aligned.apk"
"$TOOLS_DIR/apksigner" verify --verbose "$FINAL_APK"
echo "APK: $FINAL_APK"
