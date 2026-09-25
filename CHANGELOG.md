# Changelog

## 0.8.0

### Minor Changes

- 2f45260: `prettierCheck`, `prettierFix`, `eslintCheck`, `eslintFix` and `compileTypescript` now
  take the tool's configuration file as a task input, so editing it reruns the task
  instead of it being reported up to date.

  Closes #53.

- b615d49: Every pnpm task now takes the workspace's `pnpm-lock.yaml` as a task input, so upgrading a tool's pinned version reruns the
  tasks that depend on it instead of reporting them up to date.

- 9dbe0e1: **Breaking:** `check` now always depends on `test`, so that a suite a build script adds to `test` also runs as part of `check`.

  `playwrightTest` no longer takes part in `test`. Run `playwrightTest` directly, or add the dependency to `test` in the build script.

  Closes #52.

### Patch Changes

- e2c76d1: Bump default pnpm version to 11.27.1

## 0.7.0

### Minor Changes

- 022ca80: Add `--last-failed` command line option to `PlaywrightTestTask` to rerun only the tests that failed in the last run

## 0.6.0

- d60d3f1: Register the Vitest tasks for a project that only has a `vite.config.*` file
- 982f4a2: Add `NodeTask`, which runs a Node program through the Node version pinned in the `devEngines.runtime` field of the `package.json` (Closes #27)

## 0.5.1

- Format CHANGELOG

## 0.5.0

- e1f2af3: Add Vitest support. A project containing a `vitest.config.*` gets a `vitestTest` task and a `vitest` extension configuring it.

  Unlike `playwrightTest`, `vitestTest` takes part in `check` as well as `test`, so `build` runs the unit suite: a unit suite is fast and reaches nothing the build does not start. `vitest.alwaysRerun` therefore defaults to `false`, so an unchanged source tree skips the suite.

  `VitestTask` accepts `--coverage` and `--update`. The coverage report goes to `vitest.reportDirectory`, `build/reports/vitest` by default, which the task declares as an output.

- e2d7529: **Breaking:** The tasks of a tool are now registered only when the project contains a configuration file for it, instead of always being registered and skipped at execution time.

  The `enabled` property of the `typescript`, `prettier`, `eslint` and `playwright` extensions is removed; a tool configured some other way is enabled by registering a task of its type, which still picks up every convention of the tool. `fix` and `test` are still always registered.

## 0.4.0

- 04795df: Renamed `PlaywrightTask` to `PlaywrightTestTask`
- 4cfeb72: `playwrightTest` takes a `--trace=<mode>` option, passed on to Playwright as `--trace`

## 0.3.0

- 002cc3c: New Playwright support: added `playwrightTest` and `playwrightInstall` tasks, enabled by a `playwright.config.*` file
- dd98810: `includes` and `excludes` of a check task are now validated: a pattern that only Gradle's Ant
  matcher or only the tool understands fails the build naming the pattern and the property.
- 1d8db4b: **Breaking:** `compileTypescript` now behaves like `playwrightTest`: the `includes` and `excludes` are Gradle inputs only
- 002cc3c: **Breaking:** the base classes behind the extensions and tasks moved to `de.cronn.pnpm.internal` and
  are no longer API: `PnpmToolExtension`/`PnpmSourceExtension`, `PnpmCheckExtension`,
  `PnpmTestExtension`, `PnpmToolTask`/`PnpmSourceTask`, `PnpmCheckTask`, `PnpmTestTask`, `PnpmSetupTask`
  and `PlaywrightInstallTask`. The README now says what the supported surface is; a build script
  registering a `PnpmTask`, `PnpmExecTask`, `PnpmRunTask`, `TypescriptTask`, `PrettierTask`,
  `EslintTask` or `PlaywrightTask`, or configuring any of the extensions, is unaffected

## 0.2.0

- **Breaking:** `compileTypescript` no longer passes `--noEmit`; set `noEmit` in the
  `tsconfig.json` or add the argument in the task configuration to only type-check
- New `environment` on `PnpmTask`, to pass environment variables to the pnpm process; the entries
  are added to the environment of the build (Closes #23)
- **Breaking:** the tools are invoked with the `includes` and `excludes` patterns instead of the
  files they resolve to, which keeps a large source set from overrunning the command line length
  limit of Windows. The patterns now have to be understood by the tool as well, so they must be
  valid globs, and an exclude naming a directory needs a trailing `/**` (Closes #24)
- The plugin registers the repository serving the pnpm distribution itself, and steps aside when the
  build declares its repositories in `settings.gradle.kts`
- New `pnpm { repositoryUrl }`, to point the repository at a mirror or a proxy
- **Breaking:** removed `RepositoryHandler.pnpm()`; declare the repository with a plain Ivy
  declaration instead, which keeps the plugin off the settings classpath

## 0.1.0

- Support applying the plugin from a convention plugin
- Applying the plugin to a project that holds no pnpm files no longer fails
- A pnpm on `PATH` must not match the version pinned by `version`
- Remove `preferPnpmOnPath`; a pnpm on the `PATH` is always reused, whatever version it is
- Replace `setupTaskPath` and `installTaskPath` with `workspaceRootPath`
- Declare compatibility with configuration cache
- Fix: `pnpmSetup` task is not compatible with configuration cache when `preferPnpmOnPath = true`

## 0.0.2

- Configure DNS entry for Gradle verification

## 0.0.1

- Initial release
