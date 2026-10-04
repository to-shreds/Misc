# Local API and Tasker extension points

The default origin is `http://127.0.0.1:8293`. This is a phone-local API, not a public Hyundai endpoint. Do not expose it on a network.

GET `/health` and the UI assets are public. Other GET requests require a paired owner cookie or bearer token. POST requests require `Content-Type: application/json` and `X-SF-Client: ui` or `tasker`. Bodies are limited to 1 MiB. External Origin/Host values are rejected; there is no CORS support.

## Reads

GET `/api/snapshot`: normalized state, timestamps, configuration, mode/session identifier, command jobs, confirmations, notices, and diagnostics. Reading this never wakes or polls the vehicle.

GET `/api/catalog`: validated events, fields, and action types.

GET `/api/effects`: up to twenty undelivered phone effects, with IDs and expiry times. POST `/api/effects/ack` with `{"ids":["effect-id"]}` consumes them. Tasker independently persists consumed IDs before delivery to avoid replay.

GET `/api/backup`: owner-only configuration export.

## Pairing and owner actions

POST `/api/pair` with `{"code":"ONE_TIME_CODE"}` exchanges a short-lived code for a role-bound token. This route is rate limited. Never embed a real token in a Tasker project or shared script.

Owner-only POST routes: `/api/pairing-code` (a new Tasker code), `/api/config`, `/api/restore`, `/api/mode`, `/api/arm`, `/api/login`, `/api/select-vehicle`, `/api/disconnect`, `/api/confirm`, `/api/confirmation/cancel`, `/api/rule/test`, `/api/simulate`, `/api/revoke-clients`, and `/api/logout`.

Tasker-role clients can read the snapshot/catalog/effects and POST only `/api/context`, `/api/event`, `/api/command`, `/api/effects/ack`, and `/api/pause`. They cannot log into Hyundai, change configuration, switch mode, arm real controls, or confirm a sensitive request.

Configuration save/restore uses `{"revision":1,"config":{...}}`. Obtain the current revision from the snapshot. A conflicting save returns an error instead of silently overwriting another editor.

## Commands

POST `/api/command` with a unique request ID and current mode/session identifiers:

```json
{
  "action": "climate_start",
  "preset_id": "summer",
  "request_id": "a-new-unique-request-id",
  "expected_mode": "simulation",
  "epoch": "THE-CURRENT-SNAPSHOT-EPOCH"
}
```

Allowed actions: `refresh`, `force_refresh`, `lock`, `unlock`, `climate_start`, `climate_stop`, `lights`, `horn_lights`, and `locate`. A request either fails validation, returns a confirmation-required record, or returns a queued job. None is proof that the car changed state. Poll the local snapshot for the job result. Never retry a timed-out physical action with a new ID automatically.

Manual confirmations expire after sixty seconds. Rule-origin live prompts allow five minutes. The owner UI submits `/api/confirm` with `{"id":"confirmation-id","outdoors":true}` where outdoor acknowledgement is needed. Confirmation is bound to the preset and session, and consumed once. An imported rule cannot quietly confirm itself.

## Send an event from any Tasker profile

Use **Perform Task > SF Send Event**, placing JSON in **Parameter 1**. The native transport adds a fresh timestamp and unique ID:

```json
{"event":"nfc","value":"desk-tag"}
```

```json
{"event":"variable","value":"rain-started","context":{"user.weather":"rain","home":true}}
```

Custom `user.*` values are text, up to 200 characters. Use equals/contains rules for them. Built-in boolean fields remain actual JSON booleans, not strings. Sending context tells the bridge what the source believes; only send home/car state when the source actually knows it.

Alternatively set `%SFEvent` to that JSON to use the included variable-set profile. For repeated identical events, calling SF Send Event directly is clearer than relying on Android/Tasker variable event behavior.

Built-in event names: `manual`, `bluetooth_connected`, `bluetooth_disconnected`, `android_auto_connected`, `android_auto_disconnected`, `home_enter`, `home_exit`, `wifi_connected`, `wifi_disconnected`, `power_connected`, `power_disconnected`, `alarm`, `nfc`, `variable`, `vehicle_changed`, and `weather_changed`. `schedule` is internal to the scheduler.

There is no universal Android Auto, calendar, geofence, or weather detector in this package. The corresponding hooks become useful when connected to your existing profiles or another explicit data source.

A direct HTTP event uses `event`, `id`, and `at` (Unix seconds), plus optional `value` and `context`. Old/future events outside ninety seconds are rejected. `/api/context` updates fresh context without triggering a routine.

## Call a Tasker task or set an output variable

Add an exact task name to Settings' allowlist before a rule may use `run_task`. The bridge and native transport both check this. The called task has its normal Tasker permissions; review it before allowing it.

The `set_variable` action is restricted to names beginning `SFUser`, such as `SFUserNeedsFuel`. Rule conditions for externally supplied values instead use `user.name` in event context. These are separate directions of data flow.

No public status API returns Hyundai credentials or raw bearer tokens. A configuration backup is not a complete backup of Tasker or Termux.
