# Changelog

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
