# Validación de v0.2.0

Registro de trabajo del 26 de octubre de 2026. **Las pruebas automáticas y la revisión de muestras no sustituyen las pruebas con gafas reales.**

## Comprobaciones automáticas

| Comprobación | Estado | Alcance |
| --- | --- | --- |
| Backend `node --test backend/server.test.mjs` | 11 pruebas superadas | Autenticación, método/ruta, contrato, caducidad, límites, errores y red simulada |
| Gradle Wrapper con Java 17 | Descarga y configuración inicial realizadas | AGP y Kotlin resueltos; falta SDK Android local |
| `testDebugUnitTest lintDebug assembleDebug` | Pendiente | La primera ejecución se detuvo al no encontrar Android SDK |
| Pruebas JVM del protocolo Android | Añadidas; ejecución pendiente | HTTPS, rechazo de claves permanentes y caducadas, formato de visión y cancelación |
| GitHub Actions y artifact APK | Pendiente de push y ejecución | El conector de GitHub devolvió 403 al intentar escribir; se probará Git directamente |
| OpenAI con credenciales reales | No realizado | No se han usado claves reales ni consumido cuota |

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

No declarar terminada v0.2.0 hasta obtener una compilación y pruebas Android correctas, confirmar el workflow del commit publicado y descargar su artifact. La compatibilidad con un modelo concreto solo puede afirmarse después de completar y registrar las pruebas físicas correspondientes.
