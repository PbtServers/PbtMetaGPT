# Instrucciones de desarrollo de PbtMetaGPT

Aplicación Android Kotlin con Meta Wearables Device Access Toolkit y OpenAI Realtime. Mantén interfaz, documentación, errores y commits en español. El identificador es `com.pbtservers.pbtmetagpt`.

## Referencias canónicas

- Meta y muestras oficiales: https://github.com/facebook/meta-wearables-dat-android
- Complemento oficial opcional de Meta: `codex plugin marketplace add facebook/meta-wearables-dat-android` y `codex plugin add mwdat-android@mwdat-android-marketplace`.
- OpenAI Docs: https://developers.openai.com/api/docs/guides/realtime

## Prioridades

1. Contrasta las APIs con las muestras actuales de Meta: registro, modo de desarrollador, permisos, descubrimiento y ciclo de sesión/cámara. No inventes APIs ni deduzcas soporte físico de la existencia de un artefacto Maven.
2. Mantén conexión y desconexión explícitas, estados visibles, liberación de recursos y pruebas.
3. v0.4.0 admite BYOK por petición expresa del usuario: clave personal cifrada con AES-GCM y Android Keystore, advertencia explícita del riesgo de un cliente móvil, sin logs, copias ni exportación de credenciales. Conserva el backend como alternativa más segura, con autenticación y límites. Nunca incluyas claves de desarrollador en la APK, repositorio o CI.
4. Usa WebRTC para voz y rutas de comunicación Android cuando estén disponibles; muestra cuándo se utiliza el teléfono. Comprueba por separado la entrada y salida de audio con hardware real antes de declarar compatibilidad.
5. La cámara requiere consentimiento, permiso DAT y captura/vista previa explícitas. El envío a OpenAI exige una segunda confirmación. No subas vídeo ni captures permanentemente.
6. No actives sensores al volver del segundo plano, recibir permisos o reconectar. Conserva contexto solo durante una sesión autorizada y permite interrumpir, pausar y finalizar.
   La espera local puede reanudarse al finalizar una sesión iniciada por palabra de activación mientras se mantenga el consentimiento y la app visible. Navegar o salir revoca esa escucha; nunca se reanuda al volver a primer plano. Usa exclusivamente `createOnDeviceSpeechRecognizer`, sin fallback remoto; no prometas soporte de nombres o rutas no comprobados.
7. Publica la APK de CI como artifact únicamente después de pruebas y compilación correctas.

## Límites y validación

- Esta integración necesita pruebas físicas. Consulta `docs/VALIDACION.md` antes de describir funciones como verificadas.
- No afirmes acceso a «Hey Meta», escucha en segundo plano o soporte de `mwdat-speech` sin verificar las APIs y el dispositivo.
- ChatGPT Plus y la API de OpenAI tienen facturación independiente.
- No subas claves, tokens, firmas privadas, configuraciones personales, imágenes ni conversaciones.
- Usa Java 17 y ejecuta `./gradlew testDebugUnitTest lintDebug assembleDebug` y `node --test backend/server.test.mjs`.
- En v0.4.0 ejecuta también los tests Python y el escáner `tools/comprobar_secretos.py` sobre fuentes y APK. `connectedDebugAndroidTest` comprueba almacenamiento/Keystore, bienvenida y denegación de permiso en emulador; no acredita audio físico, reconocimiento hablado ni gafas.
- Compara el manifiesto con las muestras de Meta. Registra por separado las comprobaciones automáticas, emulador, Mock Device Kit y gafas reales. Un estado simulado no acredita hardware.
