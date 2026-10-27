# Instrucciones de desarrollo de PbtMetaGPT

Aplicación Android Kotlin con Meta Wearables Device Access Toolkit y OpenAI Realtime. Mantén interfaz, documentación, errores y commits en español. El identificador es `com.pbtservers.pbtmetagpt`.

## Referencias canónicas

- Meta y muestras oficiales: https://github.com/facebook/meta-wearables-dat-android
- Complemento oficial opcional de Meta: `codex plugin marketplace add facebook/meta-wearables-dat-android` y `codex plugin add mwdat-android@mwdat-android-marketplace`.
- OpenAI Docs: https://developers.openai.com/api/docs/guides/realtime

## Prioridades

Consulta `docs/PROGRESO.md` al comenzar. Publica commits de desarrollo descriptivos en español y push a `master` por cada bloque significativo, también si es parcial y está desactivado de forma segura. No uses force push. Actualiza el seguimiento con evidencias, pendientes y próxima tarea. Distingue commit experimental, APK de prueba y Release estable; no declares terminado el objetivo mientras falte validación obligatoria.

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
- Ampliación 0.4.1-dev: antes de Gradle ejecuta `python tools/preparar_sherpa.py`; antes del emulador prepara `tools/preparar_modelo_prueba.py`. No versiones AAR, modelos descargados, WAV ni APK. No omitas la prueba del modelo por fallar: corrige la causa o deja ese motor experimental/desactivado con estado explícito. La fixture de inferencia se limita a la APK de tests y no acredita escucha.
- Consulta el contrato real de cada motor. Sherpa 2.0.1 identifica Piper por `comment`, `sample_rate`, `n_speakers` y `language`; no inventes campos. Solo paquetes de datos del formato documentado, sin código, con límites/hash/procedencia/licencia y ejecución en UID aislado. No distribuyas modelos de actores sin autorización ni presupongas que una etiqueta MIT resuelve los derechos de sus datos de entrenamiento.
- Para TTS alternativo exige salida textual de Realtime, deshabilita audio nativo y conserva una sola cola cancelable. Mantén secretos de proveedores en Keystore separado y fuera de perfiles. No muestres sensibilidad acústica, tono o expresividad si el adaptador no los implementa.
- Publica avances por subfase con `docs/PROGRESO.md`, pesos 25/15/20/20/10/10, tiempos medidos y estimaciones explícitas. No muestres 100 % ni Release estable mientras quede aceptación obligatoria pendiente.
- Compara el manifiesto con las muestras de Meta. Registra por separado las comprobaciones automáticas, emulador, Mock Device Kit y gafas reales. Un estado simulado no acredita hardware.
