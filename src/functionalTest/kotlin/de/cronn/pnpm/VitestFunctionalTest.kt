package de.cronn.pnpm

import de.cronn.pnpm.fixture.GradleProjectFixture
import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class VitestFunctionalTest {

  @TempDir lateinit var projectDirectory: File

  @Test
  fun `runs the suite through pnpm exec in the package directory`() {
    val fixture = workspaceWithFrontend()

    val result = fixture.runner(":frontend:vitestTest").build()

    assertThat(result.task(":frontend:vitestTest")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    val test = fixture.stub.lastInvocationOf("vitest")
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
        listOf("exec", "vitest", "run", *fixture.configArguments("frontend/vitest.config.ts")) +
          coverageArgument(fixture),
      )
  }

  @Test
  fun `writes the coverage report below the build directory`() {
    val fixture = workspaceWithFrontend()

    fixture.runner(":frontend:vitestTest").build()

    assertThat(fixture.stub.lastInvocationOf("vitest").arguments)
      .contains(coverageArgument(fixture).single())
  }

  @Test
  fun `writes the coverage report to the directory given on the command line`() {
    val fixture = workspaceWithFrontend()
    // Canonical, because the temp directory can be a short name (Windows) or a symlink (macOS),
    // and the task hands vitest an absolute path exactly as it was given.
    val report = fixture.directory("frontend/coverage").canonicalPath

    fixture.runner(":frontend:vitestTest", "--coverageReportsDirectory=$report").build()

    assertThat(fixture.stub.lastInvocationOf("vitest").arguments)
      .contains("--coverage.reportsDirectory=$report")
  }

  @Test
  fun `collects coverage only when it is asked for`() {
    val fixture = workspaceWithFrontend()

    fixture.runner(":frontend:vitestTest").build()
    assertThat(fixture.stub.lastInvocationOf("vitest").arguments).doesNotContain("--coverage")

    fixture.runner(":frontend:vitestTest", "--coverage").build()
    // Ahead of the reports directory, which Vitest would drop after a bare --coverage.
    assertThat(fixture.stub.lastInvocationOf("vitest").arguments)
      .containsSubsequence("--coverage", coverageArgument(fixture).single())
  }

  @Test
  fun `rewrites the snapshots when it is asked for`() {
    val fixture = workspaceWithFrontend()

    fixture.runner(":frontend:vitestTest", "--update").build()

    assertThat(fixture.stub.lastInvocationOf("vitest").arguments).contains("--update")
  }

  @Test
  fun `passes the config given on the command line`() {
    val fixture = workspaceWithFrontend()
    fixture.write("frontend/vitest.ci.config.ts", "export default {}")

    fixture.runner(":frontend:vitestTest", "--config=vitest.ci.config.ts").build()

    assertThat(fixture.stub.lastInvocationOf("vitest").arguments)
      .containsExactly(
        "exec",
        "vitest",
        "run",
        *fixture.configArguments("frontend/vitest.ci.config.ts"),
        *coverageArgument(fixture).toTypedArray(),
      )
  }

  @Test
  fun `passes the options given on the command line on to vitest`() {
    val fixture = workspaceWithFrontend()

    fixture
      .runner(
        ":frontend:vitestTest",
        "--testNamePattern=login",
        "--dir=src",
        "--outputFile=build/vitest.xml",
        "--passWithNoTests",
        "--bail=1",
      )
      .build()

    assertThat(fixture.stub.lastInvocationOf("vitest").arguments)
      .containsExactly(
        "exec",
        "vitest",
        "run",
        *fixture.configArguments("frontend/vitest.config.ts"),
        *coverageArgument(fixture).toTypedArray(),
        "--testNamePattern=login",
        "--dir=" + fixture.directory("frontend/src").canonicalPath,
        "--outputFile=" + fixture.directory("frontend/build/vitest.xml").canonicalPath,
        "--passWithNoTests",
        "--bail=1",
      )
  }

  @Test
  fun `passes the options of a custom task on to vitest`() {
    val fixture =
      workspaceWithFrontend(
        packageBuildScript =
          """
          tasks.register<de.cronn.pnpm.task.VitestRunTask>("vitestLogin") {
            testNamePattern = "login"
            bail = 1
          }
          """,
      )

    fixture.runner(":frontend:vitestLogin").build()

    assertThat(fixture.stub.lastInvocationOf("vitest").arguments)
      .contains("--testNamePattern=login", "--bail=1")
  }

  @Test
  fun `passes the options of the extension on to vitest unless the task overrides them`() {
    val fixture =
      workspaceWithFrontend(
        packageBuildScript =
          """
          vitest {
            coverage = true
            passWithNoTests = true
          }
          tasks.vitestTest { coverage = false }
          """,
      )

    fixture.runner(":frontend:vitestTest").build()

    assertThat(fixture.stub.lastInvocationOf("vitest").arguments).contains("--passWithNoTests")
    assertThat(fixture.stub.lastInvocationOf("vitest").arguments).doesNotContain("--coverage")
  }

  @Test
  fun `reruns when the test name pattern changes`() {
    val fixture = workspaceWithFrontend()

    fixture.runner(":frontend:vitestTest", "--dir=src").build()
    assertThat(
        fixture
          .runner(":frontend:vitestTest", "--dir=src")
          .build()
          .task(":frontend:vitestTest")
          ?.outcome,
      )
      .isEqualTo(TaskOutcome.UP_TO_DATE)

    assertThat(
        fixture
          .runner(":frontend:vitestTest", "--dir=src", "--testNamePattern=login")
          .build()
          .task(":frontend:vitestTest")
          ?.outcome,
      )
      .isEqualTo(TaskOutcome.SUCCESS)
  }

  @Test
  fun `appends the extra arguments last`() {
    val fixture =
      workspaceWithFrontend(packageBuildScript = """vitest { extraArguments("--reporter=json") }""")

    fixture.runner(":frontend:vitestTest").build()

    assertThat(fixture.stub.lastInvocationOf("vitest").arguments).endsWith("--reporter=json")
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
  fun `reruns when the lockfile changes`() {
    val fixture = workspaceWithFrontend()

    fixture.runner(":frontend:vitestTest").build()
    assertThat(fixture.runner(":frontend:vitestTest").build().task(":frontend:vitestTest")?.outcome)
      .isEqualTo(TaskOutcome.UP_TO_DATE)

    fixture.write("pnpm-lock.yaml", "lockfileVersion: '9.0'\n# changed")

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
    assertThat(fixture.stub.lastInvocationOf("vitest").arguments)
      .startsWith("exec", "vitest", "run", *fixture.configArguments("frontend/vite.config.ts"))
  }

  /** A workspace whose `frontend` package is configured for Vitest and holds one test file. */
  private fun workspaceWithFrontend(packageBuildScript: String = ""): GradleProjectFixture =
    GradleProjectFixture(projectDirectory)
      .writeWorkspaceWithPackage(
        packageBuildScript = packageBuildScript,
        sourceFiles = listOf("src/main.ts", "src/login.test.ts"),
        vitest = true,
      )

  /** The invocation that ran the suite, as opposed to the one that installed the workspace. */
  /** The report directory argument the task always passes, whatever the options say. */
  private fun coverageArgument(fixture: GradleProjectFixture) =
    listOf(
      "--coverage.reportsDirectory=" +
        fixture.directory("frontend/build/reports/vitest").canonicalFile.absolutePath,
    )
}
