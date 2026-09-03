package de.cronn.pnpm.internal

import java.io.File
import java.util.Locale
import org.gradle.api.GradleException

/**
 * The pnpm release variant that matches a given operating system and CPU architecture.
 *
 * The operating system and architecture are passed in explicitly instead of being read from system
 * properties inside this class, so that the mapping can be tested for every supported platform on a
 * single machine.
 */
internal class PnpmPlatform(osName: String, osArch: String) {

  private val family = familyOf(osName)

  private val architecture = architectureOf(osArch)

  val isWindows: Boolean
    get() = family == Family.WINDOWS

  /** Platform part of a pnpm release asset name, for example `linux-arm64`. */
  val identifier: String = "${family.identifier}-$architecture"

  /** File extension of the pnpm release asset for this platform. */
  val archiveExtension: String = if (isWindows) "zip" else "tar.gz"

  /** Name of the pnpm executable inside an extracted pnpm release archive. */
  val executableName: String = if (isWindows) "pnpm.exe" else "pnpm"

  /** All file names a pnpm installation may use on this platform when looked up on the `PATH`. */
  val executableNamesOnPath: List<String> =
    if (isWindows) listOf("pnpm.exe", "pnpm.cmd", "pnpm.bat") else listOf("pnpm")

  /**
   * Looks up a pnpm executable on the given `PATH`. Scanning the `PATH` instead of simply executing
   * `pnpm` avoids an unrecoverable process start failure when pnpm is not installed at all.
   *
   * On Windows the executable bit is meaningless -- almost every readable file reports
   * `canExecute() == true` -- so only the well-known file names are used to identify a candidate.
   */
  fun findPnpmOnPath(path: String?): File? =
    path
      .orEmpty()
      .split(File.pathSeparator)
      .filter { it.isNotBlank() }
      .asSequence()
      .flatMap { directory -> executableNamesOnPath.asSequence().map { File(directory, it) } }
      .firstOrNull { it.isFile && (isWindows || it.canExecute()) }

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

    /** URL of the self-contained pnpm distribution for [platform]. */
    fun archiveUrl(version: String, platform: PnpmPlatform): String =
      "$DOWNLOAD_BASE_URL/v$version/pnpm-${platform.identifier}.${platform.archiveExtension}"

    private const val DOWNLOAD_BASE_URL = "https://github.com/pnpm/pnpm/releases/download"
  }
}
