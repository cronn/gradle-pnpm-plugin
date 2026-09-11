# ESLint

## Configuration

```kotlin
eslint {
  // Defaults to whether the project contains an `eslint.config.*` file
  enabled = true
  // Inputs of the tasks, and the operands ESLint is invoked with
  includes = listOf("*.ts", "src/**/*.ts", "src/**/*.tsx")
  // Excluded from the inputs, and passed as `--ignore-pattern`
  excludes = emptyList()
  // Appended to every ESLint invocation
  extraArguments = emptyList()
}
```

## Pre-defined tasks

|     Task      | Default arguments  | Contributes to |
|---------------|--------------------|----------------|
| `eslintCheck` | `--max-warnings=0` | `check`        |
| `eslintFix`   | `--max-warnings=0` | `fix`          |

## Custom tasks

Use `de.cronn.pnpm.task.EslintTask` to register custom ESLint tasks:

```kotlin
import de.cronn.pnpm.task.EslintTask

tasks.register<EslintTask>("eslintReport") {
  arguments = listOf("--format=json", "--output-file=build/eslint.json")
}
```
