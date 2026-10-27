# Guía para desarrolladores

## Organización

- `app/`: aplicación Android Kotlin/Jetpack Compose, perfiles, DAT, Realtime, audio, privacidad y pruebas.
- `backend/`: backend HTTPS opcional y pruebas Node.
- `deploy/`: despliegue opcional con contenedores/proxy.
- `tools/`: preparación verificada de Sherpa/modelo de prueba, empaquetado y comprobación de secretos.
- `docs/`: arquitectura, estado y protocolos de validación.
- `.github/workflows/android.yml`: CI Android y publicación de APK experimental.

## Requisitos y compilación

JDK 17, Android SDK 36 y Android Studio. Usa `local.properties` para la ruta SDK y, si corresponde, identificadores públicos de registro Meta DAT; no incluyas una clave OpenAI privada en el proyecto.

```bash
python tools/preparar_sherpa.py
./gradlew testDebugUnitTest lintDebug assembleDebug
node --test backend/*.test.mjs
python -m unittest discover -s tools -p 'test_*.py'
python tools/comprobar_secretos.py app/build/outputs/apk/debug/app-debug.apk
```

En Windows ejecuta `gradlew.bat`. Para `connectedDebugAndroidTest`, prepara la fixture con `python tools/preparar_modelo_prueba.py` y enciende un emulador compatible. La CI no acredita hardware real ni proveedores de pago.

## Ampliaciones y depuración

Antes de modificar el flujo de asistentes, lee [arquitectura](ARQUITECTURA.md) y revisa pruebas de perfiles y ciclo de sesión. Para incorporar un motor TTS, implementa el contrato de texto/PCM y cancelación; no reactives la pista Realtime de audio al usar TTS alternativo. Para importar voces sigue [biblioteca](BIBLIOTECA_DE_VOCES.md), no admitas código de un ZIP y revisa procedencia/licencia. Contrasta cambios de DAT con las muestras oficiales y registra pruebas de gafas por separado.

Los diagnósticos de la aplicación deben describir etapas y códigos sin secretos, transcripciones, fotos ni nombres privados. Para errores de CI consulta los logs de la ejecución del **mismo commit**. Para fallos reales sigue [PRUEBAS_REALES.md](PRUEBAS_REALES.md). Ver [CONTRIBUTING.md](../CONTRIBUTING.md).
