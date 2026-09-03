package de.cronn.pnpm.internal

import groovy.json.JsonSlurper
import org.gradle.api.GradleException

/** Reads the pnpm version pinned in `devEngines.packageManager` of a `package.json`. */
internal object PackageJson {

  /**
   * A fixed semantic version, optionally with a pre-release and build metadata. The plugin
   * downloads exactly this version, so ranges such as `^11.0.0` or `11.x` cannot be resolved.
   */
  private val FIXED_VERSION =
    Regex(
      """\d+\.\d+\.\d+(?:-[0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*)?(?:\+[0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*)?"""
    )

  @Suppress("UNCHECKED_CAST")
  fun pnpmVersion(content: String, description: String): String {
    val root =
      try {
        JsonSlurper().parseText(content) as? Map<String, Any>
      } catch (e: RuntimeException) {
        throw GradleException("Failed to parse $description", e)
      } ?: throw GradleException("Failed to parse $description")

    val packageManager =
      (root["devEngines"] as? Map<String, Any>)?.get("packageManager") as? Map<String, Any>
        ?: throw GradleException("Missing 'devEngines.packageManager' in $description")

    val name = packageManager["name"]
    if (name != "pnpm") {
      throw GradleException(
        "Expected 'devEngines.packageManager.name' to be 'pnpm' but was '$name' in $description"
      )
    }

    val version =
      packageManager["version"] as? String
        ?: throw GradleException("Missing 'devEngines.packageManager.version' in $description")

    if (!FIXED_VERSION.matches(version)) {
      throw GradleException(
        "Expected 'devEngines.packageManager.version' to be a fixed version but was '$version' " +
          "in $description. Version ranges are not supported."
      )
    }

    return version
  }
}
