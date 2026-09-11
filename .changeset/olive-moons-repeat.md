---
"gradle-pnpm-plugin": minor
---

**Breaking:** `PnpmToolTask` is now `PnpmCheckTask`, next to the new `PnpmTestTask`; a build script naming the type has to be adapted, one registering an `EslintTask`, `PrettierTask` or `TypescriptTask` does not
