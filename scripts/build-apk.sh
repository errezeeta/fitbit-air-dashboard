#!/usr/bin/env bash
# Assembles the app APK inside an x86_64 container: the ARM64 host cannot run
# the SDK's x86_64 aapt2 natively.
#
# After an app build it greps the APK's dex for the backend URL. A release once
# shipped with an empty BASE_URL and quietly showed sample data, so the build
# now fails instead of producing an update that talks to nothing.
set -euo pipefail

TASKS=${1:-:app:assembleDebug}
APK=/root/fitbit-air-app/app/build/outputs/apk/debug/app-debug.apk

docker run --rm \
  -v /root/fitbit-air-app:/work \
  -v /opt/android-sdk:/opt/android-sdk \
  -v /root/.gradle:/root/.gradle \
  -w /work \
  -e JAVA_HOME=/opt/java/openjdk \
  eclipse-temurin:17-jdk \
  bash -lc "./gradlew $TASKS --no-daemon --max-workers=1"

if [[ "$TASKS" == *assembleDebug* ]]; then
  python3 - "$APK" <<'PY'
import sys, zipfile, hashlib
apk = sys.argv[1]
url = b"fitbit-air-backend-i3pz5b53aq-no.a.run.app"
with zipfile.ZipFile(apk) as z:
    found = any(url in z.read(name) for name in z.namelist() if name.endswith(".dex"))
    digest = hashlib.sha256(open(apk, "rb").read()).hexdigest()[:16]
if not found:
    raise SystemExit(f"FALLO: {apk} no contiene la URL del backend; la app usaría datos de muestra")
print(f"OK: {apk} lleva la URL del backend (sha256 {digest})")
PY
fi
