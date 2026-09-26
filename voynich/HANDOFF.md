# Voynich decipherment research handoff

## Objective and state

Recover a reproducible decoding rule yielding continuous, independently testable plaintext. **IN PROGRESS: no verified decipherment, source-language identification or word meaning has been established.** Round 3's bounded contextual experiment is finished and verified; the decipherment objective is not complete.

## Controlling records

- Latest substantive summary: `to-shreds/Misc/voynich/round3/REPORT.md`.
- Latest compact numerical record: `voynich/round3/BENCHMARK.json`.
- Tested core contextual solver: `voynich/round3/code/contextual.py`; SHA-256 `7392b987b0a56738a8990ca254297aafe71e4a7e06b4a13a2ddf5fee2ec1ac68`, Git blob `f7b69989e61dcb65080a5b09387ed2c99c9ac31a`. Publication readback matches the tested file.
- These substantive files are present through Misc commit `9ba1690b54283efa55ab28f4732e42e9805ed33a`.
- `to-shreds/ProjectStatus/projects/voynich-decipherment/STATUS.md` controls readiness/next steps.
- Prior Round 2 records remain at `voynich/REPORT.md` and `voynich/results/BENCHMARK.json`; do not overwrite them.

The repository contains the benchmark and core solver, not the complete runnable package or binary archives. The core module imports vendor modules that are in the full package. Retrieve the named conversation/Library archive through Files before asking Jon for manual re-upload. Do not assume old sandbox paths exist in a new session.

## Current deliverables

Delivered in the conversation:

- `Voynich_Round_3_Full.zip`, 26,033,071 bytes; SHA-256 `13d4e32ad3951507347b4a2cc001569cd3942ac20ec47055e23511ba12d80482`.
- `Voynich_Round_3_Report.md`, 15,123 bytes; SHA-256 `8eeac7dad94ac307353a2f8392294a21ae3506c6661c5730735006d2eaf18a8d`.

The full package has 333 files, including 10 new Python modules, three unchanged vendor modules, all needed reference/transcription data and licenses, frozen protocols, 48 fit bundles, all 144 full confirmation outputs, calibration results and verification logs. Rebuildable caches are omitted. Original Round 1/2 archives are not duplicated inside it.

Retained Round 2 full archive: `Voynich_Round_2_Full.zip`, SHA-256 `17e4b2dfba8c201160da1aa2d3bf4a8116819ccbca6107371b1f97360ecde103`, 40,546,895 bytes. Its source/results-only archive has SHA-256 `66499e7103a89ffc111bed636595227b72989de9eb9083b253f83f096fb4168c`, 656,431 bytes. Both were rechecked unchanged, as was the original first-stage audit.

## Completed Round 3 benchmark

Two invertible alphabet permutations may differ at at most four source positions. An observable previous-ending trigger or group-initial position chooses the alphabet. Apparent spaces and one-letter-per-assumed-unit length are fixed. Static substitution is the baseline. Four reference languages, raw/composite units, and separate A/B samples produced 48 manuscript fits and 96 negative-control fits. These are configurations, not independent cipher families.

Eight known contextual ciphers were calibrated, with all three model alternatives fitted to each. The initial German group-initial control was below the 99% gate at 98.2936%; legal three-cycle polishing corrected the search barrier before any manuscript fitting. All eight final controls recovered every evaluated character. Initial source, controls and amendment are preserved.

Keys were frozen before confirmation. The prospective reserve had 1,609 clear groups across five numbered leaves before rare-unit exclusions. Context improved on static in 13/16 manuscript configurations, 16/16 shuffled-group configurations and 13/16 positional-Markov configurations. This does not distinguish genuine decryption from improved text resemblance. No output was validated as continuous source-language text.

The q check found `chedy qokeey qokeedy` at f111v.5 and `chedy qokeey okeedy` at f111v.38. This survives all three transliterations and both gap treatments. Under those readings/boundaries and without exceptions, q is not a deterministic optional prefix based only on the previous two written groups and current q-stripped group. The f39v example is weaker because merging an uncertain ZL gap removes its matched context.

The weak visual-anchor pilot tested 10 tentative published drawing correspondences, retained seven clear labels and found no exact target-page matches. This is not a whole-manuscript image alignment, a botanical identification or semantic confirmation.

## Verification and reproduction

The final 16-test suite passed, including independent recalculation of all 144 confirmation scores/outputs, all eight known-cipher recoveries, key constraints, forward/reverse operation, optimization scores, input hashes and derivations, split separation, null construction, q sensitivity and anchor exclusions. All 66 retained input/vendor files were byte-compared with the verified Round 2 archive. The final ZIP was independently extracted to a separate directory and its 16-test suite also passed.

Run `python code/reproduce.py --verify-only` within the full package. `python code/reproduce.py --recompute ../Voynich_Round_3_Recomputed` builds a new execution in a destination that must not already exist. Its overwrite guard was tested; a second complete fresh fit recomputation was not executed or claimed.

## Do not break or overclaim

No reserve here is previously untouched study-wide: all manuscript pages were available in Round 2. Fixed-key confirmation is not independent evidence. Do not rank languages by numerical scores across different language models or unitizations. One realization of each null construction is not a significance distribution. Related transliterations are not independent manuscripts.

Do not infer that q is silent or meaning-bearing, that similar residual strings mean the same thing, or that local correlation identifies encryption. The q contradiction applies only to the specified deterministic visible-local padding rule. Preserve uncertain-gap sensitivity, original data/licenses, adjacency-breaking exclusions, initial failed controls, frozen keys, and all candidate outputs.

Do not call the two-state, length-preserving model a general variable-length contextual transducer. Do not claim any decoded word, plant name or coherent passage. Do not overwrite root `Misc/HANDOFF.md`, which belongs to Zork, or modify unrelated applications.

## Next action

Develop a constrained variable-length unit/abbreviation or boundary model with explicit forward rules and limited ambiguity. First demonstrate known-plaintext recovery and retain both nonsense controls. This is a next hypothesis, not evidence that abbreviations are the solution. Independent semantic predictions and continuous verified plaintext remain required and unachieved.
