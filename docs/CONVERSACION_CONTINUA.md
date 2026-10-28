# Conversación continua y ChatGPT oficial · experimental

Intervención del 28/10/2026 sobre `master`. No es una Release estable. Las pruebas de lógica, emulador y acústica se registran por separado en PROGRESO.md.

## Auditoría y alcance

La base `879fd8f` ya contiene exclusión global de micrófono (`MicrophoneGate`), servicio privado `microphone`, Vosk español 0.42, dictado on-device, WebRTC, rutas Android, audio PCM cancelable y voces Groq/Kokoro/Piper/Qwen/Android/RVC. El controlador de Groq desactivaba una sesión iniciada con activación después de cada turno; además confirmaba la respuesta en el contexto antes de terminar la reproducción. Se reutilizan estos componentes.

La ampliación conecta una autoridad de conversación y generaciones con GroqCloud. Una sesión hablada vuelve a `FOLLOW_UP_LISTENING` después de liberar salida y detector. El contexto permanece en RAM, conserva cinco turnos y está limitado a 32 mensajes/15 000 caracteres; las instrucciones permanecen en un mensaje de sistema separado. Una respuesta interrumpida no se añade como respuesta completa al contexto. La UI conserva el fragmento marcado como interrumpido.

Estados: `IDLE`, `WAKE_LISTENING`, `USER_LISTENING`, `PROCESSING`, `SPEAKING`, `FOLLOW_UP_LISTENING`, `INTERRUPTING`, `SUSPENDED`, `STOPPED`. La espera de activación continúa bajo la política existente del servicio. WebRTC conserva su coordinador y conversación continua propios; las órdenes locales se conectan al callback público de su capturador existente. No se crea un segundo AudioRecord para competir con WebRTC.

## Uso y privacidad

En Ajustes → Voz y escucha → Conversación y escucha se elige continuidad, interrupción, confianza, inactividad y frase de cierre. La escucha continua hablada usa el Vosk español instalado voluntariamente en Activación por voz. El dictado manual conserva los adaptadores anteriores; una sesión de texto no inicia por sí sola la captura. Sin caducidad no se cierra por silencio; VAD solo delimita una intervención. Una pérdida de audio pausa la conversación y exige reanudación expresa.

- **Para:** detiene la respuesta actual e invalida generación, síntesis y audio preparados. Conserva la conversación hablada y vuelve a escuchar tras la liberación segura.
- **Termina**, **Termina la conversación**, nombre del asistente + **termina**, o la frase personalizada: finaliza y recupera la espera de activación si aún estaba autorizada.
- **Hasta luego:** cierre opcional, desactivado inicialmente; solo como enunciado completo.
- **Deja de escuchar:** cierra conversación, detiene escucha y desactiva la preferencia de segundo plano.
- **Abre GPT Live**, **Inicia GPT Live**, **Abre ChatGPT en voz**, **Quiero hablar con ChatGPT**: transferencia a ChatGPT oficial. Se admite el nombre configurado delante de la orden.

Las órdenes no se detectan mediante subcadenas: «esto es para mañana» y «para mañana» no son «Para». No se guardan grabaciones pasivas, ni se añaden órdenes al contexto del proveedor. El audio de una conversación Groq continua se reconoce localmente antes de enviar texto autorizado. Se mantienen los consentimientos separados para proveedores, cámara y síntesis.

Tras **Termina**, no se queda parado: vuelve a esperar la frase bajo el consentimiento de activación vigente. El servicio y la espera visible aguardan la liberación del capturador anterior para no suspenderse por una carrera de micrófono ocupado. Se prueba la repetición de tres ciclos sin nueva autorización. Si se dijo **Deja de escuchar**, se pausó o se transfirió a ChatGPT, esa autorización ya no está vigente y hace falta una acción explícita para escuchar otra vez.

## Interrupción concurrente

La prueba de parada durante escritura descubrió una carrera real entre `AudioTrack.write` y `release`. La salida usa ahora escrituras no bloqueantes y exclusión breve por pista; una generación impide instalar una salida creada después de la parada. El cierre es idempotente y la instrumentación libera captura y rutas incluso cuando se cancela su corrutina. Requiere CI del correctivo; no equivale a medir parada audible.

`LocalCommandListener` usa AudioRecord a 16 kHz/mono, bloques reutilizables de 20 ms y Vosk local mientras se procesa/reproduce. Usa el endpoint SHORT y exige un enunciado final completo. Una gramática sola puede forzar ruido o frases normales a «para»: por eso «Para» requiere además coincidencia exacta del reconocedor libre y que los límites de la palabra cubran la intervención detectada por el VAD existente. Se usa la confianza real del decodificador restringido cuando hay ese acuerdo; no se inventa una puntuación ni se rebaja el umbral para pasar tests. La sensibilidad sigue ajustando ese umbral entre 0,95 y 0,70. Son protecciones experimentales, sin validación acústica física.

Durante voz exige AEC Android activado y entrada real consultable; bloquea comandos coincidentes con texto sintetizado y aplica protección inicial de 350 ms y separación de 700 ms. Es una protección conservadora: puede rechazar una orden válida si coincide con el texto de la respuesta. No demuestra inmunidad al eco, reconocimiento preciso ni baja latencia acústica. La activación de AEC tampoco acredita su eficacia en una ruta concreta. Si Android silencia captura, cambia ruta o no ofrece AEC, se muestra la limitación y se conserva **Parar**. No se anuncia barge-in validado hasta superar pruebas simultáneas físicas.

Cancelar invalida primero la generación, detiene/limpia AudioTrack y cancela tareas. La canalización vuelve a comprobar cancelación después de inferencia nativa antes de enviar PCM. La siguiente captura espera el cierre del capturador anterior. Una inferencia no cancelable puede retrasar la nueva captura, sin bloquear el hilo de interfaz; debe medirse por motor.

En Realtime se verificaron `AudioBufferCallback`, `requestStartRecording`, `requestStopRecording` y `getPlatformAudioProcessingState` en el AAR exacto **150.7871.01**, además de su [fuente pública](https://github.com/webrtc-sdk/webrtc/blob/m150_release/sdk/android/api/org/webrtc/audio/JavaAudioDeviceModule.java). Una cola de ocho buffers/12 bloques reutilizables copia PCM sin modificar muestras, posición ni timestamp del proveedor. Vosk se ejecuta fuera del hilo de captura; una discontinuidad reinicia el decodificador. Durante TTS alternativo la pista saliente y el audio nativo siguen deshabilitados, y el mismo módulo mantiene captura exclusivamente local. Durante reproducción solo se admiten órdenes con AEC activo confirmado y una configuración de captura no silenciada. Esto no acredita eficacia física del AEC.

Cancelar Realtime conserva los IDs suprimidos para ignorar eventos tardíos. El audio WebRTC nativo utiliza `response.cancel` y `output_audio_buffer.clear`; para TTS textual alternativo se elimina la respuesta incompleta del contexto con `conversation.item.delete`, sin fingir alineación entre texto y audio. Se contrastó con los [eventos oficiales](https://developers.openai.com/api/reference/resources/realtime/client-events) y la [guía de interrupción](https://developers.openai.com/api/docs/guides/realtime-conversations). No se han realizado llamadas facturables para validar este bloque. La continuidad evita el límite local de duración cuando se elige sin caducidad; los límites propios de la sesión del proveedor siguen vigentes y no se reconecta ocultamente.

## Segundo plano Android 15 y 16

El servicio ya existente usa Vosk/AudioRecord, no un bucle infinito de SpeechRecognizer. Sigue siendo un ASR de 39 MB, **no un KWS dedicado con consumo certificado**. Falta medir CPU, PSS, temperatura y batería. No ejecuta Groq, TTS ni RVC durante la espera pasiva y no necesita red después de instalar el modelo.

Se exige acción explícita con actividad visible, RECORD_AUDIO, FOREGROUND_SERVICE_MICROPHONE y notificaciones visibles. El manifiesto declara el servicio privado `microphone`, sin BOOT_COMPLETED ni START_STICKY. La notificación permite pausar y detener. Recordar la preferencia no inicia servicios al crear el proceso o volver a la aplicación. No se garantiza continuidad tras retirada de recientes, muerte de proceso, cierre forzado, pérdida de permiso o políticas del fabricante. Retirar la interfaz de recientes y forzar detención son acciones distintas; el servicio tiene `stopWithTask=false`, sujeto al sistema.

Las restricciones while-in-use impiden arrancar arbitrariamente un servicio microphone desde segundo plano y desde BOOT_COMPLETED. Se gestionan rechazos sin reinicios en bucle. Si se desactiva la preferencia, salir de la actividad revoca la escucha. Una pérdida de foco o micrófono requiere intervención explícita; no se reactiva al regresar.

Salir de la interfaz sin habilitar segundo plano detiene el servicio y pausa la conversación autorizada en RAM. Se diferencia de **Detener escucha y conversación**, que finaliza ambas. La destrucción del servicio provocada por esa salida no debe borrar el contexto de la pausa. La instrumentada recorre también salida/retorno sin permiso de segundo plano y comprueba que no se reactive el micrófono; las llamadas reales siguen pendientes de dispositivo.

Referencia: [restricciones de inicio de servicios Android](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start).

## Ray-Ban Meta

No se modifica el ciclo DAT de registro/cámara ni se atribuye a DAT captura indefinida de audio. La conexión Bluetooth no demuestra origen del micrófono. El detector consulta `AudioRecord.routedDevice`; la interfaz diferencia micrófono integrado de otros tipos de entrada Android. Si no hay ruta Bluetooth de comunicación, debe elegirse explícitamente el teléfono. Falta comprobar entrada y salida por separado, eco, llamadas, desconexiones y primeras palabras en Nothing Phone 3a Lite/Ray-Ban. El SDK y las [muestras oficiales Meta](https://github.com/facebook/meta-wearables-dat-android) no acreditan esos resultados físicos.

## ChatGPT oficial

El léxico Vosk es-0.42 no incluye literalmente GPT/ChatGPT. Se añade una gramática local con las pronunciaciones españolas `ge pe te` y `chat ge pe te`, compartiendo el modelo y los mismos PCM con el dictado libre. Solo se acepta una de las cuatro órdenes completas, con confianza media mínima 0,90 y coincidencia del inicio/final con la voz detectada por el VAD. El dictado libre también bloquea negaciones y explicaciones anteriores: usar solo sus tiempos fallaba cuando omitía palabras desconocidas del principio. La comparación de eco considera equivalentes GPT y «ge pe te». La gramática no se ejecuta durante la espera pasiva. Se prueban PCM auténticos de Piper para las cuatro órdenes y frases negativas. La pronunciación inglesa y el reconocimiento acústico del usuario/gafas siguen pendientes; no se acreditan por sintetizar una fixture.

Se usa exclusivamente el launcher público de `com.openai.chatgpt`, identificador del [enlace oficial a Google Play](https://play.google.com/store/apps/details?id=com.openai.chatgpt). Antes se detienen conversación, escucha, foco y micrófono. La ausencia de la aplicación y los errores de lanzamiento se muestran en español. En segundo plano se publica una notificación con PendingIntent directo para que el usuario abra ChatGPT; no se intenta eludir restricciones de actividades.

No se ha encontrado una acción pública estable documentada para iniciar Live mediante Intent. No hay deep links inventados, intents privados, accesibilidad, API facturable ni acceso a conversaciones o credenciales. La [documentación oficial de voz](https://help.openai.com/en/articles/20001274-chatgpt-voice) explica el botón de voz y la opción **Start with Voice** de versiones compatibles: su disponibilidad depende de la aplicación/cuenta. Abrir ChatGPT no equivale a iniciar Live. Al volver se exige pulsar Hablar/Activar escucha; no se inspecciona si ChatGPT está usando su micrófono.

## Validación y métricas pendientes

En `392c8bb`, emulador x86_64 Google APIs y entrada Android tipo 15, el servicio permaneció capturando con pantalla bloqueada hasta registrar una ventana completa de espera. API35: ventana 30 028 ms, CPU del proceso 2 169 ms y PSS 222 012 KiB. API36: ventana 30 023 ms, CPU 1 901 ms y PSS 218 269 KiB. Es una sola muestra por API, sin p50/p95, batería ni máximo; no mide Nothing/Ray-Ban ni acredita bajo consumo. La CI de ese SHA sigue fallando en reconocimiento y no publicó APK.

Las pruebas JVM verifican cinco turnos, límites de historial, generaciones, cancelación tardía, comandos exactos, confianza y protección lógica. Las instrumentadas comprueban preferencias Android, permisos denegados, rechazo de inicio sin actividad y launcher ausente/público. Ninguna sustituye voz reconocida, audio concurrente ni segundo plano real.

La regresión nativa genera PCM «Para» y una frase negativa con Piper es-ES y los decodifica con Vosk oficial; comparte el decodificador de producción. Una fixture verificada por SHA-256 está solo en la APK de tests. Las pruebas de servicio/captura se ejecutan en un segundo proceso de instrumentación, con concesión explícita de permisos de CI y retirada posterior, manteniendo el bloque anterior con permiso denegado. Se conservan ambos informes. Comprueban captura con pantalla bloqueada/otra tarea del emulador, acciones de notificación y AudioRecord/AudioTrack simultáneos con señal fija; no prueban eco, inteligibilidad o Bluetooth físico.

El informe de diagnóstico incluye CPU del proceso y PSS cada 30 segundos de espera pasiva (hasta 60 muestras), junto con API y tipo numérico real de entrada. Son medidas puntuales del proceso, no consumo exclusivo del detector, máximo ni batería. `python tools/resumir_latencias_escucha.py muestras.json --dispositivo "modelo/API" --ruta "entrada/salida comprobadas"` calcula p50/p95 por rango más próximo de las muestras disponibles; no inventa ceros si falta una medida. No mezclar rutas o dispositivos en un mismo archivo.

En API 35+ Android rechaza foco de audio si la app no está arriba ni tiene un servicio en primer plano apto. Los casos de captura del emulador usan actividad visible y GrantPermissionRule dentro de su propio proceso; Gradle puede desinstalar la APK entre bloques, por lo que no se presupone conservar permisos mediante `pm grant` entre tareas. Las llamadas/pérdidas de foco pausan Realtime y Groq; reanudar o cambiar al teléfono exige una acción explícita, sin perder voluntariamente el contexto mientras viva la sesión del proveedor.

Se registran únicamente duraciones técnicas numéricas: STOP_DETECTION (inicio de palabra según Vosk hasta entrega del resultado), STOP_AUDIO_REQUEST (solicitud de parada del reproductor, **no parada audible**) y FOLLOW_UP_READY (fin de turno hasta capturador preparado). Los relojes no acreditan latencia acústica. Falta recopilar p50/p95 por dispositivo, versión, motor y ruta, incluida parada audible con una referencia externa. No hay cifras conseguidas de Nothing/Ray-Ban.

Aceptación obligatoria: veinte activaciones; cinco preguntas reales con contexto; veinte «Para» durante voz/procesamiento; frases negativas y eco del propio TTS; llamadas, permisos y cambios Bluetooth; pantalla bloqueada/otra tarea; pausa/parada desde notificación; apertura ausente/presente de ChatGPT y retorno sin captura; medición CPU/PSS/batería. Ver [VALIDACION.md](VALIDACION.md). Hasta completarlo, la entrega es experimental.
