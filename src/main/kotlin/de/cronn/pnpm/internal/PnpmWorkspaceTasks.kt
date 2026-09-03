package de.cronn.pnpm.internal

import de.cronn.pnpm.PnpmExtension
import de.cronn.pnpm.task.PnpmSetupTask
import de.cronn.pnpm.task.PnpmTask
import org.gradle.api.Project

/**
 * Registers the pnpm lifecycle tasks of a workspace root: the tasks that provision pnpm and manage
 * the dependencies of the whole workspace.
 *
 * These are the tasks every other pnpm task in the build depends on, through
 * [PnpmExtension.setupTaskPath] and [PnpmExtension.installTaskPath].
 */
internal class PnpmWorkspaceTasks(
  private val target: Project,
  private val extension: PnpmExtension,
  private val resolution: PnpmResolution,
  private val taskGroup: String,
) {

  fun register() {
    // pnpmSetup provisions the executable every other pnpm task uses. It is intentionally not a
    // PnpmTask, so the "every pnpm task depends on pnpmSetup" wiring cannot make it depend on
    // itself.
    registerSetupTask()
    registerInstallTask()
    registerDedupeTask()
    registerCleanTask()
  }

  private fun registerSetupTask() {
    target.tasks.register(SETUP_TASK_NAME, PnpmSetupTask::class.java) { task ->
      task.group = taskGroup
      task.description = "Install pnpm unless a matching pnpm is already available"
      task.archiveUrl.set(extension.archiveUrl)
      task.archiveSha256.set(extension.archiveSha256)
      task.executableName.set(resolution.executableName)
      task.installDirectory.set(extension.installDirectory)
      task.required.set(resolution.usesManagedPnpm)
      // The spec must not capture anything: the configuration cache serializes it. Reading the
      // decision from the task also keeps the PATH probe out of every build that does not run
      // pnpmSetup.
      task.onlyIf("no matching pnpm is already available") { candidate ->
        (candidate as PnpmSetupTask).required.get()
      }
    }
  }

  private fun registerInstallTask() {
    val projectDirectory = target.layout.projectDirectory
    val stampFile = target.layout.buildDirectory.file("pnpm/install.stamp")

    target.tasks.register(INSTALL_TASK_NAME, PnpmTask::class.java) { task ->
      task.group = taskGroup
      task.description = "Install all pnpm dependencies"
      task.arguments.set(listOf("install"))
      // The lockfile is the single source of truth for what gets installed: it changes whenever a
      // dependency of any workspace package changes. It is deliberately not declared as an output
      // as well -- pnpm may rewrite it, but a task must not declare the same file both ways.
      // pnpm-workspace.yaml is absent for a standalone package, which inputs.files tolerates.
      task.inputs
        .files(
          projectDirectory.file(PnpmWorkspaceLayout.WORKSPACE_FILE),
          projectDirectory.file("pnpm-lock.yaml"),
          extension.packageJson,
        )
        .withPropertyName("workspaceFiles")
      // node_modules is a symlink farm pointing into a content-addressed store; snapshotting it is
      // slow and tells Gradle nothing useful. A stamp file gives the task a real output instead.
      task.outputs.file(stampFile).withPropertyName("stampFile")
      task.doLast {
        val stamp = stampFile.get().asFile
        stamp.parentFile.mkdirs()
        stamp.writeText("pnpm install completed\n")
      }
    }
  }

  private fun registerDedupeTask() {
    target.tasks.register(DEDUPE_TASK_NAME, PnpmTask::class.java) { task ->
      task.group = taskGroup
      task.description =
        "Perform an install removing older dependencies in the lockfile if a newer version can " +
          "be used"
      task.arguments.set(listOf("dedupe"))
      // A maintenance task that mutates the lockfile in place; there is nothing to be up to date
      // about.
      task.outputs.upToDateWhen { false }
    }
  }

  private fun registerCleanTask() {
    target.tasks.register(CLEAN_TASK_NAME, PnpmTask::class.java) { task ->
      task.group = taskGroup
      task.description = "Safely remove node_modules contents from all workspace projects"
      task.arguments.set(listOf("clean"))
      task.outputs.upToDateWhen { false }
    }
  }

  companion object {
    const val SETUP_TASK_NAME: String = "pnpmSetup"
    const val INSTALL_TASK_NAME: String = "pnpmInstall"
    const val DEDUPE_TASK_NAME: String = "pnpmDedupe"
    const val CLEAN_TASK_NAME: String = "pnpmClean"
  }
}
