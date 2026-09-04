# gradle-pnpm-plugin

A Gradle plugin that provisions [pnpm](https://pnpm.io) and integrates a pnpm workspace into a Gradle
build.

The pnpm version is pinned once, in `package.json`, and Gradle takes care of the rest: it reuses a
matching pnpm from the `PATH` or downloads the pinned one, installs the workspace dependencies, and
exposes TypeScript, Prettier and ESLint as ordinary Gradle verification tasks.

Requirements: **Gradle 9.0+** and **Java 21+**. Linux, macOS and Windows on x64 and arm64.

## Setup

Apply `de.cronn.gradle-pnpm-plugin` to every project that takes part in the pnpm build. There is
only one plugin id: the plugin works out what each project is from the files in its directory.

|                The project's directory has                |                  The project is                  |                              It gets                               |
|-----------------------------------------------------------|--------------------------------------------------|--------------------------------------------------------------------|
| a `pnpm-workspace.yaml`                                   | the **workspace root**                           | the pnpm lifecycle tasks, the `pnpm` extension, and the tool tasks |
| no `pnpm-workspace.yaml`, but an ancestor project has one | a **package** of that workspace                  | the tool tasks                                                     |
| only a `package.json`                                     | a **standalone package**, its own workspace root | the same as a workspace root                                       |

The workspace root does not have to be the Gradle root project — a Gradle build that only embeds a
pnpm workspace in, say, `frontend/` works the same way, and its root project takes no part in the
pnpm build at all. A package's Gradle project has to be *below* its workspace root's Gradle project,
which is how the plugin finds the workspace the package belongs to.

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

Then apply the plugin, in the workspace root and in every package:

```kotlin
// build.gradle.kts
plugins {
  id("de.cronn.gradle-pnpm-plugin") version "<version>"
}
```

```kotlin
// frontend/build.gradle.kts
plugins {
  id("de.cronn.gradle-pnpm-plugin")
}
```

## How pnpm is provisioned

1. If `pnpm.executable` is set, that executable is used and nothing is downloaded.
2. Otherwise, if a pnpm on the `PATH` reports exactly the pinned version, it is reused.
3. Otherwise the pinned version is downloaded from the pnpm GitHub releases and extracted into
   `<workspaceRootDir>/.gradle/pnpm/<version>`.

Step 2 costs one `pnpm --version` call per build and is a configuration cache input, so installing
or upgrading pnpm invalidates the cache. Set `preferPnpmOnPath = false` for a fully hermetic build
that always uses the pinned version.

In CI, cache the workspace root's `.gradle/pnpm` (or set `installDirectory` to a location you
already cache) to avoid downloading pnpm on every run.

## Workspace tasks

Registered on the workspace root, in the `pnpm` group:

|     Task      |                                                                Description                                                                |
|---------------|-------------------------------------------------------------------------------------------------------------------------------------------|
| `pnpmSetup`   | Downloads and extracts the pinned pnpm. Skipped when a matching pnpm is available.                                                        |
| `pnpmInstall` | Runs `pnpm install`. Up to date as long as the workspace root's `pnpm-lock.yaml`, `pnpm-workspace.yaml` and `package.json` are unchanged. |
| `pnpmDedupe`  | Runs `pnpm dedupe`. Never up to date.                                                                                                     |
| `pnpmClean`   | Runs `pnpm clean`. Never up to date.                                                                                                      |

Every `PnpmTask` in the build automatically depends on `pnpmSetup`, and every `PnpmExecTask` and
`PnpmRunTask` additionally depends on `pnpmInstall`.

### Configuration

```kotlin
pnpm {
  // Defaults to devEngines.packageManager.version of the workspace root's package.json
  version = "11.23.0"
  // Defaults to <workspaceRootDir>/.gradle/pnpm/<version>
  installDirectory = layout.projectDirectory.dir(".gradle/pnpm/11.23.0")
  // Skips provisioning entirely
  executable = "/usr/local/bin/pnpm"
  // Reuse a matching pnpm from the PATH; defaults to true
  preferPnpmOnPath = true
}
```

The `pnpm` extension is created on the workspace root and shared by the whole workspace, so
configure it once, in the build script of the workspace root.

pnpm is downloaded from `https://github.com/pnpm/pnpm/releases/download`. To download it from
elsewhere, for example a mirror, set the `archiveUrl` of the `pnpmSetup` task:

```kotlin
import de.cronn.pnpm.task.PnpmSetupTask

tasks.named<PnpmSetupTask>("pnpmSetup") {
  archiveUrl = "https://my-mirror.example.com/pnpm-linux-x64.tar.gz"
}
```

## Tool tasks

Registered in every project, in the `verification` group. The workspace root gets them too, because a
workspace root is a pnpm package like any other:

|    Tool    |                             Enabled by                             |                              Tasks                               |
|------------|--------------------------------------------------------------------|------------------------------------------------------------------|
| TypeScript | `tsconfig.json`                                                    | `compileTypescript` (part of `check`)                            |
| ESLint     | `eslint.config.*` (the flat config; `.eslintrc.*` is not detected) | `eslintCheck` (part of `check`), `eslintFix` (part of `fix`)     |
| Prettier   | `prettier.config.*` or `.prettierrc*`                              | `prettierCheck` (part of `check`), `prettierFix` (part of `fix`) |

`eslintCheck` and `eslintFix` depend on `compileTypescript`, and `prettierFix` runs after
`eslintFix` so that formatting has the final say.

A tool is enabled by default exactly when the project contains a configuration file for it. The
tasks of a disabled tool are not run and drop out of `check` and `fix`, so a workspace root that
only holds the shared `package.json` does not get a `compileTypescript` that has nothing to compile.
Only the existence of these files is checked, which Gradle tracks as a configuration cache input, so
adding one enables the tool on the next build.

Note that Prettier and ESLint at a workspace root would descend into the package directories too, so
a root that has its own config usually wants `includes` narrowed to the files it actually owns.

### Configuration

Each tool has its own extension:

```kotlin
typescript {
  // Adds to the default patterns
  includes("types/**")
}

prettier {
  includes("docs/**")
  excludes("src/generated/**")
  extraArguments("--cache")
}

eslint {
  // Assigning replaces the default patterns instead of adding to them
  includes = listOf("app/**/*.ts")

  // Set it to false to keep eslintCheck and eslintFix out of check and fix
  enabled = true
}
```

- `includes` are the Ant-style patterns of the tool's inputs. It defaults to `*.ts`, `src/**/*.ts`
  and `src/**/*.tsx` for every tool, plus `*.json` and `*.md` for Prettier. `includes(…)` adds
  patterns to whatever is already there, assigning replaces it.
- `excludes(…)` removes patterns from the inputs. Nothing is excluded by default: the include
  patterns above reach neither `node_modules` nor the Gradle build directory.
- `extraArguments(…)` appends arguments to the tool's command line.
- `enabled` overrides the auto-detection above, in both directions.

Prettier and ESLint are handed exactly these files on the command line, so what Gradle tracks and
what the tool looks at cannot drift apart, and a tool whose patterns match nothing is skipped. `tsc`
is the exception: it takes the files to compile from `tsconfig.json`, and the patterns only describe
its Gradle inputs.

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

|      Property      |                          Purpose                          |
|--------------------|-----------------------------------------------------------|
| `arguments`        | Arguments appended after the task's own command           |
| `workingDirectory` | Directory pnpm runs in; defaults to the project directory |
| `ignoreExitValue`  | Tolerate a non-zero pnpm exit code; defaults to `false`   |
| `executable`       | Override the pnpm executable for a single task            |

## Configuration cache and project isolation

The plugin is compatible with the **configuration cache**, and the test suite fails the build on any
configuration cache problem. Reading `package.json`, probing the `PATH` and looking for the tool
config files are declared inputs, so all of them invalidate the cache when they change.

The plugin is **not** compatible with **project isolation**: a package reads the `pnpm` extension of
its workspace root, so that one pnpm installation is shared by the whole workspace.
That cross-project read is also why a package's Gradle project has to sit below its workspace root's
Gradle project.

## Development

```bash
./gradlew build                                  # spotless, unit tests, TestKit tests, validation
./gradlew spotlessApply                          # apply the formatting
./gradlew functionalTest -PpnpmTestGradleVersions=9.0.0,9.6.1   # cross-version tier (downloads Gradle)
./gradlew publishToMavenLocal                    # publish to the local Maven repository
```

### Testing a local build in another project

To try out uncommitted changes in a real build, include this repository as a composite build in the
target project's `settings.gradle.kts`:

```kotlin
// settings.gradle.kts
pluginManagement {
  includeBuild("../gradle-pnpm-plugin")
}
```

Gradle then substitutes the local build for the published plugin, so the `plugins` block stays as it
is, only without a version:

```kotlin
// build.gradle.kts
plugins {
  id("de.cronn.gradle-pnpm-plugin")
}
```

The plugin is rebuilt on demand whenever the target project's build runs, so an edit here takes
effect in the next build there — no `publishToMavenLocal` and no version bump.

### Publishing a new release

Releases are published to the [Gradle Plugin Portal](https://plugins.gradle.org) by the
`release` workflow when a `v*` tag is pushed; the version is derived from the tag.

## License

[Apache License 2.0](LICENSE)
