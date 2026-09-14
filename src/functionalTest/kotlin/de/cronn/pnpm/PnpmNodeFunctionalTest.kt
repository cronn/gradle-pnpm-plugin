package de.cronn.pnpm

import de.cronn.pnpm.fixture.GradleProjectFixture
import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class PnpmNodeFunctionalTest {

  @TempDir lateinit var projectDirectory: File

  @Test
  fun `runs the entry point through pnpm exec node in the package directory`() {
    val fixture = workspaceWithFrontend()

    val result = fixture.runner(":frontend:generate").build()

    assertThat(result.task(":frontend:generate")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    val run = nodeRun(fixture)
    assertThat(run.arguments).containsExactly("exec", "node", entryPoint(fixture))
    assertThat(File(run.workingDirectory).canonicalFile)
      .isEqualTo(fixture.directory("frontend").canonicalFile)
  }

  @Test
  fun `passes the node options before the entry point and the arguments after it`() {
    val fixture =
      workspaceWithFrontend(
        taskConfiguration =
          """
          nodeOptions = listOf("--enable-source-maps")
          arguments = listOf("--out", "build/generated")
          """
      )

    fixture.runner(":frontend:generate").build()

    assertThat(nodeRun(fixture).arguments)
      .containsExactly(
        "exec",
        "node",
        "--enable-source-maps",
        entryPoint(fixture),
        "--out",
        "build/generated",
      )
  }

  @Test
  fun `installs the workspace before running the program`() {
    val fixture = workspaceWithFrontend()

    fixture.runner(":frontend:generate").build()

    assertThat(fixture.stub.invocations().map { it.arguments })
      .containsSequence(listOf("install"), listOf("exec", "node", entryPoint(fixture)))
  }

  @Test
  fun `tracks the entry point as an input`() {
    val fixture = workspaceWithFrontend(taskConfiguration = """outputs.upToDateWhen { true }""")

    assertThat(fixture.runner(":frontend:generate").build().task(":frontend:generate")?.outcome)
      .isEqualTo(TaskOutcome.SUCCESS)
    assertThat(fixture.runner(":frontend:generate").build().task(":frontend:generate")?.outcome)
      .isEqualTo(TaskOutcome.UP_TO_DATE)

    fixture.write("frontend/scripts/generate.mjs", "console.log(2)")

    assertThat(fixture.runner(":frontend:generate").build().task(":frontend:generate")?.outcome)
      .isEqualTo(TaskOutcome.SUCCESS)
  }

  @Test
  fun `fails when no entry point is configured`() {
    val fixture = workspaceWithFrontend(entryPointConfiguration = "")

    val result = fixture.runner(":frontend:generate").buildAndFail()

    assertThat(result.output).contains("property 'entryPoint' doesn't have a configured value")
  }

  @Test
  fun `reuses the configuration cache on a second run`() {
    val fixture = workspaceWithFrontend()

    fixture.runner(":frontend:generate").build()
    val second = fixture.runner(":frontend:generate").build()

    assertThat(second.output).contains("Configuration cache entry")
  }

  /**
   * A workspace whose `frontend` package registers a `generate` task over a Node program.
   *
   * The task type is spelled out: [GradleProjectFixture] writes the build script of a package below
   * its `plugins` block, which leaves no room for an import.
   */
  private fun workspaceWithFrontend(
    taskConfiguration: String = "",
    entryPointConfiguration: String =
      """entryPoint = layout.projectDirectory.file("scripts/generate.mjs")""",
  ): GradleProjectFixture {
    val fixture = GradleProjectFixture(projectDirectory)
    fixture.writeWorkspace(
      packages = listOf("frontend"),
      packageBuildScript =
        """
        tasks.register<de.cronn.pnpm.task.NodeTask>("generate") {
          $entryPointConfiguration
          $taskConfiguration
        }
        """,
    )
    fixture.write("frontend/scripts/generate.mjs", "console.log(1)")
    return fixture
  }

  /** The invocation that ran the program, as opposed to the one that installed the workspace. */
  private fun nodeRun(fixture: GradleProjectFixture) =
    fixture.stub.invocations().last { it.arguments.contains("node") }

  /** The absolute path the task hands to Node. */
  private fun entryPoint(fixture: GradleProjectFixture) =
    fixture.directory("frontend/scripts/generate.mjs").canonicalFile.absolutePath
}
