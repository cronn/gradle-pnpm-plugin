package de.cronn.pnpm

import de.cronn.pnpm.fixture.GradleProjectFixture
import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Every functional test fails on a configuration cache problem while storing an entry. This one
 * makes sure the entry can be loaded again, for every task type with options set on the command
 * line, since the options are what gets serialized beyond the conventions.
 */
class ConfigurationCacheFunctionalTest {

  @TempDir lateinit var projectDirectory: File

  @Test
  fun `reuses the configuration cache for every task type`() {
    val fixture =
      GradleProjectFixture(projectDirectory)
        .writeWorkspaceWithPackage(
          packageBuildScript =
            """
          tasks.register<de.cronn.pnpm.task.NodeTask>("generate") {
            entryPoint = layout.projectDirectory.file("scripts/generate.mjs")
          }
          """,
          sourceFiles = listOf("main.ts", "src/login.test.ts", "tests/login.spec.ts"),
          playwright = true,
          vitest = true,
        )
    fixture.write("frontend/scripts/generate.mjs", "console.log(1)")
    val arguments =
      arrayOf(
        ":pnpmInstall",
        ":frontend:compileTypescript",
        "--noEmit",
        "--outDir=dist",
        ":frontend:eslintCheck",
        "--max-warnings=5",
        "--cache",
        ":frontend:prettierCheck",
        "--ignore-unknown",
        "--cache",
        ":frontend:vitestTest",
        "--coverage",
        ":frontend:playwrightTest",
        "--grep=login",
        ":frontend:generate",
      )

    val first = fixture.runner(*arguments).build()
    val second = fixture.runner(*arguments).build()

    assertThat(first.output).contains("Configuration cache entry stored")
    assertThat(second.output).contains("Configuration cache entry reused")
  }
}
