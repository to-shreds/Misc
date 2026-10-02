(function (root) {
  'use strict';
  function opaque(value) { return typeof value === 'string' && /^[A-Za-z0-9_-]{1,160}$/.test(value) ? value : null; }
  function validateRoute(value) {
    if (typeof value !== 'string' || value.length > 500 || /[\\\s]/.test(value)) return null;
    try {
      const url = new URL(value, 'https://www.dazn.com');
      if (url.protocol !== 'https:' || !['www.dazn.com', 'dazn.com'].includes(url.hostname) || url.port || url.username || url.password || url.search || url.hash) return null;
      // DAZN's own web bundle declares /:regionParams/home/:eventId/:assetId?.
      // Require both identifiers to preserve the exact selected variant.
      if (!/^\/en-GB\/home\/[A-Za-z0-9_-]{1,160}\/[A-Za-z0-9_-]{1,160}\/?$/.test(url.pathname)) return null;
      return 'https://www.dazn.com' + url.pathname.replace(/\/$/, '');
    } catch (_) { return null; }
  }
  function fromProviderIds(eventId, assetId) {
    return opaque(eventId) && opaque(assetId) ? validateRoute('/en-GB/home/' + eventId + '/' + assetId) : null;
  }
  function sanitizeVariant(value) {
    if (!value || typeof value !== 'object') return null;
    const route = validateRoute(value.route), eventId = opaque(value.eventId), variantId = opaque(value.variantId);
    const schedule = root.NHLUK.schedule;
    const game = schedule && schedule.validateGame({ id: value.gameId, gameDate: value.gameDate, startUTC: value.startUTC, homeTeam: value.homeTeam, awayTeam: value.awayTeam });
    if (!game || !route || !eventId || !variantId || route !== fromProviderIds(eventId, variantId) || !['home', 'away', 'unknown'].includes(value.feed) || !['replay', 'vod', 'catchup', 'live'].includes(value.kind)) return null;
    return Object.freeze({ gameId: game.id, gameDate: game.gameDate, startUTC: game.startUTC, homeTeam: game.homeTeam,
      awayTeam: game.awayTeam, eventId, variantId, route, feed: value.feed, kind: value.kind });
  }
  function rank(variants, preference = 'unknown') {
    const feedPreference = ['home', 'away'].includes(preference) ? preference : 'unknown';
    const clean = Array.isArray(variants) ? variants.map(sanitizeVariant).filter(Boolean) : [];
    // Live never becomes a playback candidate in v1. Its beginning cannot be proven from catalogue data.
    return clean.filter(v => v.kind !== 'live').sort((a, b) => {
      const kind = { replay: 0, vod: 1, catchup: 2 };
      const feedA = feedPreference !== 'unknown' && a.feed === feedPreference ? 0 : 1;
      const feedB = feedPreference !== 'unknown' && b.feed === feedPreference ? 0 : 1;
      return feedA - feedB || kind[a.kind] - kind[b.kind] || a.variantId.localeCompare(b.variantId);
    });
  }
  function select(variants, preference = 'unknown') { return rank(variants, preference)[0] || null; }
  const api = Object.freeze({ opaque, validateRoute, fromProviderIds, sanitizeVariant, rank, select });
  root.NHLUK = root.NHLUK || {}; root.NHLUK.routeResolver = api;
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
})(globalThis);
