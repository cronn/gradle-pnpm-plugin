# Changelog

## Unreleased

Initial release. Extracted from an internal `buildSrc` and turned into a published plugin, with the
following changes over that original:

### Added

- `de.cronn.pnpm-base` plugin for projects that only need the pnpm task types.
- `pnpmPackage` extension with `enabled`, `include(…)`, `exclude(…)` and `extraArguments(…)` per
  tool, replacing the three separate top-level extensions.
- Configurable `downloadBaseUrl`, `archiveUrl`, `archiveSha256`, `installDirectory`, `version`,
  `packageJson`, `executable` and `preferPnpmOnPath` on the `pnpm` extension.
- `workingDirectory`, `ignoreExitValue` and `pnpmVersion` on `PnpmTask`.
- Unit and Gradle TestKit test suites, including a cross-Gradle-version tier.

### Fixed

- `prettierFix.mustRunAfter(eslintFix)` referenced `eslintFix` before it was declared, which only
  worked by accident.
- `pnpmDedupe` and `pnpmClean` declared outputs but no inputs, so they reported `UP-TO-DATE` and
  silently did nothing after their first run.
- `pnpmInstall` declared `pnpm-lock.yaml` as both an input and an output, and declared the
  `node_modules` symlink farm as an output. It now has a stamp file as its output.
- `prettierFix` and `eslintFix` declared the same file tree as both inputs and outputs.
- `package.json` was read and `pnpm --version` executed eagerly on every configuration, without
  being declared as configuration cache inputs, so changing either did not invalidate the cache.
- A pnpm version change did not invalidate the tool tasks, because only the executable path was
  tracked.
- The internal Gradle API `org.gradle.internal.os.OperatingSystem` is no longer used.
- `findPnpmOnPath` no longer relies on the executable bit, which is meaningless on Windows.
- An interrupted download could leave a truncated archive behind that a later run would extract.
- Downloads now have connect and read timeouts, so an unresponsive mirror no longer hangs the build.
- `PnpmSetupTask` no longer assumes a flat archive layout and reports the archive contents when the
  pnpm executable cannot be found.
- The `fix` task has a group and a description, so it shows up in `./gradlew tasks`.
