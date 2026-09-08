package de.cronn.pnpm

import de.cronn.pnpm.fixture.GradleProjectFixture
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

  @TempDir lateinit var releaseDirectory: File

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
   * Provisioning pnpm reaches further into Gradle's API than the rest of the plugin does -- a
   * detached resolver, a resolvable configuration and an Ivy repository with a pattern layout -- so
   * it is worth resolving an actual distribution on every supported Gradle version.
   */
  @ParameterizedTest(name = "Gradle {0}", allowZeroInvocations = true)
  @MethodSource("gradleVersions")
  fun `provisions pnpm on the given gradle version`(gradleVersion: String) {
    val baseUrl =
      PnpmArchiveFixture.writeRelease(releaseDirectory, GradleProjectFixture.PNPM_VERSION)
    val fixture = GradleProjectFixture(projectDirectory)
    fixture.writeWorkspace(pnpmConfiguration = "preferPnpmOnPath = false")
    fixture.write("gradle.properties", "de.cronn.pnpm.distributionBaseUrl=$baseUrl")

    val result = fixture.runner("pnpmSetup").withGradleVersion(gradleVersion).build()

    assertThat(result.task(":pnpmSetup")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    val executableName = if (PnpmStub.isWindows) "pnpm.exe" else "pnpm"
    assertThat(
        fixture.directory(".gradle/pnpm/${GradleProjectFixture.PNPM_VERSION}/$executableName")
      )
      .isFile()
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
