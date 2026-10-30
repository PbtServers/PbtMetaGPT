# PbtMetaGPT

<p align="center"><img src="docs/assets/icono-app.png" alt="Icono de PbtMetaGPT" width="128" height="128"></p>

<p align="center">
  <strong>Tu asistente de voz, tus reglas.</strong><br>
  Conversa desde Android o tus Ray-Ban Meta con asistentes personalizables y voces de español de España.
</p>

<p align="center">
  <a href="https://github.com/PbtServers/PbtMetaGPT/actions/workflows/android.yml"><img src="https://github.com/PbtServers/PbtMetaGPT/actions/workflows/android.yml/badge.svg" alt="Pruebas y compilación Android"></a>
  <a href="LICENSE"><img src="https://img.shields.io/github/license/PbtServers/PbtMetaGPT" alt="Licencia"></a>
  <img src="https://img.shields.io/badge/Android-12%2B-3DDC84?logo=android&logoColor=white" alt="Android 12 o posterior">
</p>

<p align="center">
  <a href="#instalación">Descargar e instalar</a> ·
  <a href="#primeros-pasos">Primeros pasos</a> ·
  <a href="#ray-ban-meta-y-micrófono">Ray-Ban Meta</a> ·
  <a href="#voces-de-españa">Voces de España</a> ·
  <a href="https://github.com/PbtServers/PbtMetaGPT/issues">Ayuda e incidencias</a>
</p>

PbtMetaGPT es una aplicación Android de código abierto para hablar con asistentes que tú configuras: nombre, personalidad, instrucciones, idioma y voz. Ofrece conversación continua, contexto entre preguntas, activación por voz, interrupción y conexión con gafas Meta. También funciona con el micrófono y altavoz del teléfono.

Puedes utilizar **GroqCloud con clave propia y sin montar un servidor**, o configurar OpenAI Realtime y un backend opcional. Las voces **Piper Davefx y Sharvard** se ejecutan localmente después de descargarlas. GroqCloud tiene cuotas y condiciones propias; **ChatGPT Plus y la API de OpenAI se facturan por separado**.

## Funciones

- Conversación continua con contexto y una sola cola de respuesta cancelable.
- Frases de activación configurables, como «Atento Jarvis», con reconocimiento local en español.
- Escucha autorizada en segundo plano mediante servicio de micrófono, notificación y controles de pausa y parada.
- Selección de entrada: automática, auricular Bluetooth de las gafas o micrófono del teléfono, con diagnóstico de la captura real.
- Voces neuronales locales de España: Davefx, Sharvard masculina y Sharvard femenina, con muestras y selección persistente.
- GroqCloud, dictado local, Groq Whisper opcional y envío de fotos a Groq Vision con confirmación.
- Apertura de la aplicación oficial ChatGPT, aprovechando «Iniciar con Voz» cuando su configuración lo permite.
- Controles de Spotify por voz: reproducir, pausar y cambiar de canción desde su sesión multimedia, también con el teléfono bloqueado.
- Perfiles de asistentes, importación y exportación sin credenciales, claves cifradas con Android Keystore.
- Opciones avanzadas: OpenAI Realtime/WebRTC, Kokoro, Qwen3-TTS remoto, RVC ONNX y paquetes de voces compatibles.

La disponibilidad de audio Bluetooth, cámara y ejecución con pantalla bloqueada depende de Android, el firmware y los permisos. Consulta la [compatibilidad y validación](docs/VALIDACION.md) y la [guía de dispositivos y escucha](docs/CANDIDATA_RAYBAN.md) para conocer el alcance comprobado y los ensayos que requieren hardware real.

## Instalación

Necesitas **Android 12 o posterior**. Los modelos de voz requieren espacio libre; la activación local necesita descargar Vosk español. Para utilizar gafas, empareja primero las Ray-Ban Meta con Meta AI y Android.

1. Abre [GitHub Actions](https://github.com/PbtServers/PbtMetaGPT/actions/workflows/android.yml) e inicia sesión en GitHub.
2. Elige una ejecución correcta de `master`, comprueba su SHA y descarga el artifact de la APK.
3. Extrae el ZIP e instala `app-debug.apk` en el teléfono. Autoriza la instalación desde esa aplicación si Android lo solicita.
4. Sigue la bienvenida y concede los permisos de las funciones que vayas a usar.

El canal de Actions distribuye **APK de prueba con firma debug**, después de superar sus controles. Los artifacts caducan y la firma puede variar entre ejecutores. Si Android impide una actualización por firma distinta, exporta antes tus perfiles; las claves no se exportan. La versión y el SHA de cada compilación se consultan en sus informes de CI.

## Primeros pasos

1. En **Servicios de IA**, configura GroqCloud u OpenAI y acepta el tratamiento correspondiente. Guarda tu clave en la aplicación; no la compartas en incidencias.
2. Crea un asistente o elige una plantilla y ajusta sus instrucciones.
3. En **Voces → Natural**, selecciona una voz de España, descarga sus recursos y pulsa **Escuchar muestra**.
4. En **Ajustes → Dispositivos → Micrófono**, elige la entrada y compruébala.
5. Pulsa **Hablar** para iniciar una conversación manual o prepara la activación por voz como se explica a continuación.

No necesitas gafas, servidor, VPS ni dominio para usar GroqCloud desde el teléfono. El backend HTTPS de OpenAI es una alternativa opcional al uso directo de una clave personal.

## Activación, conversación y pantalla bloqueada

Configura las frases de tu asistente en **Activación por voz** y descarga el **modelo español Vosk**. Habilita **Escuchar frase de activación en segundo plano** si quieres continuar al minimizar o bloquear.

Pulsa **Activar escucha** con la aplicación visible, concede micrófono y notificaciones y confirma **Autorizar y activar**. Comprueba el estado de espera y la entrada activa antes de guardar el teléfono.

| Qué dices o haces | Resultado |
| --- | --- |
| «Atento Jarvis» u otra frase guardada | Inicia una conversación con el asistente configurado |
| Una pregunta y después otra | Mantiene el contexto durante la sesión |
| «Para» mientras responde | Interrumpe y cancela el audio pendiente |
| «Termina» | Finaliza la conversación y vuelve a la espera autorizada |
| «Deja de escuchar» | Detiene también la espera de activación |
| Pausar o detener en la notificación | Suspende o termina la escucha y libera sus recursos |

La espera es local: no envía audio de fondo al proveedor. Los nombres y frases deben ser reconocibles por el vocabulario del modelo; no se garantiza cualquier nombre inventado. La modalidad de activación visible utiliza el reconocedor on-device de Android; el servicio continuo utiliza Vosk y detección de actividad de voz. No controla «Hey Meta».

Una llamada, un cambio de ruta, permisos retirados o una interrupción de audio pueden suspender la escucha. Comprueba la causa y reactívala expresamente. No se reinicia sola tras un cierre forzado ni por reconectar las gafas. Si deshabilitas segundo plano, salir de la aplicación detiene la espera y pausa la conversación.

En el Nothing Phone revisa permisos, notificaciones y restricciones de batería desde Ajustes de Android. La aplicación no cambia esas opciones por ti. Los [procedimientos de validación física](docs/CANDIDATA_RAYBAN.md) incluyen bloqueo prolongado, Doze, llamadas, reconexión, consumo y latencia.

## Ray-Ban Meta y micrófono

1. Empareja las gafas en Meta AI y Bluetooth de Android. Completa los requisitos de desarrollador y autorización de Meta DAT que correspondan a tu instalación.
2. En **Ajustes → Dispositivos**, concede Bluetooth, registra PbtMetaGPT y pulsa **Conectar gafas Meta**.
3. En **Micrófono**, elige **Automático**, identifica tus gafas entre las rutas Bluetooth disponibles o selecciona **Teléfono**.
4. Pulsa **Probar micrófono de las gafas** y pronuncia una frase. Comprueba el indicador de nivel, la transcripción local y el nombre/tipo de la entrada realmente utilizada. Comprueba la salida aparte con **Probar sonido**.
5. Activa la escucha antes de pulsar Inicio o bloquear.

**Bluetooth conectado, sesión DAT activa y micrófono en uso son estados diferentes.** La captura utiliza una ruta de comunicación HFP/LE compatible publicada por Android; A2DP no proporciona entrada de micrófono. Si la ruta externa no se confirma, la captura se pausa sin cambiar ocultamente al teléfono. La prueba local no guarda grabaciones ni envía audio a Internet.

El controlador DAT pertenece a la aplicación, no a una pantalla. Con el servicio autorizado activo, pulsar Inicio o recrear la interfaz conserva su sesión; la cámara se cierra al ocultarla. La recuperación temporal está limitada y no vuelve a activar sensores. Algunos firmwares presentan conflictos entre HFP y DAT/cámara; puede ser necesario pausar audio y reconectar DAT para una foto. Consulta los [estados, diagnóstico y límites del SDK](docs/CANDIDATA_RAYBAN.md#conectar-y-conservar-la-sesión).

La cámara requiere captura explícita, permiso y vista previa. Enviar una foto a Groq Vision u OpenAI necesita una segunda confirmación. No se captura ni se transmite vídeo permanentemente.

## Abrir ChatGPT en voz

Puedes decir **«Abre GPT Live»**, **«Abre ChatGPT»**, **«Inicia ChatGPT en voz»** o **«Quiero hablar con ChatGPT»**. PbtMetaGPT cancela su respuesta, detiene la escucha y libera micrófono y foco antes de abrir la aplicación oficial.

En ChatGPT, activa **Ajustes → Voz → Iniciar con Voz**, si aparece en tu versión. La [función oficial](https://help.openai.com/en/articles/20001274-chatgpt-voice) inicia Voz en una conversación nueva o vacía; una conversación existente puede necesitar una acción manual.

No hay una interfaz pública documentada localizada que garantice iniciar Live desde otra aplicación. Se utiliza la entrada pública de ChatGPT, sin pulsaciones simuladas ni componentes privados. Si Android impide abrirla desde segundo plano o con el móvil bloqueado, utiliza la acción de la notificación. Abrir ChatGPT no acredita que Voz se haya iniciado. Al volver, pulsa **Hablar** o **Activar escucha** para recuperar el micrófono.

## Spotify con la pantalla bloqueada

En **Ajustes → Dispositivos → Música**, activa **Controlar Spotify**, lee el consentimiento y pulsa **Gestionar acceso de música en Android**. Habilita **PbtMetaGPT · música** en el acceso especial a notificaciones. Android concede un permiso amplio; la aplicación no procesa ni almacena el contenido de las notificaciones. Puedes desactivar los controles y revocar ese acceso cuando quieras.

Abre Spotify, reproduce una canción una vez y vuelve a PbtMetaGPT. Activa la escucha autorizada en segundo plano y bloquea el teléfono. En espera, usa la frase de tu asistente antes de cada orden:

- «Atento Jarvis, reproduce música» o «Atento Jarvis, pon Spotify».
- «Atento Jarvis, pausa la música».
- «Atento Jarvis, siguiente canción».
- «Atento Jarvis, canción anterior».

Durante una conversación también se admiten las órdenes completas sin repetir la activación. La orden cancela la respuesta del asistente antes de ceder el audio y vuelve a la espera ya autorizada. **«Para» interrumpe al asistente**; para Spotify utiliza «pausa la música».

Los controles actúan solo sobre la sesión de `com.spotify.music`, sin abrir actividades desde el bloqueo. Si Spotify no mantiene una sesión o no ofrece un control, la aplicación lo indica; no inicia una canción o playlist nueva por una ruta privada. Las restricciones de saltos, cuenta y conexión dependen de Spotify. La captura HFP de las gafas puede reducir la calidad de la música; la convivencia Bluetooth necesita validación física.

## Voces de España

En **Voces → Natural** tienes tres voces Piper de español de España (`es_ES`, calidad medium):

| Voz | Recursos | Uso |
| --- | --- | --- |
| Davefx | 63,2 MB; reserva de instalación de 256 MiB | Una voz y su modelo |
| Sharvard masculina | 76,7 MB compartidos; reserva de 300 MiB | Hablante masculino |
| Sharvard femenina | El mismo modelo Sharvard | Hablante femenino; sin duplicar descarga |

Selecciona una voz con un toque: la elección se guarda. La descarga y la reproducción de muestras requieren tu acción. **Cambiar entre las dos Sharvard reutiliza el mismo modelo ONNX**. Puedes comprobar o eliminar los recursos desde sus controles; cada descarga verifica SHA-256.

**Rápida** ofrece Android TTS. **Avanzada** conserva Kokoro, Qwen3-TTS remoto, RVC ONNX e importaciones, con su motor identificado. Los perfiles Kokoro anteriores se conservan. Las voces Android no se presentan como Piper ni Kokoro.

La [documentación de voces y licencias](docs/CANDIDATA_RAYBAN.md#voces-de-españa-solicitadas) detalla procedencia, hablantes, hashes y atribución. Los modelos no se incluyen en la APK. Los paquetes importados deben cumplir el [formato de la biblioteca](docs/BIBLIOTECA_DE_VOCES.md).

RVC es una conversión opcional que añade procesamiento. Los `.pth` y `.index` se pueden almacenar, pero no se ejecutan: el motor necesita los componentes ONNX compatibles. El índice FAISS no interviene en la inferencia actual. Consulta [RVC local en Android](docs/RVC_LOCAL_ANDROID.md).

## Internet y funcionamiento local

| Función | Conexión |
| --- | --- |
| Activación Vosk, dictado Vosk y prueba de micrófono | Local después de descargar el modelo |
| Piper, Kokoro y RVC compatibles | Local después de instalar sus recursos |
| Perfiles, selección de voces y ajustes | Local |
| Controles de la sesión de Spotify | Local; reproducir contenido depende de Spotify y de sus descargas/conexión |
| GroqCloud, Groq Vision, OpenAI y ChatGPT oficial | Internet |
| Groq Whisper | Internet y consentimiento adicional para enviar el audio de la petición |
| Qwen3-TTS remoto y API TTS externa | Internet; reciben el texto autorizado |
| Descarga inicial de modelos | Internet, con acción expresa |

La activación y la voz local no convierten a Groq u OpenAI en un modelo de conversación offline. Android TTS y el reconocimiento on-device dependen de los componentes e idiomas instalados.

## Seguridad y privacidad

Las claves personales se cifran en reposo con **AES-GCM y Android Keystore**, con almacenes separados para los proveedores. No se incluyen en el código, la APK, los perfiles exportados ni los registros. Un dispositivo comprometido puede exponer credenciales durante su uso: utiliza presupuestos limitados y revoca las claves desde el proveedor si pierdes el teléfono.

La espera no almacena grabaciones permanentes. El audio de dictado solo se envía a Groq Whisper si activas esa opción. Las conversaciones, fotos y textos de síntesis autorizados se envían al servicio elegido. Revisa sus condiciones antes de activarlos.

La exportación de perfiles no contiene claves; su texto personal no se cifra. Revisa cualquier archivo o captura antes de compartirlo. El [backend opcional](backend/README.md) permite evitar el uso directo de BYOK desde el cliente.

## Compilar y probar

Usa **JDK 17, Android SDK 36, NDK 28.2.13676358 y CMake 3.22.1**. La versión mínima es API31; el objetivo y compilación son API36. El proyecto utiliza Kotlin, Jetpack Compose, DAT, WebRTC, ONNX Runtime y Sherpa. Las versiones exactas están en [Gradle](app/build.gradle.kts).

Configura `ANDROID_HOME` o `sdk.dir` en `local.properties`. La preparación nativa requiere las herramientas del host indicadas por sus scripts, incluido `patchelf` para Sherpa. No añadas claves privadas a los recursos de Android.

```bash
git clone https://github.com/PbtServers/PbtMetaGPT.git
cd PbtMetaGPT
python tools/preparar_fonemizador.py
python tools/preparar_sherpa.py
./gradlew testDebugUnitTest lintDebug assembleDebug
node --test backend/*.test.mjs
python -m unittest discover -s tools -p 'test_*.py'
python tools/comprobar_secretos.py app/build/outputs/apk/debug/app-debug.apk
```

En Windows utiliza `gradlew.bat` y prepara las herramientas nativas correspondientes. La APK se genera en `app/build/outputs/apk/debug/app-debug.apk`. Para pruebas instrumentadas con emulador:

```bash
python tools/preparar_modelo_prueba.py
python tools/preparar_kokoro_prueba.py
./gradlew connectedDebugAndroidTest
```

La CI comprueba JVM, Lint, compilación, Python, backend, seguridad e instrumentación en Android 15/API35 y Android 16/API36 antes de publicar la APK. **Un emulador no acredita el micrófono de las gafas, Bluetooth, calidad audible ni consumo del teléfono.** Los resultados por compilación están en [Validación](docs/VALIDACION.md) y [Seguimiento técnico](docs/PROGRESO.md).

## Documentación y ayuda

| Documento | Contenido |
| --- | --- |
| [Dispositivos, escucha y voces](docs/CANDIDATA_RAYBAN.md) | Configuración, diagnóstico y pruebas en Nothing/Ray-Ban |
| [Conversación continua](docs/CONVERSACION_CONTINUA.md) | Estados, órdenes e interrupción |
| [Validación](docs/VALIDACION.md) | Evidencias automáticas, emulador y hardware |
| [Arquitectura](docs/ARQUITECTURA.md) | Componentes y contratos |
| [Biblioteca de voces](docs/BIBLIOTECA_DE_VOCES.md) | Paquetes y compatibilidad |
| [Pruebas reales](docs/PRUEBAS_REALES.md) | Reproducción y aceptación con dispositivos |
| [Desarrollo](docs/DESARROLLO.md) | Contribución y herramientas |

Para informar de un fallo, abre una [incidencia](https://github.com/PbtServers/PbtMetaGPT/issues) con versión/SHA, Android, pasos y diagnóstico sanitizado. No adjuntes claves, conversaciones ni imágenes personales. Consulta [CONTRIBUTING.md](CONTRIBUTING.md) para contribuir.

Proyecto de **PbtServers**, independiente de Meta, OpenAI, GroqCloud y Marvel. «Jarvis» es una referencia de personalidad, no una voz licenciada ni una integración oficial de Marvel. La [licencia del repositorio](LICENSE) no concede derechos sobre marcas, voces o modelos de terceros.
