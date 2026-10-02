(function (root) {
  'use strict';
  // Official NHL data is tainted until this boundary has copied neutral fields.
  const TEAMS = Object.freeze({
    ANA: ['Anaheim Ducks', 'Anaheim', 'Ducks'], BOS: ['Boston Bruins', 'Boston', 'Bruins'],
    BUF: ['Buffalo Sabres', 'Buffalo', 'Sabres'], CAR: ['Carolina Hurricanes', 'Carolina', 'Hurricanes'],
    CBJ: ['Columbus Blue Jackets', 'Columbus', 'Blue Jackets'], CGY: ['Calgary Flames', 'Calgary', 'Flames'],
    CHI: ['Chicago Blackhawks', 'Chicago', 'Blackhawks'], COL: ['Colorado Avalanche', 'Colorado', 'Avalanche'],
    DAL: ['Dallas Stars', 'Dallas', 'Stars'], DET: ['Detroit Red Wings', 'Detroit', 'Red Wings'],
    EDM: ['Edmonton Oilers', 'Edmonton', 'Oilers'], FLA: ['Florida Panthers', 'Florida', 'Panthers'],
    LAK: ['Los Angeles Kings', 'Los Angeles', 'Kings', 'LA Kings'],
    MIN: ['Minnesota Wild', 'Minnesota', 'Wild'], MTL: ['Montreal Canadiens', 'Montreal', 'Canadiens', 'Habs'],
    NJD: ['New Jersey Devils', 'New Jersey', 'Devils', 'NJ Devils'],
    NSH: ['Nashville Predators', 'Nashville', 'Predators'],
    NYI: ['New York Islanders', 'Islanders', 'NY Islanders'],
    NYR: ['New York Rangers', 'Rangers', 'NY Rangers'],
    OTT: ['Ottawa Senators', 'Ottawa', 'Senators'], PHI: ['Philadelphia Flyers', 'Philadelphia', 'Flyers'],
    PIT: ['Pittsburgh Penguins', 'Pittsburgh', 'Penguins'], SEA: ['Seattle Kraken', 'Seattle', 'Kraken'],
    SJS: ['San Jose Sharks', 'San Jose', 'Sharks', 'SJ Sharks'],
    STL: ['St Louis Blues', 'St Louis', 'Blues', 'Saint Louis Blues', 'Saint Louis'],
    TBL: ['Tampa Bay Lightning', 'Tampa Bay', 'Lightning', 'TB Lightning'],
    TOR: ['Toronto Maple Leafs', 'Toronto', 'Maple Leafs'],
    UTA: ['Utah Mammoth', 'Utah', 'Mammoth', 'Utah Hockey Club'],
    VAN: ['Vancouver Canucks', 'Vancouver', 'Canucks'],
    VGK: ['Vegas Golden Knights', 'Vegas', 'Golden Knights', 'Las Vegas'],
    WPG: ['Winnipeg Jets', 'Winnipeg', 'Jets'], WSH: ['Washington Capitals', 'Washington', 'Capitals'],
    ARI: ['Arizona Coyotes', 'Arizona', 'Coyotes', 'Phoenix Coyotes']
  });
  for (const aliases of Object.values(TEAMS)) Object.freeze(aliases);
  function team(value) { return typeof value === 'string' && Object.hasOwn(TEAMS, value) ? value : null; }
  function iso(value) {
    if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,3})?Z$/.test(value)) return null;
    const time = Date.parse(value);
    return Number.isFinite(time) && new Date(time).toISOString().slice(0,19) === value.slice(0,19) ? new Date(time).toISOString() : null;
  }
  function date(value) {
    return typeof value === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(value) && iso(value + 'T00:00:00Z') ? value : null;
  }
  function validateGame(value) {
    if (!value || typeof value !== 'object') return null;
    const id = typeof value.id === 'number' ? String(value.id) : value.id;
    const startUTC = iso(value.startUTC);
    const gameDate = date(value.gameDate), homeTeam = team(value.homeTeam), awayTeam = team(value.awayTeam);
    if (typeof id !== 'string' || !/^\d{10}$/.test(id) || !startUTC || !gameDate || !homeTeam || !awayTeam || homeTeam === awayTeam) return null;
    // Game dates may be the preceding date in North America, never an arbitrary date.
    const delta = Date.parse(startUTC) - Date.parse(gameDate + 'T00:00:00Z');
    if (delta < 0 || delta >= 36 * 3600000) return null;
    return Object.freeze({ id, startUTC, gameDate, homeTeam, awayTeam });
  }
  function sanitize(raw, options = {}) {
    if (!raw || !Array.isArray(raw.games)) return [];
    const selectedTeam = team(options.team || 'BUF');
    if (!selectedTeam) return [];
    const unique = new Map();
    for (const game of raw.games.slice(0, 500)) {
      // Playoff opponents are hidden until a series-watched feature is implemented.
      if (!game || ![1, 2].includes(game.gameType)) continue;
      const clean = validateGame({ id: game.id, startUTC: game.startTimeUTC, gameDate: game.gameDate,
        homeTeam: game.homeTeam && game.homeTeam.abbrev, awayTeam: game.awayTeam && game.awayTeam.abbrev });
      if (!clean || ![clean.homeTeam, clean.awayTeam].includes(selectedTeam)) continue;
      const previous = unique.get(clean.id);
      if (previous && JSON.stringify(previous) !== JSON.stringify(clean)) unique.set(clean.id, null);
      else if (!unique.has(clean.id)) unique.set(clean.id, clean);
    }
    return [...unique.values()].filter(Boolean).sort((a, b) => a.startUTC.localeCompare(b.startUTC));
  }
  function endpoint(season) {
    const value = String(season);
    if (!/^20\d{2}20\d{2}$/.test(value) || Number(value.slice(4)) !== Number(value.slice(0,4)) + 1) throw new Error('schedule-invalid-season');
    return 'https://api-web.nhle.com/v1/club-schedule-season/BUF/' + value;
  }
  function seasonAt(now = new Date()) {
    const dateValue = new Date(now);
    if (!Number.isFinite(dateValue.getTime())) throw new Error('schedule-invalid-date');
    const first = dateValue.getUTCFullYear() - (dateValue.getUTCMonth() < 6 ? 1 : 0);
    return String(first) + String(first + 1);
  }
  const api = Object.freeze({ TEAMS, team, iso, date, validateGame, sanitize, endpoint, seasonAt });
  root.NHLUK = root.NHLUK || {}; root.NHLUK.schedule = api;
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
})(globalThis);
