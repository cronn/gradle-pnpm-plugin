# Prettier

**Enabled by**: `prettier.config.*` or `.prettierrc*` in the project directory

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
  sources.setFrom(fileTree("docs") { include("**/*.md") })
  arguments = listOf("--check")
}
```
