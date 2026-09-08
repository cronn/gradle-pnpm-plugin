package de.cronn.pnpm.internal

import de.cronn.pnpm.PnpmExtension
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.repositories.IvyArtifactRepository
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.logging.Logger
import org.gradle.api.provider.Provider

/**
 * The self-contained pnpm distribution, provisioned as an ordinary Gradle dependency.
 *
 * pnpm publishes one archive per platform as a GitHub release asset, at a URL that is a pure
 * function of version and platform: `<base>/v<version>/pnpm-<platform>.<tar.gz|zip>`. That is an
 * Ivy repository with a custom pattern layout, so Gradle can do the downloading -- which means the
 * module cache is shared across builds, `--offline` and `--refresh-dependencies` work, dependency
 * verification can pin the archive, and proxies and credentials are configured the usual way.
 *
 * Repository, configuration and dependency all live on a *detached* resolver rather than on the
 * project. A project repository would be visible to every other configuration of that project: in
 * the default `PREFER_PROJECT` repositories mode, repositories declared in settings are consulted
 * only while a project declares none, so adding one here would silently strip a workspace root of
 * the repositories its JVM dependencies resolve from. Under `FAIL_ON_PROJECT_REPOS` adding one
 * fails the build outright, at add time. Being detached also keeps the pnpm resolution out of
 * `dependencyLocking { lockAllConfigurations() }` and out of any `configurations.all { }`
 * resolution rules the consumer applies.
 */
internal class PnpmDistribution(
  target: Project,
  extension: PnpmExtension,
  private val platform: PnpmPlatform,
) {

  private val logger: Logger = target.logger

  private val resolver = (target as ProjectInternal).newDetachedResolver()

  /** An archive that does not have to be provisioned at all. */
  private val noArchive: FileCollection = target.objects.fileCollection()

  val repository: IvyArtifactRepository =
    resolver.repositories.ivy { repository ->
      repository.name = REPOSITORY_NAME
      // The URL stays lazy: setUrl unpacks deferred values on every read and is first read when the
      // configuration is resolved, so a base url configured by the build script still wins even
      // though the repository is declared while the plugin is applied.
      repository.setUrl(baseUrl(target))
      repository.patternLayout { layout -> layout.artifact(ARTIFACT_PATTERN) }
      // pnpm publishes no module metadata: the archive is the whole module.
      repository.metadataSources { sources -> sources.artifact() }
      repository.content { content -> content.includeModule(GROUP, MODULE) }
    }

  val configuration: Configuration =
    resolver.configurations
      .resolvable(CONFIGURATION_NAME) { configuration ->
        configuration.description = "The self-contained pnpm distribution for $platform"
        // Requesting an explicit artifact extension already makes the dependency artifact-only.
        configuration.isTransitive = false
      }
      .get()

  init {
    // Declared as a provider, because the version is configured by the `pnpm` block of the build
    // script, which runs after the plugin is applied.
    resolver.dependencies.addProvider(
      CONFIGURATION_NAME,
      extension.version.map { version ->
        coordinates(version, platform).also {
          logger.debug("pnpm: provisioning the pnpm distribution as {}", it)
        }
      },
    )
  }

  /**
   * The archive [de.cronn.pnpm.task.PnpmSetupTask] extracts, or nothing when pnpm does not have to
   * be provisioned.
   *
   * The empty branch is what keeps a build that reuses a pnpm from the `PATH` off the network: the
   * configuration cache resolves -- and downloads -- every file collection reachable from a task
   * while it serializes the task graph, which happens before any `onlyIf` spec runs. Attach the
   * result with `ConfigurableFileCollection.from`; wrapping it in another file collection would
   * hand the codec the resolution again regardless of the flag.
   *
   * Neither branch captures the project: both file collections are created here, once.
   */
  fun archive(required: Provider<Boolean>): Provider<FileCollection> {
    val resolved: FileCollection = configuration.incoming.files
    val empty: FileCollection = noArchive
    return required.map { managed -> if (managed) resolved else empty }
  }

  /**
   * The base URL the pnpm release assets are read from, overridable for mirrors and air-gapped
   * builds through the [BASE_URL_PROPERTY] Gradle property.
   */
  private fun baseUrl(target: Project): Provider<String> =
    target.providers
      .gradleProperty(BASE_URL_PROPERTY)
      .map { configured ->
        logger.debug(
          "pnpm: reading the pnpm distribution from {}, configured through {}",
          configured,
          BASE_URL_PROPERTY,
        )
        configured.trimEnd('/')
      }
      .orElse(DEFAULT_BASE_URL)

  internal companion object {
    /** Where pnpm publishes its release assets. */
    const val DEFAULT_BASE_URL: String = "https://github.com/pnpm/pnpm/releases/download"

    /** Gradle property that points the plugin at a mirror of the pnpm releases. */
    const val BASE_URL_PROPERTY: String = "de.cronn.pnpm.distributionBaseUrl"

    /**
     * Coordinates of the pnpm distribution. Synthetic: pnpm is not published to any module
     * repository, so these exist only to address a release asset. The platform is the classifier
     * rather than part of the module name, which keeps content filters and dependency verification
     * metadata talking about a single module.
     */
    const val GROUP: String = "com.pnpm"

    const val MODULE: String = "pnpm"

    const val REPOSITORY_NAME: String = "pnpmDistribution"

    const val CONFIGURATION_NAME: String = "pnpmDistribution"

    /**
     * Maps the coordinates onto a release asset path, for example `v11.25.0/pnpm-linux-x64.tar.gz`.
     */
    const val ARTIFACT_PATTERN: String = "v[revision]/[artifact]-[classifier].[ext]"

    fun coordinates(version: String, platform: PnpmPlatform): String =
      "$GROUP:$MODULE:$version:${platform.identifier}@${platform.archiveExtension}"
  }
}
