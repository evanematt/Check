// Cloudflare Worker for the "не той кіт" site.
// Takes photo/video uploads from the site, posts them to the band's Telegram channel,
// and (when a KV namespace is bound as GALLERY) keeps a list of recent uploads for the on-site gallery.
//
// Settings (Workers → your worker → Settings → Variables and Secrets):
//   BOT_TOKEN        secret  token from @BotFather; the bot must be an admin of the channel
//   CHAT_ID          text    channel to post to, e.g. @knopaband (or a private group id for moderation)
//   ALLOWED_ORIGINS  text    optional, comma-separated; defaults to https://evanematt.github.io
//   GALLERY          KV      optional binding; enables /recent and the rate limit

const MAX_BYTES = 45 * 1024 * 1024;   // bots can send files up to 50 MB
const PHOTO_MAX = 10 * 1024 * 1024;   // sendPhoto limit; larger images are sent as files
const RATE_LIMIT = 20;                // uploads per IP per 10 minutes (needs GALLERY)
const KEEP = 60;                      // how many recent uploads the gallery remembers

export default {
  async fetch(req, env, ctx) {
    const url = new URL(req.url);
    const allowed = (env.ALLOWED_ORIGINS || 'https://evanematt.github.io').split(',').map(s => s.trim()).filter(Boolean);
    const origin = req.headers.get('Origin') || '';
    const cors = {
      'Access-Control-Allow-Origin': allowed.includes(origin) ? origin : allowed[0],
      'Access-Control-Allow-Methods': 'GET, POST, OPTIONS',
      'Access-Control-Allow-Headers': 'Content-Type',
      'Vary': 'Origin'
    };
    if (req.method === 'OPTIONS') return new Response(null, { headers: cors });
    try {
      if (url.pathname === '/upload' && req.method === 'POST') return await upload(req, env, cors);
      if (url.pathname === '/recent' && req.method === 'GET') return await recent(env, cors);
      if (url.pathname.startsWith('/file/') && req.method === 'GET') return await file(url.pathname.slice(6), req, env, ctx, cors);
      return json({ error: 'not_found' }, 404, cors);
    } catch (e) {
      return json({ error: 'server' }, 500, cors);
    }
  }
};

function json(body, status, cors) {
  return new Response(JSON.stringify(body), { status, headers: { ...cors, 'Content-Type': 'application/json' } });
}

async function allowRate(req, env) {
  if (!env.GALLERY) return true;
  const ip = req.headers.get('CF-Connecting-IP') || 'unknown';
  const key = `rl:${ip}:${Math.floor(Date.now() / 600000)}`;
  const n = Number(await env.GALLERY.get(key)) || 0;
  if (n >= RATE_LIMIT) return false;
  await env.GALLERY.put(key, String(n + 1), { expirationTtl: 900 });
  return true;
}

async function upload(req, env, cors) {
  if (Number(req.headers.get('Content-Length') || 0) > MAX_BYTES + 256 * 1024) return json({ error: 'too_big' }, 413, cors);
  if (!(await allowRate(req, env))) return json({ error: 'slow_down' }, 429, cors);
  const form = await req.formData();
  const f = form.get('file');
  if (!f || typeof f === 'string') return json({ error: 'no_file' }, 400, cors);
  if (f.size > MAX_BYTES) return json({ error: 'too_big' }, 413, cors);
  const kind = f.type.startsWith('image/') ? 'photo' : f.type.startsWith('video/') ? 'video' : null;
  if (!kind) return json({ error: 'type' }, 415, cors);

  const who = String(form.get('name') || '').replace(/[\u0000-\u001f<>]/g, '').trim().slice(0, 40);
  const caption = who ? `📸 з концерту · від ${who}` : '📸 з концерту';
  const method = kind === 'video' ? 'sendVideo' : f.size <= PHOTO_MAX ? 'sendPhoto' : 'sendDocument';
  const field = { sendPhoto: 'photo', sendVideo: 'video', sendDocument: 'document' }[method];

  const tg = new FormData();
  tg.append('chat_id', env.CHAT_ID);
  tg.append('caption', caption);
  tg.append(field, f, f.name || `${kind}`);
  if (method === 'sendVideo') tg.append('supports_streaming', 'true');
  const r = await fetch(`https://api.telegram.org/bot${env.BOT_TOKEN}/${method}`, { method: 'POST', body: tg });
  const data = await r.json();
  if (!data.ok) return json({ error: 'telegram', detail: data.description }, 502, cors);

  if (env.GALLERY) {
    const m = data.result;
    let full = null, thumb = null;
    if (m.photo) { full = m.photo[m.photo.length - 1].file_id; thumb = m.photo[Math.min(2, m.photo.length - 1)].file_id; }
    else if (m.video) { full = m.video.file_id; thumb = (m.video.thumbnail || m.video.thumb || {}).file_id || null; }
    else if (m.document) { full = m.document.file_id; thumb = (m.document.thumbnail || m.document.thumb || {}).file_id || null; }
    const list = JSON.parse((await env.GALLERY.get('recent')) || '[]');
    list.unshift({ kind, full, thumb, ts: Date.now() });
    await env.GALLERY.put('recent', JSON.stringify(list.slice(0, KEEP)));
  }
  return json({ ok: true }, 200, cors);
}

async function recent(env, cors) {
  if (!env.GALLERY) return json({ items: [] }, 200, cors);
  const list = JSON.parse((await env.GALLERY.get('recent')) || '[]');
  return new Response(JSON.stringify({ items: list }), { headers: { ...cors, 'Content-Type': 'application/json', 'Cache-Control': 'public, max-age=20' } });
}

// Streams a file the bot sent (photos, thumbnails, videos up to 20 MB) so the site can show it.
async function file(id, req, env, ctx, cors) {
  if (!/^[\w-]{10,200}$/.test(id)) return json({ error: 'bad_id' }, 400, cors);
  const cache = caches.default;
  const key = new Request(new URL(`/file/${id}`, req.url).toString());
  const hit = await cache.match(key);
  if (hit) return hit;
  const g = await (await fetch(`https://api.telegram.org/bot${env.BOT_TOKEN}/getFile?file_id=${encodeURIComponent(id)}`)).json();
  if (!g.ok) return json({ error: 'gone' }, 404, cors);
  const up = await fetch(`https://api.telegram.org/file/bot${env.BOT_TOKEN}/${g.result.file_path}`);
  const res = new Response(up.body, { headers: { ...cors, 'Content-Type': up.headers.get('Content-Type') || 'application/octet-stream', 'Cache-Control': 'public, max-age=86400' } });
  ctx.waitUntil(cache.put(key, res.clone()));
  return res;
}
