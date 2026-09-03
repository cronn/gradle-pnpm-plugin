package de.cronn.pnpm.internal

import java.io.File
import org.gradle.api.GradleException
import org.gradle.api.Project

/** The role a project plays in a pnpm build. */
internal enum class PnpmRole {
  /** Owns the pnpm installation and the workspace lifecycle tasks. */
  WORKSPACE_ROOT,

  /** A package of a workspace whose root is another project. */
  PACKAGE,
}

/**
 * Where a project sits in the pnpm build, discovered from the files in its directory rather than
 * from the plugin id it applies.
 *
 * A `pnpm-workspace.yaml` marks a workspace root; only its presence matters, so the file is never
 * parsed and no YAML parser is needed.
 */
internal class PnpmWorkspaceLayout(
  val role: PnpmRole,
  /** The project that owns the pnpm installation and the lifecycle tasks. */
  val workspaceRoot: Project,
) {

  val isWorkspaceRoot: Boolean
    get() = role == PnpmRole.WORKSPACE_ROOT

  /** Prefix that turns a task name of [workspaceRoot] into an absolute task path. */
  fun taskPathPrefix(): String =
    workspaceRoot.path.let { path -> if (path == Project.PATH_SEPARATOR) path else "$path:" }

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

      throw GradleException(
        "Cannot tell what role ${target.path} plays in the pnpm build: its directory " +
          "(${target.projectDir}) contains neither a $WORKSPACE_FILE nor a $PACKAGE_JSON, and " +
          "none of its ancestor projects contains a $WORKSPACE_FILE. Add a $WORKSPACE_FILE to the " +
          "workspace root, or a $PACKAGE_JSON to ${target.path}."
      )
    }

    /** The ancestors of [target], nearest first, up to and including the root project. */
    private fun ancestors(target: Project): Sequence<Project> =
      generateSequence(target.parent) { it.parent }

    private fun containsWorkspaceFile(project: Project): Boolean = contains(project, WORKSPACE_FILE)

    private fun contains(project: Project, fileName: String): Boolean =
      File(project.projectDir, fileName).isFile
  }
}
