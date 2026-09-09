package de.cronn.pnpm

import de.cronn.pnpm.fixture.GradleProjectFixture
import de.cronn.pnpm.fixture.GradleProjectFixture.Companion.pnpmRepository
import de.cronn.pnpm.fixture.GradleProjectFixture.Companion.settingsRepositories
import de.cronn.pnpm.fixture.PnpmArchiveFixture
import de.cronn.pnpm.fixture.PnpmStub
import java.io.File
import java.util.stream.Stream
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

/**
 * Runs the plugins against other Gradle versions.
 *
 * This tier is opt-in, because it downloads Gradle distributions: pass
 * `-PpnpmTestGradleVersions=9.0,9.7.1`. Without it there are no test cases and the class is
 * skipped.
 */
class PnpmGradleVersionFunctionalTest {

  @TempDir lateinit var projectDirectory: File

  @ParameterizedTest(name = "Gradle {0}", allowZeroInvocations = true)
  @MethodSource("gradleVersions")
  fun `builds a workspace on the given gradle version`(gradleVersion: String) {
    val fixture = GradleProjectFixture(projectDirectory)
    fixture.writeWorkspace(packages = listOf("frontend"))
    fixture.write("frontend/main.ts", "export const main = 1")

    val result = fixture.runner(":frontend:check").withGradleVersion(gradleVersion).build()

    assertThat(result.task(":frontend:prettierCheck")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    assertThat(result.task(":pnpmInstall")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
  }

  /**
   * Resolving the pnpm distribution touches the dependency management APIs that changed most
   * between Gradle versions, so it gets its own case in this tier.
   */
  @ParameterizedTest(name = "Gradle {0}", allowZeroInvocations = true)
  @MethodSource("gradleVersions")
  fun `resolves and extracts pnpm on the given gradle version`(
    gradleVersion: String,
    @TempDir releaseDirectory: File,
  ) {
    val url = PnpmArchiveFixture.writeRelease(releaseDirectory, GradleProjectFixture.PNPM_VERSION)
    val fixture = GradleProjectFixture(projectDirectory)
    fixture.writeWorkspace(pnpmConfiguration = "", repositoryUrl = url)

    val result = fixture.runner("pnpmSetup").withGradleVersion(gradleVersion).build()

    assertThat(result.task(":pnpmSetup")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    assertThat(
        fixture.directory(
          ".gradle/pnpm/${GradleProjectFixture.PNPM_VERSION}/" +
            if (PnpmStub.isWindows) "pnpm.exe" else "pnpm"
        )
      )
      .isFile()
  }

  /**
   * The plugin reads the repositories mode of the build through the one internal Gradle API it
   * uses, so this tier is where a change to it would show up first.
   */
  @ParameterizedTest(name = "Gradle {0}", allowZeroInvocations = true)
  @MethodSource("gradleVersions")
  fun `resolves pnpm from settings when project repositories are forbidden`(
    gradleVersion: String,
    @TempDir releaseDirectory: File,
  ) {
    val url = PnpmArchiveFixture.writeRelease(releaseDirectory, GradleProjectFixture.PNPM_VERSION)
    val fixture = GradleProjectFixture(projectDirectory)
    fixture.writeWorkspace(
      settingsScript = settingsRepositories(pnpmRepository(url), failOnProjectRepositories = true),
      pnpmConfiguration = "",
    )

    val result = fixture.runner("pnpmSetup").withGradleVersion(gradleVersion).build()

    assertThat(result.task(":pnpmSetup")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
  }

  companion object {
    @JvmStatic
    fun gradleVersions(): Stream<String> =
      System.getProperty("pnpm.test.gradleVersions")
        .orEmpty()
        .split(",")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .stream()
  }
}
