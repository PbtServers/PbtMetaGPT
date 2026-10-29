# Candidata experimental: gafas, micrófono, bloqueo y voces de España

Intervención iniciada el 29/10/2026, 22:08:40 UTC. Evidencias y SHA de cada APK en [PROGRESO.md](PROGRESO.md). La compilación/emulador no acredita audio Bluetooth ni aceptación del Nothing Phone 3a Lite. No es una Release estable.

## Conectar y conservar la sesión

1. Empareja las gafas en Meta AI y en Bluetooth de Android. Comprueba el modo de desarrollador/DAT o la autorización de la aplicación.
2. En **Ajustes → Dispositivos**, permite Bluetooth, registra la aplicación en Meta AI y pulsa **Conectar gafas Meta**. El registro solo se confirma con el estado del SDK, no con el enlace de retorno.
3. Configura un asistente/proveedor y habilita **Escuchar frase de activación en segundo plano**. Descarga Vosk español desde Activación por voz.
4. Pulsa **Activar escucha** con la aplicación visible y confirma el consentimiento. Concede micrófono y notificaciones.
5. Espera el estado **Esperando frase de activación** y comprueba la entrada activa antes de pulsar Inicio o bloquear.

Hay tres estados diferentes:

| Estado | Evidencia que usa la app | Qué no demuestra |
| --- | --- | --- |
| Bluetooth del sistema | Adaptador/rutas HFP o LE disponibles | No acredita sesión DAT ni PCM de las gafas |
| Enlace y sesión DAT | Metadatos `linkState`, compatibilidad y `DeviceSessionState.STARTED` | No acredita micrófono ni altavoz |
| Captura de audio | Dispositivo real de AudioRecord/configuración de grabación WebRTC y frecuencia PCM | No identifica por sí solo un modelo comercial ni confirma inteligibilidad física |

`MetaGlasses`, su StateFlow y observadores pertenecen a `PbtApplication`, con contexto de aplicación y corrutinas del proceso. No retienen Activity. Registro/desregistro reciben Activity únicamente durante la acción pública. Recrear la interfaz conserva el controlador. Ocultarla cancela siempre la cámara y conserva DAT si el servicio autorizado permanece activo. Sin ese servicio, salir libera DAT; volver no reconecta ni inicia sensores.

La recuperación DAT permite como máximo tres intentos de 2/5/15 segundos por autorización explícita, con enlace compatible y propietario activo. No reinicia cámara. Se frena durante HFP si DAT termina, porque Meta ha confirmado [conflictos de firmware entre HFP y sesión/cámara](https://github.com/facebook/meta-wearables-dat-android/discussions/130). No se introduce audio DAT experimental como sustitución ni se promete simultaneidad cámara/HFP en esos firmwares. Para una foto, pausa el audio si es necesario y reconecta DAT expresamente; se mantienen consentimiento de captura, permiso y segunda confirmación de envío.

Detener escucha/conversación es distinto de ocultar la interfaz. Un cierre forzado, proceso terminado o permiso retirado puede terminar el servicio. No se reinicia desde BOOT ni en bucle. Al detener el servicio fuera de la interfaz se libera también DAT; la conexión Bluetooth del sistema puede seguir existiendo.

Referencia contrastada: [CameraAccess oficial y manifiesto](https://github.com/facebook/meta-wearables-dat-android/tree/main/samples/CameraAccess), DAT 1.0.1. No se deduce captura de micrófono de la presencia de un artefacto Maven.

## Elegir y comprobar el micrófono

En **Ajustes → Dispositivos → Micrófono**:

- **Automático** prefiere una ruta Bluetooth de comunicación disponible; si no existe, utiliza el teléfono salvo que se hubiera exigido Bluetooth mediante la preferencia anterior.
- **Ray-Ban Meta / Bluetooth · nombre Android** permite identificar expresamente el auricular de las gafas. No se interpreta el nombre como prueba de marca: podría ser otro auricular. Sin ruta HFP/LE no aparece como disponible; A2DP no es micrófono.
- **Teléfono** solicita micrófono integrado y la ruta de comunicación del móvil.

Cambiar la selección detiene la captura y guarda la preferencia; hay que activar/reanudar expresamente. Una selección externa no cae ocultamente al micrófono del móvil.

Selecciona el nombre de tus gafas y pulsa **Probar micrófono de las gafas**. Necesita Vosk español instalado. Durante hasta 12 segundos se muestran nivel, nombre/tipo/id real de entrada y transcripción local. Pronuncia una frase sintética, por ejemplo «En Zaragoza hay cinco plazas». Aleja el teléfono y compara la entrada informada con el auricular seleccionado. La transcripción solo vive en pantalla y se borra al salir/detener; no se crea WAV, no se exporta ni se envía a un proveedor. Una barra con señal no basta: confirma también la entrada y que la frase se entienda.

La ruta usa `MODE_IN_COMMUNICATION`, foco y [`setCommunicationDevice`](https://developer.android.com/reference/android/media/AudioManager#setCommunicationDevice(android.media.AudioDeviceInfo)). Se solicita además [`AudioRecord.setPreferredDevice`](https://developer.android.com/reference/android/media/AudioRecord#setPreferredDevice(android.media.AudioDeviceInfo)) o `JavaAudioDeviceModule.setPreferredInputDevice`, verificado en el AAR WebRTC usado. La selección es una solicitud: se comprueba la captura real y se descarta PCM incompatible. AudioRecord entrega PCM16 mono a 16 kHz para Vosk; Android realiza la conversión desde el dispositivo. WebRTC publica su frecuencia de cliente real, sin asumir 16 kHz. La salida se comprueba aparte con **Probar sonido**.

Un coordinador de audio por proceso conserva la ruta/foco con concesiones. El servicio la retiene entre activación, confirmación, dictado y TTS; cerrar un turno no desconecta HFP. `MicrophoneGate` mantiene exclusión de capturadores. «Para» comparte el capturador WebRTC o el capturador local correspondiente; no abre un segundo micrófono simultáneo. AEC/interrupción acústica necesitan prueba física.

Los puertos de entrada y salida pueden tener nombres e ids distintos. Android documenta que al elegir el destino de comunicación selecciona automáticamente su fuente correspondiente. Se exige ruta confirmada y tipo de entrada compatible; si ambos puertos exponen dirección, se rechaza una dirección distinta. Esas direcciones solo se usan en RAM, sin persistir ni registrar. Si Android las oculta, el diagnóstico muestra el nombre real de entrada y la identidad se confirma mediante el ensayo físico. No se confunde una diferencia de nombres de puerto con una pérdida del micrófono.

## Usar el teléfono bloqueado

Con el servicio ya iniciado y entrada comprobada: Inicio → bloquear → «Atento Jarvis» → pregunta → respuesta → siguiente pregunta → «Para» → «Termina». «Termina» vuelve a la espera autorizada; **Deja de escuchar** y **Detener escucha y conversación** terminan también esa espera.

El servicio privado declara `microphone|connectedDevice`; este último tipo se activa solo con permiso Bluetooth. No tiene `START_STICKY`. La notificación permanente permite **Pausar** y **Detener escucha y conversación**. Una llamada, pérdida/cambio de ruta, captura silenciada o permiso retirado suspende; no se recupera por volver a primer plano o reconectar. Comprueba la causa y reactiva desde la interfaz. Pausar una conversación revoca el rearme y libera la ruta de sesión y CPU.

La espera procesa RMS/VAD ligero y conserva 300 ms circulares en RAM. Vosk recibe voz con ese prefijo, no silencio continuo; una intervención se limita a 20 segundos. No es un KWS neuronal dedicado ni se acredita consumo físico bajo sin medir. El modelo permanece en RAM para evitar descargar/cargar en cada frase. No ejecuta Groq, OpenAI, Kokoro, Qwen ni RVC en silencio. Un único wakelock parcial mantiene el procesamiento autorizado con pantalla bloqueada y se libera al suspender/detener/error.

En el Nothing revisa permisos, notificaciones y restricciones de batería de PbtMetaGPT desde Ajustes de Android. Los nombres varían con Nothing OS. Si el sistema suspende el servicio, comprueba esa restricción y repite el ensayo; la app no cambia ajustes ni solicita exclusión automática de Doze. No se promete sobrevivir al cierre forzado ni a la finalización del sistema.

Se registran CPU del proceso por ventanas de 30 s, PSS y tipo numérico de entrada, sin voz/texto/nombres Bluetooth. Usa **Registros de errores** para diagnóstico sanitizado y [resumir_latencias_escucha.py](../tools/resumir_latencias_escucha.py) para las medidas disponibles. PSS no es un máximo y CPU no equivale a batería.

## ChatGPT oficial y «Iniciar con Voz»

Órdenes completas admitidas: «Abre GPT Live», «Abre ChatGPT», «Inicia ChatGPT en voz», «Quiero hablar con ChatGPT»; se conservan las variantes anteriores. Se cancelan respuesta/colas, se revoca escucha y se espera la liberación de capturador, ruta y foco antes de abrir la aplicación oficial `com.openai.chatgpt` mediante su launcher público.

En ChatGPT abre **Ajustes → Voz → Iniciar con Voz**, si tu versión/cuenta lo ofrece. Selecciona Live si aparece esa elección. OpenAI documenta que al abrir una **conversación nueva o vacía** se inicia Voz automáticamente. Si se recupera un chat con mensajes, crea uno nuevo o pulsa Voz dentro de ChatGPT. PbtMetaGPT no puede activar ni inspeccionar esa preferencia ni confirmar que Live comenzó. [Fuente oficial](https://help.openai.com/en/articles/20001274-chatgpt-voice).

La búsqueda en documentación oficial de OpenAI no ha establecido un intent/deep link, acción de sistema o acceso directo invocable por otra app que garantice Live. La presencia de un acceso directo en un launcher no autoriza invocar componentes internos. Se usa la entrada pública y la configuración oficial; sin accesibilidad, pulsaciones simuladas, componentes privados ni rutas inventadas.

Con pantalla bloqueada o app en segundo plano se ofrece una notificación con acción de apertura. Si las notificaciones están bloqueadas se explica cómo abrir desde la interfaz. Puede ser necesario desbloquear para esa transición entre apps. Al volver, pulsa **Hablar** o **Activar escucha**: no se compite automáticamente con el micrófono de ChatGPT. ChatGPT requiere su propia cuenta/conexión; no usa la clave API de PbtMetaGPT. Plus y API tienen facturación independiente.

## Voces de España solicitadas

La aclaración del usuario sustituye la ampliación inicial de Kokoro: **Natural** ofrece Piper de España; Kokoro se conserva en **Avanzada** para perfiles anteriores.

| Voz | Variante publicada | Modelo | Recursos principales |
| --- | --- | --- | --- |
| Davefx | es_ES, medium, 22.050 Hz | Individual, 63,2 MB | Datos CC0; modelo publicado MIT |
| Sharvard · masculina | es_ES, medium, 22.050 Hz; M=0 | Compartido, 76,7 MB | Datos CC BY 3.0; MODEL_CARD y JSON fijados |
| Sharvard · femenina | es_ES, medium, 22.050 Hz; F=1 | El mismo que masculina | No duplica pesos ni sesión al cambiar M/F |

En **Voces → Natural** selecciona una voz con un toque; se guarda para el asistente. Pulsa la descarga explícita del modelo elegido y después **Escuchar muestra**. Se indican instalación, tamaño, procedencia e integridad; puedes cancelar, comprobar SHA-256 o eliminar recursos. Davefx reserva 256 MiB y Sharvard 300 MiB para instalación; no son cifras de RAM máxima. Solo se mantiene un modelo Piper activo; pasar entre modelos distintos libera el anterior. RVC sigue siendo optativo y añade coste de inferencia.

Sharvard está fijado a `5041a7a49ae5b3a0f30e6837769eb03fdbb231bd` de rhasspy/piper-voices. ONNX SHA-256 `40febfb1679c69a4505ff311dc136e121e3419a13a290ef264fdf43ddedd0fb1`; configuración `7438c9b699c72b0c3388dae1b68d3f364dc66a2150fe554a1c11f03372957b2c`. Se comprueban también MODEL_CARD y los datos de fonemización. [Modelo y ficha](https://huggingface.co/rhasspy/piper-voices/tree/main/es/es_ES/sharvard/medium).

Atribución: Aubanel, Vincent; García Lecumberri, Maria Luisa; Cooke, Martin (2014), **Sharvard_IJA**, LISTA Consortium, [DOI 10.7488/ds/75](https://datashare.ed.ac.uk/handle/10283/574). El catálogo declara ajuste a partir de Lessac. El frontend eSpeak NG es GPL-3.0-or-later. La revisión conjunta de licencias/runtime y condiciones DAT sigue siendo obligatoria antes de una distribución estable; no se incluyen pesos en la APK principal.

Español/acento se basa en la ficha `es_ES`; naturalidad, preferencia de timbre y pronunciación audible se deben escuchar. Las pruebas PCM/ASR no sustituyen esa valoración. Piper deletrea GPT/ChatGPT en español antes de fonemizar; conserva el texto presentado y las negaciones.

## Internet y funcionamiento local

| Función | Después de instalar recursos |
| --- | --- |
| VAD, Vosk, comandos, diagnóstico de micrófono | Local, sin Internet |
| Piper/Kokoro, Android TTS offline y RVC ONNX con modelos instalados | Local; calidad/rendimiento según motor y dispositivo |
| GroqCloud/Groq Vision, OpenAI Realtime/visión, Qwen remoto | Requieren Internet, consentimiento y condiciones del proveedor |
| Descargas voluntarias de modelos | Requieren Internet; revisión fija y SHA-256 |
| Registro/servicios Meta y ChatGPT oficial | Dependencias propias de esas aplicaciones; no se garantiza funcionamiento íntegramente offline |

No se ha añadido un modelo de conversación LLM offline: una voz local no convierte en local al proveedor conversacional.

## Aceptación física pendiente

Registra SHA/APK, Android 15/16 y versión exacta de Nothing OS, Meta AI, firmware y modelo real. No guardes claves, conversaciones ni imágenes en Git.

| Ensayo en Nothing/Ray-Ban | Resultado exigido | Estado |
| --- | --- | --- |
| Visible y pantalla encendida | Entrada de las gafas y salida confirmadas por separado | Pendiente |
| Inicio y otra aplicación | Servicio/notificación permanecen; DAT no se cierra por la Activity | Pendiente |
| Bloqueado 5, 15 y 30 minutos | Activación y respuesta sin desbloquear | Pendiente |
| Tres ciclos de apagar/encender pantalla | Sin reinicio de sensores por el retorno a la UI | Pendiente |
| Veinte activaciones y cinco preguntas con contexto | Frase reconocida, contexto continuo y sin audio antiguo | Pendiente |
| Veinte «Para» durante TTS/procesamiento | Cancela cola; medir parada audible externa y falsos positivos | Pendiente |
| «Termina», repetir tres conversaciones | Rearme autorizado sin dos capturadores | Pendiente |
| «Deja de escuchar» y detener por notificación | Micrófono/CPU liberados; no se reactiva al volver | Pendiente |
| Desconectar/reconectar gafas | Estados DAT/audio distintos; reintentos finitos y reanudación expresa | Pendiente |
| Elegir Teléfono tras pérdida externa | Entrada real integrada y preferencias persistentes | Pendiente |
| Llamada entrante, retirar micrófono/notificaciones | Pausa/liberación; sin captura oculta ni reinicio | Pendiente |
| ChatGPT instalado/ausente; Voz activada/desactivada; chat nuevo/con mensajes | Mensaje exacto, micrófono libre y acción legítima; retorno sin competencia | Pendiente |
| Davefx/Sharvard M/F offline y con RVC autorizado | Muestras distintas/comprensibles; sin volver a descargar al cambiar M/F | Pendiente |
| Cámara y confirmaciones | Captura puntual visible; ningún envío o rearme en segundo plano | Pendiente |
| Batería, temperatura y estabilidad | Comparar reposo/escucha durante al menos 30 min, repetir condiciones y registrar CPU/PSS | Pendiente |

Emulador API35/API36 comprueba permisos, servicio y bloqueo simulado, captura del emulador, persistencia, JNI/ORT, inferencia real y cancelación. **Mock Device Kit no ejecutado**. Ninguno acredita las filas físicas. Las suites y barreras de publicación se mantienen; consulta [VALIDACION.md](VALIDACION.md).
