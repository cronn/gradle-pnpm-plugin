package de.cronn.pnpm

import de.cronn.pnpm.fixture.GradleProjectFixture
import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class PnpmVitestFunctionalTest {

  @TempDir lateinit var projectDirectory: File

  @Test
  fun `runs the suite through pnpm exec in the package directory`() {
    val fixture = workspaceWithFrontend()

    val result = fixture.runner(":frontend:vitestTest").build()

    assertThat(result.task(":frontend:vitestTest")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    val test = vitestTest(fixture)
    // `run`, because a bare `vitest` would start the watch mode and never hand the build back.
    assertThat(test.arguments).startsWith("exec", "vitest", "run")
    assertThat(File(test.workingDirectory).canonicalFile)
      .isEqualTo(fixture.directory("frontend").canonicalFile)
  }

  @Test
  fun `installs the workspace before running the suite`() {
    val fixture = workspaceWithFrontend()

    fixture.runner(":frontend:vitestTest").build()

    assertThat(fixture.stub.invocations().map { it.arguments })
      .containsSequence(
        listOf("install"),
        listOf("exec", "vitest", "run") + coverageArgument(fixture),
      )
  }

  @Test
  fun `writes the coverage report below the build directory`() {
    val fixture = workspaceWithFrontend()

    fixture.runner(":frontend:vitestTest").build()

    assertThat(vitestTest(fixture).arguments).contains(coverageArgument(fixture).single())
  }

  @Test
  fun `collects coverage only when it is asked for`() {
    val fixture = workspaceWithFrontend()

    fixture.runner(":frontend:vitestTest").build()
    assertThat(vitestTest(fixture).arguments).doesNotContain("--coverage")

    fixture.runner(":frontend:vitestTest", "--coverage").build()
    assertThat(vitestTest(fixture).arguments).contains("--coverage")
  }

  @Test
  fun `rewrites the snapshots when it is asked for`() {
    val fixture = workspaceWithFrontend()

    fixture.runner(":frontend:vitestTest", "--update").build()

    assertThat(vitestTest(fixture).arguments).contains("--update")
  }

  @Test
  fun `test runs the suite`() {
    val fixture = workspaceWithFrontend()

    val result = fixture.runner(":frontend:test").build()

    assertThat(result.task(":frontend:vitestTest")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
  }

  @Test
  fun `check runs the suite`() {
    val fixture = workspaceWithFrontend()

    val result = fixture.runner(":frontend:check").build()

    // A unit suite is fast and reaches nothing the build does not start, so it belongs in `build`.
    assertThat(result.task(":frontend:vitestTest")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
  }

  @Test
  fun `appends the extra arguments last`() {
    val fixture =
      workspaceWithFrontend(packageBuildScript = """vitest { extraArguments("--reporter=json") }""")

    fixture.runner(":frontend:vitestTest").build()

    assertThat(vitestTest(fixture).arguments).endsWith("--reporter=json")
  }

  @Test
  fun `is up to date when the tests are unchanged`() {
    val fixture = workspaceWithFrontend()

    assertThat(fixture.runner(":frontend:vitestTest").build().task(":frontend:vitestTest")?.outcome)
      .isEqualTo(TaskOutcome.SUCCESS)
    assertThat(fixture.runner(":frontend:vitestTest").build().task(":frontend:vitestTest")?.outcome)
      .isEqualTo(TaskOutcome.UP_TO_DATE)

    fixture.write("frontend/src/login.test.ts", "export const login = 2")

    assertThat(fixture.runner(":frontend:vitestTest").build().task(":frontend:vitestTest")?.outcome)
      .isEqualTo(TaskOutcome.SUCCESS)
  }

  @Test
  fun `reruns when the vitest config changes`() {
    val fixture = workspaceWithFrontend()

    fixture.runner(":frontend:vitestTest").build()
    assertThat(fixture.runner(":frontend:vitestTest").build().task(":frontend:vitestTest")?.outcome)
      .isEqualTo(TaskOutcome.UP_TO_DATE)

    fixture.write("frontend/vitest.config.ts", "export default { test: { globals: true } }")

    assertThat(fixture.runner(":frontend:vitestTest").build().task(":frontend:vitestTest")?.outcome)
      .isEqualTo(TaskOutcome.SUCCESS)
  }

  @Test
  fun `runs the suite on every invocation when alwaysRerun is switched on`() {
    val fixture = workspaceWithFrontend(packageBuildScript = """vitest { alwaysRerun = true }""")

    fixture.runner(":frontend:vitestTest").build()

    assertThat(fixture.runner(":frontend:vitestTest").build().task(":frontend:vitestTest")?.outcome)
      .isEqualTo(TaskOutcome.SUCCESS)
  }

  @Test
  fun `runs the suite for a package configured through a vite config alone`() {
    val fixture = GradleProjectFixture(projectDirectory)
    fixture.writeWorkspace(packages = listOf("frontend"))
    // Vitest reads the Vite config when there is no dedicated one of its own.
    fixture.write("frontend/vite.config.ts", "export default {}")
    fixture.write("frontend/src/login.test.ts", "export const login = 1")

    val result = fixture.runner(":frontend:vitestTest").build()

    assertThat(result.task(":frontend:vitestTest")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    assertThat(vitestTest(fixture).arguments).startsWith("exec", "vitest", "run")
  }

  @Test
  fun `registers no vitest task for a package without a vitest or vite config`() {
    val fixture = GradleProjectFixture(projectDirectory)
    fixture.writeWorkspace(packages = listOf("frontend"))

    val result = fixture.runner(":frontend:vitestTest").buildAndFail()

    assertThat(result.output).contains("Cannot locate tasks that match ':frontend:vitestTest'")
    assertThat(fixture.runner(":frontend:tasks").build().output).doesNotContain("vitestTest - ")
  }

  @Test
  fun `reuses the configuration cache on a second run`() {
    val fixture = workspaceWithFrontend()

    fixture.runner(":frontend:vitestTest").build()
    val second = fixture.runner(":frontend:vitestTest").build()

    assertThat(second.output).contains("Configuration cache entry")
  }

  /** A workspace whose `frontend` package is configured for Vitest and holds one test file. */
  private fun workspaceWithFrontend(packageBuildScript: String = ""): GradleProjectFixture {
    val fixture = GradleProjectFixture(projectDirectory)
    fixture.writeWorkspace(
      packages = listOf("frontend"),
      packageBuildScript = packageBuildScript,
      vitest = true,
    )
    fixture.write("frontend/src/main.ts", "export const main = 1")
    fixture.write("frontend/src/login.test.ts", "export const login = 1")
    return fixture
  }

  /** The invocation that ran the suite, as opposed to the one that installed the workspace. */
  private fun vitestTest(fixture: GradleProjectFixture) =
    fixture.stub.invocations().last { it.arguments.contains("vitest") }

  /** The report directory argument the task always passes, whatever the options say. */
  private fun coverageArgument(fixture: GradleProjectFixture) =
    listOf(
      "--coverage.reportsDirectory=" +
        fixture.directory("frontend/build/reports/vitest").canonicalFile.absolutePath
    )
}
