#!/usr/bin/env bash
# Solo CI/emulador: estas pruebas usan datos públicos/ficticios, nunca claves ni conversaciones del usuario.
set -u
adb logcat -c
result=0
bash ./gradlew --no-daemon connectedDebugAndroidTest || result=$?
# Conservar las métricas sintéticas también si falla otra prueba de la suite.
mkdir -p app/build/reports/androidTests
adb logcat -d -s PbtKokoroTest:I '*:S' > app/build/reports/androidTests/kokoro-metricas-emulador.txt
cat app/build/reports/androidTests/kokoro-metricas-emulador.txt
if [ "$result" -ne 0 ]; then
  echo "Diagnóstico Android y nativo tras el fallo de las pruebas controladas:"
  adb logcat -d -s PbtStart:I PbtVoice:I PbtModelo:E sherpa-onnx:V AndroidRuntime:E libc:E linker:E DEBUG:V || true
fi
exit "$result"
