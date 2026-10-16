#!/usr/bin/env bash
# Assembles the app APK inside an x86_64 container: the ARM64 host cannot run
# the SDK's x86_64 aapt2 natively.
set -euo pipefail
docker run --rm \
  -v /root/fitbit-air-app:/work \
  -v /opt/android-sdk:/opt/android-sdk \
  -v /root/.gradle:/root/.gradle \
  -w /work \
  -e JAVA_HOME=/opt/java/openjdk \
  eclipse-temurin:17-jdk \
  bash -lc "./gradlew ${1:-:app:assembleDebug} --no-daemon --max-workers=1"
