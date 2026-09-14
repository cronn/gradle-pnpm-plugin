---
"gradle-pnpm-plugin": minor
---

**Breaking:** The tasks of a tool are now registered only when the project contains a configuration file for it, instead of always being registered and skipped at execution time.

The `enabled` property of the `typescript`, `prettier`, `eslint` and `playwright` extensions is removed; a tool configured some other way is enabled by registering a task of its type, which still picks up every convention of the tool. `fix` and `test` are still always registered.
