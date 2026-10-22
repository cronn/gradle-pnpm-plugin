package de.cronn.pnpm

import de.cronn.pnpm.fixture.GradleProjectFixture
import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class EslintFunctionalTest {

  @TempDir lateinit var projectDirectory: File

  @Test
  fun `passes the config given on the command line`() {
    val fixture = workspaceWithFrontend()
    fixture.write("frontend/eslint.strict.config.ts", "export default []")

    fixture.runner(":frontend:eslintCheck", "--config=eslint.strict.config.ts").build()

    assertThat(eslint(fixture).arguments)
      .containsExactly(
        "exec",
        "eslint",
        *fixture.configArguments("frontend/eslint.strict.config.ts"),
        "--max-warnings",
        "0",
        *ESLINT_PATTERNS,
      )
  }

  @Test
  fun `passes the options given on the command line on to eslint`() {
    val fixture = workspaceWithFrontend()

    fixture
      .runner(
        ":frontend:eslintCheck",
        "--max-warnings=5",
        "--cache",
        "--cache-location=build/eslint-cache",
        "--suppress-rule=no-console",
        "--suppress-rule=no-undef",
        "--suppressions-location=eslint-suppressions.json",
        "--pass-on-no-patterns",
      )
      .build()

    assertThat(eslint(fixture).arguments)
      .containsExactly(
        "exec",
        "eslint",
        *fixture.configArguments("frontend/eslint.config.ts"),
        "--max-warnings",
        "5",
        "--cache",
        "--cache-location",
        fixture.directory("frontend/build/eslint-cache").canonicalPath,
        "--suppress-rule",
        "no-console",
        "--suppress-rule",
        "no-undef",
        "--suppressions-location",
        fixture.directory("frontend/eslint-suppressions.json").canonicalPath,
        "--pass-on-no-patterns",
        *ESLINT_PATTERNS,
      )
  }

  @Test
  fun `passes the options of a custom task on to eslint`() {
    val fixture =
      workspaceWithFrontend(
        packageBuildScript =
          """
          tasks.register<de.cronn.pnpm.task.EslintTask>("eslintSuppress") {
            suppressAll = true
            pruneSuppressions = true
            noErrorOnUnmatchedPattern = false
          }
          """,
      )

    fixture.runner(":frontend:eslintSuppress").build()

    assertThat(eslint(fixture).arguments)
      .containsExactly(
        "exec",
        "eslint",
        *fixture.configArguments("frontend/eslint.config.ts"),
        "--max-warnings",
        "0",
        "--suppress-all",
        "--prune-suppressions",
        *SOURCE_PATTERNS,
      )
  }

  @Test
  fun `passes the options of the extension on to eslint unless the task overrides them`() {
    val fixture =
      workspaceWithFrontend(
        packageBuildScript =
          """
          eslint {
            maxWarnings = 10
            noErrorOnUnmatchedPattern = false
            cache = true
            cacheLocation = layout.buildDirectory.file("eslint-cache")
          }
          tasks.eslintCheck { cache = false }
          """,
      )

    fixture.runner(":frontend:eslintCheck").build()

    assertThat(eslint(fixture).arguments)
      .containsExactly(
        "exec",
        "eslint",
        *fixture.configArguments("frontend/eslint.config.ts"),
        "--max-warnings",
        "10",
        "--cache-location",
        fixture.directory("frontend/build/eslint-cache").canonicalPath,
        *SOURCE_PATTERNS,
      )
  }

  @Test
  fun `reuses the configuration cache on a second run with options`() {
    val fixture = workspaceWithFrontend()

    fixture.runner(":frontend:eslintCheck", "--max-warnings=5", "--cache").build()
    val second = fixture.runner(":frontend:eslintCheck", "--max-warnings=5", "--cache").build()

    assertThat(second.output).contains("Configuration cache entry reused")
  }

  /** A workspace whose `frontend` package is configured for ESLint and holds one source. */
  private fun workspaceWithFrontend(packageBuildScript: String = ""): GradleProjectFixture {
    val fixture = GradleProjectFixture(projectDirectory)
    fixture.writeWorkspace(packages = listOf("frontend"), packageBuildScript = packageBuildScript)
    fixture.write("frontend/main.ts", "export const main = 1")
    return fixture
  }

  /** The invocation that ran ESLint, as opposed to the ones that installed and compiled. */
  private fun eslint(fixture: GradleProjectFixture) =
    fixture.stub.invocations().last { it.arguments.contains("eslint") }

  private companion object {
    /** The default patterns of ESLint, the way they reach its command line. */
    val SOURCE_PATTERNS: Array<String> = arrayOf("*.ts", "src/**/*.ts", "src/**/*.tsx")
    val ESLINT_PATTERNS: Array<String> =
      arrayOf("--no-error-on-unmatched-pattern", *SOURCE_PATTERNS)
  }
}
