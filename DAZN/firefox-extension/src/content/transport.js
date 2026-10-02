(function (root) {
  'use strict';
  const STATUSES = Object.freeze(['matched', 'none', 'unsupported', 'ambiguous', 'timeout', 'http', 'network']);
  function searchURL(query) {
    const url = new URL('https://search.discovery.indazn.com/v1/search');
    url.search = new URLSearchParams({ searchTerm: query, country: 'gb', brand: 'dazn' }).toString();
    return url.href;
  }
  async function discover(input, options = {}) {
    const game = root.NHLUK.schedule.validateGame(input);
    if (!game) return { status: 'unsupported', variants: [] };
    if (options.transport === 'background' && !options.fetchImpl) {
      try {
        const envelope = await root.browser.runtime.sendMessage({ type: 'DISCOVER', game });
        const response = envelope && envelope.ok === true && envelope.result;
        if (!response || !STATUSES.includes(response.status) || !Array.isArray(response.variants)) return { status: 'unsupported', variants: [] };
        const variants = root.NHLUK.routeResolver.rank(response.variants.filter(v => v && v.gameId === game.id &&
          v.startUTC === game.startUTC && v.gameDate === game.gameDate && v.homeTeam === game.homeTeam && v.awayTeam === game.awayTeam), options.feedPreference);
        return { status: variants.length ? 'matched' : response.status === 'matched' ? 'none' : response.status, variants };
      } catch (_) { return { status: 'network', variants: [] }; }
    }
    const fetchImpl = options.fetchImpl || root.fetch.bind(root);
    let lastStatus = 'none';
    for (const query of root.NHLUK.catalogue.queries(game)) {
      const controller = new AbortController();
      const timer = setTimeout(() => controller.abort(), 8000);
      try {
        const response = await fetchImpl(searchURL(query), { method: 'GET', credentials: 'omit', redirect: 'error',
          headers: { Accept: 'application/json' }, signal: controller.signal });
        if (!response.ok) return { status: 'http', variants: [] };
        if (Number(response.headers && response.headers.get('content-length')) > 2 * 1024 * 1024) return { status: 'unsupported', variants: [] };
        const text = await response.text();
        if (text.length > 2 * 1024 * 1024) return { status: 'unsupported', variants: [] };
        let raw;
        try { raw = JSON.parse(text); } catch (_) { return { status: 'unsupported', variants: [] }; }
        // Raw provider data never crosses this function's boundary.
        const result = root.NHLUK.catalogue.match(raw, game, options);
        if (result.status === 'matched' || result.status === 'unsupported') return result;
        lastStatus = result.status;
      } catch (_) {
        return { status: controller.signal.aborted ? 'timeout' : 'network', variants: [] };
      } finally { clearTimeout(timer); }
    }
    return { status: lastStatus, variants: [] };
  }
  const api = Object.freeze({ STATUSES, discover });
  root.NHLUK = root.NHLUK || {}; root.NHLUK.transport = api;
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
})(globalThis);
