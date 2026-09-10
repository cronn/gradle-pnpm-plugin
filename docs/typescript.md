# TypeScript

**Enabled by**: `tsconfig.json` in the project directory

`tsc` takes the files it compiles from the `tsconfig.json`, and naming them on the command line
would make it ignore that file. `includes` and `excludes` therefore only describe the Gradle inputs
of the task, which is what decides when it is up to date; no pattern reaches `tsc`.

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
