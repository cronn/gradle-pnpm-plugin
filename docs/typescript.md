# TypeScript

Enabled by default when the project directory contains a `tsconfig.json`.

|        Task         | Contributes to |           Default includes            |
|---------------------|----------------|---------------------------------------|
| `compileTypescript` | `check`        | `*.ts`, `src/**/*.ts`, `src/**/*.tsx` |

The task runs `tsc --noEmit`, which takes the files it type checks from the `tsconfig.json`. Passing
them on the command line would make `tsc` ignore that file, so this is the one tool that is not
handed its sources: for TypeScript the patterns only describe the Gradle inputs of
`compileTypescript`, which is what decides when it is `UP-TO-DATE` and what makes it `NO-SOURCE`
when they match nothing.

## Custom tasks

`compileTypescript` is a `de.cronn.pnpm.task.TypescriptTask`, and so is every task a build script
registers of that type:

```kotlin
import de.cronn.pnpm.task.TypescriptTask

tasks.register<TypescriptTask>("compileTypescriptStrict") {
  arguments = listOf("--noEmit", "--strict")
}
```

The `sources`, the `extraArguments` and the `enabled` state of the `typescript` extension apply to
it like they do to `compileTypescript`; `sources` can be set on the task to work on a different set
of files.
