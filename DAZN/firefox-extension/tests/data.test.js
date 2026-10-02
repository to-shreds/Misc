'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const schedule = require('../src/content/schedule.js');
const routes = require('../src/content/route-resolver.js');
const catalogue = require('../src/content/catalogue.js');
const transport = require('../src/content/transport.js');
const resume = require('../src/content/resume.js');
const diagnostics = require('../src/content/diagnostics.js');
const game = { id: '2025020001', gameDate: '2025-10-10', startUTC: '2025-10-10T23:00:00.000Z', homeTeam: 'BUF', awayTeam: 'CBJ' };
function rawGame(overrides = {}) { return { id: 2025020001, gameType: 2, gameDate: game.gameDate, startTimeUTC: game.startUTC, homeTeam: { abbrev: 'BUF', score: 'TAINT' }, awayTeam: { abbrev: 'CBJ', score: 'TAINT' }, ...overrides }; }
function tile(overrides = {}) { return { Title: 'Columbus @ Buffalo', Start: game.startUTC, Type: 'CatchUp', IsLinear: false,
  ArticleNavigateTo: 'https://www.dazn.com/en-GB/home/event_one/asset_one', EventId: 'event_one', AssetId: 'asset_one', Description: 'TAINT', Image: { Id: 'TAINT' }, ...overrides }; }
function variant(overrides = {}) { const result = { gameId: game.id, gameDate: game.gameDate, startUTC: game.startUTC, homeTeam: game.homeTeam,
  awayTeam: game.awayTeam, eventId: 'event_one', variantId: 'asset_one', feed: 'unknown', kind: 'catchup', ...overrides };
  if (!Object.hasOwn(overrides, 'route')) result.route = routes.fromProviderIds(result.eventId, result.variantId); return result; }
function response(raw, status = 200) { return { ok: status === 200, status, headers: { get: () => null }, text: async () => JSON.stringify(raw) }; }

test('schedule copies only neutral allowed fields and never accesses game state', () => {
  const raw = rawGame(); Object.defineProperty(raw, 'gameState', { get() { throw new Error('forbidden'); } });
  assert.deepEqual(schedule.sanitize({ games: [raw] }), [game]);
  assert.equal(JSON.stringify(schedule.sanitize({ games: [raw] })).includes('TAINT'), false);
});
test('playoff and unknown game types are omitted regardless of date', () => {
  assert.deepEqual(schedule.sanitize({ games: [rawGame({ gameType: 3 }), rawGame({ gameType: 4 }), rawGame({ gameType: undefined })] }), []);
});
test('malformed dates, IDs, teams and self-matchups fail closed', () => {
  for (const patch of [{ id: 'bad' }, { gameDate: '2025-02-30' }, { startUTC: '2025-10-10T25:00:00Z' }, { homeTeam: 'XXX' }, { awayTeam: 'BUF' }, { gameDate: '2025-10-01' }]) assert.equal(schedule.validateGame({ ...game, ...patch }), null);
});
test('unrelated schedules are omitted and duplicate contradictory IDs are excluded', () => {
  assert.deepEqual(schedule.sanitize({ games: [rawGame({ awayTeam: { abbrev: 'TOR' }, homeTeam: { abbrev: 'MTL' } })] }), []);
  assert.deepEqual(schedule.sanitize({ games: [rawGame(), rawGame({ startTimeUTC: '2025-10-10T23:30:00Z' }), rawGame()] }), []);
});
test('schedule endpoint prevents arbitrary hosts or dates', () => {
  assert.equal(schedule.endpoint('20252026'), 'https://api-web.nhle.com/v1/club-schedule-season/BUF/20252026');
  assert.throws(() => schedule.endpoint('20252027'));
  assert.throws(() => schedule.endpoint('https://evil.test'));
  assert.equal(schedule.seasonAt('2026-02-01'), '20252026');
  assert.equal(schedule.seasonAt('2026-10-01'), '20262027');
});
test('all current NHL team full names and abbreviations map without substring collisions', () => {
  for (const [abbr, aliases] of Object.entries(schedule.TEAMS)) {
    assert.deepEqual(catalogue.teamsInTitle(aliases[0]), [abbr]);
    assert.deepEqual(catalogue.teamsInTitle(abbr), [abbr]);
  }
  assert.deepEqual(catalogue.teamsInTitle('Edmonton at Boston'), ['BOS', 'EDM']);
  assert.deepEqual(catalogue.teamsInTitle('New York'), []);
  assert.deepEqual(catalogue.teamsInTitle('Bostonian'), []);
});
test('city, mascot, abbreviation and accent variants identify exact pairs', () => {
  for (const title of ['Columbus @ Buffalo', 'Blue Jackets v Sabres', 'CBJ @ BUF', 'Columbus Blue Jackets at Buffalo Sabres']) assert.equal(catalogue.match({ Tiles: [tile({ Title: title })] }, game).status, 'matched');
  assert.deepEqual(catalogue.teamsInTitle('Montréal Canadiens vs New York Rangers'), ['MTL', 'NYR']);
  assert.deepEqual(catalogue.teamsInTitle('New York Islanders vs Rangers'), ['NYI', 'NYR']);
});
test('search terms come only from bundled neutral aliases', () => {
  const terms = catalogue.queries({ ...game, title: 'TAINT', result: 'TAINT' });
  assert.ok(terms.length <= 6 && terms.length >= 4);
  assert.equal(terms.join(' ').includes('TAINT'), false);
});
test('wrong opponent, extra opponent, wrong date and remote start never match', () => {
  for (const patch of [{ Title: 'Columbus @ Boston' }, { Title: 'Columbus Buffalo Boston' }, { Start: '2025-10-09T23:00:00Z' }, { Start: '2025-10-10T22:00:00Z' }]) assert.equal(catalogue.match({ Tiles: [tile(patch)] }, game).status, 'none');
});
test('UTC midnight correctly matches the preceding North American game date', () => {
  const late = { ...game, startUTC: '2025-10-11T02:00:00.000Z' };
  assert.equal(catalogue.match({ Tiles: [tile({ Start: late.startUTC })] }, late).status, 'matched');
});
test('unknown payload shape, missing dates and invented routes cannot become candidates', () => {
  assert.equal(catalogue.match({ items: [tile()] }, game).status, 'unsupported');
  for (const patch of [{ Start: undefined }, { EventId: undefined, ArticleNavigateTo: 'Fixture', ArticleNavParams: 'ContentId:event_one' }, { Type: 'Unknown' }, { EventId: 'https://evil.test' }]) assert.equal(catalogue.match({ Tiles: [tile(patch)] }, game).status, 'none');
});
test('highlights, condensed, clips, live and linear candidates stay unavailable', () => {
  for (const patch of [{ Type: 'Highlights' }, { Type: 'Condensed' }, { Title: 'Columbus @ Buffalo highlights' }, { Type: 'Live' }, { IsLinear: true }]) assert.equal(catalogue.match({ Tiles: [tile(patch)] }, game).status, 'none');
});
test('catalogue outputs have exact neutral schema and no provider prose or total duration', () => {
  const result = catalogue.match({ Tiles: [tile({ duration: 9999, Score: 'TAINT', Title: 'Columbus @ Buffalo (Home feed)' })] }, game);
  assert.equal(result.status, 'matched');
  assert.equal(result.variants[0].feed, 'home');
  assert.deepEqual(Object.keys(result.variants[0]).sort(), ['gameId','gameDate','startUTC','homeTeam','awayTeam','eventId','variantId','route','feed','kind'].sort());
  assert.equal(JSON.stringify(result).includes('TAINT'), false);
});
test('same-game variants rank feed then full replay type deterministically', () => {
  const values = [variant({ variantId: 'vod', kind: 'vod', feed: 'home' }), variant({ variantId: 'replay', kind: 'replay', feed: 'away' }), variant({ variantId: 'live', kind: 'live' })];
  assert.deepEqual(routes.rank(values, 'home').map(v => v.variantId), ['vod', 'replay']);
  assert.equal(routes.select(values).variantId, 'replay');
});
test('duplicate variant identity with contradictory routes is excluded', () => {
  assert.equal(catalogue.match({ Tiles: [tile(), tile({ EventId: 'different' })] }, game).status, 'none');
});
test('route boundary rejects auth, tracking, javascript, credentials and unsupported origins', () => {
  for (const route of ['javascript:alert(1)', 'https://www.dazn.com@evil.test/en-GB/fixture/id', '/en-GB/home', '//evil.test/en-GB/fixture/id', '/en-GB/fixture/id?token=secret', '/en-GB/fixture/id#spoiler', '/en-GB/fixture/a%2fb', 'https://www.dazn.com:8443/en-GB/fixture/id', '/en-GB/signin']) assert.equal(routes.validateRoute(route), null);
});
test('resume persists only elapsed position and exact allowed identity', () => {
  const bookmark = resume.createBookmark(game, variant(), 123.456, '2025-10-12T00:00:00Z');
  assert.equal(bookmark.elapsedSeconds, 123.4);
  assert.equal(resume.sanitizeBookmark({ ...bookmark, duration: 999, title: 'TAINT' }).duration, undefined);
  assert.equal(resume.compatible(bookmark, variant()), true);
});
test('resume rejects route changes, different variants and uncertain feed equivalence', () => {
  const bookmark = resume.createBookmark(game, variant(), 123, '2025-10-12T00:00:00Z');
  for (const patch of [{ route: '/en-GB/home/another/asset_one' }, { variantId: 'different' }, { feed: 'home' }, { eventId: 'different' }, { gameId: '2025020002' }, { kind: 'live' }]) assert.equal(resume.compatible(bookmark, variant(patch)), false);
});
test('resume rejects nonfinite, negative, excessive and malformed positions', () => {
  for (const value of [-1, NaN, Infinity, '123', 86401]) assert.equal(resume.createBookmark(game, variant(), value), null);
  assert.equal(resume.createBookmark(game, variant({ homeTeam: 'TOR' }), 12), null);
  assert.equal(resume.createBookmark(game, variant({ kind: 'live' }), 12), null);
});
test('storage keys are scoped to exact games and data is sanitized on read/write', async () => {
  const values = {};
  const store = resume.createStore({ get: async k => ({ [k]: values[k] }), set: async v => Object.assign(values, v), remove: async k => { delete values[k]; } });
  const bookmark = resume.createBookmark(game, variant(), 12);
  await store.save({ ...bookmark, title: 'TAINT' });
  assert.deepEqual(await store.load(game.id), bookmark);
  assert.equal(JSON.stringify(values).includes('TAINT'), false);
  await store.remove(game.id); assert.equal(await store.load(game.id), null);
  await assert.rejects(store.load('../../secret'));
});
test('storage failures produce fixed errors without account data', async () => {
  const store = resume.createStore({ get: async () => { throw new Error('SECRET'); }, set: async () => {}, remove: async () => {} });
  await assert.rejects(store.load(game.id), /^Error: storage-unavailable$/);
});
test('diagnostics do not preserve arbitrary error, event or stage text', () => {
  const recorder = diagnostics.createRecorder();
  recorder.record('SECRET TITLE', 'https://secret.test/token', 'Winner SECRET');
  const json = recorder.export();
  assert.equal(/SECRET|token|Winner|https/.test(json), false);
  assert.equal(recorder.snapshot().stage, 'idle');
  assert.equal(recorder.snapshot().code, 'unknown');
  for (let i = 0; i < 70; i++) recorder.record('ready', 'none', 'ready-covered');
  assert.equal(recorder.snapshot().events.length, 50);
});
test('transport sanitizes before returning and sends no credentials', async () => {
  const calls = [];
  const result = await transport.discover(game, { fetchImpl: async (url, options) => { calls.push({ url, options }); return response({ Tiles: [tile()] }); } });
  assert.equal(result.status, 'matched');
  assert.equal(JSON.stringify(result).includes('TAINT'), false);
  assert.equal(calls.length, 1);
  assert.equal(calls[0].options.credentials, 'omit');
  assert.equal(new URL(calls[0].url).hostname, 'search.discovery.indazn.com');
});
test('transport retries only neutral unmatched queries with a strict bound', async () => {
  let calls = 0;
  const result = await transport.discover(game, { fetchImpl: async () => { calls++; return response({ Tiles: [] }); } });
  assert.equal(result.status, 'none');
  assert.equal(calls, catalogue.queries(game).length);
});
test('transport never leaks HTTP bodies, thrown messages or malformed responses', async () => {
  const denied = await transport.discover(game, { fetchImpl: async () => response({ SECRET: 'SECRET' }, 403) });
  assert.deepEqual(denied, { status: 'http', variants: [] });
  const failed = await transport.discover(game, { fetchImpl: async () => { throw new Error('SECRET'); } });
  assert.deepEqual(failed, { status: 'network', variants: [] });
  const unknown = await transport.discover(game, { fetchImpl: async () => response({ SECRET: 'SECRET' }) });
  assert.deepEqual(unknown, { status: 'unsupported', variants: [] });
});
test('observed official search Results/Tiles envelope supports same-game variants', () => {
  const result = catalogue.match({ Results: [{ Id: 'navigation', Tiles: [{ Title: 'Buffalo Sabres', Type: 'Navigation' }] },
    { Id: 'events', Tiles: [tile(), tile({ AssetId: 'asset_two', Title: 'Columbus @ Buffalo (Home feed)' })] }] }, game);
  assert.equal(result.status, 'matched'); assert.equal(result.variants.length, 2);
  assert.equal(result.variants[0].route, 'https://www.dazn.com/en-GB/home/event_one/asset_one');
});
test('provider IDs route only when path identity matches selected variant', () => {
  assert.equal(routes.fromProviderIds('event_one', 'asset_one'), 'https://www.dazn.com/en-GB/home/event_one/asset_one');
  assert.equal(routes.fromProviderIds('../bad', 'asset_one'), null);
  assert.equal(routes.sanitizeVariant(variant({ route: '/en-GB/home/other/asset_one' })), null);
  const bookmark = resume.createBookmark(game, variant(), 10);
  assert.equal(resume.sanitizeBookmark({ ...bookmark, route: '/en-GB/home/other/asset_one' }), null);
});
test('search envelope rejects malformed, oversized and unknown nesting', () => {
  for (const raw of [{ Results: [{ Tiles: null }] }, { Results: new Array(21).fill({ Tiles: [] }) }, { Tiles: new Array(501).fill(tile()) }]) assert.equal(catalogue.match(raw, game).status, 'unsupported');
});
test('transport uses provider-verified searchTerm and never forwards arbitrary query input', async () => {
  let requested;
  await transport.discover({ ...game, query: 'SECRET' }, { fetchImpl: async url => { requested = url; return response({ Tiles: [tile()] }); } });
  const url = new URL(requested);
  assert.equal(url.searchParams.get('searchTerm'), catalogue.queries(game)[0]);
  assert.equal(url.searchParams.has('searchParam'), false);
  assert.equal(requested.includes('SECRET'), false);
});
test('background transport unwraps fixed protocol and sanitizes variants again', async () => {
  const old = globalThis.browser;
  try {
    globalThis.browser = { runtime: { sendMessage: async message => {
      assert.equal(message.type, 'DISCOVER');
      return { ok: true, result: { status: 'matched', variants: [{ ...variant(), title: 'SECRET' }, variant({ gameId: '2025020002' })] } };
    } } };
    const result = await transport.discover(game, { transport: 'background' });
    assert.equal(result.variants.length, 1); assert.equal(JSON.stringify(result).includes('SECRET'), false);
  } finally { globalThis.browser = old; }
});
