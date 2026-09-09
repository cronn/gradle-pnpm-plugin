package de.cronn.pnpm.internal

import java.io.File
import org.gradle.api.Project

/** The role a project plays in a pnpm build. */
internal enum class PnpmRole {
  /** Owns the pnpm installation and the workspace lifecycle tasks. */
  WORKSPACE_ROOT,

  /** A package of a workspace whose root is another project. */
  PACKAGE,

  /** Takes no part in the pnpm build: no pnpm file in its directory, and none above it. */
  NONE,
}

/**
 * Where a project sits in the pnpm build, discovered from the files in its directory rather than
 * from the plugin id it applies.
 *
 * A `pnpm-workspace.yaml` marks a workspace root; only its presence matters, so the file is never
 * parsed and no YAML parser is needed.
 *
 * Discovery is total: it never fails, whatever a project's directory looks like. Applying the
 * plugin has to succeed on a project whose directory is empty, because that is how Gradle derives
 * the type-safe accessors of a convention plugin -- it applies every plugin of a precompiled script
 * plugin's `plugins {}` block to a synthetic project over an empty temporary directory, and any
 * failure there fails the whole build. A project that takes no part in the pnpm build is therefore
 * [PnpmRole.NONE] and stays inert; [noWorkspaceRootMessage] is reported if one of its pnpm tasks is
 * requested after all.
 */
internal class PnpmWorkspaceLayout(
  val role: PnpmRole,
  /**
   * The project that owns the pnpm installation and the lifecycle tasks, or `null` for
   * [PnpmRole.NONE], which has no workspace root anywhere.
   */
  val workspaceRoot: Project?,
) {

  val isWorkspaceRoot: Boolean
    get() = role == PnpmRole.WORKSPACE_ROOT

  companion object {
    const val WORKSPACE_FILE: String = "pnpm-workspace.yaml"
    const val PACKAGE_JSON: String = "package.json"

    fun discover(target: Project): PnpmWorkspaceLayout {
      if (containsWorkspaceFile(target)) {
        target.logger.debug(
          "pnpm: {} contains {}, treating it as the pnpm workspace root",
          target.path,
          WORKSPACE_FILE,
        )
        return PnpmWorkspaceLayout(PnpmRole.WORKSPACE_ROOT, target)
      }

      val ancestor = ancestors(target).firstOrNull(::containsWorkspaceFile)
      if (ancestor != null) {
        target.logger.debug(
          "pnpm: {} has no {}, treating it as a package of the workspace root {}",
          target.path,
          WORKSPACE_FILE,
          ancestor.path,
        )
        return PnpmWorkspaceLayout(PnpmRole.PACKAGE, ancestor)
      }

      if (contains(target, PACKAGE_JSON)) {
        target.logger.debug(
          "pnpm: no {} in {} or any of its ancestors, treating it as a standalone pnpm package " +
            "that is its own workspace root",
          WORKSPACE_FILE,
          target.path,
        )
        return PnpmWorkspaceLayout(PnpmRole.WORKSPACE_ROOT, target)
      }

      target.logger.debug(
        "pnpm: {} contains neither a {} nor a {} and no ancestor project contains a {}, so it " +
          "takes no part in the pnpm build",
        target.path,
        WORKSPACE_FILE,
        PACKAGE_JSON,
        WORKSPACE_FILE,
      )
      return PnpmWorkspaceLayout(PnpmRole.NONE, null)
    }

    /**
     * Why a project that takes no part in the pnpm build cannot run a pnpm task. Reported when such
     * a task is requested, rather than when the plugin is applied: a project with no pnpm files is
     * inert, not misconfigured, until something actually needs a workspace root.
     */
    fun noWorkspaceRootMessage(projectPath: String, projectDirectory: File): String =
      "$projectPath takes no part in the pnpm build, so it has no pnpm workspace root: its " +
        "directory ($projectDirectory) contains neither a $WORKSPACE_FILE nor a $PACKAGE_JSON, " +
        "and none of its ancestor projects contains a $WORKSPACE_FILE. Add a $WORKSPACE_FILE to " +
        "the workspace root, or a $PACKAGE_JSON to $projectPath."

    /** The ancestors of [target], nearest first, up to and including the root project. */
    private fun ancestors(target: Project): Sequence<Project> =
      generateSequence(target.parent) { it.parent }

    private fun containsWorkspaceFile(project: Project): Boolean = contains(project, WORKSPACE_FILE)

    private fun contains(project: Project, fileName: String): Boolean =
      File(project.projectDir, fileName).isFile
  }
}
