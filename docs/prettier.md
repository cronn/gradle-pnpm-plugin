# Prettier

Enabled by default when the project directory contains a `prettier.config.*` or a `.prettierrc*`
file.

|      Task       | Contributes to |                    Default includes                     |
|-----------------|----------------|---------------------------------------------------------|
| `prettierCheck` | `check`        | `*.ts`, `src/**/*.ts`, `src/**/*.tsx`, `*.json`, `*.md` |
| `prettierFix`   | `fix`          | `*.ts`, `src/**/*.ts`, `src/**/*.tsx`, `*.json`, `*.md` |

Both tasks are handed exactly the files the patterns resolve to: `prettier <files> --check` for
`prettierCheck`, `prettier <files> --write --list-different` for `prettierFix`. A task whose
patterns match nothing is `NO-SOURCE`, because `prettier` without a file to work on fails instead of
doing nothing. `prettierFix` runs after `eslintFix`, so that formatting has the final say over
ESLint's automatic fixes.

## Custom tasks

Both tasks are a `de.cronn.pnpm.task.PrettierTask`, and so is every task a build script registers of
that type:

```kotlin
import de.cronn.pnpm.task.PrettierTask

tasks.register<PrettierTask>("prettierDocs") {
  sources.setFrom(fileTree("docs") { include("**/*.md") })
  arguments = listOf("--check")
}
```

The `extraArguments` and the `enabled` state of the `prettier` extension apply to it like they do to
the predefined tasks, and `sources` defaults to the extension's patterns when the task does not set
it. A task that rewrites its sources should declare `outputs.upToDateWhen { false }` the way
`prettierFix` does, so that it runs on every invocation.
