# Voz local y escucha · candidata 0.5.0-rc1

**Actualización 30/10/2026:** por aclaración del usuario, Natural ofrece Davefx y **Sharvard masculina/femenina**, las tres Piper `es_ES`. Kokoro pasa a Avanzada conservando perfiles. Sharvard comparte 76,7 MB y una sesión ONNX entre hablantes; JSON, MODEL_CARD y hashes quedan fijados. La [guía actual de voces y recursos](CANDIDATA_RAYBAN.md#voces-de-españa-solicitadas) sustituye las rutas de instalación descritas en el historial inferior. CI `9939b34` acredita PCM distintos con el mismo texto, persistencia, reutilización y muestra reproducida en API35/API36; sus dos fallos en otras pruebas siguen impidiendo publicar APK. No acredita naturalidad/latencia física; véase el resultado del SHA final en [PROGRESO.md](PROGRESO.md).

Esta intervención parte de `e263ec42bda840c5cfb6427c945f562d57a086d9`, exclusivamente en `master`. El código `b74fab9` **compila y supera JVM/Lint/escáner y 37/37 instrumentadas en cada API 35/36**: [ejecución verificada](https://github.com/PbtServers/PbtMetaGPT/actions/runs/40691905261). Kokoro y Davefx con pesos auténticos generan PCM; la biblioteca reproduce una muestra Kokoro sin claves ni micrófono. Hay medidas de emulador en [PROGRESO.md](PROGRESO.md), no de hardware. La aceptación con el Nothing/Ray-Ban y la revisión de distribución siguen pendientes. Se conserva la versión candidata.

## Idioma y acento

El requisito confirmado es **español de España**, no solamente texto en español. Por ello Natural prioriza **Piper Davefx es_ES medium**, cuya [ficha de origen](https://huggingface.co/rhasspy/piper-voices/blob/v1.0.0/es/es_ES/davefx/medium/MODEL_CARD) identifica España y datos CC0. La calidad declarada por el proveedor es medium; no se afirma que supere a Kokoro ni que suene natural en las gafas sin escucharlo.

Kokoro conserva Dora, Alex y Santa como alternativas locales. Su [catálogo oficial](https://huggingface.co/hexgrad/Kokoro-82M/blob/main/VOICES.md) documenta español, sin acreditar una variante peninsular. Cambiar el fonemizador a `es` no demuestra el acento de la voz. No se etiquetan como `es-ES` ni se sustituyen automáticamente por ellas las voces de España. Qwen remoto informa también del idioma de referencia de sus voces y no garantiza acento peninsular.

Rápida ofrece Android TTS, separado de los motores anteriores. Con preferencia `es-ES`, la selección automática y el catálogo rápido exigen esa variante; no sustituyen silenciosamente una voz de España por `es-MX`. Los perfiles anteriores conservan su selección. Los demás idiomas siguen configurables.

## Instalar y utilizar

1. Abrir **Voces → Natural** y seleccionar Davefx (España). La selección válida se guarda; no reproduce ni descarga nada.
2. Pulsar **Descargar voz de España**, tras consultar tamaño, espacio y licencia. Se verifica SHA-256 antes de publicar la instalación privada. Cancelar borra el directorio temporal. Un fallo permite reintentar.
3. Pulsar **Escuchar muestra**. Comprobar audiblemente pronunciación, acento y ruta. Comprobar/eliminar el modelo está disponible en la misma pantalla.
4. Para Kokoro, pulsar **Otras voces locales · Kokoro**, elegir voz y descargar su modelo. No utiliza Android TTS como sustituto.

Natural y Rápida no transmiten texto para sintetizar. **Avanzada** conserva Qwen remoto con consentimiento independiente, RVC ONNX, importaciones, Piper/VITS anterior y proveedores opcionales. Cambiar de categoría no ejecuta una muestra. No se descargan pesos al abrir la aplicación.

## Recursos y procedencia

| Componente | Fuente/contrato | Descarga y licencia |
| --- | --- | --- |
| Davefx | `rhasspy/piper-voices`, `v1.0.0/es/es_ES/davefx/medium` | 63.201.294 bytes; repositorio MIT, dataset CC0 según ficha |
| Kokoro v1.0 | `csukuangfj/kokoro-multi-lang-v1_0`, revisión `f7b96bb6bef5c5da4d3aa4f4e0498fbbf62dc78b` | Modelo 325.560.556 + voces 28.200.960 bytes; Apache 2.0 |
| Fonemas | eSpeak NG 1.52.0, revisión `4870adfa25b1a32b4361592f1be8a40337c58d6c` | GPL-3.0-or-later; solo cálculo de IPA, sin audio eSpeak |
| Inferencia Kokoro | Sherpa-ONNX 2.0.1 existente | Apache 2.0; AAR y SHA ya fijados en `preparar_sherpa.py` |
| Inferencia Davefx/RVC | ONNX Runtime Android 1.30.0 existente | MIT; CPU ARM64 y x86_64 |

SHA-256 exigidos:

```text
Davefx es_ES-davefx-medium.onnx:
6658b03b1a6c316ee4c265a9896abc1393353c2d9e1bca7d66c2c442e222a917
Kokoro model.onnx:
b40f62b166ac8164b0627ef48a0b358eda0985e272fb03ef5252e7206305da11
Kokoro voices.bin:
1c5a5b983d3d50d8586d437a51f3faa2da7919ce76a013c081e65671a3447c29
```

Los hashes proceden de los archivos LFS publicados: [Davefx](https://huggingface.co/rhasspy/piper-voices/blob/v1.0.0/es/es_ES/davefx/medium/es_ES-davefx-medium.onnx), [modelo Kokoro](https://huggingface.co/csukuangfj/kokoro-multi-lang-v1_0/blob/f7b96bb6bef5c5da4d3aa4f4e0498fbbf62dc78b/model.onnx) y [voces Kokoro](https://huggingface.co/csukuangfj/kokoro-multi-lang-v1_0/blob/f7b96bb6bef5c5da4d3aa4f4e0498fbbf62dc78b/voices.bin). No se cargan `.pth`, pickle, código ni bibliotecas procedentes de modelos importados.

eSpeak añade obligaciones GPL sobre su componente y el adaptador que lo enlaza. Se incluye su texto de licencia en los assets generados y se proporciona fuente/revisión y procedimiento de construcción. **No describir todos los binarios como MIT**. La distribución conjunta con DAT, sujeto a sus [términos propios](https://github.com/facebook/meta-wearables-dat-android/blob/main/LICENSE), requiere cerrar la revisión de compatibilidad de licencias antes de una publicación estable. Esta revisión no está acreditada por tests técnicos.

## Inferencia, recursos y cancelación

Davefx usa el contrato Piper publicado: fonemas IPA, IDs propios con BOS/padding/EOS, tensores `input`, `input_lengths`, `scales`, salida PCM mono 22.050 Hz. Kokoro usa `GenerationConfig.phonemeCodepoints`, su vocabulario y tabla de 54 voces; Dora=28, Alex=29, Santa=53. Genera PCM mono 24 kHz. No se confunden las dos tablas de símbolos.

Se compila un adaptador eSpeak con el NDK para ARM64/x86_64. Sherpa 2.0.1 ya no incorpora eSpeak: su campo `dataDir` está obsoleto; no basta apuntarlo a unos datos para obtener fonemización. No se añaden otro ONNX Runtime, servidor ni API local de red.

Dos hilos de inferencia, un modelo conservado durante la sesión y liberación tras 30 segundos sin turnos o al detener. Las llamadas pesadas van a `Dispatchers.Default/IO`. Cancelar Piper solicita terminar `RunOptions`; Kokoro comprueba cancelación en su callback JNI explícito `@Keep`. Un mutex impide liberar una sesión mientras genera audio. Los perfiles conservan motor, voz, modelo y sensibilidad.

Los mínimos de espacio libre son reservas de instalación, **no mediciones de RAM**: 256 MiB para Davefx y 750 MiB para Kokoro. Los pesos ocupan aproximadamente 60,3 y 337,4 MiB respectivamente, más datos del fonemizador. Hay comprobaciones de presión de memoria; siguen pendientes PSS máximo, consumo, calentamiento y estabilidad en el Nothing. No hay inferencias periódicas mientras el TTS está inactivo.

La separación nativa existente mantiene `libpbt_sherpa_ort.so` para Sherpa y `libonnxruntime.so` para Davefx/RVC. No utilizar `pickFirst` para ocultar conflictos. La conversión RVC permanece optativa, apagada por defecto en instalaciones nuevas. Se reutilizan sus sesiones durante la respuesta y se divide PCM largo para respetar el límite nativo; las uniones y la calidad requieren escucha física. FAISS `.index` sigue sin intervenir en la inferencia.

## Activación y preguntas

Inicio permite activar una ventana Android exclusivamente on-device sin instalar Vosk. Navegar o salir revoca esa escucha. El servicio Vosk existente sigue disponible expresamente en opciones avanzadas, con notificación y parada. No se activa al conceder permisos ni al volver al primer plano.

Sensibilidad de coincidencia: Baja comprueba la primera hipótesis exacta; Normal, tres; Alta, cinco y una diferencia en la última palabra larga. No es ganancia del micrófono ni verificación de identidad. Las hipótesis que apuntan a asistentes distintos se rechazan. Se mantienen normalización, frases de sesgo y múltiples frases. Silencios/sin coincidencia se rearman durante la ventana autorizada, sin el antiguo límite de ocho; errores transitorios tienen retroceso acotado. Los callbacks antiguos se invalidan antes de rearmar.

Si el resultado final contiene «Atento Luna, ¿qué hora es?», se conserva la pregunta y no se abre otra captura. Este uso por prefijo exige al menos dos palabras exactas de activación y comienzo de frase; no busca dentro de citas o conversaciones. Con activación y pregunta separadas sigue existiendo un relevo entre reconocedores: el búfer de la pregunta no puede recuperar audio captado antes de abrir su micrófono. **La ausencia de pérdidas en ese relevo no está validada**.

La captura propia Vosk/Whisper usa tramas de 20 ms, ruido de fondo adaptativo, ataque de 60 ms, búfer previo de 300 ms y silencio final de 1.100 ms. Un chasquido aislado no cuenta como pregunta. Este VAD energético no equivale a un detector neuronal ni identifica a otras personas. Android on-device conserva su VAD interno; los extras de pausas son sugerencias que el motor puede ignorar. Dictado elige entre hipótesis con confianza válida. Se puede detener desde la conversación.

La ruta Bluetooth se solicita antes de escuchar/sintetizar; se utiliza fuente de comunicación para la captura propia cuando corresponde. `SpeechRecognizer` decide su entrada efectiva: el diagnóstico distingue dispositivo solicitado de entrada no acreditada. Solo muestra transcripción/coincidencia de la prueba en memoria y la borra al cerrar el diagnóstico; no guarda audio ni texto en logs.

## Motores evaluados

**openWakeWord:** su [proyecto oficial](https://github.com/dscripka/openWakeWord) combina extracción mel, embeddings y un clasificador entrenado. Un campo de texto no crea un detector para cualquier frase. Código Apache 2.0; pesos incluidos CC BY-NC-SA 4.0, con restricciones diferentes. No se integra un clasificador español sin entrenamiento y pruebas. Para preparar uno localmente: seguir el entrenamiento oficial en un equipo propio, usar positivos autorizados en español y negativos de ruido/conversación/TTS, exportar clasificador y frontend compatibles a ONNX, conservar hashes/licencias y medir falsos positivos/negativos con grabaciones locales. **La importación/ejecución openWakeWord en esta app sigue pendiente**; no seleccionar un ONNX arbitrario como si funcionara.

**Reconocimiento:** Android on-device en es-ES es la primera ruta; Vosk small-es-0.42 sigue como alternativa sin red y con vocabulario limitado. Según su [catálogo](https://alphacephei.com/vosk/models), los modelos pequeños se orientan a móvil; sus cifras de memoria no son mediciones de esta app. Whisper multilingüe mediante Sherpa/whisper.cpp es candidato para dictado español más preciso en ARM64, pero exige descargar pesos, frontend, evaluar latencia y RAM. No se añade una supuesta selección operativa sin ese trabajo. Groq Whisper Turbo sigue separado y solo con consentimiento para enviar la pregunta, nunca como detector de activación.

**Qwen3-TTS 0.6B:** el [modelo oficial](https://huggingface.co/Qwen/Qwen3-TTS-12Hz-0.6B-CustomVoice) es multilingüe, Apache 2.0. Además del ejemplo PyTorch/CUDA, se ha localizado una implementación independiente concreta: [Qwen3 TTS Android](https://github.com/Danmoreng/qwen3-tts-android), MIT, integra por JNI [qwen3-tts.cpp](https://github.com/Danmoreng/qwen3-tts.cpp) y GGML. Su README requiere Android 12+, ARM64, NDK y dos GGUF Q4_K_M: talker 0.6B Base y tokenizer 12 Hz. No necesita un servidor localhost. No se ha integrado ni ejecutado en PbtMetaGPT: debe fijarse una revisión, verificar los hashes y licencias de sus pesos/dependencias y probar la coexistencia nativa antes de habilitarlo.

El [CMake Android del autor](https://github.com/Danmoreng/qwen3-tts-android/blob/main/app/src/main/cpp/CMakeLists.txt) utiliza CPU y deja Vulkan/OpenCL apagados por defecto. Existen opciones de compilación para esos backends; esto no verifica aceleración GPU en el Nothing, y no se ha encontrado allí integración NNAPI. Los benchmarks del runtime se declaran Windows/CUDA con RTX 5080 Laptop, por lo que no se trasladan al móvil. Conviene medir TTFS, segundos de generación por segundo de audio, PSS máximo y sostenido, batería y temperatura con CPU antes de estudiar GPU.

La cifra «0.6B» no cubre necesariamente todos los recursos: la ficha oficial consultada muestra aproximadamente 0,9B parámetros. A 4 bits, 0,9B pesos representarían unos 450 MB antes de escalas, codec, cachés y buffers: **cuenta teórica, no tamaño de descarga ni RAM medidos**. El paquete ARM64 localizado es una vía viable para investigar, no una prueba de velocidad suficiente. Español, acento peninsular, TTFS, RTF, RAM y calentamiento del Nothing siguen sin medir. Se mantiene Kokoro/Piper como ruta local y Qwen remoto como opción avanzada claramente etiquetada.

## Streaming, métricas y pruebas

Groq conserva SSE incremental. Se generan frases antes de recibir toda la respuesta, con límite por espacios si no hay puntuación; no se cortan palabras. Un fragmento PCM anticipado y otro en reproducción. RVC agrupa frases locales desde 80 caracteres, manteniendo el límite por palabras; no acumula todo el resto de una respuesta en una sola inferencia. RVC/servicio remoto pueden aumentar la espera. `END_OF_SPEECH` mide la notificación/fin de captura, `TRANSCRIPTION` la espera restante, `GROQ_FIRST_TOKEN` la espera de Groq y `TTS_FIRST_PCM` disponibilidad PCM; `FIRST_AUDIO_FRAME` sigue siendo avance de AudioTrack, **no sonido físico**. Los eventos de etapas concurrentes no se suman. Latencia de activación acústica y primer audio audible todavía requieren medida externa.

CI conserva Lint y pruebas anteriores; prepara pesos auténticos y ambas APIs 35/36. Las nuevas instrumentadas cubren instalación, corrupción conservando tamaño, salida PCM no nula, persistencia, voces Kokoro, Davefx, liberación y carga nativa conjunta. Los logs de prueba solo contienen duración, tamaño PCM y PSS con texto sintético. Un grafo RVC sintético solo acredita su tubería técnica.

Para construir: JDK 17, SDK 36, NDK `28.2.13676358`, CMake `3.22.1`, CMake/compilador C del host y `patchelf`.

```bash
python tools/preparar_fonemizador.py
python tools/preparar_sherpa.py
./gradlew testDebugUnitTest lintDebug assembleDebug
python tools/preparar_modelo_prueba.py
python tools/preparar_kokoro_prueba.py  # también prepara Davefx; solo APK de tests
./gradlew connectedDebugAndroidTest
```

**Prueba física pendiente:** instalar APK del SHA validado; elegir Davefx, desactivar red y escuchar «En Zaragoza hay cinco plazas. ¿Podéis acercaros vosotros?». Comparar con Kokoro explícitamente, sin cambiar nombres de voces. Probar 20 activaciones a volumen normal, frase seguida de pregunta y con pausa, ruido, TTS propio y conversación ajena. Repetir entrada y salida por teléfono y por gafas por separado. Registrar solo números de aciertos/fallos, latencias fría/caliente y PSS, sin conversaciones ni grabaciones privadas. Probar cancelación, rotación, pérdida de Bluetooth, foto con confirmación y RVC activado/desactivado conservando modelos.

No hay mediciones acústicas ni aprobación física de esta intervención. El historial de accesos y los resultados locales y CI se mantienen en [PROGRESO.md](PROGRESO.md).
