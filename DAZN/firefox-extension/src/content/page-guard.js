/* Runs in MAIN at document_start. Page-visible, no credentials or provider data.
 * This protects against ordinary player races, not a hostile page defeating an
 * extension. Actual Firefox/DAZN and device media-surface acceptance is required.
 */
(function () {
  'use strict';
  const doc = document;
  const nativeMuted = Object.getOwnPropertyDescriptor(HTMLMediaElement.prototype, 'muted');
  const nativePlay = HTMLMediaElement.prototype.play;
  const nativePause = HTMLMediaElement.prototype.pause;
  const nativeSetAttribute = Element.prototype.setAttribute;
  const nativeRemoveAttribute = Element.prototype.removeAttribute;
  let ready = true;
  function selected(media) {
    return doc.documentElement?.getAttribute('data-nhluk-playback') === 'exposed' &&
      media.tagName === 'VIDEO' && media.hasAttribute('data-nhluk-selected-video') &&
      doc.getElementById('nhluk-root')?.hasAttribute('data-playing');
  }
  function forceMuted(media) {
    try { nativeMuted.set.call(media, true); } catch (_) { ready = false; }
  }
  function close(event = 'nhluk:invalidate') {
    const html = doc.documentElement;
    html?.removeAttribute('data-nhluk-auth');
    html?.setAttribute('data-nhluk-playback', 'covered');
    doc.getElementById('nhluk-root')?.removeAttribute('data-playing');
    for (const media of doc.querySelectorAll('video,audio')) forceMuted(media);
    window.dispatchEvent(new Event(event));
  }
  function patch(target, property, descriptor) {
    try { Object.defineProperty(target, property, { ...descriptor, configurable: false }); }
    catch (_) { ready = false; }
  }
  if (!nativeMuted?.get || !nativeMuted?.set) ready = false;
  else patch(HTMLMediaElement.prototype, 'muted', {
    get() { return nativeMuted.get.call(this); },
    set(value) { nativeMuted.set.call(this, selected(this) ? !!value : true); },
    enumerable: nativeMuted.enumerable
  });
  const nativeControls = Object.getOwnPropertyDescriptor(HTMLMediaElement.prototype, 'controls');
  if (nativeControls?.get && nativeControls?.set) patch(HTMLMediaElement.prototype, 'controls', {
    get() { return nativeControls.get.call(this); },
    set() { nativeControls.set.call(this, false); },
    enumerable: nativeControls.enumerable
  });
  patch(HTMLMediaElement.prototype, 'play', {
    value: function (...args) {
      if (!selected(this)) forceMuted(this);
      return nativePlay.apply(this, args);
    }, writable: false
  });
  // During preparation, provider seeks are unrestricted behind the cover. Once
  // exposed, any page-origin seek must close synchronously before native seeking.
  const nativeTime = Object.getOwnPropertyDescriptor(HTMLMediaElement.prototype, 'currentTime');
  if (nativeTime?.get && nativeTime?.set) patch(HTMLMediaElement.prototype, 'currentTime', {
    get() { return nativeTime.get.call(this); },
    set(value) { if (selected(this)) close(); nativeTime.set.call(this, value); },
    enumerable: nativeTime.enumerable
  });
  if (HTMLMediaElement.prototype.fastSeek) {
    const nativeFastSeek = HTMLMediaElement.prototype.fastSeek;
    patch(HTMLMediaElement.prototype, 'fastSeek', {
      value: function (...args) { if (selected(this)) close(); return nativeFastSeek.apply(this, args); }, writable: false
    });
  }
  // Cover before page code replaces an exposed source, not at a later load event.
  for (const [prototype, name] of [[HTMLMediaElement.prototype, 'src'], [HTMLMediaElement.prototype, 'srcObject'],
    [HTMLSourceElement.prototype, 'src']]) {
    const descriptor = Object.getOwnPropertyDescriptor(prototype, name);
    if (!descriptor?.set || !descriptor?.get) continue;
    patch(prototype, name, {
      get() { return descriptor.get.call(this); },
      set(value) { if (selected(this) || this.parentElement?.hasAttribute('data-nhluk-selected-video')) close(); descriptor.set.call(this, value); },
      enumerable: descriptor.enumerable
    });
  }
  const nativeLoad = HTMLMediaElement.prototype.load;
  patch(HTMLMediaElement.prototype, 'load', {
    value: function (...args) { if (selected(this)) close(); forceMuted(this); return nativeLoad.apply(this, args); }, writable: false
  });
  function disableEmbedded(node) {
    if (!node || node.nodeType && node.nodeType !== 1 && node.nodeType !== 11) return;
    const targets = ['IFRAME', 'OBJECT', 'EMBED'].includes(node.tagName) ? [node] : [];
    if (node.querySelectorAll) targets.push(...node.querySelectorAll('iframe,object,embed'));
    for (const frame of targets) {
      if (frame.tagName === 'IFRAME') {
        if (frame.getAttribute('sandbox') !== '') nativeSetAttribute.call(frame, 'sandbox', '');
        const policy = "autoplay 'none'; encrypted-media 'none'; fullscreen 'none'; picture-in-picture 'none'";
        if (frame.getAttribute('allow') !== policy) nativeSetAttribute.call(frame, 'allow', policy);
        if (frame.hasAttribute('srcdoc')) nativeRemoveAttribute.call(frame, 'srcdoc');
        if (frame.getAttribute('src') !== 'about:blank') nativeSetAttribute.call(frame, 'src', 'about:blank');
      } else {
        const key = frame.tagName === 'OBJECT' ? 'data' : 'src';
        if (frame.hasAttribute(key)) nativeRemoveAttribute.call(frame, key);
        if (frame.getAttribute('type') !== 'text/plain') nativeSetAttribute.call(frame, 'type', 'text/plain');
      }
    }
  }
  patch(Element.prototype, 'setAttribute', {
    value: function (name, value) {
      const key = String(name).toLowerCase();
      if (['IFRAME', 'OBJECT', 'EMBED'].includes(this.tagName) && ['src', 'srcdoc', 'data', 'type', 'sandbox', 'allow'].includes(key)) {
        disableEmbedded(this); return;
      }
      if (this instanceof HTMLMediaElement && (key === 'controls' || key === 'poster')) return;
      if (key === 'src' && (selected(this) || this.parentElement?.hasAttribute('data-nhluk-selected-video'))) close();
      return nativeSetAttribute.call(this, name, value);
    }, writable: false
  });
  // v1 deliberately does not support embedded players or authentication widgets.
  // Invisible cross-origin frames cannot be controlled by the parent media guard.
  for (const constructorName of ['HTMLIFrameElement', 'HTMLObjectElement', 'HTMLEmbedElement']) {
    const constructor = globalThis[constructorName];
    if (!constructor) continue;
    for (const name of ['src', 'srcdoc', 'data']) {
      const descriptor = Object.getOwnPropertyDescriptor(constructor.prototype, name);
      if (!descriptor?.set || !descriptor?.get) continue;
      patch(constructor.prototype, name, {
        get() { return descriptor.get.call(this); },
        set() { disableEmbedded(this); }, enumerable: descriptor.enumerable
      });
    }
  }
  if (typeof Node !== 'undefined') {
    for (const method of ['appendChild', 'insertBefore', 'replaceChild']) {
      const original = Node.prototype[method];
      if (!original) continue;
      patch(Node.prototype, method, {
        value: function (node, ...args) { disableEmbedded(node); return original.call(this, node, ...args); }, writable: false
      });
    }
    for (const method of ['append', 'prepend', 'replaceChildren']) {
      const original = Element.prototype[method];
      if (!original) continue;
      patch(Element.prototype, method, {
        value: function (...nodes) { for (const node of nodes) disableEmbedded(node); return original.apply(this, nodes); }, writable: false
      });
    }
  }
  for (const method of ['pushState', 'replaceState']) {
    const original = History.prototype[method];
    patch(History.prototype, method, {
      value: function (...args) {
        close('nhluk:navigation');
        const result = original.apply(this, args);
        // A second notification after URL update permits allowlisted login refresh.
        window.dispatchEvent(new Event('nhluk:navigation'));
        return result;
      }, writable: false
    });
  }
  window.addEventListener('popstate', () => close('nhluk:navigation'), true);
  window.addEventListener('pagehide', () => close('nhluk:navigation'), true);
  for (const type of ['play', 'playing', 'volumechange', 'loadedmetadata']) doc.addEventListener(type, event => {
    if (event.target instanceof HTMLMediaElement && !selected(event.target)) forceMuted(event.target);
  }, true);
  for (const type of ['loadstart', 'emptied', 'abort']) doc.addEventListener(type, event => {
    if (event.target instanceof HTMLMediaElement) {
      if (selected(event.target)) close();
      forceMuted(event.target);
    }
  }, true);
  if (HTMLVideoElement.prototype.requestPictureInPicture) patch(HTMLVideoElement.prototype, 'requestPictureInPicture', {
    value: function () { close(); return Promise.reject(new DOMException('Unavailable in protected playback', 'NotAllowedError')); }, writable: false
  });
  for (const method of ['requestFullscreen', 'mozRequestFullScreen']) {
    const original = Element.prototype[method];
    if (!original) continue;
    patch(Element.prototype, method, {
      value: function (...args) {
        if (this === doc.documentElement) return original.apply(this, args);
        close(); return Promise.reject(new DOMException('Protected fullscreen only', 'NotAllowedError'));
      }, writable: false
    });
  }
  const session = navigator.mediaSession;
  if (session) {
    try {
      const metadataDescriptor = Object.getOwnPropertyDescriptor(Object.getPrototypeOf(session), 'metadata');
      const neutral = new MediaMetadata({ title: 'NHL UK', artist: '', album: '', artwork: [] });
      metadataDescriptor.set.call(session, neutral);
      patch(session, 'metadata', {
        get() { return neutral; },
        set() { metadataDescriptor.set.call(session, neutral); }
      });
      // Lock the neutral object as well, so session.metadata.title cannot leak.
      for (const name of ['title', 'artist', 'album', 'artwork']) {
        const descriptor = Object.getOwnPropertyDescriptor(Object.getPrototypeOf(neutral), name);
        const value = name === 'title' ? 'NHL UK' : name === 'artwork' ? [] : '';
        if (descriptor?.set) patch(neutral, name, { get() { return value; }, set() { descriptor.set.call(neutral, value); } });
      }
      const clearPosition = session.setPositionState?.bind(session);
      if (clearPosition) { clearPosition(); patch(session, 'setPositionState', { value() { clearPosition(); }, writable: false }); }
      const nativeAction = session.setActionHandler.bind(session);
      const blockAction = () => {
        close();
        for (const media of doc.querySelectorAll('video,audio')) nativePause.call(media);
      };
      const actions = ['play', 'pause', 'seekbackward', 'seekforward', 'seekto', 'previoustrack', 'nexttrack', 'stop', 'skipad'];
      for (const action of actions) { try { nativeAction(action, blockAction); } catch (_) { /* Unsupported action. */ } }
      patch(session, 'setActionHandler', {
        value(action) { if (actions.includes(action)) { try { nativeAction(action, blockAction); } catch (_) { /* Unsupported action. */ } } }, writable: false
      });
    } catch (_) { ready = false; }
  }
  function initialize() {
    if (!doc.documentElement) return;
    doc.documentElement.setAttribute('data-nhluk-guard', ready ? 'ready' : 'blocked');
    if (!doc.documentElement.hasAttribute('data-nhluk-playback')) doc.documentElement.setAttribute('data-nhluk-playback', 'covered');
    if (doc.title !== 'NHL UK') doc.title = 'NHL UK';
    for (const media of doc.querySelectorAll('video,audio')) if (!selected(media)) forceMuted(media);
    for (const frame of doc.querySelectorAll('iframe,object,embed')) disableEmbedded(frame);
    for (const link of doc.querySelectorAll('link[rel~="icon"],link[rel="apple-touch-icon"]')) link.remove();
  }
  const observer = new MutationObserver(initialize);
  observer.observe(doc, { childList: true, subtree: true, attributes: true,
    attributeFilter: ['src', 'srcdoc', 'data', 'allow', 'sandbox', 'type'] });
  initialize();
})();
