# Protocolo de pruebas reales · v0.4.0

## Primera aceptación sin backend ni gafas

No marques estos pasos como superados por haber compilado o ejecutado pruebas automáticas. Registra fecha, commit/APK, teléfono, versión Android, motor de reconocimiento e idioma instalado, resultado y código filtrado. No adjuntes claves, audio, conversaciones o imágenes personales.

1. Instalar sin datos previos. Completar bienvenida y aviso BYOK. Crear una clave de proyecto limitada e introducirla en OpenAI; verificar que nunca se muestra en claro ni aparece en informes.
2. Comprobar credenciales sin audio. Distinguir 401 (clave), 429 (cuota/límite), error de red, credencial emitida y WebRTC confirmado. Probar modo avión y cancelación de conexión; no deben aparecer cuerpos originales del proveedor.
3. Crear Personalizado llamado Jarvis, guardar personalidad, trato, humor, idioma y voz. Cerrar/reabrir: el perfil y la clave cifrada deben persistir, sin sensores encendidos.
4. Sin URL de backend y sin Bluetooth, iniciar conversación. Confirmar escucha inteligible del micrófono del móvil y respuesta por su altavoz. Hacer segunda pregunta sobre lo anterior; pausar, reanudar, interrumpir y finalizar. Repetir con permiso denegado/revocado.
5. Reproducir muestra de voz guardada: no debe activarse el micrófono. Probar cancelación durante conexión y reproducción. Cambiar voz/velocidad, guardar y reconectar; comprobar coherencia en toda la sesión.
6. Exportar perfiles e inspeccionar solo campos permitidos. Importar, duplicar y renombrar a Alex: debe cambiar la frase. Rechazar JSON con campos de clave/token, versión desconocida, exceso de tamaño o valores no soportados.
7. Sustituir la clave: se cierra la sesión y la siguiente usa la nueva. Eliminarla: no puede conectar hasta guardar otra. Revocar la clave en OpenAI, comprobar el error y no confundir borrar localmente con revocar remotamente.

## Activación local: pruebas acústicas pendientes

1. Instalar el modelo de idioma del motor Android. Probar sin Internet únicamente la detección local (la conversación posterior sí requiere Internet). Si no hay motor on-device o modelo instalado, exigir error visible y control manual, sin fallback remoto.
2. Con app visible y Bluetooth apagado, autorizar escucha, activar una ventana y decir «Atento Jarvis». Esperar «Escuchando» antes de la petición. Verificar que el audio de detección no aparece en transcripciones ni se envía a OpenAI.
3. Ensayar 20 frases correctas y 20 distractores con distintos ruidos/distancias. Registrar conteos reales de aciertos, rechazos y activaciones indebidas, sin grabar audio. Probar nombres cortos y desconocidos. La tolerancia de una letra puede confundir nombres parecidos; no es identificación de usuario.
4. Confirmar huecos entre reinicios, finalización tras dos minutos/límite de errores, liberación del micrófono al activar WebRTC y nuevas preguntas sin repetir la frase. Al finalizar la sesión, comprobar nueva ventana local con consentimiento aún vigente.
5. Navegar, bloquear pantalla, cambiar de aplicación, revocar permiso y desactivar explícitamente: debe detenerse la escucha. Regresar no debe reactivarla. Comprobar que ningún callback tardío abre una sesión.
6. Probar confirmación sonora activada/desactivada. Ensayar reproducción de la frase por terceros/altavoz: puede activar el detector, por lo que no es autenticación ni autorización para cámara.
7. Repetir con Bluetooth solo para observar la ruta real del reconocedor. No atribuirla a las gafas por el resultado de otra prueba de micrófono. No se promete selección de ruta ni escucha permanente mediante DAT.

## Alternativa de servidor

Desactivar modo directo, configurar HTTPS/token y repetir emisión, WebRTC y conversación con un perfil de voz distinto al predeterminado del backend. Comprobar autenticación, expiración, límites y cambio de modo tras finalizar. La CLI del backend comprueba su propio protocolo, no audio Android.

## Hardware Meta: pendiente

Conservar y ejecutar el protocolo detallado siguiente. Registrar entrada y salida por separado; DAT conectado no prueba audio. Para cámara mantener ambas confirmaciones y verificar que nunca captura al volver de Meta AI ni envía sin autorización.

No marques una prueba como superada sin observarla en el dispositivo indicado. Conserva fecha, commit/APK, Android, Meta AI, modelo de gafas y firmware en un registro privado. Si compartes una incidencia, usa únicamente el informe filtrado de Diagnóstico y elimina información personal. Las observaciones de la aplicación tienen hora; un resultado antiguo no demuestra el estado actual.

## 1. Backend y OpenAI sin sensores

Prepara [el despliegue](../deploy/README.md). Desde una red externa verifica HTTPS válido, `/healthz` y `401` sin autenticación. La salud del proceso no demuestra conectividad con OpenAI.

Ejecuta `check-live.mjs` con el token personal en un archivo privado. Registra salida/fecha sin credenciales. Un éxito demuestra emisión efímera, conexión WebSocket y respuesta de texto reales, **no WebRTC ni audio**. Si faltan configuración o credenciales, la prueba termina como no realizada; no existe un modo de éxito ficticio.

En Android, introduce URL HTTPS y token personal en el asistente. En Diagnóstico:

1. **Comprobar credencial temporal**: debe observar HTTPS y emisión efímera; SDP, ICE y sesión no quedan acreditados por ello.
2. **Verificar Realtime sin micrófono**: debe observar SDP aceptado, transporte/canal, `SESSION_CREATED` y `SESSION_CONFIGURED` con el perfil confirmado. Cierra la sesión al confirmar. No crea pista de micrófono ni solicita respuesta.
3. Prueba un token personal incorrecto y, si administras el servidor, un error de cuota/configuración controlado. Comprueba el código y que no se muestre ningún secreto. No gastes cuota repitiendo solicitudes innecesarias.

## 2. Micrófono y altavoz del teléfono

Sin gafas ni auriculares conectados, desactiva **Preferir audio Bluetooth**. Concede micrófono bajo acción explícita. Detén la conversación antes de probar audio local.

1. Abre Diagnóstico y autoriza **Probar micrófono durante 3 segundos**. Habla durante la captura. Comprueba muestras leídas, nivel RMS y entrada del teléfono. No se guarda ni envía sonido; nivel bajo/silencio se informa como revisión pendiente.
2. Autoriza **Reproducir tono de prueba**, a volumen moderado. Comprueba dónde se oye. Pulsa **He oído el tono** o **No lo he oído** solo después de escucharlo. La aplicación conserva el resultado como confirmación manual.
3. Regresa al asistente e inicia conversación real. Di «Recuérdame el número 37». Después pregunta «¿Qué número te he dicho?». Comprueba que OpenAI oye y responde por el teléfono. No des por correcto el contexto hasta observar la respuesta.
4. Pausa: comprueba el indicador de micrófono de Android y ausencia de envío de voz. Reanuda, interrumpe una respuesta y finaliza. Prueba cancelar durante conexión. Sal de la aplicación y comprueba que se detiene todo sin reanudación automática.
5. Prueba permiso denegado, volumen nulo, otra aplicación que toma el foco y corte de red. El sistema puede cerrar el proceso al revocar permisos: registra ese comportamiento real.

## 3. Ray-Ban Meta, por etapas

Usa gafas emparejadas con Meta AI, firmware actualizado y modo de desarrollo o autorización de Meta. Anota modelo y versiones; no generalices un resultado a otros modelos.

1. Diagnóstico debe mostrar Bluetooth activado y permiso concedido. Registra la aplicación en Meta AI. Comprueba descubrimiento, metadatos y sesión `STARTED`. Ninguno de estos estados acredita micrófono, altavoz o cámara.
2. Activa **Preferir audio Bluetooth**. Sin conversación activa, ejecuta la captura local y consulta el tipo de entrada observado. Prueba hablar cerca de las gafas con el teléfono alejado; compara con el teléfono. Una ruta Bluetooth no demuestra por sí sola que sea el micrófono de las Ray-Ban.
3. Reproduce el tono y confirma físicamente que sale por los altavoces de las gafas. Registra por separado entrada y salida. Si se usa el teléfono, registra la alternativa como tal; no marques las gafas como correctas.
4. Inicia voz real y repite conversación/contexto/interrupción. Observa en Diagnóstico los eventos nativos y los errores, y confirma en qué dispositivo se oye.
5. Consulta el permiso de cámara DAT sin abrir sensores. Desde el asistente autoriza captura. Si Meta AI redirige para permisos, vuelve, conecta y pulsa capturar otra vez. Revisa la imagen y luego confirma el envío a OpenAI. Comprueba una respuesta hablada sobre lo que aparece en ella.
6. Repite denegación/revocación de cámara, cancelación de captura, quitar gafas, cerrar patillas, pérdida Bluetooth y paso a segundo plano. Comprueba cierre de cámara y micrófono. No habilites escucha permanente ni vídeo continuo para hacer estas pruebas.

## Clasificación de resultados

| Resultado | Qué acredita |
| --- | --- |
| Backend saludable | El proceso responde; no valida OpenAI |
| Credencial emitida | El proveedor emitió un token vigente; no acredita sesión |
| `session.created` | Se estableció una sesión Realtime por ese transporte |
| Capturador nativo iniciado o muestras locales | Se abrió captura software; requiere confirmar ruta e inteligibilidad |
| Reproductor iniciado o tono enviado | El software inició reproducción; requiere escucha humana |
| Tono oído | Confirmación manual de esa salida, no de OpenAI |
| Foto recibida de DAT | Se obtuvo esa imagen; no se ha enviado por ello a OpenAI |
| Conversación/visión verificadas físicamente | Solo acredita esa combinación concreta de APK, teléfono, gafas y firmware |
