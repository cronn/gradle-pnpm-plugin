# TypeScript

## Configuration

```kotlin
typescript {
  // Defaults to whether the project contains a `tsconfig.json`
  enabled = true
  // Inputs of the task; `tsc` takes the files it checks from the `tsconfig.json`
  includes = listOf("*.ts", "src/**/*.ts", "src/**/*.tsx")
  // Excluded from the inputs
  excludes = emptyList()
  // Appended to every `tsc` invocation
  extraArguments = emptyList()
}
```

## Pre-defined tasks

|        Task         | Default arguments | Contributes to |
|---------------------|-------------------|----------------|
| `compileTypescript` | none              | `check`        |

## Custom tasks

Use `de.cronn.pnpm.task.TypescriptTask` to register custom TypeScript tasks:

```kotlin
import de.cronn.pnpm.task.TypescriptTask

tasks.register<TypescriptTask>("compileTypescriptStrict") {
  arguments = listOf("--noEmit", "--strict")
}
```
