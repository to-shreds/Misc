const test = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const path = require('node:path');
const auth = require('../src/content/auth.js');
const { sourceIdentity, sameSource } = require('../src/content/spoiler-shield.js');

function guardFixture() {
  const events = [];
  class Node {
    appendChild(child) { events.push(['insert', child.getAttribute('src')]); return child; }
    insertBefore(child) { return this.appendChild(child); }
    replaceChild(child) { return this.appendChild(child); }
  }
  class Element extends Node {
    constructor(tagName = 'DIV') { super(); this.nodeType = 1; this.tagName = tagName; this.attributes = new Map(); this.parentElement = null; }
    setAttribute(name, value) { this.attributes.set(name, String(value)); }
    getAttribute(name) { return this.attributes.get(name) ?? null; }
    hasAttribute(name) { return this.attributes.has(name); }
    removeAttribute(name) { this.attributes.delete(name); }
    requestFullscreen() { return Promise.resolve(); }
    append(...children) { for (const child of children) this.appendChild(child); }
  }
  class Frame extends Element {
    constructor() { super('IFRAME'); }
    get src() { return this.getAttribute('src'); }
    set src(value) { this.setAttribute('src', value); }
    get srcdoc() { return this.getAttribute('srcdoc'); }
    set srcdoc(value) { this.setAttribute('srcdoc', value); }
  }
  class Media extends Element {
    constructor(tag = 'VIDEO') { super(tag); this._muted = false; this._src = ''; this._object = null; }
    get muted() { return this._muted; }
    set muted(value) { this._muted = value; }
    get src() { return this._src; }
    set src(value) { events.push(['source', html.getAttribute('data-nhluk-playback')]); this._src = value; }
    get srcObject() { return this._object; }
    set srcObject(value) { this._object = value; }
    get currentTime() { return this._time || 0; }
    set currentTime(value) { events.push(['seek', html.getAttribute('data-nhluk-playback')]); this._time = value; }
    fastSeek(value) { this.currentTime = value; }
    play() { events.push(['play', this.muted]); return Promise.resolve(); }
    pause() { this.paused = true; }
    load() { events.push(['load', html.getAttribute('data-nhluk-playback')]); }
  }
  class Video extends Media { requestPictureInPicture() { return Promise.resolve(); } }
  class Source extends Element {
    get src() { return this._src; }
    set src(value) { this._src = value; }
  }
  class History {
    pushState() { events.push(['history', html.getAttribute('data-nhluk-auth'), html.getAttribute('data-nhluk-playback')]); }
    replaceState() { this.pushState(); }
  }
  class MediaMetadata {
    constructor(data) { Object.assign(this, data); }
    get title() { return this._title; } set title(value) { this._title = value; }
    get artist() { return this._artist; } set artist(value) { this._artist = value; }
    get album() { return this._album; } set album(value) { this._album = value; }
    get artwork() { return this._artwork; } set artwork(value) { this._artwork = value; }
  }
  class Session {
    get metadata() { return this._metadata; } set metadata(value) { this._metadata = value; }
    setPositionState(value) { this.position = value; }
    setActionHandler(action, handler) { this.actions[action] = handler; }
  }
  const session = new Session(); session.actions = {};
  const html = new Element('HTML'); const root = new Element();
  const media = new Video(); const audio = new Media('AUDIO');
  const doc = {
    documentElement: html, title: 'PROVIDER TEXT',
    getElementById: id => id === 'nhluk-root' ? root : null,
    querySelectorAll: selector => selector === 'video,audio' ? [media, audio] : [],
    addEventListener() {}
  };
  const window = { addEventListener() {}, dispatchEvent(event) { events.push(['event', event.type]); } };
  const context = { document: doc, window, Element, Node, HTMLIFrameElement: Frame, HTMLMediaElement: Media,
    HTMLVideoElement: Video, HTMLSourceElement: Source, History, navigator: { mediaSession: session },
    MediaMetadata, MutationObserver: class { observe() {} }, Event: class { constructor(type) { this.type = type; } },
    DOMException, Promise };
  vm.runInNewContext(fs.readFileSync(path.join(__dirname, '../src/content/page-guard.js'), 'utf8'), context);
  function expose() { html.setAttribute('data-nhluk-playback', 'exposed'); root.setAttribute('data-playing', ''); media.setAttribute('data-nhluk-selected-video', ''); }
  return { html, root, media, audio, session, events, expose, history: new History(), doc, newFrame: () => new Frame() };
}

test('auth recognizes only explicit DAZN locale sign-in routes', () => {
  for (const url of ['https://www.dazn.com/en-GB/signin', 'https://www.dazn.com/en-gb/signin/']) assert.equal(auth.isAuthURL(url), true);
  for (const url of ['https://www.dazn.com/', 'https://www.dazn.com/en-GB/home?signin',
    'https://www.dazn.com/en-GB/signin/results', 'https://www.dazn.com.evil.test/en-GB/signin',
    'http://www.dazn.com/en-GB/signin', 'https://user@www.dazn.com/en-GB/signin',
    'https://www.dazn.com:444/en-GB/signin', 'https://evil.test/?next=https://www.dazn.com/en-GB/signin']) assert.equal(auth.isAuthURL(url), false);
});

test('hidden provider unmute/play attempts stay muted synchronously', async () => {
  const f = guardFixture();
  assert.equal(f.html.getAttribute('data-nhluk-guard'), 'ready');
  f.media.muted = false; f.audio.muted = false;
  await f.media.play(); await f.audio.play();
  assert.equal(f.media.muted, true); assert.equal(f.audio.muted, true);
  assert.deepEqual(f.events.filter(e => e[0] === 'play'), [['play', true], ['play', true]]);
});

test('selected video can restore audio but unrelated audio cannot', () => {
  const f = guardFixture(); f.expose();
  f.media.muted = false; f.audio.muted = false;
  assert.equal(f.media.muted, false); assert.equal(f.audio.muted, true);
});

test('source assignment covers and mutes before native source write', () => {
  const f = guardFixture(); f.expose(); f.media.muted = false;
  f.media.src = 'https://example.invalid/new-source';
  assert.equal(f.html.getAttribute('data-nhluk-playback'), 'covered');
  assert.equal(f.media.muted, true);
  assert.deepEqual(f.events.find(e => e[0] === 'source'), ['source', 'covered']);
});

test('setAttribute source and load invalidate exposed video', () => {
  const f = guardFixture(); f.expose(); f.media.setAttribute('src', 'changed');
  assert.equal(f.html.getAttribute('data-nhluk-playback'), 'covered');
  f.expose(); f.media.load();
  assert.deepEqual(f.events.find(e => e[0] === 'load'), ['load', 'covered']);
});

test('SPA history closes auth before native navigation runs', () => {
  const f = guardFixture(); f.expose(); f.html.setAttribute('data-nhluk-auth', 'open');
  f.history.pushState({}, '', '/en-GB/home');
  assert.deepEqual(f.events.find(e => e[0] === 'history'), ['history', null, 'covered']);
});

test('provider Media Session writes cannot replace neutral metadata or position', () => {
  const f = guardFixture();
  f.session.metadata = { title: 'PROVIDER RESULT', artwork: ['image'] };
  f.session.metadata.title = 'PROVIDER RESULT';
  f.session.metadata.artwork = ['https://example.invalid/image'];
  f.session.setPositionState({ duration: 9999, position: 500 });
  assert.equal(f.session.metadata.title, 'NHL UK');
  assert.equal(f.session.metadata.artwork.length, 0);
  assert.equal(f.session.position, undefined);
});

test('system actions cover and pause instead of allowing unverified seek/play', () => {
  const f = guardFixture(); f.expose();
  f.session.setActionHandler('seekto', () => { throw Error('provider seek called'); });
  f.session.actions.seekto({ seekTime: 500 });
  assert.equal(f.media.paused, true);
  assert.equal(f.html.getAttribute('data-nhluk-playback'), 'covered');
});

test('PiP and provider fullscreen rejected while protected document fullscreen works', async () => {
  const f = guardFixture(); f.expose();
  await assert.rejects(f.media.requestPictureInPicture(), { name: 'NotAllowedError' });
  await assert.rejects(f.media.requestFullscreen(), { name: 'NotAllowedError' });
  await f.html.requestFullscreen();
});

test('media identity tracks element source, MediaStream and source children', () => {
  let source = 'blob:first'; let child = 'first'; const object = {};
  const video = { currentSrc: source, srcObject: object, getAttribute: () => source,
    querySelectorAll: () => [{ getAttribute: () => child }] };
  const first = sourceIdentity(video);
  assert.equal(sameSource(first, sourceIdentity(video)), true);
  child = 'second'; assert.equal(sameSource(first, sourceIdentity(video)), false);
  child = 'first'; video.srcObject = {}; assert.equal(sameSource(first, sourceIdentity(video)), false);
});

test('embedded frame source/srcdoc are blocked before insertion', () => {
  const f = guardFixture(); const frame = f.newFrame();
  frame.src = 'https://other-provider.invalid/player';
  frame.srcdoc = '<video autoplay></video>';
  f.html.appendChild(frame);
  assert.equal(frame.src, 'about:blank');
  assert.equal(frame.srcdoc, null);
  assert.equal(frame.getAttribute('sandbox'), '');
  assert.match(frame.getAttribute('allow'), /autoplay 'none'/);
  assert.deepEqual(f.events.find(e => e[0] === 'insert'), ['insert', 'about:blank']);
});

test('prepopulated iframe inserted by append gets neutralized before native insertion', () => {
  const f = guardFixture(); const frame = f.newFrame();
  // Model a parser-created node whose attributes predate page JavaScript hooks.
  frame.attributes.set('src', 'https://other-provider.invalid/player');
  f.html.append(frame);
  assert.equal(frame.src, 'about:blank');
  assert.equal(frame.getAttribute('sandbox'), '');
  assert.deepEqual(f.events.find(e => e[0] === 'insert'), ['insert', 'about:blank']);
});

test('exposed provider seeks cover before native write, hidden preparation seeks remain allowed', () => {
  const f = guardFixture();
  f.media.currentTime = 10;
  assert.equal(f.media.currentTime, 10);
  assert.equal(f.events.some(e => e[0] === 'event' && e[1] === 'nhluk:invalidate'), false);
  f.expose(); f.media.muted = false; f.media.currentTime = 100;
  assert.equal(f.media.currentTime, 100);
  assert.equal(f.media.muted, true);
  assert.deepEqual(f.events.filter(e => e[0] === 'seek'), [['seek', 'covered'], ['seek', 'covered']]);
  f.expose(); f.media.fastSeek(200);
  assert.equal(f.html.getAttribute('data-nhluk-playback'), 'covered');
});
