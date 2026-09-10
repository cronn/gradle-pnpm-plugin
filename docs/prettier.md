# Prettier

**Enabled by**: `prettier.config.*` or `.prettierrc*` in the project directory

`excludes` are passed as negated patterns (`!src/generated/**`). A negation only excludes what it
matches literally, so an exclude naming a directory needs the trailing `/**`.

## Pre-defined tasks

|      Task       | Default arguments  |                    Default includes                     | Contributes to |
|-----------------|--------------------|---------------------------------------------------------|----------------|
| `prettierCheck` | none               | `*.ts`, `src/**/*.ts`, `src/**/*.tsx`, `*.json`, `*.md` | `check`        |
| `prettierFix`   | `--list-different` | `*.ts`, `src/**/*.ts`, `src/**/*.tsx`, `*.json`, `*.md` | `fix`          |

## Custom tasks

Use `de.cronn.pnpm.task.PrettierTask` to register custom Prettier tasks:

```kotlin
import de.cronn.pnpm.task.PrettierTask

tasks.register<PrettierTask>("prettierDocs") {
  includes = listOf("docs/**/*.md")
  arguments = listOf("--check")
}
```
