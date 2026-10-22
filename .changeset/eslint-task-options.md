---
"gradle-pnpm-plugin": minor
---

Add `fix`, `maxWarnings`, `cache`, `cacheLocation`, `suppressAll`, `suppressRules`, `suppressionsLocation`, `pruneSuppressions`, `passOnNoPatterns` and `noErrorOnUnmatchedPattern` properties and `--config`, `--fix`, `--max-warnings`, `--cache`, `--cache-location`, `--suppress-all`, `--suppress-rule`, `--suppressions-location`, `--prune-suppressions`, `--pass-on-no-patterns` and `--no-error-on-unmatched-pattern` command line options to `EslintTask`. Custom `EslintTask`s now pass `--max-warnings=0` by default as well

Closes #61.
