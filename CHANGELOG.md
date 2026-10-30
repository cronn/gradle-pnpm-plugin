# Changelog

## 0.13.0

### Minor Changes

- d4396d1: Accept `--config` on the command line of `playwrightTest`, like the other tool tasks

  ```bash
  ./gradlew :e2e:playwrightTest --config=playwright.ci.config.ts
  ```
- cb5be82: Pass the `arguments` of every tool task ahead of the options the task sets through its properties

  A task type extending `PnpmTask` now adds its arguments by overriding `contribute(CommandLine)` instead of `buildArguments()` or `commandArguments()`:

  ```kotlin
  abstract class AngularBuildTask : PnpmExecTask() {
    @get:Input abstract val configuration: Property<String>

    init {
      command.convention("ng")
    }

    override val subcommand: String = "build"

    override fun contribute(commandLine: CommandLine) {
      super.contribute(commandLine)
      commandLine.option("--configuration", configuration, joined = true)
    }
  }
  ```

### Patch Changes

- 75660fb: Fix the Vitest coverage report being written to `coverage` instead of `coverageReportsDirectory` when `coverage` is enabled

## 0.12.0

### Minor Changes

- 4f4d691: Bootstrap the pnpm version declared in `devEngines.packageManager` of `package.json`

  When the `package.json` of the workspace root declares a fixed version, the plugin downloads exactly that version instead of `pnpm.version`, so the version no longer has to be maintained in two places:

  ```json5
  // package.json
  {
    "devEngines": {
      "packageManager": {
        "name": "pnpm",
        "version": "11.28.5",
        "onFail": "download"
      }
    }
  }
  ```

  A SemVer range like `^11.0.0` still falls back to `pnpm.version`, or the version pinned by the plugin. Builds that mirror the pnpm distribution, lock dependencies or verify them now need to serve or pin the version declared in `package.json`.

- 45464b0: Add `maxWarnings`, `noErrorOnUnmatchedPattern`, `cache`, `cacheLocation`, `suppressionsLocation` and `passOnNoPatterns` properties to the `eslint` extension to set defaults for every `EslintTask`

- a582231: Add `fix`, `maxWarnings`, `cache`, `cacheLocation`, `suppressAll`, `suppressRules`, `suppressionsLocation`, `pruneSuppressions`, `passOnNoPatterns` and `noErrorOnUnmatchedPattern` properties and `--config`, `--fix`, `--max-warnings`, `--cache`, `--cache-location`, `--suppress-all`, `--suppress-rule`, `--suppressions-location`, `--prune-suppressions`, `--pass-on-no-patterns` and `--no-error-on-unmatched-pattern` command line options to `EslintTask`. Custom `EslintTask`s now pass `--max-warnings=0` by default as well

  Closes #61.

- fccb04a: Add `configFile` property to the extension of every tool to set the configuration file of all its tasks

- d36beb2: Add `ignoreUnknown`, `noErrorOnUnmatchedPattern`, `cache` and `cacheLocation` properties to the `prettier` extension to set defaults for every `PrettierTask`

- 389342d: Add `check`, `write`, `listDifferent`, `ignoreUnknown`, `noErrorOnUnmatchedPattern`, `cache` and `cacheLocation` properties and `--config`, `--check`, `--write`, `--list-different`, `--ignore-unknown`, `--no-error-on-unmatched-pattern`, `--cache` and `--cache-location` command line options to `PrettierTask`

  Closes #60.

- f8aacf2: Add `build`, `noEmit` and `outDir` properties to the `typescript` extension to set defaults for every `TypescriptTask`

- 326199d: Add `build`, `clean`, `noEmit` and `outDir` properties and `--config`, `--build`, `--clean`, `--noEmit` and `--outDir` command line options to `TypescriptTask`

  Closes #58.

- cd20779: **Breaking Change**: `reportDirectory` of the `vitest` extension and `VitestRunTask` is renamed to `coverageReportsDirectory`, matching the `--coverage.reportsDirectory` option of Vitest it is passed as:

  ```kotlin
  vitest {
    coverageReportsDirectory = layout.buildDirectory.dir("reports/coverage")
  }
  ```

  `VitestRunTask` also accepts it as the `--coverageReportsDirectory` command line option.

- aca20c7: Add `coverage`, `passWithNoTests`, `dir` and `outputFile` properties to the `vitest` extension to set defaults for every `VitestRunTask`

- 94602f9: Add `testNamePattern`, `dir`, `outputFile`, `passWithNoTests` and `bail` properties and `--config`, `--testNamePattern`, `--dir`, `--outputFile`, `--passWithNoTests` and `--bail` command line options to `VitestRunTask`

  Closes #80.

### Patch Changes

- 82fb359: `TypescriptTask` passes its `configFile` as the last argument instead of `--project` when `build` is enabled

  Closes #87.

## 0.11.0

### Minor Changes

- 24bd53a: The configuration file detected for a tool is now passed to its CLI (`--config`, `--project` for `tsc`), so the tool
  always runs with the file that registered its tasks. Set `configFile` on a task to pass another one.

  **Breaking Change**: The `configFiles` property of the tool tasks is replaced by `configFile`. Prettier no longer applies
  configuration files from subdirectories.

- ee25775: **Breaking Change**: `VitestTask` is renamed to `VitestRunTask` to make the `vitest run` subcommand explicit. Update
  custom tasks:

  ```kotlin
  import de.cronn.pnpm.task.VitestRunTask

  tasks.register<VitestRunTask>("vitestUnitTest")
  ```
- 934cc70: **Breaking Change**: The `arguments` of `VitestRunTask` and `PlaywrightTestTask` no longer include the `run` and `test`
  subcommands, which are now always passed by the tasks themselves. Remove them from custom `arguments`:

  ```kotlin
  tasks.register<VitestRunTask>("vitestUnitTest") {
    arguments = listOf("src")
  }
  ```

## 0.10.0

### Minor Changes

- 9ce9e4f: **Breaking Change**: pnpm is no longer resolved from `PATH`. The plugin always provisions the pinned pnpm version unless
  `pnpm.executable` is set.

### Patch Changes

- a7e9fd2: `pnpmInstall` now also runs again when the `package.json` of a package changes.
- cedcb8e: Bump default pnpm version to v11.28.5

## 0.9.0

### Minor Changes

- a747d3d: Add `projects` property and `--project` command line option to `PlaywrightTestTask` to run only selected Playwright projects

  Closes #56.

- 0a614e1: Make `eslintFix` and `prettierFix` up to date when the sources did not change since their last run

  Closes #63.

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
