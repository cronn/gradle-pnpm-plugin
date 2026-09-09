# gradle-pnpm-plugin

A Gradle plugin that provisions [pnpm](https://pnpm.io) and integrates a pnpm workspace into a Gradle
build.

The pnpm version is pinned once, in the `devEngines` field of `package.json`, and pnpm itself
enforces that pin. The plugin's job is just to make sure *some* pnpm is available to run in the
first place — on a fresh checkout or CI runner, nothing is installed yet. It bootstraps a pnpm from
`PATH` or from a pnpm distribution it resolves like any other dependency, then lets pnpm's own
version resolution, lockfile checks and checksum verification take it from there. It also installs the workspace dependencies and exposes
pre-defined tasks for common tools like TypeScript, Prettier and ESLint.

Requirements: **Gradle 9.0+** and **Java 21+**. Linux, macOS and Windows on x64 and arm64.

## Setup

Apply `de.cronn.gradle-pnpm-plugin` to every project that takes part in the pnpm build. There is
only one plugin id: the plugin works out what each project is from the files in its directory —
the **workspace root** has a `pnpm-workspace.yaml`, a **package** sits below it, and a project with
only a `package.json` is a **standalone package** that acts as its own workspace root.

Pin the pnpm (and, optionally, Node.js) version in the `package.json` of your workspace root:

```json
// package.json
{
  "devEngines": {
    "packageManager": {
      "name": "pnpm",
      "version": "11.25.0"
    },
    "runtime": {
      "name": "node",
      "version": "24.20.0"
    }
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

### The pnpm repository

The plugin resolves the pnpm distribution as an ordinary dependency, `pnpm:pnpm:<version>`, so it
goes through the dependency cache, dependency verification, dependency locking and the proxy
settings of your build. It does not register the repository that serves it — which repositories a
build resolves from is the decision of that build. Declare it in the workspace root:

```kotlin
// build.gradle.kts
import de.cronn.pnpm.pnpm

repositories {
  pnpm()
}
```

or centrally, in the settings script — the plugin has to be on its classpath for that:

```kotlin
// settings.gradle.kts
import de.cronn.pnpm.pnpm

plugins {
  id("de.cronn.gradle-pnpm-plugin") version "<version>" apply false
}

dependencyResolutionManagement {
  repositories { pnpm() }
}
```

`pnpm()` returns the repository it created and takes an optional configuration action, so an
internal mirror of the pnpm releases is a one-liner:

```kotlin
repositories {
  pnpm { setUrl("https://artifacts.example.com/github/pnpm/pnpm/releases/download/") }
}
```

To pin the archive by checksum, run `./gradlew pnpmSetup --write-verification-metadata sha256` and
commit `gradle/verification-metadata.xml`. Dependency locking applies to the
`pnpmDistributionArchive` configuration.

## Workspace tasks

Registered on the workspace root, in the `pnpm` group:

|     Task      |                    Description                    |
|---------------|---------------------------------------------------|
| `pnpmSetup`   | Resolves and extracts the pinned pnpm, if needed. |
| `pnpmInstall` | Runs `pnpm install`.                              |
| `pnpmDedupe`  | Runs `pnpm dedupe`.                               |
| `pnpmClean`   | Runs `pnpm clean`.                                |

Every task that runs pnpm depends on `pnpmSetup`, and every task that runs against the installed
workspace additionally depends on `pnpmInstall`.

### Configuration

```kotlin
pnpm {
  // The version downloaded when no pnpm is on the PATH
  version = "11.25.0"
  // Defaults to <workspaceRootDir>/.gradle/pnpm/<version>
  installDirectory = layout.projectDirectory.dir(".gradle/pnpm/11.25.0")
  // Skips provisioning entirely; the PATH is not consulted
  executable = "/usr/local/bin/pnpm"
}
```

The `pnpm` extension is created on the workspace root and shared by the whole workspace, so
configure it once, in the build script of the workspace root.

In CI, the pnpm distribution comes out of the Gradle dependency cache, so caching
`~/.gradle/caches/modules-2` is enough to avoid downloading it on every run. Caching the workspace
root's `.gradle/pnpm` (or an `installDirectory` you already cache) additionally skips the
extraction, and caching pnpm's own download cache skips the package downloads.

## Package tasks

Registered in every package. Tasks related to a supported tool are enabled by default exactly when the project contains a configuration file for it. For a list of pre-defined tasks, see the documentation pages of each tool:

- [TypeScript](docs/typescript.md)
- [ESLint](docs/eslint.md)
- [Prettier](docs/prettier.md)

Each tool has its own extension for configuring `includes`, `excludes`, `extraArguments` and
`enabled`:

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

Configuration defined via the available extension properties is also applied to custom tasks using the task classes provided for each tool.

## Custom pnpm tasks

`PnpmExecTask` runs a binary provided by a workspace dependency, `PnpmRunTask` runs a `package.json`
script. Both inherit the resolved pnpm executable and the dependency on `pnpmInstall`.

```kotlin
import de.cronn.pnpm.task.PnpmExecTask
import de.cronn.pnpm.task.PnpmRunTask

tasks.register<PnpmExecTask>("ngBuild") {
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

## Development

```bash
./gradlew build                                                 # spotless, unit tests, TestKit tests, validation
./gradlew spotlessApply                                         # apply the formatting
./gradlew functionalTest -PpnpmTestGradleVersions=9.0.0,9.7.1   # cross-version tier (downloads Gradle)
./gradlew publishToMavenLocal                                   # publish to the local Maven repository
```

To try out uncommitted changes in a real build, run `./gradlew publishToMavenLocal` and add the following configuration to the target project's `settings.gradle.kts`:

```kotlin
// settings.gradle.kts
import de.cronn.pnpm.pnpm

pluginManagement {
  repositories {
    mavenLocal()
  }
}

plugins {
  id("de.cronn.gradle-pnpm-plugin") version "0.0.0-SNAPSHOT" apply false
}

dependencyResolutionManagement {
  repositories {
    pnpm()
  }
}
```

### Publishing a new release

Releases are published to the [Gradle Plugin Portal](https://plugins.gradle.org) by the
`release` workflow when a `v*` tag is pushed; the version is derived from the tag.

## License

[Apache License 2.0](LICENSE)
