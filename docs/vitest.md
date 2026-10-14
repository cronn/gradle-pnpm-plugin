# Vitest

## Configuration

```kotlin
vitest {
  // Inputs of the task; Vitest selects the tests it runs itself
  includes = listOf("src/**/*.ts", "src/**/*.tsx")
  // Excluded from the inputs
  excludes = emptyList()
  // Appended to every Vitest invocation
  extraArguments = emptyList()
  // Runs the suite on every invocation; a unit suite is a function of the files it runs over, so
  // this defaults to false
  alwaysRerun = false
  // Where the coverage report goes, passed as `--coverage.reportsDirectory`
  reportDirectory = layout.buildDirectory.dir("reports/vitest")
}
```

## Pre-defined tasks

The tasks are registered only when the project contains a `vitest.config.*` or a `vite.config.*`
file, which is passed to Vitest as `--config`.

|     Task     | Default arguments | Contributes to  |
|--------------|-------------------|-----------------|
| `vitestTest` | none              | `check`, `test` |

Set `configFile` on a task to pass another configuration file:

```kotlin
tasks.vitestTest { configFile = file("vitest.ci.config.ts") }
```

## Command line options

`VitestRunTask` accepts the following command line options:

|    Option    | Passed to Vitest as |
|--------------|---------------------|
| `--coverage` | `--coverage`        |
| `--update`   | `--update`          |

```bash
./gradlew vitestTest --coverage
./gradlew vitestTest --update
```

## Custom tasks

Use `de.cronn.pnpm.task.VitestRunTask` to register custom Vitest tasks:

```kotlin
import de.cronn.pnpm.task.VitestRunTask

tasks.register<VitestRunTask>("vitestUnitTest") {
  includes = listOf("src/**/*.ts")
  arguments = listOf("src")
}
```
