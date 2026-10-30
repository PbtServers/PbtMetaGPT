# Seguimiento de PbtMetaGPT

## Reparación de activación, español y acceso desde Inicio · 30/10/2026

**45 % de esta nueva intervención · inicio 10:54 UTC · corte 11:44 UTC (00:50).** Base local/remota `668a5b0e783da4757ba98983a002fce0f9f990b6`, cuya [CI 40982927303](https://github.com/PbtServers/PbtMetaGPT/actions/runs/40982927303) termina correctamente: 198 JVM (una remota optativa omitida), 59 instrumentadas por API35/API36 y publicación de APK. Esa evidencia no acredita los fallos físicos comunicados ahora. Pesos actuales: auditoría 10, activación 20, español/comandos 25, rendimiento 15, Inicio 10, integración/segundo plano 5 y pruebas/publicación 15.

Causas comprobadas en código: el modo continuo retornaba a Vosk antes de consultar la autorización de Whisper; la transferencia esperaba jobs sin plazo; la captura Vosk podía esperar indefinidamente una ruta no confirmada; se anunciaba lista antes de recibir PCM válido; cada turno recargaba Model; las lecturas añadían un delay fijo aunque hubiera PCM pendiente; el finally de una generación antigua cancelaba el detector global de otra nueva. Inicio elegía el botón Android alternativo y omitía el instalador del servicio Vosk. Variantes musicales solicitadas no estaban en el catálogo de intenciones.

Implementado, pendiente de CI Android: watchdogs de arranque/activación, transferencia acotada y errores visibles recuperables, protección frente al error residual anterior, captura PCM compartida Vosk/Whisper con prebuffer y confirmación de ruta, plazo inicial 5 s y turnos de hasta 28 s, modelos Vosk compartidos con reconocedores independientes, cancelación por generación, selección persistente de motor también en continuo, precisión/rapidez con pausas 1,7/1 s y Whisper large-v3/turbo, idioma Groq `es` sin traducción y vocabulario orientativo de nombres propios. Continúa disponible dictado local. No se promete que Vosk pequeño reconozca cualquier topónimo ni una mejora acústica ya medida.

Inicio conserva el diseño y muestra el control de escucha permanente, estado del modelo, descarga consentida/progreso/cancelación/reintento y estado real del servicio. Instalación única compartida, SHA-256, comprobación de espacio y reemplazo conservando el archivo anterior. Órdenes completas nuevas de Spotify y rutas públicas diferenciadas para Google, navegador, búsqueda y asistente digital; no se amplían las capacidades del transporte Spotify. Transferencias a otras aplicaciones liberan audio y respetan notificación/bloqueo. Los diagnósticos añaden categorías/tiempos/micrófono/motor, sin conversaciones ni audio.

**58 JVM locales correctas (0,369 s)**: incluye siete regresiones nuevas de activación, espera acotada, consentimiento de red/selección, pausas/prebuffer, intenciones y diagnóstico, además de regresiones previas. **39 Python, 16 backend y escáner de fuentes correctos.** Gradle Java17 se intenta: el entorno local carece de SDK Android; preparar Sherpa informa de patchelf ausente. ADB no tiene dispositivos. Se mantienen las pruebas de CI y se añaden pruebas instrumentadas del control de Inicio y transferencia exclusiva real de AudioRecord. Las seis modificaciones ajenas siguen excluidas.

Pendiente: compilar/Lint y suites completas API35/36 del nuevo SHA, ampliar corpus acústico y métricas WER/CER, documentar resultados reales y publicar APK solo tras las barreras existentes. No hay estimación fiable de aceptación física Nothing/Ray-Ban, latencia audible, Bluetooth o batería. No es Release estable.

## Acuerdo de texto para Google tras inferencia Android · 30/10/2026

**80 % acreditado · 00:56 UTC · 2 h 47 min desde el inicio.** `8e2b4a0` supera **196 JVM (195 correctas, una remota optativa omitida), Lint, compilación, seguridad y backend** en [CI 40981035518](https://github.com/PbtServers/PbtMetaGPT/actions/runs/40981035518). El bloque inicial API35 de 52 pruebas detecta un fallo Google: Davefx transcribe exactamente «abre el asistente del teléfono», confianza mínima 0,718073, insuficiente para ejecutar. La prueba musical anterior ya pasa en ese bloque, con sus positivos y negativos ampliados. Captura/servicio y la otra API aún necesitan resultados completos; no se atribuye al aviso de consola 5554 ni se publica APK.

Se mantiene el umbral de ejecución y el positivo Google. El reconocedor restringido de acciones comparte modelo/PCM con el libre y con la gramática ChatGPT conservada. Para Google se exige **igualdad del texto completo entre ambos**, confianza restringida ≥0,95 y las mismas fronteras inicial/final de voz. No se acepta «regula él», «agregó», negaciones, explicaciones ni continuaciones; no se añade un cuarto reconocedor ni inferencia pasiva. Dos JVM nuevas cubren el caso 0,718073 con acuerdo alto y rechazan textos distintos/0,94/NaN/fronteras truncadas. Las limitaciones de la orden corta y el bloqueo de Android siguen explícitas, con gúguel peninsular.

Próximo paso: publicar esta corrección, ejecutar todas las suites y generar la APK del SHA final solo si pasan ambas APIs. La estimación anterior se amplía un ciclo de CI; la aceptación física sigue sin fecha. ADB sigue sin dispositivo tras volver a comprobarlo; se ha consultado al usuario si puede conectar el Nothing mientras continúa CI.

## Corrección de inferencia Spotify tras CI · 30/10/2026

La CI Google `ee56d0a` detecta a las 00:34 UTC una importación ausente de `Intent` en el botón de ajustes de MainActivity. Se corrige explícitamente; no llegó a JVM/emuladores y no publicó APK. La corrección musical `7aad232` necesita también esa importación, incluida en el siguiente commit. Las puertas de publicación permanecen intactas.

**78 % acreditado · 00:37 UTC · 2 h 28 min desde el inicio.** La [CI de 376cefc](https://github.com/PbtServers/PbtMetaGPT/actions/runs/40978269641) registra **191 JVM (190 correctas, una remota optativa omitida), Lint/compilación/seguridad/backend correctos y 55/56 instrumentadas por API** en los informes conservados. API36 termina con fallo; API35 se cancela al publicarse el siguiente bloque, después de conservar su informe de 56 casos. Pasan Jarvis/Luna por VAD→Vosk, tres ciclos de pantalla, foco/interrupción, transporte multimedia bajo bloqueo, filtro de paquete, revocación y convivencia de foco durante escucha. No hubo APK de ese SHA por fallar la barrera.

Fallo exacto: «Pon Spotify» produce «cuando spotify», confianza libre 0,443869 en Android. Se añade un reconocedor restringido musical **con el mismo Model y PCM**, separado de la gramática ChatGPT existente. Solo el reconocedor libre marca el cierre. Se exige orden completa, confianza musical ≥0,93, misma frontera inicial/final del VAD y rechazo de negación/explicación del dictado libre. No se crea alias «cuando spotify» ni se baja ningún umbral. No se ejecuta la gramática durante espera pasiva. Se liberan/recrean todos los reconocedores al cerrar/perder tramas.

Se conserva el positivo que falló y se amplían negativos con continuación «mañana», mención «Cuando Spotify está abierto», «Prepara la cena», «Para mañana», «Quiero hablar contigo», negación de salto y frase sobre una canción. Pruebas de escritorio dan confianza restringida 1,0 en las cinco órdenes; las negaciones se filtran por dictado libre y las continuaciones por fronteras, sin atribuirlo al runtime Android. Una nueva JVM cubre confianza 0,92/NaN, negación, ausencia de texto y fronteras incompletas. La CI del bloque Google `ee56d0a` sigue en curso; esta corrección requiere nueva ejecución completa.

Pesos acreditados: auditoría 10/10, DAT/micrófono 20/25, ChatGPT/transferencias 12/15, bloqueo 16/25, voces 12/15 y pruebas/documentación 8/10 = **78/100**. Aumenta por comprobar los nuevos recorridos Android de conexión, foco, pantalla y transporte; no se acredita hardware. Siguen pendientes la nueva inferencia musical, Google/retorno, APK final y aceptación Nothing/Ray-Ban. Estimación de cierre de código/CI/APK 25–40 min, condicionada a resultados; hardware sin plazo fiable.

## Ampliación solicitada: «abre Google» y bloqueo · 30/10/2026

**72 % acreditado · 00:27 UTC · 2 h 18 min desde el inicio.** El usuario pide Google/assistant del teléfono y reafirma pantalla bloqueada y pronunciación peninsular «gúguel». Se integra transferencia compartida con ChatGPT: respuesta cancelada, micrófono/foco liberados y escucha revocada antes de abrir. En espera, la orden incluida tras la activación se resuelve localmente sin conectar un proveedor. Entrada pública `ACTION_VOICE_COMMAND` para Google si existe y después `ACTION_ASSIST`; únicamente actividad exportada y permiso accesible. No se usa `VOICE_ASSIST` oculto, navegador, componentes privados, accesibilidad ni contexto de pantalla.

Al bloquear/minimizar se ofrece notificación: **Android/Google pueden exigir toque o desbloqueo**. No se afirma sustitución manos libres de «Hey Google», asistente ya escuchando ni apertura física validada. El usuario elige Google en Aplicaciones predeterminadas; puede corresponder a Gemini. README y guía documentan esa diferencia respecto a Spotify, cuyo transporte no requiere abrir actividad.

**45 JVM locales correctas (0,090 s)** como subconjunto: órdenes/negaciones/eco, cierre no ambiguo, Spotify, conversación/cancelación, políticas y pronunciación española; escáner y 21 enlaces locales de README correctos. La pronunciación se fija a gúguel, retirando el ensayo gúgol antes de publicarlo. **Limitación acústica explícita de escritorio:** la orden corta Davefx/Sharvard F se confunde, Sharvard M reconoce «abre google» con confianza 1,0; «abre el asistente del teléfono» se reconoce con las tres. No se convierten palabras parecidas en órdenes ni se rebajan umbrales. Preparadas regresiones nativas de ese alcance, acciones públicas/resolución y transferencia desde servicio con pantalla apagada, sin iniciar Google real desde CI.

`376cefc` con Spotify supera compilación/JVM/Lint, seguridad y backend en [CI 40978269641](https://github.com/PbtServers/PbtMetaGPT/actions/runs/40978269641); sus emuladores siguen ejecutándose. Este bloque Google necesita su propia CI. Próximo paso: comprobar esos resultados, publicar Google y completar todas las puertas de APK del SHA final. Sin SDK Android local, patchelf ni dispositivo ADB; no hay aceptación física. Se mantienen los pesos 10/25/15/25/15/10, con las ampliaciones pendientes de validación y estimaciones condicionadas a CI.

## Ampliación solicitada: Spotify con pantalla bloqueada · 30/10/2026

**72 % acreditado · 00:11 UTC · 2 h 02 min desde el inicio.** El usuario añade reproducción/pausa y cambio de canción bloqueado. Se mantiene el porcentaje anterior hasta la CI del nuevo conjunto; esta ampliación añade un ciclo completo de compilación y emuladores. La aceptación física no tiene plazo fiable. `815afeb`, con el README de uso y Jarvis, supera JVM/Lint/compilación/seguridad/backend en [CI 40976326491](https://github.com/PbtServers/PbtMetaGPT/actions/runs/40976326491); sus emuladores siguen en curso al redactar.

Controles por `MediaSessionManager.getActiveSessions` autorizado y `MediaController.TransportControls`, filtro exacto Spotify, acciones publicadas y ningún comando global a otro reproductor. Preferencia voluntaria en Dispositivos → Música, consentimiento sobre acceso amplio a notificaciones, estado del permiso al volver de Android y gestión/revocación manual. El listener no procesa ni conserva notificaciones. No se inventa inicio en frío: Spotify debe publicar sesión después de reproducir una canción. Se informa de permiso ausente, sesión ausente y acción no disponible; enviar una orden no acredita sonido.

Los botones de prueba usan la misma transferencia que las órdenes: cancelan la respuesta y esperan la liberación de foco, evitando pedir reproducción por debajo del audio del asistente. El resultado se muestra mediante el aviso central de la aplicación.

En modo música autorizado, la espera conserva micrófono/ruta sin retener foco de reproducción; los turnos normales sí adquieren/liberan foco. Fuera de ese modo se preserva la política anterior. La orden incluida tras «Atento Jarvis» se ejecuta localmente, sin tono ni llamada al proveedor, y vuelve a la espera ya consentida; no reactiva una escucha suspendida. Dentro de una conversación cancela respuestas y libera foco antes de enviar el control. «Para», «Termina» y «Deja de escuchar» conservados. HFP/A2DP, música real, llamadas y reconocimiento con música de fondo necesitan ensayo físico.

**37 JVM locales correctas (0,091 s)**: cuatro nuevas de música/política y regresiones de conversación/cancelación/ChatGPT/consentimiento, como subconjunto de la suite completa; 39 Python y escáner de fuentes correctos. Piper/Vosk de escritorio reconoce las cinco frases positivas y conserva negaciones; no se atribuye a Android. Preparadas tres instrumentadas adicionales: reconocimiento PCM nativo, transporte de una sesión Android de prueba bajo bloqueo con filtro de paquete/revocación y convivencia de foco multimedia con captura del emulador. La sesión de prueba no es Spotify. Las seis huellas ajenas siguen intactas. Próximo paso: publicar el bloque experimental y resolver su CI sin omitir pruebas.

## Documentación de producto solicitada · 30/10/2026

**72 % acreditado · 23:55 UTC · 1 h 46 min desde el inicio; aceptación física sin estimación fiable.** README reescrito como documentación de uso: instalación, conversación y órdenes, escucha bloqueada, selección/prueba de micrófono, ChatGPT oficial y Davefx/Sharvard. Se retiran el diario, el aviso de portada de candidata y las instrucciones antiguas que negaban toda escucha en segundo plano; no se presenta una APK debug como Release firmada ni se certifica hardware. Los límites/evidencias se conservan en Validación y la guía técnica. Los 20 enlaces locales del README existen; diff, 39 Python y escáner de fuentes correctos.

`3db686ddc43915934038634651b483d5fecfef36` supera JVM **187 (186 correctas y una remota optativa omitida)**, Lint, compilación, seguridad de fuentes/APK y backend en [CI 40975478719](https://github.com/PbtServers/PbtMetaGPT/actions/runs/40975478719); los emuladores siguen en curso. Se amplía la regresión acústica Piper→VAD→Vosk con «Atento Jarvis, dime la hora», negativo y repetición, conservando Luna y todos los controles existentes. Jarvis está en el vocabulario real; el ensayo de escritorio reconoce la frase, pendiente todavía su ejecución Android en este conjunto. No acredita voz desde gafas.

La entrega documental responde a «README como proyecto terminado». La publicación estable sigue condicionada a los ensayos obligatorios del Nothing/Ray-Ban y a las condiciones de distribución; el formato del README no sustituye esas evidencias. Se mantienen seis cambios ajenos intactos. Pesos acreditados: auditoría 10/10, DAT/micrófono 17/25, ChatGPT 12/15, bloqueo 13/25, voces 12/15, pruebas/documentación 8/10. Próximo paso: CI completa y APK del SHA que contiene este README y la regresión Jarvis.

## Candidata final: DAT, micrófono, bloqueo, ChatGPT y español · 29/10/2026

**Estado actual: 70 % acreditado · 23:44 UTC · 1 h 35 min desde el inicio.** El código `0b4e9d21f1b96c929caca9b8521d156866c0a028` supera [CI 40973347229](https://github.com/PbtServers/PbtMetaGPT/actions/runs/40973347229): **185 JVM (184 correctas, una remota optativa omitida), 52/52 instrumentadas API35 y 52/52 API36, Lint, compilación, 39 Python, 19 Node/backend/contenedores y escáner de fuentes/APK correctos**. La publicación de APK de ese SHA sigue en curso al redactar. Las comprobaciones finales añadidas después necesitan su propia CI. La aceptación física y revisión de distribución estable siguen pendientes, sin plazo fiable.

| Fase de esta intervención | Acreditado / peso | Pendiente |
| --- | ---: | --- |
| Auditoría y diagnóstico | 10/10 | Sin atribuir causas físicas no observadas |
| DAT y micrófono | 17/25 | Fuente HFP/LE y reconexión real Nothing/Ray-Ban |
| ChatGPT oficial | 12/15 | Aplicación real, opciones de cuenta y transición bloqueada |
| Servicio y bloqueo | 13/25 | Última prueba de foco/ciclos; Doze, consumo y llamadas físicos |
| Voces Piper de España | 12/15 | Audición/latencia física y aceptación del timbre |
| Pruebas y documentación | 6/10 | CI/APK del último conjunto y aceptación física |
| **Total** | **70/100** | **Experimental; no Release estable** |

Los siguientes párrafos conservan el diagnóstico y resultados anteriores en orden de intervención; sus porcentajes iniciales no sustituyen el estado actual.

Último conjunto preparado a las **23:46 UTC**, 1 h 37 min: se afinan las etiquetas de sesión/enlace/audio, comprobación del par HFP por la API pública y dirección en RAM cuando existe (sin igualar nombres de puertos), consultas periódicas a un segundo y publicación de estado solo cuando cambia. La documentación incorpora la guía de uso/aceptación física y las voces solicitadas. Se añaden tres ciclos pantalla/aplicación y otra petición real de foco en emulador, con recuperación explícita; pendiente su CI. Las dos nuevas JVM de par de audio pasan en 0,008 s; **47 JVM locales en total como subconjunto**, 39 Python, 19 Node y escáner de fuentes correctos. Las seis huellas preexistentes siguen idénticas. Master remoto coincide antes de publicar.

Medida puntual de **0b4e9d2**, API35 emulador bloqueado: ventana 30.023 ms, CPU 1.435 ms y PSS 214.094 KiB. No es batería, no es un máximo ni un ensayo controlado del Nothing; no se promete un porcentaje de ahorro a partir de dos runners distintos. El último ajuste de consultas de sistema todavía no está incluido en esa cifra.

Inicio **22:08:40 UTC (23:08:40 Madrid)**. Base local y remota `446ffcbc917f1089e5b77602432a4185629c41a2`; exclusivamente master. Se conservan seis archivos ajenos con huellas SHA-256 en `app/build/huellas-previas-final.json`, fuera de Git. Los pesos de esta intervención son los solicitados: **10/25/15/25/15/10**; no se suman los porcentajes históricos. **15 % provisional**, pendiente de compilación/CI y hardware; sin estimación fiable de aceptación física.

Diagnóstico confirmado: `MainActivity.onStop()` cerraba siempre DAT. El controlador y sus observadores pasan al proceso (`PbtApplication`), sin retener Activity; registro/desregistro reciben Activity solo durante la acción pública. Ocultar/recrear la interfaz cancela cámara, conserva DAT si el servicio autorizado sigue activo y nunca reabre cámara. Sin servicio se liberan recursos al salir. Detener el servicio fuera de la UI cierra DAT. Recuperación de sesión limitada a tres intentos (2/5/15 s por autorización), con enlace compatible y propietario activo; no se reintenta durante HFP para evitar el conflicto de firmware documentado por Meta. El enlace DAT, la sesión y la entrada de audio son estados distintos.

Evidencias locales del primer bloque: **3 JVM de política DAT** con Java17/Kotlin2.0.21, **39 Python**, **16 Node backend**, escáner de fuentes correctos. Gradle Java17 se ejecutó y falla por **SDK location not found**; preparar Sherpa falla por falta de **patchelf**. ADB ejecutado fuera del aislamiento: **ningún dispositivo**. Las pruebas Android completas corresponden a CI, sin atribuir al nuevo código las de la base. La base `446ffcb` sí tiene [CI 40779435320](https://github.com/PbtServers/PbtMetaGPT/actions/runs/40779435320) correcta (174 JVM, una remota optativa omitida; 47 instrumentadas por API).

Fuentes revisadas: [muestra Meta](https://github.com/facebook/meta-wearables-dat-android/tree/main/samples/CameraAccess), [conflicto HFP/firmware confirmado por Meta](https://github.com/facebook/meta-wearables-dat-android/discussions/130), [ChatGPT Voice](https://help.openai.com/en/articles/20001274-chatgpt-voice), APIs Android AudioRecord/AudioManager y catálogo original Kokoro. OpenAI documenta «Iniciar con Voz» para una conversación nueva/vacía; no se ha encontrado un intent público que garantice Live. Dora/Alex/Santa ya existen en el código; se revisarán visibilidad, persistencia y audio distinto con el mismo texto. Próximo bloque: selección de entrada, prueba PCM/transcripción local y continuidad de ruta del servicio.

Segundo bloque experimental: ruta/foco compartidos con concesiones; el servicio la retiene entre detector, confirmación, dictado y salida, evitando renegociar HFP en cada turno. Selección Automático/gafas identificadas por el usuario/teléfono en Dispositivos; se muestra nombre/tipo/id y frecuencia real de captura. Se solicita también entrada preferida con las APIs públicas AudioRecord/WebRTC, manteniendo `setCommunicationDevice` como ruta bidireccional. Una selección Bluetooth no se declara micrófono real: los lectores descartan PCM si la entrada no coincide y suspenden ante pérdida/cambio/silencio del sistema. Sin fallback oculto a teléfono. Prueba explícita local Vosk de 12 s con nivel/transcripción efímeros, sin archivos/red ni logs de texto.

Actualización **22:43 UTC**, 34 minutos medidos desde el inicio: **40 % provisional**, aceptación física sin plazo estimable. La [CI de ce4b1a1](https://github.com/PbtServers/PbtMetaGPT/actions/runs/40966114487) detecta métodos duplicados en AudioInputSettings; corregidos sin omitir pruebas. ChatGPT reconoce también «Abre ChatGPT» e «Inicia ChatGPT en voz», con gramática fonética y nuevos casos PCM. La transferencia espera hasta liberar micrófono y todas las concesiones de foco; la política usada por el launcher evita abrir una actividad con teléfono bloqueado. Se explican «Iniciar con Voz» y su condición de conversación nueva/vacía, sin afirmar Live iniciado. **30 JVM locales correctas (0,087 s)**, incluidas sesión/contexto/cancelación, órdenes y políticas DAT/launcher; no es la suite Android.

**Alcance de voces corregido por el usuario durante esta intervención:** quiere más voces **Piper como Davefx, de español de España**; no desea ampliar Kokoro. Se investigan Sharvard (dos hablantes, es_ES, calidad medium, 22.050 Hz, datos CC BY 3.0) y su contrato público. Se conservará compatibilidad de perfiles Kokoro en Avanzada. La integración y la inferencia de las nuevas voces todavía no están acreditadas.

Actualización **23:00 UTC / 00:00 Madrid del 30/10**, 51 minutos: **50 % provisional**. Sharvard masculina/femenina integradas en Natural junto a Davefx; Kokoro conservado en Avanzada. Una descarga de 76.733.615 bytes para ambos hablantes, configuración y MODEL_CARD con hashes propios, revisión fija `5041a7a49ae5b3a0f30e6837769eb03fdbb231bd`. Los hablantes 0=M y 1=F se verificaron en la configuración original y en el ONNX real (`input`, `input_lengths`, `scales`, `sid`). Se utiliza un solo modelo activo; cambiar M/F reutiliza la misma sesión. Persistencia inmediata y muestra voluntaria; sin descargar automáticamente.

Evidencia de escritorio Windows/Python Piper y Vosk reales, frase fija «En Zaragoza hay cinco plazas. Hablamos en español de España.»: sid0 produce 156.672 bytes PCM, sid1 170.496; SHA256 distintos con ruido de fixture cero. Vosk transcribe respectivamente «en zaragoza y cinco plazas hablamos en español de españa» y «en zaragoza hay cinco plazas hablamos en español de españa». Inferencia+ASR 1,078 s y 0,516 s en escritorio, **no latencia Android ni audio oído**. No se guarda WAV ni se envía contenido privado. Dos JVM de catálogo correctas, más las 30 de políticas anteriores y 39 Python. Preparadas instrumentadas Android reales de ambas voces/reutilización y recorrido UI con muestra; falta ejecutarlas. La [CI de 2b0651e](https://github.com/PbtServers/PbtMetaGPT/actions/runs/40968211929) encontró `this` de ColumnScope en el botón de fallback; corregido con Activity explícita. Las puertas de APK permanecen intactas.

Actualización **23:26 UTC**, 1 h 17 min: **60 % provisional**. [9939b34 / CI 40970162902](https://github.com/PbtServers/PbtMetaGPT/actions/runs/40970162902): **180 JVM (179 correctas, una remota optativa omitida), Lint, compilación, Python/Node, seguridad y backend correctos; 49/51 instrumentadas en API35 y 49/51 en API36**. Pasan las dos voces Sharvard, su muestra/persistencia UI, controlador DAT, servicio bloqueado y ruta compartida. Fallan la aserción antigua de tipo FGS (128 frente a 144, ahora se exigen explícitamente ambos tipos/permisos) y «Abre ChatGPT» sintético: eSpeak concatena mal las siglas y la gramática devuelve solo «chat ge pe te», que se rechaza correctamente. Se incorpora deletreo español explícito antes del fonemizador **de producción**; se mantienen umbrales, fronteras acústicas y todos los negativos.

Se añade VAD ligero antes de Vosk: silencio no inferido, pre-roll de 300 ms, intervenciones acotadas y reloj corregido por tramas omitidas. La misma canalización se usa en una instrumentada nueva Piper→Vosk con activación/pregunta, negativo y repetición. Dos JVM prueban diez minutos sintéticos sin inferencia y conservación de prefijo. Se libera foco/CPU al pausar conversación; el servicio serializa la liberación con un nuevo arranque. **45 JVM locales correctas (0,151 s)**; no se suman a la suite como pruebas distintas. Pendiente CI de este nuevo bloque. CPU/PSS de una ventana de 9939b34 API35: 2.876 ms/30.031 ms y 223.684 KiB; corresponde a ASR anterior al VAD, no batería física. Sin APK nueva aprobada.

El servicio declara `microphone|connectedDevice`, como corresponde al micrófono y enlace externo; el segundo tipo solo se activa con permiso Bluetooth. Un único wakelock parcial de sesión mantiene Vosk y los turnos autorizados al bloquear, y se libera al pausar/detener/error. No se ejecuta TTS, RVC ni proveedor durante espera pasiva. Vosk sigue siendo ASR, no un detector KWS dedicado: el consumo físico y una alternativa de menor consumo continúan pendientes. Nuevas instrumentadas comprueban controlador estable al recrear y ruta compartida/liberación/selección persistente; la aceptación de entrada externa requiere gafas. **30 % provisional; 39 Python/escáner locales correctos**, compilación e instrumentadas del bloque nuevas pendientes de CI. La CI DAT `c699ba0` ya supera compilación/JVM/Lint/seguridad/backend, con emuladores aún en curso al redactar.

## Conversación continua, interrupción local y ChatGPT · 28/10/2026

**Última APK validada: `75ad07bbda03f20198210891bd071a44f954b001`**, [Actions 40776480692](https://github.com/PbtServers/PbtMetaGPT/actions/runs/40776480692): **172 JVM (171 correctas y una prueba remota optativa omitida), 47/47 instrumentadas en API35 y 47/47 en API36, Lint, compilación, seguridad de fuentes/APK, 39 Python y 19 Node/backend correctos**. [APK experimental del mismo SHA](https://github.com/PbtServers/PbtMetaGPT/actions/runs/40776480692/artifacts/12855513856). No equivale a aceptación física ni a Release estable.

Cierre de implementación: activar escucha respeta la preferencia de segundo plano y explica el consentimiento correspondiente; la regresión instrumentada pulsa los controles reales. El adaptador Groq acepta los 32 mensajes acotados del historial más las instrucciones separadas, corrigiendo un límite antiguo de 18; dos regresiones HTTP locales verifican veinte turnos y rechazan peticiones excesivas. Estas modificaciones requieren sus propias puertas de [CI Android](https://github.com/PbtServers/PbtMetaGPT/actions/workflows/android.yml); los resultados anteriores pertenecen exclusivamente a `75ad07b`. El usuario hará por su cuenta las pruebas físicas, que siguen pendientes y no bloquean la entrega experimental.

### Historial de comprobaciones anteriores

`e7b028c`, [Actions 40772018129](https://github.com/PbtServers/PbtMetaGPT/actions/runs/40772018129): **172 JVM sin fallos/errores (una remota optativa omitida), Lint, compilación, seguridad y backend correctos; API36 47/47, API35 46/47**. Pasan las cuatro órdenes ChatGPT y las pruebas de sensores en ambas APIs. El único fallo API35 es PCM sintético de «Para» transcrito como «pero»; no se acepta esa palabra como alias ni se baja el umbral. Se contrastó íntegra la tabla de fonemas con la configuración oficial de Davefx, sin diferencias. El ruido generativo de Piper cambia la señal entre ejecuciones: las fixtures nativas pasan a ruido cero y comprueban igualdad de PCM al repetir síntesis; conservan todas las aserciones y añaden «Pero» como negativo. La app y las pruebas de generación de voz habituales mantienen los parámetros originales. Esa regresión controlada no valida variación acústica ni voz física. Se amplía el registro a 120 eventos numéricos para recopilar veinte ciclos. La CI de este correctivo debe repetirse; la APK continúa bloqueada hasta aprobar ambas APIs.

Seguimiento del correctivo `748c8ee`: [Actions 40770826877](https://github.com/PbtServers/PbtMetaGPT/actions/runs/40770826877), en curso. El usuario confirma que **ahora no puede conectar el teléfono**; se continúa con automatización/emulador y queda excluida cualquier afirmación de aceptación física. Se distingue el cierre del servicio por salir de la interfaz sin segundo plano (pausa y conserva contexto autorizado) de la acción explícita de detener escucha/conversación (finaliza ambas). Se amplía el recorrido instrumentado para verificar salida/retorno sin reactivar sensores. Pendiente CI del SHA que incorpore este ajuste.

`392c8bb`, [Actions 40756146710](https://github.com/PbtServers/PbtMetaGPT/actions/runs/40756146710), reintento del mismo SHA tras descarga NDK dañada (sin commit vacío): **169 JVM, Lint, compilación, seguridad y backend correctos. API35 46/47; API36 45/47.** La carrera PCM está resuelta en ambas APIs, incluido cierre manual durante escritura/captura. Pasan los tres casos de sensores y el servicio bloqueado registra CPU/PSS reales del emulador: API35 2169 ms CPU/30028 ms de ventana, 222012 KiB PSS; API36 1901/30023 ms, 218269 KiB. Solo una muestra por API, no consumo físico ni percentiles; no se publica APK con tests fallidos.

Los fallos restantes son reconocimiento sintético: el dictado libre puede omitir el comienzo de GPT y da confianza baja a una palabra corta pese a transcribirla como «para». Se añaden límites de voz del VAD a la comprobación de gramática; para «Para» se exige coincidencia exacta de ambos decodificadores, intervención aislada y el mismo umbral de sensibilidad sobre la confianza real de la gramática. Se amplían frases negativas, sin omitir tests. El eco normaliza siglas escritas/fonéticas. Al salir sin servicio se pausa la conversación autorizada conservando contexto en RAM; no se reactiva al volver. Se rechazan frases de cierre vacías/reservadas. **43/43 JVM locales en 0,117 s**; pendiente CI del nuevo correctivo y reconocimiento físico. El objetivo sigue aproximadamente al 65 %, incompleto.

Actualización 19:34 UTC (3 h 29 min desde la marca inicial; estimación anterior de plazo superada por incidencias y validación). `fae3df5`, [Actions 40732697827](https://github.com/PbtServers/PbtMetaGPT/actions/runs/40732697827): **165 JVM, cero fallos/errores, una remota optativa omitida; Lint, compilación, seguridad y backend correctos. API35 45/46 y API36 45/46**. Pasan servicio con pantalla bloqueada/notificación y callback PCM real del módulo WebRTC. La APK continúa bloqueada. El XML solo mostraba el timeout posterior de limpieza; el logcat completo identifica la causa primaria: `AudioTrack.write` usaba un puntero nativo liberado por Parar. Se corrige la carrera con escrituras no bloqueantes y exclusión por pista; la prueba mantiene reproducción/captura y exige liberar todos los recursos. Se conserva el diagnóstico, sin omitir pruebas.

Se detectó además que el grafo español no contiene GPT/ChatGPT. Una inferencia local real con Piper/Vosk Windows demostró que una gramática sola reconoce las cuatro órdenes pero también puede omitir negaciones. Se añade comparación temporal con el dictado libre y regresiones de audio positivas/negativas usando el mismo decodificador de producción, sin cambiar el detector abierto de «Para». No se atribuyen esos resultados de escritorio a Android ni a voz física: la nueva instrumentada debe pasar en ambas APIs. **40/40 JVM locales (0,126 s), 39 Python y escáner de fuentes correctos**. El servicio del emulador se mantiene bloqueado hasta obtener una ventana real de CPU/PSS del proceso; resultados pendientes del nuevo SHA. Se elimina una dependencia de tests duplicada.

Base `879fd8f97a84f55b1b4c6b01f36ff658aba9097a`, exclusivamente `master`, sin ramas/worktrees/PR. Se preservan los seis archivos locales previos (CONTRIBUTING, DESARROLLO, README de capturas, dos plantillas y version.json), con huellas SHA-256 fuera del repositorio. Inicio registrado 16:05 UTC. **Entrega experimental; aceptación física pendiente**, con APK validada de `75ad07b`. A las 22:17 UTC habían transcurrido 6 h 12 min, incluidas esperas de aprobación y CI; no se atribuye ese tiempo a rendimiento de la aplicación.

Primer bloque publicado `5383e1f`: [Actions 40724828293](https://github.com/PbtServers/PbtMetaGPT/actions/runs/40724828293). Backend/contenedores correctos; Android detectó colisión del identificador `android` con `android.os.SystemClock` y visibilidad privada del control Compose reutilizado. Se corrigen mediante import explícito y visibilidad interna, sin omitir pruebas. Se añade inferencia Piper es-ES → Vosk real para orden aislada/frase negativa, exclusivamente en la APK de tests. El ZIP oficial Vosk se fija por SHA-256 `09b23988…62c6511f`, verificado tras descarga de 39 817 833 bytes; no se versiona ni se incluye en la APK principal. **37 Python y escáner de fuentes correctos** en esta revisión. Compilación/API35/API36 requieren repetir CI; no se publicó APK de la ejecución fallida.

Correctivo `cca2ee4`: [Actions 40725874489](https://github.com/PbtServers/PbtMetaGPT/actions/runs/40725874489) supera JVM, Lint, compilación, seguridad y backend; emuladores aún en curso al preparar el siguiente bloque. ADB local no enumera dispositivos. Se añaden dos instrumentadas de captura real del emulador (servicio con pantalla bloqueada/otra tarea, pausa y parada por notificación, reanudación expresa y AudioRecord/AudioTrack simultáneos). La CI separa explícitamente los casos con permisos concedidos de los casos de denegación; ejecuta ambos y conserva/suma sus XML. No se desactiva ninguna prueba. El resultado de pantalla bloqueada del emulador no acredita políticas Nothing ni gafas. Se recogen CPU/PSS numéricos cada 30 s de espera pasiva y tipo real de entrada; resumen p50/p95 mediante `tools/resumir_latencias_escucha.py`. **39 Python y escáner correctos**; valores físicos siguen sin medir.

Revisión local a las 16:36 UTC: **25/25 JVM correctas en 0,257 s** (18 de conversación y 7 de canalización), Java 17; `bash -n` correcto. Se cubre también excepción nativa tardía sin publicar un error de una generación cancelada. Se prepara la ruta antes del detector para que no nazca en otra entrada mientras se sintetiza, se pausa ante pérdida de captura y se revoca el consentimiento de rearmado local antes de transferir a ChatGPT. Inactividad se mide desde captura preparada y se cancela al comenzar la intervención, no al terminarla. Estas correcciones requieren CI del nuevo SHA.

Resultado completo de `cca2ee4`: **159 JVM, cero fallos/errores, una remota optativa omitida; Lint, seguridad, compilación y backend correctos. API35 43/43; API36 42/43**. Único fallo: aserción positiva de PCM «Para» de Piper → Vosk en API36. Se mantiene la prueba y su umbral de confianza. Se añade diagnóstico limitado a la frase fija de la fixture y un segundo de precaptura silenciosa, equivalente al detector ya armado antes de la orden; no se da por resuelta la causa ni se acredita calidad acústica hasta repetir ambas APIs. La APK permanece bloqueada por ese fallo.

`088dd8c`, [Actions 40728264188](https://github.com/PbtServers/PbtMetaGPT/actions/runs/40728264188): **160 JVM sin fallos/errores (una remota optativa omitida), Lint, compilación, backend y seguridad correctos; Piper → Vosk positivo/negativo pasa en API35 y API36** con la precaptura realista. Ambas matrices completan 45 pruebas y fallan únicamente los dos casos nuevos de permisos/foco. El arnés concedía permisos entre bloques cuando Gradle ya había desinstalado el paquete, y el caso de audio pedía foco sin actividad visible (Android 15+ lo rechaza). Se conserva cada caso y se corrige mediante GrantPermissionRule en el segundo proceso y ActivityScenario visible; no se rebaja la protección del sistema. Requiere CI nueva.

Ampliación Realtime: se inspeccionó el AAR exacto 150.7871.01 (49 147 033 bytes, no versionado), cuyo contrato público permite callback de PCM, captura local y estado real de AEC. Se conecta Vosk al mismo capturador, con cola acotada y bloques reutilizables, sin usar el callback señalado solo para debug ni crear otro AudioRecord. TTS alternativo mantiene la pista saliente apagada. Se descartan eventos de respuestas canceladas y se elimina su texto incompleto del contexto remoto con evento oficial; sin llamadas reales en esta validación. Se prepara tercera instrumentada para el módulo WebRTC local sin PeerConnection/red. **28 JVM locales correctas (0,183 s), 39 Python y escáner**; pendiente compilación/emuladores del nuevo bloque y toda la validación acústica/proveedor real.

**Aclaración confirmada por el usuario: «Termina» debe volver a esperar la frase y permitir empezar otra conversación.** Se corrige la carrera entre publicar cierre y liberar AudioRecord: servicio y espera visible aguardan el cierre anterior antes de adquirir el micrófono. Un intento antiguo no puede iniciar espera durante una conversación/preparación nueva. Dos pruebas adicionales recorren tres conversaciones sucesivas bajo la misma autorización y rechazan el rearme antiguo. «Deja de escuchar», pausa explícita y transferencia a ChatGPT revocan esa espera. **36/36 JVM locales en 0,251 s**, Java 17, incluyendo canalización, buffers y política de activación; 39 Python y escáner correctos. Realtime pausa por pérdida de foco/dispositivo conservando sesión y exige reanudar expresamente; permite seleccionar teléfono sin reconectar por sí solo. Pendiente repetir CI del SHA que contiene estos cambios.

Auditoría: servicio Vosk/AudioRecord, política de activación, exclusión de micrófono, rutas y cancelación PCM ya existían. Groq cerraba cada activación al terminar el turno. Se incorpora continuidad hablada, contexto en RAM acotado, órdenes locales exactas y generaciones; «Para» invalida primero, «Termina» permite volver a espera autorizada y «Deja de escuchar» la detiene. Detector concurrente Vosk con confianza/AEC, pausa de servicio desde notificación, preferencias y launcher público de ChatGPT. Se conservan motores y DAT. Detalles y limitaciones en [CONVERSACION_CONTINUA.md](CONVERSACION_CONTINUA.md).

| Subfase | Avance estimado/peso | Evidencia y pendiente |
| --- | ---: | --- |
| Auditoría y contratos | 18/25 | Código, fuentes oficiales Android/Meta/OpenAI y Vosk consultados; falta contrato físico de rutas |
| Conversación y cancelación | 12/15 | Cinco turnos, límites y resultado nativo tardío; regresión HTTP de veinte turnos preparada, falta proveedor real |
| Interrupción por voz | 12/20 | PCM Piper/Vosk, callback WebRTC y cancelación concurrente pasan en ambas APIs; sin acústica física |
| Servicio e integración | 14/20 | Servicio/pantalla bloqueada/notificación y ruta del emulador comprobados; aceptación Nothing/Ray-Ban pendiente |
| Interfaz y documentación | 8/10 | Controles integrados, límites documentados; aceptación de uso pendiente |
| Validación y publicación | 7/10 | APK de 75ad07b: 172 JVM, 47/47 por API, Python/Lint/backend/seguridad; cierre sujeto a su propia CI |
| **Total orientativo** | **71/100** | **No es Release estable ni acredita latencia o gafas** |

Las 17 pruebas JVM nuevas pasan con Java 17 y Kotlin 2.0.21 del Gradle almacenado: 0,154 s, sin Android. La compilación real de CI usa Kotlin 2.2.21. Preparar Sherpa local descargó/verificó el AAR oficial, pero falta `patchelf`; SDK Android ausente. Se preparan cinco instrumentadas adicionales sin claves ni servicios reales. No se omiten ni relajan pruebas anteriores. La CI debe aprobar JVM, Lint, seguridad, API 35/36 y compilación antes de publicar APK del mismo SHA.

Pendientes: repetir CI completa y corregir fallos; reconocimiento hablado y eco durante TTS; rutas reales de entrada/salida Nothing/Ray-Ban; llamadas, retirada real de permisos y Bluetooth; detector KWS de consumo bajo (Vosk actual es ASR, sin consumo físico medido); aceptación del callback WebRTC con proveedor real; p50/p95 de detección/parada audible/relevo, CPU/PSS/batería y aceptación física. No hay cifras físicas conseguidas. Próxima tarea: CI del nuevo bloque y pruebas reales del detector; el lanzador abre ChatGPT, no garantiza iniciar Live.

## Escucha, motores neuronales y simplificación · intervención actual 28/10/2026

Base `e263ec42bda840c5cfb6427c945f562d57a086d9`; trabajo exclusivamente en `master`, sin ramas ni worktrees. Se preservan seis archivos locales previos, comprobados por SHA-256 y excluidos de los commits. La versión continúa **0.5.0-rc1**. Implementación publicada desde `4a87663` en `origin/master`; validación automática del código `b74fab9` completa en ambas APIs, conservando la candidata.

**Estimación global orientativa: 65 %; objetivo incompleto.** No se mezcla con porcentajes históricos posteriores en este documento ni representa una aprobación de inferencia, calidad o hardware.

| Subfase | Estimación/peso | Evidencia y pendiente |
| --- | ---: | --- |
| Auditoría, contratos y procedencia | 20/25 | Fuentes primarias, hashes de pesos, variantes lingüísticas; cerrar compatibilidad de licencias y comprobar ARM64 físico |
| Activación y captura | 7/15 | Hipótesis, sensibilidad, recuperación, pre-roll/VAD y pregunta concatenada; pruebas acústicas y regresiones Android pendientes |
| Interfaz y persistencia | 14/20 | 37 instrumentadas por API, incluida selección y reproducción de Kokoro desde Voces; faltan aceptación de uso real y recorridos con gafas |
| Motores locales | 14/20 | Kokoro y Davefx auténticos generan PCM en API 35/36; muestra Kokoro reproducida y cancelación comprobada; falta audición física y RVC con voz autorizada |
| Documentación y pruebas locales | 5/10 | 141 JVM (una omitida), Lint, Python, Node y escáner correctos; métricas reales de emulador y evaluación nativa Qwen |
| CI, APK y aceptación física | 5/10 | API 35 y 36: 37/37 por versión en b74fab9; APK candidata, sin aceptación Nothing/Ray-Ban |

La preferencia confirmada por el usuario es **acento de España**: Natural prioriza Piper Davefx `es_ES`, publicado expresamente con esa variante; Kokoro conserva Dora/Alex/Santa sin atribuirles acento peninsular. Android TTS está en Rápida y Qwen remoto en Avanzada. No se sustituye silenciosamente es-ES por una variante americana. Las selecciones antiguas se conservan/migran de categoría. Todo el detalle y las limitaciones están en [VOZ_LOCAL_0.5.0.md](VOZ_LOCAL_0.5.0.md).

Comprobaciones actuales: [Actions `b74fab9`](https://github.com/PbtServers/PbtMetaGPT/actions/runs/40691905261). [APK candidata del mismo SHA](https://github.com/PbtServers/PbtMetaGPT/actions/runs/40691905261/artifacts/12818606021), artefacto de 94.034.760 bytes. Los pesos se descargan voluntariamente dentro de la app y no van incluidos en esta APK.

- **141 JVM: 140 correctas y una omitida**, sin errores ni fallos. La omitida es el benchmark Qwen remoto que ya requería activación expresa `PBT_QWEN_LIVE=1`; no se consumen cuotas para obtener verde.
- **37/37 instrumentadas correctas en API 35 y 37/37 en API 36**, con Kokoro y Davefx auténticos. Incluyen instalación, integridad/corrupción, PCM, selección/persistencia, cancelación, coexistencia JNI/ORT y reproducción de una muestra desde la biblioteca. No acreditan acústica ni audio Bluetooth real.
- **Lint, compilación ARM64/x86_64, escáner de fuentes/APK, 35 Python, 19 Node y backend/contenedores correctos**. No se han usado claves personales ni servicios facturables.
- El subconjunto local de 19 JVM pasó en 8,237 s con Kotlin 2.0.21/Java 17; CI compila realmente la aplicación con Kotlin 2.2.21 y SDK 36. El análisis sintáctico local no sustituye esas comprobaciones.
- SDK Android sigue ausente en este equipo; las validaciones Android se ejecutan en Actions. Los problemas iniciales de red y acceso se conservan como historial debajo. Los pushes posteriores han funcionado; el repositorio permanece en master y se preservan los seis archivos previos.

Hay medidas puntuales TTFS/PSS de emulador más abajo. **Latencia de activación y conversación completa, primer audio audible, PSS máximo, consumo, temperatura y acento en el Nothing siguen sin medir**. La inferencia RVC sintética no certifica voz auténtica ni su calidad. La revisión GPL/eSpeak–términos DAT sigue pendiente antes de distribución estable.

Próxima tarea: instalar la APK del SHA validado en Nothing/Ray-Ban, escuchar Davefx/Kokoro, verificar por separado micrófono/salida Bluetooth y ejecutar el protocolo físico. Medir el relevo entre reconocedores, 20 activaciones a volumen normal, cancelación, foto con confirmación y RVC con modelos autorizados. No hay un plazo fiable para la aceptación física sin dispositivo conectado; no convertir en Release estable por superar CI.

**Correctivo adicional de límites RVC, posterior a la evidencia anterior:** el modo RVC local reutilizaba la agrupación remota de Qwen, que conserva el resto de una respuesta en un único bloque. Una respuesta larga podía superar los 1000 caracteres admitidos por Kokoro/Piper. Ahora RVC agrupa desde 80 caracteres pero mantiene las fronteras locales y el límite por palabras, conservando streaming y orden. Se corrige también la detección de punto tras un número: un punto seguido de espacio sí puede cerrar frase; un decimal dividido entre deltas sigue reteniéndose. La regresión usa una respuesta de 25 frases, exige entradas TTS acotadas, reproducción antes de completar el productor y conservación de cada palabra. **7/7 pruebas JVM de pipeline correctas localmente, 0,152 s**. Pendiente CI del nuevo SHA; no atribuir automáticamente las 37/37 anteriores al correctivo.

### Historial de acceso y correcciones de esta intervención

Los siguientes apartados describen resultados de SHAs anteriores; sus pendientes no sustituyen el estado actual indicado arriba.

**Actualización de acceso:** el push posterior sí ha funcionado: `4a87663` y `8facf9b` están en `origin/master`. El rechazo 403 del conector y el bloqueo REST de la consola no demuestran que Git siga bloqueado. Para poder localizar y revisar las ejecuciones push desde clientes que solo exponen runs de PR, Actions publica su enlace y resultado en el estado del commit, con permiso mínimo `statuses: write` y token efímero solo en CI. No cambia las puertas de JVM/Lint/emulador/APK ni solicita claves personales. Pendiente consultar ese resultado y corregir los fallos reales antes de cerrar la intervención.

**Primera validación Android ejecutada:** [Actions 40685008359](https://github.com/PbtServers/PbtMetaGPT/actions/runs/40685008359), SHA `85337ab`. El backend y contenedores pasan. La compilación detecta dos errores de contexto de corrutinas en Kotlin y una cabecera UCD no propagada al puente JNI de eSpeak. Se corrigen usando el contexto explícito del servicio, comprobando la cancelación del contexto activo y enlazando el objetivo CMake UCD con sus includes públicos. Las 35 pruebas Python vuelven a pasar. Pendiente repetir compilación, Lint y las instrumentadas API 35/36 con modelos auténticos; no hay aún métricas de inferencia ni APK aprobada.

**Resultado del correctivo `ac97650`:** [Actions 40685869816](https://github.com/PbtServers/PbtMetaGPT/actions/runs/40685869816) supera compilación Android, JVM, Lint, escáner de fuentes/APK, Python y backend/contenedores. En API 35 y 36 pasan **34/35 instrumentadas**, incluidas las de pesos Kokoro auténticos. La única fallida es la inicialización JUnit de Davefx: el último `Log.i` hacía inferir retorno `Int` en vez de `void`. Se fija `runBlocking<Unit>` para ejecutar realmente esa prueba; no se elimina ni se relaja. El script conserva también métricas numéricas sintéticas si otra prueba falla, manteniendo el código de salida original. `bash -n tools/probar_android.sh` correcto. No hay APK publicada de este SHA porque ambas matrices deben pasar completamente. Inferencia Davefx y métricas del teléfono siguen pendientes.

**Resultado `0c83af0`:** [Actions 40687679506](https://github.com/PbtServers/PbtMetaGPT/actions/runs/40687679506): 141 JVM, cero errores/fallos y una omitida; Lint, compilación y escáner correctos. **API 36: 35/35 correctas; API 35: 34/35**. Kokoro y Davefx auténticos pasan en ambas. La bienvenida de API 35 no encuentra el diálogo tras pulsar Guardar: se sustituye la inyección por coordenadas por la acción accesible del mismo botón, manteniendo todas las aserciones de guardado/rotación. Se añade un recorrido de selección Kokoro, reproducción PCM en AudioTrack y recreación sin claves, más error de muestra sin modelo. Esta revisión detecta y corrige que el error de una muestra alternativa solo mostraba aviso y podía terminar como cierre normal: ahora finaliza con estado de error y liberación. Ambas pruebas nuevas quedan pendientes de CI.

Medidas puntuales **del proceso del emulador x86_64**, textos sintéticos y dos hilos; no máximos ni medidas del Nothing:

| Motor/API | Frío / primera PCM | Fragmento caliente | PSS tras generar |
| --- | ---: | ---: | ---: |
| Kokoro / 35 | 3300 ms | 1455 ms | 558215 KiB |
| Kokoro / 36 | 3754 ms | 1271 ms | 560397 KiB |
| Davefx / 35 | 1177 ms | 145 ms | 344410 KiB |
| Davefx / 36 | 1626 ms | 186 ms | 327068 KiB |

Los fragmentos fríos y calientes usan textos distintos: no son un benchmark comparativo controlado. El PSS incluye app, bibliotecas y memoria retenida por pruebas previas; no representa memoria exclusiva del modelo. Ninguna cifra acredita primer audio audible, acento ni calor/batería del teléfono. También se documenta el candidato JNI ARM64 Qwen3-TTS/ GGML independiente, CPU y opciones GPU desactivadas por defecto, sin integrarlo ni trasladar sus benchmarks CUDA a Android.

**Recorrido completo de voces `05d3adb`:** [Actions 40689848962](https://github.com/PbtServers/PbtMetaGPT/actions/runs/40689848962) pasa 37/37 instrumentadas en API 35 y 36/37 en API 36; la única fallida vuelve a una selección Realtime del Setup, no a inferencia ni reproducción. Las dos pruebas nuevas pasan en ambas APIs: selección Kokoro → muestra → reproducción AudioTrack → restauración y falta de modelo → error con liberación. Se aíslan mensajes/sesiones entre casos y se activa el selector Avanzada por su acción accesible, exigiendo inmediatamente su estado seleccionado. El botón Guardar de Rápida pasa delante del catálogo asíncrono para conservar su posición mientras llegan voces. Se mantienen todas las pruebas de pulsación, persistencia y recreación; pendiente validar este correctivo completo. La única JVM omitida es el benchmark Qwen remoto optativo, que ya exigía `PBT_QWEN_LIVE=1`; no se ha desactivado ninguna prueba para obtener verde.

## Seguimiento de RVC local y límites opcionales · 28/10/2026

El trabajo convergente de ONNX ya incluye HuBERT/ContentVec, RMVPE y síntesis RVC en CPU con un límite de entrada de ocho segundos; **no se confunde esto con audio inteligible validado en hardware**. Se conservaron los contratos preliminares de presupuesto de modelo, índice y PCM en `RvcLocalBudget` y tres pruebas JVM. Son presupuestos exploratorios, no se han conectado a la ruta de inferencia de ocho segundos y no deben presentarse como límites efectivos de RVC. El índice FAISS sigue sin ejecutarse. La prioridad ahora es la convivencia nativa con Sherpa y las pruebas en Android 15/16, sin declarar Release estable.

**Corrección de la incompatibilidad JNI identificada en CI 40623567817:** el AAR de Sherpa 2.0.1 necesita `OrtGetApiBase` de su propia biblioteca, símbolo que no exportó el `libonnxruntime.so` empaquetado para RVC 1.30.0 en los emuladores. Se reemplaza la anterior exclusión de ORT de Sherpa por separación explícita de `SONAME` y `DT_NEEDED` mediante `patchelf` sobre binarios de un AAR oficial verificado por SHA-256. El empaquetado contiene entonces `libpbt_sherpa_ort.so` para Sherpa y `libonnxruntime.so` para RVC; deben aprobarse las instrumentadas de ambas APIs antes de publicar APK. El procedimiento no acredita rendimiento, seguridad de los modelos ni audio físico y requiere una CI nueva del commit final.

**Resultado de [CI 40669899079](https://github.com/PbtServers/PbtMetaGPT/actions/runs/40669899079) sobre `5b0963b`:** compilación, backend y 123 pruebas JVM (cero fallos) correctos; se completaron las pruebas de carga nativa RVC + Sherpa y desapareció el `UnsatisfiedLinkError` por `OrtGetApiBase` en API35 y API36. Persisten fallos de UI en emulador: confirmación Groq con composición de prueba sin contenedor (API35/36), y `performScrollTo()` ajeno al objetivo de conservación de voz antes de recomponer (API36). Se corrigen manteniendo comprobación de consentimiento negativo/positivo y selección de voz real, sin omitir pruebas; requiere nueva CI, por lo que todavía no hay APK de este correctivo validada.

## Candidata v0.5.0-rc1 · 28/10/2026 · en desarrollo

Esta intervención parte del último `origin/master` `5b9855f`, en una rama de trabajo aislada. **No existe todavía una CI ni APK verificadas de v0.5.0-rc1** hasta que termine su compilación; las ejecuciones 0.4.1-dev anteriores no avalan los cambios nuevos.

- Audio de las gafas: propuesta de precalentamiento silencioso para evitar la pérdida de la primera palabra al abrir la ruta Bluetooth. Requiere reproducción y medición física en 00R6.
- Activación: señal audible de confirmación y reconocimiento libre del motor Android estrictamente offline, con alternativa Vosk, sin registrar ni subir audio.
- Voz local natural: compatibilidad con motores neuronales de Android TTS instalables voluntariamente (HayaiTTS/Kokoly), prioridad a variantes `es-ES`. Su calidad y latencia en Nothing Phone 3a Lite quedan por medir.
- Cámara y Groq: cliente de visión multimodal con consentimiento de envío por foto; el modelo público documentado es `qwen/qwen3.8-27b`. Falta llamada con clave real.
- **RVC importable:** permite ZIP, `.pth` y `.index` en una biblioteca separada, con límites, rechazo de rutas peligrosas, almacenamiento privado y limpieza. Se almacenan como datos y no se ejecutan. **La inferencia RVC sigue desactivada** hasta encontrar un motor/proveedor autorizado y compatible sin self-hosting.
- Documentación: README actualizado al estado de candidata, icono del launcher incorporado y lista de aceptación en [RELEASE_0.5.0.md](RELEASE_0.5.0.md).

La meta es una candidata funcional que se pueda someter a pruebas; **no se declara v0.5.0 estable ni 100 % por compilar o importar un modelo RVC**. Toda la aceptación del audio y la cámara precisa hardware y proveedor reales.

## Optimización de voz, interfaz simplificada e icono profesional · 28/10/2026

**75 % de esta intervención; entrega experimental**, independiente del 70 % histórico. Código `cc489d5f33872bc0c5014b43da13307d0991cbcf`, [CI completa correcta](https://github.com/PbtServers/PbtMetaGPT/actions/runs/40606942378) y [APK experimental](https://github.com/PbtServers/PbtMetaGPT/actions/runs/40606942378/artifacts/12781607132). Inicio medido 23:07 UTC del 27/10; CI/APK terminadas a las 00:20 UTC del 28/10: **73 minutos hasta la APK**, incluida la corrección de regresiones. La documentación posterior se comprueba de nuevo en CI con su propio SHA.

| Subfase | Avance/peso | Evidencia y pendiente |
| --- | ---: | --- |
| Auditoría y latencia real | 11/25 | Métricas numéricas privadas; benchmark Qwen falla HTTPS, sin PCM ni tiempos válidos. Groq real/primera palabra pendientes |
| Canalización de voz | 12/15 | Streaming SSE, frases, prefetched audio, cancelación y fallo sin perder texto; JVM/compilación correctos. Latencia acústica pendiente |
| Inicio, chat y biblioteca | 18/20 | Cuatro destinos, mensajes progresivos, modos, setup breve y pruebas Compose correctas en ambas APIs; uso diario físico pendiente |
| Tres propuestas e icono | 18/20 | B elegido tras renderizar A/B/C; 24/36/48/72 dp, mono, máscaras, cinco densidades y resolución Android correctos; launcher físico pendiente |
| Compatibilidad y preferencias | 8/10 | 28/28 instrumentadas por API, borradores/perfiles/Keystore y archivos preservados. Nothing/00R6, Bluetooth y batería pendientes |
| Pruebas y publicación | 8/10 | 108 JVM (0 fallos, 1 remota optativa omitida), 32 Python, 19 Node, Lint, escáner y APK gated correctos. Proveedores/E2E reales pendientes |
| **Total** | **75/100** | **No es una Release estable ni una conversación real de extremo a extremo acreditada** |

La APK incorpora voz Rápida predeterminada con Android TTS instalado, Qwen Natural experimental y motores anteriores en Personalizada. OpenAI API permanece separado y explícito. No se afirma que un proveedor sea más rápido. [Detalle y metodología](OPTIMIZACION_VOZ_UI.md), [tres propuestas](assets/iconos/comparativa.png) y [máscaras pequeñas](assets/iconos/mascaras.png).

El usuario confirmó que el teléfono no está disponible. La aceptación física y el 25 % restante no tienen una estimación fiable hasta disponer de cuenta Groq Free/configuración local y Nothing/00R6. Qwen público falló antes de completar la inferencia; se conserva como experimental, sin disponibilidad garantizada. RVC sigue no disponible sin backend apto probado. README.md y los seis archivos anteriores permanecen íntegros por SHA256 y fuera de los commits.

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
