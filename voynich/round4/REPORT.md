# Voynich Round 4: abbreviations and hidden boundaries

**No verified continuous plaintext, source-language identification or word meaning was established.** The variable-length, boundary-free experiment and its constrained extension were executed and checked. This repository file is the durable benchmark summary; the fuller report, complete runnable source, all candidate outputs and reference data are in the conversation package identified below.

## Model and sample

Apparent spaces are ignored within retained clear runs, never across uncertainty/drawing breaks. Source units are either raw EVA or the fixed composite inventory. Each source unit maps injectively to a plaintext letter or one of eight reference-selected bigrams; a zero-abbreviation baseline is included. Target bigrams are parsed by a deterministic greedy rule and may cross hidden plaintext word boundaries. The source inventory is assumed, not discovered. This is a bounded static variable-length model, not a general contextual or hidden-state transducer.

Word spaces are proposed separately using reference-only unigram/unknown-word Viterbi segmentation. The segmenter changes no letters. Erasing spaces loses information, so exact letter-stream recovery does not guarantee original word divisions. Dictionary matches are circular evidence because the segmenter itself uses a dictionary.

Reference models use Pliny Latin, Old Italian, German and Ancient Hebrew. Exact Round 3 A/B and negative-control samples are reused. Training has 2,501 A groups and 2,500 B groups; development has 848 and 3,278; confirmation has 399 and 1,210, totaling 1,609 groups on five numbered leaves before representation-specific exclusions. Rare unsupported source units break the stream and are recorded. No previously unseen study-wide or independent confirmation sample is claimed.

## Known-cipher calibration

Sixteen controls combine four references, zero/eight abbreviations and two code permutations, with false visible gaps. Each uses 2,500 fitting words and 1,200 separate evaluation words. The code key and plaintext are hidden from fitting, but the assumed source units and reference abbreviation inventory are supplied.

An initial six-start search failed one Hebrew abbreviation control at 7.1205% character-weighted unit recovery. The other fifteen controls were exact. All initial results and code were retained. Before any manuscript fitting, all configurations were changed to eighteen starts of 18,000 proposals and all controls rerun. Every final control recovered every evaluated letter and re-encoded its source stream exactly. The 90,384 character-weighted evaluations repeat each language's test text across four variants and are not distinct underlying characters.

Correct-letter-stream word-boundary F1 was 93.78% for Latin, 92.98% for Old Italian, 94.05% for German and 86.48% for Ancient Hebrew. F1 measures boundary precision/recall, not translated words or semantic accuracy.

## Primary results and strict extension

The primary matrix has 96 fits: 32 manuscript configurations and 64 matched null configurations. The nulls are within-run shuffled whole groups and one positional-Markov realization per sample. They are not independent replications or a significance distribution. All primary keys were frozen before primary confirmation.

A candidate must reproduce its source stream under the same forward rule. For example, when `th` is a required greedy abbreviation, independently emitting `t` followed by a unit starting in `h` is invalid: encoding those letters again selects `th`, changing the code stream. The language model's tiny probability floor is a search convenience, not permission to accept such a candidate.

For the eight-abbreviation fits, the whole confirmation sample re-encoded for 4/16 manuscript keys, 3/16 shuffled keys and 1/16 generated-text keys. Across training, development and confirmation together, the counts were 0/16, 0/16 and 1/16. Thus every primary manuscript abbreviation key failed its own forward mechanism somewhere in the tested samples.

A separately frozen post-primary extension refitted all 48 abbreviation configurations using only training constraints. Binary assignment forbids every illegal observed training adjacency and minimizes frequency-weighted changes from the primary key. Two primary keys were already feasible; the other 46 assignment searches returned verified status-0 solutions. Those optima concern only the stated repair objective. Six starts of 18,000 feasible-only swap proposals then optimize language likelihood without violating training constraints. This is not a globally optimal cipher search.

The extension passes feasible/infeasible miniature tests against exhaustive enumeration and preserves all eight already-recovered abbreviation controls. The latter are regression checks from recovered keys, not eight new blind repair calibrations. Keys were frozen before rescoring the already used development and confirmation data.

| Input | All training runs obey rule | All development runs obey rule | All confirmation runs obey rule | All three samples obey rule |
|---|---:|---:|---:|---:|
| Manuscript | 16/16 | 5/16 | 7/16 | 3/16 |
| Shuffled groups | 16/16 | 5/16 | 10/16 | 5/16 |
| Generated text | 16/16 | 7/16 | 12/16 | 6/16 |

The three manuscript survivors are A/Hebrew/raw, A/Old-Italian/composite and A/German/raw. Those labels identify reference models, not recovered languages. None was established as coherent continuous plaintext. The mechanically consistent Old Italian reference candidate begins:

```text
chiaganerasagamania sian chiama li a ch io lanchita co
mi e anga a si o si vana fio onsavan via la tan more van m
dava savia n moria gandaviania la san van man man
```

This is candidate output, not a translation.

## Word-order diagnostic

A secondary reference-word-bigram check, fixed before primary fitting, compares each inferred word sequence with 100 shuffles of its own words. It does not fit keys or select gaps. Unadjusted rank p below .05 occurred in 4/32 primary manuscript configurations, 5/32 shuffled-source configurations and 1/32 generated-source configurations. In the strict extension, counts were 3/16, 3/16 and 0/16. One manuscript and one shuffled-source candidate pass both all-sample forward consistency and this word-order threshold.

These are exploratory, unadjusted statistics across correlated models. They do not establish a language. Even genuine reference texts have negative absolute bigram-minus-unigram gain under this sparse model, so a negative absolute value is not itself evidence of meaninglessness. All candidate outputs, segmentation proposals, exclusions and 100-shuffle vectors are preserved.

## Verification and actual fresh recomputation

The primary 16-test suite and strict eight-test suite passed without errors or failures. Checks independently recalculate all 144 confirmation scores and source-to-output mappings, all strict development/training scores, every known-control recovery, exact forward consistency, assignment constraints/objectives, miniature exhaustive cases, segmentation, frozen hashes, source coverage and word-order records.

A complete primary run was genuinely repeated in a fresh directory: sixteen calibrations, four boundary tests, 96 key fits, 96 confirmations and 96 word-order diagnostics. Keys and compared scientific fields matched exactly, including full predictions and shuffle vectors. Runtime metadata and dependent timestamps/hashes were excluded from comparison. This claim does not include a second full strict-extension fitting run; that extension has independently recalculated saved-result verification.

All 83 retained input/vendor files were byte-compared with the verified Round 3 package. Earlier research archives remain unchanged, including all eight first-stage audit members compared with the verified Round 2 copy. Source and dataset hashes, initial failures and protocol amendments remain preserved.

## Artifacts and continuation

Conversation deliverables, not binary uploads to this repository:

- `Voynich_Round_4_Full.zip`: 28,123,330 bytes; SHA-256 `28fb4555fa59645bb096d4fd76bad9bdbefac9931170a95734512e5d9a2f0df0`.
- `Voynich_Round_4_Report.md`: 19,103 bytes; SHA-256 `5ba6317544bd2f7803452e82b9e1937f0251f776d031e8dae86fd2945b7b9fe5`.

The ZIP has 557 files, including 11 new Python scripts, two retained vendor modules, full reference data/licenses, all keys/outputs and verification/recomputation records. It omits rebuildable caches and does not duplicate earlier round ZIPs. Run `python code/reproduce_all.py --verify-only` or `python code/reproduce_all.py --recompute ../Voynich_Round_4_Recomputed --workers 2`; the recomputation destination must not exist. The lower-level `reproduce.py` covers the primary stage only.

The tested constrained module is also published at `round4/code/strict.py`. It depends on files in the complete package and is not a standalone repository installation. The attempted repository upload of the base decoder was blocked; its tested source remains in the complete ZIP. No claim is made that the entire runnable package was copied into GitHub.

The experiment is complete, but the decipherment objective remains IN PROGRESS. The next distinct task is to infer a limited source segmentation and target expansion inventory jointly, first demonstrating recovery when the source-unit inventory is hidden. A future candidate must retain exact forward consistency and make independent semantic or cross-label predictions. Merely increasing language-model flexibility is not a solution.

Sources: https://www.voynich.nu/transcr.html; https://docs.scipy.org/doc/scipy/reference/generated/scipy.optimize.milp.html. Exact corpus versions, original URLs and licenses are in the package manifests. Related unit-analysis research at https://arxiv.org/html/2608.17096v1 is a preprint, not a translation or authority for these computed results. The complete numerical record is the package's `results/BENCHMARK.json`.
