(function (scope) {
  'use strict';
  const NS = scope.NHLUK = scope.NHLUK || {};
  function install() {
    const session = scope.navigator?.mediaSession;
    if (!session) return { neutralize() {}, destroy() {} };
    function neutralize() {
      try {
        session.metadata = typeof scope.MediaMetadata === 'function' ?
          new scope.MediaMetadata({ title: 'NHL UK', artist: '', album: '', artwork: [] }) : null;
      } catch (_) { /* MAIN guard supplies the primary metadata lock. */ }
      try { session.setPositionState(); } catch (_) { /* Unsupported platform. */ }
      // MAIN owns action handlers. An isolated-world native method call could
      // bypass its wrappers and accidentally restore browser-default seeking.
    }
    neutralize();
    // This backstop is not a substitute for the synchronous MAIN-world guard.
    scope.addEventListener('nhluk:navigation', neutralize);
    return { neutralize, destroy() { neutralize(); scope.removeEventListener('nhluk:navigation', neutralize); } };
  }
  NS.mediaSession = { install, create: install };
  if (typeof module !== 'undefined' && module.exports) module.exports = NS.mediaSession;
})(globalThis);
