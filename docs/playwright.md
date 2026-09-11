# Playwright

**Enabled by**: `playwright.config.*` in the project directory

## Pre-defined tasks

|       Task       | Default arguments |        Default includes        | Contributes to |
|------------------|-------------------|--------------------------------|----------------|
| `playwrightTest` | none              | `tests/**/*.ts`, `src/**/*.ts` | `test`         |

## Command line options

`playwrightTest` accepts the following command line options:

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
