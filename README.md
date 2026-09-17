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

Pin the pnpm (and, optionally, Node.js) version in the `package.json` of your workspace root with
downloads enabled:

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

The plugin registers no repository when the build sets `RepositoriesMode.PREFER_SETTINGS` or
`FAIL_ON_PROJECT_REPOS`. In this case, you need to define the repository yourself:

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

The settings describe the one pnpm installation the whole workspace shares, so configure them once,
in the build script of the workspace root. Every package inherits its values from there. Setting one
of them on a package overrides it for that project's own pnpm invocations only — pnpm is still
provisioned by the workspace root.

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

Workspace packages use the tasks provided by
Gradle's [Base Plugin](https://docs.gradle.org/current/userguide/base_plugin.html). The tasks of a
supported tool are registered exactly when the project contains a configuration file for it. Tools
contribute to the aggregate tasks and provide custom tasks with sensible defaults which should
require little to no configuration for most projects.

### Aggregate and lifecycle tasks

Every project the plugin is applied to provides the following tasks:

|    Task    |                         Description                         |
|------------|-------------------------------------------------------------|
| `check`    | Runs the verification tasks of the configured tools.        |
| `fix`      | Applies the automatic source fixes of the configured tools. |
| `test`     | Runs the test suites of the configured tools.               |
| `build`    | Runs `assemble` and `check`.                                |
| `assemble` | Assembles the outputs.                                      |
| `clean`    | Deletes the build outputs.                                  |

Prefer these tasks over the tool-specific ones: adding the configuration file of a tool to a project
makes it part of aggregate and lifecycle tasks without any further change to the build script.

Whether a test suite takes part in `check`, and therefore in `build`, is the tool's own decision: a
unit suite does, an end-to-end suite does not. Run `test` explicitly to run every suite.

### Supported tools

- [TypeScript](docs/typescript.md)
- [ESLint](docs/eslint.md)
- [Prettier](docs/prettier.md)
- [Vitest](docs/vitest.md)
- [Playwright](docs/playwright.md)

Each tool has its own extension for configuration, which is also applied to custom tasks using the
task classes provided for each tool.

## Custom pnpm tasks

|   Task type    |                    Description                    |
|----------------|---------------------------------------------------|
| `PnpmExecTask` | Runs a binary provided by a workspace dependency. |
| `PnpmRunTask`  | Runs a script declared in a `package.json`.       |
| `NodeTask`     | Runs a Node program.                              |

```kotlin
import de.cronn.pnpm.task.NodeTask
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

tasks.register<NodeTask>("generateApiClient") {
  entryPoint = layout.projectDirectory.file("scripts/generate.mjs")
  // Options for Node itself, which go before the program
  nodeOptions = listOf("--enable-source-maps")
  // Arguments for the program
  arguments = listOf("--out", "build/generated")
  outputs.dir(layout.buildDirectory.dir("generated"))
}
```

A `NodeTask` runs its program on the Node version pinned in the `devEngines.runtime` field
of the `package.json` (see [Setup](#setup)), which pnpm downloads into its own store — Node is
never installed globally.

## Development

```bash
./gradlew build               # spotless, tests, validation
./gradlew spotlessApply       # apply the formatting
./gradlew publishToMavenLocal # publish to the local Maven repository
```

### Testing the plugin locally

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

### Testing against other Gradle versions

`./gradlew build` runs the TestKit suite against the Gradle version of the wrapper only. To
additionally run it against other versions, pass them as a comma-separated list:

```bash
./gradlew functionalTest -PpnpmTestGradleVersions=9.0.0,9.7.1
```

Each version is downloaded on demand. Without the property the version-specific tests are skipped.
CI runs this tier in a separate job.

### Refreshing the dependency lockfiles

Every configuration is locked, so a dependency change fails the build until the lockfiles are
regenerated:

```bash
./gradlew dependencies --write-locks
./gradlew buildEnvironment --write-locks
```

Dependabot does not update `gradle.lockfile` for dependencies declared in the version catalog, so
its Gradle update pull requests need the lockfiles refreshed by hand.

### Releases

#### Creating a changelog entry

The changelog is assembled by [changesets](https://changesets.dev) from the files in `.changeset`.
Run `pnpm changeset add` to create a new changeset.

#### Publishing a new release

Run `./scripts/prepare-release.sh`. It consumes the pending changesets, bumps the plugin version,
and commits the result as `chore: Version plugin` together with a `v<version>` tag.

Review the commit, then push it:

```shell
git push origin main v<version>
```

The tag starts the `release` workflow, which publishes to the
[Gradle Plugin Portal](https://plugins.gradle.org) and creates the GitHub release from the changelog
entry of that version.
