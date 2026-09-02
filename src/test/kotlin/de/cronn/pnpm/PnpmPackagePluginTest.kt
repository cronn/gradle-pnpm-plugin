package de.cronn.pnpm

import de.cronn.pnpm.PnpmWorkspacePluginTest.Companion.workspaceProject
import de.cronn.pnpm.task.PnpmExecTask
import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class PnpmPackagePluginTest {

  @Test
  fun `registers a task per tool`(@TempDir directory: File) {
    val project = packageProject(directory)

    assertThat(project.tasks.names)
      .contains("compileTypescript", "prettierCheck", "prettierFix", "eslintCheck", "eslintFix")
    assertThat(project.tasks.getByName("eslintCheck")).isInstanceOf(PnpmExecTask::class.java)
  }

  @Test
  fun `uses the same commands and arguments as the tools expect`(@TempDir directory: File) {
    val project = packageProject(directory)

    assertThat(execTask(project, "compileTypescript").command.get()).isEqualTo("tsc")
    assertThat(execTask(project, "compileTypescript").arguments.get()).containsExactly("--noEmit")
    assertThat(execTask(project, "prettierCheck").arguments.get()).containsExactly(".", "--check")
    assertThat(execTask(project, "prettierFix").arguments.get())
      .containsExactly(".", "--write", "--list-different")
    assertThat(execTask(project, "eslintCheck").arguments.get())
      .containsExactly(".", "--max-warnings=0")
    assertThat(execTask(project, "eslintFix").arguments.get())
      .containsExactly(".", "--max-warnings=0", "--fix")
  }

  @Test
  fun `appends extra arguments configured for a tool`(@TempDir directory: File) {
    val project = packageProject(directory)
    extension(project).prettier { it.extraArguments("--cache", "--log-level=warn") }

    assertThat(execTask(project, "prettierCheck").arguments.get())
      .containsExactly(".", "--check", "--cache", "--log-level=warn")
  }

  @Test
  fun `wires the check tasks into check`(@TempDir directory: File) {
    val project = packageProject(directory)

    assertThat(dependencyNames(project.tasks.getByName("check")))
      .contains("compileTypescript", "prettierCheck", "eslintCheck")
  }

  @Test
  fun `wires the fix tasks into fix`(@TempDir directory: File) {
    val project = packageProject(directory)
    val fix = project.tasks.getByName("fix")

    assertThat(fix.group).isEqualTo("verification")
    assertThat(dependencyNames(fix)).contains("prettierFix", "eslintFix")
  }

  @Test
  fun `runs prettier after eslint when fixing`(@TempDir directory: File) {
    val project = packageProject(directory)
    val prettierFix = project.tasks.getByName("prettierFix")

    assertThat(prettierFix.mustRunAfter.getDependencies(prettierFix).map { it.name })
      .containsExactly("eslintFix")
  }

  @Test
  fun `makes the linting tasks depend on the typescript compilation`(@TempDir directory: File) {
    val project = packageProject(directory)

    assertThat(dependencyNames(project.tasks.getByName("eslintCheck")))
      .contains("compileTypescript")
    assertThat(dependencyNames(project.tasks.getByName("eslintFix"))).contains("compileTypescript")
  }

  @Test
  fun `removes a disabled tool from check and fix`(@TempDir directory: File) {
    val project = packageProject(directory)
    extension(project).eslint { it.enabled.set(false) }

    assertThat(dependencyNames(project.tasks.getByName("check")))
      .contains("prettierCheck")
      .doesNotContain("eslintCheck")
    assertThat(dependencyNames(project.tasks.getByName("fix")))
      .contains("prettierFix")
      .doesNotContain("eslintFix")
  }

  @Test
  fun `makes the tool tasks depend on the workspace install`(@TempDir directory: File) {
    val project = packageProject(directory)

    assertThat(dependencyNames(project.tasks.getByName("prettierCheck"))).contains("pnpmInstall")
  }

  @Test
  fun `applies the base plugin so the lifecycle tasks exist`(@TempDir directory: File) {
    val project = packageProject(directory)

    assertThat(project.plugins.hasPlugin("base")).isTrue()
    assertThat(project.plugins.hasPlugin("de.cronn.pnpm-base")).isTrue()
  }

  private fun extension(project: Project): PnpmPackageExtension =
    project.extensions.getByType(PnpmPackageExtension::class.java)

  private fun execTask(project: Project, name: String): PnpmExecTask =
    project.tasks.getByName(name) as PnpmExecTask

  private fun dependencyNames(task: Task): List<String> =
    task.taskDependencies.getDependencies(task).map { it.name }

  private fun packageProject(directory: File): Project {
    val root = workspaceProject(directory)
    val packageDirectory = File(directory, "frontend").apply { mkdirs() }
    val project =
      ProjectBuilder.builder()
        .withName("frontend")
        .withParent(root)
        .withProjectDir(packageDirectory)
        .build()
    project.pluginManager.apply("de.cronn.pnpm-package")
    return project
  }
}
