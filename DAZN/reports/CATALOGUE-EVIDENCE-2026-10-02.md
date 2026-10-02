# Catalogue and browser-route evidence

Inspected 2026-10-02. This report contains field names and route templates only. It does not reproduce event titles, scores, descriptions, media URLs, account data, or production event IDs.

## Official search transport observation

This neutral request independently returned HTTP 200 during implementation and this evidence review:

https://search.discovery.indazn.com/v1/search?searchTerm=Buffalo&country=gb&brand=dazn

The response envelope is:

```text
{
  Results: [{ Id: string, Tiles: array }, ...],
  InfoType: string,
  SeachEngineCallDurationMs: number,
  starterText: string,
  searchId: string
}
```

The spelling `SeachEngineCallDurationMs` is as observed. The first result group may have no tiles even when later groups contain data. Parse every supported `Results[].Tiles` array, then sanitize immediately. Do not expose `starterText` or other raw provider strings.

The observed response contained both navigation tiles and event tiles. Event tiles used `Contestants` objects with `Id`, `Images`, and `Title`; the current event content types included `CatchUp` and `UpComing`. Event tiles carried `EventId`, `AssetId`, `Start`, `End`, `EventStartTime`, `MediaStartTime`, `VideoType`, `Related`, `Videos`, `ArticleNavigateTo`, and `ArticleNavParams`, among additional unsafe fields. Do not treat a navigation tile as a playable event, and do not infer that every query returns all matching events without checking the requested game.

Requests using `query` or `searchParam` instead of `searchTerm` returned HTTP 400 in the implementation investigation. The provider error text misleadingly referred to `searchParam`. The successful request, rather than that error wording, controls the implemented parameter name.

The public response body was gzip-compressed even when the Python request did not explicitly request compression. Browser fetch normally handles content encoding; command-line evidence collection decoded gzip before JSON parsing.

## Official browser route evidence

The public DAZN web application supplied the following bootstrap script when requested using a Firefox user agent:

https://www.dazn.com/static/boot-29.406.0-4653cdae46adfb07d899.js

Its catalogue configuration identifies the catalogue chapter and its loader obtains the chapter at:

https://www.dazn.com/chapters/moon/catalog/index.html

That chapter lists the following current application bundles. These are DAZN's own deployed source, not a third-party route convention.

### Event and asset selection

https://www.dazn.com/chapters/moon/catalog/static/main-82368f0b-3ab07b940bc01b8342e7.fcc01c8.js

The route table declares `/:regionParams/home/:eventId/:assetId?`. Its handler passes the two opaque IDs to `setCurrentView` as `pendingSelection: {eventId, assetId}`. The optional asset parameter is also recorded separately from the event parameter in the application's deep-link diagnostic call.

This supports the exact structured-data conversion:

```text
https://www.dazn.com/en-GB/home/<EventId>/<AssetId>
```

The event-only form is also declared by the application, but specifying the selected asset is preferable when a validated asset is available. IDs must be validated and escaped as opaque path components. No title or slug construction is necessary.

A public `/en-GB/home` response independently contained event-tile anchors with this two-ID route shape. Fetching one such anchor returned HTTP 200 and an identical canonical URL. Fetching the Event service using its first path ID returned an object whose `EventId` matched that ID. No authenticated playback was attempted.

### Article navigation parameters

https://www.dazn.com/chapters/moon/catalog/static/main-c92480b7-c2d089e3df9c0bea01e9.fcc01c8.js

Its tile normalizer reads `NavigateTo` or `ArticleNavigateTo`, and `NavParams` or `ArticleNavParams`. Its route-construction module (module ID `62296`) parses the navigation parameters and, for a fixture page, joins the lowercased navigation target, `ContentId:` plus the parsed content ID, and the event ID.

That supports this additional route shape when the corresponding structured fields have been validated:

```text
https://www.dazn.com/en-GB/fixture/ContentId:<ContentId>/<EventId>
```

The route table in the first bundle declares `/:regionParams/fixture/:categoryId/:eventId?/:assetId?`.

The observed provider Event response used:

```text
ArticleNavigateTo = Fixture
ArticleNavParams = PageType:Fixture;ContentType:Fixture;ContentId:<opaque ID>
```

`ArticleNavigateTo` is therefore not itself a URL. A bare `/fixture/<EventId>` should not be inferred from its name. The explicit home/event/asset route has stronger evidence for selecting the exact variant.

## Official Event service observation

The following existing, publicly documented-in-client URL form returned HTTP 200 without credentials:

```text
https://event.discovery.indazn.com/eu/v7/Event?id=<EventId>&country=gb&languageCode=en&brand=dazn
```

Relevant response fields included `Id`, `AssetId`, `AssetTypeId`, `EventId`, `Type`, `NavigateTo`, `NavParams`, `Start`, `End`, `Competition`, `Sport`, `Contestants`, `Videos`, `Related`, `VideoType`, `IsLinear`, `ArticleNavParams`, `ArticleNavigateTo`, `EventStartTime`, and `MediaStartTime`.

The response also contains unsafe display fields and other state. This list is schema evidence, not an instruction to preserve all of those fields. The application must continue to reduce the response to its strict safe allowlist before UI, storage, logs, or diagnostics.

## Third-party implementation evidence

Inspected the author-maintained source at commit `41baee655f179889dee1006589eb6b1c6931f476`:

https://github.com/herrnst/plugin.video.dazn/blob/41baee655f179889dee1006589eb6b1c6931f476/resources/lib/client.py

https://github.com/herrnst/plugin.video.dazn/blob/41baee655f179889dee1006589eb6b1c6931f476/resources/lib/tiles.py

https://github.com/herrnst/plugin.video.dazn/blob/41baee655f179889dee1006589eb6b1c6931f476/resources/lib/parser.py

These corroborate `Tiles` arrays, nested `Related` tiles, `AssetId` and `EventId`, `NavigateTo` and `NavParams`, `ArticleNavigateTo`, and Event lookup with `id`, `country`, and `languageCode`. They do not establish current search transport or authenticated Firefox playback.

The previously cited `luishighnest/script2` repository returned GitHub HTTP 404 during this investigation. Treat its historical mention as historical evidence only.

## Verification boundary

This establishes current public response fields and DAZN's own declared browser routes. It does not establish successful subscription playback, Firefox Android Widevine support for this account/device, stable decoder preparation, exact feed availability for a particular NHL game, or a spoiler-safe first frame. Those remain actual-browser and actual-device acceptance tests.
