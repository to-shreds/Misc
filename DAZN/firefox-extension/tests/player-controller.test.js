"use strict";
const test = require("node:test");
const assert = require("node:assert/strict");
const { PlayerController, STATES, contains } = require("../src/content/player-controller.js");

class Clock {
  constructor() { this.time = 0; this.id = 0; this.jobs = new Map(); }
  now = () => this.time;
  setTimeout = (callback, delay) => { const id = ++this.id; this.jobs.set(id, { callback, at: this.time + delay }); return id; };
  clearTimeout = id => this.jobs.delete(id);
  advance(ms) {
    const end = this.time + ms;
    let next;
    while ((next = [...this.jobs.entries()].filter(([, job]) => job.at <= end).sort((a, b) => a[1].at - b[1].at)[0])) {
      this.time = next[1].at;
      this.jobs.delete(next[0]);
      next[1].callback();
    }
    this.time = end;
  }
}
class Media extends EventTarget {
  constructor(clock) {
    super();
    this.clock = clock;
    this.currentSrc = "blob:fixture";
    this.srcObject = null;
    this.readyState = 2;
    this.rangeValues = [[0, 1000]];
    this.position = 600;
    this.paused = true;
    this.seeking = false;
    this.ended = false;
    this.playbackRate = 1;
    this.muted = false;
    this.frames = new Map();
    this.frameID = 0;
    this.seekDelay = 1;
    this.playResult = undefined;
    this.seekWrites = [];
  }
  get currentTime() { return this.position; }
  set currentTime(value) {
    this.seekWrites.push(value);
    this.position = value;
    this.seeking = true;
    this.emit("seeking");
    this.clock.setTimeout(() => { this.seeking = false; this.emit("seeked"); }, this.seekDelay);
  }
  get duration() { throw new Error("Total runtime must not be read"); }
  get seekable() { return { length: this.rangeValues.length, start: i => this.rangeValues[i][0], end: i => this.rangeValues[i][1] }; }
  getAttribute() { return null; }
  removeAttribute() {}
  play() { this.paused = false; this.emit("play"); this.emit("playing"); return this.playResult; }
  pause() { if (!this.paused) { this.paused = true; this.emit("pause"); } }
  requestVideoFrameCallback(callback) { const id = ++this.frameID; this.frames.set(id, callback); return id; }
  cancelVideoFrameCallback(id) { this.frames.delete(id); }
  emit(event) { this.dispatchEvent(new Event(event)); }
  frame(time) {
    this.clock.advance(40);
    this.position = time;
    const callbacks = [...this.frames.values()];
    this.frames.clear();
    for (const callback of callbacks) callback(this.clock.now(), { mediaTime: time, presentedFrames: this.frameID });
  }
}
function fixture(config) {
  const clock = new Clock(), video = new Media(clock), status = [], progress = [];
  const state = { candidates: [video], covered: true, revealed: 0 };
  const shield = { cover: () => { state.covered = true; }, reveal: () => { state.covered = false; state.revealed++; return true; } };
  const player = new PlayerController({ getMedia: () => state.candidates, shield, clock, config, onState: (...args) => status.push(args), onProgress: value => progress.push(value) });
  return { clock, video, status, progress, state, shield, player };
}
async function ready(f, target = 0) {
  const promise = f.player.prepare({ target });
  f.clock.advance(2);
  f.video.frame(target);
  f.video.frame(target + 0.04);
  f.video.frame(target + 0.08);
  assert.equal(await promise, true);
  assert.equal(f.player.state, STATES.READY_COVERED);
}
async function playing(f) {
  await ready(f);
  const promise = f.player.play();
  f.video.frame(0.12);
  f.video.frame(0.16);
  f.video.frame(0.20);
  assert.equal(await promise, true);
  assert.equal(f.player.state, STATES.PLAYING_EXPOSED);
}

test("preparation stays covered and muted until a separate explicit decoded Play", async () => {
  const f = fixture();
  await ready(f);
  assert.equal(f.state.covered, true);
  assert.equal(f.video.muted, true);
  assert.equal(f.video.paused, true);
  const promise = f.player.play();
  f.video.frame(0.12);
  f.video.frame(0.16);
  assert.equal(f.state.covered, true);
  assert.equal(f.video.muted, true);
  f.video.frame(0.20);
  assert.equal(await promise, true);
  assert.equal(f.state.revealed, 1);
  assert.equal(f.video.muted, false);
  assert.equal(f.video.controls, false);
  assert.equal(f.video.disablePictureInPicture, true);
});
test("a timer alone cannot establish readiness without decoded frames", async () => {
  const f = fixture({ prepareTimeout: 500 });
  const promise = f.player.prepare();
  f.clock.advance(600);
  assert.equal(await promise, false);
  assert.equal(f.player.state, STATES.FAILED_COVERED);
  assert.equal(f.state.revealed, 0);
});
test("delayed insertion and delayed metadata are acquired behind cover", async () => {
  const f = fixture();
  f.state.candidates = [];
  const promise = f.player.prepare();
  f.clock.advance(250);
  assert.equal(f.player.state, STATES.WAITING_FOR_MEDIA);
  f.video.readyState = 0;
  f.state.candidates = [f.video];
  f.clock.advance(100);
  assert.equal(f.player.state, STATES.WAITING_FOR_TIMELINE);
  f.video.readyState = 2;
  f.clock.advance(52);
  [0, 0.04, 0.08].forEach(time => f.video.frame(time));
  assert.equal(await promise, true);
  assert.equal(f.state.covered, true);
});
test("provider initialization seeks are reasserted within the same hidden transaction", async () => {
  const f = fixture();
  const promise = f.player.prepare();
  const generation = f.player.generation;
  for (const time of [400, 500, 550, 620]) {
    f.video.currentTime = time;
    f.clock.advance(3);
    assert.equal(f.video.currentTime, 0);
    assert.equal(f.player.generation, generation);
    assert.equal(f.state.covered, true);
  }
  [0, 0.04, 0.08].forEach(time => f.video.frame(time));
  assert.equal(await promise, true);
  assert.equal(f.state.revealed, 0);
});
test("unstable provider initialization has a finite seek budget", async () => {
  const f = fixture({ maxSeekAttempts: 3 });
  const promise = f.player.prepare();
  for (let i = 0; i < 5; i++) { f.video.currentTime = 600; f.clock.advance(3); }
  assert.equal(await promise, false);
  assert.equal(f.player.reason, "POSITION_UNSTABLE");
  assert.equal(f.state.covered, true);
});
test("DVR earliest range is never assumed to be the requested beginning", async () => {
  const f = fixture({ prepareTimeout: 250 });
  f.video.rangeValues = [[300, 1000]];
  const promise = f.player.prepare({ target: 0 });
  f.clock.advance(300);
  assert.equal(await promise, false);
  assert.equal(f.video.seekWrites.length, 0);
  assert.equal(f.state.revealed, 0);
});
test("a gap in seekable ranges does not count as an available saved target", async () => {
  const f = fixture({ prepareTimeout: 250 });
  f.video.rangeValues = [[0, 20], [40, 1000]];
  const promise = f.player.prepare({ target: 30 });
  f.clock.advance(300);
  assert.equal(await promise, false);
  assert.equal(contains([{ start: 0, end: 20 }, { start: 40, end: 1000 }], 30), false);
});
test("DVR beginning disappearing during frame verification fails covered", async () => {
  const f = fixture();
  const promise = f.player.prepare();
  f.clock.advance(2);
  f.video.frame(0);
  f.video.rangeValues = [[15, 1000]];
  f.video.frame(0.04);
  assert.equal(await promise, false);
  assert.equal(f.player.reason, "TARGET_UNAVAILABLE");
  assert.equal(f.video.muted, true);
});
test("duplicate and far-target decoded frames are insufficient", async () => {
  const f = fixture({ prepareTimeout: 400 });
  const promise = f.player.prepare();
  f.clock.advance(2);
  [0, 0, 0, 0, 0].forEach(time => f.video.frame(time));
  assert.notEqual(f.player.state, STATES.READY_COVERED);
  f.video.frame(300);
  f.clock.advance(400);
  assert.equal(await promise, false);
  assert.equal(f.state.revealed, 0);
});
test("multiple media elements are ambiguous and never guessed", async () => {
  const f = fixture();
  const other = new Media(f.clock);
  f.state.candidates.push(other);
  assert.equal(await f.player.prepare(), false);
  assert.equal(f.player.reason, "MEDIA_AMBIGUOUS");
  assert.equal(other.muted, true);
});
test("missing decoded frame API fails covered", async () => {
  const f = fixture();
  f.video.requestVideoFrameCallback = undefined;
  assert.equal(await f.player.prepare(), false);
  assert.equal(f.player.reason, "FRAME_VERIFICATION_UNAVAILABLE");
});
test("replacement media starts a fresh generation and ignores old callbacks", async () => {
  const f = fixture();
  const promise = f.player.prepare();
  const staleCallback = [...f.video.frames.values()][0];
  const previousGeneration = f.player.generation;
  const replacement = new Media(f.clock);
  replacement.currentSrc = "blob:replacement";
  f.state.candidates = [replacement];
  f.clock.advance(52);
  assert.ok(f.player.generation > previousGeneration);
  staleCallback(0, { mediaTime: 0 });
  assert.equal(f.player.frames, 0);
  [0, 0.04, 0.08].forEach(time => replacement.frame(time));
  assert.equal(await promise, true);
  assert.equal(f.player.video, replacement);
  assert.equal(f.state.revealed, 0);
});
test("same-element source replacement discards earlier ready evidence", async () => {
  const f = fixture();
  const promise = f.player.prepare();
  f.clock.advance(2);
  f.video.frame(0);
  const generation = f.player.generation;
  f.video.currentSrc = "blob:full-replay";
  f.video.emit("loadedmetadata");
  assert.ok(f.player.generation > generation);
  f.clock.advance(2);
  [0, 0.04, 0.08].forEach(time => f.video.frame(time));
  assert.equal(await promise, true);
});
test("new src attribute invalidates old currentSrc before asynchronous load events", async () => {
  const f = fixture();
  let attribute = "/fixture-a.webm";
  f.video.getAttribute = name => name === "src" ? attribute : null;
  const promise = f.player.prepare();
  f.clock.advance(2);
  f.video.frame(0);
  const generation = f.player.generation;
  attribute = "/fixture-b.webm";
  f.video.readyState = 0;
  f.video.rangeValues = [];
  f.clock.advance(51);
  assert.ok(f.player.generation > generation);
  assert.equal(f.player.state, STATES.WAITING_FOR_TIMELINE);
  f.video.currentSrc = "https://fixture/fixture-b.webm";
  f.video.readyState = 2;
  f.video.rangeValues = [[0, 1000]];
  f.clock.advance(52);
  [0, 0.04, 0.08].forEach(time => f.video.frame(time));
  assert.equal(await promise, true);
});
test("metadata reset from reloading the same source starts a fresh transaction", async () => {
  const f = fixture();
  const promise = f.player.prepare();
  f.clock.advance(2);
  f.video.frame(0);
  const generation = f.player.generation;
  f.video.readyState = 0;
  f.video.rangeValues = [];
  f.video.emit("timeupdate");
  assert.ok(f.player.generation > generation);
  assert.equal(f.player.state, STATES.WAITING_FOR_TIMELINE);
  f.video.readyState = 2;
  f.video.rangeValues = [[0, 1000]];
  f.clock.advance(52);
  [0, 0.04, 0.08].forEach(time => f.video.frame(time));
  assert.equal(await promise, true);
});
test("a backwards timeline replacement starts fresh while hidden", async () => {
  const f = fixture();
  const promise = f.player.prepare();
  f.clock.advance(2);
  f.video.frame(0);
  const generation = f.player.generation;
  f.video.rangeValues = [[0, 300]];
  f.clock.advance(52);
  assert.ok(f.player.generation > generation);
  f.clock.advance(2);
  [0, 0.04, 0.08].forEach(time => f.video.frame(time));
  assert.equal(await promise, true);
});
test("source replacement after reveal immediately covers and mutes", async () => {
  const f = fixture();
  await playing(f);
  f.video.currentSrc = "blob:next-item";
  f.video.emit("loadstart");
  assert.equal(f.player.state, STATES.FAILED_COVERED);
  assert.equal(f.state.covered, true);
  assert.equal(f.video.muted, true);
  assert.equal(f.video.paused, true);
});
test("source reassigning during explicit Play never reveals", async () => {
  const f = fixture();
  await ready(f);
  const promise = f.player.play();
  f.video.frame(0.12);
  f.video.currentSrc = "blob:changed";
  f.video.frame(0.16);
  assert.equal(await promise, false);
  assert.equal(f.state.revealed, 0);
});
test("explicit Play revalidates the target before calling play", async () => {
  const f = fixture();
  await ready(f);
  f.video.position = 500;
  assert.equal(await f.player.play(), false);
  assert.equal(f.state.covered, true);
  assert.equal(f.video.paused, true);
});
test("a provider seek after reveal fails synchronously at seeking", async () => {
  const f = fixture();
  await playing(f);
  f.video.currentTime = 300;
  assert.equal(f.state.covered, true);
  assert.equal(f.video.muted, true);
  assert.equal(f.player.reason, "POSITION_CHANGED");
});
test("silent position discontinuity after reveal fails before another authorized frame", async () => {
  const f = fixture();
  await playing(f);
  f.video.frame(300);
  assert.equal(f.state.covered, true);
  assert.equal(f.player.reason, "POSITION_CHANGED");
});
test("pause does not grant provider autoplay permission", async () => {
  const f = fixture();
  await playing(f);
  assert.equal(f.player.pause(), true);
  assert.equal(f.player.state, STATES.PAUSED_EXPOSED);
  f.video.play();
  assert.equal(f.player.state, STATES.FAILED_COVERED);
  assert.equal(f.state.covered, true);
  assert.equal(f.video.muted, true);
});
test("explicit resume from pause uses a new covered frame verification", async () => {
  const f = fixture();
  await playing(f);
  f.player.pause();
  const promise = f.player.play();
  assert.equal(f.state.covered, true);
  [0.24, 0.28, 0.32].forEach(time => f.video.frame(time));
  assert.equal(await promise, true);
  assert.equal(f.state.revealed, 2);
});
test("ended content is covered and cannot autoplay next content", async () => {
  const f = fixture();
  await playing(f);
  f.video.ended = true;
  f.video.emit("ended");
  assert.equal(f.state.covered, true);
  assert.equal(f.video.muted, true);
  assert.equal(f.player.reason, "PLAYBACK_STOPPED");
});
test("skip and restart return to READY_COVERED without automatically exposing", async () => {
  const f = fixture();
  await playing(f);
  const promise = f.player.skip(30);
  assert.equal(f.state.covered, true);
  f.clock.advance(2);
  [30.2, 30.24, 30.28].forEach(time => f.video.frame(time));
  assert.equal(await promise, true);
  assert.equal(f.player.state, STATES.READY_COVERED);
  assert.equal(f.state.revealed, 1);
  const restart = f.player.restart();
  f.clock.advance(2);
  [0, 0.04, 0.08].forEach(time => f.video.frame(time));
  assert.equal(await restart, true);
  assert.equal(f.player.getSnapshot().elapsed, 0);
});
test("old generation promise rejection cannot fail a replacement transaction", async () => {
  const f = fixture();
  let reject;
  f.video.playResult = new Promise((_, fail) => { reject = fail; });
  const first = f.player.prepare();
  const replacement = new Media(f.clock);
  f.state.candidates = [replacement];
  const second = f.player.prepare();
  assert.equal(await first, false);
  reject(new Error("old provider rejection"));
  await Promise.resolve();
  f.clock.advance(2);
  [0, 0.04, 0.08].forEach(time => replacement.frame(time));
  assert.equal(await second, true);
});
test("close cancels old callbacks and leaves no timer or unmuted media", async () => {
  const f = fixture();
  const promise = f.player.prepare();
  const callback = [...f.video.frames.values()][0];
  f.player.close();
  callback(0, { mediaTime: 0 });
  f.clock.advance(20000);
  assert.equal(await promise, false);
  assert.equal(f.player.state, STATES.IDLE);
  assert.equal(f.state.revealed, 0);
  assert.equal(f.video.muted, true);
  assert.equal(f.clock.jobs.size, 0);
});
test("shield refusal never restores audible media", async () => {
  const f = fixture();
  await ready(f);
  f.shield.reveal = () => false;
  const promise = f.player.play();
  [0.12, 0.16, 0.20].forEach(time => f.video.frame(time));
  assert.equal(await promise, false);
  assert.equal(f.player.reason, "PROTECTION_UNAVAILABLE");
  assert.equal(f.video.muted, true);
  assert.equal(f.video.paused, true);
});
test("shield exception stays covered and settles the pending Play", async () => {
  const f = fixture();
  await ready(f);
  f.shield.reveal = () => { throw new Error("fixture shield exception"); };
  const promise = f.player.play();
  [0.12, 0.16, 0.20].forEach(time => f.video.frame(time));
  assert.equal(await promise, false);
  assert.equal(f.state.covered, true);
  assert.equal(f.video.muted, true);
});
test("shield invalidation immediately fails covered through the public hook", async () => {
  const f = fixture();
  await playing(f);
  f.player.invalidate();
  assert.equal(f.player.state, STATES.FAILED_COVERED);
  assert.equal(f.player.reason, "PROTECTION_UNAVAILABLE");
  assert.equal(f.state.covered, true);
  assert.equal(f.video.muted, true);
});
