package de.cronn.pnpm.fixture

import java.io.File
import org.gradle.testkit.runner.GradleRunner

/** Builds a pnpm workspace on disk and runs Gradle against it. */
class GradleProjectFixture(val rootDirectory: File) {

  val stub: PnpmStub = PnpmStub(File(rootDirectory, "pnpm-stub"))

  private lateinit var stubExecutable: File

  /**
   * Writes a workspace whose root project applies `de.cronn.pnpm-workspace` and whose [packages]
   * each apply `de.cronn.pnpm-package`.
   */
  fun writeWorkspace(
    packages: List<String> = emptyList(),
    rootBuildScript: String = "",
    packageBuildScript: String = "",
    pnpmVersion: String = PNPM_VERSION,
    /** Body of the `pnpm { }` block; defaults to pointing the build at the recording stub. */
    pnpmConfiguration: String? = null,
  ) {
    stubExecutable = stub.install()

    write(
      "settings.gradle.kts",
      """
      rootProject.name = "workspace"
      ${packages.joinToString("\n") { "include(\"$it\")" }}
      """,
    )
    write(
      "package.json",
      """
      {
        "name": "root",
        "private": true,
        "devEngines": { "packageManager": { "name": "pnpm", "version": "$pnpmVersion" } }
      }
      """,
    )
    write("pnpm-lock.yaml", "lockfileVersion: '9.0'")
    write("pnpm-workspace.yaml", "packages:\n${packages.joinToString("\n") { "  - $it" }}")
    write(
      "build.gradle.kts",
      """
      plugins { id("de.cronn.pnpm-workspace") }

      pnpm {
        ${pnpmConfiguration ?: "executable = ${quoted(stubExecutable)}"}
      }

      $rootBuildScript
      """,
    )

    packages.forEach { name ->
      write(
        "$name/build.gradle.kts",
        """
        plugins { id("de.cronn.pnpm-package") }

        $packageBuildScript
        """,
      )
      write("$name/package.json", """{ "name": "$name" }""")
    }
  }

  fun write(path: String, content: String) {
    val file = File(rootDirectory, path)
    file.parentFile.mkdirs()
    file.writeText(content.trimIndent().trim() + "\n")
  }

  fun runner(vararg arguments: String): GradleRunner =
    GradleRunner.create()
      .withProjectDir(rootDirectory)
      .withPluginClasspath()
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

  /** Kotlin string literal for [file], safe on Windows where paths contain backslashes. */
  private fun quoted(file: File): String = "\"${file.invariantSeparatorsPath}\""

  companion object {
    const val PNPM_VERSION: String = "11.23.0"
  }
}
