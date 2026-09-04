package de.cronn.pnpm

import de.cronn.pnpm.fixture.GradleProjectFixture
import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class PnpmPackageFunctionalTest {

  @TempDir lateinit var projectDirectory: File

  @Test
  fun `runs prettier through pnpm exec in the package directory`() {
    val fixture = workspaceWithFrontend()

    val result = fixture.runner(":frontend:prettierCheck").build()

    assertThat(result.task(":frontend:prettierCheck")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    val prettier = fixture.stub.invocations().single { it.arguments.contains("prettier") }
    assertThat(prettier.arguments).containsExactly("exec", "prettier", *PRETTIER_SOURCES, "--check")
    assertThat(File(prettier.workingDirectory).canonicalFile)
      .isEqualTo(fixture.directory("frontend").canonicalFile)
  }

  @Test
  fun `installs the workspace before running a tool`() {
    val fixture = workspaceWithFrontend()

    fixture.runner(":frontend:prettierCheck").build()

    assertThat(fixture.stub.invocations().first().arguments).containsExactly("install")
  }

  @Test
  fun `check runs all three tools`() {
    val fixture = workspaceWithFrontend()

    val result = fixture.runner(":frontend:check").build()

    assertThat(result.task(":frontend:compileTypescript")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    assertThat(result.task(":frontend:prettierCheck")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    assertThat(result.task(":frontend:eslintCheck")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    assertThat(fixture.stub.invocations().map { it.arguments })
      .contains(
        listOf("exec", "tsc", "--noEmit"),
        listOf("exec", "prettier", *PRETTIER_SOURCES, "--check"),
        listOf("exec", "eslint", *ESLINT_SOURCES, "--max-warnings=0", "--no-warn-ignored"),
      )
  }

  @Test
  fun `fix applies eslint before prettier`() {
    val fixture = workspaceWithFrontend()

    fixture.runner(":frontend:fix").build()

    val fixes =
      fixture.stub
        .invocations()
        .map { it.arguments }
        .filter { it.contains("--fix") || it.contains("--write") }
    assertThat(fixes)
      .containsExactly(
        listOf("exec", "eslint", *ESLINT_SOURCES, "--max-warnings=0", "--no-warn-ignored", "--fix"),
        listOf("exec", "prettier", *PRETTIER_SOURCES, "--write", "--list-different"),
      )
  }

  @Test
  fun `appends the configured extra arguments`() {
    val fixture =
      workspaceWithFrontend(
        packageBuildScript =
          """
          prettier { extraArguments("--cache") }
          """
      )

    fixture.runner(":frontend:prettierCheck").build()

    assertThat(fixture.stub.invocations().map { it.arguments })
      .contains(listOf("exec", "prettier", *PRETTIER_SOURCES, "--check", "--cache"))
  }

  @Test
  fun `skips a disabled tool and drops it from check`() {
    val fixture =
      workspaceWithFrontend(
        packageBuildScript =
          """
          eslint { enabled = false }
          """
      )

    val result = fixture.runner(":frontend:check").build()

    assertThat(result.task(":frontend:eslintCheck")).isNull()
    assertThat(result.task(":frontend:prettierCheck")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    assertThat(fixture.stub.invocations().map { it.arguments }).noneSatisfy { arguments ->
      assertThat(arguments).contains("eslint")
    }
  }

  @Test
  fun `is up to date when the sources are unchanged and reruns when they change`() {
    val fixture = workspaceWithFrontend()

    fixture.runner(":frontend:eslintCheck").build()
    val second = fixture.runner(":frontend:eslintCheck").build()
    assertThat(second.task(":frontend:eslintCheck")?.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)

    fixture.write("frontend/main.ts", "export const changed = true")
    val third = fixture.runner(":frontend:eslintCheck").build()
    assertThat(third.task(":frontend:eslintCheck")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
  }

  @Test
  fun `the default patterns reach neither the node_modules nor the build directory`() {
    val fixture = workspaceWithFrontend()
    fixture.write("frontend/node_modules/dependency/index.ts", "export const dependency = 1")
    fixture.write("frontend/build/generated.ts", "export const generated = 1")

    fixture.runner(":frontend:eslintCheck").build()
    val second = fixture.runner(":frontend:eslintCheck").build()
    assertThat(second.task(":frontend:eslintCheck")?.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)

    fixture.write("frontend/node_modules/dependency/index.ts", "export const dependency = 2")
    fixture.write("frontend/build/generated.ts", "export const generated = 2")
    val third = fixture.runner(":frontend:eslintCheck").build()
    assertThat(third.task(":frontend:eslintCheck")?.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)
  }

  @Test
  fun `honours the include and exclude patterns of a tool`() {
    val fixture =
      workspaceWithFrontend(
        packageBuildScript =
          """
          eslint {
            additionalIncludes("sources/**")
            excludes("sources/generated/**")
          }
          """
      )
    fixture.write("frontend/sources/app.ts", "export const app = 1")
    fixture.write("frontend/sources/generated/api.ts", "export const api = 1")

    fixture.runner(":frontend:eslintCheck").build()
    val second = fixture.runner(":frontend:eslintCheck").build()
    assertThat(second.task(":frontend:eslintCheck")?.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)

    // An excluded file is not an input, so touching it must not invalidate the task.
    fixture.write("frontend/sources/generated/api.ts", "export const api = 2")
    val third = fixture.runner(":frontend:eslintCheck").build()
    assertThat(third.task(":frontend:eslintCheck")?.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)

    // An included file is.
    fixture.write("frontend/sources/app.ts", "export const app = 2")
    val fourth = fixture.runner(":frontend:eslintCheck").build()
    assertThat(fourth.task(":frontend:eslintCheck")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
  }

  @Test
  fun `replaces the default patterns with the configured includes`() {
    val fixture =
      workspaceWithFrontend(
        packageBuildScript =
          """
          eslint { includes = listOf("sources/**/*.ts") }
          """
      )
    fixture.write("frontend/sources/app.ts", "export const app = 1")

    fixture.runner(":frontend:eslintCheck").build()

    assertThat(fixture.stub.invocations().map { it.arguments })
      .contains(listOf("exec", "eslint", "sources/app.ts", "--max-warnings=0", "--no-warn-ignored"))

    // main.ts matches a default pattern, which the configured includes replaced.
    fixture.write("frontend/main.ts", "export const main = 2")
    val second = fixture.runner(":frontend:eslintCheck").build()
    assertThat(second.task(":frontend:eslintCheck")?.outcome).isEqualTo(TaskOutcome.UP_TO_DATE)
  }

  @Test
  fun `skips a tool whose patterns match no file`() {
    val fixture =
      workspaceWithFrontend(
        packageBuildScript =
          """
          eslint { includes = listOf("sources/**/*.ts") }
          """
      )

    val result = fixture.runner(":frontend:eslintCheck").build()

    // Invoking eslint without any file operand would fail instead of doing nothing.
    assertThat(result.task(":frontend:eslintCheck")?.outcome).isEqualTo(TaskOutcome.SKIPPED)
    assertThat(fixture.stub.invocations()).noneSatisfy { invocation ->
      assertThat(invocation.arguments).contains("eslint")
    }
  }

  @Test
  fun `supports custom pnpm exec and pnpm run tasks`() {
    val fixture =
      workspaceWithFrontend(
        packageBuildScript =
          """
          import de.cronn.pnpm.task.PnpmExecTask
          import de.cronn.pnpm.task.PnpmRunTask

          tasks.register<PnpmExecTask>("generateApiClients") {
            command = "openapi-ts"
            arguments.add("--dry-run")
          }

          tasks.register<PnpmRunTask>("buildFrontend") { script = "build" }
          """
      )

    fixture.runner(":frontend:generateApiClients", ":frontend:buildFrontend").build()

    assertThat(fixture.stub.invocations().map { it.arguments })
      .contains(listOf("exec", "openapi-ts", "--dry-run"), listOf("run", "build"))
  }

  @Test
  fun `reuses the configuration cache across runs`() {
    val fixture = workspaceWithFrontend()

    fixture.runner(":frontend:check").build()
    val second = fixture.runner(":frontend:check").build()

    assertThat(second.output).contains("Configuration cache entry reused")
  }

  @Test
  fun `does not register a tool task when the project is not configured for it`() {
    val fixture = workspaceWithFrontend()
    fixture.directory("frontend/eslint.config.ts").delete()

    val result = fixture.runner(":frontend:check").build()

    assertThat(result.task(":frontend:eslintCheck")).isNull()
    assertThat(result.task(":frontend:prettierCheck")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
    assertThat(fixture.stub.invocations()).noneSatisfy { invocation ->
      assertThat(invocation.arguments).contains("eslint")
    }
  }

  @Test
  fun `adding a tool config file invalidates the configuration cache`() {
    val fixture = workspaceWithFrontend()
    val config = fixture.directory("frontend/eslint.config.ts")
    config.delete()

    fixture.runner(":frontend:check").build()
    val reused = fixture.runner(":frontend:check").build()
    assertThat(reused.output).contains("Configuration cache entry reused")

    config.writeText("export default []\n")
    val afterAdding = fixture.runner(":frontend:check").build()

    // The existence check of the config file is a tracked configuration input, so the tool is
    // picked up without having to discard the cache by hand.
    assertThat(afterAdding.output).contains("Configuration cache entry stored")
    assertThat(afterAdding.task(":frontend:eslintCheck")?.outcome).isEqualTo(TaskOutcome.SUCCESS)
  }

  private fun workspaceWithFrontend(packageBuildScript: String = ""): GradleProjectFixture {
    val fixture = GradleProjectFixture(projectDirectory)
    fixture.writeWorkspace(packages = listOf("frontend"), packageBuildScript = packageBuildScript)
    fixture.write("frontend/main.ts", "export const main = 1")
    return fixture
  }

  private companion object {
    /** The files of the `frontend` package that match the default patterns of each tool. */
    val ESLINT_SOURCES: Array<String> = arrayOf("eslint.config.ts", "main.ts", "prettier.config.ts")
    val PRETTIER_SOURCES: Array<String> =
      arrayOf("eslint.config.ts", "main.ts", "package.json", "prettier.config.ts", "tsconfig.json")
  }
}
