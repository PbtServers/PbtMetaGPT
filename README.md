# PbtMetaGPT · v0.3.0

Aplicación Android en Kotlin para conversar con OpenAI Realtime y explorar la cámara de las Ray-Ban Meta mediante el SDK oficial Meta Wearables Device Access Toolkit (DAT).

**Compilación debug, Android Lint y pruebas automáticas superadas en GitHub Actions. Las integraciones están implementadas en código, pero no se han probado de extremo a extremo con OpenAI real ni con gafas reales. No es una versión certificada como compatible con ningún modelo de Ray-Ban Meta.** El resultado exacto de cada comprobación se registra en [VALIDACION.md](docs/VALIDACION.md).

Esta versión se centra en preparar pruebas reales: incorpora despliegue Docker Compose para Ubuntu Server, diagnósticos por etapa y pruebas locales de audio. Consulta la [auditoría de la base v0.2.0](docs/AUDITORIA-v0.3.0.md), la [guía de despliegue](deploy/README.md) y el [protocolo de pruebas físicas](docs/PRUEBAS_REALES.md). No se ha desplegado un servidor del usuario ni se ha usado una clave real durante el desarrollo.

## Funciones implementadas en código

- Interfaz Material 3 en español de España, con estados de registro, conexión, micrófono y cámara.
- Registro y retirada del registro en Meta AI, descubrimiento DAT, metadatos de dispositivos y sesiones con conexión/desconexión explícitas.
- Conversación de voz por WebRTC con credenciales temporales; inicio, cancelación de conexión, pausa, reanudación, interrupción y finalización.
- Contexto conservado en la conexión Realtime durante la sesión, incluida la pausa. Finalizar, salir de la aplicación o perder la conexión cierra la sesión; no hay historial persistente ni reconexión automática.
- Selección de ruta de comunicación Android: teléfono o preferencia Bluetooth. La ruta disponible no demuestra por sí sola que el micrófono de las gafas funcione.
- Captura puntual de foto desde las gafas, vista previa local y segunda confirmación antes de preguntar «¿Qué estoy viendo?» a OpenAI.
- Backend personal autenticado mediante tokens opacos, con hashes de credenciales, límites de emisión y errores filtrados.
- Pantalla de diagnóstico en español: permisos, Bluetooth, DAT, cámara, HTTPS, credencial temporal, SDP, ICE, canal de datos, sesión y eventos nativos de audio. Cada resultado muestra su hora y distingue confirmación manual de observación software.
- Captura local de tres segundos para medir nivel/ruta y tono de un segundo con confirmación humana. No se guardan ni envían muestras.
- Comprobación WebRTC sin micrófono desde Android y prueba CLI de sesión/respuesta por WebSocket, ambas con credencial temporal real cuando el usuario configura el backend.
- Docker Compose con Caddy HTTPS, backend no publicado, secretos montados, proceso no root y prueba de contenedores en CI.
- Flujo de GitHub Actions para pruebas, Android Lint, compilación y publicación de APK debug únicamente tras superar la validación.

No se incluyen simulaciones de conexión ni dispositivos ficticios en la aplicación.

## Entorno Android

| Componente | Versión fijada |
| --- | --- |
| Identificador | `com.pbtservers.pbtmetagpt` |
| Versión de la aplicación | `0.3.0` / código `3` |
| Java | 17 |
| Gradle Wrapper | 8.14.1, descarga verificada por SHA-256 |
| Android Gradle Plugin | 8.11.1 |
| Kotlin y complemento Compose | 2.2.21 |
| Android | mínimo 12 / API 31; compilación y destino API 36 |
| Meta DAT | `com.meta.wearable:mwdat-core:1.0.1` y `mwdat-camera:1.0.1` |
| Transporte WebRTC Android | `io.github.webrtc-sdk:android:150.7871.01` |
| Backend | Node.js 22 o posterior, sin dependencias externas |

Los artefactos DAT están publicados en Maven Central: **no hace falta un token de GitHub Packages**. La versión y las APIs se han contrastado con la [muestra oficial CameraAccess](https://github.com/facebook/meta-wearables-dat-android/tree/main/samples/CameraAccess) y el [README oficial](https://github.com/facebook/meta-wearables-dat-android). La biblioteca WebRTC es una [distribución comunitaria de WebRTC para Android](https://github.com/webrtc-sdk/android), no un SDK Android de OpenAI.

`mwdat-speech` y `mwdat-inputs` existen en el catálogo oficial, pero no se añaden sin uso: esta versión utiliza audio de comunicación Android/WebRTC y cámara DAT. Su disponibilidad en Maven no acredita soporte de esas capacidades en unas gafas concretas.

### Compilar

1. Instala JDK 17, Android SDK Platform 36 y Build Tools 36.0.0.
2. Crea `local.properties` con tu ruta al SDK. No se sube al repositorio:

   ```properties
   sdk.dir=C:/Android/Sdk
   mwdat_application_id=0
   mwdat_client_token=
   ```

3. Para desarrollo, activa el modo de desarrollador en Meta AI. El identificador `0` solo corresponde al modo de desarrollo; para una integración registrada, usa los valores de Wearables Developer Center. Configura el esquema de retorno `pbtmetagpt` conforme al registro de la aplicación. No introduzcas secretos privados en estos valores.
4. Ejecuta con `JAVA_HOME` apuntando a Java 17:

   ```powershell
   .\gradlew.bat --no-daemon testDebugUnitTest lintDebug assembleDebug
   ```

   En Linux/macOS: `./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug`.

La APK, si la compilación termina correctamente, estará en `app/build/outputs/apk/debug/app-debug.apk`. No contiene ninguna clave API privada. Android Studio también puede abrir el proyecto usando JDK 17.

## Backend y primera conversación

Sigue [backend/README.md](backend/README.md) para configurar la clave de OpenAI **solo en el servidor**, generar un token personal y publicar el servicio mediante HTTPS. La aplicación no conecta a servidores HTTP ni sigue redirecciones de autenticación.

Para Ubuntu Server usa [deploy/README.md](deploy/README.md). La clave y los hashes se montan desde archivos bajo `/run/secrets`; las variables de entorno configuran dominio, correo, modelo y ubicación de esos archivos. `GET /healthz` no consume cuota ni prueba OpenAI. La prueba real requiere una URL, un token personal y acción explícita del usuario; los tests de CI usan exclusivamente valores ficticios.

En la aplicación:

1. Introduce la dirección HTTPS del backend y el token personal de acceso. No introduzcas una clave `sk-…` de OpenAI.
2. Autoriza el micrófono y pulsa **Iniciar conversación**. El token personal permanece solo en memoria; la dirección del backend se guarda localmente.
3. Usa **Pausar/Reanudar** para mantener la sesión con el micrófono apagado o encendido. **Interrumpir** cancela la respuesta actual. **Finalizar** libera audio, red y contexto local.
4. Para probar las gafas, permite Bluetooth, registra la aplicación en Meta AI y pulsa **Conectar gafas**. La aplicación solo muestra conexión confirmada tras recibir `STARTED` de DAT.
5. Para visión, autoriza **Capturar foto con las gafas**. Si Meta AI solicita permiso, vuelve a conectar y pulsar capturar al regresar. Revisa la vista previa y confirma **Enviar y preguntar** durante una conversación activa.

La cámara se cierra tras la captura o al cancelar. No se envía el flujo de vídeo. La foto se reduce antes de enviarla y la lectura de detalles pequeños puede perder precisión. Si sales de la aplicación se detienen la conversación, la captura y la sesión de gafas; volver no reactiva automáticamente los sensores.

## Limitaciones y privacidad

- DAT sigue en vista previa para desarrolladores. El acceso y la distribución dependen de Meta AI, cuenta, región, modelo, firmware, modo de desarrollo y autorizaciones de Meta. Comprueba los requisitos oficiales vigentes.
- No se sustituye «Hey Meta», no se interceptan comandos del asistente de Meta y no hay escucha permanente.
- La integración de voz utiliza las rutas Bluetooth de Android, no promete acceso directo a los micrófonos mediante `mwdat-speech`. Entrada y salida deben comprobarse por separado con hardware real. Si no hay ruta Bluetooth al iniciar, la aplicación avisa de que usará el teléfono.
- La cámara puede fallar o pausarse al quitarse las gafas, cerrar las patillas, cambiar de aplicación, revocar permisos o perder conectividad. Un dispositivo descubierto no equivale a una capacidad verificada.
- El contexto remoto sigue las condiciones de OpenAI; cerrar la app borra las referencias locales, no constituye una solicitud de borrado al proveedor. Audio e imágenes autorizadas se transmiten a OpenAI.
- No se guardan fotos en la galería, audio ni conversaciones en archivos. Se deshabilitan las copias de seguridad de Android y la analítica opcional de DAT. Se protege la pantalla contra capturas del sistema.
- ChatGPT Plus y la API de OpenAI se facturan por separado. Limitar emisiones de credenciales no garantiza un límite absoluto de gasto: configura presupuestos y supervisión en el proyecto de OpenAI.

## Pruebas y CI

```powershell
node --test backend/*.test.mjs
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
```

El flujo [android.yml](.github/workflows/android.yml) se ejecuta en cada push a `master`, en pull requests y manualmente. Publica informes y, cuando pasan las comprobaciones Android, el artifact `PbtMetaGPT-v0.3.0-debug` durante 14 días. Incluye un trabajo independiente que valida Caddy y arranca backend/proxy con Docker Compose, comprobando salud y rechazo de acceso anónimo. La prueba del proxy en CI usa HTTP interno, sin emitir certificados públicos. Las pruebas del backend usan respuestas controladas de OpenAI, sin consumir cuota; no demuestran conectividad real con el proveedor ni con las gafas. Comprueba el resultado de ambos trabajos antes de dar por válida una entrega.

Consulta la [matriz de validación](docs/VALIDACION.md) y la [arquitectura](docs/ARQUITECTURA.md).

## Fuentes oficiales

- [Meta DAT Android y muestras](https://github.com/facebook/meta-wearables-dat-android).
- [Centro de desarrollo de Meta Wearables](https://wearables.developer.meta.com/docs/develop/dat/).
- [OpenAI Docs: conexión Realtime con WebRTC](https://developers.openai.com/api/docs/guides/realtime-webrtc).
- [OpenAI Docs: credenciales temporales](https://developers.openai.com/api/reference/resources/realtime/subresources/client_secrets/methods/create).
- [OpenAI Docs: conversaciones, audio e imágenes](https://developers.openai.com/api/docs/guides/realtime-conversations).
