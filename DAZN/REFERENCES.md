# Technical references and prior evidence

These are implementation references, not DAZN-supported public developer documentation unless explicitly noted.

## Firefox extension architecture

Mozilla WebExtensions manifest browser-specific settings:

https://developer.mozilla.org/en-US/docs/Mozilla/Add-ons/WebExtensions/manifest.json/browser_specific_settings

Relevant points:

- one Firefox extension can target desktop and Android;
- Android availability is declared with `browser_specific_settings.gecko_android`;
- Firefox extension signing uses a stable Gecko extension ID;
- current AMO submissions require a data-collection declaration.

Mozilla content scripts:

https://developer.mozilla.org/en-US/docs/Mozilla/Add-ons/WebExtensions/Content_scripts

Relevant points:

- content scripts can read and modify the DAZN page DOM;
- MV3 content scripts run subject to page-origin CORS for cross-origin requests;
- content scripts can communicate with background scripts for privileged extension operations.

Mozilla host permissions:

https://developer.mozilla.org/en-US/docs/Mozilla/Add-ons/WebExtensions/manifest.json/host_permissions

Relevant point:

- extension background/pages with host permissions can perform cross-origin fetch without ordinary CORS restrictions.

Mozilla background scripts:

https://developer.mozilla.org/en-US/docs/Mozilla/Add-ons/WebExtensions/manifest.json/background

Relevant point:

- Firefox MV3 uses background scripts/event pages; extension service workers are not currently the Firefox implementation path.

Firefox for Android extension development:

https://extensionworkshop.com/documentation/develop/developing-extensions-for-firefox-for-android/

web-ext workflow:

https://extensionworkshop.com/documentation/develop/getting-started-with-web-ext/

Relevant points:

- the same extension source is developed similarly for desktop and Android;
- `web-ext lint` checks Android compatibility;
- `web-ext run --target=firefox-android` installs/tests the extension on a connected Android device.

Firefox Android extension installation:

https://support.mozilla.org/en-US/kb/find-and-install-add-ons-firefox-android

## DAZN discovery implementations inspected

herrnst/plugin.video.dazn

https://github.com/herrnst/plugin.video.dazn

Relevant behavior:

- obtains service endpoints from DAZN startup services;
- uses DAZN Rail, Event, EPG and Playback services;
- demonstrates DAZN's internal structured service model.

iptv-org/epg DAZN integration

https://github.com/iptv-org/epg/blob/master/sites/dazn.com/dazn.com.config.js

Relevant behavior:

- uses DAZN discovery Rail services;
- sends ordinary web Referer/Accept headers;
- demonstrates structured DAZN catalogue data.

luishighnest/script2 DAZN navigator

https://github.com/luishighnest/script2

Relevant behavior:

- uses search.discovery.indazn.com/v1/search;
- uses event.discovery.indazn.com event lookup;
- uses browser-impersonating HTTP sessions in some paths.

These projects are evidence of internal DAZN interfaces, not guarantees that DAZN supports or will preserve them.

## Legacy Android references

Android VPN architecture:

https://developer.android.com/develop/connectivity/vpn

Android WebView:

https://developer.android.com/develop/ui/views/layout/webapps

Accessibility service configuration:

https://developer.android.com/guide/topics/ui/accessibility/service

## Important caution

Do not infer current DAZN behavior solely from an open-source client.

Do not infer Firefox Android behavior solely from desktop Firefox.

Real Firefox desktop and real Firefox Android tests are required. On-device evidence controls.
