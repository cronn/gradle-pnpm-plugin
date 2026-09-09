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
  fun `resolves and extracts the pinned pnpm version`() {
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
  fun `does not extract again on a second run`() {
    val fixture = workspaceWithLocalRelease()

    fixture.runner("pnpmSetup").build()
    val second = fixture.runner("pnpmSetup").build()

    assertThat(second.task(":pnpmSetup")?.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)
  }

  @Test
  fun `leaves no archive behind in the install directory`() {
    val fixture = workspaceWithLocalRelease()

    fixture.runner("pnpmSetup").build()

    val installDirectory = fixture.directory(".gradle/pnpm/${GradleProjectFixture.PNPM_VERSION}")
    assertThat(installDirectory.walkTopDown().filter { it.isFile }.map { it.name }.toList())
      .noneMatch { it.endsWith(".tar.gz") || it.endsWith(".zip") }
  }

  @Test
  fun `is skipped when a matching pnpm is configured explicitly`() {
    val fixture = GradleProjectFixture(projectDirectory)
    fixture.writeWorkspace()

    val result = fixture.runner("pnpmSetup").build()

    assertThat(result.task(":pnpmSetup")?.outcome).isEqualTo(TaskOutcome.SKIPPED)
  }

  /**
   * A pnpm on the `PATH` is reused whatever version it is: the pinned version only decides which
   * pnpm is downloaded when there is none.
   */
  @Test
  fun `is skipped when a pnpm of another version is on the path`() {
    val onPath = PnpmStub(File(projectDirectory, "pnpm-on-path"))
    val executable = onPath.install()
    val fixture = workspaceWithLocalRelease()

    val result = fixture.runner("pnpmInstall", pnpmOnPath = executable).build()

    assertThat(result.task(":pnpmSetup")?.outcome).isEqualTo(TaskOutcome.SKIPPED)
    assertThat(onPath.invocations())
      .singleElement()
      .extracting { it.arguments }
      .isEqualTo(listOf("install"))
  }

  /**
   * The configuration cache resolves the inputs of a task while it stores the entry, before any
   * `onlyIf` runs. A build that does not provision pnpm must therefore not even reach the
   * repository -- here an unreachable one, so that any resolution would fail the build.
   */
  @Test
  fun `resolves nothing when pnpm comes from somewhere else`() {
    val fixture = GradleProjectFixture(projectDirectory)
    fixture.writeWorkspace(
      imports = listOf("de.cronn.pnpm.pnpm"),
      rootBuildScript =
        """
        repositories {
          pnpm { setUrl("https://127.0.0.1:1/unreachable") }
        }
        """
          .trimIndent(),
    )

    val first = fixture.runner("pnpmSetup").build()
    val second = fixture.runner("pnpmSetup").build()

    assertThat(first.task(":pnpmSetup")?.outcome).isEqualTo(TaskOutcome.SKIPPED)
    assertThat(second.task(":pnpmSetup")?.outcome).isEqualTo(TaskOutcome.SKIPPED)
  }

  @Test
  fun `reports what the archive contained when pnpm is missing from it`() {
    val repositoryUrl =
      PnpmArchiveFixture.writeRelease(
        releaseDirectory,
        GradleProjectFixture.PNPM_VERSION,
        entries = mapOf("README.md" to "no pnpm here\n", "bin/other" to "nope\n"),
      )
    val fixture = workspaceWithLocalRelease(repositoryUrl)

    val result = fixture.runner("pnpmSetup").buildAndFail()

    assertThat(result.output)
      .contains("Expected a pnpm executable named '$executableName'")
      .contains("the archive contained: README.md")
  }

  @Test
  fun `fails with a readable message when no repository serves the pnpm distribution`() {
    val fixture = GradleProjectFixture(projectDirectory)
    fixture.writeWorkspace(pnpmConfiguration = "")

    val result = fixture.runner("pnpmSetup").buildAndFail()

    assertThat(result.output)
      .contains("pnpmDistributionArchive")
      .contains("pnpm:pnpm:${GradleProjectFixture.PNPM_VERSION}")
  }

  /**
   * The pnpm repository is declared with exclusive content: another repository of the same build
   * must not serve pnpm, even when it could and even when it is declared first.
   */
  @Test
  fun `keeps the other repositories of the build from serving pnpm`() {
    val decoy = File(releaseDirectory, "decoy").apply { mkdirs() }
    val decoyUrl =
      PnpmArchiveFixture.writeRelease(
        decoy,
        GradleProjectFixture.PNPM_VERSION,
        entries = mapOf("pnpm" to "#!/bin/sh\necho decoy\n", "pnpm.exe" to "decoy\n"),
      )
    val fixture =
      workspaceWithLocalRelease(
        repositoryUrl =
          PnpmArchiveFixture.writeRelease(
            File(releaseDirectory, "releases").apply { mkdirs() },
            GradleProjectFixture.PNPM_VERSION,
          ),
        firstRepositories = ivyRepository(decoyUrl),
      )

    val result = fixture.runner("pnpmSetup").build()

    assertThat(result.task(":pnpmSetup")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    assertThat(
        fixture.directory(".gradle/pnpm/${GradleProjectFixture.PNPM_VERSION}/$executableName")
      )
      .content()
      .doesNotContain("decoy")
  }

  /**
   * And the pnpm repository is never asked for anything but pnpm -- not even for a module whose
   * artifact happens to sit right next to the pnpm release it serves.
   */
  @Test
  fun `is not consulted for the other dependencies of the build`() {
    val repositoryUrl =
      PnpmArchiveFixture.writeRelease(releaseDirectory, GradleProjectFixture.PNPM_VERSION)
    File(releaseDirectory, "v1.0").apply { mkdirs() }
    File(releaseDirectory, "v1.0/example-linux.tar.gz").writeText("not pnpm\n")

    val fixture =
      workspaceWithLocalRelease(
        repositoryUrl = repositoryUrl,
        extraBuildScript =
          """
          val other = configurations.dependencyScope("other")
          val otherArchive = configurations.resolvable("otherArchive") {
            extendsFrom(other.get())
          }
          dependencies { add("other", "org.example:example:1.0:linux@tar.gz") }
          tasks.register("resolveOther") {
            inputs.files(otherArchive.map { it.incoming.files })
          }
          """
            .trimIndent(),
      )

    val result = fixture.runner("resolveOther").buildAndFail()

    assertThat(result.output).contains("Could not find org.example:example:1.0")
  }

  @Test
  fun `uses the resolved pnpm for the workspace tasks`() {
    val fixture = workspaceWithLocalRelease()

    if (PnpmStub.isWindows) {
      // A `pnpm.exe` inside the archive cannot be faked by a script, so pnpmInstall only gets as
      // far as starting it. That it tries to start the installed executable is the point here.
      val result = fixture.runner("pnpmInstall").buildAndFail()

      assertThat(result.task(":pnpmSetup")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
      assertThat(result.output)
        .contains("A problem occurred starting process")
        .contains(installedExecutablePath)
    } else {
      val result = fixture.runner("pnpmInstall").build()

      assertThat(result.task(":pnpmSetup")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
      // The extracted stub echoes its arguments, which proves the managed pnpm was invoked.
      assertThat(result.output).contains("installed pnpm called with: install")
    }
  }

  /** Tail of the path of the pnpm the plugin installs, independent of the temporary directory. */
  private val installedExecutablePath: String
    get() =
      listOf(".gradle", "pnpm", GradleProjectFixture.PNPM_VERSION, executableName)
        .joinToString(File.separator)

  private val executableName: String
    get() = if (PnpmStub.isWindows) "pnpm.exe" else "pnpm"

  /**
   * A workspace that resolves pnpm from a local pnpm repository. Registering the repository is
   * exactly what a real build does, only with the pnpm releases of GitHub behind it.
   */
  private fun workspaceWithLocalRelease(
    repositoryUrl: String? = null,
    /** Repositories declared before the pnpm repository. */
    firstRepositories: String = "",
    extraBuildScript: String = "",
  ): GradleProjectFixture {
    val url =
      repositoryUrl
        ?: PnpmArchiveFixture.writeRelease(releaseDirectory, GradleProjectFixture.PNPM_VERSION)
    val fixture = GradleProjectFixture(projectDirectory)
    fixture.writeWorkspace(
      imports = listOf("de.cronn.pnpm.pnpm"),
      rootBuildScript =
        """
        repositories {
          $firstRepositories
          pnpm { setUrl("$url") }
        }

        $extraBuildScript
        """
          .trimIndent(),
      pnpmConfiguration = "",
    )
    return fixture
  }

  /** An Ivy repository over a local pnpm release directory, laid out like the pnpm repository. */
  private fun ivyRepository(url: String): String =
    """
    ivy {
      setUrl("$url")
      patternLayout { artifact("v[revision]/[artifact]-[classifier].[ext]") }
      metadataSources { artifact() }
    }
    """
      .trimIndent()
}
