---
"gradle-pnpm-plugin": minor
---

The configuration file detected for a tool is now passed to its CLI (`--config`, `--project` for `tsc`), so the tool
always runs with the file that registered its tasks. Set `configFile` on a task to pass another one.

**Breaking Change**: The `configFiles` property of the tool tasks is replaced by `configFile`. Prettier no longer applies
configuration files from subdirectories.
