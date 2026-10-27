# Estabilización experimental de audio y escucha local

Esta intervención es independiente del 85 % funcional general y del seguimiento anterior de interfaz. **No se ha confirmado la causa de los cierres del teléfono.** La CI verde de la base `f9daabd` ([40542139078](https://github.com/PbtServers/PbtMetaGPT/actions/runs/40542139078)) no acredita esta intervención ni audio físico.

Dispositivo comunicado por la usuaria: **Nothing Phone 3a Lite / Android 16 / Nothing OS 4.1 Beta**, con Meta Glasses Adventurer visibles como **Meta Glasses 00R6**. El nombre Bluetooth no se usa como identificador estable de modelo. No se atribuye el fallo a la beta ni a Meta sin evidencias.

## Riesgos localizados en código y cambios

- El audio y la espera local dependían de `Activity.onStop`; minimizar cerraba Realtime. El cliente de audio autorizado vive ahora en `PbtApplication`, y únicamente el servicio explícito permite continuar fuera de la actividad. Sin ese servicio, salir sigue deteniendo la conversación.
- El cierre de DAT también detenía Realtime, aunque una sesión DAT de cámara y una ruta Android de audio son independientes. Las pérdidas de ruta y foco se gestionan en `AudioRoute`; desconectar DAT no acredita perder el audio Bluetooth.
- No había exclusión global entre Android SpeechRecognizer, pruebas de micrófono y Realtime. `MicrophoneGate` concede un único uso del micrófono y rechaza liberaciones antiguas. El servicio espera la finalización y liberación de Vosk/AudioRecord antes de crear Realtime.
- La transición desde el callback del reconocedor Android era inmediata. Ahora se encola fuera de su callback; el reconocedor se conserva antes de configurar/iniciar para poder destruirlo si falla esa preparación.
- La liberación de WebRTC se interrumpía ante una excepción de estado/permisos y podía dejar referencias parciales. Se desvinculan las referencias antes de liberar y se registran los fallos específicos mientras se cierra el resto. No se capturan abortos nativos como si fueran excepciones recuperables.

Son defectos o riesgos demostrables mediante lectura y pruebas de políticas. **No prueban que alguno explique el cierre inmediato observado en el Nothing.** La captura de errores real sigue siendo un requisito antes de darlo por reparado.

## Escucha persistente autorizada

Inicio incorpora «Activar escucha». Requiere un asistente guardado, OpenAI configurado, micrófono, notificaciones y el modelo local. La confirmación explica segundo plano, bloqueo, consumo y que una frase guardada iniciará una conversación que puede consumir API.

`ContinuousWakeService` declara tipo `microphone` y permisos de servicio/micrófono. Solo se inicia mediante la acción visible; no tiene receptor de arranque, alarmas ni reinicio `START_STICKY`. La notificación persistente abre Inicio o detiene escucha y conversación. Navegar entre apartados y recrear la actividad no revoca el servicio autorizado. Detenerlo explícitamente invalida los callbacks antes de destruir recursos. Tras morir el proceso no se reinicia solo: se muestra suspensión al siguiente arranque cuando queda la marca de sesión interrumpida.

Estados: desactivado → preparando motor → esperando frase → detectada → preparando Realtime → conversación confirmada → retorno a escucha. Un fallo de conexión, permisos, captura silenciada por Android o audio interrumpido suspende la escucha; requiere otra acción del usuario. Finalizar normalmente una conversación confirmada permite el retorno con el consentimiento aún vigente. El servicio no abre pantallas desde segundo plano para sortear restricciones: el estado y los controles quedan en la notificación.

No hay grabaciones de fondo, almacenamiento de PCM ni envío continuo a OpenAI. El procesamiento nativo y su micrófono se cierran antes de iniciar red/audio Realtime. La escucha usa AudioRecord y ASR local, **no un bucle permanente de SpeechRecognizer**. El reconocedor Android se conserva como prueba alternativa visible, con ventanas limitadas.

Un bloqueo parcial de CPU existe solo durante captura Vosk autorizada; se libera al cancelar, fallar o pasar a conversación. Puede consumir batería considerablemente. Se proporciona un enlace a los ajustes Android de permisos y batería, sin solicitar exenciones automáticamente ni eludir decisiones del sistema.

## Motor, modelos y frases personalizadas

Motor integrado: **Vosk Android 0.3.75**. Descarga explícita opcional de **vosk-model-small-es-0.42**, publicado como modelo español de 39 MB con licencia Apache 2.0 por su proveedor. No se incorpora el modelo a Git ni a la APK. La app descarga exclusivamente el ZIP oficial por HTTPS, sin redirecciones, con límites de 64 MiB comprimido / 256 MiB expandido, raíz fija, lista cerrada de archivos y rechazo de duplicados. Instala mediante carpeta temporal; errores/cancelación eliminan esa carpeta. Guarda una huella SHA-256 calculada de la descarga; **no es una comparación con un hash oficial publicado**. El modelo puede eliminarse desde la interfaz cuando el servicio está parado.

Se utiliza ASR español con vocabulario abierto del modelo y coincidencia posterior de frases guardadas. No se restringe todo el ruido ambiental a una gramática de dos frases. El detector evalúa resultados finales, por lo que puede requerir una pausa tras la frase. Las tildes, mayúsculas, coincidencias exactas, tolerancia textual y ambigüedades siguen pasando por `PhraseMatcher` y `ActivationGate`.

«Atento» sin nombre se configura desmarcando «Añadir automáticamente el nombre». «Atento Jarvis» puede configurarse, pero la palabra Jarvis debe ser reconocible por el modelo. **Vosk no aprende palabras nuevas al escribir una frase**: nombres fuera del vocabulario pueden necesitar adaptación del diccionario/modelo con herramientas Kaldi. Esta versión ofrece el modelo oficial español y la prueba Android alternativa; no importa ni entrena modelos ASR arbitrarios. No se promete reconocimiento fiable de cualquier nombre. Las frases ambiguas entre asistentes no activan ninguno. Las frases y los perfiles se guardan por el circuito existente, sin registrar el texto reconocido.

## Registros sin ADB

Recorrido: **Inicio → Ajustes → Diagnóstico → Registros de errores** (también «Más ajustes»).

1. Instalar una APK de esta intervención cuando la nueva CI la valide; una APK anterior no incorpora estos registros.
2. Reproducir una sola acción: iniciar conversación, activar escucha o seleccionar/probar voz.
3. Si se cierra, volver a abrir la app y entrar en Registros de errores.
4. Pulsar «Exportar registros .txt» y elegir destino mediante el selector Android. Revisar el archivo y compartirlo junto a la acción y hora aproximada. No incluir la clave, tokens ni conversaciones.

El diario privado se guarda en `noBackupFilesDir`, con un máximo de tres archivos de 64 KiB y retención de siete días; incluye fecha UTC, componente, categoría y, si existe, tipo de excepción y hasta doce archivos/líneas de la propia app. **No acepta texto libre**: descarta mensajes/causas de excepciones, URL, hilos, identificadores, transcripciones, fotografías y audio. La exportación añade versión de la app, API Android y ABI de una lista fija. Se puede borrar también el informe del último cierre.

Se encadena el manejador original de excepciones Java/Kotlin, conservando el comportamiento de cierre de Android después de escribir el informe. Se registran hitos antes de credenciales, carga WebRTC, módulo de audio, fábrica y reconocedor. Los errores DAT y de proveedor registrados por `Diagnostics` dejan categorías persistentes sin copiar cuerpos externos. Al siguiente arranque se consultan causas históricas de salida del propio proceso mediante `ApplicationExitInfo`, sin exportar trazas del sistema.

Un SIGSEGV/SIGABRT puede impedir escribir la pila; la causa histórica y el último hito solo acotan la investigación. ANR, falta de memoria y terminación abrupta tampoco garantizan un informe completo. El diario no sustituye una traza nativa si estos datos no bastan. No se presenta una salida nativa como causa identificada sin examinar su evidencia.

## Interfaz de esta intervención

- Inicio: asistente protagonista, estado de OpenAI y gafas, inicio manual, escucha y accesos a perfiles/voces/dispositivos/ajustes.
- Asistentes: tarjetas con avatar, personalidad y selección activa; inspiraciones opcionales en menú. Editor básico con ajustes avanzados agrupados.
- Voces: selección destacada y muestra explícita antes de la biblioteca existente; conserva motores, voces y modelos anteriores.
- Activación: servicio persistente separado de la prueba Android visible y del editor de coincidencia/tolerancia.
- Dispositivos: mantiene tarjetas DAT por modelo/capacidades, registro Meta AI, captura y análisis confirmado, sin deducir marca desde el nombre.
- Ajustes: conexión, privacidad, permisos/audio, apariencia y diagnóstico; registros con exportación y borrado confirmados.

Colores Material semánticos; el rediseño no acredita por sí solo contraste ni usabilidad física. Setup persistente, icono, BYOK, TTS y fotografía no se sustituyen. **README sin cambios en esta intervención. Los seis archivos locales previos quedan fuera de los commits.**

## Verificación automática y límites actuales

- Ejecutadas localmente: **19 pruebas JVM** reales de `ErrorJournalTest`, `MicrophoneGateTest` `WakeSessionPolicyTest` y `FaultClassifierTest`, compiladas con Kotlin/JVM y JUnit disponibles en Gradle. No usan stubs Android y no equivalen a toda la suite Gradle.
- **9 Python + 19 Node** correctas. Análisis sintáctico de **77 archivos Kotlin** correcto; no comprueba tipos Android.
- Añadidas, pendientes de Android/CI: carga nativa WebRTC/Vosk sin captura, contrato de servicio, registros/borrado desde Compose, regresión de «Atento» y navegación de inicio actualizada conservando las comprobaciones previas.
- Java 17 localizado. Gradle JVM/Lint/APK/instrumentadas bloqueado por **SDK location not found**. Descarga de Sherpa y Git HTTPS bloqueados por la red del entorno. La matriz CI conserva Android 15/API35 y añade Android 16/API36; solo publica la APK final si ambos emuladores, JVM/Lint y backend/contenedores pasan.
- No se han ejecutado captura acústica, API de pago ni sensores reales. Sin compatibilidad física nueva acreditada y sin Release estable.

## Estado de publicación

Implementación local `0328564`. Git HTTPS no conecta con GitHub y la escritura por el conector devuelve HTTP403. Master remoto sigue en `f9daabd`; Actions no tiene una ejecución del nuevo SHA. No hay APK nueva validada. No se publica una Release estable ni se reutiliza una APK anterior como evidencia de estos cambios.

## Aceptación física pendiente

| Recorrido en Nothing / 00R6 | Qué registrar | Estado |
| --- | --- | --- |
| Inicio manual sin gafas; permiso denegado; API sin red/cuota | Pantalla, motivo recuperable y registro exportado | Pendiente |
| «Atento» y «Atento Jarvis» con cada asistente | Aciertos/falsos positivos, distancia, ruido e idioma | Pendiente |
| Escucha visible, minimizada y bloqueada durante 15, 30 y 60 min | Suspensiones, temperatura, batería inicial/final y tiempo efectivo | Pendiente |
| Frase → Realtime confirmado → conversación → finalizar → escucha | Una sola sesión, micrófono liberado antes de cambio y rearme autorizado | Pendiente |
| Parada desde Inicio y notificación; cierre del proceso | Audio realmente detenido y ningún reinicio automático | Pendiente |
| Revocar micrófono/notificación; llamada; ahorro de batería | Suspensión visible, informe y reintento explícito | Pendiente |
| 00R6 conectadas; pérdida de Bluetooth; alternativa móvil | Entrada y salida comprobadas por separado; sin afirmar ruta solo por DAT | Pendiente |
| Forzar fallo recuperable y repetir el cierre original | Informe guardado, exportado y revisado sin datos privados | Pendiente |
| Fotografía → vista previa → confirmación → análisis | Imagen conservada si falla configuración; API funcional | Pendiente |

No borrar datos ni reinstalar como primer diagnóstico: se perderían la clave cifrada y las preferencias. En Nothing OS beta, los menús de batería pueden variar; comprobar los ajustes reales sin inferir que la beta sea la causa.

## Fuentes oficiales consultadas

- [Servicios de micrófono y restricciones al iniciar desde segundo plano](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start).
- [Foco de audio: apps visibles o servicio foreground al apuntar a API35+](https://developer.android.com/media/optimize/audio-focus).
- [ApplicationExitInfo](https://developer.android.com/reference/android/app/ApplicationExitInfo).
- [Vosk: modelos, tamaño, español y licencias](https://alphacephei.com/vosk/models).
- [Adaptación de vocabulario y modelos Vosk](https://alphacephei.com/vosk/adaptation).
- [API Android Vosk oficial](https://github.com/alphacep/vosk-api/tree/master/android).
- [Sherpa KWS: modelos publicados principalmente inglés/chino](https://k2-fsa.github.io/sherpa/onnx/kws/index.html); no se eligieron para prometer español sin un modelo adecuado.
- [Nothing: ajustes de uso de batería](https://support.nothing.tech/hc/en-us/articles/22754437093009-My-apps-won-t-run-What-can-I-do).

## Archivos de esta intervenci?n

- `.github/workflows/android.yml`
- `app/build.gradle.kts`
- `app/src/androidTest/java/com/pbtservers/pbtmetagpt/AudioRuntimeInstrumentedTest.kt`
- `app/src/androidTest/java/com/pbtservers/pbtmetagpt/ConversationInstrumentedTest.kt`
- `app/src/androidTest/java/com/pbtservers/pbtmetagpt/ErrorLogInstrumentedTest.kt`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/pbtservers/pbtmetagpt/AssistantShell.kt`
- `app/src/main/java/com/pbtservers/pbtmetagpt/AudioRoute.kt`
- `app/src/main/java/com/pbtservers/pbtmetagpt/ContinuousWakeEngine.kt`
- `app/src/main/java/com/pbtservers/pbtmetagpt/ContinuousWakeService.kt`
- `app/src/main/java/com/pbtservers/pbtmetagpt/DiagnosticTypes.kt`
- `app/src/main/java/com/pbtservers/pbtmetagpt/Diagnostics.kt`
- `app/src/main/java/com/pbtservers/pbtmetagpt/ErrorJournal.kt`
- `app/src/main/java/com/pbtservers/pbtmetagpt/ErrorLogScreen.kt`
- `app/src/main/java/com/pbtservers/pbtmetagpt/FaultClassifier.kt`
- `app/src/main/java/com/pbtservers/pbtmetagpt/HomeDashboard.kt`
- `app/src/main/java/com/pbtservers/pbtmetagpt/ListeningControls.kt`
- `app/src/main/java/com/pbtservers/pbtmetagpt/LocalAudioDiagnostics.kt`
- `app/src/main/java/com/pbtservers/pbtmetagpt/LocalWakeListener.kt`
- `app/src/main/java/com/pbtservers/pbtmetagpt/MainActivity.kt`
- `app/src/main/java/com/pbtservers/pbtmetagpt/MicrophoneGate.kt`
- `app/src/main/java/com/pbtservers/pbtmetagpt/PbtApplication.kt`
- `app/src/main/java/com/pbtservers/pbtmetagpt/RealtimeClient.kt`
- `app/src/main/java/com/pbtservers/pbtmetagpt/VoiceLibraryScreen.kt`
- `app/src/main/java/com/pbtservers/pbtmetagpt/WakeModel.kt`
- `app/src/main/java/com/pbtservers/pbtmetagpt/WakeSessionPolicy.kt`
- `app/src/test/java/com/pbtservers/pbtmetagpt/ErrorJournalTest.kt`
- `app/src/test/java/com/pbtservers/pbtmetagpt/FaultClassifierTest.kt`
- `app/src/test/java/com/pbtservers/pbtmetagpt/MicrophoneGateTest.kt`
- `app/src/test/java/com/pbtservers/pbtmetagpt/WakeConfigurationTest.kt`
- `app/src/test/java/com/pbtservers/pbtmetagpt/WakeSessionPolicyTest.kt`
- `docs/ESTABILIZACION_AUDIO.md`
- `docs/PROGRESO.md`
