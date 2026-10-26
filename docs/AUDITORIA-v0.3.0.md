# Auditoría desde v0.2.0

Base: `9898ef73ac89cead77da1d10cc4e45a2feae378d`. La base compilaba y superaba Lint, 13 pruebas Node y 4 pruebas JVM. No constaban llamadas reales a OpenAI, pruebas de instalación, Mock Device Kit ni hardware real. Esta auditoría no cambia ese historial.

| Hallazgo | Cambio en v0.3.0 | Límite de la evidencia |
| --- | --- | --- |
| Backend ligado a localhost, inaccesible desde otro contenedor | HOST configurable; Compose sin publicar el puerto del backend | La accesibilidad pública y ACME requieren servidor y dominio reales |
| Clave y hashes solo desde variables de entorno | Compatibilidad con archivos de secretos, rechazo de fuentes ambiguas y Docker sin secretos en la imagen | Los archivos Compose no son un almacén cifrado |
| Errores del proveedor reducidos a un 502 genérico | Códigos filtrados para autenticación, acceso, modelo, cuota, red y timeout, con identificador de petición | No se reenvían mensajes originales ni cuerpos sensibles |
| No había un procedimiento reproducible de conexión real | Prueba CLI con credencial efímera y sesión/respuesta reales; comprobación WebRTC sin micrófono en Android | CLI usa WebSocket; Android y audio requieren sus propias pruebas |
| Registro, SDP, ICE y sesión mezclados en un único estado | Pantalla por etapas, códigos reales observados e historial acotado | No se infiere funcionamiento físico de un estado software |
| Sin prueba local separada de audio | Captura breve para RMS/ruta y tono con confirmación manual | RMS no acredita inteligibilidad; iniciar reproducción no acredita que se oiga |
| El cuerpo HTTP se leía al reanudar en el hilo de interfaz | OkHttp consume y cierra credencial/SDP en su callback IO antes de reanudar la pantalla; cancelar cancela también esa lectura | Corregido en código; red real pendiente de configuración del usuario |
| Selección de ruta no demostraba entrada/salida reales | Las pruebas locales consultan los dispositivos realmente enrutados y señalan discrepancias | Un tipo Bluetooth no identifica el modelo de gafas |
| Errores DAT y cámara poco específicos | Estados, códigos de tipo/enum del SDK y códigos por etapa, sin exportar nombres ni identificadores de dispositivos | El soporte de cámara y audio de Ray-Ban sigue pendiente de prueba física |
| Permisos comprobados solo al crear la pantalla | Relectura al volver a primer plano y bajo acción del usuario | No activa sensores ni reanuda sesiones al volver |
| Un cierre podía reemplazar el último error de control | Se conserva el error que provocó el cierre | El diagnóstico representa el último intento y su hora, no un certificado de estado permanente |

La interfaz y el informe están en español. Los códigos técnicos de SDK/protocolo se conservan como códigos. El informe no incluye URL de backend, tokens, clave privada, transcripción, muestras de audio, fotos, direcciones Bluetooth ni nombres personales de dispositivos. Vive en memoria; el usuario puede borrarlo o copiarlo expresamente. Los estados iniciales son **sin comprobar**, nunca simulaciones positivas.

No se han añadido claves ni se ha usado una API privada de Meta. Se mantiene DAT `1.0.1`, ya resuelto y compilado en la base; la revisión usa sus flujos de registro, dispositivos, sesión y cámara. No se habilita `mwdat-speech`, «Hey Meta», activación de sensores en segundo plano ni subida de vídeo.
