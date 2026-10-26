import { readFileSync } from 'node:fs';
import { pathToFileURL } from 'node:url';

export async function checkLive({ baseUrl, token, model = 'gpt-realtime', fetchImpl = fetch, WebSocketImpl = WebSocket, timeoutMs = 20_000 }) {
  const base = new URL(baseUrl);
  if (base.protocol !== 'https:' || base.username || base.password || base.search || base.hash) throw new Error('CONFIG_HTTPS');
  if (!/^[A-Za-z0-9_-]{32,256}$/.test(token || '') || /^sk[-_]/.test(token)) throw new Error('CONFIG_TOKEN');
  if (!/^[a-zA-Z0-9._-]{1,100}$/.test(model)) throw new Error('CONFIG_MODEL');
  const root = base.href.replace(/\/$/, '');
  const health = await fetchImpl(root + '/healthz', { redirect: 'error', signal: AbortSignal.timeout(timeoutMs) });
  if (!health.ok || (await health.json()).status !== 'ok') throw new Error('BACKEND_HEALTH');
  const result = await fetchImpl(root + '/realtime/client-secret', {
    method: 'POST', headers: { Authorization: `Bearer ${token}` }, redirect: 'error', signal: AbortSignal.timeout(timeoutMs),
  });
  if (!result.ok) throw new Error('BACKEND_HTTP_' + result.status);
  const secret = await result.json();
  if (!secret.value?.startsWith('ek_') || secret.expires_at <= Date.now() / 1000 + 5) throw new Error('INVALID_SECRET');
  return new Promise((resolve, reject) => {
    const socket = new WebSocketImpl('wss://api.openai.com/v1/realtime?model=' + encodeURIComponent(model), ['realtime', 'openai-insecure-api-key.' + secret.value]);
    let sessionCreated = false;
    let textReceived = false;
    let finished = false;
    const finish = (code) => {
      if (finished) return;
      finished = true;
      clearTimeout(timer);
      socket.close();
      if (code) reject(new Error(code));
      else resolve({ backend_https: true, credencial_temporal: true, session_created: true, respuesta_texto: true, transporte: 'WebSocket', audio_probado: false, gafas_probadas: false });
    };
    const timer = setTimeout(() => finish('REALTIME_TIMEOUT'), timeoutMs);
    socket.addEventListener('error', () => finish('REALTIME_NETWORK'));
    socket.addEventListener('close', () => { if (!finished) finish('REALTIME_CLOSED'); });
    socket.addEventListener('message', event => {
      try {
        const data = JSON.parse(event.data);
        if (data.type === 'session.created') {
          sessionCreated = true;
          socket.send(JSON.stringify({ type: 'response.create', response: { output_modalities: ['text'], instructions: 'Responde únicamente PRUEBA_OK.', max_output_tokens: 16 } }));
        }
        if (data.type === 'response.output_text.done') textReceived = typeof data.text === 'string' && data.text.includes('PRUEBA_OK');
        if (data.type === 'error') finish('REALTIME_REJECTED');
        if (data.type === 'response.done') finish(sessionCreated && textReceived && data.response?.status === 'completed' ? null : 'REALTIME_RESPONSE');
      } catch { finish('REALTIME_EVENT'); }
    });
  });
}
if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  if (process.env.RUN_LIVE_CHECK !== '1' || !process.env.PBT_BACKEND_URL || !process.env.PBT_BACKEND_TOKEN_FILE) {
    console.error('Prueba real no realizada. Configura RUN_LIVE_CHECK=1, PBT_BACKEND_URL y PBT_BACKEND_TOKEN_FILE. Puede consumir cuota de OpenAI.');
    process.exitCode = 2;
  } else {
    try {
      const report = await checkLive({ baseUrl: process.env.PBT_BACKEND_URL, token: readFileSync(process.env.PBT_BACKEND_TOKEN_FILE, 'utf8').trim(), model: process.env.OPENAI_REALTIME_MODEL || 'gpt-realtime' });
      console.log(JSON.stringify({ fecha: new Date().toISOString(), ...report }, null, 2));
    } catch (problem) {
      const code = /^[A-Z_0-9]{1,80}$/.test(problem.message) ? problem.message : 'NETWORK_OR_CONFIGURATION';
      console.error('Prueba real fallida: ' + code + '. No se muestran credenciales ni respuestas del proveedor.');
      process.exitCode = 1;
    }
  }
}
