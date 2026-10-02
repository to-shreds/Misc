(function (root) {
  'use strict';
  const PREFIX = 'nhluk.resume.';
  function sanitizeBookmark(value) {
    if (!value || typeof value !== 'object' || value.schema !== 1) return null;
    const resolver = root.NHLUK.routeResolver;
    const gameId = typeof value.gameId === 'number' ? String(value.gameId) : value.gameId;
    const route = resolver.validateRoute(value.route), eventId = resolver.opaque(value.eventId), variantId = resolver.opaque(value.variantId);
    if (typeof gameId !== 'string' || !/^\d{10}$/.test(gameId) || !route || !eventId || !variantId || route !== resolver.fromProviderIds(eventId, variantId) ||
      !['home', 'away', 'unknown'].includes(value.feed) || typeof value.elapsedSeconds !== 'number' ||
      !Number.isFinite(value.elapsedSeconds) || value.elapsedSeconds < 0 || value.elapsedSeconds > 86400 ||
      typeof value.lastWatched !== 'string' || !root.NHLUK.schedule.iso(value.lastWatched)) return null;
    return Object.freeze({ schema: 1, gameId, route, eventId, variantId, feed: value.feed,
      elapsedSeconds: Math.floor(value.elapsedSeconds * 10) / 10, lastWatched: root.NHLUK.schedule.iso(value.lastWatched) });
  }
  function createBookmark(game, variant, elapsedSeconds, now = new Date()) {
    const cleanGame = root.NHLUK.schedule.validateGame(game), cleanVariant = root.NHLUK.routeResolver.sanitizeVariant(variant);
    const timestamp = new Date(now);
    if (!cleanGame || !cleanVariant || cleanGame.id !== cleanVariant.gameId || cleanGame.startUTC !== cleanVariant.startUTC ||
      cleanGame.gameDate !== cleanVariant.gameDate || cleanGame.homeTeam !== cleanVariant.homeTeam || cleanGame.awayTeam !== cleanVariant.awayTeam ||
      cleanVariant.kind === 'live' || !Number.isFinite(timestamp.getTime())) return null;
    return sanitizeBookmark({ schema: 1, gameId: cleanGame.id, route: cleanVariant.route, eventId: cleanVariant.eventId,
      variantId: cleanVariant.variantId, feed: cleanVariant.feed, elapsedSeconds, lastWatched: timestamp.toISOString() });
  }
  function compatible(bookmark, variant) {
    const clean = sanitizeBookmark(bookmark), candidate = root.NHLUK.routeResolver.sanitizeVariant(variant);
    if (!clean || !candidate || clean.gameId !== candidate.gameId || candidate.kind === 'live') return false;
    // An event identifier and a feed do not prove equal pre-roll or media timelines.
    // Until a provider supplies an explicit timeline equivalence, keep exact identity.
    return clean.eventId === candidate.eventId && clean.variantId === candidate.variantId &&
      clean.route === candidate.route && clean.feed === candidate.feed;
  }
  function createStore(storage) {
    if (!storage || typeof storage.get !== 'function' || typeof storage.set !== 'function' || typeof storage.remove !== 'function') throw new Error('storage-unavailable');
    function key(id) {
      const value = String(id);
      if (!/^\d{10}$/.test(value)) throw new Error('resume-incompatible');
      return PREFIX + value;
    }
    return Object.freeze({
      async load(id) {
        const storageKey = key(id);
        try { const stored = await storage.get(storageKey); const result = sanitizeBookmark(stored[storageKey]); return result && result.gameId === String(id) ? result : null; }
        catch (_) { throw new Error('storage-unavailable'); }
      },
      async save(bookmark) {
        const clean = sanitizeBookmark(bookmark);
        if (!clean) throw new Error('resume-incompatible');
        try { await storage.set({ [key(clean.gameId)]: clean }); return clean; }
        catch (_) { throw new Error('storage-unavailable'); }
      },
      async remove(id) { try { await storage.remove(key(id)); } catch (_) { throw new Error('storage-unavailable'); } }
    });
  }
  const api = Object.freeze({ sanitizeBookmark, createBookmark, compatible, createStore });
  root.NHLUK = root.NHLUK || {}; root.NHLUK.resume = api;
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
})(globalThis);
