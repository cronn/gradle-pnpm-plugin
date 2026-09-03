package de.cronn.pnpm

import de.cronn.pnpm.fixture.GradleProjectFixture
import de.cronn.pnpm.fixture.PnpmArchiveFixture
import de.cronn.pnpm.fixture.PnpmStub
import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class PnpmSetupFunctionalTest {

  @TempDir lateinit var projectDirectory: File

  @TempDir lateinit var releaseDirectory: File

  @Test
  fun `downloads and extracts the pinned pnpm version`() {
    val fixture = workspaceWithLocalRelease()

    val result = fixture.runner("pnpmSetup").build()

    assertThat(result.task(":pnpmSetup")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    val installed =
      fixture.directory(".gradle/pnpm/${GradleProjectFixture.PNPM_VERSION}/$executableName")
    assertThat(installed).isFile()
    if (!PnpmStub.isWindows) {
      assertThat(installed.canExecute()).isTrue()
    }
  }

  @Test
  fun `does not download again on a second run`() {
    val fixture = workspaceWithLocalRelease()

    fixture.runner("pnpmSetup").build()
    val second = fixture.runner("pnpmSetup").build()

    assertThat(second.task(":pnpmSetup")?.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)
  }

  @Test
  fun `leaves no downloaded archive behind`() {
    val fixture = workspaceWithLocalRelease()

    fixture.runner("pnpmSetup").build()

    val installDirectory = fixture.directory(".gradle/pnpm/${GradleProjectFixture.PNPM_VERSION}")
    assertThat(installDirectory.walkTopDown().filter { it.isFile }.map { it.name }.toList())
      .noneMatch { it.endsWith(".tar.gz") || it.endsWith(".zip") || it.endsWith(".part") }
  }

  @Test
  fun `is skipped when a matching pnpm is configured explicitly`() {
    val fixture = GradleProjectFixture(projectDirectory)
    fixture.writeWorkspace()

    val result = fixture.runner("pnpmSetup").build()

    assertThat(result.task(":pnpmSetup")?.outcome).isEqualTo(TaskOutcome.SKIPPED)
  }

  @Test
  fun `fails when the checksum does not match`() {
    val fixture =
      workspaceWithLocalRelease(
        extraConfiguration = """archiveSha256 = "${"0".repeat(SHA256_LENGTH)}""""
      )

    val result = fixture.runner("pnpmSetup").buildAndFail()

    assertThat(result.output).contains("Checksum mismatch for")
    assertThat(result.output).contains("expected SHA-256 ${"0".repeat(SHA256_LENGTH)}")
  }

  @Test
  fun `reports what the archive contained when pnpm is missing from it`() {
    val baseUrl =
      PnpmArchiveFixture.writeRelease(
        releaseDirectory,
        GradleProjectFixture.PNPM_VERSION,
        entries = mapOf("README.md" to "no pnpm here\n", "bin/other" to "nope\n"),
      )
    val fixture = workspaceWithLocalRelease(baseUrl = baseUrl)

    val result = fixture.runner("pnpmSetup").buildAndFail()

    assertThat(result.output)
      .contains("Expected a pnpm executable named '$executableName'")
      .contains("the archive contained: README.md")
  }

  @Test
  fun `fails with a readable message when the archive cannot be downloaded`() {
    val fixture =
      workspaceWithLocalRelease(baseUrl = File(releaseDirectory, "missing").toURI().toString())

    val result = fixture.runner("pnpmSetup").buildAndFail()

    assertThat(result.output).contains("Failed to download pnpm from")
  }

  @Test
  fun `uses the downloaded pnpm for the workspace tasks`() {
    val fixture = workspaceWithLocalRelease()

    if (PnpmStub.isWindows) {
      // A `pnpm.exe` inside the archive cannot be faked by a script, so pnpmInstall only gets as
      // far as starting it. That it tries to start the downloaded executable is the point here.
      val result = fixture.runner("pnpmInstall").buildAndFail()

      assertThat(result.task(":pnpmSetup")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
      assertThat(result.output)
        .contains("A problem occurred starting process")
        .contains(downloadedExecutablePath)
    } else {
      val result = fixture.runner("pnpmInstall").build()

      assertThat(result.task(":pnpmSetup")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
      // The extracted stub echoes its arguments, which proves the managed pnpm was invoked.
      assertThat(result.output).contains("installed pnpm called with: install")
    }
  }

  /** Tail of the path of the pnpm the plugin downloads, independent of the temporary directory. */
  private val downloadedExecutablePath: String
    get() =
      listOf(".gradle", "pnpm", GradleProjectFixture.PNPM_VERSION, executableName)
        .joinToString(File.separator)

  private val executableName: String
    get() = if (PnpmStub.isWindows) "pnpm.exe" else "pnpm"

  private fun workspaceWithLocalRelease(
    baseUrl: String? = null,
    extraConfiguration: String = "",
  ): GradleProjectFixture {
    val url =
      baseUrl
        ?: PnpmArchiveFixture.writeRelease(releaseDirectory, GradleProjectFixture.PNPM_VERSION)
    val fixture = GradleProjectFixture(projectDirectory)
    fixture.writeWorkspace(
      pnpmConfiguration =
        """
        downloadBaseUrl = "$url"
        preferPnpmOnPath = false
        $extraConfiguration
        """
          .trimIndent()
    )
    return fixture
  }

  private companion object {
    const val SHA256_LENGTH = 64
  }
}
