package de.cronn.pnpm.internal

import de.cronn.pnpm.PNPM_GROUP
import de.cronn.pnpm.PNPM_MODULE
import org.gradle.api.Project
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.dsl.DependencyFactory
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Provider

/**
 * The pnpm distribution archive, resolved through Gradle's dependency management so that it takes
 * part in the dependency cache, dependency verification, dependency locking and the proxy
 * configuration of the build.
 *
 * The repository serving it is not registered here; a build declares it with `repositories { pnpm()
 * }`.
 */
internal object PnpmDistribution {

  /** Configuration the pnpm distribution is declared on, so that a build can substitute it. */
  const val DECLARED_CONFIGURATION_NAME: String = "pnpmDistribution"

  /** Configuration the pnpm distribution is resolved from; this is what a lock file pins. */
  const val ARCHIVE_CONFIGURATION_NAME: String = "pnpmDistributionArchive"

  /**
   * Creates the two configurations on [target] and returns what `pnpmSetup` takes as its input.
   *
   * The returned provider yields the resolvable configuration only when pnpm actually has to be
   * provisioned. That gate has to sit in the value of the file collection rather than in the
   * `onlyIf` of the task: the configuration cache resolves every file collection reachable from the
   * task graph while it stores the entry, which happens before any `onlyIf` is consulted. A build
   * that reuses a pnpm from the `PATH` therefore resolves nothing, and needs no repository.
   *
   * The dependency itself is declared unconditionally, so that lock state and verification metadata
   * do not depend on what happens to be installed on the machine writing them.
   */
  fun register(
    target: Project,
    version: Provider<String>,
    platform: PnpmPlatform,
    usesManagedPnpm: Provider<Boolean>,
  ): Provider<List<FileCollection>> {
    val dependencyFactory = target.dependencyFactory
    val logger = target.logger

    val declared =
      target.configurations.dependencyScope(DECLARED_CONFIGURATION_NAME) { configuration ->
        configuration.description = "The pnpm distribution archive to install"
        configuration.dependencies.addAllLater(
          version.map { pinned ->
            logger.debug(
              "pnpm: resolving the pnpm distribution as {}:{}:{}:{}@{}",
              PNPM_GROUP,
              PNPM_MODULE,
              pinned,
              platform.identifier,
              platform.archiveExtension,
            )
            listOf(dependency(dependencyFactory, pinned, platform))
          }
        )
      }

    val archive =
      target.configurations.resolvable(ARCHIVE_CONFIGURATION_NAME) { configuration ->
        configuration.description = "Resolves the pnpm distribution archive"
        configuration.extendsFrom(declared.get())
      }

    return usesManagedPnpm.map { managed ->
      if (managed) listOf<FileCollection>(archive.get()) else emptyList()
    }
  }

  /**
   * `pnpm:pnpm:11.25.0:linux-x64@tar.gz`. Passing an extension makes Gradle add an explicit
   * artifact and mark the dependency as not transitive, which is what turns this into an
   * artifact-only dependency that needs no module metadata.
   */
  private fun dependency(
    dependencyFactory: DependencyFactory,
    version: String,
    platform: PnpmPlatform,
  ): ExternalModuleDependency =
    dependencyFactory.create(
      PNPM_GROUP,
      PNPM_MODULE,
      version,
      platform.identifier,
      platform.archiveExtension,
    )
}
