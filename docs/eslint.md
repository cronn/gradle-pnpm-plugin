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
}
```

## Pre-defined tasks

The tasks are registered only when the project contains an `eslint.config.*`
file, which is passed to ESLint as `--config`.

|     Task      | Default arguments  | Contributes to |
|---------------|--------------------|----------------|
| `eslintCheck` | `--max-warnings=0` | `check`        |
| `eslintFix`   | `--max-warnings=0` | `fix`          |

Set `configFile` on a task to pass another configuration file:

```kotlin
tasks.eslintCheck { configFile = file("eslint.strict.config.ts") }
```

## Custom tasks

Use `de.cronn.pnpm.task.EslintTask` to register custom ESLint tasks:

```kotlin
import de.cronn.pnpm.task.EslintTask

tasks.register<EslintTask>("eslintReport") {
  arguments = listOf("--format=json", "--output-file=build/eslint.json")
}
```
