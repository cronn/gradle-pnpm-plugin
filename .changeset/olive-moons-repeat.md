---
"gradle-pnpm-plugin": minor
---

**Breaking:** `PnpmToolTask` is now `PnpmCheckTask` and `PnpmToolExtension` is now `PnpmCheckExtension`, next to the new `PnpmTestTask`; a build script naming these types has to be adapted, one registering an `EslintTask`, `PrettierTask` or `TypescriptTask` does not
