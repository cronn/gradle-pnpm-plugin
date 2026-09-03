package de.cronn.pnpm.internal

import java.io.File
import org.gradle.api.Project

/**
 * The configuration files that mark a Node tool as being used by a project.
 *
 * A tool whose configuration file is present is enabled by default, so that applying the plugin to
 * a project -- a workspace root in particular -- does not add tasks for tools that project does not
 * use. Only the existence of the files is checked, which Gradle tracks as a configuration cache
 * input, so creating or deleting one invalidates the cached configuration.
 */
internal object ToolConfigFiles {

  /** Extensions a JavaScript tool accepts for its flat config file. */
  private val CONFIG_EXTENSIONS = listOf("js", "mjs", "cjs", "ts", "mts", "cts")

  val TYPESCRIPT: List<String> = listOf("tsconfig.json")

  /** Only the flat config; the legacy `.eslintrc.*` format is deliberately not detected. */
  val ESLINT: List<String> = variants("eslint.config")

  val PRETTIER: List<String> =
    variants("prettier.config") +
      listOf(".prettierrc") +
      listOf(".prettierrc.json", ".prettierrc.yaml", ".prettierrc.yml") +
      variants(".prettierrc")

  fun anyPresent(project: Project, fileNames: List<String>): Boolean =
    fileNames.any { File(project.projectDir, it).isFile }

  private fun variants(baseName: String): List<String> =
    CONFIG_EXTENSIONS.map { extension -> "$baseName.$extension" }
}
