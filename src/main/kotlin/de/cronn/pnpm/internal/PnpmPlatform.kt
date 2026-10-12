package de.cronn.pnpm.internal

import java.util.Locale
import org.gradle.api.GradleException

/**
 * The pnpm release variant that matches a given operating system and CPU architecture.
 *
 * The operating system and architecture are passed in explicitly instead of being read from system
 * properties inside this class, so that the mapping can be tested for every supported platform on a
 * single machine.
 */
internal class PnpmPlatform(val osName: String, val osArch: String) {

  private val family = familyOf(osName)

  private val architecture = architectureOf(osArch)

  val isWindows: Boolean
    get() = family == Family.WINDOWS

  /**
   * Platform part of a pnpm release asset name, for example `linux-arm64`. Used as the classifier
   * of the dependency the pnpm distribution is resolved as.
   */
  val identifier: String = "${family.identifier}-$architecture"

  /**
   * File extension of the pnpm release asset for this platform. Used as the extension of the
   * dependency the pnpm distribution is resolved as.
   */
  val archiveExtension: String = if (isWindows) "zip" else "tar.gz"

  /** Name of the pnpm executable inside an extracted pnpm release archive. */
  val executableName: String = if (isWindows) "pnpm.exe" else "pnpm"

  private enum class Family(val identifier: String) {
    LINUX("linux"),
    MAC_OS("darwin"),
    WINDOWS("win32"),
  }

  private fun familyOf(osName: String): Family {
    val normalized = osName.lowercase(Locale.ROOT)
    return when {
      normalized.startsWith("windows") -> Family.WINDOWS
      normalized.startsWith("mac") || normalized.contains("darwin") -> Family.MAC_OS
      normalized.contains("linux") -> Family.LINUX
      else -> throw GradleException("Unsupported operating system for pnpm: $osName")
    }
  }

  private fun architectureOf(osArch: String): String =
    when (osArch.lowercase(Locale.ROOT)) {
      "x86_64",
      "amd64" -> "x64"
      "aarch64",
      "arm64" -> "arm64"
      else -> throw GradleException("Unsupported architecture for pnpm: $osArch")
    }

  internal companion object {
    /** The platform of the JVM running Gradle. */
    fun current(): PnpmPlatform =
      PnpmPlatform(System.getProperty("os.name").orEmpty(), System.getProperty("os.arch").orEmpty())
  }
}
