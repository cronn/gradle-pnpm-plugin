# gradle-pnpm-plugin

A Gradle plugin that provisions [pnpm](https://pnpm.io) and integrates a pnpm workspace into a Gradle
build.

The pnpm version is pinned once, in the `devEngines` field of `package.json`, and pnpm itself
enforces that pin. The plugin's job is just to make sure *some* pnpm is available to run in the
first place — on a fresh checkout or CI runner, nothing is installed yet. It bootstraps a pnpm from
`PATH` or, failing that, from the pnpm releases resolved as an ordinary Gradle dependency, then lets
pnpm's own version resolution, lockfile checks and checksum verification take it from there. It also installs the workspace dependencies and exposes
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

## Workspace tasks

Registered on the workspace root, in the `pnpm` group:

|     Task      |              Description               |
|---------------|----------------------------------------|
| `pnpmSetup`   | Provisions the pinned pnpm, if needed. |
| `pnpmInstall` | Runs `pnpm install`.                   |
| `pnpmDedupe`  | Runs `pnpm dedupe`.                    |
| `pnpmClean`   | Runs `pnpm clean`.                     |

Every task that runs pnpm depends on `pnpmSetup`, and every task that runs against the installed
workspace additionally depends on `pnpmInstall`.

### Configuration

```kotlin
pnpm {
  // Overrides pnpm version for initial setup
  version = "11.25.0"
  // Defaults to <workspaceRootDir>/.gradle/pnpm/<version>
  installDirectory = layout.projectDirectory.dir(".gradle/pnpm/11.25.0")
  // Skips provisioning entirely
  executable = "/usr/local/bin/pnpm"
  // Reuse a matching pnpm from the PATH; defaults to true
  preferPnpmOnPath = true
}
```

The `pnpm` extension is created on the workspace root and shared by the whole workspace, so
configure it once, in the build script of the workspace root.

### Where pnpm comes from

Unless a usable pnpm is already available, the plugin resolves the pnpm distribution as a regular
Gradle dependency, from an Ivy repository laid out over the pnpm GitHub releases:

```
com.pnpm:pnpm:<version>:<platform>@<tar.gz|zip>
  -> https://github.com/pnpm/pnpm/releases/download/v<version>/pnpm-<platform>.<tar.gz|zip>
```

Gradle therefore does the downloading, which means the archive is cached in the shared module cache
rather than per project, `--offline` and `--refresh-dependencies` work as usual, and proxies and
credentials are configured the way they are for every other dependency.

The repository is declared on a resolver detached from the project, so it is invisible to the rest
of the build: it does not interact with `repositoriesMode`, it is never consulted for anything but
pnpm, `dependencyLocking { lockAllConfigurations() }` does not produce a lock entry for it, and
`configurations.all { }` rules do not apply to it.

**Mirrors and air-gapped builds.** Point the `de.cronn.pnpm.distributionBaseUrl` Gradle property at
any mirror that keeps the pnpm release layout — including a `file:` URL:

```properties
# gradle.properties
de.cronn.pnpm.distributionBaseUrl=https://artifacts.example.com/pnpm-releases
```

**Dependency verification.** Because pnpm is now a resolved artifact, it is covered by
`gradle/verification-metadata.xml`. If your build already verifies dependencies, add an entry per
platform you build on, or the resolution will fail:

```xml
<component group="com.pnpm" name="pnpm" version="11.25.0">
  <artifact name="pnpm-11.25.0-linux-x64.tar.gz">
    <sha256 value="…"/>
  </artifact>
</component>
```

Generate it with `./gradlew --write-verification-metadata sha256 pnpmSetup`.

Note that the archive is resolved during the configuration phase, so a failure to download it
surfaces before `pnpmSetup` runs. Nothing is resolved when pnpm does not have to be provisioned at
all.

**In CI**, cache Gradle's module cache (`~/.gradle/caches/modules-2`, which
[`gradle/actions/setup-gradle`](https://github.com/gradle/actions) already does) alongside the
workspace root's `.gradle/pnpm` — or set `installDirectory` to a location you already cache — and
pnpm's own download cache, to avoid downloading anything on every run.

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

To try out uncommitted changes in a real build, include this repository as a composite build in the
target project's `settings.gradle.kts`:

```kotlin
// settings.gradle.kts
pluginManagement {
  includeBuild("../gradle-pnpm-plugin")
}
```

### Publishing a new release

Releases are published to the [Gradle Plugin Portal](https://plugins.gradle.org) by the
`release` workflow when a `v*` tag is pushed; the version is derived from the tag.

## License

[Apache License 2.0](LICENSE)
