# Playwright

## Configuration

```kotlin
playwright {
  // Defaults to whether the project contains a `playwright.config.*` file
  enabled = true
  // Inputs of the task; Playwright selects the tests it runs itself
  includes = listOf("tests/**/*.ts", "src/**/*.ts")
  // Excluded from the inputs
  excludes = emptyList()
  // Appended to every Playwright invocation
  extraArguments = emptyList()
  // Runs the suite on every invocation; set it to false for a suite that really is a function of
  // the files it runs over
  alwaysRerun = true
  // Browsers `playwrightInstall` downloads; empty takes them from the Playwright configuration
  browsers = emptyList()
  // Whether `playwrightTest` depends on `playwrightInstall`; set it to false where the browsers
  // are provisioned by a container image or a CI step
  installBrowsers = true
  // Installs the system libraries the browsers need, as `--with-deps`; needs root on Linux
  installSystemDependencies = false
  // Where the artifacts of a failing test go, passed as `--output`
  outputDirectory = layout.buildDirectory.dir("playwright/test-results")
  // Where the HTML reporter writes to
  reportDirectory = layout.buildDirectory.dir("reports/playwright")
}
```

## Pre-defined tasks

|       Task       | Default arguments | Contributes to |
|------------------|-------------------|----------------|
| `playwrightTest` | none              | `test`         |

## Command line options

`PlaywrightTestTask` accepts the following command line options:

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
./gradlew playwrightTest --grep=login --update-snapshots
./gradlew playwrightTest --filter=tests/login.spec.ts:42
./gradlew playwrightTest --ui
./gradlew playwrightTest --trace=off
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
