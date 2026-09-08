package de.cronn.pnpm

import java.io.File
import java.net.URI
import org.assertj.core.api.Assertions.assertThat
import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class PnpmRepositoriesTest {

  @Test
  fun `registers an ivy repository for the pnpm releases`(@TempDir directory: File) {
    val project = project(directory)

    val repository = project.repositories.pnpm()

    assertThat(project.repositories).containsExactly(repository)
    assertThat(repository.name).isEqualTo(PNPM_REPOSITORY_NAME)
    assertThat(repository.url).isEqualTo(URI(PNPM_RELEASES_URL))
  }

  @Test
  fun `applies the configuration action last`(@TempDir directory: File) {
    val project = project(directory)

    val repository =
      project.repositories.pnpm { mirror ->
        mirror.name = "pnpmMirror"
        mirror.setUrl("https://artifacts.example.com/pnpm/")
      }

    assertThat(project.repositories.single()).isSameAs(repository)
    assertThat(repository.name).isEqualTo("pnpmMirror")
    assertThat(repository.url).isEqualTo(URI("https://artifacts.example.com/pnpm/"))
  }

  @Test
  fun `can be declared more than once`(@TempDir directory: File) {
    val project = project(directory)

    project.repositories.pnpm()
    project.repositories.pnpm()

    assertThat(project.repositories.map { it.name }).containsExactly("pnpm", "pnpm2")
  }

  /**
   * The exclusive content declaration filters the content of the other repositories; it must not
   * remove or reorder them. That it actually keeps them from serving pnpm, and keeps the pnpm
   * repository from serving anything else, is covered by the functional tests, which resolve for
   * real.
   */
  @Test
  fun `leaves the other repositories of the build in place`(@TempDir directory: File) {
    val project = project(directory)

    project.repositories.mavenCentral()
    project.repositories.pnpm()
    project.repositories.mavenLocal()

    assertThat(project.repositories.map { it.name })
      .containsExactly("MavenRepo", "pnpm", "MavenLocal")
  }

  private fun project(directory: File): Project =
    ProjectBuilder.builder().withProjectDir(directory).build()
}
