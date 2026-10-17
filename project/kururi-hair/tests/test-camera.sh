#!/usr/bin/env bash
set -euo pipefail
PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
TEST_DIR="$PROJECT_DIR/build/camera-tests"
mkdir -p "$TEST_DIR"
java -m jdk.compiler/com.sun.tools.javac.Main -encoding UTF-8 -d "$TEST_DIR" \
    "$PROJECT_DIR/app/src/main/java/jp/yaegaki/kururihair/CameraGeometry.java" \
    "$PROJECT_DIR/tests/CameraGeometryTest.java"
java -cp "$TEST_DIR" CameraGeometryTest
