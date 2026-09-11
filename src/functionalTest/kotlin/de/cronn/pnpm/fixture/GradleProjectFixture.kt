package de.cronn.pnpm.fixture

import java.io.File
import java.util.Properties
import org.gradle.testkit.runner.GradleRunner

/** Builds a pnpm workspace on disk and runs Gradle against it. */
class GradleProjectFixture(val rootDirectory: File) {

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
      plugins { id("de.cronn.gradle-pnpm-plugin") }

      pnpm {
        ${pnpmVersion?.let { "version = \"$it\"" } ?: ""}
        ${repositoryUrl?.let { "repositoryUrl = \"$it\"" } ?: ""}
        ${pnpmConfiguration ?: "executable = ${quoted(stubExecutable)}"}
      }

      $rootBuildScript
      """,
    )

    packages.forEach { name -> writePackage(name, packageBuildScript, playwright) }
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
      plugins { id("de.cronn.gradle-pnpm-plugin") }

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
          plugins { id("de.cronn.gradle-pnpm-plugin") }

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
      )
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

  /** Kotlin string literals of the plugin classpath, for an `implementation(files(...))` call. */
  private fun pluginClasspathLiterals(): String =
    pluginClasspath().joinToString(", ") { quoted(it) }

  /**
   * The classpath of the plugin under test, read from the metadata the `java-gradle-plugin` plugin
   * generates and puts on this source set's runtime classpath.
   */
  private fun pluginClasspath(): List<File> {
    val stream =
      checkNotNull(javaClass.classLoader.getResourceAsStream(PLUGIN_METADATA)) {
        "$PLUGIN_METADATA is not on the classpath"
      }
    val properties = Properties().apply { stream.use { load(it) } }
    // Properties.load has already unescaped the entry, so it splits on the plain path separator.
    return checkNotNull(properties.getProperty("implementation-classpath")) {
        "$PLUGIN_METADATA declares no implementation-classpath"
      }
      .split(File.pathSeparator)
      .map(::File)
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
    write(
      "${prefix}package.json",
      """
      {
        "name": "root",
        "private": true
      }
      """,
    )
    write("${prefix}pnpm-lock.yaml", "lockfileVersion: '9.0'")
    write("${prefix}pnpm-workspace.yaml", "packages:\n${packages.joinToString("\n") { "  - $it" }}")
    writeCheckConfigs(directory)
  }

  /** A pnpm package at [path], with a config file for every tool so all of them are enabled. */
  private fun writePackage(
    path: String,
    buildScript: String = "",
    playwright: Boolean = false,
  ) {
    write(
      "$path/build.gradle.kts",
      """
      plugins { id("de.cronn.gradle-pnpm-plugin") }

      $buildScript
      """,
    )
    write("$path/package.json", """{ "name": "${path.substringAfterLast('/')}" }""")
    writeCheckConfigs(path)
    if (playwright) writePlaywrightConfig(path)
  }

  /**
   * Writes a config file for TypeScript, ESLint and Prettier, which is what makes the plugin enable
   * those tools for a project.
   */
  fun writeCheckConfigs(directory: String) {
    val prefix = if (directory.isEmpty()) "" else "$directory/"
    write("${prefix}tsconfig.json", "{}")
    write("${prefix}eslint.config.ts", "export default []")
    write("${prefix}prettier.config.ts", "export default {}")
  }

  /**
   * Writes the `playwright.config.ts` that enables Playwright for [directory].
   *
   * Deliberately not part of [writeCheckConfigs]: enabling Playwright everywhere would add its
   * tasks, and its pnpm invocations, to every test that only cares about the source tools.
   */
  fun writePlaywrightConfig(directory: String) {
    val prefix = if (directory.isEmpty()) "" else "$directory/"
    write("${prefix}playwright.config.ts", "export default { testDir: \"tests\" }")
  }

  fun write(path: String, content: String) {
    val file = File(rootDirectory, path)
    file.parentFile.mkdirs()
    file.writeText(content.trimIndent().trim() + "\n")
  }

  /**
   * Runs Gradle, with [pnpmOnPath] as the only pnpm the build finds on its `PATH`.
   *
   * Set [injectPluginClasspath] to `false` for a build that gets the plugin through `buildSrc`:
   * injecting it into the outer build's script classpath as well would load the plugin's classes a
   * second time, so the extension the convention plugin created would not be the type the outer
   * build script sees.
   */
  fun runner(
    vararg arguments: String,
    pnpmOnPath: File? = null,
    injectPluginClasspath: Boolean = true,
  ): GradleRunner =
    GradleRunner.create()
      .withProjectDir(rootDirectory)
      .apply { if (injectPluginClasspath) withPluginClasspath() }
      .withEnvironment(environmentWith(pnpmOnPath))
      .withArguments(
        arguments.toList() +
          listOf(
            "--configuration-cache",
            // Turn every configuration cache problem into a build failure instead of a warning.
            "-Dorg.gradle.configuration-cache.problems=fail",
            "--stacktrace",
          )
      )
      .forwardOutput()

  fun directory(path: String): File = File(rootDirectory, path)

  /**
   * The environment of the test JVM, with every `PATH` entry that holds a pnpm removed and the
   * directory of [pnpmOnPath] put in front. The plugin reuses a pnpm from the `PATH`, so a pnpm
   * installed on the machine running the tests would otherwise decide the outcome of every test.
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
          File.pathSeparator
        )
      }
    }

  /** Kotlin string literal for [file], safe on Windows where paths contain backslashes. */
  private fun quoted(file: File): String = "\"${file.invariantSeparatorsPath}\""

  companion object {
    /** Variable every runner's environment holds, recorded by [PnpmStub]. */
    const val INHERITED_VARIABLE: String = "${PnpmStub.ENVIRONMENT_PREFIX}INHERITED"

    const val INHERITED_VALUE: String = "inherited"

    const val PNPM_VERSION: String = "11.23.0"

    /**
     * The declaration of the pnpm repository the README documents, over [url]. Builds that declare
     * their repositories themselves write exactly this, in a build script or in the settings
     * script, and it deliberately mentions none of the plugin's classes.
     */
    fun pnpmRepository(url: String): String =
      """
      exclusiveContent {
        forRepository {
          ivy {
            name = "pnpm"
            setUrl("$url")
            patternLayout { artifact("v[revision]/[artifact]-[classifier].[ext]") }
            metadataSources { artifact() }
          }
        }
        filter { includeModule("pnpm", "pnpm") }
      }
      """
        .trimIndent()

    /** A `dependencyResolutionManagement` block declaring [repositories]. */
    fun settingsRepositories(
      vararg repositories: String,
      failOnProjectRepositories: Boolean = false,
    ): String =
      """
      dependencyResolutionManagement {
        ${if (failOnProjectRepositories) "repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS" else ""}
        repositories {
          ${repositories.joinToString("\n")}
        }
      }
      """
        .trimIndent()

    /**
     * Id of the convention plugin every project of [writeWorkspaceWithConventionPlugins] applies.
     */
    const val CONVENTION_PLUGIN_ID: String = "pnpm-conventions"

    /** Id of the convention plugin only the workspace root applies. */
    const val WORKSPACE_CONVENTION_PLUGIN_ID: String = "pnpm-workspace-conventions"

    private const val PLUGIN_METADATA = "plugin-under-test-metadata.properties"

    private val PNPM_EXECUTABLE_NAMES = listOf("pnpm", "pnpm.exe", "pnpm.cmd", "pnpm.bat")
  }
}
