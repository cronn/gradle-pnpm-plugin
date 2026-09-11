---
"gradle-pnpm-plugin": minor
---

**Breaking:** the base classes behind the extensions and tasks moved to `de.cronn.pnpm.internal` and
are no longer API: `PnpmToolExtension`/`PnpmSourceExtension`, `PnpmCheckExtension`,
`PnpmTestExtension`, `PnpmToolTask`/`PnpmSourceTask`, `PnpmCheckTask`, `PnpmTestTask`, `PnpmSetupTask`
and `PlaywrightInstallTask`. The README now says what the supported surface is; a build script
registering a `PnpmTask`, `PnpmExecTask`, `PnpmRunTask`, `TypescriptTask`, `PrettierTask`,
`EslintTask` or `PlaywrightTask`, or configuring any of the extensions, is unaffected
