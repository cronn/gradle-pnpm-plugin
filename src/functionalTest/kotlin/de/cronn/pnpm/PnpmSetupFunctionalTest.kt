package de.cronn.pnpm

import de.cronn.pnpm.fixture.GradleProjectFixture
import de.cronn.pnpm.fixture.GradleProjectFixture.Companion.pnpmRepository
import de.cronn.pnpm.fixture.GradleProjectFixture.Companion.settingsRepositories
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
    fixture.writeWorkspace(repositoryUrl = "https://127.0.0.1:1/unreachable")

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
    fixture.writeWorkspace(pnpmConfiguration = "", repositoryUrl = emptyRepositoryUrl())

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

  // The repository the plugin registers, and the cases in which it steps aside

  @Test
  fun `registers the pnpm repository in the workspace root`() {
    val fixture = GradleProjectFixture(projectDirectory)
    fixture.writeWorkspace(rootBuildScript = PRINT_REPOSITORIES)

    val result = fixture.runner("printRepositories").build()

    assertThat(result.output)
      .contains("repositories: [pnpm]")
      .contains("https://github.com/pnpm/pnpm/releases/download/")
  }

  /**
   * A build that declares its repositories in settings declares the pnpm repository there too, as a
   * plain Ivy declaration -- so `settings.gradle.kts` needs none of the plugin's classes, and the
   * plugin stays in the build script classpath of the projects that apply it.
   */
  @Test
  fun `resolves pnpm from the repository declared in settings`() {
    val url = PnpmArchiveFixture.writeRelease(releaseDirectory, GradleProjectFixture.PNPM_VERSION)
    val fixture = GradleProjectFixture(projectDirectory)
    fixture.writeWorkspace(
      settingsScript = settingsRepositories(pnpmRepository(url), failOnProjectRepositories = true),
      pnpmConfiguration = "",
    )

    val result = fixture.runner("pnpmSetup").build()

    assertThat(result.task(":pnpmSetup")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    assertThat(fixture.directory("settings.gradle.kts")).content().doesNotContain("de.cronn")
  }

  /**
   * And when it forgets to, the plugin must not be what fails the build: adding a project
   * repository is rejected outright in that mode, so it does not even try.
   */
  @Test
  fun `steps aside when project repositories are forbidden`() {
    val fixture = GradleProjectFixture(projectDirectory)
    fixture.writeWorkspace(
      settingsScript =
        settingsRepositories(ivyRepository(emptyRepositoryUrl()), failOnProjectRepositories = true),
      pnpmConfiguration = "",
    )

    val result = fixture.runner("pnpmSetup").buildAndFail()

    assertThat(result.output)
      .contains("Could not find pnpm:pnpm:${GradleProjectFixture.PNPM_VERSION}")
      .doesNotContain("was added by")
  }

  /**
   * Gradle consults the repositories declared in settings for a project that declares none of its
   * own. Registering one here would cut the project off from them, and every other dependency it
   * has with it, so the plugin leaves such a project alone.
   */
  @Test
  fun `leaves the repositories declared in settings in charge`() {
    val url = PnpmArchiveFixture.writeRelease(releaseDirectory, GradleProjectFixture.PNPM_VERSION)
    File(releaseDirectory, "v1.0").mkdirs()
    File(releaseDirectory, "v1.0/example-linux.tar.gz").writeText("an unrelated dependency\n")

    val fixture = GradleProjectFixture(projectDirectory)
    fixture.writeWorkspace(
      settingsScript = settingsRepositories(pnpmRepository(url), ivyRepository(url)),
      rootBuildScript = RESOLVE_OTHER,
      pnpmConfiguration = "",
    )

    val result = fixture.runner("pnpmSetup", "resolveOther").build()

    assertThat(result.task(":pnpmSetup")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    assertThat(result.output).contains("resolved: [example-linux.tar.gz]")
  }

  /** Tail of the path of the pnpm the plugin installs, independent of the temporary directory. */
  private val installedExecutablePath: String
    get() =
      listOf(".gradle", "pnpm", GradleProjectFixture.PNPM_VERSION, executableName)
        .joinToString(File.separator)

  private val executableName: String
    get() = if (PnpmStub.isWindows) "pnpm.exe" else "pnpm"

  /**
   * A workspace that resolves pnpm from a local pnpm release directory, which is what pointing the
   * build at a mirror looks like: the plugin registers its own repository, over that URL.
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
      rootBuildScript =
        """
        repositories {
          $firstRepositories
        }

        $extraBuildScript
        """
          .trimIndent(),
      pnpmConfiguration = "",
      repositoryUrl = url,
    )
    return fixture
  }

  /** URL of a repository directory that holds nothing at all. */
  private fun emptyRepositoryUrl(): String =
    File(releaseDirectory, "empty").apply { mkdirs() }.toURI().toString()

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

  companion object {

    /** Prints the repositories of the project once the plugin had its say on them. */
    val PRINT_REPOSITORIES: String =
      """
      afterEvaluate {
        val declared = repositories.map { it.name }
        val urls =
          repositories
            .filterIsInstance<org.gradle.api.artifacts.repositories.IvyArtifactRepository>()
            .map { it.url.toString() }
        tasks.register("printRepositories") {
          doLast {
            println("repositories: " + declared)
            println("urls: " + urls)
          }
        }
      }
      """
        .trimIndent()

    /** Resolves an unrelated artifact-only dependency, to prove the build still can. */
    val RESOLVE_OTHER: String =
      """
      val other = configurations.dependencyScope("other")
      val otherArchive = configurations.resolvable("otherArchive") { extendsFrom(other.get()) }
      dependencies { add("other", "org.example:example:1.0:linux@tar.gz") }
      tasks.register("resolveOther") {
        inputs.files(otherArchive.map { it.incoming.files })
        doLast { println("resolved: " + inputs.files.files.map { it.name }) }
      }
      """
        .trimIndent()
  }
}
