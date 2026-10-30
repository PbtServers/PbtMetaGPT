# Regresiones de activación, comandos y voz

Reparación iniciada el 30/10/2026 sobre `a15368d00f36f352de8d1fd2fc6398c0b0fa1e0e`. Las pruebas del teléfono comunicadas por la usuaria invalidan cualquier interpretación de la CI anterior como aceptación física. Esta intervención conserva la interfaz y los seis cambios documentales ajenos.

## Causas y correcciones

| Fallo | Causa comprobada en código | Corrección |
| --- | --- | --- |
| Aviso que suspende toda la escucha | El timeout de 3000 ms llamaba a suspendListening. El vaciado de AudioTrack permitía duración + 3000 ms: 3190 ms para 190 ms de sonido, además de preparación/escrituras. | Aviso accesorio con presupuesto de 500 ms; omitir errores y timeouts propios, propagar cancelación de sesión y cerrar salida antes de continuar. |
| Renegociación durante el aviso | WakeConfirmation adquiría otra concesión de ruta/foco aunque el servicio ya tuviera una. | Reutilizar la ruta preparada; trabajo de reproducción fuera de Main. |
| Escrituras sin progreso | Reintento de escritura cero sin límite propio. | Detectar tres segundos sin escritura; vaciado con fallo de reproducción, sin cancelar por timeout interno toda la conversación. |
| Música que espera foco | El servicio podía conservar foco solicitado antes de activar el control musical. | Ceder su foco expresamente sin retirar ruta ni consentimiento, después de liberar la respuesta anterior. |
| Orden enviada al modelo | Catálogo incompleto para «Pon música en Spotify» y ausencia de una aclaración para imperativos dudosos. | Enrutador previo a Groq, variantes completas y solicitud de repetición recuperable. No basta mencionar Spotify/Google. |
| Orden dudosa suspende sesión | Rechazo de confianza convertido en HostedFailure. | RepeatLocalCommand mantiene sesión y seguimiento, sin ejecución ni envío de esa orden al modelo. |
| Gramática interfiere con dictado | Dictado completo utilizaba LocalCommandDecoder, tres reconocedores y VAD interno de comandos. | Un reconocedor libre conserva todos los resultados finales; el VAD de captura decide el cierre. Comprobación adicional de órdenes solo sobre intervenciones breves, hasta seis segundos en RAM, con borrado. |
| Asteriscos pronunciados | Los fragmentos del proveedor llegaban sin procesar a síntesis. | SpeechMarkup incremental antes de segmentación, y TtsText para la cola alternativa. Chat e historial mantienen el original. |

Android documenta que el [umbral inicial de AudioTrack](https://developer.android.com/reference/android/media/AudioTrack#getStartThresholdInFrames()) equivale inicialmente a su capacidad de búfer. Una señal más corta puede no arrancar aunque esté escrita. PcmPlayer fija un umbral de hasta 20 ms y no mayor que el primer fragmento. La prueba de AudioTrack auténtico reproduce primero la espera con búfer de gran capacidad y exige después avance/liberación con la corrección; su resultado debe consultarse en la CI del SHA. Se conservan el timeout accesorio y la recuperación si el proveedor no permite reproducir. El error TTS permanece visible durante la siguiente escucha hasta otra síntesis o una nueva sesión.

No se ha confirmado qué componente físico deja inmóvil AudioTrack: contador Bluetooth, foco y negociación son hipótesis pendientes de Logcat y diagnóstico del Nothing/Ray-Ban. El timeout comprobado explica la suspensión, no demuestra una causa física concreta.

La pérdida real del micrófono, revocación, llamada o cambio de ruta sigue pausando por seguridad. Omitir un aviso no autoriza usar otro micrófono ni ignorar una llamada.

La primera ejecución ampliada detectó cuatro fallos API35 y tres API36; se mantienen documentados en PROGRESO. La cesión musical ahora distingue todas las concesiones pasivas del servicio/detector y conserva las activas. El resultado del control tiene StateFlow propio para que el consumo del aviso de interfaz no oculte su finalización.

Para la apertura ambigua del asistente del teléfono puede hacerse otra lectura libre del mismo PCM, acotada a ocho segundos en RAM y borrada al finalizar. Solo se considera ante el imperativo explícito con primera hipótesis dudosa; exige una alternativa completa concordante, confianza de palabras >=0,95 y fronteras físicas. Los scores N-best no se reinterpretan como confianza. Los casos hotel/televisor/negación y las tres voces forman parte de la regresión; no se acredita recuperación hasta pasar Android.

## Español y voz

Idioma solicitado es-ES. Vosk pequeño es un modelo de español genérico: no garantiza nombres propios ni una precisión suficiente para dictado natural. Automático respeta Whisper cuando existe consentimiento; sin ese consentimiento puede utilizar Android local disponible también en conversación continua. Vosk explícito sigue disponible y la activación permanece local. No hay fallback inglés ni autorización implícita de red.

SpeechMarkup conserva énfasis, preguntas, exclamaciones, pausas, cifras y unidades, elimina delimitadores y URL de enlaces con etiqueta, trata listas/títulos/código y mantiene estado entre tokens. Los asteriscos escapados o dentro de código se leen como símbolo. No incorpora SSML ni controles emocionales inexistentes. Los motores conservan sus parámetros realmente implementados; la expresividad física queda pendiente de audición.

## Pruebas reproducibles y sus límites

- VoiceRegressionTest reproduce timeout interno/externo, cancelación, aviso desactivado, errores accesorios de ruta/foco, comandos, confianza y Markdown fragmentado.
- PlaybackRegressionInstrumentedTest ejecuta PcmPlayer con salida interna controlada: cabeza congelada, escritura cero, cierre y siguiente reproducción.
- HostedRoutingInstrumentedTest ejecuta el controlador conversacional y llega al control musical, cuenta y rechaza cualquier HTTP de Groq para esas órdenes y comprueba repetición recuperable.
- MusicControlInstrumentedTest recorre enrutador → SpotifyControls → MediaController → callbacks de una MediaSession Android auténtica del paquete de pruebas; mantiene permisos, filtro y bloqueo. No es la aplicación Spotify real.
- MicrophoneServiceInstrumentedTest mantiene sus pruebas de captura real del emulador, notificación, revocación, pantalla bloqueada y añade activación del control musical después de arrancar el servicio.
- WakeConversationRegressionInstrumentedTest activa el servicio real e inyecta PCM sintético en el detector nativo. Comprueba aviso normal, bloqueado y desactivado, dictado nativo de preguntas, respuesta controlada, Markdown visual/hablado, contexto, siguiente turno, interrupción, final y vuelta a espera autorizada. Un escenario se ejecuta con pantalla virtual bloqueada. El control de interrupción del controlador se prueba aquí; la acústica de Para se conserva en su suite nativa separada.
- SpanishRecognitionInstrumentedTest amplía el corpus a 20 frases sintéticas Davefx es-ES, 16 kHz, con WER/CER/omisiones e inferencia del decodificador de dictado de producción. Se mantienen los casos anteriores y los negativos.

Las sustituciones de PCM, proveedor y síntesis solo tienen estado mutable en `src/debug`, sin componentes exportados, escucha de red ni lectura externa. `src/release` devuelve null y no ofrece setters. Las fixtures permanecen en androidTest, fuera de la APK de producción.

Los 75 JVM locales son un subconjunto; la suite completa, Lint, seguridad de APK y emuladores requieren la CI del commit publicado. No se sustituyen por resultados anteriores. No hay corpus humano autorizado ni comparación física de Whisper/Android, consumo o latencia audible.

## ChatGPT oficial

Se conserva el launcher público y la cesión del micrófono/foco, con notificación legítima en segundo plano. [OpenAI documenta Iniciar con Voz](https://help.openai.com/en/articles/20001274-chatgpt-voice): Ajustes → Voz → Iniciar con Voz, versiones compatibles, al abrir una conversación nueva o vacía. No se ha encontrado un contrato público que permita a otra aplicación garantizar ese destino. Abrir ChatGPT no confirma Live. No se ha probado una cuenta/aplicación oficial instalada en este entorno.

## Aceptación física pendiente

En Nothing/Ray-Ban: veinte activaciones con aviso, sin aviso y tras omitir un aviso; repetir con Inicio/bloqueo 5/15/30 minutos. Confirmar entrada y salida separadas, comandos de música con Spotify previamente activo, foco, llamada, desconexión/reconexión y revocación. Probar preguntas con pausas de 1,2 s y topónimos; esperar «Te escucho» antes de la pregunta. El prebuffer no recupera audio anterior a abrir la captura. Comparar primera trama numérica con latencia audible medida externamente.

Conservar solo diagnóstico técnico, sin conversaciones ni audio privado. No hay estimación de aceptación física sin acceso al dispositivo. Una APK debug es experimental; no es una Release estable.
