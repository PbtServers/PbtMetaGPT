# Correcciones de interfaz, configuración y registro

Intervención del 27 de octubre de 2026 sobre `654d06d`. Estado experimental. Este seguimiento es independiente del progreso funcional general del 85 %. Los resultados de CI deben corresponder al SHA instalado.

## Diagnóstico y alcance

### Texto negro sobre fondo oscuro

La raíz `AssistantShell` pintaba un fondo oscuro con `background`, pero no establecía `LocalContentColor`. `MaterialTheme` no sustituye ese color por sí solo; los `Text` de formularios y del asistente podían heredar negro. `PbtTheme` envuelve todas las pantallas y diálogos en `Surface` con `onBackground`, usa paletas semánticas claras y oscuras y sigue el sistema por defecto. Se eliminó el gradiente oscuro fijo. Más ajustes permite elegir sistema, claro u oscuro. Las pruebas comprueban contraste AA de pares semánticos e herencia real en Compose. Los estados deshabilitados conservan el tratamiento de Material 3; WCAG exime controles inactivos. Falta inspección física con tamaño de fuente ampliado.

### Selección de voces

Sin Logcat del cierre original ni dispositivo del usuario, **no se atribuye una causa raíz confirmada al cierre**. La selección oficial observada en código solo modificaba un perfil; no debía iniciar red ni síntesis. Se centraliza en `VoiceSelection`, con catálogo por motor, selección pura, identidad conservada y normalización de voces exclusivas al volver a Realtime. Pulsar el motor ya seleccionado ahora conserva sus identificadores locales (antes los borraba). Se detiene una muestra anterior antes de cambiar de voz. La carga de modelos locales se limita al motor Sherpa y sus errores de E/S o permisos se comunican sin propagar una excepción desde el efecto Compose. No se han añadido capturas genéricas para ocultar el cierre.

Pruebas: recorrido de todos los identificadores oficiales con exportación/importación, transición Speech → Realtime, rechazo de voz incompatible, conservación del motor activo y selección/guardado de todas las voces Realtime desde Compose sin credencial ni audio. La reproducción real sigue pendiente.

Si vuelve a cerrarse, conectar por USB un teléfono de pruebas sin claves ni conversaciones personales y ejecutar:

```powershell
adb logcat -c
adb logcat -v threadtime AndroidRuntime:E PbtVoice:I libc:F DEBUG:F '*:S' > cierre-voz-local.txt
```

Reproducir una selección sin muestra y detener con Ctrl+C. Repetir por separado con muestra si el primer caso no falla. Anotar versión APK/SHA, Android, motor, voz anterior/nueva y si había audio. `PbtVoice` emite solo `OFFICIAL_VOICE_SELECTED`, sin nombre, instrucciones ni claves. Revisar manualmente el archivo antes de compartirlo; no subir Logcat completo ni archivos de claves. Un aborto nativo requiere conservar su backtrace y biblioteca, no solo el diálogo de Android.

### Registro Meta AI

Se mantiene DAT core/camera **1.0.1**, contrastado con la [muestra oficial](https://github.com/facebook/meta-wearables-dat-android/tree/main/samples/CameraAccess). El valor público predeterminado del token pasa de vacío a `0`, coherente con el identificador de desarrollo `0`. Una integración de producción requiere ambos valores del Wearables Developer Center; no se generan identificadores ficticios ni endpoints.

La [incidencia oficial 214 y su respuesta](https://github.com/facebook/meta-wearables-dat-android/issues/214#issuecomment-6154296740) describen el mismo error visible en Meta AI 294: el modo de desarrollador no se reconoce al recibir el enlace o el registro queda obsoleto tras reinstalar Meta AI. No basta con atribuirlo a `DFA resolution failed`: ese aviso también aparece al registrar correctamente. No está demostrado que sea la causa en el teléfono del usuario.

La app comprueba la presencia de `com.facebook.stella`, declara su visibilidad y usa exclusivamente `Wearables.startRegistration(activity)`. Conserva el esquema `pbtmetagpt` y añade `singleTop` para retornar a la misma actividad. El callback solo registra un evento seguro; no concede registro ni abre sensores. El estado solo es registrado con `REGISTERED` (ya no con `UNREGISTERING`) y conectado con `DeviceSessionState.STARTED`. Si falta confirmación, permite reintentar sin reiniciar el Setup. Se distinguen ausencia de app, apertura no disponible, permisos y SDK no preparado. Cancelación o tiempo límite no equivalen a éxito.

Se atiende también `registrationErrorStream`. La inicialización comprueba el resultado real de `Wearables.initialize`: un fallo no marca el SDK como preparado; `ALREADY_INITIALIZED` permite reconstruir observadores al recrear la actividad.

Recuperación recomendada: con gafas conectadas, desactivar/activar modo desarrollador en Meta AI, aceptar confirmación, comprobar DAT instalado en las gafas, cerrar y reabrir Meta AI y reintentar desde Dispositivos. Tras reinstalar Meta AI puede quedar un registro local obsoleto: retirar el registro primero. El borrado de datos de Android recomendado como último recurso por Meta elimina también perfiles y claves; exportar perfiles antes y volver a introducir claves dentro de la app. PbtMetaGPT nunca borra automáticamente sus datos para resolver el registro.

## Nuevo Setup e interfaz

1. Bienvenida breve y botón Comenzar.
2. OpenAI: clave cifrada, comprobación sin audio, aviso API/Plus y backend opcional. Es posible preparar el perfil sin clave; conversar permanece bloqueado hasta configurar credenciales.
3. Asistente: nombre, personalidad e instrucciones; el resto de campos está en ajustes avanzados.
4. Voz: motor, catálogo compatible, muestra explícita y ajustes avanzados; omitir conserva una configuración válida o vuelve a Realtime si el motor alternativo quedó incompleto.
5. Gafas Meta: registro opcional, Finalizar u Omitir sin exigir gafas.

`SetupStore` guarda etapa y borrador incluso cuando el nombre todavía está incompleto. Volver atrás y recrear la actividad restaura opciones. El borrador no es un perfil utilizable hasta superar validación. La clave se guarda únicamente con el botón explícito en Keystore; los campos de contraseña sin guardar y el token de backend no se persisten. Se rechazan patrones de claves en instrucciones del borrador. Un fallo de validación o de comprobación mantiene etapa y contenido. La aplicación nunca activa micrófono, cámara o sesión al restaurar.

La barra inferior con iconos originales da acceso a Inicio, Hablar (Conversación), Asistentes, Voz y Gafas (Dispositivos). Más ajustes conserva personalidad, biblioteca, activación, OpenAI, diagnóstico, privacidad y temas en un menú desplegable. Los formularios limitan su ancho en pantallas grandes, las voces se distribuyen en filas adaptables y se mantiene el desplazamiento con teclado. El Setup usa un selector compacto de motor y conserva el resto de opciones avanzadas. Las barras del sistema ajustan el contraste de sus iconos al tema elegido. No se eliminan motores, frases, importaciones, BYOK ni backend. No se promete accesibilidad física completa sin recorrer TalkBack y fuentes grandes en dispositivo.

## Icono Android original

Símbolo vectorial propio de conversación, onda de voz y destello, en verde menta/lavanda sobre azul oscuro. Sin textos pequeños ni marcas de terceros. Android vector permite todas las densidades sin PNG descargados. Se incluyen foreground/background, `ic_launcher` e `ic_launcher_round` adaptativos y recurso monocromático API 33+. El manifiesto declara ambos iconos. La prueba instrumentada exige que PackageManager resuelva un `AdaptiveIconDrawable`. Esto no acredita por sí solo la apariencia en cada launcher, ajustes o recientes: instalar la APK y comprobar esos tres lugares, además de iconos temáticos y máscara circular.

## Familias Meta Wearables, incluido Adventurer

El [changelog oficial DAT](https://github.com/facebook/meta-wearables-dat-android/blob/main/CHANGELOG.md) añadió `DeviceType.META_GLASSES` en 0.8.0. Se utiliza ese valor explícitamente en DAT 1.0.1. `GlassesDevice` conserva el `DeviceIdentifier` de DAT separado del nombre visible. No interpreta «Meta Glasses 00R6» como identificador estable de modelo ni lo renombra como Ray-Ban. Muestra nombre, tipo/modelo declarado por DAT, compatibilidad, estado de enlace y sesión real. Si DAT solo informa la familia genérica, no se inventa un modelo comercial.

| Familia prevista en el flujo DAT | Tratamiento y límite |
| --- | --- |
| Meta Glasses Adventurer / nombre 00R6 | Se admite `META_GLASSES`; el sufijo visible no decide compatibilidad ni capacidades |
| Otras Meta Glasses | Se aceptan mediante sus metadatos DAT, sin prefijos ni listas de nombres |
| Ray-Ban Meta Gen 1 y Gen 2 / Optics | Mismo registro y selección por identificador DAT; no se deduce generación de Bluetooth |
| Oakley Meta HSTN y Vanguard | Mismo flujo, condicionado a compatibilidad y enlace reales |
| Meta Ray-Ban Display | Se muestra `isDisplayCapable()`; la app no implementa envío de contenido a pantalla |

El selector automático mantiene el filtro oficial de incompatibilidad. Cada fila permite seleccionar el identificador concreto mediante `SpecificDeviceSelector` si DAT declara `COMPATIBLE` y `CONNECTED`. Eso solo permite intentar la sesión: no equivale a `STARTED`. Una actualización requerida o un enlace pendiente bloquean el intento por fila con explicación. Para cámara se utiliza el resultado real de `addCamera`: si falla, se deshabilita la captura en ese intento hasta reconectar; ni el nombre ni la pantalla implican cámara disponible. No existe aquí una propiedad universal inventada de capacidades de micrófono/cámara. Audio utiliza rutas de comunicación Android; la pantalla se consulta con la API oficial. No se incluye el módulo experimental `mwdat-speech` ni se prometen sensores nuevos.

Hay un [reporte oficial de Adventurer](https://github.com/facebook/meta-wearables-dat-android/discussions/183) con registro, permiso y metadatos compatibles pero sesión rechazada. Es evidencia de que no se debe convertir detección/registro en una promesa de funcionamiento. No identifica por sí solo la causa en 00R6 de la usuaria. Las pruebas nuevas recorren todos los tipos DAT y específicamente `META_GLASSES`, nombres distintos, enlace pendiente y firmware requerido; ejecución Android/JVM pendiente en este entorno.

## Fotografía preparada con botón deshabilitado

**Causa comprobada en código:** el botón exigía `vs.active && !vs.paused && !vs.responding && !vs.sendingImage`; una clave guardada y comprobada no hacía activa una sesión Realtime. Por tanto, una captura correcta sin conversación previa mantenía el botón gris. La foto sí se propagaba mediante `StateFlow` a Compose. Además, `disconnect` y `stopCapture` descartaban la imagen al salir temporalmente de la actividad.

`PhotoAnalysisPolicy` distingue sin imagen, configuración pendiente, procesando, pausa, preparada y error. Con imagen válida y configuración presente el botón abre confirmación **sin exigir una sesión anterior**. Tras confirmar, se reutiliza una sesión disponible o se prepara una sesión Realtime de análisis con transceptor de recepción y sin crear pista de micrófono ni exigir su permiso. Se confirma primero el perfil con el proveedor, se reduce/codifica JPEG, se envía `conversation.item.create` con `input_image` y pregunta y después `response.create`. No se añade otro endpoint ni se cambia el modelo `gpt-realtime` del proyecto. El formato coincide con [OpenAI Docs: entradas de imagen Realtime](https://developers.openai.com/api/docs/guides/realtime-conversations#image-inputs). La sesión solo de imagen no puede activar el micrófono mediante Pausar/Reanudar; para hablar hay una acción explícita que finaliza el análisis e inicia otra conversación.

La clave se sigue leyendo desde Keystore al solicitar la credencial temporal. Configuración presente no acredita que la clave siga siendo válida o tenga cuota; esos errores aparecen sin borrar la imagen. `PhotoMemory` conserva el bitmap exclusivamente en memoria durante navegación, salida temporal y recreación de actividad. Se elimina al descartar o terminar el propietario; no se guarda en disco ni se reenvía al restaurar. Se conserva también tras enviar para poder reintentar si falla la respuesta. «Descartar foto» borra la copia local, no solicita borrado al proveedor. El análisis tiene límite de dos minutos; la captura, envío y respuesta real siguen pendientes de API funcional y gafas.

Pruebas nuevas: foto + credenciales + sesión inactiva habilita; apertura no envía; Cancelar no envía; confirmar ejecuta una sola acción; falta de configuración se explica; procesamiento/pausa bloquean; error permite reintentar; desconectar/recrear conserva bitmap; JPEG es decodificable y el evento completo queda por debajo de 64 KiB. El callback de envío de la prueba es controlado: no demuestra respuesta real del modelo.

## Cierre inmediato al iniciar conversación

No se dispone del teléfono ni de su Logcat. **No se afirma una causa raíz confirmada ni que los dos cierres sean el mismo fallo.** El controlador anterior iniciaba la conexión sin navegar al destino Conversación. Ahora abre primero la pantalla y espera un frame antes de validar el perfil e iniciar red/audio. No crea un ViewModel de conversación ni serializa un perfil en rutas; el nuevo ViewModel es únicamente memoria de foto y no inicia sensores.

Se ha corregido un riesgo concreto de ciclo de vida: `lifecycleScope` usa `Main.immediate`, de modo que un callback nativo recibido en UI podía ejecutar `fail`/`dispose` dentro de la propia pila del observer. [El contrato de WebRTC lo prohíbe](https://webrtc.googlesource.com/src/+/refs/heads/main/sdk/android/api/org/webrtc/PeerConnection.java#1317). `NativeCallbackQueue` siempre encola en `Dispatchers.Main` y descarta generaciones antiguas. La prueba instrumentada comprueba ambas propiedades. Esto es una corrección preventiva respaldada por el contrato, no la prueba de causalidad del cierre reportado.

También se distinguen errores de carga de biblioteca (`LinkageError`) de los fallos recuperables habituales. Un SIGABRT/SIGSEGV nativo no puede recuperarse con un `try/catch`; necesita su backtrace. Las trazas `PbtStart` registran solo etapas constantes: botón, pantalla, credencial, ruta, WebRTC, módulo, factoría y captura. Nunca registran SDP, claves, nombres, fotos ni instrucciones. Un fallo de ruta Bluetooth ofrece **Usar micrófono y altavoz del móvil**; no se decide marca por nombre ni se toma A2DP como ruta bidireccional. Desconectar Bluetooth detiene la sesión y permite reintentar en el móvil. Entrada y salida necesitan pruebas separadas.

```powershell
adb logcat -c
adb logcat -v threadtime PbtStart:I PbtVoice:I AndroidRuntime:E libc:F linker:E DEBUG:F '*:S' > cierre-conversacion-local.txt
```

Comparar el último marcador antes del cierre y el bloque exacto de AndroidRuntime o backtrace. Capturar primero sin gafas y después con 00R6, repitiendo con y sin preferencia Bluetooth; no activar captura permanente ni publicar logs sin revisar. La prueba de navegación usa credencial dañada ficticia para fallar antes de red/audio y comprobar que Conversación sigue visible. No simula ni certifica gafas reales.

## Verificación

Superadas localmente: 19 Node y 9 Python (incluidos cuatro contratos nuevos de recursos/contraste), escáner de patrones de secretos y análisis sintáctico de Kotlin con el parser del Gradle local. El parser no comprueba tipos ni sustituye una compilación. Los contratos de icono resuelven recursos y verifican que sus curvas permanezcan dentro del círculo seguro adaptativo; no acreditan instalación.

Se localizó Java 17 en `G:\Programas\zulu-17` y se repitió Gradle con caché dentro del repositorio. El bloqueo final es **SDK location not found**. La descarga fijada de Sherpa falla por permisos de red; ADB tampoco puede iniciar su servidor local en este entorno. No se presentan JVM, Lint, APK o instrumentadas como superadas. La publicación mediante Git está bloqueada por red y la conexión GitHub rechaza crear el árbol con **HTTP 403, Resource not accessible by integration**. Por tanto no hay ejecución CI ni APK de estos cambios; las ejecuciones anteriores no los validan.

Pendientes físicos: reproducción de voces y ambos cierres, registro/cancelación/retorno Meta AI real, teléfono API 31+, dos temas con accesibilidad, icono instalado en launcher/ajustes/recientes, rutas de entrada/salida y captura autorizada con Meta Glasses Adventurer/00R6 y cada familia que se quiera certificar. No se usan credenciales reales en CI ni se declara una Release estable.

## Archivos de esta intervención

Corrección posterior de CI: en `8f06bf6`, los tests de gafas trataban incorrectamente `DeviceIdentifier` como si fuera un `String`. El parche envuelve las tres fixtures con `DeviceIdentifier(...)` y corrige la comparación del identificador, conservando casos y aserciones. La clase de producción no se cambia. La [CI 40536110246](https://github.com/PbtServers/PbtMetaGPT/actions/runs/40536110246) falló al compilar las pruebas JVM; no acredita emulador ni APK. El nuevo resultado debe comprobarse en la ejecución del commit correctivo. El porcentaje permanece en 60 % hasta obtener nuevas verificaciones satisfactorias.

Implementación local `d57101d` y cierre documental posterior. Los seis archivos locales preexistentes se conservan fuera de estos commits.

- `README.md`
- `app/build.gradle.kts`
- `app/src/androidTest/java/com/pbtservers/pbtmetagpt/ConversationInstrumentedTest.kt`
- `app/src/androidTest/java/com/pbtservers/pbtmetagpt/NativeCallbackInstrumentedTest.kt`
- `app/src/androidTest/java/com/pbtservers/pbtmetagpt/PhotoAnalysisInstrumentedTest.kt`
- `app/src/androidTest/java/com/pbtservers/pbtmetagpt/SetupInstrumentedTest.kt`
- `app/src/androidTest/java/com/pbtservers/pbtmetagpt/ThemeInstrumentedTest.kt`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/java/com/pbtservers/pbtmetagpt/AssistantProfile.kt`
- `app/src/main/java/com/pbtservers/pbtmetagpt/AssistantShell.kt`
- `app/src/main/java/com/pbtservers/pbtmetagpt/AudioRoute.kt`
- `app/src/main/java/com/pbtservers/pbtmetagpt/AudioRoutePolicy.kt`
- `app/src/main/java/com/pbtservers/pbtmetagpt/DatRegistrationPolicy.kt`
- `app/src/main/java/com/pbtservers/pbtmetagpt/GlassesDevice.kt`
- `app/src/main/java/com/pbtservers/pbtmetagpt/ImageEncoding.kt`
- `app/src/main/java/com/pbtservers/pbtmetagpt/LocalAudioDiagnostics.kt`
- `app/src/main/java/com/pbtservers/pbtmetagpt/MainActivity.kt`
- `app/src/main/java/com/pbtservers/pbtmetagpt/MetaGlasses.kt`
- `app/src/main/java/com/pbtservers/pbtmetagpt/NativeCallbackQueue.kt`
- `app/src/main/java/com/pbtservers/pbtmetagpt/PbtTheme.kt`
- `app/src/main/java/com/pbtservers/pbtmetagpt/PhotoAnalysis.kt`
- `app/src/main/java/com/pbtservers/pbtmetagpt/PhotoAnalysisPanel.kt`
- `app/src/main/java/com/pbtservers/pbtmetagpt/RealtimeClient.kt`
- `app/src/main/java/com/pbtservers/pbtmetagpt/SetupStore.kt`
- `app/src/main/java/com/pbtservers/pbtmetagpt/VoiceLibraryScreen.kt`
- `app/src/main/java/com/pbtservers/pbtmetagpt/VoiceSelection.kt`
- `app/src/main/res/drawable/ic_assistants.xml`
- `app/src/main/res/drawable/ic_conversation.xml`
- `app/src/main/res/drawable/ic_glasses.xml`
- `app/src/main/res/drawable/ic_home.xml`
- `app/src/main/res/drawable/ic_launcher_foreground.xml`
- `app/src/main/res/drawable/ic_launcher_monochrome.xml`
- `app/src/main/res/drawable/ic_voice.xml`
- `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`
- `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`
- `app/src/main/res/mipmap-anydpi-v33/ic_launcher.xml`
- `app/src/main/res/mipmap-anydpi-v33/ic_launcher_round.xml`
- `app/src/main/res/values/colors.xml`
- `app/src/test/java/com/pbtservers/pbtmetagpt/AudioRoutePolicyTest.kt`
- `app/src/test/java/com/pbtservers/pbtmetagpt/DatRegistrationPolicyTest.kt`
- `app/src/test/java/com/pbtservers/pbtmetagpt/GlassesDeviceTest.kt`
- `app/src/test/java/com/pbtservers/pbtmetagpt/PhotoAnalysisTest.kt`
- `app/src/test/java/com/pbtservers/pbtmetagpt/ThemeContrastTest.kt`
- `app/src/test/java/com/pbtservers/pbtmetagpt/VoiceSelectionTest.kt`
- `docs/CORRECCIONES_INTERFAZ.md`
- `docs/PROGRESO.md`
- `tools/probar_android.sh`
- `tools/test_android_resources.py`
