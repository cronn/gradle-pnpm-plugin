@file:JvmName("PnpmRepositories")

package de.cronn.pnpm

import org.gradle.api.Action
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.artifacts.repositories.IvyArtifactRepository

/** Group of the module the pnpm distribution archive is resolved as. */
public const val PNPM_GROUP: String = "pnpm"

/** Name of the module the pnpm distribution archive is resolved as. */
public const val PNPM_MODULE: String = "pnpm"

/** Name of the repository created by [pnpm], unless the configuration action renames it. */
public const val PNPM_REPOSITORY_NAME: String = "pnpm"

/** Base URL the pnpm release assets are published under. */
public const val PNPM_RELEASES_URL: String = "https://github.com/pnpm/pnpm/releases/download/"

/**
 * Ivy artifact pattern of a pnpm release asset, relative to the repository URL, for example
 * `v11.25.0/pnpm-linux-x64.tar.gz`.
 */
public const val PNPM_ARTIFACT_PATTERN: String = "v[revision]/[artifact]-[classifier].[ext]"

/**
 * Registers the repository that serves the pnpm distribution archives, and returns it for further
 * configuration.
 *
 * The plugin resolves pnpm as an ordinary dependency, `$PNPM_GROUP:$PNPM_MODULE:<version>`, so that
 * the archive goes through the dependency cache, dependency verification, dependency locking and
 * the proxy configuration of the build. It deliberately does not register the repository itself:
 * which repositories a build resolves from is the decision of that build.
 *
 * ```kotlin
 * import de.cronn.pnpm.pnpm
 *
 * repositories {
 *   pnpm()
 * }
 * ```
 *
 * The repository is registered through [RepositoryHandler.exclusiveContent], so it is the only
 * repository asked for `$PNPM_GROUP:$PNPM_MODULE`, and it is never asked for anything else.
 *
 * @param configuration applied last, so it can override every default: the URL of an internal
 *   mirror, the name, the credentials, or the content filter of the repository itself.
 */
@JvmOverloads
public fun RepositoryHandler.pnpm(
  configuration: Action<in IvyArtifactRepository> = Action<IvyArtifactRepository> {}
): IvyArtifactRepository {
  var created: IvyArtifactRepository? = null

  exclusiveContent { exclusive ->
    // The factory runs synchronously, and ivy(Action) configures the repository before it adds it,
    // so the configuration action can still change the name the repository is registered under.
    exclusive.forRepository {
      ivy { repository ->
          repository.name = PNPM_REPOSITORY_NAME
          repository.setUrl(PNPM_RELEASES_URL)
          repository.patternLayout { layout -> layout.artifact(PNPM_ARTIFACT_PATTERN) }
          // A release asset has no module descriptor next to it: the artifact is the metadata.
          repository.metadataSources { sources -> sources.artifact() }
          configuration.execute(repository)
        }
        .also { created = it }
    }
    // exclusiveContent turns this into the content filter of the repository above, and into the
    // matching exclude filter of every other repository of this handler, present and future.
    exclusive.filter { filter -> filter.includeModule(PNPM_GROUP, PNPM_MODULE) }
  }

  return checkNotNull(created) { "exclusiveContent did not create the pnpm repository" }
}
