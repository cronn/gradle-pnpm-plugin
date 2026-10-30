package de.cronn.pnpm.fixture

import java.io.File

/** Files and build script snippets that the unit, functional and integration tests all write. */
object ProjectFiles {

  const val PLUGIN_ID: String = "de.cronn.gradle-pnpm-plugin"

  /** The pnpm version the tests configure, distinct from the default version of the plugin. */
  const val PNPM_VERSION: String = "11.23.0"

  /** Writes a `package.json` into [directory] that marks it as a private workspace root. */
  fun writeRootPackageJson(directory: File) {
    writeFile(
      File(directory, "package.json"),
      """
      {
        "name": "root",
        "private": true
      }
      """,
    )
  }

  /** Overwrites the `package.json` in [directory] with one pinning pnpm to [version]. */
  fun writeDevEngines(directory: File, version: String) {
    writeFile(
      File(directory, "package.json"),
      """
      {
        "name": "root",
        "private": true,
        "devEngines": {
          "packageManager": { "name": "pnpm", "version": "$version", "onFail": "download" }
        }
      }
      """,
    )
  }

  /**
   * Writes a config file for TypeScript, ESLint and Prettier into [directory], which is what makes
   * the plugin register the tasks of those tools for a project.
   */
  fun writeCheckConfigs(directory: File) {
    writeFile(File(directory, "tsconfig.json"), "{}")
    writeFile(File(directory, "eslint.config.ts"), "export default []")
    writeFile(File(directory, "prettier.config.ts"), "export default {}")
  }

  /**
   * Writes the `playwright.config.ts` that makes the plugin register the Playwright tasks.
   *
   * Deliberately not part of [writeCheckConfigs]: registering the Playwright tasks everywhere would
   * add them, and their pnpm invocations, to every test that only cares about the source tools.
   */
  fun writePlaywrightConfig(directory: File) {
    writeFile(File(directory, "playwright.config.ts"), "export default { testDir: \"tests\" }")
  }

  /**
   * Writes the `vitest.config.ts` that makes the plugin register the Vitest tasks, deliberately not
   * part of [writeCheckConfigs] for the same reason as [writePlaywrightConfig].
   */
  fun writeVitestConfig(directory: File) {
    writeFile(File(directory, "vitest.config.ts"), "export default {}")
  }

  /** Writes [content] to [file], trimming its indentation and creating its parent directories. */
  fun writeFile(file: File, content: String) {
    file.parentFile.mkdirs()
    file.writeText(content.trimIndent().trim() + "\n")
  }

  /** An Ivy repository laid out like the pnpm releases, over [url]. */
  fun ivyRepository(url: String, name: String? = null): String =
    """
    ivy {
      ${name?.let { "name = \"$it\"" } ?: ""}
      setUrl("$url")
      patternLayout { artifact("v[revision]/[artifact]-[classifier].[ext]") }
      metadataSources { artifact() }
    }
    """
      .trimIndent()

  /**
   * The declaration of the pnpm repository the README documents, over [url]. Builds that declare
   * their repositories themselves write exactly this, in a build script or in the settings script,
   * and it deliberately mentions none of the plugin's classes.
   */
  fun pnpmRepository(url: String): String =
    """
    exclusiveContent {
      forRepository {
        ${ivyRepository(url, name = "pnpm")}
      }
      filter { includeModule("pnpm", "pnpm") }
    }
    """
      .trimIndent()

  /** A `dependencyResolutionManagement` block declaring [repositories]. */
  fun settingsRepositories(
    vararg repositories: String,
    failOnProjectRepositories: Boolean = false,
  ): String =
    """
    dependencyResolutionManagement {
      ${if (failOnProjectRepositories) "repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS" else ""}
      repositories {
        ${repositories.joinToString("\n")}
      }
    }
    """
      .trimIndent()
}
