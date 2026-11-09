// What the Worker accepts and refuses of the usage statistics (CastBay Desktop and the Android app), with a stand-in
// for the database. Run it with: node web/worker/stats.test.mjs
import worker from './index.js';

const rows = [];
const env = {
  ASSETS: { fetch: async () => new Response('{"version":"1.10.0"}', { headers: { 'Content-Type': 'application/json' } }) },
  STATS: {
    prepare(sql) {
      return { sql, bind(...args) { return { sql, args, run: async () => { rows.push({ sql, args }); } }; } };
    },
    async batch(list) { list.forEach((l) => rows.push({ sql: l.sql, args: l.args })); },
  },
};

const day = new Date().toISOString().slice(0, 10);
const good = {
  platform: 'desktop', id: '0b8d6a52-1c3e-4a4b-9d52-6e1a0b8f2c47', day, app: '0.1.4', os: 'linux', osVersion: 'ubuntu 26.04',
  arch: 'x86_64', session: 'wayland', screen: '1080p', lang: 'zh', encoders: ['nvenc', 'x264'],
  counts: { 'cast.screen': 2, 'length.1to10m': 1, 'encoder.nvenc': 2, 'fps.30': 2, 'bitrate.6to11': 2, 'sound.on': 2, 'fail.encoder': 1 },
  settings: { encoder: 'auto', language: 'auto', updates: true, errorReports: false },
};

async function post(path, body) {
  const response = await worker.fetch(new Request('https://x/' + path, { method: 'POST', body: typeof body === 'string' ? body : JSON.stringify(body) }), env);
  return response.status;
}

let failed = 0;
const check = (name, actual, expected) => { if (actual !== expected) { failed++; console.log('FAIL', name, actual, '!=', expected); } else console.log('ok  ', name); };

check('a good desktop summary', await post('api/stats', good), 200);
check('it went to desktop_daily', rows.at(-1).sql.includes('desktop_daily'), true);
check('the encoders kept as JSON', rows.at(-1).args[9], '["nvenc","x264"]');
const bad = (name, change) => post('api/stats', { ...good, ...change }).then((s) => check(name, s, 400));
await bad('unknown counter', { counts: { 'cast.screen': 1, 'something.else': 1 } });
await bad('a count of zero', { counts: { 'cast.screen': 0 } });
await bad('unknown os', { os: 'macos' });
await bad('unknown encoder', { encoders: ['qsv'] });
await bad('unknown setting', { settings: { password: 'x' } });
await bad('a name in a setting', { settings: { encoder: 'My PC!' } });
await bad('bad id', { id: 'not-a-uuid' });
await bad('a day too old', { day: '2020-01-01' });
await bad('bad version', { app: '1.0;drop' });
await bad('bad os version', { osVersion: "x'; --" });
check('not JSON', await post('api/stats', 'nope'), 400);
const before = rows.length;
check('the delete covers both tables', await post('api/stats/delete', { id: good.id }), 200);
check('two statements', rows.length - before, 2);
check('both tables named', rows.slice(before).map((r) => r.sql.includes('desktop_daily')).sort().join(), 'false,true');
// An Android summary still goes to its own table.
const android = { id: good.id, day, app: '1.8.8', android: '14', sdk: 34, maker: 'Sony', model: 'XR', screen: '1080p', device: 'tv', touch: false, lang: 'en', counts: { 'cast.airplay.mirror': 1 }, settings: { access: 'open' } };
check('an Android summary', await post('api/stats', android), 200);
check('into daily', /INTO daily /.test(rows.at(-1).sql), true);
// A language with a region (Traditional Chinese, a future pt-BR) is a setting value too.
check('a zh-TW language setting', await post('api/stats', { ...android, settings: { access: 'open', language: 'zh-TW' } }), 200);
check('kept as zh-TW', rows.at(-1).args.at(-2).includes('"language":"zh-TW"'), true);

// The once-a-day update check: answered from the assets as before, and counted per version.
async function latest(headers, { method = 'GET', environment = env } = {}) {
  const pending = [];
  const response = await worker.fetch(new Request('https://x/latest.json', { method, headers }), environment, { waitUntil: (p) => pending.push(p) });
  await Promise.all(pending);
  return response;
}
const agent = (version) => ({ 'User-Agent': `CastBay/${version} (https://github.com/weenas/castbay)` });
let before2 = rows.length;
let r = await latest(agent('1.10.0'));
check('latest.json answers', r.status, 200);
check('latest.json is the asset itself', await r.text(), '{"version":"1.10.0"}');
check('one count written', rows.length - before2, 1);
check('into update_checks', rows.at(-1).sql.includes('update_checks'), true);
check('for the version, directly', rows.at(-1).args.join(), '1.10.0,web');
before2 = rows.length; await latest(agent('1.10.1-beta.2'));
check('a beta keeps its version', rows.at(-1).args.join(), '1.10.1-beta.2,web');
await latest(agent('1.9.0-dev+14e6f06'));
check('a test build counts as other', rows.at(-1).args.join(), 'other,web');
await latest({ 'User-Agent': 'Mozilla/5.0 (Windows NT 10.0) Chrome/130' });
check('a browser counts as other', rows.at(-1).args.join(), 'other,web');
await latest({});
check('no user agent counts as other', rows.at(-1).args.join(), 'other,web');
await latest(agent('1.10.0.9'));
check('a made-up version counts as other', rows.at(-1).args.join(), 'other,web');
await latest(agent('12345.1.1'));
check('a huge version counts as other', rows.at(-1).args.join(), 'other,web');
await latest(agent('1.10.0;DROP TABLE'));
check('only the version is kept, bound as a value', rows.at(-1).args.join(), '1.10.0,web');
await latest({ ...agent('1.10.0'), 'CF-Connecting-IP': '45.195.196.12' }, { environment: { ...env, RELAY_IPS: '45.195.196.12' } });
check('through the relay', rows.at(-1).args.join(), '1.10.0,relay');
before2 = rows.length;
r = await latest(agent('1.10.0'), { method: 'HEAD' });
check('HEAD is answered', r.status, 200);
check('HEAD is not counted', rows.length, before2);
const broken = { ...env, STATS: { prepare() { throw new Error('no such table: update_checks'); } } };
r = await latest(agent('1.10.0'), { environment: broken });
check('a failing count does not fail the check', r.status, 200);
check('and the answer is the same', await r.text(), '{"version":"1.10.0"}');
console.log(failed ? `${failed} FAILED` : 'all passed');
process.exit(failed ? 1 : 0);
