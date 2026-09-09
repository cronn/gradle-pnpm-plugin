package de.cronn.pnpm.fixture

import java.io.File
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
    /** Imports of the root build script; they have to precede the `plugins` block. */
    imports: List<String> = emptyList(),
    rootBuildScript: String = "",
    packageBuildScript: String = "",
    /** The pinned pnpm version, or `null` to fall back to the plugin's default version. */
    pnpmVersion: String? = PNPM_VERSION,
    /** Body of the `pnpm { }` block; defaults to pointing the build at the stub. */
    pnpmConfiguration: String? = null,
  ) {
    stubExecutable = stub.install()

    writeSettings(rootProjectName = "workspace", projects = packages)
    writePackageRoot("", packages)
    write(
      "build.gradle.kts",
      """
      ${imports.joinToString("\n      ") { "import $it" }}

      plugins { id("de.cronn.gradle-pnpm-plugin") }

      pnpm {
        ${pnpmVersion?.let { "version = \"$it\"" } ?: ""}
        ${pnpmConfiguration ?: "executable = ${quoted(stubExecutable)}"}
      }

      $rootBuildScript
      """,
    )

    packages.forEach { name -> writePackage(name, packageBuildScript) }
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
  ) {
    stubExecutable = stub.install()

    val packagePaths = packages.map { "$workspaceRoot:$it" }
    writeSettings(rootProjectName = "build", projects = listOf(workspaceRoot) + packagePaths)
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

  private fun writeSettings(rootProjectName: String, projects: List<String>) {
    write(
      "settings.gradle.kts",
      """
      rootProject.name = "$rootProjectName"
      ${projects.joinToString("\n") { "include(\"$it\")" }}
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
    writeToolConfigs(directory)
  }

  /** A pnpm package at [path], with a config file for every tool so all of them are enabled. */
  private fun writePackage(path: String, buildScript: String = "") {
    write(
      "$path/build.gradle.kts",
      """
      plugins { id("de.cronn.gradle-pnpm-plugin") }

      $buildScript
      """,
    )
    write("$path/package.json", """{ "name": "${path.substringAfterLast('/')}" }""")
    writeToolConfigs(path)
  }

  /**
   * Writes a config file for TypeScript, ESLint and Prettier, which is what makes the plugin enable
   * those tools for a project.
   */
  fun writeToolConfigs(directory: String) {
    val prefix = if (directory.isEmpty()) "" else "$directory/"
    write("${prefix}tsconfig.json", "{}")
    write("${prefix}eslint.config.ts", "export default []")
    write("${prefix}prettier.config.ts", "export default {}")
  }

  fun write(path: String, content: String) {
    val file = File(rootDirectory, path)
    file.parentFile.mkdirs()
    file.writeText(content.trimIndent().trim() + "\n")
  }

  /** Runs Gradle, with [pnpmOnPath] as the only pnpm the build finds on its `PATH`. */
  fun runner(vararg arguments: String, pnpmOnPath: File? = null): GradleRunner =
    GradleRunner.create()
      .withProjectDir(rootDirectory)
      .withPluginClasspath()
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
   */
  private fun environmentWith(pnpmOnPath: File?): Map<String, String> =
    System.getenv().mapValues { (name, value) ->
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
    const val PNPM_VERSION: String = "11.23.0"

    private val PNPM_EXECUTABLE_NAMES = listOf("pnpm", "pnpm.exe", "pnpm.cmd", "pnpm.bat")
  }
}
