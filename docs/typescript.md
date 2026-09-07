# TypeScript

**Enabled by**: `tsconfig.json` in the project directory

## Pre-defined tasks

|        Task         | Default arguments |           Default includes            | Contributes to |
|---------------------|-------------------|---------------------------------------|----------------|
| `compileTypescript` | `--noEmit`        | `*.ts`, `src/**/*.ts`, `src/**/*.tsx` | `check`        |

## Custom tasks

Use `de.cronn.pnpm.task.TypescriptTask` to register custom TypeScript tasks:

```kotlin
import de.cronn.pnpm.task.TypescriptTask

tasks.register<TypescriptTask>("compileTypescriptStrict") {
  arguments = listOf("--noEmit", "--strict")
}
```
