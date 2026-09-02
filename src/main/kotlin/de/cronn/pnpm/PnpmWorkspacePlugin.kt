package de.cronn.pnpm

import de.cronn.pnpm.internal.PnpmResolution
import de.cronn.pnpm.task.PnpmSetupTask
import de.cronn.pnpm.task.PnpmTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Provisions the pnpm version pinned in `package.json` and adds the pnpm lifecycle tasks of a pnpm
 * workspace.
 *
 * Apply this plugin to the root project of the build; the workspace tasks it registers are the ones
 * every other project depends on through [PnpmExtension.setupTaskPath] and
 * [PnpmExtension.installTaskPath].
 */
public class PnpmWorkspacePlugin : Plugin<Project> {

  override fun apply(target: Project) {
    if (target != target.rootProject) {
      throw GradleException(
        "The de.cronn.pnpm-workspace plugin must be applied to the root project, " +
          "but was applied to ${target.path}. Use de.cronn.pnpm-package for workspace packages."
      )
    }

    target.pluginManager.apply(PnpmBasePlugin::class.java)
    val extension = target.extensions.getByType(PnpmExtension::class.java)
    val resolution = target.extensions.getByType(PnpmResolution::class.java)

    // pnpmSetup provisions the executable every other pnpm task uses. It is intentionally not a
    // PnpmTask, so the base plugin's "every pnpm task depends on pnpmSetup" wiring cannot make it
    // depend on itself.
    registerSetupTask(target, extension, resolution)
    registerInstallTask(target, extension)
    registerDedupeTask(target)
    registerCleanTask(target)
  }

  private fun registerSetupTask(
    target: Project,
    extension: PnpmExtension,
    resolution: PnpmResolution,
  ) =
    target.tasks.register(SETUP_TASK_NAME, PnpmSetupTask::class.java) { task ->
      task.group = PnpmBasePlugin.TASK_GROUP
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

  private fun registerInstallTask(target: Project, extension: PnpmExtension) {
    val projectDirectory = target.layout.projectDirectory
    val stampFile = target.layout.buildDirectory.file("pnpm/install.stamp")

    target.tasks.register(INSTALL_TASK_NAME, PnpmTask::class.java) { task ->
      task.group = PnpmBasePlugin.TASK_GROUP
      task.description = "Install all pnpm dependencies"
      task.arguments.set(listOf("install"))
      // The lockfile is the single source of truth for what gets installed: it changes whenever a
      // dependency of any workspace package changes. It is deliberately not declared as an output
      // as well -- pnpm may rewrite it, but a task must not declare the same file both ways.
      task.inputs
        .files(
          projectDirectory.file("pnpm-workspace.yaml"),
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

  private fun registerDedupeTask(target: Project) {
    target.tasks.register(DEDUPE_TASK_NAME, PnpmTask::class.java) { task ->
      task.group = PnpmBasePlugin.TASK_GROUP
      task.description =
        "Perform an install removing older dependencies in the lockfile if a newer version can " +
          "be used"
      task.arguments.set(listOf("dedupe"))
      // A maintenance task that mutates the lockfile in place; there is nothing to be up to date
      // about.
      task.outputs.upToDateWhen { false }
    }
  }

  private fun registerCleanTask(target: Project) {
    target.tasks.register(CLEAN_TASK_NAME, PnpmTask::class.java) { task ->
      task.group = PnpmBasePlugin.TASK_GROUP
      task.description = "Safely remove node_modules contents from all workspace projects"
      task.arguments.set(listOf("clean"))
      task.outputs.upToDateWhen { false }
    }
  }

  internal companion object {
    const val SETUP_TASK_NAME: String = "pnpmSetup"
    const val INSTALL_TASK_NAME: String = "pnpmInstall"
    const val DEDUPE_TASK_NAME: String = "pnpmDedupe"
    const val CLEAN_TASK_NAME: String = "pnpmClean"
  }
}
