#!/bin/sh
set -eu

PROJECT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
BUILD_DIR="$PROJECT_DIR/.build"

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
