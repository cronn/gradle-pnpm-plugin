package de.cronn.pnpm

import de.cronn.pnpm.fixture.GradleProjectFixture
import de.cronn.pnpm.fixture.GradleProjectFixture.Companion.PRETTIER_ARGUMENTS
import de.cronn.pnpm.fixture.GradleProjectFixture.Companion.PRETTIER_INCLUDES
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

    assertThat(fixture.stub.lastInvocationOf("prettier").arguments)
      .containsExactly(
        "exec",
        "prettier",
        *fixture.configArguments("frontend/prettier.strict.config.ts"),
        "--check",
        *PRETTIER_ARGUMENTS,
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

    assertThat(fixture.stub.lastInvocationOf("prettier").arguments)
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
        *PRETTIER_INCLUDES,
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

    assertThat(fixture.stub.lastInvocationOf("prettier").arguments)
      .containsExactly(
        "exec",
        "prettier",
        *fixture.configArguments("frontend/prettier.config.ts"),
        "--list-different",
        *PRETTIER_INCLUDES,
      )
  }

  @Test
  fun `passes the options of the extension on to prettier unless the task overrides them`() {
    val fixture =
      workspaceWithFrontend(
        packageBuildScript =
          """
          prettier {
            ignoreUnknown = true
            noErrorOnUnmatchedPattern = false
            cache = true
            cacheLocation = layout.buildDirectory.file("prettier-cache")
          }
          tasks.prettierCheck { cache = false }
          """,
      )

    fixture.runner(":frontend:prettierCheck").build()

    assertThat(fixture.stub.lastInvocationOf("prettier").arguments)
      .containsExactly(
        "exec",
        "prettier",
        *fixture.configArguments("frontend/prettier.config.ts"),
        "--check",
        "--ignore-unknown",
        "--cache-location",
        fixture.directory("frontend/build/prettier-cache").canonicalPath,
        *PRETTIER_INCLUDES,
      )
  }

  /** A workspace whose `frontend` package is configured for Prettier and holds one source. */
  private fun workspaceWithFrontend(packageBuildScript: String = ""): GradleProjectFixture =
    GradleProjectFixture(projectDirectory)
      .writeWorkspaceWithPackage(
        packageBuildScript = packageBuildScript,
      )
}
