# Changelog

## Unreleased

Initial release. Extracted from an internal `buildSrc` and turned into a published plugin, with the
following changes over that original:

### Added

- A single `de.cronn.pnpm` plugin, applied to the workspace root and to every package alike. It
  discovers the pnpm workspace root from the `pnpm-workspace.yaml`, so the workspace root no longer
  has to be the Gradle root project, and a project with only a `package.json` works as a standalone
  package. This replaces the separate `de.cronn.pnpm-workspace`, `de.cronn.pnpm-package` and
  `de.cronn.pnpm-base` plugins.
- The workspace root gets the TypeScript, Prettier and ESLint tasks too, because a workspace root is
  a pnpm package like any other.
- Each tool is enabled by default exactly when the project holds a config file for it
  (`tsconfig.json`, `eslint.config.*` or `prettier.config.*`/`.prettierrc*`).
- A `typescript`, `prettier` and `eslint` extension per project, each with `enabled`, `include(…)`,
  `exclude(…)` and `extraArguments(…)`. The pnpm installation is configured separately, through the
  `pnpm` extension of the workspace root.
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
