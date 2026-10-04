# Santa Fe Control Center

This directory is the canonical GitHub home for the Santa Fe / Bluelink Tasker project.

Current status: **IN PROGRESS**.

The first generated Tasker project is **known broken on-device**: it imports tasks by name, but every imported task shows zero actions in Tasker 6.7.6-beta. The existing generator therefore needs correction before the Tasker artifact is usable.

## Architecture

Tasker -> localhost bridge -> U.S. Hyundai Bluelink API

No Termux:Tasker dependency is intended. Tasker communicates with the local bridge over HTTP on 127.0.0.1.

## Preserve

- Keep simulation and live scopes separate.
- Do not store Hyundai credentials in Tasker XML or exported variables.
- Do not add Termux:Tasker, AutoInput, Shizuku, or a cloud relay as dependencies.
- Do not treat an accepted remote request or a timeout as proof that the car performed the action.
- Preserve unrelated projects and user settings.
- The user's sample Regex Tasker export and original Hyundai APK are reference inputs only and must not be committed here because the sample XML contains unrelated secrets.

See HANDOFF.md for the current state and exact next step.