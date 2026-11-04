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

  /**
   * No version the plugin can bootstrap, so it falls back to the version configured in the `pnpm`
   * extension, and the [reason] why.
   */
  data class Fallback(val reason: String) : PackageJsonPnpmVersion

  companion object {

    /** Releases before this major version ship assets that the plugin cannot resolve. */
    private const val MINIMUM_MAJOR_VERSION = 11

    private val FIXED_VERSION = Regex("""(\d+)\.\d+\.\d+(-[0-9A-Za-z.-]+)?""")

    fun parse(packageJson: String): PackageJsonPnpmVersion {
      val root =
        try {
          JsonSlurper().parseText(packageJson)
        } catch (e: JsonException) {
          return Fallback("it is not valid JSON (${e.message})")
        } catch (e: IllegalArgumentException) {
          return Fallback("it is empty")
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
          ?: return Fallback("it declares no pnpm in devEngines.packageManager")
      val version =
        (pnpm["version"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
          ?: return Fallback("its pnpm entry in devEngines.packageManager has no version")

      val match =
        FIXED_VERSION.matchEntire(version)
          ?: return Fallback("its devEngines.packageManager declares the range $version")
      val major = match.groupValues[1].toIntOrNull() ?: Int.MAX_VALUE
      if (major < MINIMUM_MAJOR_VERSION) {
        return Fallback(
          "its devEngines.packageManager declares pnpm $version, which predates pnpm " +
            MINIMUM_MAJOR_VERSION,
        )
      }
      return Fixed(version)
    }
  }
}
