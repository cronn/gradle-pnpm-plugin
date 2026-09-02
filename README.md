# gradle-pnpm-plugin

Gradle plugins that provision [pnpm](https://pnpm.io) and integrate a pnpm workspace into a Gradle
build.

The pnpm version is pinned once, in `package.json`, and Gradle takes care of the rest: it reuses a
matching pnpm from the `PATH` or downloads the pinned one, installs the workspace dependencies, and
exposes TypeScript, Prettier and ESLint as ordinary Gradle verification tasks.

| Plugin | Applied to | Purpose |
| --- | --- | --- |
| `de.cronn.pnpm-workspace` | root project | Provisions pnpm and adds the workspace lifecycle tasks |
| `de.cronn.pnpm-package` | each workspace package | Adds the TypeScript, Prettier and ESLint tasks |
| `de.cronn.pnpm-base` | any project | Only the pnpm task types and executable resolution |

Requirements: **Gradle 9.0+** and **Java 17+**. Linux, macOS and Windows on x64 and arm64.

## Setup

Pin the pnpm version in the `package.json` of your workspace root:

```json
{
  "name": "root",
  "private": true,
  "devEngines": {
    "packageManager": { "name": "pnpm", "version": "11.23.0" }
  }
}
```

Apply the workspace plugin to the root project:

```kotlin
// build.gradle.kts
plugins {
  id("de.cronn.pnpm-workspace") version "<version>"
}
```

And the package plugin to every workspace package:

```kotlin
// frontend/build.gradle.kts
plugins {
  id("de.cronn.pnpm-package")
}
```

## How pnpm is provisioned

1. If `pnpm.executable` is set, that executable is used and nothing is downloaded.
2. Otherwise, if a pnpm on the `PATH` reports exactly the pinned version, it is reused.
3. Otherwise the pinned version is downloaded from the pnpm GitHub releases and extracted into
   `<rootDir>/.gradle/pnpm/<version>`.

Step 2 costs one `pnpm --version` call per build and is a configuration cache input, so installing
or upgrading pnpm invalidates the cache. Set `preferPnpmOnPath = false` for a fully hermetic build
that always uses the pinned version.

In CI, cache `.gradle/pnpm` (or set `installDirectory` to a location you already cache) to avoid
downloading pnpm on every run.

## Workspace tasks

Registered by `de.cronn.pnpm-workspace` on the root project, in the `pnpm` group:

| Task | Description |
| --- | --- |
| `pnpmSetup` | Downloads and extracts the pinned pnpm. Skipped when a matching pnpm is available. |
| `pnpmInstall` | Runs `pnpm install`. Up to date as long as `pnpm-lock.yaml`, `pnpm-workspace.yaml` and the root `package.json` are unchanged. |
| `pnpmDedupe` | Runs `pnpm dedupe`. Never up to date. |
| `pnpmClean` | Runs `pnpm clean`. Never up to date. |

Every `PnpmTask` in the build automatically depends on `pnpmSetup`, and every `PnpmExecTask` and
`PnpmRunTask` additionally depends on `pnpmInstall`.

### Configuration

```kotlin
pnpm {
  // Defaults to <rootDir>/package.json
  packageJson = layout.projectDirectory.file("package.json")
  // Defaults to devEngines.packageManager.version of the package.json above
  version = "11.23.0"
  // Defaults to <rootDir>/.gradle/pnpm/<version>
  installDirectory = layout.projectDirectory.dir(".gradle/pnpm/11.23.0")
  // Defaults to https://github.com/pnpm/pnpm/releases/download
  downloadBaseUrl = "https://my-mirror.example.com/pnpm"
  // Defaults to <downloadBaseUrl>/v<version>/pnpm-<platform>.<tar.gz|zip>
  archiveUrl = "https://my-mirror.example.com/pnpm-linux-x64.tar.gz"
  // Verified after download when set
  archiveSha256 = "…"
  // Skips provisioning entirely
  executable = "/usr/local/bin/pnpm"
  // Reuse a matching pnpm from the PATH; defaults to true
  preferPnpmOnPath = true
}
```

The `pnpm` extension is created on the root project and shared by the whole build, so configure it
once, in the root build script.

## Package tasks

Registered by `de.cronn.pnpm-package`, in the `verification` group:

| Task | Command | Wired into |
| --- | --- | --- |
| `compileTypescript` | `pnpm exec tsc --noEmit` | `check` |
| `prettierCheck` | `pnpm exec prettier . --check` | `check` |
| `eslintCheck` | `pnpm exec eslint . --max-warnings=0` | `check` |
| `prettierFix` | `pnpm exec prettier . --write --list-different` | `fix` |
| `eslintFix` | `pnpm exec eslint . --max-warnings=0 --fix` | `fix` |

`eslintCheck` and `eslintFix` depend on `compileTypescript`, and `prettierFix` runs after
`eslintFix` so that formatting has the final say.

### Configuration

Each tool is configured through the `pnpmPackage` extension:

```kotlin
pnpmPackage {
  typescript {
    include("src/**")
  }
  prettier {
    include("src/**", "docs/**")
    exclude("src/generated")
    extraArguments("--cache")
  }
  eslint {
    // No eslintCheck/eslintFix tasks take part in check and fix
    enabled = false
  }
}
```

- `include(…)` adds Ant-style patterns to the tool's inputs, on top of its defaults
  (`eslint.config.ts` and `prettier.config.ts` for TypeScript, `*.ts`, `*.json` and `*.md` for
  Prettier, `*.ts` for ESLint).
- `exclude(…)` removes patterns from the inputs.
- `extraArguments(…)` appends arguments to the tool's command line.
- `enabled` defaults to `true`; a disabled tool is skipped and removed from `check` and `fix`.

Declaring the right inputs is what makes the check tasks skippable: a task whose sources have not
changed is `UP-TO-DATE`.

## Custom pnpm tasks

`PnpmExecTask` runs a binary provided by a workspace dependency, `PnpmRunTask` runs a `package.json`
script. Both inherit the resolved pnpm executable and the dependency on `pnpmInstall`.

```kotlin
import de.cronn.pnpm.task.PnpmExecTask
import de.cronn.pnpm.task.PnpmRunTask

val generateApiClients = tasks.register<PnpmExecTask>("generateApiClients") {
  group = "build"
  command = "openapi-ts"
  inputs.file(layout.projectDirectory.file("openapi-ts.config.ts"))
  outputs.dir(layout.projectDirectory.dir("src/generated/api"))
}

tasks.register<PnpmExecTask>("ngBuild") {
  dependsOn(generateApiClients)
  group = "build"
  command = "ng"
  arguments = listOf("build")
  inputs.dir(layout.projectDirectory.dir("src"))
  outputs.dir(layout.buildDirectory.dir("dist"))
}

tasks.register<PnpmRunTask>("buildFrontend") {
  script = "build"
}
```

Both types support:

| Property | Purpose |
| --- | --- |
| `arguments` | Arguments appended after the task's own command |
| `workingDirectory` | Directory pnpm runs in; defaults to the project directory |
| `ignoreExitValue` | Tolerate a non-zero pnpm exit code; defaults to `false` |
| `executable` | Override the pnpm executable for a single task |

## Configuration cache and project isolation

The plugins are compatible with the **configuration cache**, and the test suite fails the build on
any configuration cache problem. Reading `package.json` and probing the `PATH` are declared inputs,
so both invalidate the cache when they change.

The plugins are **not** compatible with **project isolation**: the `pnpm` extension lives on the
root project and is read by the other projects, so that one pnpm installation is shared by the whole
build. If you need isolation today, apply `de.cronn.pnpm-base` per project and configure
`executable` explicitly.

## Development

```bash
./gradlew build                                  # spotless, unit tests, TestKit tests, validation
./gradlew spotlessApply                          # apply the formatting
./gradlew functionalTest -PpnpmTestGradleVersions=9.0.0,9.6.1   # cross-version tier (downloads Gradle)
./gradlew publishToMavenLocal                    # try it out in another build
```

Releases are published to the [Gradle Plugin Portal](https://plugins.gradle.org) by the
`release` workflow when a `v*` tag is pushed; the version is derived from the tag.

## License

[Apache License 2.0](LICENSE)
