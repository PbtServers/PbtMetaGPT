# Conversación continua y ChatGPT oficial · experimental

Intervención del 28/10/2026 sobre `master`. No es una Release estable. Las pruebas de lógica, emulador y acústica se registran por separado en PROGRESO.md.

## Auditoría y alcance

La base `879fd8f` ya contiene exclusión global de micrófono (`MicrophoneGate`), servicio privado `microphone`, Vosk español 0.42, dictado on-device, WebRTC, rutas Android, audio PCM cancelable y voces Groq/Kokoro/Piper/Qwen/Android/RVC. El controlador de Groq desactivaba una sesión iniciada con activación después de cada turno; además confirmaba la respuesta en el contexto antes de terminar la reproducción. Se reutilizan estos componentes.

La ampliación conecta una autoridad de conversación y generaciones con GroqCloud. Una sesión hablada vuelve a `FOLLOW_UP_LISTENING` después de liberar salida y detector. El contexto permanece en RAM, conserva cinco turnos y está limitado a 32 mensajes/15 000 caracteres; las instrucciones permanecen en un mensaje de sistema separado. Una respuesta interrumpida no se añade como respuesta completa al contexto. La UI conserva el fragmento marcado como interrumpido.

Estados: `IDLE`, `WAKE_LISTENING`, `USER_LISTENING`, `PROCESSING`, `SPEAKING`, `FOLLOW_UP_LISTENING`, `INTERRUPTING`, `SUSPENDED`, `STOPPED`. La espera de activación continúa bajo la política existente del servicio. WebRTC conserva su conversación continua propia; **las nuevas órdenes locales concurrentes se conectan inicialmente a Groq, no a la captura WebRTC**. No se crea un segundo AudioRecord para competir con WebRTC.

## Uso y privacidad

En Ajustes → Voz y escucha → Conversación y escucha se elige continuidad, interrupción, confianza, inactividad y frase de cierre. La escucha continua hablada usa el Vosk español instalado voluntariamente en Activación por voz. El dictado manual conserva los adaptadores anteriores; una sesión de texto no inicia por sí sola la captura. Sin caducidad no se cierra por silencio; VAD solo delimita una intervención. Una pérdida de audio pausa la conversación y exige reanudación expresa.

- **Para:** detiene la respuesta actual e invalida generación, síntesis y audio preparados. Conserva la conversación hablada y vuelve a escuchar tras la liberación segura.
- **Termina**, **Termina la conversación**, nombre del asistente + **termina**, o la frase personalizada: finaliza y recupera la espera de activación si aún estaba autorizada.
- **Hasta luego:** cierre opcional, desactivado inicialmente; solo como enunciado completo.
- **Deja de escuchar:** cierra conversación, detiene escucha y desactiva la preferencia de segundo plano.
- **Abre GPT Live**, **Inicia GPT Live**, **Abre ChatGPT en voz**, **Quiero hablar con ChatGPT**: transferencia a ChatGPT oficial. Se admite el nombre configurado delante de la orden.

Las órdenes no se detectan mediante subcadenas: «esto es para mañana» y «para mañana» no son «Para». No se guardan grabaciones pasivas, ni se añaden órdenes al contexto del proveedor. El audio de una conversación Groq continua se reconoce localmente antes de enviar texto autorizado. Se mantienen los consentimientos separados para proveedores, cámara y síntesis.

## Interrupción concurrente

`LocalCommandListener` usa AudioRecord a 16 kHz/mono, bloques reutilizables de 20 ms y Vosk local mientras se procesa/reproduce. Usa el endpoint SHORT y exige un enunciado final completo con confianza. Se evaluó la gramática de una palabra: puede forzar ruido o frases normales a «para», por lo que se conserva el grafo abierto español y se filtran órdenes exactas. La sensibilidad ajusta un umbral real de confianza entre 0,95 y 0,70; no es una sensibilidad acústica inventada.

Durante voz exige AEC Android activado y entrada real consultable; bloquea comandos coincidentes con texto sintetizado y aplica protección inicial de 350 ms y separación de 700 ms. Es una protección conservadora: puede rechazar una orden válida si coincide con el texto de la respuesta. No demuestra inmunidad al eco, reconocimiento preciso ni baja latencia acústica. La activación de AEC tampoco acredita su eficacia en una ruta concreta. Si Android silencia captura, cambia ruta o no ofrece AEC, se muestra la limitación y se conserva **Parar**. No se anuncia barge-in validado hasta superar pruebas simultáneas físicas.

Cancelar invalida primero la generación, detiene/limpia AudioTrack y cancela tareas. La canalización vuelve a comprobar cancelación después de inferencia nativa antes de enviar PCM. La siguiente captura espera el cierre del capturador anterior. Una inferencia no cancelable puede retrasar la nueva captura, sin bloquear el hilo de interfaz; debe medirse por motor.

## Segundo plano Android 15 y 16

El servicio ya existente usa Vosk/AudioRecord, no un bucle infinito de SpeechRecognizer. Sigue siendo un ASR de 39 MB, **no un KWS dedicado con consumo certificado**. Falta medir CPU, PSS, temperatura y batería. No ejecuta Groq, TTS ni RVC durante la espera pasiva y no necesita red después de instalar el modelo.

Se exige acción explícita con actividad visible, RECORD_AUDIO, FOREGROUND_SERVICE_MICROPHONE y notificaciones visibles. El manifiesto declara el servicio privado `microphone`, sin BOOT_COMPLETED ni START_STICKY. La notificación permite pausar y detener. Recordar la preferencia no inicia servicios al crear el proceso o volver a la aplicación. No se garantiza continuidad tras retirada de recientes, muerte de proceso, cierre forzado, pérdida de permiso o políticas del fabricante. Retirar la interfaz de recientes y forzar detención son acciones distintas; el servicio tiene `stopWithTask=false`, sujeto al sistema.

Las restricciones while-in-use impiden arrancar arbitrariamente un servicio microphone desde segundo plano y desde BOOT_COMPLETED. Se gestionan rechazos sin reinicios en bucle. Si se desactiva la preferencia, salir de la actividad revoca la escucha. Una pérdida de foco o micrófono requiere intervención explícita; no se reactiva al regresar.

Referencia: [restricciones de inicio de servicios Android](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start).

## Ray-Ban Meta

No se modifica el ciclo DAT de registro/cámara ni se atribuye a DAT captura indefinida de audio. La conexión Bluetooth no demuestra origen del micrófono. El detector consulta `AudioRecord.routedDevice`; la interfaz diferencia micrófono integrado de otros tipos de entrada Android. Si no hay ruta Bluetooth de comunicación, debe elegirse explícitamente el teléfono. Falta comprobar entrada y salida por separado, eco, llamadas, desconexiones y primeras palabras en Nothing Phone 3a Lite/Ray-Ban. El SDK y las [muestras oficiales Meta](https://github.com/facebook/meta-wearables-dat-android) no acreditan esos resultados físicos.

## ChatGPT oficial

Se usa exclusivamente el launcher público de `com.openai.chatgpt`, identificador del [enlace oficial a Google Play](https://play.google.com/store/apps/details?id=com.openai.chatgpt). Antes se detienen conversación, escucha, foco y micrófono. La ausencia de la aplicación y los errores de lanzamiento se muestran en español. En segundo plano se publica una notificación con PendingIntent directo para que el usuario abra ChatGPT; no se intenta eludir restricciones de actividades.

No se ha encontrado una acción pública estable documentada para iniciar Live mediante Intent. No hay deep links inventados, intents privados, accesibilidad, API facturable ni acceso a conversaciones o credenciales. La [documentación oficial de voz](https://help.openai.com/en/articles/20001274-chatgpt-voice) explica el botón de voz y la opción **Start with Voice** de versiones compatibles: su disponibilidad depende de la aplicación/cuenta. Abrir ChatGPT no equivale a iniciar Live. Al volver se exige pulsar Hablar/Activar escucha; no se inspecciona si ChatGPT está usando su micrófono.

## Validación y métricas pendientes

Las pruebas JVM verifican cinco turnos, límites de historial, generaciones, cancelación tardía, comandos exactos, confianza y protección lógica. Las instrumentadas comprueban preferencias Android, permisos denegados, rechazo de inicio sin actividad y launcher ausente/público. Ninguna sustituye voz reconocida, audio concurrente ni segundo plano real.

Se registran únicamente duraciones técnicas numéricas: STOP_DETECTION (inicio de palabra según Vosk hasta entrega del resultado), STOP_AUDIO_REQUEST (solicitud de parada del reproductor, **no parada audible**) y FOLLOW_UP_READY (fin de turno hasta capturador preparado). Los relojes no acreditan latencia acústica. Falta recopilar p50/p95 por dispositivo, versión, motor y ruta, incluida parada audible con una referencia externa. No hay cifras conseguidas de Nothing/Ray-Ban.

Aceptación obligatoria: veinte activaciones; cinco preguntas reales con contexto; veinte «Para» durante voz/procesamiento; frases negativas y eco del propio TTS; llamadas, permisos y cambios Bluetooth; pantalla bloqueada/otra tarea; pausa/parada desde notificación; apertura ausente/presente de ChatGPT y retorno sin captura; medición CPU/PSS/batería. Ver [VALIDACION.md](VALIDACION.md). Hasta completarlo, la entrega es experimental.
