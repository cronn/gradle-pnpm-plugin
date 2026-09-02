package de.cronn.pnpm

import de.cronn.pnpm.task.PnpmSetupTask
import de.cronn.pnpm.task.PnpmTask
import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class PnpmWorkspacePluginTest {

  @Test
  fun `registers the pnpm lifecycle tasks on the root project`(@TempDir directory: File) {
    val project = workspaceProject(directory)

    assertThat(project.tasks.getByName("pnpmSetup")).isInstanceOf(PnpmSetupTask::class.java)
    assertThat(project.tasks.getByName("pnpmInstall")).isInstanceOf(PnpmTask::class.java)
    assertThat(project.tasks.getByName("pnpmDedupe")).isInstanceOf(PnpmTask::class.java)
    assertThat(project.tasks.getByName("pnpmClean")).isInstanceOf(PnpmTask::class.java)

    assertThat(project.tasks.getByName("pnpmInstall").group).isEqualTo("pnpm")
    assertThat((project.tasks.getByName("pnpmInstall") as PnpmTask).arguments.get())
      .containsExactly("install")
    assertThat((project.tasks.getByName("pnpmDedupe") as PnpmTask).arguments.get())
      .containsExactly("dedupe")
    assertThat((project.tasks.getByName("pnpmClean") as PnpmTask).arguments.get())
      .containsExactly("clean")
  }

  @Test
  fun `reads the pinned pnpm version from package json`(@TempDir directory: File) {
    val project = workspaceProject(directory)

    assertThat(extension(project).version.get()).isEqualTo(PNPM_VERSION)
  }

  @Test
  fun `derives the install directory and the archive url from the pinned version`(
    @TempDir directory: File
  ) {
    val project = workspaceProject(directory)
    val extension = extension(project)

    assertThat(extension.installDirectory.get().asFile)
      .isEqualTo(File(project.projectDir, ".gradle/pnpm/$PNPM_VERSION"))
    assertThat(extension.archiveUrl.get())
      .startsWith("https://github.com/pnpm/pnpm/releases/download/v$PNPM_VERSION/pnpm-")
  }

  @Test
  fun `uses an explicitly configured executable without downloading pnpm`(
    @TempDir directory: File
  ) {
    val project = workspaceProject(directory)
    extension(project).executable.set("/opt/pnpm/pnpm")

    val install = project.tasks.getByName("pnpmInstall") as PnpmTask
    assertThat(install.executable.get()).isEqualTo("/opt/pnpm/pnpm")
  }

  @Test
  fun `makes every pnpm task depend on the setup task`(@TempDir directory: File) {
    val project = workspaceProject(directory)

    val install = project.tasks.getByName("pnpmInstall")
    assertThat(install.taskDependencies.getDependencies(install).map { it.name })
      .contains("pnpmSetup")
  }

  @Test
  fun `cannot be applied to a subproject`(@TempDir directory: File) {
    val root = workspaceProject(directory)
    val subproject = ProjectBuilder.builder().withName("frontend").withParent(root).build()

    assertThatThrownBy { subproject.pluginManager.apply("de.cronn.pnpm-workspace") }
      .hasRootCauseInstanceOf(org.gradle.api.GradleException::class.java)
      .rootCause()
      .hasMessageContaining("must be applied to the root project")
      .hasMessageContaining("de.cronn.pnpm-package")
  }

  private fun extension(project: Project): PnpmExtension =
    project.extensions.getByType(PnpmExtension::class.java)

  internal companion object {
    const val PNPM_VERSION = "11.23.0"

    fun writePackageJson(directory: File, version: String = PNPM_VERSION) {
      File(directory, "package.json")
        .writeText(
          """
          {
            "name": "root",
            "private": true,
            "devEngines": { "packageManager": { "name": "pnpm", "version": "$version" } }
          }
          """
            .trimIndent()
        )
    }

    fun workspaceProject(directory: File): Project {
      writePackageJson(directory)
      val project = ProjectBuilder.builder().withProjectDir(directory).build()
      project.pluginManager.apply("de.cronn.pnpm-workspace")
      return project
    }
  }
}
