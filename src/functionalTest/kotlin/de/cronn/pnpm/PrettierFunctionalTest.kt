package de.cronn.pnpm

import de.cronn.pnpm.fixture.GradleProjectFixture
import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class PrettierFunctionalTest {

  @TempDir lateinit var projectDirectory: File

  @Test
  fun `passes the config given on the command line`() {
    val fixture = workspaceWithFrontend()
    fixture.write("frontend/prettier.strict.config.ts", "export default {}")

    fixture.runner(":frontend:prettierCheck", "--config=prettier.strict.config.ts").build()

    assertThat(prettier(fixture).arguments)
      .containsExactly(
        "exec",
        "prettier",
        *fixture.configArguments("frontend/prettier.strict.config.ts"),
        "--check",
        *PRETTIER_PATTERNS,
      )
  }

  @Test
  fun `passes the options given on the command line on to prettier`() {
    val fixture = workspaceWithFrontend()

    fixture
      .runner(
        ":frontend:prettierCheck",
        "--ignore-unknown",
        "--cache",
        "--cache-location=build/prettier-cache",
      )
      .build()

    assertThat(prettier(fixture).arguments)
      .containsExactly(
        "exec",
        "prettier",
        *fixture.configArguments("frontend/prettier.config.ts"),
        "--check",
        "--ignore-unknown",
        "--no-error-on-unmatched-pattern",
        "--cache",
        "--cache-location",
        fixture.directory("frontend/build/prettier-cache").canonicalPath,
        *SOURCE_PATTERNS,
      )
  }

  @Test
  fun `passes the options of a custom task on to prettier`() {
    val fixture =
      workspaceWithFrontend(
        packageBuildScript =
          """
          tasks.register<de.cronn.pnpm.task.PrettierTask>("prettierList") {
            listDifferent = true
            noErrorOnUnmatchedPattern = false
          }
          """,
      )

    fixture.runner(":frontend:prettierList").build()

    assertThat(prettier(fixture).arguments)
      .containsExactly(
        "exec",
        "prettier",
        *fixture.configArguments("frontend/prettier.config.ts"),
        "--list-different",
        *SOURCE_PATTERNS,
      )
  }

  @Test
  fun `reuses the configuration cache on a second run with options`() {
    val fixture = workspaceWithFrontend()

    fixture.runner(":frontend:prettierCheck", "--ignore-unknown", "--cache").build()
    val second = fixture.runner(":frontend:prettierCheck", "--ignore-unknown", "--cache").build()

    assertThat(second.output).contains("Configuration cache entry reused")
  }

  /** A workspace whose `frontend` package is configured for Prettier and holds one source. */
  private fun workspaceWithFrontend(packageBuildScript: String = ""): GradleProjectFixture {
    val fixture = GradleProjectFixture(projectDirectory)
    fixture.writeWorkspace(packages = listOf("frontend"), packageBuildScript = packageBuildScript)
    fixture.write("frontend/main.ts", "export const main = 1")
    return fixture
  }

  /** The invocation that ran Prettier, as opposed to the one that installed the workspace. */
  private fun prettier(fixture: GradleProjectFixture) =
    fixture.stub.invocations().last { it.arguments.contains("prettier") }

  private companion object {
    /** The default patterns of Prettier, the way they reach its command line. */
    val SOURCE_PATTERNS: Array<String> =
      arrayOf("*.ts", "src/**/*.ts", "src/**/*.tsx", "*.json", "*.md")
    val PRETTIER_PATTERNS: Array<String> =
      arrayOf("--no-error-on-unmatched-pattern", *SOURCE_PATTERNS)
  }
}
