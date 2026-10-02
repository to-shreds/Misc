(function (root) {
  'use strict';
  // No error messages, URLs, titles, timings, account data or provider text enter this recorder.
  const STAGES = Object.freeze(['idle', 'shield', 'schedule', 'auth', 'catalogue', 'route', 'media', 'timeline', 'positioning', 'settling', 'ready', 'playback', 'resume', 'closed']);
  const CODES = Object.freeze(['none', 'unknown', 'schedule-unavailable', 'schedule-invalid', 'auth-required', 'auth-unavailable',
    'catalogue-timeout', 'catalogue-http', 'catalogue-network', 'catalogue-unsupported', 'catalogue-unavailable',
    'not-matched', 'ambiguous', 'route-unavailable', 'media-unavailable', 'multiple-media', 'timeline-unavailable',
    'target-unavailable', 'source-changed', 'position-unverified', 'frame-unverified', 'preparation-timeout',
    'playback-unverified', 'playback-error', 'storage-unavailable', 'resume-incompatible', 'cancelled', 'shield-failed']);
  const EVENTS = Object.freeze(['covered', 'muted', 'schedule-loaded', 'auth-opened', 'auth-returned', 'catalogue-requested',
    'catalogue-matched', 'route-selected', 'media-attached', 'media-replaced', 'timeline-ready', 'target-requested',
    'provider-seek', 'target-reasserted', 'frame-verified', 'ready-covered', 'play-requested', 'play-verified',
    'paused', 'resume-saved', 'source-changed', 'failed-covered', 'closed', 'unknown']);
  function createRecorder() {
    let stage = 'idle', code = 'none';
    const events = [];
    function record(nextStage, nextCode = 'none', event) {
      if (STAGES.includes(nextStage)) stage = nextStage;
      code = CODES.includes(nextCode) ? nextCode : 'unknown';
      if (event !== undefined) {
        events.push(EVENTS.includes(event) ? event : 'unknown');
        if (events.length > 50) events.shift();
      }
      return snapshot();
    }
    function snapshot() { return { schema: 1, build: '0.1.0', stage, code, events: events.slice() }; }
    return Object.freeze({ record, snapshot, export: () => JSON.stringify(snapshot(), null, 2), clear() { stage = 'idle'; code = 'none'; events.length = 0; } });
  }
  const api = Object.freeze({ STAGES, CODES, EVENTS, createRecorder });
  root.NHLUK = root.NHLUK || {}; root.NHLUK.diagnostics = api;
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
})(globalThis);
