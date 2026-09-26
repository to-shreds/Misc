# Voynich decipherment attempt: second-stage benchmark

**No verified decipherment, plaintext language, or word meaning was established.** The expanded experiment was executed; its statistical output is not a translation.

## Executed scope

The matrix contains 232 manuscript configurations, 30 known-cipher calibrations, and 24 scrambled comparisons. Six additional q-context models cover three transliterations and two treatments of uncertain gaps. Configurations are combinations of assumptions, samples, and reference corpora, not 232 independent cipher families or an exhaustive search.

The complete ZL file was parsed. It contains 5,385 loci across 227 page identifiers, including foldout panels, not 227 physical leaves. The conservative paragraph analysis retained 33,935 clear groups, comprising 6,532 distinct spellings, on 207 paragraph-bearing page identifiers. It excluded 1,134 uncertain or specially annotated token candidates, breaking adjacency at each exclusion and drawing intrusion.

The principal key-fitting groups were Currier A, Currier B, bathing-section text, and starred paragraphs. These overlap. Separate models were not fitted to all proposed scribal hands. Most attacks used approximately 2,500 training groups and up to 1,500 test groups; the whole-group model used the full available A/B training sets. The complete paragraph test pool contains 5,735 groups.

Recto, verso, and foldout panels of a numbered leaf stayed on the same side of the key-fitting split. Previously inspected first-stage pages were forced into training. Test pages were withheld from individual key fitting but reused in exploratory hypothesis screening. They are not an untouched final confirmation set.

## Decoder families

| Family or sensitivity group | Configurations |
|---|---:|
| Ordinary fixed one-to-one substitution, raw and composite units | 64 |
| Learned ciphertext chunks with unregularized many-to-one substitution | 24 |
| Learned chunks with a fitted emission term to penalize collapse | 24 |
| Spaces removed, within-word reversal, q/y deletion, or specified gallows merges | 48 |
| Fixed letter substitution plus free within-word anagrams | 16 |
| Whole written groups mapped to letters or spaces | 8 |
| Medical/botanical Latin sensitivity using Celsus and Pliny | 32 |
| Bounded source-unit expansion into reference-learned Latin letter chunks | 16 |
| **Total** | **232** |

Ten reference corpora or adapters represent seven languages: Latin, Old Italian, Ancient Hebrew, German, Old French, Arabic, and English. Latin references include Aquinas, Dante, Celsus, and Pliny. Modern reference varieties are calibration controls, not assertions about the manuscript's source language. Corpus versions, normalizations, licenses, and hashes are in the complete package.

## Calibration and actual outcomes

The ordinary substitution solver recovered 100% of normalized held-out letters in all eight basic known-cipher controls. Across 30 calibration configurations, 26 reported exact recovery under their stated metric; the other four ranged from 99.3957% to 99.9393%. Anagram controls measure letter-key recovery, while abbreviation controls use character-weighted chunk recovery. These are not all identical metrics.

No manuscript configuration yielded a candidate that could be validated as continuous plaintext. A failed bounded search is not proof that no key exists. Scores across different language models and unitizations are not comparable language probabilities.

An unregularized Old French model appeared to have 70.2% dictionary coverage across 1,500 test groups. Its opening output was:

```text
sie ce si si ai s e ai
le ele ele is
se as i ere i le i le
```

This was a false positive caused by many ciphertext chunks collapsing onto a small number of letters and generating short vocabulary hits. Coverage among decoded words at least four characters long was only 12.6761%. The subsequent emission-profile model addressed this optimization defect but still produced no verified plaintext.

A whole-group-as-letter Latin model fitted 3,885 distinct B-group code types. Its training score was 5.9310 bits per modeled unit and its test score was 8.7276 bits; 665 test occurrences were unseen code groups. This illustrates poor generalization under an overly flexible codebook, not a decipherment.

## Contextual q result

For groups beginning with o or qo, the base was defined as the string after removing q where present. Only bases with both forms occurring at least twice in training were eligible. This is an operational pairing, not an assertion of shared meaning.

A classifier controlling base identity, Currier category, and illustration-section metadata predicted initial-q presence correctly in 67.5197% of 1,524 ZL test cases. Adding the previous group's final one and two characters improved accuracy to 76.3780%. Test log loss fell from 0.848779 to 0.728949 bits, saving 0.119831 bits per eligible case. A 3,000-draw leaf-block bootstrap across the 15 test leaves gave a descriptive 95% interval of 0.084004 to 0.145206 bits saved.

The improvement persisted in the IT and RF sensitivity checks and when uncertain gaps were merged rather than split. These are related transliterations of the same manuscript. RF is an automatically generated combination of ZL and Claston's GC, not an independent manuscript or Friedman transcription.

The result constrains contextual encoding hypotheses. It does not establish that q is silent, redundant, punctuation, a grammatical prefix, or any particular sound. Deleting q did not yield readable text under the tested fixed-key models. Related boundary effects are already described in prior literature; no claim of first discovery is made.

## Narrow invariant checks

A fixed bijection followed by completely sorting every word cannot produce a repeated symbol in separated blocks such as ABA. There were 6,004 counterexamples under raw EVA and 4,988 under the stated composite representation. This rejects only that exact model, not all anagram ciphers.

The zodiac-label metadata contains 299 loci. The conservative parser retained 271 complete clear labels, with 238 distinct strings and several within-panel duplicates. The inventory is not a simple one-to-one code consisting solely of the 30 values 1 through 30. Other numeric encodings, homophones, additional information, and contextual rules remain possible. No label meaning was recovered.

## Verification and continuation

The final 11-test suite passed. Checks include original input hashes, all 24 derived UD surface-token files, parser behavior, lossless retained-text unitization, leaf separation, corpus counts, probability normalization, independent incremental-score checking, all eight ordinary calibration keys, an independently recalculated manuscript test score and preview, finite results, and matrix completeness.

A verification helper initially used the wrong retained-test-count field name for the whole-group model. Correcting that helper changed no scientific result. Both verification logs are preserved in the package.

The next substantive model to develop is a constrained contextual forward encoder, calibrated on known plaintext before interpreting a Voynich fit. A systematic image-grounded semantic alignment, a new independent confirmation design, and a validated continuous translation remain undone. The project is IN PROGRESS.

## Artifacts and canonical sources

The full per-configuration keys, executable source, results, source data, licenses, and unchanged first-stage audit are delivered in the conversation artifacts:

- `Voynich_Round_2_Full.zip`, 40,546,895 bytes, SHA-256 `17e4b2dfba8c201160da1aa2d3bf4a8116819ccbca6107371b1f97360ecde103`.
- `Voynich_Round_2_Code_and_Results.zip`, 656,431 bytes, SHA-256 `66499e7103a89ffc111bed636595227b72989de9eb9083b253f83f096fb4168c`. Reference data can be fetched automatically from hash-checked public inputs.
- `Voynich_Round_2_Report.md`, the fuller experiment record, SHA-256 `50ef9023d065aceabd9fcafdf54e5c0631fdd204dafc775b9ad118c904b6eaad`.

This repository file is the durable benchmark summary. It does not imply that the complete binary bundles or all candidate-key files were uploaded into this repository.

Primary sources: https://www.voynich.nu/transcr.html; the exact ZL/IT/RF files and UD r2.17 file URLs in the package's manifests; https://universaldependencies.org/treebanks/la_ittb/index.html; https://universaldependencies.org/treebanks/la_udante/index.html; https://universaldependencies.org/treebanks/it_old/index.html; Tesserae-derived Celsus and Pliny inputs preserved from https://github.com/lrozanova/voynich-units/tree/66f8adaadc120f93e8a4c906685ba66a9d3ab847. Related prior work is the August 2026 preprint https://arxiv.org/html/2608.17096v1, cited as research rather than a validated translation.
