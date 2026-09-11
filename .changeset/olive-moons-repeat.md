---
"gradle-pnpm-plugin": minor
---

**Breaking:** `PnpmToolTask` is now `PnpmCheckTask` and `PnpmToolExtension` is now
`PnpmSourceExtension`, with `PnpmSourceTask`, the narrower `PnpmCheckExtension` and
`PnpmTestTask`/`PnpmTestExtension` between them; a build script naming these types has to be
adapted, one registering an `EslintTask`, `PrettierTask`, `TypescriptTask` or `PlaywrightTask` does
not
