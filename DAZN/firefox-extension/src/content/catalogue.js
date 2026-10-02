(function (root) {
  'use strict';
  const MAX_START_DELTA_MS = 30 * 60 * 1000;
  function normalize(value) {
    return typeof value === 'string' ? value.normalize('NFKD').replace(/[\u0300-\u036f]/g, '').toLowerCase().replace(/[^a-z0-9]+/g, ' ').trim() : '';
  }
  function teamsInTitle(value) {
    if (typeof value !== 'string' || value.length > 500) return [];
    const title = ' ' + normalize(value) + ' ';
    return Object.entries(root.NHLUK.schedule.TEAMS).filter(([abbr, aliases]) =>
      [abbr, ...aliases].some(alias => title.includes(' ' + normalize(alias) + ' '))).map(([abbr]) => abbr);
  }
  function queries(input) {
    const game = root.NHLUK.schedule.validateGame(input);
    if (!game) return [];
    const teams = root.NHLUK.schedule.TEAMS, away = teams[game.awayTeam], home = teams[game.homeTeam];
    return [...new Set([away[0] + ' ' + home[0], home[0] + ' ' + away[0], away[1] + ' ' + home[1],
      game.awayTeam + ' ' + game.homeTeam, 'Buffalo Sabres', game.awayTeam === 'BUF' ? home[0] : away[0]])].slice(0, 6);
  }
  function extractTiles(raw) {
    if (!raw || typeof raw !== 'object') return null;
    // Search envelope verified directly with DAZN on 2026-10-02; Rail/Event uses Tiles.
    if (Array.isArray(raw.Tiles)) return raw.Tiles.length <= 500 ? raw.Tiles : null;
    if (!Array.isArray(raw.Results) || raw.Results.length > 20 || raw.Results.some(group => !group || !Array.isArray(group.Tiles))) return null;
    const tiles = raw.Results.flatMap(group => group.Tiles);
    return tiles.length <= 500 ? tiles : null;
  }
  function candidate(tile, game) {
    if (!tile || typeof tile !== 'object' || tile.IsLinear === true) return null;
    const pair = teamsInTitle(tile.Title);
    if (pair.length !== 2 || !pair.includes(game.homeTeam) || !pair.includes(game.awayTeam)) return null;
    const providerStart = root.NHLUK.schedule.iso(tile.Start);
    if (!providerStart || Math.abs(Date.parse(providerStart) - Date.parse(game.startUTC)) > MAX_START_DELTA_MS) return null;
    // Require the same scheduled Eastern game date, even around UTC midnight.
    const easternDate = new Intl.DateTimeFormat('en-CA', { timeZone: 'America/New_York', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date(providerStart));
    if (easternDate !== game.gameDate) return null;
    const kinds = { CatchUp: 'catchup', Replay: 'replay', VOD: 'vod', Live: 'live' };
    const kind = kinds[tile.Type];
    if (!kind || /\b(highlights?|condensed|recap|preview|interview|goals?|clips?)\b/i.test(tile.Title)) return null;
    // DAZN's published frontend explicitly routes home/EventId/AssetId to pendingSelection.
    const route = root.NHLUK.routeResolver.fromProviderIds(tile.EventId, tile.AssetId);
    if (!route) return null;
    const feedMatch = tile.Title.match(/\((home|away)(?: feed)?\)\s*$/i);
    const feed = feedMatch ? feedMatch[1].toLowerCase() : 'unknown';
    return root.NHLUK.routeResolver.sanitizeVariant({ gameId: game.id, gameDate: game.gameDate,
      startUTC: game.startUTC, homeTeam: game.homeTeam, awayTeam: game.awayTeam,
      eventId: tile.EventId, variantId: tile.AssetId, route, feed, kind });
  }
  function match(raw, input, options = {}) {
    const game = root.NHLUK.schedule.validateGame(input), tiles = extractTiles(raw);
    if (!game || !tiles) return { status: 'unsupported', variants: [] };
    const byId = new Map(), rejected = new Set();
    for (const tile of tiles) {
      const value = candidate(tile, game);
      if (!value) continue;
      const key = value.variantId;
      if (byId.has(key) && JSON.stringify(byId.get(key)) !== JSON.stringify(value)) rejected.add(key);
      else byId.set(key, value);
    }
    const variants = root.NHLUK.routeResolver.rank([...byId.values()].filter(v => !rejected.has(v.variantId)), options.feedPreference);
    return { status: variants.length ? 'matched' : 'none', variants };
  }
  const api = Object.freeze({ normalize, teamsInTitle, queries, match });
  root.NHLUK = root.NHLUK || {}; root.NHLUK.catalogue = api;
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
})(globalThis);
