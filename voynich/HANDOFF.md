# Voynich decipherment handoff

## Objective and state

Recover consistent decoding rules that yield continuous, independently testable plaintext. **IN PROGRESS. No verified decipherment, source-language identification or word meaning has been established.** Round 4's bounded abbreviation/boundary experiment and constrained extension are complete and verified; the larger objective is not complete.

## Controlling sources

- Latest durable benchmark: `to-shreds/Misc/voynich/round4/REPORT.md`.
- Compact numerical record: `voynich/round4/BENCHMARK.json`.
- Tested constrained search module: `voynich/round4/code/strict.py`.
- These substantive records are present through Misc commit `b2d0b640e8ed252880c46c15ef60e5dd8cbee7ad`.
- `to-shreds/ProjectStatus/projects/voynich-decipherment/STATUS.md` controls readiness and next steps.
- Complete runnable code, datasets and candidate outputs are in the delivered Round 4 archive. The repository contains a summary and one core module, not the entire installation. The base-decoder publication was blocked; its actual tested file is included in the ZIP. Do not claim full repository upload.

Use Files to retrieve named conversation/Library artifacts before requesting manual re-upload. Do not assume old sandbox paths persist. Current conversation attachments with backing files mount automatically for container tools.

## Current complete artifacts

- `Voynich_Round_4_Full.zip`: 28,123,330 bytes; SHA-256 `28fb4555fa59645bb096d4fd76bad9bdbefac9931170a95734512e5d9a2f0df0`.
- `Voynich_Round_4_Report.md`: 19,103 bytes; SHA-256 `5ba6317544bd2f7803452e82b9e1937f0251f776d031e8dae86fd2945b7b9fe5`.

The ZIP has 557 files: 11 new Python scripts, two retained vendor modules, all needed reference/transcription data and licenses, initial failed calibrations, protocols/amendments, frozen keys, complete output and verification records. Rebuildable caches and earlier full archives are omitted. The published strict module has SHA-256 `5874b50139382f0d95b7bdb5360697c8e564f45d4757ab89113183bc4c18683c`, Git blob `e45e35637dfa0b4ff531e837b1a0a28ebf1a7d1e`, verified by publication readback. Package-only base engine SHA-256 is `e91ec2bef9e9d9dfb0493a2633ab10479523c4ba5db6b4b0d8c21ab22b82d333`.

## What Round 4 completed

Ignored apparent spaces within clear uninterrupted runs; tested injective raw/composite source units mapped to letters or eight reference-selected greedy bigrams, with zero bigrams as baseline. Word divisions are inferred separately. This is a static bounded expansion model, not arbitrary contextual shorthand. Unsupported source units break the stream and are reported.

Sixteen known ciphers were tested. One initial Hebrew control recovered only 7.1205%; increasing every search from six to eighteen starts before manuscript fitting yielded exact letter-stream recovery in all sixteen. Initial failures and code were preserved. Correct-letter boundary F1 ranged from 86.48% to 94.05%, so exact letters do not establish original spaces. Inventories were supplied in these controls, not discovered.

The primary matrix executed 32 manuscript and 64 negative-control fits. None of sixteen actual eight-abbreviation keys obeyed its forward rule on all training/development/confirmation samples, despite four passing confirmation alone. A post-primary train-only constrained extension refitted all 48 abbreviation configurations. Forty-six needed MILP repair; two were already feasible. All 48 constrained keys obey training. All-sample consistency holds for 3/16 manuscript, 5/16 shuffled and 6/16 generated keys. None was established as a translation.

A secondary word-order test gives unadjusted p below .05 for 4/32 primary actual configurations versus 5/32 shuffled and 1/32 generated; strict counts are 3/16, 3/16 and 0/16. One strict actual and one shuffled candidate pass both checks. These statistics are exploratory, correlated and not evidence of a language identification.

## Verification and reproduction

Primary 16-test and strict eight-test suites pass without errors or failures, including independent recalculation of all 144 confirmation scores and outputs. The final ZIP was separately extracted; all 556 manifest-listed member hashes and all 24 tests passed there. ZIP integrity, final hashes and unchanged prior archives were rechecked. All 83 retained input/vendor files were compared to the verified Round 3 archive.

The whole primary pipeline was ACTUALLY recomputed in a new directory: 16 controls, four boundary tests, 96 fitted keys, 96 outputs and 96 word-order diagnostics. All compared scientific fields matched exactly. Runtime metadata and dependent hashes/timestamps were excluded. This does not include a second complete strict-extension fitting run.

Within the complete package, run `python code/reproduce_all.py --verify-only` or `python code/reproduce_all.py --recompute ../Voynich_Round_4_Recomputed --workers 2`. Destination must not exist; overwrite guards were tested. The lower-level `reproduce.py` covers only primary work. Tested environment: Python 3.13.5, NumPy 2.3.5, Numba 0.65.1, SciPy 1.17.0. No network or service keys are needed for reproduction from the full ZIP.

## Do not break or overclaim

Preserve prior rounds and all original data, licenses, uncertainty exclusions, failed controls and frozen keys. Do not alter `Misc/HANDOFF.md`, which belongs to Zork. Source-unit assumptions and reference-trained abbreviations are not recovered pronunciations or historical abbreviations. Spaces are information-losing; dictionary segmentation is not semantic validation. No pages are pristine study-wide: exact Round 3 samples and null realizations were reused. The strict extension is explicitly post-primary, not fresh confirmation.

Assignment optimality concerns minimum weighted changes from a preferred key, not globally optimal language decoding. A local feasible swap search is not exhaustive. Related transliterations and reused configurations are not independent replications. Negative absolute word-order gain also occurs on genuine reference prose. Do not promote isolated words, mechanical consistency or unadjusted permutation statistics into a translation.

## Next action

Jointly infer a small, explicitly constrained source segmentation and target expansion inventory. First demonstrate recovery when the source-unit inventory is hidden, rather than supplied as in Round 4. Preserve exact forward checks and matched nulls; require independent semantic or cross-label predictions. A larger score alone is not a result. This model and independent semantic confirmation remain unimplemented or unestablished.

## Earlier artifacts retained

Round 3 remains at `voynich/round3/`; its full archive has SHA-256 `13d4e32ad3951507347b4a2cc001569cd3942ac20ec47055e23511ba12d80482`, 26,033,071 bytes. Round 2 remains at `voynich/REPORT.md` and `voynich/results/BENCHMARK.json`; full archive SHA-256 `17e4b2dfba8c201160da1aa2d3bf4a8116819ccbca6107371b1f97360ecde103`, 40,546,895 bytes. Original first-stage members were byte-checked against the verified Round 2 copy. None of those prior archives or reports was overwritten.
