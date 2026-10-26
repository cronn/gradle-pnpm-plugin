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
  // Passed to `tsc` as `--project` (or as last argument with `build`) by every task; defaults to
  // the detected configuration file
  configFile = file("tsconfig.json")
  // Defaults of every `TypescriptTask`, unset by default
  build = true
  noEmit = true
  outDir = layout.buildDirectory.dir("typescript")
}
```

A task overrides these defaults with its own value:

```kotlin
tasks.compileTypescript { noEmit = false }
```

## Pre-defined tasks

The tasks are registered only when the project contains a `tsconfig.json` file, which is passed to
`tsc` as `--project`.

|        Task         | Default arguments | Contributes to |
|---------------------|-------------------|----------------|
| `compileTypescript` | none              | `check`        |

Set `configFile` on a task to pass another configuration file to that task only:

```kotlin
tasks.compileTypescript { configFile = file("tsconfig.build.json") }
```

## Command line options

`TypescriptTask` accepts the following command line options:

|       Option       | Task property |              Passed to `tsc` as              |
|--------------------|---------------|----------------------------------------------|
| `--project=<file>` | `configFile`  | `--project`, or last argument with `--build` |
| `--build`          | `build`       | `--build`                                    |
| `--clean`          | `clean`       | `--clean`                                    |
| `--noEmit`         | `noEmit`      | `--noEmit`                                   |
| `--outDir=<dir>`   | `outDir`      | `--outDir`                                   |

```bash
./gradlew compileTypescript --noEmit
./gradlew compileTypescript --project=tsconfig.build.json --outDir=dist
./gradlew compileTypescript --build --clean
```

## Custom tasks

Use `de.cronn.pnpm.task.TypescriptTask` to register custom TypeScript tasks:

```kotlin
import de.cronn.pnpm.task.TypescriptTask

tasks.register<TypescriptTask>("compileTypescriptStrict") {
  noEmit = true
  arguments = listOf("--strict")
}

tasks.register<TypescriptTask>("buildTypescript") {
  configFile = file("tsconfig.build.json")
  outDir = layout.buildDirectory.dir("dist")
}
```
