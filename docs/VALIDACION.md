# Validación de v0.2.0

Registro de trabajo del 26 de octubre de 2026. **Las pruebas automáticas y la revisión de muestras no sustituyen las pruebas con gafas reales.**

## Comprobaciones automáticas

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

No declarar terminada v0.2.0 hasta obtener una compilación y pruebas Android correctas, confirmar el workflow del commit publicado y descargar su artifact. La compatibilidad con un modelo concreto solo puede afirmarse después de completar y registrar las pruebas físicas correspondientes.
