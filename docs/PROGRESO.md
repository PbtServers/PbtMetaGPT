# Seguimiento de PbtMetaGPT

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
