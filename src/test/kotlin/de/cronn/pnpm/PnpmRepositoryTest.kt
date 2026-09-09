package de.cronn.pnpm

import de.cronn.pnpm.PnpmPluginTest.Companion.packageProject
import de.cronn.pnpm.PnpmPluginTest.Companion.workspaceProject
import de.cronn.pnpm.internal.PnpmRepository
import java.io.File
import java.net.URI
import org.assertj.core.api.Assertions.assertThat
import org.gradle.api.Project
import org.gradle.api.artifacts.repositories.IvyArtifactRepository
import org.gradle.api.internal.project.ProjectInternal
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * The plugin registers the repository serving the pnpm distribution after the project was
 * evaluated, so every case here has to evaluate the project first.
 *
 * `ProjectBuilder` builds a project without settings, which is the case in which the plugin cannot
 * tell whether the build manages its repositories centrally and registers the repository.
 */
class PnpmRepositoryTest {

  @Test
  fun `registers an ivy repository for the pnpm releases on the workspace root`(
    @TempDir directory: File
  ) {
    val project = evaluated(workspaceProject(directory))

    val repository = project.repositories.single()
    assertThat(repository.name).isEqualTo(PnpmRepository.PNPM_REPOSITORY_NAME)
    assertThat(repository).isInstanceOf(IvyArtifactRepository::class.java)
    assertThat((repository as IvyArtifactRepository).url)
      .isEqualTo(URI(PnpmRepository.PNPM_RELEASES_URL))
  }

  @Test
  fun `registers no repository in a package`(@TempDir directory: File) {
    val project = evaluated(packageProject(directory))

    assertThat(project.repositories).isEmpty()
  }

  @Test
  fun `resolves the distribution from a configured mirror`(@TempDir directory: File) {
    val project = workspaceProject(directory)
    extension(project).repositoryUrl.set("https://artifacts.example.com/pnpm/")

    val repository = evaluated(project).repositories.single() as IvyArtifactRepository
    assertThat(repository.url).isEqualTo(URI("https://artifacts.example.com/pnpm/"))
  }

  /** A build that declares the repository itself keeps the one it declared. */
  @Test
  fun `registers no repository when the build already declares one under that name`(
    @TempDir directory: File
  ) {
    val project = workspaceProject(directory)
    project.repositories.ivy { it.name = PnpmRepository.PNPM_REPOSITORY_NAME }

    assertThat(evaluated(project).repositories.map { it.name })
      .containsExactly(PnpmRepository.PNPM_REPOSITORY_NAME)
  }

  /**
   * The exclusive content declaration filters the content of the other repositories; it must not
   * remove or reorder them.
   */
  @Test
  fun `leaves the other repositories of the build in place`(@TempDir directory: File) {
    val project = workspaceProject(directory)
    project.repositories.mavenCentral()
    project.repositories.mavenLocal()

    assertThat(evaluated(project).repositories.map { it.name })
      .containsExactly("MavenRepo", "MavenLocal", PnpmRepository.PNPM_REPOSITORY_NAME)
  }

  private fun evaluated(project: Project): Project = project.also {
    (it as ProjectInternal).evaluate()
  }

  private fun extension(project: Project): PnpmExtension =
    project.extensions.getByType(PnpmExtension::class.java)
}
