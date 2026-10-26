import http from 'node:http';
import { createHash, timingSafeEqual } from 'node:crypto';
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
    const send = (status, body, extra = {}) => {
      res.writeHead(status, {
        'Content-Type': 'application/json; charset=utf-8',
        'Cache-Control': 'no-store',
        'X-Content-Type-Options': 'nosniff',
        ...extra,
      });
      res.end(JSON.stringify(body));
    };
    if (req.url !== '/realtime/client-secret') return send(404, { error: 'Ruta no encontrada.' });
    if (req.method !== 'POST') return send(405, { error: 'Utiliza POST.' }, { Allow: 'POST' });
    const bearer = /^Bearer ([A-Za-z0-9_-]{32,256})$/.exec(req.headers.authorization || '')?.[1];
    if (!bearer) return send(401, { error: 'Acceso no autorizado.' });
    const digest = createHash('sha256').update(bearer).digest();
    let authorized = false;
    for (const hash of allowed) authorized = timingSafeEqual(digest, hash) || authorized;
    if (!authorized) return send(401, { error: 'Acceso no autorizado.' });
    // No se admiten modelos, instrucciones ni cuerpos suministrados por el cliente.
    if (req.headers['transfer-encoding'] || (req.headers['content-length'] && req.headers['content-length'] !== '0')) {
      req.resume();
      return send(400, { error: 'La solicitud no admite cuerpo.' });
    }
    if (!consume(digest.toString('hex'), 5, 60_000) ||
        !consume('global-hour', 30, 3_600_000) || !consume('global-day', 100, 86_400_000) || inFlight >= 4) {
      return send(429, { error: 'Límite de sesiones alcanzado. Inténtalo más tarde.' }, { 'Retry-After': '60' });
    }
    inFlight++;
    try {
      const upstream = await fetchImpl('https://api.openai.com/v1/realtime/client_secrets', {
        method: 'POST',
        headers: { Authorization: `Bearer ${apiKey}`, 'Content-Type': 'application/json' },
        body: JSON.stringify({
          expires_after: { anchor: 'created_at', seconds: 60 },
          session: {
            type: 'realtime', model,
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
      if (!upstream.ok) return send(502, { error: 'OpenAI no pudo autorizar la sesión. Comprueba la configuración y la cuota del servidor.' });
      const data = await upstream.json();
      if (typeof data.value !== 'string' || !data.value.startsWith('ek_') ||
          !Number.isSafeInteger(data.expires_at) || data.expires_at <= Math.floor(now() / 1000) + 5) {
        return send(502, { error: 'OpenAI devolvió una credencial temporal inválida.' });
      }
      // Nunca devolver la clave privada, cabeceras ni errores sin filtrar del proveedor.
      send(200, { value: data.value, expires_at: data.expires_at });
    } catch {
      send(502, { error: 'No se pudo contactar con OpenAI a tiempo.' });
    } finally { inFlight--; }
  });
  server.requestTimeout = 15_000;
  server.headersTimeout = 10_000;
  server.maxHeadersCount = 30;
  server.maxConnections = 50;
  return server;
}
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const server = createTokenServer();
  server.listen(Number(process.env.PORT || 8787), '127.0.0.1', () =>
    console.log('Backend PbtMetaGPT preparado en localhost. Publica únicamente mediante un proxy HTTPS autenticado.'));
}
