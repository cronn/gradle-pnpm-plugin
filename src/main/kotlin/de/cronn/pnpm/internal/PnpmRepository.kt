package de.cronn.pnpm.internal

import de.cronn.pnpm.PnpmExtension
import org.gradle.api.Project
import org.gradle.api.initialization.resolve.DependencyResolutionManagement
import org.gradle.api.initialization.resolve.RepositoriesMode
import org.gradle.api.internal.GradleInternal

/**
 * The repository that serves the pnpm distribution archives.
 *
 * The plugin registers it itself, in the workspace root, because that is the only project resolving
 * `$PNPM_GROUP:$PNPM_MODULE`. It steps aside whenever the repositories of the build are the
 * decision of the build rather than of the project: a build that declares its repositories in
 * `settings.gradle.kts` declares this one there too, with a plain Ivy declaration that needs none
 * of the plugin's classes on the settings classpath.
 */
internal object PnpmRepository {

  /** Group of the module the pnpm distribution archive is resolved as. */
  const val PNPM_GROUP: String = "pnpm"

  /** Name of the module the pnpm distribution archive is resolved as. */
  const val PNPM_MODULE: String = "pnpm"

  /** Name the repository is registered under. */
  const val PNPM_REPOSITORY_NAME: String = "pnpm"

  /** Base URL the pnpm release assets are published under. */
  const val PNPM_RELEASES_URL: String = "https://github.com/pnpm/pnpm/releases/download/"

  /**
   * Ivy artifact pattern of a pnpm release asset, relative to the repository URL, for example
   * `v11.25.0/pnpm-linux-x64.tar.gz`.
   */
  const val PNPM_ARTIFACT_PATTERN: String = "v[revision]/[artifact]-[classifier].[ext]"

  /**
   * Registers the repository in [target], unless the build serves pnpm itself.
   *
   * The decision is made after the project was evaluated, because it depends on what the build
   * script declares -- on [PnpmExtension.repositoryUrl] and on the repositories of the project. It
   * cannot be deferred any further: `FAIL_ON_PROJECT_REPOS` rejects a project repository the moment
   * it is added, so the opt-out has to be read before that, and a configuration resolved during the
   * configuration of [target] would not see a repository added any later.
   */
  fun register(target: Project, extension: PnpmExtension) {
    target.afterEvaluate { project ->
      val skipped = skipReason(project)
      if (skipped != null) {
        project.logger.debug(
          "pnpm: not registering the '{}' repository in {}: {}",
          PNPM_REPOSITORY_NAME,
          project.path,
          skipped,
        )
        return@afterEvaluate
      }

      val url = extension.repositoryUrl.get()
      project.logger.debug(
        "pnpm: resolving {}:{} in {} from {}",
        PNPM_GROUP,
        PNPM_MODULE,
        project.path,
        url,
      )
      add(project, url)
    }
  }

  /** Registers the repository, laid out like the declaration the README documents. */
  private fun add(project: Project, url: String) {
    project.repositories.exclusiveContent { exclusive ->
      exclusive.forRepository {
        project.repositories.ivy { repository ->
          repository.name = PNPM_REPOSITORY_NAME
          repository.setUrl(url)
          repository.patternLayout { layout -> layout.artifact(PNPM_ARTIFACT_PATTERN) }
          // A release asset has no module descriptor next to it: the artifact is the metadata.
          repository.metadataSources { sources -> sources.artifact() }
        }
      }
      // exclusiveContent turns this into the content filter of the repository above, and into the
      // matching exclude filter of every other repository of this handler, present and future.
      exclusive.filter { filter -> filter.includeModule(PNPM_GROUP, PNPM_MODULE) }
    }
  }

  /**
   * Why the build serves pnpm better than a repository registered here would, or `null` to register
   * one.
   *
   * The last two reasons are what keeps the plugin from taking a decision that is not its to take.
   * Gradle consults the repositories declared in `settings.gradle.kts` for a project that declares
   * none of its own; adding one here would cut the project off from them, and every other
   * dependency it has with it.
   *
   * Declaring the repository under [PNPM_REPOSITORY_NAME] is the first reason, and the way a build
   * script takes the declaration over -- to lay the repository out differently, for example.
   */
  private fun skipReason(project: Project): String? {
    if (project.repositories.findByName(PNPM_REPOSITORY_NAME) != null) {
      return "the build already declares a '$PNPM_REPOSITORY_NAME' repository"
    }

    val management = dependencyResolutionManagement(project) ?: return null
    val mode = management.repositoriesMode.get()
    if (mode != RepositoriesMode.PREFER_PROJECT) {
      return "the build declares its repositories in settings ($mode)"
    }
    if (project.repositories.isEmpty() && management.repositories.isNotEmpty()) {
      return "${project.path} declares no repositories, so the build resolves from the " +
        "repositories declared in settings"
    }
    return null
  }

  /**
   * The dependency resolution management of the build, or `null` when it cannot be read -- under
   * `ProjectBuilder`, which skips the settings phase, and were Gradle ever to drop the only
   * internal API this plugin uses. Both fall back to registering the repository, which is what a
   * build without central repository management wants.
   */
  private fun dependencyResolutionManagement(project: Project): DependencyResolutionManagement? =
    try {
      (project.gradle as GradleInternal).settings.dependencyResolutionManagement
    } catch (e: RuntimeException) {
      project.logger.debug("pnpm: cannot read the settings of {}: {}", project.path, e.toString())
      null
    }
}
