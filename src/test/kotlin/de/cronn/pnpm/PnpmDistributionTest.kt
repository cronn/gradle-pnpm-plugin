package de.cronn.pnpm

import de.cronn.pnpm.internal.PnpmDistribution
import de.cronn.pnpm.internal.PnpmPlatform
import java.io.File
import java.net.URI
import org.assertj.core.api.Assertions.assertThat
import org.gradle.api.Project
import org.gradle.api.artifacts.ModuleDependency
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class PnpmDistributionTest {

  @ParameterizedTest
  @CsvSource(
    "Linux, amd64, linux-x64, tar.gz",
    "Mac OS X, aarch64, darwin-arm64, tar.gz",
    "Windows 11, amd64, win32-x64, zip",
  )
  fun `builds the release asset coordinates`(
    osName: String,
    osArch: String,
    identifier: String,
    extension: String,
  ) {
    val coordinates = PnpmDistribution.coordinates(PNPM_VERSION, PnpmPlatform(osName, osArch))

    assertThat(coordinates).isEqualTo("com.pnpm:pnpm:$PNPM_VERSION:$identifier@$extension")
  }

  /**
   * Gradle substitutes the artifact pattern, so no test can assert the resulting URL directly. This
   * substitutes it by hand instead, to keep the shape of a pnpm release asset pinned by a test.
   */
  @ParameterizedTest
  @CsvSource(
    "Linux, amd64, pnpm-linux-x64.tar.gz",
    "Mac OS X, aarch64, pnpm-darwin-arm64.tar.gz",
    "Windows 11, amd64, pnpm-win32-x64.zip",
  )
  fun `maps the coordinates onto a pnpm release asset`(
    osName: String,
    osArch: String,
    expectedAsset: String,
  ) {
    val platform = PnpmPlatform(osName, osArch)
    val asset =
      PnpmDistribution.ARTIFACT_PATTERN.replace("[revision]", PNPM_VERSION)
        .replace("[artifact]", PnpmDistribution.MODULE)
        .replace("[classifier]", platform.identifier)
        .replace("[ext]", platform.archiveExtension)

    assertThat("${PnpmDistribution.DEFAULT_BASE_URL}/$asset")
      .isEqualTo("https://github.com/pnpm/pnpm/releases/download/v$PNPM_VERSION/$expectedAsset")
  }

  @Test
  fun `reads the pnpm releases when no base url is configured`(@TempDir directory: File) {
    val distribution = distribution(project(directory))

    assertThat(distribution.repository.url).isEqualTo(URI(PnpmDistribution.DEFAULT_BASE_URL))
  }

  @Test
  fun `reads the base url from the gradle property, without its trailing slash`(
    @TempDir directory: File
  ) {
    val project = project(directory, baseUrl = "$MIRROR/")

    assertThat(distribution(project).repository.url).isEqualTo(URI(MIRROR))
  }

  /** The `pnpm` block runs after the plugin is applied, so both must still be picked up. */
  @Test
  fun `reads the base url and the version lazily`(@TempDir directory: File) {
    val project = project(directory)
    val extension = project.objects.newInstance(PnpmExtension::class.java)
    val distribution = PnpmDistribution(project, extension, PnpmPlatform.current())

    extension.version.set(PNPM_VERSION)

    val dependency = distribution.configuration.dependencies.single() as ModuleDependency
    assertThat(dependency.group).isEqualTo("com.pnpm")
    assertThat(dependency.name).isEqualTo("pnpm")
    assertThat(dependency.version).isEqualTo(PNPM_VERSION)
    assertThat(dependency.artifacts.single().extension)
      .isEqualTo(PnpmPlatform.current().archiveExtension)
    assertThat(dependency.artifacts.single().classifier)
      .isEqualTo(PnpmPlatform.current().identifier)
    assertThat(dependency.isTransitive).isFalse()
  }

  /**
   * The repository lives on a detached resolver: a project repository would be consulted by every
   * other configuration of the project, and in the default repositories mode it would suppress the
   * repositories the build declares in its settings.
   */
  @Test
  fun `declares neither a repository nor a configuration on the project`(@TempDir directory: File) {
    val project = project(directory)

    distribution(project)

    assertThat(project.repositories).isEmpty()
    assertThat(project.configurations.names).doesNotContain(PnpmDistribution.CONFIGURATION_NAME)
  }

  @Test
  fun `resolves nothing when pnpm does not have to be provisioned`(@TempDir directory: File) {
    val project = project(directory)
    val distribution = distribution(project)

    val archive = distribution.archive(project.provider { false })

    assertThat(archive.get().files).isEmpty()
  }

  private fun distribution(project: Project): PnpmDistribution {
    val extension = project.objects.newInstance(PnpmExtension::class.java)
    extension.version.set(PNPM_VERSION)
    return PnpmDistribution(project, extension, PnpmPlatform.current())
  }

  private fun project(directory: File, baseUrl: String? = null): Project {
    if (baseUrl != null) {
      File(directory, "gradle.properties")
        .writeText("${PnpmDistribution.BASE_URL_PROPERTY}=$baseUrl\n")
    }
    return ProjectBuilder.builder().withProjectDir(directory).build()
  }

  private companion object {
    const val PNPM_VERSION = "11.23.0"
    const val MIRROR = "https://mirror.example.com/pnpm"
  }
}
