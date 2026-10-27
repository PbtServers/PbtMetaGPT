# Validación de v0.4.0

Base: `f067b35`, rama `master`. Primera ejecución completa correcta de v0.4.0: commit `49c8f4c`, [Actions 40483523153](https://github.com/PbtServers/PbtMetaGPT/actions/runs/40483523153). Los cambios posteriores deben consultarse en la ejecución de su propio commit; un artifact anterior no los valida.

| Comprobación v0.4.0 | Estado y límite |
| --- | --- |
| Backend Node | 19 pruebas superadas localmente; sin llamadas reales |
| JVM: perfiles, frases, AES-GCM, contratos BYOK, protocolos y cancelación HTTP | 22 pruebas superadas en CI, `49c8f4c`; sin proveedor de pago ni sensores |
| Android Lint y APK debug | Superados en CI, `49c8f4c`; la compilación local completa se intentó y terminó por ausencia de SDK Android |
| Keystore y persistencia en Android | 4 pruebas instrumentadas superadas en emulador Android 15/API 35, `49c8f4c`; incluye sustitución, corrupción, eliminación, perfiles, bienvenida/creación de Jarvis y denegación de micrófono |
| Patrones de secretos en fuentes y APK | Superados en CI, `49c8f4c`; 3 pruebas Python del detector superadas también localmente |
| BYOK → credencial temporal → WebRTC → voz | Implementado, sin clave real disponible; prueba manual pendiente |
| Muestra de voces oficiales y velocidad | Contrato revisado; escucha real pendiente |
| «Atento [nombre]» | Comparación textual probada automáticamente en CI; reconocimiento acústico local pendiente de teléfono/modelo instalado |
| Micrófono y altavoz del teléfono | Sin pruebas físicas; no extrapolar desde un emulador |
| Ray-Ban Meta: DAT, audio y cámara | Sin hardware físico; ningún modelo certificado |
| Backend opcional y despliegue público | Contenedores y proxy superados en CI, `49c8f4c`; HTTPS público y conexión real pendientes |

Los tests de frases no miden precisión acústica ni tasas reales de falsos positivos/negativos. Los tests de AES JVM no acreditan Keystore: esa comprobación corresponde al trabajo de emulador. Las claves de las pruebas son ficticias. El escáner no garantiza detectar todos los formatos de secretos. La comprobación real de credenciales en la aplicación es una acción del usuario y no se ejecuta con claves privadas en CI.

La primera ejecución (`b768b4a`) compiló correctamente, pero el escáner confundió las etiquetas PEM literales del parser mbedTLS incluido por Meta con claves privadas. Se corrigió para exigir un bloque con carga, se añadieron pruebas del detector y se mantuvo el análisis de todas las bibliotecas nativas. No se excluyó la dependencia ni se desactivó el control. La siguiente ejecución superó los tres trabajos y publicó el [artifact de `49c8f4c`](https://github.com/PbtServers/PbtMetaGPT/actions/runs/40483523153/artifacts/12724341536).

El emulador no ha realizado escucha, síntesis de pago, captura física ni reconocimiento acústico. Las cuatro pruebas terminaron con 0 fallos y 0 omitidas. La aplicación de un perfil se comprueba contra los campos confirmados por OpenAI antes de abrir el micrófono; su prueba JVM usa JSON controlado y no acredita una sesión real.

Consulta [PRUEBAS_REALES.md](PRUEBAS_REALES.md) para registrar resultados manuales y [README](../README.md) para límites de rutas de audio, escucha y BYOK.

## Historial de v0.3.0

Registro de trabajo del 26 de octubre de 2026. **Las pruebas automáticas y la revisión de muestras no sustituyen las pruebas con gafas reales.**

## Estado de v0.3.0

Base de desarrollo: `9898ef7`. Se han revisado sus limitaciones y creado [AUDITORIA-v0.3.0.md](AUDITORIA-v0.3.0.md). Las instrucciones de intervención manual están en [deploy/README.md](../deploy/README.md) y [PRUEBAS_REALES.md](PRUEBAS_REALES.md).

| Comprobación | Resultado de v0.3.0 | Qué demuestra |
| --- | --- | --- |
| Node: backend y protocolo de comprobación real | 19 pruebas superadas localmente y en CI | Autenticación, límites, secretos, errores, salud y protocolo con proveedor sustituido |
| Android: pruebas JVM, Lint y APK debug | Diez pruebas JVM, compilación y Lint superados en CI, commit `162553e` | Incluye respuestas HTTP lentas y cancelación durante la lectura; no ejecuta sensores |
| Docker Compose / Caddy | Superado en CI, commit `98baa48` | Caddy válido, contenedores saludables, tráfico por proxy, rechazo 401 y ningún puerto publicado del backend; no se ha emitido un certificado público |
| Comprobación CLI con OpenAI real | No realizada: faltan backend HTTPS y token personal configurados | El comando se ha ejecutado sin credenciales y termina como no realizado |
| WebRTC real en Android | Pendiente de intervención manual | Pantalla preparada; recibir session.created es distinto de verificar voz |
| Micrófono/altavoz del teléfono | Pendiente de intervención manual | Pruebas locales de nivel/ruta y tono implementadas, sin grabaciones persistentes |
| Ray-Ban Meta: DAT, audio y cámara | Sin pruebas físicas | No se declara funcional ni compatible ningún modelo |
| Ubuntu Server público y HTTPS ACME | Preparado, no desplegado | No se dispone de servidor/dominio del usuario ni de claves en el entorno |

Evidencia de contenedores: [Actions 40374529684](https://github.com/PbtServers/PbtMetaGPT/actions/runs/40374529684), trabajo **Backend Docker y proxy**. Se corrigieron la sintaxis de `tmpfs` y una aserción que dependía de la salida del comando `compose port`; la comprobación definitiva consulta los puertos realmente publicados del contenedor con Docker Inspect, sin mostrar su entorno.

Ejecución completa de v0.3.0 con ambos trabajos correctos: [Actions 40375124794](https://github.com/PbtServers/PbtMetaGPT/actions/runs/40375124794), commit `162553e`. Incluye APK debug e informes. Consulta la ejecución correspondiente al commit final para descargar su propio artifact; un artifact anterior no valida cambios posteriores.

Se intentó `adb devices -l` en el entorno local: el daemon ADB no pudo arrancar. No fue posible enumerar ni inspeccionar teléfonos/gafas; ese intento no cuenta como prueba de hardware ni demuestra que no haya dispositivos conectados.

Las pruebas automáticas de la comprobación real sustituyen red/WebSocket para verificar el protocolo; no son llamadas reales. La prueba de Docker usa secretos ficticios y HTTP en loopback del runner. No se ha utilizado una clave privada ni consumido cuota de OpenAI durante el desarrollo.

La pantalla de diagnóstico empieza con todos los sensores **sin comprobar**, registra códigos reales al recibirlos y conserva hora/último intento. Un RMS positivo o un capturador iniciado no acredita inteligibilidad; un tono enviado requiere confirmación humana. El informe no exporta secretos, transcripciones, fotos, muestras, nombres ni direcciones de dispositivos.

## Historial de v0.2.0: comprobaciones automáticas

| Comprobación | Estado | Alcance |
| --- | --- | --- |
| Backend `node --test backend/server.test.mjs` | 13 pruebas superadas, localmente y en CI | Autenticación, método/ruta, contrato, caducidad, límites por identidad/globales, concurrencia, errores y red simulada |
| Gradle Wrapper con Java 17 | Correcto | Distribución y dependencias reales resueltas; las dependencias DAT no requieren GitHub Packages |
| `testDebugUnitTest lintDebug assembleDebug` | Superado en GitHub Actions | SDK 36; la compilación local completa sigue necesitando instalar el SDK Android |
| Pruebas JVM del protocolo Android | Superadas en CI | Cuatro pruebas: HTTPS, rechazo de claves permanentes y caducadas, formato de visión y cancelación |
| GitHub Actions y artifact APK | Publicados y comprobados | Push realizado con Git; APK debug e informes descargables |
| OpenAI con credenciales reales | No realizado | No se han usado claves reales ni consumido cuota |

Primera ejecución completa correcta: commit `8cb9824affd482ba4b258fbe46f0fc98c8e1bb72`, [Actions 40370770200](https://github.com/PbtServers/PbtMetaGPT/actions/runs/40370770200). [APK debug de esa ejecución](https://github.com/PbtServers/PbtMetaGPT/actions/runs/40370770200/artifacts/12671509247), con retención de 14 días. Los cambios posteriores deben comprobarse en su propia ejecución de Actions; los artifacts pertenecen al commit indicado en cada ejecución.

Se corrigieron durante CI la instalación del paquete Android `tools` retirado y el uso de `DeviceIdentifier` como clave tipada en los metadatos DAT. No se han suprimido errores de compilación ni de Lint para obtener el resultado.

## Pruebas manuales pendientes

Registra en cada ejecución: APK/commit, versión Android, versión Meta AI, modelo de gafas, firmware, modo de desarrollo o canal, resultado y errores. No añadas tokens, imágenes personales ni conversaciones al repositorio.

| Prueba | Emulador | Mock Device Kit | Ray-Ban Meta reales |
| --- | --- | --- | --- |
| Instalación, interfaz en español, tamaño de fuente, rotación y navegación | Pendiente | No aplica | Pendiente |
| Permisos Bluetooth/micrófono denegados, concedidos y revocados | Pendiente | Pendiente | Pendiente |
| Registro, cancelación del registro y retirada en Meta AI | No acredita compatibilidad | Pendiente | Pendiente |
| Descubrimiento y estado de firmware | No acredita compatibilidad | Pendiente | Pendiente |
| Conexión, pausa DAT, patillas cerradas, pérdida Bluetooth y desconexión | No acredita compatibilidad | Pendiente | Pendiente |
| Micrófono y altavoz del teléfono con OpenAI real | Pendiente | No aplica | Pendiente en teléfono |
| Micrófono de las gafas y respuesta por sus altavoces, por separado | No verificable | No verificable físicamente | Pendiente |
| Inicio, cancelación durante HTTP/SDP y error de autenticación/cuota | Pendiente | No aplica | Pendiente en teléfono |
| Conversación con varios turnos y contexto tras pausa | Pendiente | No aplica | Pendiente |
| Interrumpir respuesta y hablar de nuevo sin audio anterior | Pendiente | No aplica | Pendiente |
| Captura autorizada, vista previa, rechazo y envío confirmado | No acredita cámara real | Pendiente | Pendiente |
| Pregunta visual con respuesta hablada en la misma sesión | No acredita cámara real | Pendiente | Pendiente |
| Cámara apagada y micrófono liberado al salir o cancelar | Pendiente | Pendiente | Pendiente |
| Backend HTTPS, certificado inválido y fallo de red durante voz | Pendiente | No aplica | Pendiente en teléfono |

No se ha ejecutado Mock Device Kit ni se incluye un modo que simule gafas en la aplicación. No se han verificado capacidades de `mwdat-speech`, comandos «Hey Meta», controles táctiles ni funcionamiento en segundo plano. Estas características no se anuncian como disponibles.

## Criterio de entrega

La entrega técnica de v0.3.0 requiere ambos trabajos de CI correctos, APK del commit publicado e instrucciones reproducibles. La conexión real con OpenAI solo se marca como probada después de ejecutar la comprobación contra el proveedor; la conversación por voz y la compatibilidad con un modelo concreto solo pueden afirmarse después de registrar sus pruebas físicas. Preparar herramientas de diagnóstico no equivale a completar esas comprobaciones.
