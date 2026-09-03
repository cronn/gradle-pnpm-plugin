package de.cronn.pnpm

import de.cronn.pnpm.internal.PackageJson
import de.cronn.pnpm.internal.PnpmOnPath
import de.cronn.pnpm.internal.PnpmOnPathSource
import de.cronn.pnpm.internal.PnpmPlatform
import de.cronn.pnpm.internal.PnpmResolution
import de.cronn.pnpm.internal.PnpmToolTasks
import de.cronn.pnpm.internal.PnpmWorkspaceLayout
import de.cronn.pnpm.internal.PnpmWorkspaceTasks
import de.cronn.pnpm.internal.ToolConfigFiles
import de.cronn.pnpm.task.PnpmExecTask
import de.cronn.pnpm.task.PnpmRunTask
import de.cronn.pnpm.task.PnpmTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.provider.Provider
import org.gradle.util.GradleVersion

/**
 * Provisions pnpm and integrates a pnpm workspace into the Gradle build.
 *
 * Apply this plugin to every project that takes part in the pnpm build; it discovers what each
 * project is from the files in its directory:
 * - a project whose directory contains a `pnpm-workspace.yaml` is the **workspace root**. It gets
 *   the pnpm lifecycle tasks (`pnpmSetup`, `pnpmInstall`, `pnpmDedupe`, `pnpmClean`) and the `pnpm`
 *   extension that configures the pnpm installation shared by the workspace;
 * - a project below such a project is a **package** of that workspace;
 * - a project with a `package.json` but no `pnpm-workspace.yaml` anywhere above it is a standalone
 *   package that is its own workspace root.
 *
 * Every project gets the Node tool tasks and the `typescript`, `prettier` and `eslint` extensions
 * that configure them, the workspace root included, because a workspace root is a pnpm package like
 * any other. Each tool is enabled by default exactly when the project contains a configuration file
 * for it.
 *
 * All wiring happens within the project the plugin is applied to. The two edges that necessarily
 * cross project boundaries -- provisioning pnpm and installing the workspace -- are expressed as
 * task paths ([PnpmExtension.setupTaskPath], [PnpmExtension.installTaskPath]) rather than as
 * cross-project task references, so that the plugin does not mutate another project's model.
 */
public class PnpmPlugin : Plugin<Project> {

  override fun apply(target: Project) {
    requireSupportedGradleVersion()

    val layout = PnpmWorkspaceLayout.discover(target)
    val platform = PnpmPlatform.current()
    val workspace = workspaceExtension(target, layout, platform)
    val resolution = resolution(target, workspace, platform)

    target.extensions.add(PnpmResolution::class.java, RESOLUTION_NAME, resolution)

    target.tasks.withType(PnpmTask::class.java).configureEach { task ->
      task.executable.convention(resolution.executable)
      task.pnpmVersion.convention(workspace.version)
      task.dependsOn(workspace.setupTaskPath)
    }

    // pnpm exec and pnpm run both need the workspace dependencies to be present.
    target.tasks.withType(PnpmExecTask::class.java).configureEach { task ->
      task.dependsOn(workspace.installTaskPath)
    }
    target.tasks.withType(PnpmRunTask::class.java).configureEach { task ->
      task.dependsOn(workspace.installTaskPath)
    }

    if (layout.isWorkspaceRoot) {
      PnpmWorkspaceTasks(target, workspace, resolution, TASK_GROUP).register()
    }

    registerToolTasks(target)
  }

  /**
   * The `pnpm` extension of the workspace root of [layout], created on first use.
   *
   * A package resolves the extension of its workspace root, which Gradle has already evaluated: it
   * evaluates a project's ancestors before the project itself. Reading it is why the plugin is not
   * compatible with project isolation.
   */
  private fun workspaceExtension(
    target: Project,
    layout: PnpmWorkspaceLayout,
    platform: PnpmPlatform,
  ): PnpmExtension {
    val root = layout.workspaceRoot
    val existing = root.extensions.findByType(PnpmExtension::class.java)
    if (existing != null) {
      return existing
    }

    if (root != target) {
      throw GradleException(
        "The directory of ${root.path} contains a ${PnpmWorkspaceLayout.WORKSPACE_FILE}, so it is " +
          "the pnpm workspace root of ${target.path}, but it does not apply the $PLUGIN_ID " +
          "plugin. Apply id(\"$PLUGIN_ID\") in the build script of ${root.path}."
      )
    }

    val created = root.extensions.create(EXTENSION_NAME, PnpmExtension::class.java)
    applyWorkspaceConventions(root, layout, created, platform)
    return created
  }

  private fun applyWorkspaceConventions(
    target: Project,
    layout: PnpmWorkspaceLayout,
    extension: PnpmExtension,
    platform: PnpmPlatform,
  ) {
    val workspaceDirectory = target.layout.projectDirectory
    val providers = target.providers
    val taskPathPrefix = layout.taskPathPrefix()

    extension.packageJson.convention(workspaceDirectory.file(PnpmWorkspaceLayout.PACKAGE_JSON))
    extension.downloadBaseUrl.convention(DEFAULT_DOWNLOAD_BASE_URL)
    extension.preferPnpmOnPath.convention(true)
    extension.setupTaskPath.convention(taskPathPrefix + PnpmWorkspaceTasks.SETUP_TASK_NAME)
    extension.installTaskPath.convention(taskPathPrefix + PnpmWorkspaceTasks.INSTALL_TASK_NAME)

    target.logger.debug(
      "pnpm: workspace root {} provisions pnpm through {}{} and {}{}",
      target.path,
      taskPathPrefix,
      PnpmWorkspaceTasks.SETUP_TASK_NAME,
      taskPathPrefix,
      PnpmWorkspaceTasks.INSTALL_TASK_NAME,
    )

    // fileContents declares a tracked configuration input, so editing the pinned pnpm version
    // invalidates the configuration cache. Two constraints shape this:
    //  - the provider must be created here, not inside a lambda: file contents can only be
    //    obtained while the build is being configured;
    //  - the fallback must not throw, because storing the configuration cache evaluates both
    //    branches of orElse. A missing file therefore surfaces as empty content.
    val packageJsonText = providers.fileContents(extension.packageJson).asText.orElse("")

    extension.version.convention(
      extension.packageJson.zip(packageJsonText) { packageJson, text ->
        if (text.isBlank()) {
          throw GradleException(
            "Expected a package.json pinning the pnpm version in " +
              "'devEngines.packageManager' at ${packageJson.asFile}, but the file is missing " +
              "or empty"
          )
        }
        PackageJson.pnpmVersion(text, packageJson.asFile.path)
      }
    )

    extension.installDirectory.convention(
      extension.version.map { version -> workspaceDirectory.dir(".gradle/pnpm/$version") }
    )

    extension.archiveUrl.convention(
      extension.downloadBaseUrl.zip(extension.version) { baseUrl, version ->
        PnpmPlatform.archiveUrl(baseUrl, version, platform)
      }
    )
  }

  private fun resolution(
    target: Project,
    extension: PnpmExtension,
    platform: PnpmPlatform,
  ): PnpmResolution {
    val pnpmOnPath = pnpmOnPath(target, platform)
    val managed =
      extension.installDirectory.file(platform.executableName).map { it.asFile.absolutePath }
    val executable =
      extension.executable.orElse(usablePnpmOnPath(target, extension, pnpmOnPath)).orElse(managed)

    return PnpmResolution(
      executable = executable,
      usesManagedPnpm = executable.zip(managed) { resolved, path -> resolved == path },
      executableName = platform.executableName,
    )
  }

  /**
   * Creates the extension of each Node tool and registers its tasks.
   *
   * A tool is enabled exactly when the project is configured for it. Only the existence of the
   * config files is checked, which Gradle tracks as a configuration cache input, so adding one
   * enables the tool on the next build.
   */
  private fun registerToolTasks(target: Project) {
    target.pluginManager.apply(BasePlugin::class.java)

    val typescript =
      target.extensions.create(TYPESCRIPT_EXTENSION_NAME, TypescriptExtension::class.java)
    val prettier = target.extensions.create(PRETTIER_EXTENSION_NAME, PrettierExtension::class.java)
    val eslint = target.extensions.create(ESLINT_EXTENSION_NAME, EslintExtension::class.java)

    typescript.enabled.convention(
      configured(target, TYPESCRIPT_EXTENSION_NAME, ToolConfigFiles.TYPESCRIPT)
    )
    prettier.enabled.convention(
      configured(target, PRETTIER_EXTENSION_NAME, ToolConfigFiles.PRETTIER)
    )
    eslint.enabled.convention(configured(target, ESLINT_EXTENSION_NAME, ToolConfigFiles.ESLINT))

    PnpmToolTasks(target, typescript, prettier, eslint).register()
  }

  /** Whether [target] contains one of [configFiles], logging the decision made for [name]. */
  private fun configured(target: Project, name: String, configFiles: List<String>): Boolean {
    val present = ToolConfigFiles.anyPresent(target, configFiles)
    if (present) {
      target.logger.debug("pnpm: enabling {} in {}, it has a config file", name, target.path)
    } else {
      target.logger.debug(
        "pnpm: disabling {} in {}, none of {} exists",
        name,
        target.path,
        configFiles.joinToString(", "),
      )
    }
    return present
  }

  private fun pnpmOnPath(target: Project, platform: PnpmPlatform): Provider<PnpmOnPath> =
    target.providers.of(PnpmOnPathSource::class.java) { spec ->
      spec.parameters.searchPath.set(target.providers.environmentVariable(PATH_VARIABLE))
      spec.parameters.executableNames.set(platform.executableNamesOnPath)
    }

  private fun usablePnpmOnPath(
    target: Project,
    extension: PnpmExtension,
    pnpmOnPath: Provider<PnpmOnPath>,
  ): Provider<String> {
    val absent = target.providers.provider<String> { null }
    return extension.preferPnpmOnPath.flatMap { prefer ->
      if (prefer) {
        extension.version.flatMap { pinned ->
          pnpmOnPath.filter { it.version == pinned }.map { it.executablePath }
        }
      } else {
        absent
      }
    }
  }

  private fun requireSupportedGradleVersion() {
    if (GradleVersion.current() < MINIMUM_GRADLE_VERSION) {
      throw GradleException(
        "The $PLUGIN_ID plugin requires Gradle $MINIMUM_GRADLE_VERSION or later, " +
          "but this build uses ${GradleVersion.current().version}"
      )
    }
  }

  internal companion object {
    const val PLUGIN_ID: String = "de.cronn.gradle-pnpm-plugin"
    const val EXTENSION_NAME: String = "pnpm"
    const val TYPESCRIPT_EXTENSION_NAME: String = "typescript"
    const val PRETTIER_EXTENSION_NAME: String = "prettier"
    const val ESLINT_EXTENSION_NAME: String = "eslint"
    const val TASK_GROUP: String = "pnpm"
    const val DEFAULT_DOWNLOAD_BASE_URL: String = "https://github.com/pnpm/pnpm/releases/download"
    const val RESOLUTION_NAME: String = "pnpmResolution"
    private const val PATH_VARIABLE = "PATH"
    private val MINIMUM_GRADLE_VERSION = GradleVersion.version("9.0")
  }
}
