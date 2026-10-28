#!/usr/bin/env bash
# Solo CI/emulador: estas pruebas usan datos públicos/ficticios, nunca claves ni conversaciones del usuario.
set -u
adb logcat -c
result=0
# Dos procesos de instrumentación: los casos de denegación conservan su permiso
# denegado; los casos de captura se ejecutan después con concesión explícita de CI.
# Todos los casos se ejecutan; se conservan ambos XML antes de la siguiente tarea.
capture_class=com.pbtservers.pbtmetagpt.MicrophoneServiceInstrumentedTest
package=com.pbtservers.pbtmetagpt
bash ./gradlew --no-daemon connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.notClass="$capture_class" || result=$?
mkdir -p app/build/reports/androidTests/sin-microfono
if [ -d app/build/outputs/androidTest-results/connected ]; then
  cp -R app/build/outputs/androidTest-results/connected/. app/build/reports/androidTests/sin-microfono/
fi
# Gradle desinstala la APK al terminar un bloque. GrantPermissionRule concede
# exclusivamente en la clase de captura después de la instalación del segundo.
bash ./gradlew --no-daemon connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class="$capture_class" || result=$?
mkdir -p app/build/reports/androidTests/con-microfono
if [ -d app/build/outputs/androidTest-results/connected ]; then
  cp -R app/build/outputs/androidTest-results/connected/. app/build/reports/androidTests/con-microfono/
fi
if adb shell pm path "$package" | grep -q '^package:'; then
  adb shell pm revoke "$package" android.permission.RECORD_AUDIO || result=1
  adb shell pm revoke "$package" android.permission.POST_NOTIFICATIONS || result=1
fi
if adb shell dumpsys activity services "$package" | grep -E 'ServiceRecord.*ContinuousWakeService'; then
  echo 'El servicio no se ha liberado después de las pruebas y la retirada de permiso.'
  result=1
fi
# Conservar las métricas sintéticas también si falla otra prueba de la suite.
mkdir -p app/build/reports/androidTests
adb logcat -d -s PbtKokoroTest:I '*:S' > app/build/reports/androidTests/kokoro-metricas-emulador.txt
cat app/build/reports/androidTests/kokoro-metricas-emulador.txt
if [ "$result" -ne 0 ]; then
  echo "Diagnóstico Android y nativo tras el fallo de las pruebas controladas:"
  adb logcat -d -s PbtStart:I PbtVoice:I PbtModelo:E sherpa-onnx:V AndroidRuntime:E libc:E linker:E DEBUG:V || true
fi
exit "$result"
