# PbtMetaGPT · v0.4.0

Aplicación Android de código abierto para conversar con OpenAI Realtime desde el móvil y explorar la integración con Ray-Ban Meta mediante el SDK oficial Meta Wearables DAT. Interfaz y documentación en español de España. Identificador: `com.pbtservers.pbtmetagpt`.

**El modo directo BYOK no necesita VPS, Docker ni dominio. Cada persona utiliza y paga su propia API de OpenAI.** La suscripción ChatGPT y la facturación de OpenAI API son independientes.

Las funciones están implementadas en código; la validación automática se registra en [VALIDACION.md](docs/VALIDACION.md). No se han realizado llamadas con claves reales, reconocimiento hablado ni pruebas físicas de micrófono, altavoz o gafas durante este desarrollo. No se certifica compatibilidad con ningún modelo de Ray-Ban Meta.

## Instalar y empezar sin gafas ni servidor

1. Abre [GitHub Actions](https://github.com/PbtServers/PbtMetaGPT/actions/workflows/android.yml), selecciona una ejecución correcta del commit deseado y descarga `PbtMetaGPT-v0.4.0-debug` en **Artifacts** (requiere iniciar sesión en GitHub, retención de 14 días).
2. Descomprime el ZIP e instala `app-debug.apk` en Android 12 o posterior. Autoriza la instalación desde esa fuente. Es una APK de desarrollo, no una publicación firmada para producción. La firma debug de distintos runners puede cambiar; si Android rechaza actualizar, tendrás que desinstalar la anterior y configurar de nuevo los datos locales.
3. Sigue la bienvenida y lee el aviso BYOK. Crea una clave de proyecto en [OpenAI API](https://platform.openai.com/api-keys), habilita facturación de API, limita sus permisos y supervisa el gasto. No introduzcas tu contraseña de ChatGPT.
4. En **OpenAI**, guarda tu clave personal y pulsa **Comprobar credenciales sin audio**. El resultado confirma únicamente la emisión de una credencial temporal; la voz se verifica por separado. Los errores se muestran sin revelar la respuesta original del proveedor.
5. En **Asistentes**, elige **Crear Personalizado** y escribe, por ejemplo, **Jarvis**. No se impone ningún nombre. Guarda el nombre, cómo quieres que se dirija a ti, personalidad, descripción, forma de hablar, formalidad, humor, vocabulario e instrucciones.
6. En **Voz**, guarda una voz oficial y su velocidad. Puedes escuchar una muestra con el micrófono apagado; consume API. Los cambios se aplican en una sesión nueva. Las instrucciones de expresión son preferencias, no una garantía de imitación.
7. Autoriza el micrófono y pulsa **Iniciar conversación** en Inicio o Conversación. Sin Bluetooth se solicita el altavoz del móvil. Espera a **Escuchando** antes de hablar. Pregunta varias veces sin reconectar: el contexto permanece durante la sesión.
8. **Pausar** mantiene la sesión sin capturar; **Interrumpir** cancela la respuesta; **Finalizar** o salir de la app libera recursos y contexto local. No se guardan conversaciones ni audio en archivos.

La configuración inicial guía bienvenida, privacidad, clave, comprobación, personaje, nombre, frase, voz, personalidad, permisos, gafas opcionales y prueba de conversación. Puedes volver a los pasos anteriores y utilizar los ajustes posteriormente.

## «Atento [nombre]»

En **Activación por voz**, selecciona tu asistente, concede el permiso y autoriza expresamente la escucha local. Activa una ventana de espera y di únicamente **«Atento Jarvis»**, si así se llama tu asistente. Espera la confirmación visual de conexión antes de hacer la petición. La confirmación sonora es opcional.

- Solo se crea `SpeechRecognizer.createOnDeviceSpeechRecognizer`; no hay fallback remoto ni reconocimiento continuo con OpenAI. Se necesita un motor local y su modelo de idioma instalados por el sistema.
- No se entrenan modelos por personaje. El nombre se compara con el mejor resultado final, ignorando mayúsculas y tildes. Se tolera una edición en nombres de al menos cinco caracteres y una sola palabra; «Atento» debe reconocerse completo. Se rechazan frases concatenadas y resultados parciales. Puede haber falsos positivos entre nombres muy parecidos y falsos negativos con nombres inventados; no es autenticación.
- Ventanas de hasta dos minutos, reinicios con pausas y límite de intentos sin resultado. Android no diseña este reconocedor para escucha permanente: puede haber huecos, consumo de batería, modelos ausentes y restricciones del fabricante. El mecanismo requiere pruebas habladas en cada teléfono.
- El reconocedor elige la ruta de captura Android. No se atribuye su entrada a las gafas ni se garantiza una ruta concreta. Prueba primero con Bluetooth desconectado; la prueba local de micrófono de Diagnóstico corresponde a otro capturador y no acredita esta ruta.
- Al reconocer la frase se destruye el reconocedor antes de abrir WebRTC. No se conserva ni se envía a OpenAI el audio de detección; haz la petición después de que conecte. Durante la conversación no repitas la frase.
- Finalizar una sesión activada por voz vuelve a abrir una ventana local mientras sigan el consentimiento y la app visible. Navegar a otra pantalla o salir desactiva esa escucha; volver no activa sensores. El control de desactivación inmediata está en Activación por voz. El indicador de escucha permanece visible.
- No hay servicio de fondo, escucha con la pantalla bloqueada, modificación del firmware ni sustitución de «Hey Meta». Si no hay reconocedor compatible, utiliza el botón manual.

## Perfiles y voces

Puedes crear, editar, seleccionar, duplicar, eliminar, exportar e importar hasta 50 asistentes. Jarvis, Friday y PbtAdmin son plantillas opcionales de estilo; no reproducen identidades, interpretaciones ni voces originales. **Personalizado** empieza sin nombre.

Los perfiles incluyen descripción, instrucciones, personalidad, tono, tratamiento, humor, vocabulario, voz, idioma y extensión habitual. Los archivos JSON se seleccionan con el gestor de documentos de Android. La importación valida versión, tamaño, campos y valores; asigna nuevos identificadores y no sustituye perfiles existentes. La exportación incluye únicamente esos campos, nunca el almacén de claves; las instrucciones personales se exportan sin cifrar. No pegues credenciales en ningún campo de personalidad.

El modelo fijado es `gpt-realtime`. Voces documentadas: `marin`, `cedar`, `alloy`, `ash`, `ballad`, `coral`, `echo`, `sage`, `shimmer`, `verse`. La velocidad admite 0,25–1,5×. No se muestran controles de tono acústico, clonación ni parámetros no soportados. El perfil se congela al iniciar; la captura espera la confirmación `session.updated`. La disponibilidad efectiva depende del acceso de tu proyecto y de la API.

## Privacidad y BYOK

La clave personal se cifra mediante AES-GCM con IV aleatorio por escritura y datos asociados; la clave de cifrado no exportable reside en Android Keystore. Puedes sustituirla y eliminarla en **OpenAI**; ambas acciones cierran sesiones. La eliminación borra el registro cifrado y su alias de Keystore. Si este queda invalidado, vuelve a introducir tu clave. Se deshabilitan las copias de seguridad y las capturas de pantalla del sistema.

**Una clave permanente en un cliente móvil puede extraerse si se compromete el proceso o el dispositivo, aunque esté cifrada en disco.** Durante la solicitud existe temporalmente en memoria y se transmite exclusivamente por HTTPS a OpenAI. Keystore no elimina ese riesgo. Usa proyectos separados, permisos reducidos, supervisión y revocación inmediata ante pérdida del teléfono. Los presupuestos de API no deben asumirse como un corte absoluto de gasto.

No se incluyen claves privadas en código, recursos, APK, logs ni CI. No hay interceptores HTTP de registro. Diagnóstico exporta observaciones y códigos filtrados, no cuerpos del proveedor, claves, URL personales, nombres de dispositivos, transcripciones ni imágenes. La comprobación de patrones de secretos es una protección adicional y no detecta todos los formatos posibles.

El audio de conversaciones autorizadas y las imágenes confirmadas se envían a OpenAI. Sus condiciones determinan el tratamiento remoto; cerrar la sesión no solicita borrado al proveedor. Los perfiles se guardan localmente. Revisa las instrucciones importadas antes de utilizarlas.

## Backend opcional

Desactiva **Conexión directa** en OpenAI para utilizar la alternativa con servidor. Introduce su dirección HTTPS y un token personal opaco, conservado solo en memoria. La clave de OpenAI permanece en el backend, que autentica y limita la emisión. Consulta [backend/README.md](backend/README.md) y [despliegue Ubuntu con Compose y Caddy](deploy/README.md). No necesitas desplegarlo para BYOK.

El perfil se aplica a la sesión WebRTC mediante `session.update`, también en modo servidor. Los límites de emisión del backend no constituyen una política inmutable de instrucciones en el canal cliente ni garantizan un límite de gasto. No se admiten URL HTTP ni redirecciones de autenticación.

## Conectar Ray-Ban Meta

En **Dispositivos**, permite Bluetooth, registra la aplicación en Meta AI y conecta una sesión DAT. Necesitas gafas emparejadas, Meta AI y modo de desarrollador o una aplicación autorizada por Meta. El artifact usa el identificador DAT `0` de desarrollo. La distribución pública mediante Meta requiere sus autorizaciones, cuenta, región, modelo y firmware compatibles.

DAT confirma conexión solo con el estado `STARTED`. El audio de conversación utiliza las rutas de comunicación Android/WebRTC, con preferencia Bluetooth opcional; no se implementa acceso directo a micrófonos a través de `mwdat-speech`. **Entrada y salida deben verificarse por separado con hardware**. Si la ruta Bluetooth no está disponible se indica el uso del teléfono.

Para visión: autoriza una captura puntual, concede el permiso DAT, revisa la foto local y confirma por segunda vez su envío durante una conversación activa. La cámara se cierra al capturar, cancelar o salir. No se envía vídeo ni se guardan fotos en la galería. Si Meta AI pide permiso o te saca de la aplicación, vuelve a conectar y solicita de nuevo la captura; no se activan sensores al regresar. La imagen se reduce y puede perder detalles pequeños.

**Diagnóstico** ofrece estados reales de permisos, Bluetooth, DAT, cámara, HTTPS, credenciales, SDP, ICE, canal de datos y audio nativo. Sus pruebas locales de nivel y tono no certifican inteligibilidad ni el funcionamiento de las gafas. [Protocolo de pruebas reales](docs/PRUEBAS_REALES.md).

## Compilar y verificar

Java 17, Gradle Wrapper 8.14.1, AGP 8.11.1, Kotlin 2.2.21, Android SDK 36 y Build Tools 36.0.0. Android mínimo API 31, destino 36. Meta DAT core/camera 1.0.1 en Maven Central; WebRTC comunitario `io.github.webrtc-sdk:android:150.7871.01`. No hace falta token de GitHub Packages.

Crea `local.properties` (no versionado) con `sdk.dir` y, si procede, los valores públicos de registro `mwdat_application_id` y `mwdat_client_token`. No son lugar para la clave OpenAI. Configura el esquema de retorno `pbtmetagpt` conforme al registro DAT.

```powershell
.\gradlew.bat --no-daemon testDebugUnitTest lintDebug assembleDebug
node --test backend/*.test.mjs
python tools/comprobar_secretos.py app/build/outputs/apk/debug/app-debug.apk
# Con un emulador Android iniciado:
.\gradlew.bat connectedDebugAndroidTest
```

En Linux/macOS usa `./gradlew`. La APK se genera en `app/build/outputs/apk/debug/app-debug.apk`. El [workflow](.github/workflows/android.yml) ejecuta JVM, Lint, debug, análisis de patrones de secretos, backend, contenedores y pruebas instrumentadas de Keystore/persistencia en emulador. Publica la APK después de superar los tres trabajos. CI no usa claves reales ni acredita sensores físicos o conectividad de pago. [Arquitectura](docs/ARQUITECTURA.md).

## Fuentes oficiales

- [OpenAI Docs: WebRTC](https://developers.openai.com/api/docs/guides/realtime-webrtc) y [credenciales temporales, voces y velocidad](https://developers.openai.com/api/reference/resources/realtime/subresources/client_secrets/methods/create).
- [Android: SpeechRecognizer y límites](https://developer.android.com/reference/android/speech/SpeechRecognizer), [opciones de reconocimiento](https://developer.android.com/reference/android/speech/RecognizerIntent) y [Keystore](https://developer.android.com/privacy-and-security/keystore).
- [Meta DAT Android y muestras](https://github.com/facebook/meta-wearables-dat-android) y [Wearables Developer Center](https://wearables.developer.meta.com/docs/develop/dat/).
