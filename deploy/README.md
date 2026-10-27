# Despliegue personal en Ubuntu Server

Desde v0.4.0 este despliegue es **opcional**: el modo directo BYOK funciona sin VPS, Docker ni dominio. Esta guía corresponde únicamente a la alternativa que mantiene la clave de OpenAI en el servidor.

Preparación de v0.3.0. No se ha accedido a ningún servidor del usuario ni se ha emitido un certificado público durante el desarrollo. La prueba de CI valida la configuración y el tráfico HTTP interno con secretos ficticios; no acredita un despliegue real ni acceso a OpenAI.

## Requisitos

- Ubuntu Server con Docker Engine y el complemento Docker Compose, versión 2.24.4 o posterior para los archivos de CI. Usa las [instrucciones oficiales de Docker para Ubuntu](https://docs.docker.com/engine/install/ubuntu/).
- Un dominio propio con DNS A/AAAA apuntando al servidor. No publiques un registro AAAA que no tenga conectividad IPv6 funcional.
- Puertos TCP 80 y 443 accesibles desde Internet para HTTPS/ACME. Conserva el acceso SSH antes de ajustar el cortafuegos. Ningún servicio existente debe ocupar esos puertos.
- Salida HTTPS hacia OpenAI, autoridades de certificación y registros de imágenes.
- Proyecto de OpenAI con facturación y acceso al modelo `gpt-realtime` o al modelo compatible configurado.

El único servicio con puertos publicados es Caddy. El backend escucha en `0.0.0.0` dentro de su contenedor y no publica `8787` en el servidor. Docker configura reglas de red propias: comprueba los puertos publicados y no confíes solo en reglas UFW. El despliegue estándar descrito usa Docker Engine con daemon del sistema; no se ha validado Docker rootless.

## Preparar configuración y secretos

En el servidor, clona el repositorio y entra en `deploy`:

```bash
git clone https://github.com/PbtServers/PbtMetaGPT.git
cd PbtMetaGPT/deploy
umask 077
cp .env.example .env
install -d -m 700 secrets
```

Edita `.env` con tu dominio, correo ACME y modelo. Este archivo no necesita contener claves. El dominio debe ser solo el nombre DNS, sin esquema, puerto, barras ni espacios. Los ejemplos `example.com` no sirven para producción.

Introduce la clave de OpenAI de forma interactiva, sin ponerla en el historial de órdenes:

```bash
read -rsp 'Clave privada de OpenAI: ' OPENAI_SETUP_KEY
printf '\n'
printf '%s' "$OPENAI_SETUP_KEY" > secrets/openai_api_key
unset OPENAI_SETUP_KEY
```

Genera un token personal de 32 bytes y su hash. Este comando usa Node.js 22; si no está instalado en Ubuntu puedes ejecutar el mismo código en un equipo de confianza y transferir los archivos de forma privada:

```bash
node -e 'const c=require("node:crypto"),f=require("node:fs");const t=c.randomBytes(32).toString("base64url");f.writeFileSync("secrets/backend_token",t+"\n",{mode:0o600});f.writeFileSync("secrets/client_token_sha256",c.createHash("sha256").update(t).digest("hex")+"\n",{mode:0o600})'
sudo chown 1000:1000 secrets/openai_api_key secrets/client_token_sha256
sudo chmod 400 secrets/openai_api_key secrets/client_token_sha256
```

El contenedor de backend ejecuta Node como UID 1000, con sistema de archivos de solo lectura, sin capacidades Linux y sin posibilidad de elevar privilegios. Compose monta únicamente los archivos necesarios bajo `/run/secrets`. Los permisos de archivos locales deben permitir leerlos al UID 1000; Compose no cambia el propietario de un secreto enlazado desde un archivo. No montes `backend_token` en ningún contenedor: se entrega por un canal privado al usuario de Android.

`secrets/`, `.env` y los informes locales están excluidos de Git. Los secretos no se copian a la imagen ni se pasan como argumentos de compilación. La aplicación Android nunca necesita la clave privada de OpenAI. Los secretos Compose son archivos montados, no un almacén cifrado: protege también el disco, las copias de seguridad y el acceso al servidor.

## Arrancar y verificar

```bash
docker compose config --quiet
docker compose up -d --build --wait
docker compose ps
curl --fail https://TU_DOMINIO/healthz
curl --silent --output /dev/null --write-out '%{http_code}\n' -X POST https://TU_DOMINIO/realtime/client-secret
```

Resultados esperados: servicios saludables, `{"status":"ok"}` y rechazo `401` sin token. **`healthz` solo prueba el proceso; no contacta con OpenAI.** Comprueba el certificado con la validación TLS normal; no uses `curl -k` ni desactives TLS en Android. Caddy obtiene y renueva los certificados; necesita los volúmenes persistentes `caddy_data` y `caddy_config`.

Comprueba también desde otra red que `8787` no sea accesible. No actives registros de acceso que guarden Authorization, cuerpos o parámetros. El backend no registra credenciales. Sus errores usan códigos y un identificador de petición; los cuerpos originales del proveedor no se devuelven.

## Prueba real de OpenAI

Con Node.js 22 o posterior, desde `deploy`:

```bash
RUN_LIVE_CHECK=1 \
PBT_BACKEND_URL=https://TU_DOMINIO \
PBT_BACKEND_TOKEN_FILE="$PWD/secrets/backend_token" \
OPENAI_REALTIME_MODEL=gpt-realtime \
node ../backend/check-live.mjs
```

El modelo debe coincidir con el del backend. La prueba consume una emisión de credencial y puede consumir cuota al solicitar una respuesta de texto de hasta 16 tokens. No utiliza la clave privada en el cliente ni lee micrófonos. Espera una credencial temporal vigente, `session.created`, texto `PRUEBA_OK` y `response.done` completado. Nunca imprime tokens, texto del proveedor ni mensajes de error externos. El resultado distingue explícitamente **WebSocket**, **audio no probado** y **gafas no probadas**; no sustituye la prueba WebRTC de Android.

En Android, introduce la URL y el token personal; abre **Diagnóstico**. **Comprobar credencial temporal** valida la emisión. **Verificar Realtime sin micrófono** negocia SDP/WebRTC con una pista solo de recepción y cierra después de recibir `session.created`. Ninguna de estas comprobaciones demuestra una conversación por voz; sigue [PRUEBAS_REALES.md](../docs/PRUEBAS_REALES.md).

## Operación y recuperación

- Una instancia de backend: sus límites están en memoria y se reinician con el proceso. No escales réplicas sin moverlos a un almacén compartido. Usa presupuestos y alertas de OpenAI; el límite de emisión no limita el gasto de una sesión ya abierta.
- Para actualizar, haz copia privada de configuración y volúmenes, revisa el commit y ejecuta `git pull --ff-only` seguido de `docker compose up -d --build --wait`.
- Para detener: `docker compose down`. Conserva los volúmenes y secretos; no añadas `--volumes` si quieres conservar los certificados.
- Para revocar acceso, sustituye/elimina el hash del token, genera otro si procede y recrea el backend con `docker compose up -d --force-recreate backend`. La revocación afecta a nuevas emisiones; no invalida credenciales o sesiones ya emitidas por OpenAI.
- Después de cambiar una clave o un archivo de secreto, recrea el contenedor y repite las comprobaciones. No hay recarga automática de secretos.
- Si falla el arranque, revisa permisos de los archivos, dominio, DNS y ocupación de puertos. No pegues `docker inspect` completo, `.env`, cabeceras ni archivos secretos en incidencias públicas.

El comando de CI con `compose.ci.yml` publica HTTP solo en loopback del runner y usa una configuración Caddy distinta. **No uses ese archivo para producción.**
