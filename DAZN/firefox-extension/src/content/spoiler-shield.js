(function (scope) {
  'use strict';
  const NS = scope.NHLUK = scope.NHLUK || {};
  function sourceIdentity(video) {
    return {
      source: video.currentSrc || '', attribute: video.getAttribute('src') || '',
      object: video.srcObject,
      children: [...video.querySelectorAll('source')].map(node => node.getAttribute('src') || '').join('\n')
    };
  }
  function sameSource(a, b) {
    return !!a && !!b && a.source === b.source && a.attribute === b.attribute &&
      a.object === b.object && a.children === b.children;
  }
  function create({ onInvalidate = () => {} } = {}) {
    const doc = scope.document;
    let root = doc.getElementById('nhluk-root');
    let selected = null;
    let identity = null;
    let destroyed = false;
    let covering = false;
    let invalidation = onInvalidate;
    const hooked = new WeakSet();
    function ensureRoot() {
      if (!doc.documentElement) return;
      if (!root) { root = doc.createElement('div'); root.id = 'nhluk-root'; }
      if (root.parentNode !== doc.documentElement) doc.documentElement.append(root);
      if (!doc.documentElement.hasAttribute('data-nhluk-playback')) doc.documentElement.setAttribute('data-nhluk-playback', 'covered');
    }
    function mute(video) {
      if (!video.muted) video.muted = true;
      if (!video.defaultMuted) video.defaultMuted = true;
    }
    function cover(reason = 'covered') {
      if (covering) return;
      covering = true;
      selected = null; identity = null;
      if (doc.documentElement) doc.documentElement.setAttribute('data-nhluk-playback', 'covered');
      root?.removeAttribute('data-playing');
      for (const node of doc.querySelectorAll('[data-nhluk-selected-video],[data-nhluk-video-parent]')) {
        node.removeAttribute('data-nhluk-selected-video');
        node.removeAttribute('data-nhluk-video-parent');
      }
      for (const media of doc.querySelectorAll('video,audio')) mute(media);
      covering = false;
    }
    function invalidate(reason) {
      const wasExposed = !!selected;
      cover(reason);
      if (wasExposed) invalidation(reason);
    }
    function strip(media) {
      media.controls = false;
      media.removeAttribute('controls');
      media.removeAttribute('poster');
      media.disablePictureInPicture = true;
      media.setAttribute('disablepictureinpicture', '');
      media.setAttribute('controlslist', 'nodownload nofullscreen noremoteplayback');
      media.disableRemotePlayback = true;
      if (media !== selected) mute(media);
      if (!hooked.has(media)) {
        hooked.add(media);
        for (const event of ['volumechange', 'play', 'playing']) media.addEventListener(event, () => {
          if (media !== selected) mute(media);
          if (selected && (!sameSource(identity, sourceIdentity(selected)) || !selected.isConnected)) invalidate('source-changed');
        }, true);
        for (const event of ['loadstart', 'emptied', 'abort', 'error']) media.addEventListener(event, () => {
          mute(media);
          if (media === selected) invalidate('source-changed');
        }, true);
      }
    }
    function neutralizePage() {
      if (doc.title !== 'NHL UK') doc.title = 'NHL UK';
      // No remote favicon fetch and no provider title in browser tabs/history UI.
      for (const link of doc.querySelectorAll('link[rel~="icon"],link[rel="apple-touch-icon"]')) link.remove();
    }
    function observe(records = []) {
      if (destroyed) return;
      ensureRoot();
      if (selected && (!selected.isConnected || !sameSource(identity, sourceIdentity(selected)) ||
        !selected.hasAttribute('data-nhluk-selected-video') ||
        doc.documentElement.getAttribute('data-nhluk-playback') !== 'exposed' ||
        !root?.hasAttribute('data-playing'))) invalidate('source-changed');
      // A second inserted player is ambiguous even if it has not started yet.
      if (selected && records.some(record => record.type === 'childList' &&
        [...record.addedNodes].some(node => node.nodeType === 1 &&
          (node.matches?.('video') || node.querySelector?.('video'))))) invalidate('media-replaced');
      for (const media of doc.querySelectorAll('video,audio')) strip(media);
      neutralizePage();
    }
    const observer = new scope.MutationObserver(observe);
    observer.observe(doc, { childList: true, subtree: true, attributes: true,
      attributeFilter: ['src', 'poster', 'controls', 'data-nhluk-selected-video', 'data-nhluk-playback', 'data-playing'],
      characterData: true });
    function navigation() { invalidate('navigation'); }
    function fullscreen() {
      if (doc.fullscreenElement && doc.fullscreenElement !== doc.documentElement) {
        invalidate('fullscreen-rejected');
        doc.exitFullscreen?.().catch(() => {});
      }
    }
    scope.addEventListener('nhluk:navigation', navigation, true);
    scope.addEventListener('nhluk:invalidate', navigation, true);
    scope.addEventListener('pagehide', navigation, true);
    scope.addEventListener('popstate', navigation, true);
    doc.addEventListener('fullscreenchange', fullscreen, true);
    ensureRoot(); observe(); cover();
    return {
      get root() { ensureRoot(); return root; },
      cover,
      reveal(video) {
        if (destroyed || !video || video.tagName !== 'VIDEO' || !video.isConnected ||
            doc.documentElement.getAttribute('data-nhluk-guard') !== 'ready') return false;
        cover();
        strip(video);
        identity = sourceIdentity(video);
        selected = video;
        for (let parent = video.parentElement; parent && parent !== doc.documentElement; parent = parent.parentElement) {
          parent.setAttribute('data-nhluk-video-parent', '');
        }
        video.setAttribute('data-nhluk-selected-video', '');
        root.setAttribute('data-playing', '');
        doc.documentElement.setAttribute('data-nhluk-playback', 'exposed');
        return true;
      },
      isExposed(video) { return selected === video && sameSource(identity, sourceIdentity(video)); },
      setInvalidationHandler(fn) { invalidation = typeof fn === 'function' ? fn : () => {}; },
      destroy() {
        cover(); destroyed = true; observer.disconnect();
        scope.removeEventListener('nhluk:navigation', navigation, true);
        scope.removeEventListener('nhluk:invalidate', navigation, true);
        scope.removeEventListener('pagehide', navigation, true);
        scope.removeEventListener('popstate', navigation, true);
        doc.removeEventListener('fullscreenchange', fullscreen, true);
      }
    };
  }
  NS.shield = { create, sourceIdentity, sameSource };
  if (typeof module !== 'undefined' && module.exports) module.exports = NS.shield;
})(globalThis);
