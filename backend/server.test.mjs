import { test } from 'node:test';
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { once } from 'node:events';
import { createTokenServer, serverConfiguration } from './server.mjs';
import { mkdtempSync, writeFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

const token = 'acceso_personal_de_prueba_1234567890';
const hash = createHash('sha256').update(token).digest('hex');
const clock = 1_800_000_000_000;
async function fixture(t, overrides = {}) {
  const calls = [];
  const currentTime = overrides.now ?? (() => clock);
  const server = createTokenServer({
    apiKey: 'clave-privada-solo-servidor', tokenHashes: [hash], now: () => clock,
    fetchImpl: async (...args) => {
      calls.push(args);
      return { ok: true, json: async () => ({ value: 'ek_temporal_de_prueba', expires_at: Math.floor(currentTime() / 1000) + 60, private: 'no devolver' }) };
    }, ...overrides,
  });
  server.listen(0, '127.0.0.1');
  await once(server, 'listening');
  t.after(() => new Promise(resolve => { server.close(resolve); server.closeAllConnections(); }));
  const url = `http://127.0.0.1:${server.address().port}/realtime/client-secret`;
  const request = (options = {}) => fetch(url, { method: 'POST', headers: { Authorization: `Bearer ${token}` }, ...options });
  return { request, calls, url };
}
test('rechaza iniciar el servicio sin secretos o hashes válidos', () => {
  assert.throws(() => createTokenServer({ apiKey: '', tokenHashes: [] }));
  assert.throws(() => createTokenServer({ apiKey: 'x', tokenHashes: ['token-sin-hash'] }));
});
test('rechaza clientes anónimos sin contactar con OpenAI', async t => {
  const { request, calls } = await fixture(t);
  assert.equal((await request({ headers: {} })).status, 401);
  assert.equal(calls.length, 0);
});
test('rechaza tokens incorrectos', async t => {
  const { request } = await fixture(t);
  assert.equal((await request({ headers: { Authorization: 'Bearer ' + 'x'.repeat(40) } })).status, 401);
});
test('solo admite POST en la ruta exacta', async t => {
  const { request, url } = await fixture(t);
  assert.equal((await request({ method: 'GET' })).status, 405);
  assert.equal((await fetch(url + '/otra')).status, 404);
});
test('emite solo credencial efímera y fecha, sin caché', async t => {
  const { request, calls } = await fixture(t);
  const response = await request();
  assert.equal(response.status, 200);
  assert.equal(response.headers.get('cache-control'), 'no-store');
  assert.deepEqual(await response.json(), { value: 'ek_temporal_de_prueba', expires_at: clock / 1000 + 60 });
  assert.equal(calls[0][0], 'https://api.openai.com/v1/realtime/client_secrets');
  const config = JSON.parse(calls[0][1].body);
  assert.equal(config.expires_after.seconds, 60);
  assert.equal(config.session.type, 'realtime');
  assert.equal(config.session.audio.output.voice, 'marin');
});
test('impide que el cliente cambie el modelo o instrucciones', async t => {
  const { request, calls } = await fixture(t);
  assert.equal((await request({ body: '{"model":"otro"}' })).status, 400);
  assert.equal(calls.length, 0);
});
test('limita emisiones por identidad', async t => {
  const { request, calls } = await fixture(t);
  for (let i = 0; i < 5; i++) assert.equal((await request()).status, 200);
  const limited = await request();
  assert.equal(limited.status, 429);
  assert.equal(limited.headers.get('retry-after'), '60');
  assert.equal(calls.length, 5);
});
test('renueva la ventana de límite', async t => {
  let time = clock;
  const { request } = await fixture(t, {
    now: () => time,
    fetchImpl: async () => ({ ok: true, json: async () => ({ value: 'ek_temporal_de_prueba', expires_at: Math.floor(time / 1000) + 60 }) }),
  });
  for (let i = 0; i < 5; i++) await request();
  time += 60_001;
  assert.equal((await request()).status, 200);
});
test('no filtra errores del proveedor', async t => {
  const { request } = await fixture(t, { fetchImpl: async () => ({ ok: false }) });
  const result = await request();
  assert.equal(result.status, 502);
  assert.ok(!(await result.text()).includes('clave-privada'));
});
test('controla cortes de red', async t => {
  const { request } = await fixture(t, { fetchImpl: async () => { throw Error('dato sensible'); } });
  const result = await request();
  assert.equal(result.status, 502);
  assert.ok(!(await result.text()).includes('dato sensible'));
});
test('rechaza credenciales permanentes y caducadas', async t => {
  for (const data of [{ value: 'sk_privada', expires_at: clock / 1000 + 60 }, { value: 'ek_temporal', expires_at: clock / 1000 }]) {
    const { request } = await fixture(t, { fetchImpl: async () => ({ ok: true, json: async () => data }) });
    assert.equal((await request()).status, 502);
  }
});
test('el límite global horario persiste entre ventanas de identidad', async t => {
  let time = clock;
  const { request, calls } = await fixture(t, { now: () => time });
  for (let minute = 0; minute < 6; minute++) {
    for (let i = 0; i < 5; i++) assert.equal((await request()).status, 200);
    time += 60_001;
  }
  assert.equal((await request()).status, 429);
  assert.equal(calls.length, 30);
  time += 3_600_000;
  assert.equal((await request()).status, 200);
});
test('limita las llamadas simultáneas al proveedor y libera las plazas', async t => {
  let release;
  const gate = new Promise(resolve => { release = resolve; });
  let started;
  const ready = new Promise(resolve => { started = resolve; });
  let count = 0;
  const { request } = await fixture(t, { fetchImpl: async () => {
    if (++count === 4) started();
    await gate;
    return { ok: true, json: async () => ({ value: 'ek_temporal_de_prueba', expires_at: clock / 1000 + 60 }) };
  } });
  const pending = Array.from({ length: 4 }, () => request());
  try {
    await ready;
    assert.equal((await request()).status, 429);
  } finally { release(); }
  assert.deepEqual((await Promise.all(pending)).map(response => response.status), [200, 200, 200, 200]);
  assert.equal(count, 4);
});
test('healthz comprueba el proceso sin consumir cuota ni mostrar configuración', async t => {
  const { url, calls } = await fixture(t);
  const response = await fetch(url.replace('/realtime/client-secret', '/healthz'));
  assert.equal(response.status, 200);
  assert.deepEqual(await response.json(), { status: 'ok' });
  assert.equal(calls.length, 0);
});
test('clasifica errores reales del proveedor sin reenviar sus cuerpos', async t => {
  for (const [status, code] of [[401, 'OPENAI_AUTH'], [403, 'OPENAI_ACCESS'], [404, 'OPENAI_MODEL'], [429, 'OPENAI_QUOTA'], [500, 'OPENAI_UPSTREAM']]) {
    const { request } = await fixture(t, { fetchImpl: async () => ({ ok: false, status, json: () => { assert.fail('No leer cuerpos de error sensibles'); } }) });
    const response = await request();
    const data = await response.json();
    assert.equal(response.status, 502);
    assert.equal(data.code, code);
    assert.equal(data.request_id, response.headers.get('x-request-id'));
    assert.match(data.request_id, /^[0-9a-f-]{36}$/);
  }
});
test('admite secretos montados y rechaza fuentes ambiguas', t => {
  const directory = mkdtempSync(join(tmpdir(), 'pbtmetagpt-'));
  t.after(() => rmSync(directory, { recursive: true, force: true }));
  const keyFile = join(directory, 'key');
  const hashesFile = join(directory, 'hashes');
  writeFileSync(keyFile, 'clave-ficticia\n');
  writeFileSync(hashesFile, hash + '\n');
  const configuration = serverConfiguration({ OPENAI_API_KEY_FILE: keyFile, CLIENT_TOKEN_SHA256_FILE: hashesFile, HOST: '0.0.0.0' });
  assert.equal(configuration.host, '0.0.0.0');
  assert.equal(configuration.apiKey, 'clave-ficticia');
  assert.deepEqual(configuration.tokenHashes, [hash]);
  assert.throws(() => serverConfiguration({ OPENAI_API_KEY: 'x', OPENAI_API_KEY_FILE: keyFile }));
  assert.throws(() => serverConfiguration({ PORT: 'invalido' }));
});
