# Validación de v0.3.0

Registro de trabajo del 26 de octubre de 2026. **Las pruebas automáticas y la revisión de muestras no sustituyen las pruebas con gafas reales.**

## Estado de v0.3.0

Base de desarrollo: `9898ef7`. Se han revisado sus limitaciones y creado [AUDITORIA-v0.3.0.md](AUDITORIA-v0.3.0.md). Las instrucciones de intervención manual están en [deploy/README.md](../deploy/README.md) y [PRUEBAS_REALES.md](PRUEBAS_REALES.md).

| Comprobación | Resultado de v0.3.0 | Qué demuestra |
| --- | --- | --- |
| Node: backend y protocolo de comprobación real | 19 pruebas superadas localmente y en CI | Autenticación, límites, secretos, errores, salud y protocolo con proveedor sustituido |
| Android: pruebas JVM, Lint y APK debug | Superado en CI, commit `9e0eaea` | Ocho pruebas JVM y compilación/análisis; no ejecuta sensores |
| Docker Compose / Caddy | Superado en CI, commit `98baa48` | Caddy válido, contenedores saludables, tráfico por proxy, rechazo 401 y ningún puerto publicado del backend; no se ha emitido un certificado público |
| Comprobación CLI con OpenAI real | No realizada: faltan backend HTTPS y token personal configurados | El comando se ha ejecutado sin credenciales y termina como no realizado |
| WebRTC real en Android | Pendiente de intervención manual | Pantalla preparada; recibir session.created es distinto de verificar voz |
| Micrófono/altavoz del teléfono | Pendiente de intervención manual | Pruebas locales de nivel/ruta y tono implementadas, sin grabaciones persistentes |
| Ray-Ban Meta: DAT, audio y cámara | Sin pruebas físicas | No se declara funcional ni compatible ningún modelo |
| Ubuntu Server público y HTTPS ACME | Preparado, no desplegado | No se dispone de servidor/dominio del usuario ni de claves en el entorno |

Evidencia de contenedores: [Actions 40374529684](https://github.com/PbtServers/PbtMetaGPT/actions/runs/40374529684), trabajo **Backend Docker y proxy**. Se corrigieron la sintaxis de `tmpfs` y una aserción que dependía de la salida del comando `compose port`; la comprobación definitiva consulta los puertos realmente publicados del contenedor con Docker Inspect, sin mostrar su entorno.

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
