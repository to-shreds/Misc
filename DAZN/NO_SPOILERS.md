# No-spoiler contract

This is a hard requirement, not a preference or optional mode.

The user routinely watches Sabres games as replays the next day or late at night while a game may still be in progress. A score, result, current game state, later playoff opponent, thumbnail, replay runtime, live edge, or accidental current frame can spoil the game.

## Never expose

- scores or final results;
- period, clock, live state, winner, series score, or outcome language;
- provider recap text or headlines;
- thumbnails, posters, hero images, canvases, or preview frames from DAZN sports pages;
- standings;
- total video duration;
- time remaining;
- playback percentage or progress bar;
- later playoff opponents before prior series are explicitly marked watched;
- autoplay next content;
- lock-screen or Bluetooth metadata derived from DAZN;
- a live/current frame while preparing a from-beginning replay.

## Allowed neutral information

- teams in the selected scheduled matchup;
- scheduled start date/time;
- home/away identity;
- elapsed time the user has personally watched;
- neutral status such as Preparing, Ready, Paused, or unavailable;
- fixed diagnostic stage/error vocabulary that contains no game-state information.

## Fail-closed rule

If the app cannot establish that the requested beginning or saved elapsed position is safely available, it must remain covered and muted.

There is no fallback to the live edge, an unfiltered browser, a DAZN sports home page, or the native DAZN application.

## Broadcast limitation

The app cannot remove spoilers spoken or shown inside the broadcast once the user intentionally starts playback. Its job is to prevent the app and DAZN UI from revealing later game state before the selected playback point.
