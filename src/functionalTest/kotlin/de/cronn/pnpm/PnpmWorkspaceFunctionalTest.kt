package de.cronn.pnpm

import de.cronn.pnpm.fixture.GradleProjectFixture
import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class PnpmWorkspaceFunctionalTest {

  @TempDir lateinit var projectDirectory: File

  @Test
  fun `pnpmInstall runs pnpm install in the workspace root`() {
    val fixture = GradleProjectFixture(projectDirectory)
    fixture.writeWorkspace()

    val result = fixture.runner("pnpmInstall").build()

    assertThat(result.task(":pnpmInstall")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    assertThat(fixture.stub.invocations()).hasSize(1)
    val invocation = fixture.stub.invocations().single()
    assertThat(invocation.arguments).containsExactly("install")
    assertThat(File(invocation.workingDirectory).canonicalFile)
      .isEqualTo(projectDirectory.canonicalFile)
  }

  @Test
  fun `pnpmSetup is skipped when an executable is configured explicitly`() {
    val fixture = GradleProjectFixture(projectDirectory)
    fixture.writeWorkspace()

    val result = fixture.runner("pnpmInstall").build()

    assertThat(result.task(":pnpmSetup")?.outcome).isEqualTo(TaskOutcome.SKIPPED)
  }

  @Test
  fun `pnpmInstall is up to date when the lockfile is unchanged`() {
    val fixture = GradleProjectFixture(projectDirectory)
    fixture.writeWorkspace()

    fixture.runner("pnpmInstall").build()
    val second = fixture.runner("pnpmInstall").build()

    assertThat(second.task(":pnpmInstall")?.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)
  }

  @Test
  fun `pnpmInstall runs again after the lockfile changed`() {
    val fixture = GradleProjectFixture(projectDirectory)
    fixture.writeWorkspace()

    fixture.runner("pnpmInstall").build()
    fixture.write("pnpm-lock.yaml", "lockfileVersion: '9.0'\n# changed")
    val second = fixture.runner("pnpmInstall").build()

    assertThat(second.task(":pnpmInstall")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
  }

  @Test
  fun `pnpmDedupe and pnpmClean always run`() {
    val fixture = GradleProjectFixture(projectDirectory)
    fixture.writeWorkspace()

    fixture.runner("pnpmDedupe", "pnpmClean").build()
    val second = fixture.runner("pnpmDedupe", "pnpmClean").build()

    assertThat(second.task(":pnpmDedupe")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    assertThat(second.task(":pnpmClean")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    assertThat(fixture.stub.invocations().map { it.arguments })
      .containsExactlyInAnyOrder(
        listOf("dedupe"),
        listOf("clean"),
        listOf("dedupe"),
        listOf("clean"),
      )
  }

  @Test
  fun `reuses the configuration cache on a second run`() {
    val fixture = GradleProjectFixture(projectDirectory)
    fixture.writeWorkspace()

    val first = fixture.runner("pnpmInstall").build()
    val second = fixture.runner("pnpmInstall").build()

    assertThat(first.output).contains("Configuration cache entry stored")
    assertThat(second.output).contains("Configuration cache entry reused")
  }

  @Test
  fun `picks up a changed pinned pnpm version even when the configuration cache is reused`() {
    val fixture = GradleProjectFixture(projectDirectory)
    fixture.writeWorkspace(
      rootBuildScript =
        """
        val pinnedVersion = pnpm.version
        tasks.register("printPnpmVersion") {
          // Copied into a local so the doLast action does not capture the build script itself.
          val version = pinnedVersion
          doLast { println("pinned=" + version.get()) }
        }
        """
    )

    val first = fixture.runner("printPnpmVersion").build()
    assertThat(first.output).contains("pinned=${GradleProjectFixture.PNPM_VERSION}")

    fixture.write(
      "package.json",
      """
      {
        "name": "root",
        "private": true,
        "devEngines": { "packageManager": { "name": "pnpm", "version": "11.0.0" } }
      }
      """,
    )
    val second = fixture.runner("printPnpmVersion").build()

    // The file contents are re-read on every build, so a reused entry still yields a fresh value.
    assertThat(second.output).contains("pinned=11.0.0")
  }

  @Test
  fun `fails with an actionable message when pnpm fails`() {
    val fixture = GradleProjectFixture(projectDirectory)
    fixture.writeWorkspace()
    fixture.stub.install(exitCode = 3)

    val result = fixture.runner("pnpmInstall").buildAndFail()

    assertThat(result.task(":pnpmInstall")?.outcome).isEqualTo(TaskOutcome.FAILED)
    assertThat(result.output).contains("finished with non-zero exit value 3")
  }

  @Test
  fun `discovers a workspace root below the gradle root project`() {
    val fixture = GradleProjectFixture(projectDirectory)
    fixture.writeNestedWorkspace()

    val result = fixture.runner(":frontend:app:prettierCheck").build()

    assertThat(result.task(":frontend:pnpmInstall")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    assertThat(result.task(":frontend:app:prettierCheck")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    // The gradle root project holds no pnpm files, so it takes no part in the pnpm build.
    assertThat(result.task(":pnpmInstall")).isNull()

    val invocations = fixture.stub.invocations()
    assertThat(invocations.first().arguments).containsExactly("install")
    assertThat(invocations.first().workingDirectory)
      .isEqualTo(fixture.directory("frontend").canonicalPath)
    assertThat(invocations.last().arguments)
      .containsExactly(
        "exec",
        "prettier",
        "eslint.config.ts",
        "package.json",
        "prettier.config.ts",
        "tsconfig.json",
        "--check",
      )
    assertThat(invocations.last().workingDirectory)
      .isEqualTo(fixture.directory("frontend/app").canonicalPath)
  }

  @Test
  fun `a fix task of the workspace root and of a package can run in the same build`() {
    val fixture = GradleProjectFixture(projectDirectory)
    fixture.writeWorkspace(packages = listOf("frontend"))
    fixture.write("frontend/main.ts", "export const main = 1")

    // A fix task declares no output location, so the root project's task does not claim the
    // directory of every package and Gradle reports no implicit dependency between the two tasks.
    val result = fixture.runner(":prettierFix", ":frontend:prettierFix").build()

    assertThat(result.task(":prettierFix")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    assertThat(result.task(":frontend:prettierFix")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

    // A task that rewrites its own sources is never up to date.
    val second = fixture.runner(":prettierFix", ":frontend:prettierFix").build()
    assertThat(second.task(":prettierFix")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    assertThat(second.task(":frontend:prettierFix")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
  }

  @Test
  fun `fails when the workspace root does not apply the plugin`() {
    val fixture = GradleProjectFixture(projectDirectory)
    fixture.writeNestedWorkspace()
    fixture.write("frontend/build.gradle.kts", "// forgot to apply the plugin")

    val result = fixture.runner(":frontend:app:tasks").buildAndFail()

    assertThat(result.output)
      .contains("it is the pnpm workspace root of :frontend:app")
      .contains("""Apply id("de.cronn.gradle-pnpm-plugin") in the build script of :frontend""")
  }

  @Test
  fun `treats a single package without a workspace file as its own workspace root`() {
    val fixture = GradleProjectFixture(projectDirectory)
    fixture.writeWorkspace()
    fixture.directory("pnpm-workspace.yaml").delete()

    val result = fixture.runner("pnpmInstall", "prettierCheck").build()

    assertThat(result.task(":pnpmInstall")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    assertThat(result.task(":prettierCheck")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
  }

  @Test
  fun `reports a missing package json`() {
    val fixture = GradleProjectFixture(projectDirectory)
    fixture.writeWorkspace()
    fixture.directory("package.json").delete()

    val result = fixture.runner("pnpmInstall").buildAndFail()

    assertThat(result.output)
      .contains("Expected a package.json pinning the pnpm version")
      .contains("but the file is missing or empty")
  }
}
