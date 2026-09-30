# Technical references and prior evidence

These are implementation references, not DAZN-supported public developer documentation unless explicitly noted.

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

## Android documentation

Android VPN architecture:

https://developer.android.com/develop/connectivity/vpn

Android WebView:

https://developer.android.com/develop/ui/views/layout/webapps

Accessibility service configuration:

https://developer.android.com/guide/topics/ui/accessibility/service

## Important caution

Do not infer current DAZN behavior solely from an open-source client. The phone diagnostics have repeatedly shown that endpoint shape, browser context, and player lifecycle matter. On-device evidence controls.
