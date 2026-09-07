# ESLint

**Enabled by**: `eslint.config.*` in the project directory (the legacy `.eslintrc.*` is not detected)

## Pre-defined tasks

|     Task      | Default arguments |           Default includes            | Contributes to |
|---------------|-------------------|---------------------------------------|----------------|
| `eslintCheck` | `--max-warnings`  | `*.ts`, `src/**/*.ts`, `src/**/*.tsx` | `check`        |
| `eslintFix`   | `--max-warnings`  | `*.ts`, `src/**/*.ts`, `src/**/*.tsx` | `fix`          |

## Custom tasks

Use `de.cronn.pnpm.task.EslintTask` to register custom ESLint tasks:

```kotlin
import de.cronn.pnpm.task.EslintTask

tasks.register<EslintTask>("eslintReport") {
  arguments = listOf("--format=json", "--output-file=build/eslint.json")
}
```
