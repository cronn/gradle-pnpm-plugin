package de.cronn.pnpm

import de.cronn.pnpm.fixture.GradleProjectFixture
import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class PnpmPlaywrightFunctionalTest {

  @TempDir lateinit var projectDirectory: File

  @Test
  fun `runs the suite through pnpm exec in the package directory`() {
    val fixture = workspaceWithE2e()

    val result = fixture.runner(":e2e:playwrightTest").build()

    assertThat(result.task(":e2e:playwrightTest")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    val test = playwrightTest(fixture)
    assertThat(test.arguments).startsWith("exec", "playwright", "test")
    assertThat(File(test.workingDirectory).canonicalFile)
      .isEqualTo(fixture.directory("e2e").canonicalFile)
  }

  @Test
  fun `installs the workspace and the browsers before running the suite`() {
    val fixture = workspaceWithE2e()

    fixture.runner(":e2e:playwrightTest").build()

    // pnpm install first, then the browsers, then the suite: each needs the one before it.
    assertThat(fixture.stub.invocations().map { it.arguments })
      .containsSequence(listOf("install"), listOf("exec", "playwright", "install"))
    assertThat(fixture.stub.invocations().last().arguments).contains("test")
  }

  @Test
  fun `writes its artifacts and its report below the build directory`() {
    val fixture = workspaceWithE2e()

    fixture.runner(":e2e:playwrightTest").build()

    val test = playwrightTest(fixture)
    val output = fixture.directory("e2e/build/playwright/test-results").canonicalFile
    assertThat(test.arguments).contains("--output=$output")
  }

  @Test
  fun `test does not run the suite`() {
    val fixture = workspaceWithE2e()

    val result = fixture.runner(":e2e:test").build()

    // An end-to-end suite is slow and usually needs a server the build does not start, so it is
    // deliberately no part of `test`.
    assertThat(result.task(":e2e:playwrightTest")).isNull()
  }

  @Test
  fun `check does not run the suite`() {
    val fixture = workspaceWithE2e()

    val result = fixture.runner(":e2e:check").build()

    // An end-to-end suite is slow and usually needs a server the build does not start, so it is
    // deliberately no part of check -- which it would otherwise reach through `test`.
    assertThat(result.task(":e2e:playwrightTest")).isNull()
    // `test` itself has no actions, so Gradle reports it as UP-TO-DATE rather than SUCCESS; its
    // presence in the graph is enough to show that check now runs it.
    assertThat(result.task(":e2e:test")).isNotNull()
    assertThat(result.task(":e2e:eslintCheck")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
  }

  @Test
  fun `passes the command line options on to playwright`() {
    val fixture = workspaceWithE2e()

    fixture
      .runner(
        ":e2e:playwrightTest",
        "--grep=login",
        "--update-snapshots",
        "--fail-fast",
        "--last-failed",
      )
      .build()

    assertThat(playwrightTest(fixture).arguments)
      .containsSubsequence("--update-snapshots", "--last-failed", "-x", "--grep=login")
  }

  @Test
  fun `passes the filters on as the operands of the suite`() {
    val fixture = workspaceWithE2e()

    fixture
      .runner(":e2e:playwrightTest", "--filter=tests/login.spec.ts:42", "--filter=tests/other")
      .build()

    // Playwright takes its filters as operands, so they follow the test command directly.
    assertThat(playwrightTest(fixture).arguments)
      .containsSequence("test", "tests/login.spec.ts:42", "tests/other")
  }

  @Test
  fun `stops at the first failure when the tests are repeated`() {
    val fixture = workspaceWithE2e()

    fixture.runner(":e2e:playwrightTest", "--repeat-each=3").build()

    // Repeating a test is how a flaky one is hunted down; the run is over as soon as it fails once.
    assertThat(playwrightTest(fixture).arguments).contains("-x", "--repeat-each=3")
  }

  @Test
  fun `records a trace in the requested mode`() {
    val fixture = workspaceWithE2e()

    fixture.runner(":e2e:playwrightTest", "--trace=retain-on-failure").build()

    assertThat(playwrightTest(fixture).arguments).contains("--trace=retain-on-failure")
  }

  @Test
  fun `fails on a trace mode Playwright does not know`() {
    val fixture = workspaceWithE2e()

    val result = fixture.runner(":e2e:playwrightTest", "--trace=always").buildAndFail()

    // Playwright would reject it only once the browsers are up, which costs a whole run.
    assertThat(result.output).contains("The trace mode \"always\"", "retain-on-first-failure")
  }

  @Test
  fun `appends the extra arguments last`() {
    val fixture =
      workspaceWithE2e(
        packageBuildScript =
          """
          playwright { extraArguments("--reporter=json") }
          """
      )

    fixture.runner(":e2e:playwrightTest", "--grep=login").build()

    // extraArguments come last, so a project-wide argument always has the final say.
    assertThat(playwrightTest(fixture).arguments).endsWith("--grep=login", "--reporter=json")
  }

  @Test
  fun `runs the suite again even when nothing changed`() {
    val fixture = workspaceWithE2e()

    fixture.runner(":e2e:playwrightTest").build()
    val second = fixture.runner(":e2e:playwrightTest").build()

    // A browser suite reaches a backend no Gradle input describes, so unchanged inputs are no
    // reason to believe the last result still holds.
    assertThat(second.task(":e2e:playwrightTest")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
  }

  @Test
  fun `reruns an interactive run whatever the inputs say`() {
    val fixture = workspaceWithE2e()

    fixture.runner(":e2e:playwrightTest").build()
    val interactive = fixture.runner(":e2e:playwrightTest", "--ui").build()

    // There is a person waiting for the window, so being up to date is no reason not to open it.
    assertThat(interactive.task(":e2e:playwrightTest")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    assertThat(playwrightTest(fixture).arguments).contains("--ui")
  }

  @Test
  fun `is up to date when alwaysRerun is switched off and the tests are unchanged`() {
    val fixture =
      workspaceWithE2e(
        packageBuildScript =
          """
          playwright { alwaysRerun = false }
          """
      )

    fixture.runner(":e2e:playwrightTest").build()
    val second = fixture.runner(":e2e:playwrightTest").build()
    assertThat(second.task(":e2e:playwrightTest")?.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)

    fixture.write("e2e/tests/login.spec.ts", "export const changed = true")
    val third = fixture.runner(":e2e:playwrightTest").build()
    assertThat(third.task(":e2e:playwrightTest")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
  }

  @Test
  fun `reruns when alwaysRerun is switched off and the playwright config changes`() {
    val fixture =
      workspaceWithE2e(
        packageBuildScript =
          """
          playwright { alwaysRerun = false }
          """
      )

    fixture.runner(":e2e:playwrightTest").build()
    val second = fixture.runner(":e2e:playwrightTest").build()
    assertThat(second.task(":e2e:playwrightTest")?.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)

    fixture.write("e2e/playwright.config.ts", "export default { testDir: \"tests\", retries: 1 }")
    val third = fixture.runner(":e2e:playwrightTest").build()
    assertThat(third.task(":e2e:playwrightTest")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
  }

  @Test
  fun `installs the configured browsers with their system dependencies`() {
    val fixture =
      workspaceWithE2e(
        packageBuildScript =
          """
          playwright {
            browsers = listOf("chromium")
            installSystemDependencies = true
          }
          """
      )

    fixture.runner(":e2e:playwrightInstall").build()

    assertThat(fixture.stub.invocations().map { it.arguments })
      .contains(listOf("exec", "playwright", "install", "--with-deps", "chromium"))
  }

  @Test
  fun `skips the browser install when it is switched off`() {
    val fixture =
      workspaceWithE2e(
        packageBuildScript =
          """
          playwright { installBrowsers = false }
          """
      )

    val result = fixture.runner(":e2e:playwrightTest").build()

    assertThat(result.task(":e2e:playwrightInstall")).isNull()
  }

  @Test
  fun `registers no playwright task for a package without a playwright config`() {
    val fixture = GradleProjectFixture(projectDirectory)
    fixture.writeWorkspace(packages = listOf("e2e"))
    fixture.write("e2e/tests/login.spec.ts", "export const login = 1")

    // A project with no playwright.config.* does not carry the tasks at all, so asking for one is
    // an error rather than a build that quietly does nothing.
    val result = fixture.runner(":e2e:playwrightTest").buildAndFail()

    assertThat(result.output).contains("Cannot locate tasks that match ':e2e:playwrightTest'")

    val listed = fixture.runner(":e2e:tasks").build().output
    assertThat(listed).doesNotContain("playwrightTest - ", "playwrightInstall - ")
    assertThat(fixture.stub.invocations().map { it.arguments }).noneSatisfy { arguments ->
      assertThat(arguments).contains("playwright")
    }
  }

  @Test
  fun `reuses the configuration cache on a second run`() {
    val fixture = workspaceWithE2e()

    fixture.runner(":e2e:playwrightTest").build()
    val second = fixture.runner(":e2e:playwrightTest", "--grep=login").build()

    assertThat(second.output).contains("Configuration cache entry")
  }

  /** A workspace whose `e2e` package is configured for Playwright and holds one test file. */
  private fun workspaceWithE2e(packageBuildScript: String = ""): GradleProjectFixture {
    val fixture = GradleProjectFixture(projectDirectory)
    fixture.writeWorkspace(
      packages = listOf("e2e"),
      packageBuildScript = packageBuildScript,
      playwright = true,
    )
    fixture.write("e2e/main.ts", "export const main = 1")
    fixture.write("e2e/tests/login.spec.ts", "export const login = 1")
    return fixture
  }

  /** The invocation that ran the suite, as opposed to the one that installed the browsers. */
  private fun playwrightTest(fixture: GradleProjectFixture) =
    fixture.stub.invocations().last { it.arguments.contains("test") }
}
