# ESLint

Enabled by default when the project directory contains an `eslint.config.*` flat config file. The
legacy `.eslintrc.*` format is not detected.

|     Task      | Contributes to |           Default includes            |
|---------------|----------------|---------------------------------------|
| `eslintCheck` | `check`        | `*.ts`, `src/**/*.ts`, `src/**/*.tsx` |
| `eslintFix`   | `fix`          | `*.ts`, `src/**/*.ts`, `src/**/*.tsx` |

Both tasks depend on `compileTypescript`, and both are handed exactly the files the patterns resolve
to: `eslint <files> --max-warnings=0`, with `--fix` appended for `eslintFix`. A task whose patterns
match nothing is `NO-SOURCE`, because `eslint` without a file to work on fails instead of doing
nothing.

## Custom tasks

Both tasks are a `de.cronn.pnpm.task.EslintTask`, and so is every task a build script registers of
that type:

```kotlin
import de.cronn.pnpm.task.EslintTask

tasks.register<EslintTask>("eslintReport") {
  arguments = listOf("--format=json", "--output-file=build/eslint.json")
}
```

The `sources`, the `extraArguments` and the `enabled` state of the `eslint` extension apply to it
like they do to the predefined tasks, and it depends on the TypeScript tasks in the same way.
`sources` can be set on the task to lint a different set of files; a task that rewrites its sources
should declare `outputs.upToDateWhen { false }` the way `eslintFix` does, so that it runs on every
invocation.
