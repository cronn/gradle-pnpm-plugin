package de.cronn.pnpm.internal

import groovy.json.JsonSlurper
import org.gradle.api.GradleException

/** Reads the pnpm version pinned in `devEngines.packageManager` of a `package.json`. */
internal object PackageJson {

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

    return packageManager["version"] as? String
      ?: throw GradleException("Missing 'devEngines.packageManager.version' in $description")
  }
}
