package de.cronn.pnpm

import de.cronn.pnpm.fixture.GradleProjectFixture
import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * The plugin applied from a precompiled script plugin -- a convention plugin in `buildSrc`.
 *
 * Gradle derives the type-safe accessors of such a script by applying every plugin of its `plugins
 * {}` block to a synthetic project over an empty temporary directory, and turns any failure there
 * into a build failure. Applying the plugin must therefore succeed on a project with no pnpm files,
 * and whatever the plugin registers on that synthetic project is the surface the convention plugin
 * can use -- for every project it is later applied to, whatever role that project plays.
 */
class PnpmConventionPluginFunctionalTest {

  @TempDir lateinit var projectDirectory: File

  @Test
  fun `generates the accessors of a convention plugin that applies the plugin`() {
    val fixture = GradleProjectFixture(projectDirectory)
    fixture.writeWorkspaceWithConventionPlugins()

    // help configures nothing of the outer build, so this fails exactly when buildSrc does.
    val result = fixture.runner("help", injectPluginClasspath = false).build()

    assertThat(result.output).doesNotContain("type-safe Gradle model accessors")
  }

  @Test
  fun `applies the plugin to a workspace root and a package alike`() {
    val fixture = GradleProjectFixture(projectDirectory)
    fixture.writeWorkspaceWithConventionPlugins()

    val result =
      fixture
        .runner("pnpmInstall", ":frontend:prettierCheck", injectPluginClasspath = false)
        .build()

    assertThat(result.task(":pnpmInstall")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    assertThat(result.task(":frontend:prettierCheck")?.outcome).isEqualTo(TaskOutcome.SUCCESS)

    // The convention plugin configured pnpm and prettier on the package too, so its extra argument
    // has to show up in the invocation -- accessor generation alone would not prove that.
    val prettier =
      fixture.stub.invocations().single { invocation -> invocation.arguments.contains("prettier") }
    assertThat(prettier.arguments).contains("--cache")
  }

  @Test
  fun `reuses the configuration cache on a second run`() {
    val fixture = GradleProjectFixture(projectDirectory)
    fixture.writeWorkspaceWithConventionPlugins()

    fixture.runner("pnpmInstall", injectPluginClasspath = false).build()
    val second = fixture.runner("pnpmInstall", injectPluginClasspath = false).build()

    assertThat(second.output).contains("Reusing configuration cache")
  }
}
