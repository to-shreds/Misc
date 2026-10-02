/* Background requests return allowlisted data only. No cookies, tokens or page text. */
(() => {
  'use strict';
  const N = globalThis.NHLUK;
  const cache = new Map();
  const safeSender = sender => sender.id === browser.runtime.id && sender.frameId === 0 &&
    /^https:\/\/(?:www\.)?dazn\.com\//.test(sender.url || '');
  const pendingKey = sender => `pending:${sender.tab.id}`;
  async function schedule(season) {
    const url = N.schedule.endpoint(season);
    const cached = cache.get(url);
    if (cached && Date.now() - cached.at < 300000) return cached.games;
    const response = await fetch(url, {credentials:'omit', redirect:'error', signal:AbortSignal.timeout(12000)});
    if (!response.ok) throw new Error('network');
    const text = await response.text();
    if (text.length > 2000000) throw new Error('size');
    const games = N.schedule.sanitize(JSON.parse(text));
    cache.set(url, {at:Date.now(),games});
    return games;
  }
  browser.runtime.onMessage.addListener(async (message, sender) => {
    if (!safeSender(sender) || !message || typeof message.type !== 'string') return undefined;
    try {
      if (message.type === 'SCHEDULE') return {ok:true,games:await schedule(message.season)};
      if (message.type === 'DISCOVER') {
        const game = N.schedule.validateGame(message.game);
        if (!game) return {ok:false,code:'invalid-request'};
        return {ok:true,result:await N.transport.discover(game,{fetchImpl:fetch})};
      }
      if (message.type === 'PENDING_SET') {
        const game = N.schedule.validateGame(message.game);
        const variant = N.routeResolver.sanitizeVariant(message.variant);
        if (!game || !variant || variant.gameId !== game.id ||
            !['replay','vod','catchup'].includes(variant.kind) ||
            !Number.isFinite(message.target) || message.target < 0 || message.target > 86400) return {ok:false,code:'invalid-request'};
        if (variant.startUTC !== game.startUTC || variant.homeTeam !== game.homeTeam || variant.awayTeam !== game.awayTeam) return {ok:false,code:'invalid-request'};
        await browser.storage.session.set({[pendingKey(sender)]:{game,variant,target:message.target,at:Date.now(),redirects:0}});
        return {ok:true};
      }
      if (message.type === 'PENDING_GET' || message.type === 'PENDING_REDIRECT') {
        const key = pendingKey(sender);
        const value = (await browser.storage.session.get(key))[key];
        if (!value || Date.now()-value.at > 3600000) {await browser.storage.session.remove(key);return {ok:true,pending:null};}
        if (message.type === 'PENDING_REDIRECT') {
          if (value.redirects >= 2) return {ok:false,code:'route-unavailable'};
          value.redirects += 1;
          await browser.storage.session.set({[key]:value});
        }
        return {ok:true,pending:value};
      }
      if (message.type === 'PENDING_CLEAR') {await browser.storage.session.remove(pendingKey(sender));return {ok:true};}
    } catch { return {ok:false,code:'request-unavailable'}; }
    return {ok:false,code:'invalid-request'};
  });
  browser.tabs.onRemoved.addListener(tabId => browser.storage.session.remove(`pending:${tabId}`).catch(()=>{}));
  browser.action.onClicked.addListener(() => browser.tabs.create({url:'https://www.dazn.com/'}));
})();
