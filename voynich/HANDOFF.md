# Voynich decipherment research handoff

## Objective and current state

Recover a reproducible decoding rule yielding continuous, independently testable plaintext. The expanded second-stage matrix was executed and verified, but **no verified decipherment, language identification, or word meaning has been established**. Overall project state is IN PROGRESS, not COMPLETE.

## Controlling sources

- `to-shreds/Misc/voynich/REPORT.md` is the durable benchmark summary.
- `to-shreds/ProjectStatus/projects/voynich-decipherment/STATUS.md` controls readiness and next steps.
- The complete source, per-configuration keys, reference data, and results are in the delivered conversation artifacts, not fully copied into this repository.
- `Voynich_Round_2_Full.zip`: SHA-256 `17e4b2dfba8c201160da1aa2d3bf4a8116819ccbca6107371b1f97360ecde103`, 40,546,895 bytes.
- `Voynich_Round_2_Code_and_Results.zip`: SHA-256 `66499e7103a89ffc111bed636595227b72989de9eb9083b253f83f096fb4168c`, 656,431 bytes. Use Files to retrieve this named artifact from the conversation or Library before requesting manual re-upload; do not assume it has a sandbox path in a future session.
- Full ZL input SHA-256: `bf5b6d4ac1e3a51b1847a9c388318d609020441ccd56984c901c32b09beccafc`. IT, RF, UD r2.17, and domain inputs have individual hashes in the package manifests.
- Public-data workflows are isolated at `.github/workflows/voynich-research-data.yml` and `.github/workflows/voynich-domain-data.yml`. Both ran successfully. Their Actions artifacts have limited retention; the full delivered ZIP is the durable data copy.

## Completed benchmark

232 manuscript configurations, 30 known-cipher calibrations, 24 scrambled comparisons, six q-context runs, sorting/label-inventory tests, and a passing 11-test regression suite. Ordinary substitution controls recovered all evaluated normalized test letters for eight reference corpora. No manuscript output was validated as continuous language. Source code consists of 15 Python scripts in the complete package.

For eligible bases appearing with and without initial q, prior endings improve held-out q-presence prediction from 67.52% to 76.38% on 1,524 ZL cases. The effect persists in the checked transliterations and gap treatments. It is structural, not a phonetic assignment, and related boundary effects predate this experiment.

## Do not break or overclaim

Preserve raw inputs, source licenses, first-stage audit, whole-token uncertainty exclusions, and adjacency breaks. Do not call the configuration matrix exhaustive. The whole corpus was parsed, but most key searches used samples. A/B and bath/starred groups overlap; all scribal hands were not separately modeled. Preserve the emission-profile correction because unregularized many-to-one decoding creates bogus short dictionary matches.

The test pages were reused across exploratory hypotheses and are not an untouched final confirmation set. Three transliterations are not independent manuscripts; RF combines ZL and GC. Statistical fit, dictionary coverage, and boundary structure do not establish a decipherment. Do not quote candidate JSON strings as translations.

Do not overwrite `Misc/HANDOFF.md`, which belongs to Zork, or alter unrelated applications.

## Verification and reproduction

The full package's `python code/reproduce.py --verify-only` passed. A source-only copy can use `python code/reproduce.py --fetch --output ./results-fresh` to fetch hash-verified inputs and recompute. Use a fresh result directory after changing code, data, or parameters. Saved JSON files are otherwise reused intentionally.

## Unresolved and next action

No acceptable continuous plaintext candidate exists. A contextual finite-state forward encoder remains unimplemented. Develop it with matched known-plaintext controls and complexity penalties before interpreting a Voynich fit. A systematic image-grounded semantic alignment and a fresh confirmation design remain undone. A future solution needs reproducible reading rules and independent semantic predictions, not free word associations.
