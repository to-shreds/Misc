# Voynich Round 3: contextual decoding benchmark

**No verified continuous plaintext, source-language identification, or word meaning was established.** The bounded contextual encoder/decoder was implemented, calibrated, applied to the manuscript and matched negative controls, and verified against frozen outputs. This file is the durable benchmark summary. The fuller report, all source files, complete candidate outputs and reference data are in the delivered Round 3 package identified below.

## Implemented model and corrected premises

The implementation uses two bijective source-unit-to-plaintext-letter alphabet permutations that differ at no more than four positions. Apparent spaces remain spaces. The alternative alphabet is selected either at a group's first unit or throughout a group when the preceding written group ends in the designated trigger, EVA y for manuscript tests. A static single-alphabet model is the baseline. There are no per-word or per-page codebooks.

This is a genuine forward encoder and inverse decoder, but only for two bounded, length-preserving contextual families. It is not a general variable-length or hidden-state transducer. Raw EVA and a fixed composite representation were tested separately against Pliny Latin, Old Italian, German and Ancient Hebrew reference models. Reference-only four-gram language models were used. Each search has six restarts and 20,000 proposals per restart, plus up to four legal two-/three-cycle polishing sweeps for contextual fits. The relative-key coding cost is not a complete minimum-description-length criterion.

Two prior statements needed correction. The Round 2 q association does not distinguish contextual encryption from ordinary grammar: fixed substitution can preserve contextual dependencies in the plaintext. Also, no manuscript page reserved here was previously unseen study-wide. All pages were available in Round 2. Round 3 has a prospectively quarantined fitting/selection reserve, not independent pristine evidence.

## Calibration

Eight true contextual ciphers were generated, two families in each of four languages. The fitter received ciphertext and the observable trigger, not the key or plaintext. Each used 2,500 training groups and 1,200 evaluation groups. The first German group-initial control recovered 98.2936% of evaluated characters, below the stated 99% gate. It exposed a three-letter-cycle barrier in the pair-move search under the four-position cap. Legal cycle polishing was added before any manuscript fit; initial code, controls and the amendment were preserved.

The final solver recovered 100% of evaluated characters in all eight controls, totalling 45,192 character evaluations. Each language's evaluation passage was used under both known ciphers, so this is not a count of distinct underlying characters. All three model alternatives were fitted to each positive control, yielding 24 final calibration comparisons. This is in-family validation with known observable triggers, not proof of universal contextual-cipher recovery.

## Matrix, split and frozen evaluation

The A/B training samples have 2,501 and 2,500 clear groups, respectively, because complete uninterrupted runs are retained. Development has 848 A groups and 3,278 B groups. Confirmation has 399 A groups and 1,210 B groups, or 1,609 clear groups on five numbered leaves before representation-specific rare-unit exclusions. Depending on the reference alphabet and unitization, 394 to 399 A groups and 1,203 to 1,210 B groups were scored. Exclusions break adjacency. Recto, verso and the special foldout grouping remain on one side of the split.

The experiment consists of 48 bundles, each with three models: 48 actual-manuscript fits and 96 negative-control fits, for 144 frozen-key confirmation evaluations. Mode selection used development data only, separately within each group/language/unitization/control construction. Keys and selections were hashed before confirmation. Counts are configurations, not independent cipher families.

Negative controls were (1) whole groups shuffled within uninterrupted runs and (2) a train-fitted positional Markov generator preserving requested word lengths and approximating local glyph dependencies. The generator does not preserve all manuscript statistics. One fixed realization of each construction was used per sample. This is not a Monte Carlo significance distribution.

| Input | Selected contextual model improves confirmation score over static | Does not improve |
|---|---:|---:|
| Actual manuscript | 13 of 16 | 3 of 16 |
| Shuffled whole groups | 16 of 16 | 0 of 16 |
| Positional Markov text | 13 of 16 | 3 of 16 |

Thus contextual score improvement is not specific to a decipherment. In the A/Latin/raw comparison, improvement is 0.371 bits per scored unit for the manuscript, 0.560 for shuffled groups and 0.085 for generated text. These are matched comparisons, not comparable language probabilities across languages or unitizations. Scored units include boundary spaces.

The selected A/Latin/raw output begins:

```text
pariadre trim iadripe arimare paripis aribarus ebar six
narce adriim arum arde it arulm idedaris ine nullo cdep
vidim i dare nullo ariadre vidare aris im ede ne ne
```

This is not a Latin translation. Every candidate key and all 144 full confirmation outputs are preserved. No output was established as continuous source-language text under a verified reading rule. Failed bounded searches do not prove that a language or all contextual ciphers are impossible.

## The q necessary-condition check

Under the retained transcriptions, f111v contains these excerpts:

```text
f111v.5:  chedy qokeey qokeedy
f111v.38: chedy qokeey okeedy
```

The preceding two written groups and the current unprefixed remainder are identical; initial q differs. This survives ZL, IT and RF under both splitting and merging uncertain gaps. Therefore, assuming those readings and boundaries and allowing no exceptions, q cannot be a deterministic optional prefix controlled solely by the preceding two groups and the current q-stripped group, fixed throughout that folio.

A second example on f39v is weaker: it disappears in ZL when the uncertain or,aiin gap is merged. IT and RF retain it. This sensitivity was explicitly checked and recorded. The transliterations represent one manuscript and are related, not independent replications.

This rejects only the specified local redundant-padding model. It does not identify q's sound or meaning, assert shared meaning of prefixed/unprefixed forms, or exclude deeper context, following context, position, hidden state, stochastic choices, writing errors or alternative segmentation.

An analogous diagnostic on ordinary unencrypted reference text finds contextual test-log-loss improvements for Old Italian, German and Ancient Hebrew, but not Pliny Latin. Prefix selection used the largest eligible training sample, not the largest test effect. The operational string-pair definition does not establish morphological equivalence.

| Reference | Initial character | Test cases | Bits saved by previous-ending features |
|---|---|---:|---:|
| Pliny Latin | s | 1,066 | -0.00218 |
| Old Italian | a | 603 | 0.04490 |
| German | m | 1,239 | 0.01637 |
| Ancient Hebrew | ו | 3,674 | 0.05396 |

These ordinary-language effects do not prove that grammar explains Voynich; they demonstrate why contextual association alone is insufficient to diagnose encryption.

## Weak visual-anchor pilot

Ten tentative, published miniature-plant/herbal-page correspondences were fixed before the textual comparison. These are source-author visual proposals, not newly established botanical identities or a blind image classifier. Three labels were excluded for uncertainty. None of the seven evaluable labels occurred exactly in its proposed target page's retained paragraph text. Descriptive edit-distance comparisons are retained but yield no decoded plant name.

This does not show the images are unrelated or the text meaningless. The proposed matches may be wrong, labels may not be names, and inflection or encoding may change a name's form. Systematic whole-manuscript visual alignment and independent semantic validation remain undone. No candidate survived to semantic confirmation.

## Verification and deliverables

The final 16-test suite passed with zero errors or failures. It independently recalculated all 144 confirmation scores and outputs from saved keys; reconstructed all eight known-cipher recoveries; verified key constraints, forward/inverse operation, incremental and cycle scores, input hashes, reference derivations, leaf separation, source/control samples, q examples, gap sensitivity and anchor exclusions. All 66 retained input/vendor files were byte-compared with the unchanged, hash-verified Round 2 archive. The old archives were rechecked and remain unchanged.

The saved-result verification entry point was executed. A new-directory recomputation entry point is supplied and its overwrite guard was exercised. No second complete fresh recomputation is claimed. Original Round 1/2 archives are not duplicated in the Round 3 ZIP.

Delivered in the conversation, not uploaded here as binary archives:

- `Voynich_Round_3_Full.zip`: 26,033,071 bytes; SHA-256 `13d4e32ad3951507347b4a2cc001569cd3942ac20ec47055e23511ba12d80482`.
- `Voynich_Round_3_Report.md`: 15,123 bytes; SHA-256 `8eeac7dad94ac307353a2f8392294a21ae3506c6661c5730735006d2eaf18a8d`.

The full package contains 333 files, including all code, reference texts and licenses, frozen protocols and keys, initial and final calibrations, complete predictions and verification logs. It excludes rebuildable caches. These package paths and hashes identify the complete executable source; this summary does not imply all source or binary files were copied to GitHub.

## Continuation

The experiment is complete; the decipherment objective remains IN PROGRESS. The next model should test constrained variable-length units or abbreviations and alternative boundaries, rather than expand this alphabet-switching model merely to obtain a better score. This is a next hypothesis, not a finding that abbreviations are the answer. Any candidate needs calibrated recovery, nonsense controls, coherent continuous text and independent semantic predictions.

Primary sources and exact input descriptions: https://www.voynich.nu/transcr.html; https://www.voynich.nu/q20/f111v_tr.txt; https://www.voynich.nu/q15/index.html. Package provenance contains all corpus URLs, versions, hashes and licenses. The load-bearing numeric record is `results/BENCHMARK.json` in the full package, backed by all fitted keys and outputs.
