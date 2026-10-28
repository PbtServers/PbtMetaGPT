#!/usr/bin/env bash
# Solo CI/emulador: estas pruebas usan datos públicos/ficticios, nunca claves ni conversaciones del usuario.
set -u
adb logcat -c
if bash ./gradlew --no-daemon connectedDebugAndroidTest; then
  mkdir -p app/build/reports/androidTests
  adb logcat -d -s PbtKokoroTest:I '*:S' > app/build/reports/androidTests/kokoro-metricas-emulador.txt
  exit 0
else
  result=$?
  echo "Diagnóstico Android y nativo tras el fallo de las pruebas controladas:"
  adb logcat -d -s PbtStart:I PbtVoice:I PbtModelo:E sherpa-onnx:V AndroidRuntime:E libc:E linker:E DEBUG:V || true
  exit "$result"
fi
