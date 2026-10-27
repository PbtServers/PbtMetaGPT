# PbtMetaGPT · 0.4.1-dev

Ampliación experimental de **v0.4.0**: aplicación Android pública y de código abierto para conversar con OpenAI desde el móvil y explorar Ray-Ban Meta mediante el SDK oficial DAT. Interfaz y documentación en español de España. Identificador: `com.pbtservers.pbtmetagpt`.

**BYOK funciona por conexión directa: no necesitas VPS, Docker ni dominio.** Cada persona introduce su propia clave API y asume su consumo. ChatGPT Plus y la facturación de OpenAI API son independientes.

Esta es una **compilación de prueba**, no una Release estable. Consulta [PROGRESO.md](docs/PROGRESO.md) y [VALIDACION.md](docs/VALIDACION.md) para separar código implementado, pruebas automáticas y pruebas reales pendientes. No se declara compatibilidad completa con ningún modelo de Ray-Ban Meta. La conversación con credenciales reales y la escucha física siguen pendientes de validación.

## Instalar y configurar sin gafas ni backend

1. En [GitHub Actions](https://github.com/PbtServers/PbtMetaGPT/actions/workflows/android.yml), selecciona una ejecución correcta del commit que quieras probar y descarga `PbtMetaGPT-v0.4.1-dev-debug` en Artifacts. Requiere iniciar sesión; retención de APK de 14 días.
2. Descomprime e instala `app-debug.apk` en Android 12 o posterior. Es una firma debug; puede cambiar entre runners. Si Android rechaza actualizar, exporta los perfiles, desinstala la anterior y configura de nuevo. Las claves nunca se exportan.
3. Sigue la bienvenida y lee el aviso BYOK. Crea una clave de proyecto en [OpenAI API](https://platform.openai.com/api-keys), activa facturación, limita permisos y supervisa el gasto. No introduzcas tu contraseña de ChatGPT.
4. En **OpenAI**, guarda la clave cifrada y comprueba credenciales sin audio. Recibir un secreto temporal no demuestra voz, WebRTC ni hardware. Puedes comprobar, sustituir y eliminar la clave.
5. Crea un asistente **Personalizado**, sin nombre impuesto, o elige una plantilla opcional Jarvis, Friday o PbtAdmin. Guarda nombre, avatar de emoji/iniciales, cómo te llama, personalidad, instrucciones, estilo, formalidad, humor, vocabulario, idioma y extensión.
6. En **Voz** o **Biblioteca de voces**, elige motor y voz/modelo. Escucha una muestra antes de guardar. Las muestras de proveedores de pago consumen API; las locales no necesitan clave de síntesis ni backend. La conversación posterior sí utiliza OpenAI.
7. Autoriza el micrófono y pulsa **Iniciar conversación**. Espera a **Escuchando** antes de pedir algo. Puedes seguir preguntando en la misma sesión sin repetir la activación. Pausar conserva contexto; interrumpir cancela respuesta/audio; finalizar libera recursos. No hay historial persistente.

La bienvenida guía clave, comprobación, personaje, nombre, frase, voz, personalidad, permisos, gafas opcionales y conversación. Puedes modificar todo posteriormente. La duración máxima de sesión (1–60 minutos), bienvenida opcional y confirmación sonora se guardan por personaje. La duración acota consumo y recursos; no constituye un límite absoluto de gasto.

## Frases de activación por perfil

**Activación por voz** permite añadir, editar, eliminar, habilitar/deshabilitar y elegir una frase principal. Puedes añadir automáticamente el nombre o escribir la frase completa: «Atento Jarvis», «Escucha Jarvis», «Atención Jarvis», «Oye Jarvis», «Jarvis» u otra. Renombrar el asistente modifica solo las frases con nombre automático.

No hay una cuota de número de frases. Los perfiles tienen un presupuesto conjunto de 256 KiB, cada frase admite 160 caracteres y las sugerencias al reconocedor se acotan a 32 Ki caracteres por la comunicación Binder de Android. Ese límite de sugerencias no elimina frases de la comparación final.

- Se usa exclusivamente `SpeechRecognizer.createOnDeviceSpeechRecognizer`, nunca OpenAI ni un reconocedor remoto como detector. Necesitas motor local y modelo de idioma instalados. No se entrenan modelos por frase; la capacidad de reconocer nombres inventados depende del motor.
- Activa expresamente una ventana de escucha con la app visible. Hay indicador, parada inmediata, máximo de dos minutos, pausas de rearme, timeout y límite de errores. Android no diseña este reconocedor para escucha permanente; puede haber huecos y consumo de batería. No hay servicio de fondo, escucha con pantalla bloqueada ni sustitución de «Hey Meta».
- El motor no expone sensibilidad acústica. Se ofrece coincidencia textual exacta o una diferencia en la última palabra larga; se ignoran mayúsculas y tildes. La normalización inicial admite caracteres latinos/dígitos. Las frases cortas, una sola palabra o nombres parecidos aumentan falsos positivos. No es autenticación.
- Se evalúa solo el mejor resultado final completo. Se rechazan peticiones concatenadas, ambigüedad entre personajes y callbacks antiguos/duplicados. Puedes escuchar las frases de varios asistentes del **mismo idioma**; una coincidencia inequívoca selecciona el perfil y su motor/voz.
- Cada frase tiene comparación escrita y prueba local con micrófono sin abrir OpenAI. No confundas una prueba textual con precisión acústica.
- El sistema elige la ruta del reconocedor. No se atribuye a DAT ni se garantiza micrófono de las gafas. Prueba primero con Bluetooth apagado. No se guarda ni envía el audio de detección; el reconocedor se destruye antes de conectar WebRTC. Haz la petición cuando aparezca **Escuchando**.
- Una sesión iniciada por activación puede abrir otra ventana local al finalizar mientras siga autorizado y visible. Navegar o salir desactiva esa continuidad; regresar no activa sensores.

## Biblioteca y motores de síntesis

| Motor | Ruta implementada y controles |
| --- | --- |
| OpenAI Realtime | Voz nativa WebRTC, contexto de sesión, voz oficial, velocidad e instrucciones de expresión. No puede cambiar voz tras emitir audio: reconecta. |
| Android TTS | Voz que el proveedor declara instalada y local; velocidad y tono solicitados mediante su API. Necesita motor/idioma instalados; comprobar comportamiento y privacidad del proveedor. |
| OpenAI Speech | Texto → `gpt-4o-mini-tts` → PCM. Requiere clave BYOK incluso con conversación en modo servidor. **Transitorio: retirada anunciada para 06/01/2027**; predeterminado recomendado dentro de la app: Realtime. |
| API TTS externa | HTTPS configurable, modelo/voz por perfil y token cifrado separado. Debe cumplir el contrato JSON/PCM de la pantalla. Requiere autorización explícita de envío de respuestas a ese proveedor. |
| Sherpa ONNX 2.0.1 | Modelo Piper/VITS local con símbolos y pronunciaciones explícitas; CPU de 64 bits, velocidad y requisitos de RAM/almacenamiento. No admite ONNX arbitrario ni fonemizador universal. |

La **entrada de voz**, conversación OpenAI, respuesta textual, síntesis y reproducción están separadas. Con un motor alternativo Realtime produce solo texto y su pista de salida se deshabilita. Se sintetiza únicamente una respuesta completada, con un solo trabajo/reproductor y descarte de resultados tardíos al cancelar. Durante síntesis/reproducción alternativa se apaga el micrófono para evitar realimentación; puedes interrumpir con el botón y volver a escuchar. El texto completo generado permanece en el contexto remoto aunque interrumpas su lectura.

Esta ruta aumenta latencia, memoria y, según el proveedor, coste. La síntesis admite respuestas de hasta 4000 caracteres y PCM de hasta 16 MiB, con límites de tiempo. No se muestra tono o expresividad como un parámetro universal: solo controles implementados por el adaptador. Cambiar idioma de conversación no reentrena la pronunciación de una voz local.

Realtime ofrece `marin`, `cedar`, `alloy`, `ash`, `ballad`, `coral`, `echo`, `sage`, `shimmer`, `verse`. Speech añade `fable`, `onyx`, `nova`. El catálogo se valida por motor; voces externas y modelos locales usan identificadores propios. No se clonan ni prometen voces de actores o interpretaciones originales. Las plantillas de personajes son inspiración de estilo.

### Importar un modelo propio compatible

Necesitas un ZIP `sherpa-piper-phonemes-v1` con **voz.json, model.onnx, tokens.txt y lexicon.tsv**. El manifiesto contiene formato, motor/versión, idioma, licencia, procedencia, autorización, requisitos y hashes. Consulta [BIBLIOTECA_DE_VOCES.md](docs/BIBLIOTECA_DE_VOCES.md) para el esquema y la preparación mediante `tools/empaquetar_voz.py`.

La importación valida estructura, integridad, límites y metadatos antes de publicar el paquete. La inferencia se ejecuta en un proceso aislado, con runtime oficial fijado por SHA-256, sin cargar bibliotecas del paquete ni acceder a credenciales. Un modelo que pasa esa revisión puede fallar al ejecutar: **probar su muestra es una comprobación independiente**, y escucharla físicamente también.

El diccionario debe contener cada palabra/número; una respuesta fuera de vocabulario produce error sin inventar pronunciación ni cambiar de voz automáticamente. Solo se usa el hablante 0 del modelo. El botón de eliminar libera sus archivos; los perfiles que lo referencian deberán elegir otra voz. No se incluyen modelos en la APK principal ni en perfiles exportados. No se distribuyen modelos de actores clonados ni se considera una declaración de autorización prueba legal de derechos.

## Privacidad, claves y exportaciones

Las claves se cifran con AES-256-GCM, IV aleatorio, autenticación y clave no exportable de Android Keystore. OpenAI y el proveedor TTS tienen alias/almacenes separados. La clave OpenAI solo se envía al host oficial; nunca se reenvía automáticamente a una API externa. Puedes borrar el token externo y revocar la autorización del proveedor por separado. No hay claves privadas en recursos, APK, repositorio ni CI.

**Una clave permanente en un móvil puede extraerse si se compromete el dispositivo o el proceso, aunque esté cifrada en disco.** Durante una petición existe en memoria. Keystore no elimina ese riesgo: usa proyectos separados, permisos reducidos, supervisión y revocación en el proveedor ante pérdida del teléfono. Borrar localmente no revoca la clave remota. Se deshabilitan copias de seguridad, capturas de pantalla y analítica/crash reporting opcionales de DAT.

Las exportaciones de asistentes incluyen configuración y referencias de voz, nunca almacenes de claves ni datos de conexión de proveedores. Las instrucciones personales se exportan sin cifrar; revísalas. Los perfiles importados se validan y reciben identificadores nuevos; las referencias de modelos/motores deben reasociarse en otro teléfono.

No se persisten conversaciones ni audio de activación. Android TTS puede generar un WAV temporal privado, eliminado al terminar/cancelar y limpiado al iniciar tras un cierre inesperado. Las conversaciones autorizadas y fotos confirmadas se envían a OpenAI; una API TTS autorizada recibe el texto de respuesta. Cerrar no solicita borrado al proveedor. Diagnóstico exporta descripciones y códigos filtrados, no claves, URL personales, cuerpos de error, transcripciones, muestras ni fotos.

## Ray-Ban Meta y backend opcional

En **Dispositivos**, concede Bluetooth, registra con Meta AI y conecta DAT. Necesitas gafas emparejadas, Meta AI, cuenta/región/modelo/firmware compatibles y modo de desarrollador o autorización de Meta. La APK usa el identificador DAT `0` de desarrollo. La conexión solo se confirma con `STARTED`; eso no acredita audio o cámara.

La conversación usa rutas de comunicación Android, con preferencia Bluetooth opcional y alternativa del teléfono. La salida TTS usa esa misma ruta solicitada; **entrada y salida deben comprobarse por separado**. No se promete escucha permanente mediante las gafas ni acceso directo a sus micrófonos por `mwdat-speech`.

La cámara requiere captura puntual autorizada, vista previa y segunda confirmación de envío. Se cierra al capturar/cancelar/salir; no se sube vídeo. Si Meta AI pide permisos fuera de la app, vuelve a conectar y solicita la captura de nuevo. No se activan sensores al regresar.

Puedes desactivar modo directo en OpenAI para usar [backend HTTPS opcional](backend/README.md), con token opaco y clave OpenAI solo en servidor. [Ubuntu/Compose/Caddy](deploy/README.md). Los perfiles se aplican en la sesión cliente; los límites de emisión no constituyen una política inmutable de instrucciones ni garantizan gasto máximo.

## Compilación y pruebas

Java 17, Android SDK/Build Tools 36, Gradle 8.14.1, AGP 8.11.1, Kotlin 2.2.21. Mínimo API 31; destino 36. DAT core/camera 1.0.1 en Maven Central y WebRTC comunitario `150.7871.01`. Configura `local.properties` con `sdk.dir`; los datos públicos DAT opcionales no son lugar para claves OpenAI.

```powershell
python tools/preparar_sherpa.py
.\gradlew.bat --no-daemon testDebugUnitTest lintDebug assembleDebug
node --test backend/*.test.mjs
python -m unittest discover -s tools -p 'test_*.py'
python tools/comprobar_secretos.py app/build/outputs/apk/debug/app-debug.apk
# Para pruebas Android con fixture y emulador iniciado:
python tools/preparar_modelo_prueba.py
.\gradlew.bat connectedDebugAndroidTest
```

En Linux/macOS usa `./gradlew`. Los scripts de runtime/fixture descargan únicamente las versiones fijadas y verifican sus hashes. La fixture no se sube a Git ni forma parte de la APK principal. Las pruebas estructurales usan datos ficticios; la prueba nativa usa un modelo LJSpeech de procedencia revisada y solo acredita PCM, sin prueba auditiva.

[Actions](.github/workflows/android.yml) prueba JVM, Python, Node, Lint, debug, contenedores, seguridad y Android/emulador. Solo publica la APK después de superar los tres trabajos. Un commit de desarrollo, una APK de prueba y una versión estable son estados distintos. Mantén el [protocolo real](docs/PRUEBAS_REALES.md) y la [arquitectura](docs/ARQUITECTURA.md) como referencias para validar el dispositivo.

## Referencias oficiales

- [OpenAI Docs: WebRTC](https://developers.openai.com/api/docs/guides/realtime-webrtc), [sesiones](https://developers.openai.com/api/docs/guides/realtime-conversations), [secretos temporales](https://developers.openai.com/api/reference/resources/realtime/subresources/client_secrets/methods/create) y [Speech/retirada anunciada](https://developers.openai.com/api/docs/guides/text-to-speech).
- [Android: reconocimiento y límites](https://developer.android.com/reference/android/speech/SpeechRecognizer), [síntesis](https://developer.android.com/reference/android/speech/tts/TextToSpeech) y [Keystore](https://developer.android.com/privacy-and-security/keystore).
- [Meta DAT Android](https://github.com/facebook/meta-wearables-dat-android) y [Sherpa ONNX 2.0.1](https://github.com/k2-fsa/sherpa-onnx/releases/tag/v2.0.1).
