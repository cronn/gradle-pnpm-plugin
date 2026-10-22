# ESLint

## Configuration

```kotlin
eslint {
  // Inputs of the tasks, and the operands ESLint is invoked with
  includes = listOf("*.ts", "src/**/*.ts", "src/**/*.tsx")
  // Excluded from the inputs, and passed as `--ignore-pattern`
  excludes = emptyList()
  // Appended to every ESLint invocation
  extraArguments = emptyList()
  // Passed to ESLint as `--config` by every task; defaults to the detected configuration file
  configFile = file("eslint.config.ts")
}
```

## Pre-defined tasks

The tasks are registered only when the project contains an `eslint.config.*`
file, which is passed to ESLint as `--config`.

|     Task      |       Default options       | Contributes to |
|---------------|-----------------------------|----------------|
| `eslintCheck` | `--max-warnings=0`          | `check`        |
| `eslintFix`   | `--max-warnings=0`, `--fix` | `fix`          |

Every `EslintTask` passes `--max-warnings=0` and `--no-error-on-unmatched-pattern` unless
`maxWarnings` or `noErrorOnUnmatchedPattern` is set otherwise.

Set `configFile` on a task to pass another configuration file to that task only:

```kotlin
tasks.eslintCheck { configFile = file("eslint.strict.config.ts") }
```

## Command line options

`EslintTask` accepts the following command line options:

|              Option               |        Task property        |        Passed to ESLint as        |
|-----------------------------------|-----------------------------|-----------------------------------|
| `--config=<file>`                 | `configFile`                | `--config`                        |
| `--fix`                           | `fix`                       | `--fix`                           |
| `--max-warnings=<n>`              | `maxWarnings`               | `--max-warnings`                  |
| `--cache`                         | `cache`                     | `--cache`                         |
| `--cache-location=<file>`         | `cacheLocation`             | `--cache-location`                |
| `--suppress-all`                  | `suppressAll`               | `--suppress-all`                  |
| `--suppress-rule=<rule>`          | `suppressRules`             | `--suppress-rule`                 |
| `--suppressions-location=<file>`  | `suppressionsLocation`      | `--suppressions-location`         |
| `--prune-suppressions`            | `pruneSuppressions`         | `--prune-suppressions`            |
| `--pass-on-no-patterns`           | `passOnNoPatterns`          | `--pass-on-no-patterns`           |
| `--no-error-on-unmatched-pattern` | `noErrorOnUnmatchedPattern` | `--no-error-on-unmatched-pattern` |
| none                              | `excludes`                  | `--ignore-pattern`                |

```bash
./gradlew eslintCheck --max-warnings=10
./gradlew eslintCheck --suppress-rule=no-console --suppress-rule=no-undef
```

## Custom tasks

Use `de.cronn.pnpm.task.EslintTask` to register custom ESLint tasks:

```kotlin
import de.cronn.pnpm.task.EslintTask

tasks.register<EslintTask>("eslintSuppress") {
  suppressAll = true
}

tasks.register<EslintTask>("eslintReport") {
  arguments = listOf("--format=json", "--output-file=build/eslint.json")
}
```
