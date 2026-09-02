package de.cronn.pnpm

import de.cronn.pnpm.internal.PackageJson
import de.cronn.pnpm.internal.PnpmOnPath
import de.cronn.pnpm.internal.PnpmOnPathSource
import de.cronn.pnpm.internal.PnpmPlatform
import de.cronn.pnpm.internal.PnpmResolution
import de.cronn.pnpm.task.PnpmExecTask
import de.cronn.pnpm.task.PnpmRunTask
import de.cronn.pnpm.task.PnpmTask
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Provider
import org.gradle.util.GradleVersion

/**
 * Registers the pnpm task types for a single project and resolves the pnpm executable they use.
 *
 * This plugin is applied automatically by [PnpmWorkspacePlugin] and [PnpmPackagePlugin]. Apply it
 * directly to a project that declares its own [PnpmTask]s without using either of those.
 *
 * All wiring happens within the project the plugin is applied to. The two edges that necessarily
 * cross project boundaries -- provisioning pnpm and installing the workspace -- are expressed as
 * task paths ([PnpmExtension.setupTaskPath], [PnpmExtension.installTaskPath]) rather than as
 * cross-project task references, so that the plugin does not read or mutate another project's
 * model.
 */
public class PnpmBasePlugin : Plugin<Project> {

  override fun apply(target: Project) {
    requireSupportedGradleVersion()

    val platform = PnpmPlatform.current()
    // The pnpm installation is a property of the build, not of a single project, so exactly one
    // extension exists: the one on the root project. Other projects read it, which is why the
    // plugins are not compatible with project isolation.
    val extension = extension(target, platform)

    val pnpmOnPath = pnpmOnPath(target, platform)
    val managed =
      extension.installDirectory.file(platform.executableName).map { it.asFile.absolutePath }
    val executable =
      extension.executable.orElse(usablePnpmOnPath(target, extension, pnpmOnPath)).orElse(managed)

    target.extensions.add(
      PnpmResolution::class.java,
      RESOLUTION_NAME,
      PnpmResolution(
        executable = executable,
        usesManagedPnpm = executable.zip(managed) { resolved, path -> resolved == path },
        executableName = platform.executableName,
      ),
    )

    target.tasks.withType(PnpmTask::class.java).configureEach { task ->
      task.executable.convention(executable)
      task.pnpmVersion.convention(extension.version)
      task.dependsOn(extension.setupTaskPath)
    }

    // pnpm exec and pnpm run both need the workspace dependencies to be present.
    target.tasks.withType(PnpmExecTask::class.java).configureEach { task ->
      task.dependsOn(extension.installTaskPath)
    }
    target.tasks.withType(PnpmRunTask::class.java).configureEach { task ->
      task.dependsOn(extension.installTaskPath)
    }
  }

  private fun extension(target: Project, platform: PnpmPlatform): PnpmExtension {
    val root = target.rootProject
    val existing = root.extensions.findByType(PnpmExtension::class.java)
    if (existing != null) {
      return existing
    }

    if (target != root) {
      throw GradleException(
        "No pnpm configuration was found on the root project. Apply the " +
          "de.cronn.pnpm-workspace plugin to the root project of the build, " +
          "or apply de.cronn.pnpm-base to it."
      )
    }

    val created = root.extensions.create(EXTENSION_NAME, PnpmExtension::class.java)
    applyConventions(root, created, platform)
    return created
  }

  private fun applyConventions(target: Project, extension: PnpmExtension, platform: PnpmPlatform) {
    val rootDirectory = target.rootProject.layout.projectDirectory
    val providers = target.providers

    extension.packageJson.convention(rootDirectory.file(PACKAGE_JSON))
    extension.downloadBaseUrl.convention(DEFAULT_DOWNLOAD_BASE_URL)
    extension.preferPnpmOnPath.convention(true)
    extension.setupTaskPath.convention(DEFAULT_SETUP_TASK_PATH)
    extension.installTaskPath.convention(DEFAULT_INSTALL_TASK_PATH)

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
      extension.version.map { version -> rootDirectory.dir(".gradle/pnpm/$version") }
    )

    extension.archiveUrl.convention(
      extension.downloadBaseUrl.zip(extension.version) { baseUrl, version ->
        PnpmPlatform.archiveUrl(baseUrl, version, platform)
      }
    )
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
        "The de.cronn.pnpm plugins require Gradle $MINIMUM_GRADLE_VERSION or later, " +
          "but this build uses ${GradleVersion.current().version}"
      )
    }
  }

  internal companion object {
    const val EXTENSION_NAME: String = "pnpm"
    const val TASK_GROUP: String = "pnpm"
    const val PACKAGE_JSON: String = "package.json"
    const val DEFAULT_DOWNLOAD_BASE_URL: String = "https://github.com/pnpm/pnpm/releases/download"
    const val DEFAULT_SETUP_TASK_PATH: String = ":pnpmSetup"
    const val DEFAULT_INSTALL_TASK_PATH: String = ":pnpmInstall"
    const val RESOLUTION_NAME: String = "pnpmResolution"
    private const val PATH_VARIABLE = "PATH"
    private val MINIMUM_GRADLE_VERSION = GradleVersion.version("9.0")
  }
}
