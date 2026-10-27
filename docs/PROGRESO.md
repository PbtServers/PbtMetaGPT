# Seguimiento de PbtMetaGPT

## Servicios online gratuitos sin infraestructura propia · 27/10/2026

**70 % de esta ampliación; experimental, sin aceptación de extremo a extremo.** No sustituye ni incrementa el 85 % histórico de otra intervención. Inicio medido: 21:54 UTC; corte de seguimiento 22:33 UTC (unos 39 minutos). La estimación comunicada de 20–35 minutos corresponde al cierre de revisión/documentación/publicación desde el mensaje de estado de las 22:06 UTC; no a la aceptación real, que depende de cuenta Free y hardware.

| Subfase | Avance acreditado / peso | Pendiente |
| --- | ---: | --- |
| Investigación y acceso a servicios | 14/25 | Groq Free documentado; falta cuenta y conexión real. Gemini descartado para Free en EEE; Cerebras actual exige medio de pago |
| Configuración y perfiles | 13/15 | Selección Groq/OpenAI, claves separadas, consentimientos y perfiles; Keystore y persistencia correctos en API35/API36. Falta cuenta real |
| Conversación y activación local | 12/20 | Ruta Groq por texto/Vosk compilada y políticas probadas; falta generación real y reconocimiento hablado |
| Voces online | 13/20 | Qwen CustomVoice/Gradio: contrato y WAV real no silencioso correctos desde CI; Android y audición pendientes. RVC desactivado |
| Integración y diseño | 9/10 | Sin fallback facturable; iconos adaptativo/mono/respaldo revisados a 48/64/96 px y recursos Android validados. Faltan teléfono/gafas |
| Pruebas y documentación | 9/10 | Suite JVM completa, Python, Node, Lint, escaneo APK y API35/API36 correctos en CI; falta aceptación real con Groq y hardware |
| **Total** | **70/100** | **Qwen real y ambas APIs Android probados; conversación completa y hardware pendientes** |

Detalle de proveedores, restricciones, recorrido y comandos: [SERVICIOS_ONLINE.md](SERVICIOS_ONLINE.md). El nuevo modo conserva OpenAI opcional, Vosk, frases «Atento», servicio/notificación/parada, DAT, cámara con confirmación y motores existentes. Groq recibe exclusivamente texto/contexto autorizado; el dictado permanece local. Qwen exige consentimiento propio, contrato compatible y origen del archivo restringido. No hay token HF ni acceso a créditos HF de pago. RVC no importa ni sube `.pth`/`.index`; el candidato r3gm/rvc_zero limita su demo a educación/investigación y no se ha probado su API para este uso.

**Evidencias de esta intervención:** 87 archivos Kotlin sin errores sintácticos (no comprueba tipos); 26 JVM ejecutadas directamente con Kotlin/JUnit de Gradle (7 nuevas de cuota/modelo/seguridad y 19 de políticas/diario); 31 Python y 19 Node correctas. Se añaden 6 contratos JVM Qwen y 2 instrumentadas Android de aislamiento Keystore/consentimiento, pendientes de suite Android. Prueba remota sin mocks ejecutada: Groq **no ejecutado** por falta de clave; API Qwen **bloqueada** por red (`URLError`, WinError10013), síntesis **no ejecutada**. No se han provocado cuotas por abuso. No se reutilizan resultados de CI anterior.

Java17/Gradle con caché local llega a configurar, pero `testDebugUnitTest lintDebug assembleDebug` falla por **SDK location not found**. Preparación de Sherpa y modelo de pruebas fallan por red bloqueada. ADB no tiene dispositivos; instrumentadas Android15/16 y escaneo de APK no se pueden acreditar. La CI conserva ambas APIs y la publicación condicionada; no se ha creado una Release estable ni APK nueva local. README.md y los seis archivos locales anteriores se comprueban por SHA256 y quedan fuera de los commits. El resultado de publicación se registrará tras el intento de push, sin force.

**Publicación y nueva evidencia:** `6e97bc9` (integración), `88a968a` (icono) y `a673343` (prueba remota) publicados en `origin/master` sin force. Las dos primeras CI se cancelaron por la concurrencia al llegar el siguiente commit; no cuentan como validación. [Qwen real 40593117803](https://github.com/PbtServers/PbtMetaGPT/actions/runs/40593117803) sobre `a673343` **correcta**: el script sin mocks comprobó contrato y generó un WAV PCM16 mono con muestras no silenciosas, sin token, con Ryan/0.6B y una frase sintética española. Artifact `evidencia-qwen-real`, ID 12773909868. No se ha escuchado ese audio ni probado la ruta Android/gafas. [Android 40593117870](https://github.com/PbtServers/PbtMetaGPT/actions/runs/40593117870) sigue en compilación/JVM/Lint al redactar; backend correcto. Se mantienen pendientes Groq real y cuotas reales: no hay clave ni se ha simulado su aceptación.

Actualización 22:17 UTC: la CI Android de `a673343` ha superado compilación, JVM, Lint, Python/Node y escaneo de la APK; faltan las instrumentadas de ambas APIs. Se añade después un correctivo de la cola de muestras para conservar los mensajes públicos sanitizados de cuota Qwen, con una regresión JVM: no imprime errores originales ni cambia de voz/proveedor. El correctivo requiere su propia CI; los resultados anteriores no lo acreditan. Se mantiene 60 % hasta esa validación. El único bloqueo de credenciales para conversación completa sigue siendo Groq Free; la APK no incluye claves de desarrollador.

**Validación del correctivo `e5cadba`:** [CI 40593718535](https://github.com/PbtServers/PbtMetaGPT/actions/runs/40593718535) supera compilación Android, suite JVM completa (incluida la regresión de cuota), Android Lint, Python, Node, backend/proxy, escaneo de secretos en fuentes/APK y pruebas instrumentadas en **Android15/API35 y Android16/API36**. No se reutiliza el éxito de `a673343` para certificar este correctivo. La inferencia remota Qwen corresponde a la ejecución independiente de `a673343`; su adaptador y contrato no cambiaron después. Los resultados locales de 26 JVM se conservan como subconjunto, sin sumarlos otra vez a la suite de CI. Se eleva esta ampliación a **70 %**; Groq no se ha probado con credencial real, ni se acreditan audio físico, batería o 00R6. Siguiente aceptación: configurar cuenta Groq Free en la APK y completar el recorrido documentado con el teléfono y las gafas. No enviar claves por chat.

**APK experimental publicada a las 22:32 UTC:** los cinco trabajos de esa CI han terminado correctamente. [Artifact PbtMetaGPT-v0.4.1-dev-debug](https://github.com/PbtServers/PbtMetaGPT/actions/runs/40593718535/artifacts/12775912184), ID 12775912184, código exacto `e5cadba`. Huella SHA256 del contenedor ZIP publicada por GitHub: `75334cd47527210fdf7877f9571e630a6891da03a5f4aacc1ace6df891a67bbc` (no es el hash interno del APK). Informes API35: 12775786793; API36: 12775303925; validación: 12775242863. El commit posterior actualiza solo documentación; no cambia el código de la APK entregada. No se ha publicado una Release estable. README y seis archivos previos siguen idénticos por SHA256 y excluidos de todos estos commits.

## Permiso de red WebRTC y nuevo icono · 27/10/2026

**65 % de intervención técnica, validación física pendiente.** El informe real del Nothing Phone 3a Lite indica `SecurityException` por falta de `android.permission.ACCESS_NETWORK_STATE` durante `org.webrtc.NetworkMonitorAutoDetect`, seguida de aborto JNI (`SIGABRT`). Se ha añadido el permiso normal en el manifiesto principal, una prueba Python de regresión y comprobación del permiso real con `aapt dump permissions` tanto en la APK de validación como en la de publicación. El código de WebRTC permanece intacto hasta comprobar este defecto específico. Se requiere reinstalar la APK y comprobar en el dispositivo que ya no aparece el mismo aborto.

Icono: sustitución de la P anterior por gafas blancas con conversación verde integrada, fondo negro, variante monocromática y área segura Android. El verde `#9DE9D0` procede de la paleta principal ya definida en `PbtTheme.kt`. README y seis archivos locales anteriores preservados; no hay verificación física de Meta Glasses 00R6.

## Corrección de inicialización Realtime e icono · 27/10/2026

**70 % de intervención técnica local acreditado; no de reparación física.** El cierre comunicado ocurre cuando aparece «Conectando con OpenAI…», antes de que el usuario obtenga una sesión operativa. En `RealtimeClient.kt` la inicialización global `PeerConnectionFactory.initialize` se repetía por conexión; el nuevo `WebRtcBootstrap` la ejecuta una sola vez por proceso, con sincronización y posibilidad de reintentar tras excepción. Hay tres regresiones JVM para llamadas repetidas, concurrentes y fallo recuperable del inicializador. Se añadieron marcadores de las etapas de creación de conexión y SDP sin incluir credenciales, SDP ni mensajes externos. Es una reparación preventiva de un defecto de ciclo de vida comprobado en el código; no identifica por sí misma el `SIGABRT` o cierre físico.

Icono renovado como recurso vectorial: P dorada de formas simples sobre azul oscuro; el detalle interno de gafas/chat permanece casi imperceptible. Variante monocromática conservada y máscaras adaptativas sin alterar. Contratos del icono incluidos en las pruebas Python.

**Evidencia local:** 29 Python y 16 Node correctas; escáner de fuentes sin hallazgos y `git diff --check` correcto. Falta obtener resultado Android completo del commit nuevo, ejecutar instrumentadas de API35/API36, verificar la APK experimental y obtener el registro de cierre del teléfono para atribuir causalidad. El README y seis archivos locales previos siguen intactos. Progreso de estabilización histórica 35 % y funcional anterior 85 %, sin sumarse por estas verificaciones. No se acredita audio, Vosk ni Meta Glasses 00R6.

Resultado de [Actions 40577175151](https://github.com/PbtServers/PbtMetaGPT/actions/runs/40577175151) sobre `cbbec73`: compilación y escaneo de APK, pruebas JVM/Python, Lint, backend y emulador API35 correctos. API36 no ejecutó pruebas por error de infraestructura al descargar la imagen del sistema Google APIs x86_64 (`ZipFile unknown archive` y ADB sin emulador). El trabajo final de publicación fue omitido; no hay APK publicable atribuible a este SHA. El reintento de un job mediante GitHub API fue denegado (HTTP 403), por lo que se solicita una nueva ejecución mediante este commit documental sin relajar la barrera de publicación.

## Correctivo estructural de identificadores DEX · 27/10/2026

**35 % de estabilización; 85 % funcional histórico, sin incremento.** Sobre `472d00a` se clasifican exclusivamente coincidencias OpenAI contenidas en un `string_id` referenciado solo por `method_ids`, sin valores estáticos ni referencias `const-string` verificadas mediante el desensamblador oficial. Si el DEX, el desensamblador o las referencias no son concluyentes, continúa el rechazo. No hay excepciones por hash, clase, biblioteca o nombre de archivo; los patrones OpenAI y PEM permanecen idénticos. El JSON conserva metadatos de los identificadores clasificados sin revelar cadenas.

**Pruebas locales:** 29 Python, 16 Node y escaneo de fuentes correctos, sin APK local ni SDK Android disponible. Queda comprobar la clasificación de los cinco índices originales del DEX reconstruido en CI, las pruebas instrumentadas de Android 15/16 y el artifact experimental del mismo SHA. README y seis archivos anteriores intactos; ninguna validación física de audio, Vosk o Meta Glasses 00R6.

## Nueva estabilización de audio y escucha persistente · 27/10/2026

**35 % de esta nueva tarea**, independiente del 85 % general y del 60 % de la intervención anterior. Base `f9daabd`, cuya [CI 40542139078](https://github.com/PbtServers/PbtMetaGPT/actions/runs/40542139078) está correcta. No valida el código añadido aquí ni el audio real. Dispositivo comunicado: Nothing Phone 3a Lite, Android 16, Nothing OS 4.1 Beta; gafas Meta Glasses 00R6. Detalle, límites y protocolo sin ADB: [ESTABILIZACION_AUDIO.md](ESTABILIZACION_AUDIO.md).

| Bloque de esta tarea | Avance acreditado / peso | Pendiente |
| --- | ---: | --- |
| Auditoría y evidencias | 8/15 | Informe del cierre real; no se confirma causa física |
| Diagnóstico y corrección de cierres | 8/30 | Compilación Android, reproducción y reparación contrastada con teléfono |
| Escucha local y frases | 8/20 | Servicio/Vosk y políticas implementados; modelo, permisos, acústica, bloqueo y batería sin validar |
| Rediseño de apartados | 4/20 | Inicio, ajustes, tarjetas/editor y biblioteca modificados; emulador y revisión física pendientes |
| Pruebas, documentación y publicación | 7/15 | 19 JVM nuevas + 9 Python + 19 Node correctas; Android/CI/publicación nuevas pendientes |
| **Total** | **35/100** | **Experimental; ninguna función acústica nueva certificada** |

El diario privado registra hitos y errores con códigos/tipos/archivos de la app, sin mensajes externos ni contenido del usuario. Incluye exportación `.txt` por selector Android, retención de siete días y borrado confirmado. `ApplicationExitInfo` aporta causas de salida cuando Android las conserva; no garantiza una pila de fallos nativos. El manejador Java/Kotlin conserva la cadena de cierre de Android.

El servicio de tipo micrófono se inicia desde una confirmación visible, usa Vosk español opcional y una notificación de parada. No reinicia tras una parada explícita ni tras morir el proceso. La exclusión de micrófono y la política de consentimiento/callbacks tienen pruebas JVM ejecutadas. Un fallo de conexión/audio suspende; una conversación confirmada que finaliza normalmente puede volver a escucha con el consentimiento vigente. No se promete reconocimiento de nombres fuera del vocabulario ni compatibilidad física con la beta.

Verificación local: 19 JVM compiladas y ejecutadas directamente con Kotlin/JUnit de Gradle (no la suite Android completa); 77 archivos Kotlin sin errores sintácticos, sin comprobar tipos. Gradle Java17 bloqueado por `SDK location not found`; descarga de Sherpa y Git HTTPS bloqueados por red. CI preparada para API35 y API36, con APK final condicionada a ambas. README sin modificaciones y seis archivos locales anteriores conservados byte a byte. Se mantiene el porcentaje hasta nuevas verificaciones. Próxima aceptación: publicar/ejecutar CI y obtener el informe del cierre desde una APK nueva validada, seguido de las pruebas físicas documentadas.

### Publicación de esta estabilización

Implementación local: `0328564`. Push a `origin/master` intentado y rechazado por falta de conexión HTTPS. El conector GitHub permite lectura, pero la escritura devuelve **HTTP 403: Resource not accessible by integration**. Master remoto continúa en `f9daabd`; la consulta de Actions para el SHA de implementación devuelve cero ejecuciones. **Sin APK nueva ni CI de esta intervención**, y sin sumar porcentaje por publicar. Los seis archivos preexistentes son los únicos cambios ajenos que continúan sin incluirse en commits. Para desbloquear la CI se necesita publicar los commits desde un entorno con conexión y permisos de escritura; no se ha usado force push.

### Correctivo de la colisión de controladores en escucha Android

`b036a7b` ya está publicado en master. La [ejecución 40556319743](https://github.com/PbtServers/PbtMetaGPT/actions/runs/40556319743) supera backend/contenedores, pero falla en `:app:compileDebugKotlin`: el `gate` local de tipo `MicrophoneGate?` ocultaba el campo `ActivationGate`. Esto impedía resolver `accept` y propagaba un tipo incorrecto hasta el callback del asistente. Los emuladores y la publicación de APK quedaron omitidos por ese fallo.

El parche renombra únicamente esa variable local y sus dos usos a `microphoneGate`. `gate.accept(best, gateEpoch)` conserva el controlador de activación y su retorno `AssistantProfile?`; después de comprobar que no es nulo se detiene el reconocedor y se encola el callback con comprobación de generación. Se mantienen permisos, concesión/liberación del micrófono, rechazo de resultados antiguos, modo de prueba y todas las pruebas existentes.

Verificado localmente: 19 JVM de políticas/diario correctas, 9 Python y 19 Node correctas, 77 archivos Kotlin sin errores sintácticos, README sin diferencias y seis archivos previos idénticos a sus copias. Gradle Java17 sigue sin SDK Android; la preparación de Sherpa sigue bloqueada por red. Estas verificaciones no acreditan la compilación Android ni sustituyen la CI del correctivo. **Se mantiene el 35 %** hasta validar nuevas etapas Android; acústica y cierres físicos continúan pendientes.

### Bloqueo del escáner de APK después del correctivo

La [ejecución 40558091837](https://github.com/PbtServers/PbtMetaGPT/actions/runs/40558091837) de `46eae50` supera compilación Android, JVM, Lint, Python, Node y backend/contenedores. La barrera posterior de secretos rechaza `classes4.dex`; API35/API36 y APK final quedan omitidos. No se reutilizan como prueba los resultados de otro SHA.

No hay copia conservada de la APK/DEX en los artifacts ni en el equipo de la usuaria. El mensaje antiguo no permite determinar qué patrón ni clase coincidió: no se afirma que sea una clave real ni un falso positivo. Se incorpora diagnóstico de patrón, posición, longitud, huella y atribución DEX/JAR/AAR sin imprimir candidatos. Los patrones y todos los rechazos originales se conservan; no hay exclusiones ni eliminación de dependencias. CI guardará metadatos en el artifact de informes incluso cuando el escáner rechace la APK. Detalle y siguiente decisión: [DIAGNOSTICO_ESCANER.md](DIAGNOSTICO_ESCANER.md).

Verificación de esta intervención: **20 Python correctas**, incluidas once regresiones nuevas; fuentes sin coincidencias, README intacto y seis archivos anteriores conservados. **Se mantiene el 35 %**. Faltan reconstruir el artefacto, identificar la coincidencia real, resolverla con evidencias y ejecutar ambos emuladores antes de publicar APK.

## Intervención de interfaz y errores · 27/10/2026

Tarea independiente del **85 % funcional** documentado abajo. Base `654d06d`; README profesional conservado con una referencia breve añadida. Detalle: [CORRECCIONES_INTERFAZ.md](CORRECCIONES_INTERFAZ.md).

| Bloque de esta tarea | Avance acreditado / peso |
| --- | ---: |
| Auditoría y diagnóstico | 10/10 |
| Modo oscuro | 12/15; pares personalizados AA comprobados, Compose/Lint pendientes |
| Cierre de voces y arranque de conversación | 8/20; selección y callbacks reforzados, causa exacta pendiente de Logcat |
| Registro y familias Meta Wearables | 10/20; DAT 1.0.1, META_GLASSES/00R6 sin filtros de marca, errores/callback; JVM y hardware pendientes |
| Setup, navegación y fotografía | 12/20; cinco etapas, borrador, navegación e imagen sin conversación previa; pruebas Android/API pendientes |
| Icono | 3/5; recursos, manifiesto y círculo seguro comprobados; APK/instalación pendientes |
| Pruebas, documentación y publicación | 5/10; 9 Python y 19 Node correctas, documentación y commits locales; CI/push bloqueados |
| **Total de esta tarea** | **60/100** |

Commit local de implementación: **`d57101d`**. Añadidas 17 pruebas JVM y 13 Android (pendientes de ejecución), además de actualizar el recorrido del Setup existente y añadir cuatro contratos Python ejecutados. Análisis sintáctico: 59 archivos Kotlin sin errores, sin comprobar tipos. Fotografía: la condición antigua exigía una sesión Realtime activa aunque la clave estuviera comprobada; ahora permite confirmar y abrir una sesión de imagen con micrófono apagado, conservando el bitmap en memoria.

Se localizó Java 17 en `G:\Programas\zulu-17`; Gradle termina con **SDK location not found**. Descarga de Sherpa bloqueada por red; servidor ADB no disponible por restricciones del entorno. En el turno inicial, Git no podía conectar a GitHub y el conector rechazaba escritura con **HTTP 403 / Resource not accessible by integration**; entonces no había push ni CI de esta intervención. La ejecución posterior se registra a continuación. No se reutiliza el resultado de un SHA anterior.

Cambios documentales locales preexistentes conservados y excluidos de los commits. README: una línea genérica ampliada a Meta Wearables y dos párrafos breves (Setup/familias); apartados, tablas, badges y advertencias conservados. No hay pruebas físicas nuevas ni Release estable. Próximo paso: disponer de SDK/runtime y acceso de publicación, ejecutar el conjunto Android y analizar Logcat de los dos cierres en 00R6 antes de declararlos resueltos. El protocolo y las causas confirmadas/pendientes están en el documento de correcciones.

### Corrección de tipos en las pruebas DAT

`8f06bf6` ya figura publicado en `origin/master`. La [ejecución 40536110246](https://github.com/PbtServers/PbtMetaGPT/actions/runs/40536110246) confirma compilación Kotlin de producción y backend/contenedores correctos, pero falla en `:app:compileDebugUnitTestKotlin`: tres fixtures de `GlassesDeviceTest` pasaban `String` en lugar de `DeviceIdentifier`. No se ejecutó el emulador ni se publicó la APK.

El parche usa `DeviceIdentifier(...)` en las tres construcciones y compara el identificador con otro valor del mismo tipo. Conserva los tres tests y todas las aserciones: `META_GLASSES`/00R6 sin deducir modelo, recorrido de todos los tipos DAT y bloqueo por firmware/enlace pendientes. No modifica `GlassesDevice` ni el README.

Verificación local del parche: 9 Python correctas, 59 archivos Kotlin sin errores sintácticos, escáner de secretos correcto y seis archivos preexistentes idénticos a sus copias originales. Gradle con Java 17 sigue bloqueado por SDK ausente. La sintaxis no acredita compatibilidad de tipos con el SDK: queda pendiente la nueva CI completa. **Se mantiene el 60 %**, sin sumar de nuevo verificaciones anteriores.

### Corrección de sincronización y desplazamiento del Setup

La [ejecución 40538057814](https://github.com/PbtServers/PbtMetaGPT/actions/runs/40538057814) de `1fdbe8b` supera compilación, JVM, Lint y backend; el emulador ejecuta 20 pruebas: 18 correctas y dos fallidas en `SetupInstrumentedTest`. La APK final sigue retenida por ese fallo.

El test de todas las voces intentaba `performScrollTo()` sobre «Voz», situado en la barra inferior fija, sin ancestro desplazable. Ahora exige que el destino sea visible y lo pulsa; mantiene el desplazamiento dentro del formulario, todas las voces y las aserciones de selección y guardado. El test de restauración verifica selección y borrador guardado antes de pasar de la sincronización Compose a `ActivityScenario.recreate()`, conservando las comprobaciones posteriores.

También se corrige un riesgo de producción: `ON_STOP` leía un `Triple` de `rememberUpdatedState` que solo se renovaba al recomponer. Una selección seguida inmediatamente de salida podía guardar la voz anterior. El observador lee ahora los `MutableState` actuales de etapa, borrador y finalización. Una nueva regresión pausa el reloj Compose, ejecuta la acción semántica de selección y detiene la actividad antes del siguiente frame; comprueba disco y restauración posterior. No añade esperas arbitrarias ni cambia la semántica de los chips. Quedan 21 pruebas instrumentadas previstas, pendientes de la nueva ejecución completa. **El porcentaje se mantiene en 60 %** hasta validar el correctivo; los resultados de `1fdbe8b` no certifican este parche.

Última actualización: **27 de octubre de 2026, 11:49 UTC**. Objetivo: ampliación de **v0.4.0**. Número de compilación experimental **0.4.1-dev**, código 5. No se ha creado una Release estable.

- Progreso general ponderado: **85 %**; incluye implementación y pruebas automáticas, con descuento explícito de validación real pendiente.
- Fase actual: cierre documental, retirada del diagnóstico temporal y comprobación de CI del commit final.
- Último commit de implementación publicado y validado al redactar: **fb19b89**, rama `master`, push realizado. El commit que contiene este registro incorpora el cierre; consulta `git log` y la CI de HEAD para identificarlo.
- Evidencia: [Actions 40497932982](https://github.com/PbtServers/PbtMetaGPT/actions/runs/40497932982), tres trabajos correctos, APK experimental publicada. Cada artifact pertenece a su commit; uno anterior no valida cambios posteriores.
- Compilación: debug y Android Lint correctos en CI con Java 17 y SDK 36. El entorno local no tiene SDK Android; no se presenta esa compilación como local.
- Pruebas: **43 JVM + 7 Android/emulador + 19 Node + 5 Python = 74 superadas**, sin llamadas de pago ni sensores físicos. Docker/proxy y escáner de patrones de secretos en fuentes/APK correctos.
- Tiempo transcurrido medido: unos **105 minutos**. Estimación restante para comprobar el cierre: **8–15 minutos**, sin garantía. No hay una estimación fiable de aceptación física sin teléfono/gafas y configuración personal de los proveedores.

## Reparto del progreso

| Fase | Completado/peso | Qué falta para aceptación completa |
| --- | ---: | --- |
| BYOK y conexión directa | 20/25 | Conversación real con credencial del usuario, cuota, red y contexto |
| Configuración y perfiles | 14/15 | Recorrido manual completo con muestra auditiva |
| Frases múltiples y motor local | 17/20 | Precisión acústica, nombres, batería y rutas en teléfono |
| Motores TTS y modelos | 16/20 | Proveedores reales, escucha y rendimiento; vocabulario de modelos propios |
| Integración y diagnóstico | 9/10 | Confirmación física de rutas/sensores |
| Pruebas, documentación y APK | 9/10 | Protocolo manual registrado con hardware y servicios reales |

## Implementado y respaldado

- BYOK cifrado con Android Keystore/AES-GCM, sustitución/eliminación, advertencia de cliente móvil y backend HTTPS opcional. No se incorporan claves de desarrollador ni secretos de CI en la APK.
- Perfiles v2 con migración v1: personalidad, voz/motor, idioma, avatar de emoji/iniciales, bienvenida, confirmación y duración. Exportación/importación sin almacenes de claves ni datos de proveedores; referencias locales de voz deben reasociarse en otro teléfono.
- Múltiples frases por asistente: edición, eliminación, habilitación, principal y nombre automático. Comparación exacta o tolerancia textual opcional; rechazo de ambigüedad y de activaciones duplicadas/antiguas. Selección del asistente correcto entre perfiles del mismo idioma.
- Reconocimiento exclusivamente on-device con consentimiento, indicador, parada, ventanas visibles de hasta dos minutos y alternativa manual. Prueba escrita y prueba local sin abrir OpenAI. No hay escucha en segundo plano ni acceso a «Hey Meta».
- Biblioteca de voces: Android TTS local instalado, OpenAI Realtime nativo, OpenAI Speech transitorio, API HTTPS con token cifrado separado y paquetes Piper/VITS para Sherpa ONNX 2.0.1. Solo se exponen controles implementados por cada adaptador.
- Paquetes de datos con cuatro nombres fijos, hashes, límites de descompresión, metadatos ONNX, símbolos y pronunciaciones. Rechazo de código/rutas/archivos dañados, autorización/licencia/procedencia, requisitos y eliminación de almacenamiento. Cancelar antes de publicar limpia la instalación temporal.
- Modelo ejecutado en UID aislado, sin credenciales ni acceso al directorio privado. Recibe descriptores y usa un ZIP interno de dos assets en memoria mediante ResourcesProvider/AssetManager; nunca instala código del paquete.
- **Inferencia real acreditada en emulador**: fixture LJSpeech/Piper con pesos y SHA-256 fijados produce PCM no vacío/no nulo y frecuencia válida. Se prueba también eliminar el paquete. No acredita pronunciación, calidad ni altavoces. La fixture solo se descarga para la APK de pruebas, no para la aplicación principal.
- Realtime textual para TTS alternativo, contexto en la misma sesión, audio nativo deshabilitado y un solo trabajo/reproductor cancelable. Micrófono apagado durante síntesis/reproducción alternativa. Interrupción, pausa, cierre, descarte de resultados tardíos y limpieza de WAV temporal.
- Interfaz, README, arquitectura, biblioteca, protocolo y seguimiento en español. Diagnóstico distingue configuración validada, PCM recibido y confirmación física pendiente.

## Incidencias resueltas

1. Los metadatos oficiales Piper usan `comment`, `sample_rate`, `n_speakers` y `language`; se corrigieron los campos inicialmente asumidos y el nombre de instancia aislada exigido por Android.
2. La apertura de `/proc/self/fd` desde el UID aislado devolvía error 13. La carga ahora usa assets por descriptor sin ampliar permisos ni retirar aislamiento.
3. El diagnóstico completo identificó un aborto posterior en JNI: buscaba `invoke(float[]) -> Integer` en una lambda indy de Kotlin. Se sustituyó por un objeto de función explícito con `@Keep`. La CI de `fb19b89` supera la prueba real que fallaba. No se ha omitido ni sustituido por pesos ficticios.
4. El bloque de cierre retira el diagnóstico ampliado temporal de la fixture; los errores de la aplicación conservan códigos/etapas filtrados.

## Pendientes y limitaciones

- **OpenAI real:** BYOK, sesión WebRTC, voces nativas, varios turnos/contexto, cancelación, cuota y red. No hay credencial de pago configurada en el entorno. Los tests HTTP usan un servidor de prueba y no acreditan disponibilidad del proveedor.
- **TTS real:** Android instalado, OpenAI Speech y API externa requieren consulta/síntesis/escucha con su proveedor y condiciones. El modelo local necesita diccionario para cada palabra/número: no incorpora un fonemizador universal, no acepta ONNX arbitrario y usa solo hablante 0. Puede fallar por cobertura o recursos.
- **Teléfono:** reconocimiento acústico en español y nombres inventados, falsos positivos/negativos reales, batería, micrófono, audición, interrupción y comportamiento al navegar/bloquear. La frase no es autenticación.
- **Ray-Ban Meta:** DAT, registro, descubrimiento, conexión, entrada y salida Bluetooth por separado, cámara autorizada y visión. Ningún modelo/firmware certificado físicamente. No se promete captura continua mediante gafas, firmware personalizado ni segundo plano.
- **Servidor opcional:** Ubuntu/HTTPS público, certificado y conexión al proveedor pendientes; contenedores y proxy solo comprobados en CI.
- OpenAI Speech está anunciado para retirada el 06/01/2027; el adaptador se desactiva en esa fecha. Realtime sigue como opción predeterminada.
- La licencia/procedencia y declaración del propietario no prueban por sí solas derechos sobre una voz. No se distribuyen modelos clonados de actores.

## Próxima tarea concreta y continuidad

Comprobar la CI y artifact del commit de cierre sin reutilizar el resultado de otro SHA. Después instalar su APK en un teléfono, crear Jarvis, configurar una clave personal dentro de la app, probar primero Realtime con el móvil y registrar el protocolo de `PRUEBAS_REALES.md`. Probar a continuación reconocimiento local, motores alternativos y finalmente cada ruta de las gafas por separado. No enviar claves, fotos ni conversaciones al repositorio.

Al reanudar: leer README, AGENTS y este archivo, comprobar `git status`, `origin/master`, CI y artifacts. Continuar con el siguiente pendiente real, sin rehacer lo validado. Mantener commits/push por bloque significativo y el estado experimental hasta superar aceptación manual. No usar force push ni publicar una Release estable por el mero éxito de CI.

## Renovación documental (27 de octubre de 2026)

Se ha reestructurado el README para separar introducción, instalación, estados verificables, uso, DAT, privacidad, arquitectura y desarrollo. El detalle anterior se conserva en `docs/GUIA_TECNICA.md`. Se han preparado `CONTRIBUTING.md`, `docs/DESARROLLO.md`, plantillas de incidencias y la guía de capturas reales. No se han añadido capturas ficticias ni modificado la aplicación Android.

**El porcentaje funcional permanece en el 85 % documentado**: mejorar el README no valida conversación con API real ni hardware Ray-Ban Meta. La validación CI de cualquier commit de documentación deberá comprobarse por su SHA.

## Seguimiento del escáner tras c043114

Progreso de estabilización **35 %**, separado del 85 % funcional histórico. La ejecución 40561936117 conserva cinco hallazgos idénticos de 30 bytes en distintas cadenas de `classes4.dex`, sin atribución a constantes o dependencias. No se ha demostrado si son secretos: el bloqueo continúa y no se añade una excepción.

Se amplía exclusivamente la atribución segura a nombres, tipos, anotaciones y depuración DEX; 25 pruebas Python correctas, incluidas cinco regresiones nuevas. No se modifica la lógica Android ni los patrones de detección. README intacto y seis archivos previos conservados byte a byte. La siguiente evidencia necesaria es el informe ampliado del DEX real; Android 15/16 y APK nueva siguen pendientes. Véase `docs/DIAGNOSTICO_ESCANER.md`.
