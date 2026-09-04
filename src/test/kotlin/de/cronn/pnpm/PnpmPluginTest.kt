package de.cronn.pnpm

import de.cronn.pnpm.task.PnpmExecTask
import de.cronn.pnpm.task.PnpmSetupTask
import de.cronn.pnpm.task.PnpmTask
import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class PnpmPluginTest {

  @Test
  fun `registers the pnpm lifecycle tasks on the workspace root`(@TempDir directory: File) {
    val project = workspaceProject(directory)

    assertThat(project.tasks.getByName("pnpmSetup")).isInstanceOf(PnpmSetupTask::class.java)
    assertThat(project.tasks.getByName("pnpmInstall")).isInstanceOf(PnpmTask::class.java)
    assertThat(project.tasks.getByName("pnpmDedupe")).isInstanceOf(PnpmTask::class.java)
    assertThat(project.tasks.getByName("pnpmClean")).isInstanceOf(PnpmTask::class.java)

    assertThat(project.tasks.getByName("pnpmInstall").group).isEqualTo("pnpm")
    assertThat(pnpmTask(project, "pnpmInstall").arguments.get()).containsExactly("install")
    assertThat(pnpmTask(project, "pnpmDedupe").arguments.get()).containsExactly("dedupe")
    assertThat(pnpmTask(project, "pnpmClean").arguments.get()).containsExactly("clean")
  }

  @Test
  fun `registers the tool tasks on the workspace root as well`(@TempDir directory: File) {
    val project = workspaceProject(directory)

    assertThat(project.tasks.names)
      .contains("compileTypescript", "prettierCheck", "prettierFix", "eslintCheck", "eslintFix")
    assertThat(dependencyNames(project.tasks.getByName("check")))
      .contains("compileTypescript", "prettierCheck", "eslintCheck")
  }

  @Test
  fun `does not register the lifecycle tasks on a package`(@TempDir directory: File) {
    val project = packageProject(directory)

    assertThat(project.tasks.names)
      .doesNotContain("pnpmSetup", "pnpmInstall", "pnpmDedupe", "pnpmClean")
  }

  @Test
  fun `creates the pnpm extension only on the workspace root`(@TempDir directory: File) {
    val project = packageProject(directory)

    assertThat(project.extensions.findByType(PnpmExtension::class.java)).isNull()
    assertThat(project.rootProject.extensions.findByName("pnpm"))
      .isInstanceOf(PnpmExtension::class.java)
  }

  @Test
  fun `creates an extension per tool in every project`(@TempDir directory: File) {
    val root = workspaceProject(directory)
    val project = packageProject(directory)

    listOf(root, project).forEach { each ->
      assertThat(each.extensions.findByName("typescript"))
        .isInstanceOf(TypescriptExtension::class.java)
      assertThat(each.extensions.findByName("prettier")).isInstanceOf(PrettierExtension::class.java)
      assertThat(each.extensions.findByName("eslint")).isInstanceOf(EslintExtension::class.java)
    }
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

    assertThat(extension(project).installDirectory.get().asFile)
      .isEqualTo(File(project.projectDir, ".gradle/pnpm/$PNPM_VERSION"))
    assertThat(setupTask(project).archiveUrl.get())
      .startsWith("https://github.com/pnpm/pnpm/releases/download/v$PNPM_VERSION/pnpm-")
  }

  @Test
  fun `uses an explicitly configured executable without downloading pnpm`(
    @TempDir directory: File
  ) {
    val project = workspaceProject(directory)
    extension(project).executable.set("/opt/pnpm/pnpm")

    assertThat(pnpmTask(project, "pnpmInstall").executable.get()).isEqualTo("/opt/pnpm/pnpm")
  }

  @Test
  fun `makes every pnpm task depend on the setup task`(@TempDir directory: File) {
    val project = workspaceProject(directory)

    assertThat(dependencyNames(project.tasks.getByName("pnpmInstall"))).contains("pnpmSetup")
  }

  @Test
  fun `makes the tool tasks depend on the workspace install`(@TempDir directory: File) {
    val project = packageProject(directory)

    assertThat(dependencyNames(project.tasks.getByName("prettierCheck"))).contains("pnpmInstall")
  }

  // Discovery

  @Test
  fun `discovers a workspace root that is not the gradle root project`(@TempDir directory: File) {
    val root = ProjectBuilder.builder().withProjectDir(directory).build()
    val frontend = workspaceProject(File(directory, "frontend"), name = "frontend", parent = root)
    val app = packageProject(File(directory, "frontend/app"), name = "app", parent = frontend)

    val extension = extension(frontend)
    assertThat(extension.setupTaskPath.get()).isEqualTo(":frontend:pnpmSetup")
    assertThat(extension.installTaskPath.get()).isEqualTo(":frontend:pnpmInstall")
    assertThat(extension.installDirectory.get().asFile)
      .isEqualTo(File(frontend.projectDir, ".gradle/pnpm/$PNPM_VERSION"))
    assertThat(root.extensions.findByName("pnpm")).isNull()
    assertThat(app.tasks.names).contains("prettierCheck").doesNotContain("pnpmInstall")
  }

  @Test
  fun `treats a project without a workspace file as its own workspace root`(
    @TempDir directory: File
  ) {
    writePackageJson(directory)
    val project = ProjectBuilder.builder().withProjectDir(directory).build()

    project.pluginManager.apply(PLUGIN_ID)

    assertThat(project.tasks.names).contains("pnpmInstall", "prettierCheck")
    assertThat(extension(project).setupTaskPath.get()).isEqualTo(":pnpmSetup")
  }

  @Test
  fun `fails when the project is neither a workspace root nor a package`(@TempDir directory: File) {
    val project = ProjectBuilder.builder().withProjectDir(directory).build()

    assertThatThrownBy { project.pluginManager.apply(PLUGIN_ID) }
      .hasRootCauseInstanceOf(GradleException::class.java)
      .rootCause()
      .hasMessageContaining("Cannot tell what role : plays in the pnpm build")
      .hasMessageContaining("neither a pnpm-workspace.yaml nor a package.json")
  }

  @Test
  fun `fails when the workspace root does not apply the plugin`(@TempDir directory: File) {
    writePackageJson(directory)
    File(directory, "pnpm-workspace.yaml").writeText("packages:\n  - frontend\n")
    val root = ProjectBuilder.builder().withProjectDir(directory).build()
    val packageDirectory = File(directory, "frontend").apply { mkdirs() }
    File(packageDirectory, "package.json").writeText("""{ "name": "frontend" }""")
    val frontend =
      ProjectBuilder.builder()
        .withName("frontend")
        .withParent(root)
        .withProjectDir(packageDirectory)
        .build()

    assertThatThrownBy { frontend.pluginManager.apply(PLUGIN_ID) }
      .hasRootCauseInstanceOf(GradleException::class.java)
      .rootCause()
      .hasMessageContaining("it is the pnpm workspace root of :frontend")
      .hasMessageContaining("""Apply id("de.cronn.gradle-pnpm-plugin") in the build script of :""")
  }

  // Tool tasks

  @Test
  fun `uses the same commands and arguments as the tools expect`(@TempDir directory: File) {
    val project = packageProject(directory)

    assertThat(execTask(project, "compileTypescript").command.get()).isEqualTo("tsc")
    assertThat(execTask(project, "compileTypescript").arguments.get()).containsExactly("--noEmit")
    assertThat(execTask(project, "prettierCheck").arguments.get())
      .containsExactly(*PRETTIER_SOURCES, "--check")
    assertThat(execTask(project, "prettierFix").arguments.get())
      .containsExactly(*PRETTIER_SOURCES, "--write", "--list-different")
    assertThat(execTask(project, "eslintCheck").arguments.get())
      .containsExactly(*ESLINT_SOURCES, "--max-warnings=0", "--no-warn-ignored")
    assertThat(execTask(project, "eslintFix").arguments.get())
      .containsExactly(*ESLINT_SOURCES, "--max-warnings=0", "--no-warn-ignored", "--fix")
  }

  @Test
  fun `appends extra arguments configured for a tool`(@TempDir directory: File) {
    val project = packageProject(directory)
    prettier(project).extraArguments("--cache", "--log-level=warn")

    assertThat(execTask(project, "prettierCheck").arguments.get())
      .containsExactly(*PRETTIER_SOURCES, "--check", "--cache", "--log-level=warn")
  }

  @Test
  fun `passes the resolved sources to the tool`(@TempDir directory: File) {
    val project = packageProject(directory)
    File(project.projectDir, "src/nested").mkdirs()
    File(project.projectDir, "src/nested/app.ts").writeText("export const app = 1\n")
    File(project.projectDir, "generated.ts").writeText("export const generated = 1\n")
    eslint(project).excludes("generated.ts")

    assertThat(execTask(project, "eslintCheck").arguments.get())
      .containsExactly(
        "eslint.config.ts",
        "prettier.config.ts",
        "src/nested/app.ts",
        "--max-warnings=0",
        "--no-warn-ignored",
      )
  }

  @Test
  fun `replaces the default patterns with the configured includes`(@TempDir directory: File) {
    val project = packageProject(directory)
    File(project.projectDir, "src").mkdirs()
    File(project.projectDir, "src/app.ts").writeText("export const app = 1\n")
    eslint(project).includes.set(listOf("src/**/*.ts"))

    assertThat(execTask(project, "eslintCheck").arguments.get())
      .containsExactly("src/app.ts", "--max-warnings=0", "--no-warn-ignored")
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
    eslint(project).enabled.set(false)

    assertThat(dependencyNames(project.tasks.getByName("check")))
      .contains("prettierCheck")
      .doesNotContain("eslintCheck")
    assertThat(dependencyNames(project.tasks.getByName("fix")))
      .contains("prettierFix")
      .doesNotContain("eslintFix")
  }

  @Test
  fun `applies the base plugin so the lifecycle tasks exist`(@TempDir directory: File) {
    val project = packageProject(directory)

    assertThat(project.plugins.hasPlugin("base")).isTrue()
  }

  // Auto-discovery of the tools

  @Test
  fun `enables a tool only when the project is configured for it`(@TempDir directory: File) {
    val root = workspaceProject(directory)
    val packageDirectory = File(directory, "frontend").apply { mkdirs() }
    File(packageDirectory, "package.json").writeText("""{ "name": "frontend" }""")
    File(packageDirectory, "eslint.config.mjs").writeText("export default []\n")
    val project = childProject("frontend", root, packageDirectory)

    assertThat(eslint(project).enabled.get()).isTrue()
    assertThat(typescript(project).enabled.get()).isFalse()
    assertThat(prettier(project).enabled.get()).isFalse()

    assertThat(dependencyNames(project.tasks.getByName("check")))
      .contains("eslintCheck")
      .doesNotContain("compileTypescript", "prettierCheck")
  }

  @Test
  fun `does not enable eslint for a legacy eslintrc config`(@TempDir directory: File) {
    val root = workspaceProject(directory)
    val packageDirectory = File(directory, "frontend").apply { mkdirs() }
    File(packageDirectory, "package.json").writeText("""{ "name": "frontend" }""")
    File(packageDirectory, ".eslintrc.json").writeText("{}")
    val project = childProject("frontend", root, packageDirectory)

    assertThat(eslint(project).enabled.get()).isFalse()
  }

  @Test
  fun `an explicitly enabled tool wins over the discovery`(@TempDir directory: File) {
    val root = workspaceProject(directory)
    val packageDirectory = File(directory, "frontend").apply { mkdirs() }
    File(packageDirectory, "package.json").writeText("""{ "name": "frontend" }""")
    val project = childProject("frontend", root, packageDirectory)

    typescript(project).enabled.set(true)

    assertThat(typescript(project).enabled.get()).isTrue()
    assertThat(dependencyNames(project.tasks.getByName("check"))).contains("compileTypescript")
  }

  private fun setupTask(project: Project): PnpmSetupTask =
    project.tasks.getByName("pnpmSetup") as PnpmSetupTask

  private fun extension(project: Project): PnpmExtension =
    project.extensions.getByType(PnpmExtension::class.java)

  private fun typescript(project: Project): TypescriptExtension =
    project.extensions.getByType(TypescriptExtension::class.java)

  private fun prettier(project: Project): PrettierExtension =
    project.extensions.getByType(PrettierExtension::class.java)

  private fun eslint(project: Project): EslintExtension =
    project.extensions.getByType(EslintExtension::class.java)

  private fun execTask(project: Project, name: String): PnpmExecTask =
    project.tasks.getByName(name) as PnpmExecTask

  private fun pnpmTask(project: Project, name: String): PnpmTask =
    project.tasks.getByName(name) as PnpmTask

  private fun dependencyNames(task: Task): List<String> =
    task.taskDependencies.getDependencies(task).map { it.name }

  internal companion object {
    const val PNPM_VERSION = "11.23.0"
    const val PLUGIN_ID = "de.cronn.gradle-pnpm-plugin"

    /** The files of a package project that match the default patterns of each tool. */
    val ESLINT_SOURCES: Array<String> = arrayOf("eslint.config.ts", "prettier.config.ts")
    val PRETTIER_SOURCES: Array<String> =
      arrayOf("eslint.config.ts", "package.json", "prettier.config.ts", "tsconfig.json")

    fun writePackageJson(directory: File, version: String = PNPM_VERSION) {
      directory.mkdirs()
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

    /** Writes a config file for every tool, so that all of them are auto-enabled. */
    fun writeToolConfigs(directory: File) {
      directory.mkdirs()
      File(directory, "tsconfig.json").writeText("{}")
      File(directory, "eslint.config.ts").writeText("export default []\n")
      File(directory, "prettier.config.ts").writeText("export default {}\n")
    }

    /** A project that is a pnpm workspace root, because its directory has a pnpm-workspace.yaml. */
    fun workspaceProject(directory: File, name: String? = null, parent: Project? = null): Project {
      writePackageJson(directory)
      writeToolConfigs(directory)
      File(directory, "pnpm-workspace.yaml").writeText("packages:\n  - frontend\n")

      val project =
        if (parent == null) {
          ProjectBuilder.builder().withProjectDir(directory).build()
        } else {
          childProjectBuilder(requireNotNull(name), parent, directory)
        }
      project.pluginManager.apply(PLUGIN_ID)
      return project
    }

    /** A package of the workspace root [parent], defaulting to a `frontend` project below it. */
    fun packageProject(
      directory: File,
      name: String = "frontend",
      parent: Project? = null,
    ): Project {
      val (root, packageDirectory) =
        if (parent == null) {
          workspaceProject(directory) to File(directory, name)
        } else {
          parent to directory
        }

      packageDirectory.mkdirs()
      File(packageDirectory, "package.json").writeText("""{ "name": "$name" }""")
      writeToolConfigs(packageDirectory)

      return childProject(name, root, packageDirectory)
    }

    fun childProject(name: String, parent: Project, directory: File): Project =
      childProjectBuilder(name, parent, directory).also { it.pluginManager.apply(PLUGIN_ID) }

    private fun childProjectBuilder(name: String, parent: Project, directory: File): Project {
      directory.mkdirs()
      return ProjectBuilder.builder()
        .withName(name)
        .withParent(parent)
        .withProjectDir(directory)
        .build()
    }
  }
}
