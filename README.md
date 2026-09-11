# gradle-pnpm-plugin

A Gradle plugin that provisions [pnpm](https://pnpm.io) and integrates a pnpm workspace into a
Gradle build.

The pnpm version is pinned once, in the `devEngines` field of `package.json`, and pnpm itself
enforces that pin. The plugin's job is just to make sure *some* pnpm is available to run in the
first place — on a fresh checkout or CI runner, nothing is installed yet. It bootstraps a pnpm from
`PATH` or from a pnpm distribution it resolves like any other dependency, then lets pnpm's own
version resolution, lockfile checks and checksum verification take it from there. It also installs
the workspace dependencies and exposes pre-defined tasks for common tools like TypeScript, Prettier
and ESLint.

Requirements: **Gradle 9.0+** and **Java 21+**. Linux, macOS and Windows on x64 and arm64.

## Setup

Apply `de.cronn.gradle-pnpm-plugin` to every project that takes part in the pnpm build:

```kotlin
// settings.gradle.kts
pluginManagement {
  repositories {
    gradlePluginPortal()
  }
}
```

```kotlin
// build.gradle.kts
plugins {
  id("de.cronn.gradle-pnpm-plugin") version "<version>"
}
```

Pin the pnpm (and, optionally, Node.js) version in the `package.json` of your workspace root with downloads enabled:

```json5
// package.json
{
  "devEngines": {
    "packageManager": {
      "name": "pnpm",
      "version": "11.25.0",
      "onFail": "download"
    },
    "runtime": {
      "name": "node",
      "version": "24.20.0",
      "onFail": "download"
    }
  }
}
```

### The pnpm repository

The plugin resolves the pnpm distribution as an ordinary dependency, `pnpm:pnpm:<version>`, so it
goes through the dependency cache, dependency verification, dependency locking and the proxy
settings of your build. It registers the Ivy repository serving it in the workspace root.

`repositoryUrl` points that repository at an internal mirror of the pnpm releases, or at whatever a
proxy serves them under:

```kotlin
pnpm {
  repositoryUrl = "https://artifacts.example.com/github/pnpm/pnpm/releases/download/"
}
```

The plugin registers no repository when the build sets `RepositoriesMode.PREFER_SETTINGS` or `FAIL_ON_PROJECT_REPOS`. In this case, you need to define the repository yourself:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
  repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
  repositories {
    exclusiveContent {
      forRepository {
        ivy {
          name = "pnpm"
          setUrl("https://github.com/pnpm/pnpm/releases/download/")
          patternLayout { artifact("v[revision]/[artifact]-[classifier].[ext]") }
          metadataSources { artifact() }
        }
      }
      filter { includeModule("pnpm", "pnpm") }
    }
  }
}
```

To pin the archive by checksum, run `./gradlew pnpmSetup --write-verification-metadata sha256` and
commit `gradle/verification-metadata.xml`. Dependency locking applies to the
`pnpmDistributionArchive` configuration.

## Workspace root

### Configuration

```kotlin
pnpm {
  // The version downloaded when no pnpm is on the PATH
  version = "11.25.0"
  // Defaults to <workspaceRootDir>/.gradle/pnpm/<version>
  installDirectory = layout.projectDirectory.dir(".gradle/pnpm/11.25.0")
  // Skips provisioning entirely; the PATH is not consulted
  executable = "/usr/local/bin/pnpm"
  // Where the pnpm distribution is downloaded from
  repositoryUrl = "https://github.com/pnpm/pnpm/releases/download/"
  // Gradle project path to the workspace root
  workspaceRootPath = ":frontend"
}
```

The settings describe the one pnpm installation
the whole workspace shares, so configure them once, in the build script of the workspace root. Every
package inherits its values from there. Setting one of them on a package overrides it for that
project's own pnpm invocations only — pnpm is still provisioned by the workspace root.

In CI, the pnpm distribution comes out of the Gradle dependency cache, so caching
`~/.gradle/caches/modules-2` is enough to avoid downloading it on every run. Caching the workspace
root's `.gradle/pnpm` (or an `installDirectory` you already cache) additionally skips the
extraction, and caching pnpm's own download cache skips the package downloads.

### Pre-defined tasks

|     Task      |                    Description                    |
|---------------|---------------------------------------------------|
| `pnpmSetup`   | Resolves and extracts the pinned pnpm, if needed. |
| `pnpmInstall` | Runs `pnpm install`.                              |
| `pnpmDedupe`  | Runs `pnpm dedupe`.                               |
| `pnpmClean`   | Runs `pnpm clean`.                                |

A workspace root also is a workspace package.

## Workspace packages

Workspace packages uses the tasks provided Gradle's [Base Plugin](https://docs.gradle.org/current/userguide/base_plugin.html). Tasks related to a supported tool are enabled by default exactly when
the project contains a configuration file for it. Tools contribute to the base tasks and provide custom tasks with sensible defaults which should require little to no configuration for most projects.

### Supported tools

- [TypeScript](docs/typescript.md)
- [ESLint](docs/eslint.md)
- [Prettier](docs/prettier.md)
- [Playwright](docs/playwright.md)

Each tool has its own extension for configuration, which is also applied to custom tasks using
the task classes provided for each tool.

## Custom pnpm tasks

`PnpmExecTask` runs a binary provided by a workspace dependency, `PnpmRunTask` runs a `package.json`
script.

```kotlin
import de.cronn.pnpm.task.PnpmExecTask
import de.cronn.pnpm.task.PnpmRunTask

tasks.register<PnpmExecTask>("angularBuild") {
  group = "build"
  command = "ng"
  arguments = listOf("build")
  inputs.dir(layout.projectDirectory.dir("src"))
  outputs.dir(layout.buildDirectory.dir("dist"))
}

tasks.register<PnpmRunTask>("buildFrontend") {
  script = "build:frontend"
}
```

## Development

```bash
./gradlew build               # spotless, tests, validation
./gradlew spotlessApply       # apply the formatting
./gradlew publishToMavenLocal # publish to the local Maven repository
```

To try out uncommitted changes in a real build, run `./gradlew publishToMavenLocal` and add the
following configuration to the target project's `settings.gradle.kts`:

```kotlin
// settings.gradle.kts
pluginManagement {
  repositories {
    mavenLocal()
  }
}
```

Then set the version to `0.0.0-SNAPSHOT`:

```kotlin
// settings.gradle.kts
plugins {
  id("de.cronn.gradle-pnpm-plugin") version "0.0.0-SNAPSHOT"
}
```

### Releases

#### Creating a changelog entry

The changelog is assembled by [changesets](https://changesets.dev) from the files in `.changeset`.
Run `pnpm changeset add` to create a new changeset.

#### Publishing a new release

Run `pnpm changeset version` to update the changelog and bump the plugin version, then commit the
result. Releases are published to the [Gradle Plugin Portal](https://plugins.gradle.org) by the
`release` workflow. To trigger a release, create a new tag  `v<version>` and set the generated
changelog as description.
