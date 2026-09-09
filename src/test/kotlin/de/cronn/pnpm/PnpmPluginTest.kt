package de.cronn.pnpm

import de.cronn.pnpm.internal.PnpmDistribution
import de.cronn.pnpm.internal.PnpmPlatform
import de.cronn.pnpm.internal.PnpmRepository.PNPM_GROUP
import de.cronn.pnpm.internal.PnpmRepository.PNPM_MODULE
import de.cronn.pnpm.task.EslintTask
import de.cronn.pnpm.task.PnpmExecTask
import de.cronn.pnpm.task.PnpmSetupTask
import de.cronn.pnpm.task.PnpmTask
import de.cronn.pnpm.task.PnpmToolTask
import de.cronn.pnpm.task.PrettierTask
import de.cronn.pnpm.task.TypescriptTask
import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.provider.Provider
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
  fun `creates the pnpm extension in every project`(@TempDir directory: File) {
    val project = packageProject(directory)
    val root = project.rootProject

    // Every project has one, so that the plugin surface a convention plugin sees does not depend on
    // the role of the project the accessors happened to be generated from.
    assertThat(root.extensions.findByName("pnpm")).isInstanceOf(PnpmExtension::class.java)
    assertThat(project.extensions.findByName("pnpm")).isInstanceOf(PnpmExtension::class.java)
    assertThat(extension(project)).isNotSameAs(extension(root))

    assertThat(extension(project).workspaceRootPath.get()).isEqualTo(":")
    assertThat(extension(project).version.get()).isEqualTo(extension(root).version.get())
    assertThat(extension(project).installDirectory.get())
      .isEqualTo(extension(root).installDirectory.get())
  }

  @Test
  fun `a package keeps following its workspace root configured later`(@TempDir directory: File) {
    val project = packageProject(directory)
    val root = project.rootProject

    // Inherited as a convention rather than copied, so configuring the workspace root after a
    // package was configured still reaches that package.
    extension(root).version.set("11.0.0")

    assertThat(extension(project).version.get()).isEqualTo("11.0.0")
    assertThat(extension(project).installDirectory.get().asFile)
      .isEqualTo(File(root.projectDir, ".gradle/pnpm/11.0.0"))
  }

  @Test
  fun `a package can override the pnpm version for its own tasks`(@TempDir directory: File) {
    val project = packageProject(directory)
    val root = project.rootProject
    extension(root).version.set("11.0.0")

    extension(project).version.set("11.1.0")

    assertThat(pnpmTask(root, "pnpmInstall").pnpmVersion.get()).isEqualTo("11.0.0")
    assertThat(toolTask(project, "prettierCheck").pnpmVersion.get()).isEqualTo("11.1.0")
  }

  @Test
  fun `a package follows a redirected workspace root`(@TempDir directory: File) {
    val project = packageProject(directory)

    extension(project.rootProject).workspaceRootPath.set(":other")

    assertThat(extension(project).workspaceRootPath.get()).isEqualTo(":other")
    assertThat(dependencyPaths(toolTask(project, "prettierCheck")))
      .contains(":other:pnpmSetup", ":other:pnpmInstall")
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
  fun `uses the default pnpm version bundled with the plugin`(@TempDir directory: File) {
    val project = workspaceProject(directory)

    assertThat(extension(project).version.get()).isEqualTo(PnpmPlugin.DEFAULT_PNPM_VERSION)
  }

  @Test
  fun `derives the install directory and the distribution from the default version`(
    @TempDir directory: File
  ) {
    val project = workspaceProject(directory)

    assertThat(extension(project).installDirectory.get().asFile)
      .isEqualTo(File(project.projectDir, ".gradle/pnpm/${PnpmPlugin.DEFAULT_PNPM_VERSION}"))
    assertThat(distributionDependency(project).version).isEqualTo(PnpmPlugin.DEFAULT_PNPM_VERSION)
  }

  @Test
  fun `derives the install directory and the distribution from a configured version`(
    @TempDir directory: File
  ) {
    val project = workspaceProject(directory)
    extension(project).version.set(PNPM_VERSION)

    assertThat(extension(project).installDirectory.get().asFile)
      .isEqualTo(File(project.projectDir, ".gradle/pnpm/$PNPM_VERSION"))

    val dependency = distributionDependency(project)
    assertThat(dependency.group).isEqualTo(PNPM_GROUP)
    assertThat(dependency.name).isEqualTo(PNPM_MODULE)
    assertThat(dependency.version).isEqualTo(PNPM_VERSION)
    assertThat(dependency.isTransitive).isFalse()

    val platform = PnpmPlatform.current()
    val artifact = dependency.artifacts.single()
    assertThat(artifact.name).isEqualTo(PNPM_MODULE)
    assertThat(artifact.classifier).isEqualTo(platform.identifier)
    assertThat(artifact.extension).isEqualTo(platform.archiveExtension)
  }

  @Test
  fun `declares the distribution configurations only on the workspace root`(
    @TempDir directory: File
  ) {
    val project = packageProject(directory)

    assertThat(project.configurations.names)
      .doesNotContain(
        PnpmDistribution.DECLARED_CONFIGURATION_NAME,
        PnpmDistribution.ARCHIVE_CONFIGURATION_NAME,
      )
    assertThat(project.rootProject.configurations.names)
      .contains(
        PnpmDistribution.DECLARED_CONFIGURATION_NAME,
        PnpmDistribution.ARCHIVE_CONFIGURATION_NAME,
      )
  }

  /**
   * The setup task has nothing to resolve when pnpm comes from somewhere else. The configuration
   * cache resolves the inputs of a task while it stores the entry, before any `onlyIf` runs, so an
   * empty input is what keeps such a build from needing a repository at all.
   */
  @Test
  fun `resolves no distribution when pnpm is not managed by the plugin`(@TempDir directory: File) {
    val project = workspaceProject(directory)
    extension(project).executable.set("/usr/local/bin/pnpm")

    assertThat(setupTask(project).distributionArchive.isEmpty).isTrue()
    assertThat(setupTask(project).required.get()).isFalse()
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
    assertThat(extension.workspaceRootPath.get()).isEqualTo(":frontend")
    assertThat(extension.installDirectory.get().asFile)
      .isEqualTo(File(frontend.projectDir, ".gradle/pnpm/${PnpmPlugin.DEFAULT_PNPM_VERSION}"))
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
    assertThat(extension(project).workspaceRootPath.get()).isEqualTo(":")
  }

  /**
   * Gradle derives the type-safe accessors of a convention plugin by applying the plugin to a
   * synthetic project over an empty temporary directory, and fails the whole build if that throws.
   * A project with an empty directory is exactly what this builds, so this is the regression guard
   * for using the plugin from a convention plugin.
   */
  @Test
  fun `applies to a project with no pnpm files`(@TempDir directory: File) {
    val project = ProjectBuilder.builder().withProjectDir(directory).build()

    project.pluginManager.apply(PLUGIN_ID)

    assertThat(project.extensions.findByName("pnpm")).isInstanceOf(PnpmExtension::class.java)
    assertThat(project.extensions.findByName("typescript"))
      .isInstanceOf(TypescriptExtension::class.java)
    assertThat(project.extensions.findByName("prettier"))
      .isInstanceOf(PrettierExtension::class.java)
    assertThat(project.extensions.findByName("eslint")).isInstanceOf(EslintExtension::class.java)
    assertThat(project.tasks.names).contains("compileTypescript", "prettierCheck", "eslintCheck")

    // It takes no part in the pnpm build, so it owns neither the lifecycle tasks nor the
    // distribution of a workspace root.
    assertThat(project.tasks.names)
      .doesNotContain("pnpmSetup", "pnpmInstall", "pnpmDedupe", "pnpmClean")
    assertThat(project.configurations.names)
      .doesNotContain(
        PnpmDistribution.DECLARED_CONFIGURATION_NAME,
        PnpmDistribution.ARCHIVE_CONFIGURATION_NAME,
      )
  }

  @Test
  fun `reports the missing workspace root when a pnpm task of such a project runs`(
    @TempDir directory: File
  ) {
    val project = ProjectBuilder.builder().withProjectDir(directory).build()
    project.pluginManager.apply(PLUGIN_ID)
    val task = project.tasks.register("runSomething", PnpmExecTask::class.java).get()

    // Gradle wraps the failure of a dependency provider, so only the message is asserted on.
    assertThatThrownBy { task.taskDependencies.getDependencies(task) }
      .rootCause()
      .isInstanceOf(GradleException::class.java)
      .hasMessageContaining(": takes no part in the pnpm build")
      .hasMessageContaining("neither a pnpm-workspace.yaml nor a package.json")
      .hasMessageContaining("Add a pnpm-workspace.yaml to the workspace root")
  }

  @Test
  fun `does not report a missing workspace root for a real workspace root`(
    @TempDir directory: File
  ) {
    val project = workspaceProject(directory)

    assertThat(dependencyNames(project.tasks.getByName("prettierCheck")))
      .contains("pnpmInstall", "pnpmSetup")
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
  fun `registers each predefined task as the task type of its tool`(@TempDir directory: File) {
    val project = packageProject(directory)

    assertThat(project.tasks.getByName("compileTypescript"))
      .isInstanceOf(TypescriptTask::class.java)
    assertThat(project.tasks.getByName("prettierCheck")).isInstanceOf(PrettierTask::class.java)
    assertThat(project.tasks.getByName("prettierFix")).isInstanceOf(PrettierTask::class.java)
    assertThat(project.tasks.getByName("eslintCheck")).isInstanceOf(EslintTask::class.java)
    assertThat(project.tasks.getByName("eslintFix")).isInstanceOf(EslintTask::class.java)
  }

  @Test
  fun `uses the same commands and arguments as the tools expect`(@TempDir directory: File) {
    val project = packageProject(directory)

    assertThat(toolTask(project, "compileTypescript").command.get()).isEqualTo("tsc")
    assertThat(toolTask(project, "compileTypescript").arguments.get()).containsExactly("--noEmit")
    assertThat(toolTask(project, "prettierCheck").command.get()).isEqualTo("prettier")
    assertThat(toolTask(project, "prettierCheck").arguments.get()).containsExactly("--check")
    assertThat(toolTask(project, "prettierFix").arguments.get())
      .containsExactly("--write", "--list-different")
    assertThat(toolTask(project, "eslintCheck").command.get()).isEqualTo("eslint")
    assertThat(toolTask(project, "eslintCheck").arguments.get()).containsExactly("--max-warnings=0")
    assertThat(toolTask(project, "eslintFix").arguments.get())
      .containsExactly("--max-warnings=0", "--fix")
  }

  @Test
  fun `uses the sources of the tool as the inputs of its tasks`(@TempDir directory: File) {
    val project = packageProject(directory)

    assertThat(sourceNames(toolTask(project, "prettierCheck"))).containsExactly(*PRETTIER_SOURCES)
    assertThat(sourceNames(toolTask(project, "prettierFix"))).containsExactly(*PRETTIER_SOURCES)
    assertThat(sourceNames(toolTask(project, "eslintCheck"))).containsExactly(*ESLINT_SOURCES)
    assertThat(sourceNames(toolTask(project, "compileTypescript"))).containsExactly(*BASE_SOURCES)
  }

  @Test
  fun `takes the extra arguments of a tool over to its tasks`(@TempDir directory: File) {
    val project = packageProject(directory)
    prettier(project).extraArguments("--cache", "--log-level=warn")

    assertThat(toolTask(project, "prettierCheck").extraArguments.get())
      .containsExactly("--cache", "--log-level=warn")
  }

  @Test
  fun `resolves the sources of a tool from its includes and excludes`(@TempDir directory: File) {
    val project = packageProject(directory)
    File(project.projectDir, "src/nested").mkdirs()
    File(project.projectDir, "src/nested/app.ts").writeText("export const app = 1\n")
    File(project.projectDir, "generated.ts").writeText("export const generated = 1\n")
    eslint(project).excludes("generated.ts")

    assertThat(sourceNames(toolTask(project, "eslintCheck")))
      .containsExactly("eslint.config.ts", "prettier.config.ts", "src/nested/app.ts")
  }

  @Test
  fun `adds to the default patterns when includes is called as a method`(@TempDir directory: File) {
    val project = packageProject(directory)
    File(project.projectDir, "types").mkdirs()
    File(project.projectDir, "types/api.d.ts").writeText("export {}\n")
    eslint(project).includes("types/**")

    assertThat(sourceNames(toolTask(project, "eslintCheck")))
      .containsExactly(*ESLINT_SOURCES, "types/api.d.ts")
  }

  @Test
  fun `adds a list of patterns when includes and excludes are called with one`(
    @TempDir directory: File
  ) {
    val project = packageProject(directory)
    File(project.projectDir, "types").mkdirs()
    File(project.projectDir, "types/api.d.ts").writeText("export {}\n")
    File(project.projectDir, "generated.ts").writeText("export const generated = 1\n")
    eslint(project).includes(listOf("types/**", "generated.ts"))
    eslint(project).excludes(listOf("generated.ts"))

    assertThat(sourceNames(toolTask(project, "eslintCheck")))
      .containsExactly(*ESLINT_SOURCES, "types/api.d.ts")
  }

  @Test
  fun `replaces the default patterns with the configured includes`(@TempDir directory: File) {
    val project = packageProject(directory)
    File(project.projectDir, "src").mkdirs()
    File(project.projectDir, "src/app.ts").writeText("export const app = 1\n")
    eslint(project).includes.set(listOf("src/**/*.ts"))

    assertThat(sourceNames(toolTask(project, "eslintCheck"))).containsExactly("src/app.ts")
  }

  @Test
  fun `configures a tool task registered by the build script like a predefined one`(
    @TempDir directory: File
  ) {
    val project = packageProject(directory)
    prettier(project).extraArguments("--cache")
    val custom =
      project.tasks.register("prettierDocs", PrettierTask::class.java) { task ->
        task.arguments.set(listOf("--check"))
      }

    val task = custom.get()
    assertThat(task.command.get()).isEqualTo("prettier")
    assertThat(task.extraArguments.get()).containsExactly("--cache")
    assertThat(sourceNames(task)).containsExactly(*PRETTIER_SOURCES)
    assertThat(task.group).isEqualTo("verification")
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

  private fun distributionDependency(project: Project): ExternalModuleDependency =
    project.rootProject.configurations
      .getByName(PnpmDistribution.DECLARED_CONFIGURATION_NAME)
      .dependencies
      .single() as ExternalModuleDependency

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

  private fun toolTask(project: Project, name: String): PnpmToolTask =
    project.tasks.getByName(name) as PnpmToolTask

  /** The sources of [task] the way they reach the command line: relative and sorted. */
  private fun sourceNames(task: PnpmToolTask): List<String> {
    val directory = task.workingDirectory.get().asFile
    return task.sources.files.map { it.relativeTo(directory).invariantSeparatorsPath }.sorted()
  }

  private fun pnpmTask(project: Project, name: String): PnpmTask =
    project.tasks.getByName(name) as PnpmTask

  private fun dependencyNames(task: Task): List<String> =
    task.taskDependencies.getDependencies(task).map { it.name }

  /**
   * The task paths [task] declares a dependency on, without resolving them. The paths of another
   * project's lifecycle tasks are what the plugin wires in, and they need no such project to exist.
   */
  private fun dependencyPaths(task: Task): List<String> =
    task.dependsOn.filterIsInstance<Provider<*>>().map { it.get().toString() }

  internal companion object {
    const val PNPM_VERSION = "11.23.0"
    const val PLUGIN_ID = "de.cronn.gradle-pnpm-plugin"

    /** The files of a package project that match the default patterns of each tool. */
    val BASE_SOURCES: Array<String> = arrayOf("eslint.config.ts", "prettier.config.ts")
    val ESLINT_SOURCES: Array<String> = BASE_SOURCES
    val PRETTIER_SOURCES: Array<String> =
      arrayOf("eslint.config.ts", "package.json", "prettier.config.ts", "tsconfig.json")

    fun writePackageJson(directory: File) {
      directory.mkdirs()
      File(directory, "package.json")
        .writeText(
          """
          {
            "name": "root",
            "private": true
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
