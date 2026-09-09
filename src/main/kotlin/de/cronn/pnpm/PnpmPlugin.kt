package de.cronn.pnpm

import de.cronn.pnpm.internal.PnpmDistribution
import de.cronn.pnpm.internal.PnpmOnPathSource
import de.cronn.pnpm.internal.PnpmPlatform
import de.cronn.pnpm.internal.PnpmRepository
import de.cronn.pnpm.internal.PnpmResolution
import de.cronn.pnpm.internal.PnpmRole
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
 *   package that is its own workspace root;
 * - a project with neither takes no part in the pnpm build. It stays inert: it gets the extensions
 *   and the (disabled) tool tasks, but no pnpm lifecycle tasks, and only reports the missing
 *   workspace root if one of its pnpm tasks is requested after all.
 *
 * Every project gets the `pnpm` extension, the Node tool tasks and the `typescript`, `prettier` and
 * `eslint` extensions that configure them, the workspace root included, because a workspace root is
 * a pnpm package like any other. Each tool is enabled by default exactly when the project contains
 * a configuration file for it.
 *
 * The plugin surface deliberately does not depend on the role, because Gradle derives the type-safe
 * accessors of a convention plugin by applying the plugin to a synthetic project over an empty
 * temporary directory: whatever is registered there is the surface every project the convention
 * plugin is applied to has to provide. Applying the plugin must therefore never fail because of the
 * files in a project's directory.
 *
 * All wiring happens within the project the plugin is applied to. The two edges that necessarily
 * cross project boundaries -- provisioning pnpm and installing the workspace -- are expressed as
 * task paths derived from [PnpmExtension.workspaceRootPath] rather than as cross-project task
 * references, so that the plugin does not mutate another project's model.
 */
public class PnpmPlugin : Plugin<Project> {

  override fun apply(target: Project) {
    requireSupportedGradleVersion()

    val layout = PnpmWorkspaceLayout.discover(target)
    val platform = PnpmPlatform.current()
    val workspace = createWorkspaceExtension(target, layout)
    val resolution = resolution(target, workspace, platform)

    target.extensions.add(PnpmResolution::class.java, RESOLUTION_NAME, resolution)

    target.tasks.withType(PnpmTask::class.java).configureEach { task ->
      task.executable.convention(resolution.executable)
      task.pnpmVersion.convention(workspace.version)
      task.dependsOn(lifecycleTaskPath(target, workspace, PnpmWorkspaceTasks.SETUP_TASK_NAME))
    }

    // pnpm exec and pnpm run both need the workspace dependencies to be present.
    val installTaskPath = lifecycleTaskPath(target, workspace, PnpmWorkspaceTasks.INSTALL_TASK_NAME)
    target.tasks.withType(PnpmExecTask::class.java).configureEach { task ->
      task.dependsOn(installTaskPath)
    }
    target.tasks.withType(PnpmRunTask::class.java).configureEach { task ->
      task.dependsOn(installTaskPath)
    }

    if (layout.isWorkspaceRoot) {
      PnpmRepository.register(target, workspace)
      val distributionArchive =
        PnpmDistribution.register(target, workspace.version, platform, resolution.usesManagedPnpm)
      PnpmWorkspaceTasks(target, workspace, resolution, distributionArchive, TASK_GROUP).register()
    }

    registerToolTasks(target)
  }

  /**
   * Creates the `pnpm` extension of [target].
   *
   * Every project gets one, whatever role it plays, so that the extension is part of the plugin
   * surface a convention plugin can rely on. Only the conventions differ: a package inherits the
   * values of its workspace root, so that one pnpm installation is still shared by the whole
   * workspace and configuring `pnpm { }` once in the workspace root remains the way to do it.
   */
  private fun createWorkspaceExtension(
    target: Project,
    layout: PnpmWorkspaceLayout,
  ): PnpmExtension {
    val extension = target.extensions.create(EXTENSION_NAME, PnpmExtension::class.java)
    when (layout.role) {
      PnpmRole.WORKSPACE_ROOT -> applyWorkspaceConventions(target, extension)
      PnpmRole.PACKAGE ->
        applyPackageConventions(target, extension, checkNotNull(layout.workspaceRoot))
      PnpmRole.NONE -> applyInertConventions(target, extension)
    }
    return extension
  }

  private fun applyWorkspaceConventions(target: Project, extension: PnpmExtension) {
    val workspaceDirectory = target.layout.projectDirectory
    val workspaceRootPath = target.path

    extension.workspaceRootPath.convention(workspaceRootPath)

    target.logger.debug(
      "pnpm: workspace root {} provisions pnpm through {} and {}",
      target.path,
      PnpmWorkspaceTasks.taskPath(workspaceRootPath, PnpmWorkspaceTasks.SETUP_TASK_NAME),
      PnpmWorkspaceTasks.taskPath(workspaceRootPath, PnpmWorkspaceTasks.INSTALL_TASK_NAME),
    )

    extension.version.convention(DEFAULT_PNPM_VERSION)
    extension.repositoryUrl.convention(PnpmRepository.PNPM_RELEASES_URL)

    extension.installDirectory.convention(
      extension.version.map { version -> workspaceDirectory.dir(".gradle/pnpm/$version") }
    )
  }

  /**
   * Takes the conventions of a package from the `pnpm` extension of its workspace root, which
   * Gradle has already evaluated: it evaluates a project's ancestors before the project itself.
   * Reading it is why the plugin is not compatible with project isolation.
   *
   * The values are inherited as conventions rather than copied, so a `pnpm { }` block that runs in
   * the workspace root after this package was configured still reaches it. Setting one of them here
   * overrides it for this project's own pnpm invocations only; pnpm is still provisioned by the
   * workspace root.
   */
  private fun applyPackageConventions(
    target: Project,
    extension: PnpmExtension,
    root: Project,
  ) {
    val rootExtension =
      root.extensions.findByType(PnpmExtension::class.java)
        ?: throw GradleException(
          "The directory of ${root.path} contains a ${PnpmWorkspaceLayout.WORKSPACE_FILE}, so it " +
            "is the pnpm workspace root of ${target.path}, but it does not apply the $PLUGIN_ID " +
            "plugin. Apply id(\"$PLUGIN_ID\") in the build script of ${root.path}."
        )

    target.logger.debug(
      "pnpm: package {} inherits its pnpm configuration from the workspace root {}",
      target.path,
      root.path,
    )

    extension.workspaceRootPath.convention(rootExtension.workspaceRootPath)
    extension.version.convention(rootExtension.version)
    extension.installDirectory.convention(rootExtension.installDirectory)
    extension.executable.convention(rootExtension.executable)
    extension.repositoryUrl.convention(rootExtension.repositoryUrl)
  }

  /**
   * Conventions of a project that takes no part in the pnpm build.
   * [PnpmExtension.workspaceRootPath] deliberately gets none: its absence is what makes
   * [lifecycleTaskPath] report the missing workspace root, and only if a pnpm task of this project
   * is actually requested.
   */
  private fun applyInertConventions(target: Project, extension: PnpmExtension) {
    val projectDirectory = target.layout.projectDirectory

    extension.version.convention(DEFAULT_PNPM_VERSION)
    extension.repositoryUrl.convention(PnpmRepository.PNPM_RELEASES_URL)

    extension.installDirectory.convention(
      extension.version.map { version -> projectDirectory.dir(".gradle/pnpm/$version") }
    )
  }

  /**
   * Path of the lifecycle task [taskName] in the workspace root configured in [extension].
   *
   * A project that takes no part in the pnpm build has no workspace root, so the provider fails
   * instead of yielding a path. That is deliberately not checked when the plugin is applied: such a
   * project is inert, and only requesting one of its pnpm tasks makes the missing workspace root a
   * problem worth reporting.
   */
  private fun lifecycleTaskPath(
    target: Project,
    extension: PnpmExtension,
    taskName: String,
  ): Provider<String> {
    // Locals, so that the provider captures neither the project nor this plugin: the configuration
    // cache serializes it as part of a task dependency.
    val projectPath = target.path
    val projectDirectory = target.projectDir
    val missingWorkspaceRoot =
      target.providers.provider<String> {
        throw GradleException(
          PnpmWorkspaceLayout.noWorkspaceRootMessage(projectPath, projectDirectory)
        )
      }

    return extension.workspaceRootPath.orElse(missingWorkspaceRoot).map { path ->
      PnpmWorkspaceTasks.taskPath(path, taskName)
    }
  }

  private fun resolution(
    target: Project,
    extension: PnpmExtension,
    platform: PnpmPlatform,
  ): PnpmResolution {
    val pnpmOnPath = pnpmOnPath(target, platform)
    val managed =
      extension.installDirectory.file(platform.executableName).map { it.asFile.absolutePath }
    val executable = extension.executable.orElse(pnpmOnPath).orElse(managed)

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

  /**
   * The pnpm on the `PATH`, whatever version it is. Its version is deliberately not checked: the
   * pinned [PnpmExtension.version] only decides which pnpm is downloaded when there is none.
   */
  private fun pnpmOnPath(target: Project, platform: PnpmPlatform): Provider<String> =
    target.providers.of(PnpmOnPathSource::class.java) { spec ->
      spec.parameters.searchPath.set(target.providers.environmentVariable(PATH_VARIABLE))
      spec.parameters.osName.set(platform.osName)
      spec.parameters.osArch.set(platform.osArch)
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
    const val RESOLUTION_NAME: String = "pnpmResolution"
    const val DEFAULT_PNPM_VERSION: String = "11.25.0"
    private const val PATH_VARIABLE = "PATH"
    private val MINIMUM_GRADLE_VERSION = GradleVersion.version("9.0")
  }
}
