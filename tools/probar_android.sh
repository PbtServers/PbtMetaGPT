#!/usr/bin/env bash
# Solo CI/emulador: estas pruebas usan datos públicos/ficticios, nunca claves ni conversaciones del usuario.
set -u
adb logcat -c
if bash ./gradlew --no-daemon connectedDebugAndroidTest; then
  exit 0
else
  result=$?
  echo "Diagnóstico nativo de la fixture pública tras el fallo:"
  adb logcat -d -s PbtModelo:E sherpa-onnx:V AndroidRuntime:E libc:E linker:E DEBUG:V || true
  exit "$result"
fi
