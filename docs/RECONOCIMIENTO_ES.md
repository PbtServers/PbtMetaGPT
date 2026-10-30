# Reconocimiento español y diagnóstico de voz

## Preparación desde Inicio

En Inicio, la tarjeta **Escucha permanente** muestra el modelo español y el estado observable del servicio. **Descargar reconocimiento de voz en español** abre una confirmación con origen, tamaño y espacio temporal. La descarga comparte gestor con Ajustes, muestra bytes reales y admite cancelación/reintento. Se comprueba SHA-256 antes de extraer, se limita el ZIP y se conserva el directorio anterior hasta instalar el nuevo. Un archivo inválido no permite activar el motor. No se descargan modelos ni se abre el micrófono al instalar la aplicación.

Pulsa **Activar escucha permanente**, concede permisos y confirma. La casilla **Continuar con el teléfono bloqueado** permite autorizar segundo plano sin salir de Inicio; conserva la preferencia anterior. Desmarcada, salir pausa la conversación y detiene la espera. Marcada, el servicio de micrófono iniciado con la actividad visible mantiene notificación, pausa y parada explícitas. No hay reinicio tras cierre forzado, revocación o reconexión. La etiqueta de espera solo se publica después de recibir PCM y comprobar su ruta.

## Motor y consentimiento

Las opciones avanzadas conservan la configuración del dictado. Cambiarla no abre sensores y exige detener la escucha actual.

| Selección | Dictado individual y conversación continua | Red |
| --- | --- | --- |
| Vosk español | Modelo small-es-0.42 fijado por SHA-256; español genérico | Ninguna para reconocer |
| Android local | `createOnDeviceSpeechRecognizer`, idioma explícito del perfil, `es-ES` por defecto; error si falta el motor/idioma | Sin fallback remoto |
| Groq Whisper | Audio PCM mono a 16 kHz, transcripción con `language=es`; requiere proveedor y autorización de audio separados | Sí; cuota/condiciones de Groq |
| Automático | Whisper si ya está autorizado para Groq; Android local en dictado individual si existe; Vosk en continuo o sin Android | Solo con autorización previa de audio |

**Máxima precisión** utiliza Whisper large-v3 y cierre energético de 1,7 s, ampliado a 2 s para intervenciones cortas. **Máxima rapidez** utiliza Whisper large-v3-turbo y 1 s. Con motores locales cambia el cierre, no sus pesos ni su precisión acústica intrínseca. El fabricante puede ignorar los tiempos orientativos de Android SpeechRecognizer. No se promete una WER concreta ni acento regional en los modelos que solo admiten español genérico.

El prompt de Whisper orienta nombres como Google, Spotify y ChatGPT; no sustituye audio, no traduce y no modifica palabras a posteriori para forzar órdenes. La activación y las interrupciones siguen siendo locales. Autoriza el envío de audio de dictado en las opciones avanzadas únicamente si quieres usar Whisper. Groq conversacional recibe texto aunque el reconocimiento sea local: un reconocedor offline no convierte el proveedor de respuestas en offline.

Antes de enviar una intervención breve (hasta seis segundos, incluido silencio) a Whisper, se intenta resolver una orden local sobre ese mismo PCM ya capturado. Solo acepta texto completo y confianza suficiente; si reconoce Para, Termina o una acción, evita la petición de transcripción y de respuesta. No abre otro micrófono ni ejecuta ASR adicional sobre preguntas largas. Si el modelo local no puede confirmar la orden, continúa únicamente el Whisper previamente autorizado. En dictado Vosk, una acción con confianza insuficiente produce un error recuperable y no se ejecuta ni se envía como pregunta genérica.

**Pregunta junto a la activación:** el texto ya reconocido por el detector Vosk se conserva sin pedir repetirlo. Ese texto inicial no se vuelve a transcribir con Whisper porque no se conserva ni se envía retrospectivamente el audio de espera. Para máxima precisión, di la activación, espera **Te escucho** y formula la pregunta; las intervenciones siguientes utilizan el motor elegido. Esta diferencia debe tenerse en cuenta al comparar exactitud.

## Activación, recuperación y recursos

El servicio espera la liberación de la captura anterior antes de armar el detector. Al detectar, invalida callbacks anteriores y cede AudioRecord antes de abrir otra captura. La preparación tiene plazos de recuperación: detector 20 s, arranque de conversación 15 s, señal de confirmación 3 s y liberación previa aproximadamente 8–10 s. El PCM debe confirmar su entrada en 5 s. Vencer un plazo muestra un error y suspende; no autoriza una segunda captura ni inicia un bucle de reconexión.

La espera de intervención del dictado continuo se acota a 60 s por captura y las preguntas a 28 s de voz; el silencio retorna al siguiente turno autorizado. La preferencia de inactividad puede finalizar antes. Se mantienen 300 ms de prebuffer en RAM para conservar las primeras sílabas. Se reutilizan bloques de 20 ms y se vacía el PCM pendiente sin imponer otra pausa después de cada trama. Los búferes de captura/Whisper se borran al liberar y no se escriben grabaciones permanentes.

El Model Vosk se comparte en el proceso, con reconocedores independientes. La presión de memoria puede liberar el modelo cuando no hay consumidores; no se cierra mientras una captura lo usa. Se evita esperar al cierre de modelos TTS para volver a capturar. La cola de síntesis/reproducción sigue cancelable y una generación antigua no cancela el detector de una nueva. Iniciar/detener el detector se serializa; sus callbacks antiguos no suspenden una conversación posterior. Tener una notificación pausada no concede permiso para continuar otra captura manual al salir de la actividad.

En los decodificadores de comandos, el endpoint acústico nativo no decide el cierre: puede interpretar un nombre desconocido como silencio y perder el centro de «Inicia ChatGPT en voz». La compuerta de energía conserva toda la intervención y su prebuffer; solo después consulta los resultados finales. Mantiene confianza/fronteras y limita cada segmento a 20 s. En silencio no alimenta los tres reconocedores. El cierre local de comandos utiliza 1,1 s de silencio: mejora la integridad a costa de esperar esa confirmación, cuya latencia debe medirse en el dispositivo.

El aviso Bluetooth conserva 190 ms de sonido, pero evita otros 300 ms de silencio cuando la ruta ya está retenida por el servicio. Es una reducción de espera programada; no acredita la latencia audible física. La codificación WAV usa un búfer reutilizado por bloque y una escritura por trama. La primera frase se mide cuando queda disponible en el flujo, antes de esperar a la síntesis.

## Órdenes y proveedores

Las órdenes completas se resuelven antes de Groq. No se ejecutan acciones por una mención de una palabra dentro de una pregunta.

| Frase | Acción |
| --- | --- |
| Para | Interrumpir la voz del asistente |
| Termina | Finalizar conversación y regresar a espera autorizada |
| Deja de escuchar | Revocar también la escucha |
| Pon música; ponme música; pon algo de música; quiero escuchar música; abre Spotify; ponme algo en Spotify | Solicitar reproducción a la sesión multimedia Spotify existente |
| Pausa Spotify; para Spotify | Pausar música |
| Continúa la música | Continuar reproducción |
| Siguiente canción; pon la siguiente; canción anterior | Controles publicados por la sesión Spotify |
| Google; abre Google | Aplicación Google |
| Abre el navegador | Navegador predeterminado |
| Busca en Google restaurantes de Barcelona | Búsqueda mediante acción pública o URL HTTPS codificada |
| Abre el asistente del teléfono | Asistente digital configurado, Google/Gemini según Android |
| Abre GPT Live; abre ChatGPT | Transferencia a la aplicación oficial; inicio de Voz no confirmado |
| Qué es Spotify; cómo funciona Google; qué música me recomiendas | Pregunta al proveedor conversacional |

Spotify conserva filtro de paquete, permisos, play/pause/next/previous y liberación de foco. No selecciona canciones concretas ni inventa una reproducción en frío. Si falta autorización o sesión multimedia, informa del problema y no delega la orden al modelo. Durante respuesta, el detector de interrupciones exige confianza, enunciado final, fronteras completas y protección de eco; la gramática no basta por sí sola. Google puede reconocerse peor según el hablante del modelo pequeño: no se añaden equivalencias de palabras ajenas ni se rebajan umbrales. La orden «Abre el asistente del teléfono» es una alternativa distinta y explícita.

Abrir otra aplicación cancela respuesta/captura/foco. Con bloqueo o sin actividad visible utiliza notificación; Android puede exigir toque y desbloqueo. Al volver, se necesita una acción explícita para reanudar. ChatGPT se abre por su launcher público. [OpenAI documenta Iniciar con Voz](https://help.openai.com/en/articles/20001274-chatgpt-voice) en Ajustes → Voz para conversaciones nuevas/vacías en versiones compatibles; no se ha localizado una API pública que garantice abrir Live directamente.

## Medidas y aceptación

El informe técnico incluye una ventana acotada de transiciones con número de sesión, fases, tiempos, micrófono ocupado/libre, tipo de entrada y motor. No registra conversaciones, nombres Bluetooth, audio ni credenciales. Las latencias incluyen preparación del micrófono, inicio de voz, cierre del VAD, transcripción, primer token, primera frase, primera muestra PCM, primera trama enviada y total. El cierre del VAD es una estimación energética, no el instante físico exacto en que termina la voz. Primera trama enviada tampoco acredita sonido audible en las gafas.

La instrumentación sintetiza 14 frases públicas Davefx es-ES y las remuestrea a 16 kHz. Compara dictado Vosk libre y decodificación de comandos sobre el mismo PCM, con WER, CER y omisiones; normaliza mayúsculas, puntuación y tildes. Conserva errores, incluidos topónimos, sin exigir artificialmente cero errores. Verifica aparte que preguntas y negaciones no ejecutan acciones. Los informes `reconocimiento-es-metricas-emulador.txt` de cada API incluyen carga inicial/reutilizada y transferencia AudioRecord. Esa comparación **no** es una medición anterior/posterior de toda la aplicación ni de Whisper/Android reales.

Sin grabaciones humanas autorizadas ni credenciales de servicio para pruebas reales, no se presenta una mejora porcentual de exactitud o latencia total. El usuario ha indicado que no hay corpus personal disponible. Las suites no acreditan Google/Spotify instalados, voces humanas, consumo Nothing, HFP/LE, gafas o latencia audible. Las evidencias del SHA y sus fallos se registran en [PROGRESO.md](PROGRESO.md).

En el Nothing/Ray-Ban, prueba veinte activaciones, preguntas con pausa de 1,2 s, Para durante TTS, Termina y las variantes musicales; repite visible, Inicio y bloqueo 5/15/30 minutos. Compara el tipo/nombre de entrada en Dispositivos, teléfono frente a gafas, permisos revocados, llamada, desconexión/reconexión y regreso de ChatGPT. Exporta únicamente el diagnóstico técnico, sin grabaciones ni claves. Aplica también [VALIDACION.md](VALIDACION.md) y [CANDIDATA_RAYBAN.md](CANDIDATA_RAYBAN.md).

Fuentes de contrato: [Groq Speech to Text](https://console.groq.com/docs/speech-to-text), [RecognizerIntent de Android](https://developer.android.com/reference/android/speech/RecognizerIntent) y [servicios de micrófono en primer plano](https://developer.android.com/develop/background-work/services/fgs/service-types#microphone).
