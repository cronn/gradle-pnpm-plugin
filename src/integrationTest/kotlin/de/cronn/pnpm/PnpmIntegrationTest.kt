package de.cronn.pnpm

import de.cronn.pnpm.fixture.IntegrationProjectFixture
import de.cronn.pnpm.fixture.ProjectFiles.pnpmRepository
import de.cronn.pnpm.fixture.ProjectFiles.settingsRepositories
import de.cronn.pnpm.internal.PnpmRepository
import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class PnpmIntegrationTest {

  @TempDir lateinit var projectDirectory: File

  @Test
  fun `installs the workspace with the pnpm it provisions`() {
    val fixture = IntegrationProjectFixture(projectDirectory)

    val result = fixture.runner("pnpmInstall").build()

    assertThat(result.task(":pnpmSetup")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    assertThat(fixture.installedPnpm()).isFile()
    assertThat(result.task(":pnpmInstall")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    assertThat(fixture.directory("node_modules")).isDirectory()
    // The install is frozen, so a lockfile that does not match the packages fails it instead.
    assertThat(fixture.directory("pnpm-lock.yaml"))
      .hasSameTextualContentAs(fixture.templateFile("pnpm-lock.yaml"))
  }

  @Test
  fun `is up to date on a second install`() {
    val fixture = IntegrationProjectFixture(projectDirectory)

    fixture.runner("pnpmInstall").build()
    val second = fixture.runner("pnpmInstall").build()

    assertThat(second.output).contains("Configuration cache entry reused")
    assertThat(second.task(":pnpmSetup")?.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)
    assertThat(second.task(":pnpmInstall")?.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)
  }

  @Test
  fun `runs pnpm dedupe and pnpm clean`() {
    val fixture = IntegrationProjectFixture(projectDirectory)

    val result = fixture.runner("pnpmDedupe", "pnpmClean").build()

    assertThat(result.task(":pnpmDedupe")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    assertThat(result.task(":pnpmClean")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
  }

  @Test
  fun `resolves pnpm from the repository the readme declares in settings`() {
    val fixture = IntegrationProjectFixture(projectDirectory)
    fixture
      .directory("settings.gradle.kts")
      .appendText(
        "\n" +
          settingsRepositories(
            pnpmRepository(PnpmRepository.PNPM_RELEASES_URL),
            failOnProjectRepositories = true,
          ) +
          "\n",
      )

    val result = fixture.runner("pnpmSetup").build()

    assertThat(result.task(":pnpmSetup")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    assertThat(fixture.installedPnpm()).isFile()
  }

  @Test
  fun `runs a node script on the node version of the workspace`() {
    val fixture = IntegrationProjectFixture(projectDirectory)

    val result = fixture.runner(":app:greet").build()

    assertThat(result.task(":app:greet")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    // The version devEngines.runtime pins, which pnpm downloads whatever Node.js is installed.
    assertThat(result.output).contains("Hello, Gradle, from Node.js v24.22.1!")
  }
}
