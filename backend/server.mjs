import http from 'node:http';
import { createHash, timingSafeEqual, randomUUID } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { pathToFileURL } from 'node:url';

export function createTokenServer({
  apiKey = process.env.OPENAI_API_KEY,
  tokenHashes = process.env.CLIENT_TOKEN_SHA256?.split(',') ?? [],
  model = process.env.OPENAI_REALTIME_MODEL || 'gpt-realtime',
  fetchImpl = fetch,
  now = Date.now,
} = {}) {
  if (!apiKey || !tokenHashes.length || tokenHashes.some(h => !/^[a-f0-9]{64}$/i.test(h))) {
    throw new Error('Configura OPENAI_API_KEY y CLIENT_TOKEN_SHA256 (SHA-256 de cada token de acceso personal).');
  }
  const allowed = tokenHashes.map(h => Buffer.from(h, 'hex'));
  const windows = new Map();
  let inFlight = 0;
  function consume(id, maximum, milliseconds) {
    const time = now();
    let window = windows.get(id);
    if (!window || time >= window.until) {
      window = { until: time + milliseconds, count: 0 };
      windows.set(id, window);
    }
    if (window.count >= maximum) return false;
    window.count++;
    return true;
  }
  const server = http.createServer(async (req, res) => {
    const requestId = randomUUID();
    const send = (status, body, extra = {}) => {
      res.writeHead(status, {
        'Content-Type': 'application/json; charset=utf-8',
        'Cache-Control': 'no-store',
        'X-Content-Type-Options': 'nosniff',
        'X-Request-Id': requestId,
        ...extra,
      });
      res.end(JSON.stringify(body));
    };
    if (req.url === '/healthz' && req.method === 'GET') return send(200, { status: 'ok' });
    const failure = (status, code, message, extra = {}) => send(status, { error: message, code, request_id: requestId }, extra);
    if (req.url !== '/realtime/client-secret') return send(404, { error: 'Ruta no encontrada.' });
    if (req.method !== 'POST') return send(405, { error: 'Utiliza POST.' }, { Allow: 'POST' });
    const bearer = /^Bearer ([A-Za-z0-9_-]{32,256})$/.exec(req.headers.authorization || '')?.[1];
    if (!bearer) return failure(401, 'BACKEND_AUTH', 'Acceso no autorizado.');
    const digest = createHash('sha256').update(bearer).digest();
    let authorized = false;
    for (const hash of allowed) authorized = timingSafeEqual(digest, hash) || authorized;
    if (!authorized) return failure(401, 'BACKEND_AUTH', 'Acceso no autorizado.');
    // No se admiten modelos, instrucciones ni cuerpos suministrados por el cliente.
    if (req.headers['transfer-encoding'] || (req.headers['content-length'] && req.headers['content-length'] !== '0')) {
      req.resume();
      return send(400, { error: 'La solicitud no admite cuerpo.' });
    }
    if (!consume(digest.toString('hex'), 5, 60_000) ||
        !consume('global-hour', 30, 3_600_000) || !consume('global-day', 100, 86_400_000) || inFlight >= 4) {
      return failure(429, 'BACKEND_RATE_LIMIT', 'Límite de sesiones alcanzado. Inténtalo más tarde.', { 'Retry-After': '60' });
    }
    inFlight++;
    try {
      const upstream = await fetchImpl('https://api.openai.com/v1/realtime/client_secrets', {
        method: 'POST',
        headers: { Authorization: `Bearer ${apiKey}`, 'Content-Type': 'application/json', 'OpenAI-Safety-Identifier': digest.toString('hex') },
        body: JSON.stringify({
          expires_after: { anchor: 'created_at', seconds: 60 },
          session: {
            type: 'realtime', model,
            max_output_tokens: 1024,
            instructions: 'Habla en español de España. Responde de forma breve y clara. No afirmes ver nada salvo que el usuario comparta una imagen. Reconoce la incertidumbre.',
            output_modalities: ['audio'],
            audio: {
              input: { turn_detection: { type: 'server_vad', create_response: true, interrupt_response: true } },
              output: { voice: 'marin' },
            },
          },
        }),
        signal: AbortSignal.timeout(10_000),
      });
      if (!upstream.ok) {
        const failures = {
          401: ['OPENAI_AUTH', 'OpenAI rechazó la clave del servidor. Revisa su configuración.'],
          403: ['OPENAI_ACCESS', 'OpenAI no permite el acceso solicitado. Revisa proyecto, región y permisos.'],
          404: ['OPENAI_MODEL', 'No se encuentra el modelo de Realtime configurado en el servidor.'],
          429: ['OPENAI_QUOTA', 'OpenAI ha limitado la solicitud. Revisa cuota, facturación y límites.'],
        };
        const [code, message] = failures[upstream.status] ?? ['OPENAI_UPSTREAM', 'OpenAI no pudo autorizar la sesión.'];
        return failure(502, code, message);
      }
      const data = await upstream.json();
      if (typeof data.value !== 'string' || !data.value.startsWith('ek_') ||
          !Number.isSafeInteger(data.expires_at) || data.expires_at <= Math.floor(now() / 1000) + 5) {
        return failure(502, 'OPENAI_INVALID_SECRET', 'OpenAI devolvió una credencial temporal inválida.');
      }
      // Nunca devolver la clave privada, cabeceras ni errores sin filtrar del proveedor.
      send(200, { value: data.value, expires_at: data.expires_at });
    } catch (problem) {
      failure(502, problem?.name === 'TimeoutError' ? 'OPENAI_TIMEOUT' : 'OPENAI_NETWORK', 'No se pudo contactar con OpenAI a tiempo.');
    } finally { inFlight--; }
  });
  server.requestTimeout = 15_000;
  server.headersTimeout = 10_000;
  server.maxHeadersCount = 30;
  server.maxConnections = 50;
  return server;
}
export function serverConfiguration(env = process.env) {
  const secret = (name) => {
    if (env[name] && env[name + '_FILE']) throw new Error('Configura una única fuente para ' + name + '.');
    return env[name + '_FILE'] ? readFileSync(env[name + '_FILE'], 'utf8').trim() : env[name];
  };
  const host = env.HOST || '127.0.0.1';
  const port = Number(env.PORT || 8787);
  if (!['127.0.0.1', '0.0.0.0'].includes(host) || !Number.isInteger(port) || port < 1 || port > 65535) {
    throw new Error('HOST o PORT no válidos.');
  }
  return { host, port, apiKey: secret('OPENAI_API_KEY'), tokenHashes: secret('CLIENT_TOKEN_SHA256')?.split(',').map(h => h.trim()), model: env.OPENAI_REALTIME_MODEL || 'gpt-realtime' };
}
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  try {
    const config = serverConfiguration();
    const server = createTokenServer(config);
    server.listen(config.port, config.host, () => console.log('Backend PbtMetaGPT preparado. No se registran credenciales.'));
    const shutdown = () => { server.close(() => process.exit(0)); setTimeout(() => process.exit(1), 12_000).unref(); };
    process.once('SIGTERM', shutdown);
    process.once('SIGINT', shutdown);
  } catch { console.error('No se pudo iniciar el backend. Revisa las variables y los archivos de secretos.'); process.exitCode = 1; }
}
