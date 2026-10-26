# Backend personal de PbtMetaGPT

Servicio Node.js 22 para emitir credenciales temporales de OpenAI Realtime. Escucha únicamente en `127.0.0.1:8787`. No debe exponerse mediante HTTP público. La aplicación Android exige HTTPS.

## Configuración

1. Genera un token aleatorio para cada persona/dispositivo autorizado y calcula su hash:

   ```sh
   node -e "const c=require('node:crypto');const t=c.randomBytes(32).toString('base64url');console.log('Token personal:',t);console.log('SHA-256:',c.createHash('sha256').update(t).digest('hex'))"
   ```

   Entrega el token por un canal privado al usuario y guarda **solo su hash** en el servidor. No compartas el token en GitHub, logs ni capturas. Este token autoriza a consumir tu cuota de OpenAI; no es la clave de OpenAI.

2. Copia `.env.example` a `.env` y completa los valores en el servidor:

   ```dotenv
   OPENAI_API_KEY=clave_privada_solo_en_el_servidor
   CLIENT_TOKEN_SHA256=hash_sha256_del_token_personal
   OPENAI_REALTIME_MODEL=gpt-realtime
   PORT=8787
   ```

   `CLIENT_TOKEN_SHA256` admite varios hashes hexadecimales de 64 caracteres separados por comas. Para revocar un token, elimina su hash y reinicia el servicio. No se registra la clave ni se devuelve al cliente. Evita guardar cabeceras Authorization en el proxy.

3. Arranca desde el directorio `backend`:

   ```sh
   node --env-file=.env server.mjs
   ```

   El proceso falla al iniciar si faltan la clave o los hashes. No hay una credencial de acceso predeterminada.

4. Configura un proxy inverso con certificado válido. Por ejemplo, con Caddy:

   ```caddyfile
   asistente.tudominio.es {
       request_body {
           max_size 1KB
       }
       reverse_proxy 127.0.0.1:8787
   }
   ```

   Restringe conexiones y peticiones no autenticadas en el proxy. Este servicio está diseñado para uso personal en una sola instancia; no incluye cuentas públicas, registro de usuarios ni despliegue automático.

## Contrato

`POST /realtime/client-secret`, sin cuerpo. Cabecera `Authorization: Bearer <token personal>`.

Respuesta correcta:

```json
{"value":"ek_credencial_temporal","expires_at":1800000060}
```

La respuesta lleva `Cache-Control: no-store`. Android usa la credencial para negociar SDP con `POST https://api.openai.com/v1/realtime/calls`. Las credenciales de OpenAI viven solo en memoria. No hay claves privadas ni URL del proveedor configurable desde Android.

Errores: `401` sin autorización, `400` si se envía cuerpo, `404/405` para ruta/método incorrectos, `429` por límite y `502` por rechazo o fallo del proveedor. Los errores del proveedor se filtran antes de responder.

Los clientes no pueden elegir instrucciones ni modelo al solicitar la credencial. El modelo se configura en el servidor y debe admitir audio e imágenes. OpenAI permite modificar parte de la configuración durante la sesión: las instrucciones asociadas al token **no son una frontera de seguridad**.

## Límites y pruebas

- Credenciales de 60 segundos para establecer conexiones; **la caducidad no termina una sesión ya establecida** y la credencial puede reutilizarse mientras sea válida, según OpenAI.
- Cinco emisiones por minuto y token; 30 globales por hora y 100 globales por día; cuatro solicitudes simultáneas como máximo.
- Ventanas en memoria: se reinician al reiniciar el proceso y no se comparten entre réplicas. Para exposición a varios usuarios o escalado, añade identidad con caducidad/revocación, límites persistentes y controles de gasto.
- Timeout de OpenAI de 10 segundos. No se registran cuerpos ni cabeceras sensibles.
- Establece límites de gasto y alertas en OpenAI: el límite de emisión no controla cuánto consume cada sesión.

Ejecuta `node --test server.test.mjs`. Las pruebas sustituyen únicamente el proveedor externo; no llaman realmente a OpenAI ni prueban la ruta de audio del teléfono.
