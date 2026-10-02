(function (root) {
  "use strict";
  const STATES = Object.freeze({
    IDLE: "IDLE", WAITING_FOR_MEDIA: "WAITING_FOR_MEDIA", WAITING_FOR_TIMELINE: "WAITING_FOR_TIMELINE",
    POSITIONING: "POSITIONING", SETTLING: "SETTLING", READY_COVERED: "READY_COVERED",
    PLAYING_EXPOSED: "PLAYING_EXPOSED", PAUSED_EXPOSED: "PAUSED_EXPOSED", FAILED_COVERED: "FAILED_COVERED"
  });
  const exposed = state => state === STATES.PLAYING_EXPOSED || state === STATES.PAUSED_EXPOSED;
  const DEFAULTS = Object.freeze({
    prepareTimeout: 15000, playTimeout: 6000, pollInterval: 50, tolerance: 0.45,
    rangeEpsilon: 0.05, stableFrames: 3, maxSeekAttempts: 8, maxSourceRestarts: 3, driftTolerance: 0.7
  });
  function rangesOf(video) {
    const result = [];
    try {
      for (let i = 0; i < video.seekable.length; i++) {
        const start = video.seekable.start(i), end = video.seekable.end(i);
        if (Number.isFinite(start) && Number.isFinite(end) && end >= start) result.push({ start, end });
      }
    } catch (_) { return []; }
    return result;
  }
  function contains(ranges, target, epsilon = 0.05) {
    return ranges.some(range => target >= range.start - epsilon && target <= range.end && range.end > range.start);
  }
  function sourceOf(video) {
    // currentSrc may retain the previous resource while a new src is loading.
    // Preserve both identities so a synchronous provider replacement is visible.
    return {
      src: video.currentSrc || "", attribute: video.getAttribute?.("src") || "", object: video.srcObject || null,
      children: Array.from(video.querySelectorAll?.("source") || []).map(node => node.getAttribute("src") || "").join("\n")
    };
  }
  function sameSource(a, b) {
    return !!a && !!b && a.src === b.src && a.attribute === b.attribute && a.children === b.children && a.object === b.object;
  }

  /**
   * Covered preparation is a bounded transaction. Timer checks only acquire media,
   * police invariants, and enforce deadlines; decoded frames are the readiness evidence.
   * This controls HTML media, not DRM or DAZN's entitlement/session internals.
   */
  class PlayerController {
    constructor({ getMedia, shield, onState = () => {}, onProgress = () => {}, clock, config = {} } = {}) {
      if (typeof getMedia !== "function" || !shield || typeof shield.cover !== "function" || typeof shield.reveal !== "function") {
        throw new TypeError("A media selector and shield are required");
      }
      this.getMedia = getMedia;
      this.shield = shield;
      this.onState = onState;
      this.onProgress = onProgress;
      this.clock = clock || { now: () => performance.now(), setTimeout: (...args) => setTimeout(...args), clearTimeout: id => clearTimeout(id) };
      this.config = { ...DEFAULTS, ...config };
      this.state = STATES.IDLE;
      this.generation = 0;
      this.video = null;
      this.listeners = [];
      this.timer = null;
      this.frameHandle = null;
      this.operation = null;
      this.elapsed = 0;
      this.target = 0;
      this.beginning = 0;
      this.reason = null;
      this.intendedMuted = false;
      this.destroyed = false;
      this.shield.cover();
    }
    getSnapshot() { return { state: this.state, elapsed: this.elapsed, reason: this.reason, generation: this.generation }; }
    _state(state, reason = null) {
      if (this.state === state && this.reason === reason) return;
      this.state = state;
      this.reason = reason;
      this.onState(state, { reason });
    }
    _mute(video) {
      if (!video) return;
      video.muted = true;
      video.controls = false;
      video.autoplay = false;
      video.loop = false;
      video.disablePictureInPicture = true;
      video.removeAttribute?.("poster");
    }
    _cover() {
      this.shield.cover();
      this._mute(this.video);
      for (const video of this._candidates()) this._mute(video);
    }
    _candidates() {
      try { return Array.from(this.getMedia() || []).filter(Boolean); } catch (_) { return []; }
    }
    _pause(video = this.video) { try { video?.pause(); } catch (_) { /* Already covered. */ } }
    _cancelCallbacks() {
      if (this.timer !== null) this.clock.clearTimeout(this.timer);
      this.timer = null;
      if (this.frameHandle !== null) {
        try { this.video?.cancelVideoFrameCallback?.(this.frameHandle); } catch (_) { /* Token also fences it. */ }
      }
      this.frameHandle = null;
      for (const [video, event, listener] of this.listeners) video.removeEventListener(event, listener);
      this.listeners = [];
    }
    _finish(value) {
      const operation = this.operation;
      this.operation = null;
      operation?.resolve(value);
    }
    _fail(reason) {
      this._cover();
      this.generation++;
      this._cancelCallbacks();
      this._pause();
      this._state(STATES.FAILED_COVERED, reason);
      this._finish(false);
    }
    close() {
      this._cover();
      this.generation++;
      this._cancelCallbacks();
      this._pause();
      this._finish(false);
      this.video = null;
      this.source = null;
      this.elapsed = 0;
      this._state(STATES.IDLE);
    }
    destroy() { this.close(); this.destroyed = true; }
    invalidate() { this._fail("PROTECTION_UNAVAILABLE"); }
    prepare({ target = 0, beginning = 0 } = {}) {
      if (this.destroyed) return Promise.resolve(false);
      this._cover();
      this._finish(false);
      if (!Number.isFinite(target) || !Number.isFinite(beginning) || beginning < 0 || target < beginning) {
        this._fail("TARGET_UNAVAILABLE");
        return Promise.resolve(false);
      }
      this.target = target;
      this.beginning = beginning;
      this.sourceRestarts = 0;
      this.deadline = this.clock.now() + this.config.prepareTimeout;
      this.mode = "prepare";
      const result = new Promise(resolve => { this.operation = { resolve }; });
      this._beginGeneration();
      return result;
    }
    _beginGeneration() {
      this._cover();
      this.generation++;
      this._cancelCallbacks();
      this._pause();
      this.video = null;
      this.source = null;
      this.ranges = [];
      this.frames = 0;
      this.seekAttempts = 0;
      this.playRequested = false;
      this.lastFrame = null;
      this.positioned = false;
      this._state(STATES.WAITING_FOR_MEDIA);
      this._tick(this.generation);
    }
    _restartSource() {
      this._cover();
      if (this.mode !== "prepare" || ++this.sourceRestarts > this.config.maxSourceRestarts) return this._fail("SOURCE_CHANGED");
      // A replacement never inherits the old source's decoded-frame evidence.
      this._beginGeneration();
    }
    _attach(video, token) {
      this.video = video;
      this._mute(video);
      this._pause(video);
      const events = ["seeking", "seeked", "timeupdate", "playing", "play", "pause", "ended", "loadstart", "emptied", "loadedmetadata", "error", "ratechange", "volumechange"];
      for (const event of events) {
        const listener = () => { if (token === this.generation) this._event(event, token); };
        video.addEventListener(event, listener);
        this.listeners.push([video, event, listener]);
      }
    }
    _event(event, token) {
      if (token !== this.generation) return;
      if (event === "error") return this._fail("MEDIA_UNAVAILABLE");
      if (event === "ended") return this._fail("PLAYBACK_STOPPED");
      if (["loadstart", "emptied", "loadedmetadata"].includes(event) && this.source) return this._restartSource();
      if (event === "volumechange" && !exposed(this.state)) {
        if (!this.video.muted) this.video.muted = true;
        return;
      }
      if (exposed(this.state)) {
        if (event === "seeking") return this._fail("POSITION_CHANGED");
        if ((event === "play" || event === "playing") && this.state === STATES.PAUSED_EXPOSED) return this._fail("UNEXPECTED_PLAYBACK");
        if (event === "ratechange" && this.video.playbackRate !== 1) return this._fail("POSITION_CHANGED");
        if (!this._exposedInvariant()) return;
        if (event === "pause" && this.state === STATES.PLAYING_EXPOSED) {
          this.elapsed = this.video.currentTime;
          this._state(STATES.PAUSED_EXPOSED);
          this.onProgress(this.elapsed);
        }
        return;
      }
      if (this.state === STATES.READY_COVERED) {
        if (!this._readyInvariant()) return this._fail("POSITION_CHANGED");
        if (event === "play" || event === "playing") this._pause();
        return;
      }
      if (event === "seeking") {
        this.frames = 0;
        this.lastFrame = null;
      }
      // Provider seeks are permitted here. Their settled position is reasserted
      // by the bounded preparation transaction, without an internal-seek flag.
      if (event === "seeked" || event === "timeupdate" || event === "playing") this._checkPosition(token);
    }
    _schedule(token) {
      if (this.timer !== null || token !== this.generation || this.state === STATES.FAILED_COVERED || this.state === STATES.IDLE) return;
      this.timer = this.clock.setTimeout(() => { this.timer = null; this._tick(token); }, this.config.pollInterval);
    }
    _checkSelection() {
      const candidates = this._candidates();
      if (candidates.length > 1) { this._fail("MEDIA_AMBIGUOUS"); return false; }
      if (this.video && candidates[0] !== this.video) { this._restartSource(); return false; }
      return true;
    }
    _tick(token) {
      if (token !== this.generation) return;
      if (!this._checkSelection()) return;
      if (exposed(this.state)) {
        if (this._exposedInvariant()) this._schedule(token);
        return;
      }
      if (this.state === STATES.READY_COVERED) {
        if (!this._readyInvariant()) return this._fail("POSITION_CHANGED");
        this._mute(this.video);
        this._pause();
        this._schedule(token);
        return;
      }
      if (this.clock.now() >= this.deadline) return this._fail(this.video ? "TARGET_UNAVAILABLE" : "MEDIA_UNAVAILABLE");
      if (!this.video) {
        const candidates = this._candidates();
        if (candidates.length === 1) this._attach(candidates[0], token);
      }
      if (!this.video) { this._schedule(token); return; }
      this._mute(this.video);
      if (typeof this.video.requestVideoFrameCallback !== "function") return this._fail("FRAME_VERIFICATION_UNAVAILABLE");
      const source = sourceOf(this.video);
      if (this.source && !sameSource(this.source, source)) return this._restartSource();
      if (this.source && this.video.readyState < 1) return this._restartSource();
      const ranges = rangesOf(this.video);
      if (this.positioned && !contains(ranges, this.target, this.config.rangeEpsilon)) return this._fail("TARGET_UNAVAILABLE");
      if (this.video.readyState < 1 || (!source.src && !source.object) || !contains(ranges, this.target, this.config.rangeEpsilon)) {
        this._state(STATES.WAITING_FOR_TIMELINE);
        this._schedule(token);
        return;
      }
      if (!this.source) this.source = source;
      if (this._timelineReplaced(ranges)) return this._restartSource();
      this.ranges = ranges;
      if (!this.positioned) this._position(token);
      else this._checkPosition(token);
      if (token === this.generation) {
        this._requestFrame(token);
        this._schedule(token);
      }
    }
    _timelineReplaced(ranges) {
      if (!this.ranges?.length || !ranges.length) return false;
      // A moving DVR end may advance. A backwards reset invalidates the transaction.
      return ranges[ranges.length - 1].end < this.ranges[this.ranges.length - 1].end - this.config.tolerance;
    }
    _position(token) {
      if (token !== this.generation) return;
      if (++this.seekAttempts > this.config.maxSeekAttempts) return this._fail("POSITION_UNSTABLE");
      this.frames = 0;
      this.lastFrame = null;
      this.positioned = true;
      this._state(STATES.POSITIONING);
      try { this.video.currentTime = this.target; } catch (_) { return this._fail("TARGET_UNAVAILABLE"); }
      if (token !== this.generation) return;
      this._state(STATES.SETTLING);
      this._requestFrame(token);
      this._hiddenPlay(token);
    }
    _hiddenPlay(token) {
      if (this.playRequested || token !== this.generation || !this.video.paused) return;
      this.playRequested = true;
      let result;
      try { result = this.video.play(); } catch (_) { return this._fail("PLAY_UNAVAILABLE"); }
      Promise.resolve(result).then(() => {
        if (token !== this.generation) return;
        this.playRequested = false;
      }, () => { if (token === this.generation) this._fail("PLAY_UNAVAILABLE"); });
    }
    _checkPosition(token) {
      if (token !== this.generation || !this.video || exposed(this.state) || this.state === STATES.READY_COVERED) return;
      if (!this.source || this.video.seeking || !this.positioned) return;
      if (!sameSource(this.source, sourceOf(this.video))) return this._restartSource();
      if (this.video.readyState < 1) return this._restartSource();
      const ranges = rangesOf(this.video);
      if (!contains(ranges, this.target, this.config.rangeEpsilon)) return this._fail("TARGET_UNAVAILABLE");
      if (Math.abs(this.video.currentTime - this.target) > this.config.tolerance) {
        if (this.mode === "play") return this._fail("POSITION_CHANGED");
        this._position(token);
      } else this._hiddenPlay(token);
    }
    _requestFrame(token) {
      if (this.frameHandle !== null || !this.video || token !== this.generation) return;
      this.frameHandle = this.video.requestVideoFrameCallback((_, metadata) => {
        if (token !== this.generation) return;
        this.frameHandle = null;
        this._frame(token, metadata);
      });
    }
    _frame(token, metadata) {
      if (token !== this.generation || !this._checkSelection()) return;
      if (exposed(this.state)) {
        if (!this._exposedInvariant(metadata.mediaTime)) return;
        if (this.state === STATES.PLAYING_EXPOSED) {
          this.elapsed = this.video.currentTime;
          this.onProgress(this.elapsed);
          this._requestFrame(token);
        }
        return;
      }
      if (this.state !== STATES.SETTLING) { this._requestFrame(token); return; }
      if (!sameSource(this.source, sourceOf(this.video))) return this._restartSource();
      if (this.video.readyState < 1) return this._restartSource();
      const ranges = rangesOf(this.video);
      if (!contains(ranges, this.target, this.config.rangeEpsilon)) return this._fail("TARGET_UNAVAILABLE");
      if (this._timelineReplaced(ranges)) return this._restartSource();
      this.ranges = ranges;
      const time = metadata.mediaTime;
      const near = Number.isFinite(time) && Math.abs(time - this.target) <= this.config.tolerance &&
        Math.abs(this.video.currentTime - this.target) <= this.config.tolerance && !this.video.seeking && !this.video.ended;
      const consecutive = this.lastFrame === null || (time > this.lastFrame && time - this.lastFrame <= this.config.tolerance);
      if (near && consecutive) { this.frames++; this.lastFrame = time; }
      else { this.frames = 0; this.lastFrame = null; this._checkPosition(token); }
      if (token !== this.generation) return;
      if (this.frames >= this.config.stableFrames) {
        if (this.mode === "prepare") {
          this._pause();
          if (!this._readyInvariant()) return this._fail("POSITION_CHANGED");
          this.elapsed = this.target;
          this._state(STATES.READY_COVERED);
          this._finish(true);
        } else {
          if (this.video.paused || !this._readyInvariant()) return this._fail("PLAY_UNAVAILABLE");
          this.lastPosition = this.video.currentTime;
          this.lastClock = this.clock.now();
          this._state(STATES.PLAYING_EXPOSED);
          let revealed = false;
          try { revealed = this.shield.reveal(this.video) !== false; } catch (_) { /* Fail closed below. */ }
          if (!revealed) return this._fail("PROTECTION_UNAVAILABLE");
          this.video.muted = this.intendedMuted;
          this._finish(true);
          this._requestFrame(token);
        }
      } else this._requestFrame(token);
    }
    _readyInvariant() {
      return this.video && sameSource(this.source, sourceOf(this.video)) && !this.video.seeking && !this.video.ended &&
        Math.abs(this.video.currentTime - this.target) <= this.config.tolerance &&
        contains(rangesOf(this.video), this.target, this.config.rangeEpsilon);
    }
    _exposedInvariant(frameTime) {
      const video = this.video;
      if (!video || !sameSource(this.source, sourceOf(video))) { this._fail("SOURCE_CHANGED"); return false; }
      const ranges = rangesOf(video);
      if (this._timelineReplaced(ranges)) { this._fail("SOURCE_CHANGED"); return false; }
      this.ranges = ranges;
      if (video.seeking || video.ended || video.playbackRate !== 1) { this._fail("POSITION_CHANGED"); return false; }
      const now = this.clock.now(), position = video.currentTime;
      const allowance = this.state === STATES.PLAYING_EXPOSED ? Math.max(0, now - this.lastClock) / 1000 : 0;
      if (!Number.isFinite(position) || position < this.lastPosition - this.config.driftTolerance ||
          position > this.lastPosition + allowance + this.config.driftTolerance ||
          (frameTime !== undefined && (!Number.isFinite(frameTime) || Math.abs(frameTime - position) > this.config.driftTolerance))) {
        this._fail("POSITION_CHANGED"); return false;
      }
      this.lastPosition = position;
      this.lastClock = now;
      return true;
    }
    play() {
      if (this.destroyed || (this.state !== STATES.READY_COVERED && this.state !== STATES.PAUSED_EXPOSED)) return Promise.resolve(false);
      if (this.state === STATES.PAUSED_EXPOSED) {
        if (!this._exposedInvariant()) return Promise.resolve(false);
        this.target = this.video.currentTime;
      }
      this._cover();
      if (!this._checkSelection() || !this._readyInvariant()) {
        this._fail("POSITION_CHANGED"); return Promise.resolve(false);
      }
      this.generation++;
      this._cancelCallbacks();
      const video = this.video, source = this.source;
      this._attach(video, this.generation);
      this.source = source;
      this.mode = "play";
      this.frames = 0;
      this.lastFrame = null;
      this.playRequested = false;
      this.deadline = this.clock.now() + this.config.playTimeout;
      const result = new Promise(resolve => { this.operation = { resolve }; });
      this._state(STATES.SETTLING);
      this._requestFrame(this.generation);
      this._hiddenPlay(this.generation);
      this._schedule(this.generation);
      return result;
    }
    pause() {
      if (this.state !== STATES.PLAYING_EXPOSED) return false;
      if (!this._exposedInvariant()) return false;
      this._pause();
      this.elapsed = this.video.currentTime;
      this._state(STATES.PAUSED_EXPOSED);
      this.onProgress(this.elapsed);
      return true;
    }
    seek(target) { return this.prepare({ target, beginning: this.beginning }); }
    skip(delta) {
      if (!Number.isFinite(delta) || ![STATES.READY_COVERED, STATES.PLAYING_EXPOSED, STATES.PAUSED_EXPOSED].includes(this.state)) return Promise.resolve(false);
      return this.seek(Math.max(this.beginning, this.video.currentTime + delta));
    }
    restart() { return this.prepare({ target: this.beginning, beginning: this.beginning }); }
  }
  const api = { PlayerController, STATES, rangesOf, contains };
  root.NHLUK = root.NHLUK || {};
  root.NHLUK.player = api;
  if (typeof module !== "undefined" && module.exports) module.exports = api;
})(globalThis);
