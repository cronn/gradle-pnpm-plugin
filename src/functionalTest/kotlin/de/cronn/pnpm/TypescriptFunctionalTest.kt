package de.cronn.pnpm

import de.cronn.pnpm.fixture.GradleProjectFixture
import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class TypescriptFunctionalTest {

  @TempDir lateinit var projectDirectory: File

  @Test
  fun `passes the detected tsconfig as the project`() {
    val fixture = workspaceWithFrontend()

    val result = fixture.runner(":frontend:compileTypescript").build()

    assertThat(result.task(":frontend:compileTypescript")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    assertThat(tsc(fixture).arguments)
      .containsExactly("exec", "tsc", *projectArguments(fixture, "frontend/tsconfig.json"))
  }

  @Test
  fun `passes the project given on the command line`() {
    val fixture = workspaceWithFrontend()
    fixture.write("frontend/tsconfig.build.json", "{}")

    fixture.runner(":frontend:compileTypescript", "--project=tsconfig.build.json").build()

    assertThat(tsc(fixture).arguments)
      .containsExactly("exec", "tsc", *projectArguments(fixture, "frontend/tsconfig.build.json"))
  }

  @Test
  fun `passes noEmit and outDir on to tsc`() {
    val fixture = workspaceWithFrontend()

    fixture.runner(":frontend:compileTypescript", "--noEmit", "--outDir=dist").build()

    assertThat(tsc(fixture).arguments)
      .containsSubsequence("--noEmit", "--outDir", fixture.directory("frontend/dist").canonicalPath)
  }

  @Test
  fun `passes build first and clean as they are`() {
    val fixture = workspaceWithFrontend()

    fixture.runner(":frontend:compileTypescript", "--build", "--clean").build()

    assertThat(tsc(fixture).arguments)
      .containsExactly(
        "exec",
        "tsc",
        "--build",
        *projectArguments(fixture, "frontend/tsconfig.json"),
        "--clean",
      )
  }

  @Test
  fun `passes the options of a custom task on to tsc`() {
    val fixture =
      workspaceWithFrontend(
        packageBuildScript =
          """
          tasks.register<de.cronn.pnpm.task.TypescriptTask>("compileTypescriptBuild") {
            build = true
            noEmit = true
            outDir = layout.buildDirectory.dir("tsc")
          }
          """,
      )

    fixture.runner(":frontend:compileTypescriptBuild").build()

    assertThat(tsc(fixture).arguments)
      .containsExactly(
        "exec",
        "tsc",
        "--build",
        *projectArguments(fixture, "frontend/tsconfig.json"),
        "--noEmit",
        "--outDir",
        fixture.directory("frontend/build/tsc").canonicalPath,
      )
  }

  @Test
  fun `passes the options of the extension on to tsc unless the task overrides them`() {
    val fixture =
      workspaceWithFrontend(
        packageBuildScript =
          """
          typescript {
            noEmit = true
            outDir = layout.buildDirectory.dir("tsc")
          }
          tasks.compileTypescript { noEmit = false }
          """,
      )

    fixture.runner(":frontend:compileTypescript").build()

    assertThat(tsc(fixture).arguments)
      .containsExactly(
        "exec",
        "tsc",
        *projectArguments(fixture, "frontend/tsconfig.json"),
        "--outDir",
        fixture.directory("frontend/build/tsc").canonicalPath,
      )
  }

  @Test
  fun `reuses the configuration cache on a second run with options`() {
    val fixture = workspaceWithFrontend()

    fixture.runner(":frontend:compileTypescript", "--noEmit", "--outDir=dist").build()
    val second = fixture.runner(":frontend:compileTypescript", "--noEmit", "--outDir=dist").build()

    assertThat(second.output).contains("Configuration cache entry reused")
  }

  /** A workspace whose `frontend` package is configured for TypeScript and holds one source. */
  private fun workspaceWithFrontend(packageBuildScript: String = ""): GradleProjectFixture {
    val fixture = GradleProjectFixture(projectDirectory)
    fixture.writeWorkspace(packages = listOf("frontend"), packageBuildScript = packageBuildScript)
    fixture.write("frontend/src/main.ts", "export const main = 1")
    return fixture
  }

  /** The invocation that ran tsc, as opposed to the one that installed the workspace. */
  private fun tsc(fixture: GradleProjectFixture) =
    fixture.stub.invocations().last { it.arguments.contains("tsc") }

  private fun projectArguments(fixture: GradleProjectFixture, path: String) =
    fixture.configArguments(path, option = "--project")
}
