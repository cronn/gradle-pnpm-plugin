package de.cronn.pnpm.internal

import groovy.json.JsonException
import groovy.json.JsonSlurper

/**
 * The pnpm version a `package.json` declares in `devEngines.packageManager`.
 *
 * Only a fixed version can be bootstrapped: resolving a SemVer range would take a registry lookup,
 * which is exactly what pnpm itself does once it runs. Everything else is left to the version
 * configured in the `pnpm` extension.
 */
internal sealed interface PackageJsonPnpmVersion {

  /** A fixed version, which is the one the plugin bootstraps. */
  data class Fixed(val version: String) : PackageJsonPnpmVersion

  /** A SemVer range, or anything else that is not a fixed version. */
  data class Range(val spec: String) : PackageJsonPnpmVersion

  /** A fixed version older than any the plugin can provision. */
  data class Unsupported(val version: String) : PackageJsonPnpmVersion

  /** No pnpm version is declared, or the `package.json` cannot be read. */
  data class Absent(val reason: String) : PackageJsonPnpmVersion

  companion object {

    /** Releases before this major version ship assets that the plugin cannot resolve. */
    private const val MINIMUM_MAJOR_VERSION = 11

    private val FIXED_VERSION = Regex("""(\d+)\.\d+\.\d+(-[0-9A-Za-z.-]+)?""")

    fun parse(packageJson: String): PackageJsonPnpmVersion {
      val root =
        try {
          JsonSlurper().parseText(packageJson)
        } catch (e: JsonException) {
          return Absent("it is not valid JSON (${e.message})")
        } catch (e: IllegalArgumentException) {
          return Absent("it is empty")
        }

      val packageManager =
        ((root as? Map<*, *>)?.get("devEngines") as? Map<*, *>)?.get(
          "packageManager",
        )
      // The field is either a single entry or a list of alternatives.
      val entries =
        when (packageManager) {
          is Map<*, *> -> listOf(packageManager)
          is List<*> -> packageManager.filterIsInstance<Map<*, *>>()
          else -> emptyList()
        }
      val pnpm =
        entries.firstOrNull { it["name"] == "pnpm" }
          ?: return Absent("it declares no pnpm in devEngines.packageManager")
      val version =
        (pnpm["version"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
          ?: return Absent("its pnpm entry in devEngines.packageManager has no version")

      val match = FIXED_VERSION.matchEntire(version) ?: return Range(version)
      val major = match.groupValues[1].toIntOrNull() ?: Int.MAX_VALUE
      return if (major < MINIMUM_MAJOR_VERSION) Unsupported(version) else Fixed(version)
    }
  }
}
