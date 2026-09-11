# Playwright

**Enabled by**: `playwright.config.*` in the project directory

Playwright picks the tests it runs itself, from its configuration file and from the command line
options of the task. `includes` and `excludes` therefore only describe the Gradle inputs of the
task.

## Pre-defined tasks

|        Task         | Default arguments  |        Default includes        |  Contributes to  |
|---------------------|--------------------|--------------------------------|------------------|
| `playwrightTest`    | `test`, `--output` | `tests/**/*.ts`, `src/**/*.ts` | `test`           |
| `playwrightInstall` | `install`          | none                           | `playwrightTest` |

`test` is a lifecycle task of the project that every test tool contributes to. `check` deliberately
does not depend on it: an end-to-end suite is slow and usually needs a server the build does not
start. Add the edge where that is not so:

```kotlin
tasks.check { dependsOn(tasks.test) }
```

`playwrightTest` runs on every invocation. A browser suite reaches a backend, a database or a
fixture server, and none of those is a Gradle input, so unchanged inputs say nothing about whether
the last result still holds. Set `alwaysRerun` to `false` for a suite that really is a function of
the files it runs over; it is then skipped while its sources and its Playwright configuration are
unchanged.

## Command line options

What a run is usually varied by is an option of `playwrightTest`, so an ad-hoc run needs no edit to
the build script. `./gradlew help --task playwrightTest` lists them all; anything else Playwright
takes goes into the `arguments` of the task or the `extraArguments` of the extension.

|        Option        |                                                  Passed to Playwright as                                                  |
|----------------------|---------------------------------------------------------------------------------------------------------------------------|
| `--ui`               | `--ui`                                                                                                                    |
| `--headed`           | `--headed`                                                                                                                |
| `--update-snapshots` | `--update-snapshots`                                                                                                      |
| `--fail-fast`        | `-x`                                                                                                                      |
| `--grep=<regex>`     | `--grep`                                                                                                                  |
| `--filter=<filter>`  | an operand: a regex matched against the path of a test file, optionally suffixed with `:<line>`; repeat for more than one |
| `--repeat-each=<n>`  | `--repeat-each`, and `-x` with it                                                                                         |
| `--trace=<mode>`     | `--trace`; one of `on`, `off`, `on-first-retry`, `on-all-retries`, `retain-on-failure`, `retain-on-first-failure`         |

```bash
./gradlew :e2e:playwrightTest --grep=login --update-snapshots
./gradlew :e2e:playwrightTest --filter=tests/login.spec.ts:42
./gradlew :e2e:playwrightTest --ui
./gradlew :e2e:playwrightTest --trace=off
```

## Configuration

```kotlin
playwright {
  // Only these browsers are downloaded; empty takes them from the Playwright configuration
  browsers = listOf("chromium")
  // Installs the system libraries the browsers need; needs root on Linux
  installSystemDependencies = true
  // Set it to false where the browsers are provisioned by a container image or a CI step
  installBrowsers = true
  // Set it to false for a suite that really is a function of the files it runs over
  alwaysRerun = false
  // Where the artifacts of a failing test and the HTML report go
  outputDirectory = layout.buildDirectory.dir("playwright/test-results")
  reportDirectory = layout.buildDirectory.dir("reports/playwright")
}
```

## Custom tasks

Use `de.cronn.pnpm.task.PlaywrightTestTask` to register custom Playwright tasks:

```kotlin
import de.cronn.pnpm.task.PlaywrightTestTask

tasks.register<PlaywrightTestTask>("playwrightSmokeTest") {
  includes = listOf("tests/smoke/**/*.ts")
  arguments = listOf("tests/smoke")
}
```
