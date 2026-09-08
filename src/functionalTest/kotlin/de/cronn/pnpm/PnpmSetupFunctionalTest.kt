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

  /**
   * The configuration cache resolves every file collection reachable from a task while it
   * serializes the task graph, which happens before any `onlyIf` spec runs. A build that already
   * has a usable pnpm must not resolve the distribution at all, which an unreachable base url
   * proves: resolving it would fail the build.
   */
  @Test
  fun `does not resolve the pnpm distribution when a matching pnpm is configured`() {
    val fixture = GradleProjectFixture(projectDirectory)
    fixture.writeWorkspace()

    val result =
      fixture
        .runner("pnpmSetup", "-P$BASE_URL_PROPERTY=${File(releaseDirectory, "absent").toURI()}")
        .build()

    assertThat(result.task(":pnpmSetup")?.outcome).isEqualTo(TaskOutcome.SKIPPED)
  }

  @Test
  fun `reports what the archive contained when pnpm is missing from it`() {
    val baseUrl =
      PnpmArchiveFixture.writeRelease(
        releaseDirectory,
        GradleProjectFixture.PNPM_VERSION,
        entries = mapOf("README.md" to "no pnpm here\n", "bin/other" to "nope\n"),
      )
    val fixture = workspaceWithLocalRelease(baseUrl)

    val result = fixture.runner("pnpmSetup").buildAndFail()

    assertThat(result.output)
      .contains("Expected a pnpm executable named '$executableName'")
      .contains("the archive contained: README.md")
  }

  @Test
  fun `fails with a readable message when the pnpm distribution cannot be resolved`() {
    val empty = File(releaseDirectory, "empty").apply { mkdirs() }
    val fixture = workspaceWithLocalRelease(empty.toURI().toString())

    val result = fixture.runner("pnpmSetup").buildAndFail()

    assertThat(result.output)
      .contains("Could not find com.pnpm:pnpm:${GradleProjectFixture.PNPM_VERSION}")
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

  /** The repository the plugin declares is detached, so this mode does not apply to it. */
  @Test
  fun `provisions pnpm when the build forbids project repositories`() {
    val fixture =
      workspaceWithLocalRelease(
        settingsScript =
          """
          dependencyResolutionManagement {
            repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
            repositories { mavenCentral() }
          }
          """
            .trimIndent()
      )

    val result = fixture.runner("pnpmSetup").build()

    assertThat(result.task(":pnpmSetup")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
  }

  /**
   * In the default repositories mode, repositories declared in settings are consulted only while a
   * project declares none -- so a project repository added by the plugin would silently break the
   * resolution of everything else in the workspace root.
   */
  @Test
  fun `leaves the repositories declared in settings alone`() {
    val fixture =
      workspaceWithLocalRelease(
        settingsScript =
          """
          dependencyResolutionManagement { repositories { mavenCentral() } }
          """
            .trimIndent(),
        rootBuildScript =
          """
          val other: Configuration by configurations.creating
          dependencies { other("org.apache.commons:commons-lang3:3.20.0") }

          tasks.register("resolveOther") {
            val files = other.incoming.files
            doLast { logger.lifecycle("resolved " + files.files.single().name) }
          }
          """
            .trimIndent(),
      )

    val result = fixture.runner("pnpmSetup", "resolveOther").build()

    assertThat(result.task(":pnpmSetup")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    assertThat(result.output).contains("resolved commons-lang3-3.20.0.jar")
  }

  /** The detached configuration is not reached by the locking the build applies to its own. */
  @Test
  fun `does not add the pnpm distribution to the dependency locks`() {
    val fixture =
      workspaceWithLocalRelease(rootBuildScript = "dependencyLocking { lockAllConfigurations() }")

    fixture.runner("pnpmSetup", "--write-locks").build()

    val lockfiles =
      fixture.rootDirectory.walkTopDown().filter { it.isFile && it.name.endsWith(".lockfile") }
    assertThat(lockfiles.map { it.readText() }.toList()).noneMatch { it.contains("com.pnpm") }
  }

  /** Tail of the path of the pnpm the plugin downloads, independent of the temporary directory. */
  private val downloadedExecutablePath: String
    get() =
      listOf(".gradle", "pnpm", GradleProjectFixture.PNPM_VERSION, executableName)
        .joinToString(File.separator)

  private val executableName: String
    get() = if (PnpmStub.isWindows) "pnpm.exe" else "pnpm"

  /**
   * A workspace whose `pnpmSetup` resolves pnpm from a local mirror of the pnpm releases, pointed
   * at through the Gradle property that also serves real mirrors and air-gapped builds.
   */
  private fun workspaceWithLocalRelease(
    baseUrl: String? = null,
    rootBuildScript: String = "",
    settingsScript: String = "",
  ): GradleProjectFixture {
    val url =
      baseUrl
        ?: PnpmArchiveFixture.writeRelease(releaseDirectory, GradleProjectFixture.PNPM_VERSION)
    val fixture = GradleProjectFixture(projectDirectory)
    fixture.writeWorkspace(
      rootBuildScript = rootBuildScript,
      pnpmConfiguration = "preferPnpmOnPath = false",
      settingsScript = settingsScript,
    )
    fixture.write("gradle.properties", "$BASE_URL_PROPERTY=$url")
    return fixture
  }

  private companion object {
    const val BASE_URL_PROPERTY = "de.cronn.pnpm.distributionBaseUrl"
  }
}
