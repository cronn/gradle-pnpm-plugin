# TypeScript

## Configuration

```kotlin
typescript {
  // Inputs of the task; `tsc` takes the files it checks from the `tsconfig.json`
  includes = listOf("*.ts", "src/**/*.ts", "src/**/*.tsx")
  // Excluded from the inputs
  excludes = emptyList()
  // Appended to every `tsc` invocation
  extraArguments = emptyList()
}
```

## Pre-defined tasks

The tasks are registered only when the project contains a `tsconfig.json` file, which is passed to
`tsc` as `--project`.

|        Task         | Default arguments | Contributes to |
|---------------------|-------------------|----------------|
| `compileTypescript` | none              | `check`        |

Set `configFile` on a task to pass another configuration file:

```kotlin
tasks.compileTypescript { configFile = file("tsconfig.build.json") }
```

## Custom tasks

Use `de.cronn.pnpm.task.TypescriptTask` to register custom TypeScript tasks:

```kotlin
import de.cronn.pnpm.task.TypescriptTask

tasks.register<TypescriptTask>("compileTypescriptStrict") {
  arguments = listOf("--noEmit", "--strict")
}
```
