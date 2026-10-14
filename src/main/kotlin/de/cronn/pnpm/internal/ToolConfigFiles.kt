package de.cronn.pnpm.internal

import java.io.File
import org.gradle.api.Project
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile

/**
 * The configuration files that mark a Node tool as being used by a project.
 *
 * The tasks of a tool are registered only when its configuration file is present, so that applying
 * the plugin to a project -- a workspace root in particular -- does not add tasks for tools that
 * project does not use. Only the existence of the files is checked, which Gradle tracks as a
 * configuration cache input, so creating or deleting one invalidates the cached configuration.
 *
 * Each list is in the order the tool itself searches for its configuration, so that the file the
 * plugin hands the tool is the one the tool would have picked on its own.
 */
internal object ToolConfigFiles {

  val TYPESCRIPT: List<String> = listOf("tsconfig.json")

  /** Only the flat config; the legacy `.eslintrc.*` format is deliberately not detected. */
  val ESLINT: List<String> = variants("eslint.config", "js", "mjs", "cjs", "ts", "mts", "cts")

  val PLAYWRIGHT: List<String> =
    variants("playwright.config", "ts", "js", "mts", "mjs", "cts", "cjs")

  /**
   * A dedicated Vitest configuration, or the Vite one it falls back to: Vitest reads
   * `vite.config.*` when no `vitest.config.*` is there, so a Vite project carrying only the former
   * still runs its suite.
   */
  val VITEST: List<String> =
    listOf("vitest.config", "vite.config").flatMap { baseName ->
      variants(baseName, "ts", "mts", "cts", "js", "mjs", "cjs")
    }

  /**
   * The JavaScript configurations interleave: Prettier looks for `.prettierrc.js` before
   * `prettier.config.js`, but for both of those before `.prettierrc.ts`.
   */
  val PRETTIER: List<String> =
    listOf(".prettierrc", ".prettierrc.json", ".prettierrc.yml", ".prettierrc.yaml") +
      listOf("js", "ts", "mjs", "mts", "cjs", "cts").flatMap { extension ->
        listOf(".prettierrc.$extension", "prettier.config.$extension")
      }

  fun anyPresent(project: Project, fileNames: List<String>): Boolean = fileNames.any {
    File(project.projectDir, it).isFile
  }

  /**
   * The first of [fileNames] that exists in [projectDirectory], which is the one the tool is
   * handed. Resolved eagerly, the same as [anyPresent], for the same reason.
   */
  fun firstExisting(projectDirectory: Directory, fileNames: List<String>): RegularFile? =
    fileNames.map { projectDirectory.file(it) }.firstOrNull { it.asFile.isFile }

  private fun variants(baseName: String, vararg extensions: String): List<String> =
    extensions.map { extension ->
      "$baseName.$extension"
    }
}
