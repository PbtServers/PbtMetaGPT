import { test } from 'node:test';
import assert from 'node:assert/strict';
import { checkLive } from './check-live.mjs';
const token = 'token_personal_de_prueba_1234567890';
class Socket {
  listeners = {};
  addEventListener(name, action) {
    this.listeners[name] = action;
    if (name === 'message') queueMicrotask(() => action({ data: '{"type":"session.created"}' }));
  }
  send(text) {
    assert.equal(JSON.parse(text).response.output_modalities[0], 'text');
    queueMicrotask(() => {
      this.listeners.message({ data: '{"type":"response.output_text.done","text":"PRUEBA_OK"}' });
      this.listeners.message({ data: '{"type":"response.done","response":{"status":"completed"}}' });
    });
  }
  close() {}
}
const fetchImpl = async url => ({ ok: true, json: async () => url.endsWith('/healthz') ? { status: 'ok' } : { value: 'ek_temporal_de_prueba', expires_at: Date.now() / 1000 + 60 } });
test('verifica el protocolo sin confundir texto con audio o hardware', async () => {
  const report = await checkLive({ baseUrl: 'https://backend.example.com', token, fetchImpl, WebSocketImpl: Socket });
  assert.equal(report.session_created, true);
  assert.equal(report.audio_probado, false);
  assert.equal(report.gafas_probadas, false);
});
test('rechaza HTTP y claves privadas antes de contactar con la red', async () => {
  for (const config of [{ baseUrl: 'http://backend.example.com', token }, { baseUrl: 'https://backend.example.com', token: 'sk-' + 'x'.repeat(40) }]) {
    await assert.rejects(checkLive({ ...config, fetchImpl: () => { assert.fail('No debe llamar a la red'); } }));
  }
});
test('una credencial emitida sin sesión no cuenta como conexión Realtime', async () => {
  class ClosedSocket extends Socket {
    addEventListener(name, action) { if (name === 'close') queueMicrotask(action); }
  }
  await assert.rejects(checkLive({ baseUrl: 'https://backend.example.com', token, fetchImpl, WebSocketImpl: ClosedSocket }), /REALTIME_CLOSED/);
});
