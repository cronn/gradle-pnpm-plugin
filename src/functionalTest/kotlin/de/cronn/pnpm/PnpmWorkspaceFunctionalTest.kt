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
  fun `fails when the workspace plugin is applied to a subproject`() {
    val fixture = GradleProjectFixture(projectDirectory)
    fixture.writeWorkspace(packages = listOf("frontend"))
    fixture.write(
      "frontend/build.gradle.kts",
      """
      plugins { id("de.cronn.pnpm-workspace") }
      """,
    )

    val result = fixture.runner("tasks").buildAndFail()

    assertThat(result.output).contains("must be applied to the root project")
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
