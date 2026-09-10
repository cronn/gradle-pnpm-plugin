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

Apply `de.cronn.gradle-pnpm-plugin` to every project that takes part in the pnpm build. The plugin
works out what each project is from the files in its directory:

- **workspace root**: project with a `pnpm-workspace.yaml`
- **workspace package**: project with a `package.json` and an ancestor project with a `pnpm-workspace.yaml`
- **standalone package**: project with a `package.json` and no ancestor project with a `pnpm-workspace.yaml`

A project with neither takes no part in the pnpm build.

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

### Convention plugins

The plugin can be applied from
a [convention plugin](https://docs.gradle.org/current/samples/sample_convention_plugins.html)
— a precompiled script plugin in `buildSrc`. Put the plugin on buildSrc's compile classpath:

```kotlin
// buildSrc/build.gradle.kts
plugins {
  `kotlin-dsl`
}

repositories {
  gradlePluginPortal()
}

dependencies {
  implementation("de.cronn:gradle-pnpm-plugin:<version>")
}
```

and apply it from the convention plugin's `plugins` block:

```kotlin
// buildSrc/src/main/kotlin/pnpm-conventions.gradle.kts
plugins {
  id("de.cronn.gradle-pnpm-plugin")
}

pnpm {
  version = "11.25.0"
}

prettier {
  extraArguments("--cache")
}
```

The same convention plugin can be applied to the workspace root and to every package: the `pnpm`,
`typescript`, `prettier` and `eslint` extensions exist in every project, whatever role it plays.

The pnpm lifecycle tasks are the exception, because they exist only on the workspace root. Gradle
derives the type-safe accessors of a convention plugin by applying the plugin to a synthetic project
over an empty directory, which is no workspace root, so there is no `tasks.pnpmInstall` accessor.
Address them by name, from a convention plugin that only the workspace root applies:

```kotlin
// buildSrc/src/main/kotlin/pnpm-workspace-conventions.gradle.kts
import de.cronn.pnpm.task.PnpmTask

plugins {
  id("pnpm-conventions")
}

tasks.named<PnpmTask>("pnpmInstall") {
  // ...
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

The plugin registers no repository when

- the build declares one named `pnpm` itself
- the build sets `RepositoriesMode.PREFER_SETTINGS` or `FAIL_ON_PROJECT_REPOS`
- the repositories are defined in `settings.gradle.kts`

To define the repository yourself, use the following snippet:

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
  // Where the pnpm distribution is downloaded from
  repositoryUrl = "https://github.com/pnpm/pnpm/releases/download/"
}
```

`version`, `installDirectory`, `executable` and `repositoryUrl` describe the one pnpm installation
the whole workspace shares, so configure them once, in the build script of the workspace root. Every package
inherits its values from there. Setting one of them on a package overrides it for that project's own
pnpm invocations only — pnpm is still provisioned by the workspace root.

`workspaceRootPath` is the one property that is per project. It says which project provisions pnpm
for this one, and defaults to the nearest ancestor project holding a `pnpm-workspace.yaml`. Set it
to point a project at a workspace root the plugin cannot discover on its own, because it is not one
of that project's Gradle ancestors:

```kotlin
pnpm {
  workspaceRootPath = ":frontend"
}
```

In CI, the pnpm distribution comes out of the Gradle dependency cache, so caching
`~/.gradle/caches/modules-2` is enough to avoid downloading it on every run. Caching the workspace
root's `.gradle/pnpm` (or an `installDirectory` you already cache) additionally skips the
extraction, and caching pnpm's own download cache skips the package downloads.

## Package tasks

Registered in every package. Tasks related to a supported tool are enabled by default exactly when
the project contains a configuration file for it. For a list of pre-defined tasks, see the
documentation pages of each tool:

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

Configuration defined via the available extension properties is also applied to custom tasks using
the task classes provided for each tool.

The patterns are both the Gradle inputs of the tasks and what the tool is invoked with -- naming
every source file on the command line overruns the command line length limit of Windows. They
therefore have to be understood by Gradle's Ant matcher *and* by the tool:

- an exclude naming a directory needs a trailing `/**`: `excludes("src/generated/**")`
- an include needs a file extension: `includes("sources/**/*.ts")`
- brace expansion (`src/**/*.{ts,tsx}`) matches nothing in Gradle, which skips the task
- an exclude without a slash is anchored to the project directory in Gradle, but matches at any
  depth in ESLint, which follows the gitignore syntax

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

### Publishing a new release

Releases are published to the [Gradle Plugin Portal](https://plugins.gradle.org) by the
`release` workflow when a `v*` tag is pushed; the version is derived from the tag.

## License

[Apache License 2.0](LICENSE)
