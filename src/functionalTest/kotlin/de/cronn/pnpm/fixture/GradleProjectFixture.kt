package de.cronn.pnpm.fixture

import java.io.File
import org.gradle.testkit.runner.GradleRunner

/** Builds a pnpm workspace on disk and runs Gradle against it, with a stub as its pnpm. */
class GradleProjectFixture(rootDirectory: File) : TestKitBuild(rootDirectory) {

  val stub: PnpmStub = PnpmStub(File(rootDirectory, "pnpm-stub"))

  private lateinit var stubExecutable: File

  /**
   * Writes a workspace whose root project is the pnpm workspace root and whose [packages] are its
   * pnpm packages. Every project applies `de.cronn.gradle-pnpm-plugin`.
   */
  fun writeWorkspace(
    packages: List<String> = emptyList(),
    rootBuildScript: String = "",
    packageBuildScript: String = "",
    /** The pinned pnpm version, or `null` to fall back to the plugin's default version. */
    pnpmVersion: String? = PNPM_VERSION,
    /** Body of the `pnpm { }` block; defaults to pointing the build at the stub. */
    pnpmConfiguration: String? = null,
    /** Added to the `pnpm { }` block, so the plugin registers its repository over a local URL. */
    repositoryUrl: String? = null,
    /** Appended to the settings script, for example a `dependencyResolutionManagement` block. */
    settingsScript: String = "",
    /** Whether every package also gets a `playwright.config.ts`, which enables Playwright. */
    playwright: Boolean = false,
    /** Whether every package also gets a `vitest.config.ts`, which enables Vitest. */
    vitest: Boolean = false,
  ) {
    stubExecutable = stub.install()

    writeSettings(
      rootProjectName = "workspace",
      projects = packages,
      settingsScript = settingsScript,
    )
    writePackageRoot("", packages)
    write(
      "build.gradle.kts",
      """
      plugins { id("${ProjectFiles.PLUGIN_ID}") }

      pnpm {
        ${pnpmVersion?.let { "version = \"$it\"" } ?: ""}
        ${repositoryUrl?.let { "repositoryUrl = \"$it\"" } ?: ""}
        ${pnpmConfiguration ?: "executable = ${quoted(stubExecutable)}"}
      }

      $rootBuildScript
      """,
    )

    packages.forEach { name -> writePackage(name, packageBuildScript, playwright, vitest) }
  }

  /**
   * Writes a workspace with the single package [name], holding a TypeScript module at each of
   * [sourceFiles] for the tools to run over.
   */
  fun writeWorkspaceWithPackage(
    name: String = "frontend",
    packageBuildScript: String = "",
    sourceFiles: List<String> = listOf("main.ts"),
    playwright: Boolean = false,
    vitest: Boolean = false,
  ): GradleProjectFixture {
    writeWorkspace(
      packages = listOf(name),
      packageBuildScript = packageBuildScript,
      playwright = playwright,
      vitest = vitest,
    )
    sourceFiles.forEach { path ->
      write("$name/$path", "export const ${path.substringAfterLast('/').substringBefore('.')} = 1")
    }
    return this
  }

  /**
   * Writes a build whose Gradle root project holds no pnpm files at all, and whose pnpm workspace
   * root is the [workspaceRoot] project. Covers the layout where a Gradle build only embeds a pnpm
   * workspace.
   */
  fun writeNestedWorkspace(
    workspaceRoot: String = "frontend",
    packages: List<String> = listOf("app"),
    pnpmVersion: String = PNPM_VERSION,
    /** Projects that hold no pnpm files and take no part in the pnpm build. */
    extraProjects: List<String> = emptyList(),
  ) {
    stubExecutable = stub.install()

    val packagePaths = packages.map { "$workspaceRoot:$it" }
    writeSettings(
      rootProjectName = "build",
      projects = listOf(workspaceRoot) + packagePaths + extraProjects,
    )
    write("build.gradle.kts", "// no pnpm files here")
    writePackageRoot(workspaceRoot, packages)
    write(
      "$workspaceRoot/build.gradle.kts",
      """
      plugins { id("${ProjectFiles.PLUGIN_ID}") }

      pnpm {
        version = "$pnpmVersion"
        executable = ${quoted(stubExecutable)}
      }
      """,
    )

    packages.forEach { name -> writePackage("$workspaceRoot/$name") }
  }

  /**
   * Writes a `buildSrc` build holding the precompiled script plugins [conventionPlugins], keyed by
   * plugin id.
   *
   * The plugin under test is put on buildSrc's compile classpath explicitly: `buildSrc` is a
   * separate build, so it does not see the plugin classpath TestKit injects into the outer build.
   */
  fun writeConventionPlugins(conventionPlugins: Map<String, String>) {
    write("buildSrc/settings.gradle.kts", """rootProject.name = "build-logic"""")
    write(
      "buildSrc/build.gradle.kts",
      """
      plugins { `kotlin-dsl` }

      repositories { mavenCentral() }

      dependencies { implementation(files(${pluginClasspathLiterals()})) }
      """,
    )
    conventionPlugins.forEach { (id, body) ->
      write("buildSrc/src/main/kotlin/$id.gradle.kts", body)
    }
  }

  /**
   * A workspace whose root project and [packages] all get the plugin through the same convention
   * plugin in `buildSrc`, which is what makes it the test of the role-independent plugin surface:
   * the convention plugin configures `pnpm { }` and `prettier { }` for a workspace root and for a
   * package alike.
   */
  fun writeWorkspaceWithConventionPlugins(packages: List<String> = listOf("frontend")) {
    stubExecutable = stub.install()

    writeConventionPlugins(
      mapOf(
        // Applied to the workspace root and to every package alike: it may only use the part of the
        // plugin surface that does not depend on the role of a project.
        CONVENTION_PLUGIN_ID to
          """
          plugins { id("${ProjectFiles.PLUGIN_ID}") }

          pnpm {
            version = "$PNPM_VERSION"
            executable = ${quoted(stubExecutable)}
          }

          prettier { extraArguments("--cache") }
          """,
        // Applied to the workspace root only, and composed on top of the shared one. The lifecycle
        // tasks exist only there, and only under their name: the synthetic project the accessors
        // come from is no workspace root.
        WORKSPACE_CONVENTION_PLUGIN_ID to
          """
          import de.cronn.pnpm.task.PnpmTask

          plugins { id("$CONVENTION_PLUGIN_ID") }

          tasks.named<PnpmTask>("pnpmInstall") { ignoreExitValue = false }
          """,
      ),
    )

    writeSettings(rootProjectName = "workspace", projects = packages)
    writePackageRoot("", packages)
    write("build.gradle.kts", """plugins { id("$WORKSPACE_CONVENTION_PLUGIN_ID") }""")
    packages.forEach { name ->
      write("$name/build.gradle.kts", """plugins { id("$CONVENTION_PLUGIN_ID") }""")
      write("$name/package.json", """{ "name": "$name" }""")
      writeCheckConfigs(name)
    }
  }

  private fun writeSettings(
    rootProjectName: String,
    projects: List<String>,
    settingsScript: String = "",
  ) {
    write(
      "settings.gradle.kts",
      """
      rootProject.name = "$rootProjectName"
      ${projects.joinToString("\n") { "include(\"$it\")" }}

      $settingsScript
      """,
    )
  }

  /** The files that make [directory] a pnpm workspace root. */
  private fun writePackageRoot(directory: String, packages: List<String>) {
    val prefix = if (directory.isEmpty()) "" else "$directory/"
    ProjectFiles.writeRootPackageJson(directory(directory))
    write("${prefix}pnpm-lock.yaml", "lockfileVersion: '9.0'")
    write("${prefix}pnpm-workspace.yaml", "packages:\n${packages.joinToString("\n") { "  - $it" }}")
    writeCheckConfigs(directory)
  }

  /** A pnpm package at [path], with a config file for every tool so their tasks exist. */
  private fun writePackage(
    path: String,
    buildScript: String = "",
    playwright: Boolean = false,
    vitest: Boolean = false,
  ) {
    write(
      "$path/build.gradle.kts",
      """
      plugins { id("${ProjectFiles.PLUGIN_ID}") }

      $buildScript
      """,
    )
    write("$path/package.json", """{ "name": "${path.substringAfterLast('/')}" }""")
    writeCheckConfigs(path)
    if (playwright) writePlaywrightConfig(path)
    if (vitest) writeVitestConfig(path)
  }

  /** Writes a config file for TypeScript, ESLint and Prettier into the project at [directory]. */
  fun writeCheckConfigs(directory: String) {
    ProjectFiles.writeCheckConfigs(directory(directory))
  }

  fun writePlaywrightConfig(directory: String) {
    ProjectFiles.writePlaywrightConfig(directory(directory))
  }

  fun writeVitestConfig(directory: String) {
    ProjectFiles.writeVitestConfig(directory(directory))
  }

  /**
   * Runs Gradle, with [pnpmOnPath] as the only pnpm the build finds on its `PATH`.
   *
   * See [gradleRunner] for [injectPluginClasspath].
   */
  fun runner(
    vararg arguments: String,
    pnpmOnPath: File? = null,
    injectPluginClasspath: Boolean = true,
  ): GradleRunner =
    gradleRunner(arguments.toList(), environmentWith(pnpmOnPath), injectPluginClasspath)

  /** The pnpm executable the plugin installs for [version] with the default install directory. */
  fun installedPnpm(version: String = PNPM_VERSION): File =
    directory(".gradle/pnpm/$version/${PnpmArchiveFixture.executableName}")

  /** The arguments a tool task hands its tool the config file at [path] with. */
  fun configArguments(path: String, option: String = "--config"): Array<String> =
    arrayOf(option, directory(path).canonicalPath)

  /**
   * The environment of the test JVM, with every `PATH` entry that holds a pnpm removed and the
   * directory of [pnpmOnPath] put in front, so that a pnpm installed on the machine running the
   * tests cannot take part in any test.
   *
   * [INHERITED_VARIABLE] stands for whatever a real build inherits from its environment, so that a
   * test can tell an environment a task added to from one it replaced.
   */
  private fun environmentWith(pnpmOnPath: File?): Map<String, String> =
    (System.getenv() + mapOf(INHERITED_VARIABLE to INHERITED_VALUE)).mapValues { (name, value) ->
      if (!name.equals("PATH", ignoreCase = true)) {
        value
      } else {
        val directories =
          value.split(File.pathSeparator).filterNot { directory ->
            PNPM_EXECUTABLE_NAMES.any { File(directory, it).isFile }
          }
        (listOfNotNull(pnpmOnPath?.parentFile?.absolutePath) + directories).joinToString(
          File.pathSeparator,
        )
      }
    }

  companion object {
    /** Variable every runner's environment holds, recorded by [PnpmStub]. */
    const val INHERITED_VARIABLE: String = "${PnpmStub.ENVIRONMENT_PREFIX}INHERITED"

    const val INHERITED_VALUE: String = "inherited"

    const val PNPM_VERSION: String = ProjectFiles.PNPM_VERSION

    /**
     * Id of the convention plugin every project of [writeWorkspaceWithConventionPlugins] applies.
     */
    const val CONVENTION_PLUGIN_ID: String = "pnpm-conventions"

    /** Id of the convention plugin only the workspace root applies. */
    const val WORKSPACE_CONVENTION_PLUGIN_ID: String = "pnpm-workspace-conventions"

    private val PNPM_EXECUTABLE_NAMES = listOf("pnpm", "pnpm.exe", "pnpm.cmd", "pnpm.bat")
  }
}
