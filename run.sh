#!/bin/sh
set -eu

PROJECT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
BUILD_DIR="$PROJECT_DIR/.build"

if ! command -v javac >/dev/null 2>&1 || ! command -v java >/dev/null 2>&1; then
  echo "RELASI AI requires a JDK 25 installation (both javac and java must be on PATH)." >&2
  exit 1
fi

JAVAC_VERSION=$(javac -version 2>&1 | awk '{print $2}')
JAVA_VERSION=$(java -version 2>&1 | awk -F '"' 'NR == 1 {print $2}')
case "$JAVAC_VERSION:$JAVA_VERSION" in
  25*:25*) ;;
  *)
    echo "RELASI AI requires JDK 25; found javac $JAVAC_VERSION and java $JAVA_VERSION." >&2
    echo "Install JDK 25 and make its bin directory first on PATH, then rerun ./run.sh." >&2
    exit 1
    ;;
esac

CRM_PROFILE=$(printf '%s' "${RELASI_CRM_ADAPTER:-DYNAMICS}" | tr '[:lower:]' '[:upper:]')
case "$CRM_PROFILE" in
  DYNAMICS|SALESFORCE) export RELASI_CRM_ADAPTER="$CRM_PROFILE" ;;
  *)
    echo "Invalid RELASI_CRM_ADAPTER '$CRM_PROFILE'; choose DYNAMICS or SALESFORCE." >&2
    exit 1
    ;;
esac

cd "$PROJECT_DIR"
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR/classes"
find src/main/java -name '*.java' -print > "$BUILD_DIR/sources.list"

javac --release 25 --add-modules jdk.httpserver \
  -d "$BUILD_DIR/classes" \
  @"$BUILD_DIR/sources.list"

if [ -d src/main/resources ]; then
  cp -R src/main/resources/. "$BUILD_DIR/classes/"
fi

exec java --add-modules jdk.httpserver \
  -cp "$BUILD_DIR/classes" \
  com.pswied.core.RelasiAiApplication
