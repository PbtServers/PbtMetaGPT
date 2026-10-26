import http from 'node:http';

const port = Number(process.env.PORT || 8787);
const apiKey = process.env.OPENAI_API_KEY;
const server = http.createServer(async (req, res) => {
  if (req.method !== 'POST' || req.url !== '/realtime/client-secret') {
    res.writeHead(404).end('Not found'); return;
  }
  // SECURITY: This skeleton intentionally does not expose tokens publicly.
  // Add authenticated mobile clients, rate limits and abuse prevention before enabling.
  res.writeHead(501, { 'Content-Type': 'application/json' });
  res.end(JSON.stringify({ error: 'Endpoint disabled until client authentication and rate limiting are implemented.' }));
});
server.listen(port, '127.0.0.1', () => console.log(`PbtMetaGPT backend localhost:${port}; token endpoint disabled`));
