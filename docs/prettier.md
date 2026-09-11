# Prettier

## Configuration

```kotlin
prettier {
  // Defaults to whether the project contains a `prettier.config.*` or `.prettierrc*` file
  enabled = true
  // Inputs of the tasks, and the operands Prettier is invoked with
  includes = listOf("*.ts", "src/**/*.ts", "src/**/*.tsx", "*.json", "*.md")
  // Excluded from the inputs, and passed as negated operands
  excludes = emptyList()
  // Appended to every Prettier invocation
  extraArguments = emptyList()
}
```

## Pre-defined tasks

|      Task       | Default arguments  | Contributes to |
|-----------------|--------------------|----------------|
| `prettierCheck` | none               | `check`        |
| `prettierFix`   | `--list-different` | `fix`          |

## Custom tasks

Use `de.cronn.pnpm.task.PrettierTask` to register custom Prettier tasks:

```kotlin
import de.cronn.pnpm.task.PrettierTask

tasks.register<PrettierTask>("prettierDocs") {
  includes = listOf("docs/**/*.md")
  arguments = listOf("--check")
}
```
