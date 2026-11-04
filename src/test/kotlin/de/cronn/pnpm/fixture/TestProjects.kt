package de.cronn.pnpm.fixture

import de.cronn.pnpm.fixture.ProjectFiles.PLUGIN_ID
import de.cronn.pnpm.fixture.ProjectFiles.writeCheckConfigs
import de.cronn.pnpm.fixture.ProjectFiles.writePlaywrightConfig
import de.cronn.pnpm.fixture.ProjectFiles.writeRootPackageJson
import de.cronn.pnpm.fixture.ProjectFiles.writeVitestConfig
import java.io.File
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder

/**
 * `ProjectBuilder` projects the plugin is applied to.
 *
 * The config files of a project are written before the plugin is applied, because that is when the
 * plugin decides which tool tasks to register.
 */
object TestProjects {

  /** A project that is a pnpm workspace root, because its directory has a pnpm-workspace.yaml. */
  fun workspaceProject(directory: File): Project {
    writeRootPackageJson(directory)
    writeCheckConfigs(directory)
    File(directory, "pnpm-workspace.yaml").writeText("packages:\n  - frontend\n")

    val project = ProjectBuilder.builder().withProjectDir(directory).build()
    project.pluginManager.apply(PLUGIN_ID)
    return project
  }

  /**
   * A package [name] below a new workspace root in [directory]. [writeConfigs] writes the config
   * files of the tools the package is configured for.
   */
  fun packageProject(
    directory: File,
    name: String = "frontend",
    writeConfigs: (File) -> Unit = ::writeCheckConfigs,
  ): Project {
    val root = workspaceProject(directory)
    val packageDirectory = File(directory, name)
    packageDirectory.mkdirs()
    File(packageDirectory, "package.json").writeText("""{ "name": "$name" }""")
    writeConfigs(packageDirectory)

    val project =
      ProjectBuilder.builder()
        .withName(name)
        .withParent(root)
        .withProjectDir(packageDirectory)
        .build()
    project.pluginManager.apply(PLUGIN_ID)
    return project
  }

  /** A package project that is also configured for Playwright. */
  fun playwrightProject(directory: File, name: String = "e2e"): Project =
    packageProject(directory, name) { packageDirectory ->
      writeCheckConfigs(packageDirectory)
      writePlaywrightConfig(packageDirectory)
    }

  /** A package project that is also configured for Vitest. */
  fun vitestProject(directory: File): Project =
    packageProject(directory) { packageDirectory ->
      writeCheckConfigs(packageDirectory)
      writeVitestConfig(packageDirectory)
    }
}
