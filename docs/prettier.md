# Prettier

## Configuration

```kotlin
prettier {
  // Inputs of the tasks, and the operands Prettier is invoked with
  includes = listOf("*.ts", "src/**/*.ts", "src/**/*.tsx", "*.json", "*.md")
  // Excluded from the inputs, and passed as negated operands
  excludes = emptyList()
  // Appended to every Prettier invocation
  extraArguments = emptyList()
  // Passed to Prettier as `--config` by every task; defaults to the detected configuration file
  configFile = file("prettier.config.ts")
  // Defaults of every `PrettierTask`; `noErrorOnUnmatchedPattern` is `true`, the others are unset
  ignoreUnknown = true
  noErrorOnUnmatchedPattern = true
  cache = true
  cacheLocation = layout.buildDirectory.file("prettier-cache")
}
```

A task overrides these defaults with its own value:

```kotlin
tasks.prettierFix { cache = false }
```

## Pre-defined tasks

The tasks are registered only when the project contains a
`prettier.config.*` or `.prettierrc*` file, which is passed to Prettier as `--config`. Configuration
files in subdirectories are therefore not applied.

|      Task       |        Default options        | Contributes to |
|-----------------|-------------------------------|----------------|
| `prettierCheck` | `--check`                     | `check`        |
| `prettierFix`   | `--write`, `--list-different` | `fix`          |

## Command line options

`PrettierTask` accepts the following command line options:

|              Option               |        Task property        |       Passed to Prettier as       |
|-----------------------------------|-----------------------------|-----------------------------------|
| `--config=<file>`                 | `configFile`                | `--config`                        |
| `--check`                         | `check`                     | `--check`                         |
| `--write`                         | `write`                     | `--write`                         |
| `--list-different`                | `listDifferent`             | `--list-different`                |
| `--ignore-unknown`                | `ignoreUnknown`             | `--ignore-unknown`                |
| `--no-error-on-unmatched-pattern` | `noErrorOnUnmatchedPattern` | `--no-error-on-unmatched-pattern` |
| `--cache`                         | `cache`                     | `--cache`                         |
| `--cache-location=<file>`         | `cacheLocation`             | `--cache-location`                |

```bash
./gradlew prettierCheck --ignore-unknown
./gradlew prettierFix --cache --cache-location=build/prettier-cache
```

## Custom tasks

Use `de.cronn.pnpm.task.PrettierTask` to register custom Prettier tasks:

```kotlin
import de.cronn.pnpm.task.PrettierTask

tasks.register<PrettierTask>("prettierDocs") {
  includes = listOf("docs/**/*.md")
  check = true
}
```
