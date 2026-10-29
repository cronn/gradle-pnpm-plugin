package de.cronn.pnpm.fixture

import de.cronn.pnpm.internal.PnpmPlatform
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream

/**
 * Creates a local pnpm "release", laid out the way the pnpm repository expects it, so that
 * [de.cronn.pnpm.internal.task.PnpmSetupTask] resolves a fixture instead of a real pnpm
 * distribution.
 */
object PnpmArchiveFixture {

  private val platform = PnpmPlatform.current()

  /** Name of the pnpm executable in a release for this platform. */
  val executableName: String = platform.executableName

  /** Name of the release asset for this platform, for example `pnpm-linux-x64.tar.gz`. */
  val assetName: String = "pnpm-${platform.identifier}.${platform.archiveExtension}"

  /**
   * Writes the release of [version] into [directory], under the `v<version>/<asset>` path the Ivy
   * pattern of the pnpm repository resolves against.
   *
   * @param entries file name to content; defaults to a recording pnpm stub.
   * @return the URL of [directory], to be used as the URL of the pnpm repository.
   */
  fun writeRelease(
    directory: File,
    version: String,
    entries: Map<String, String> = defaultEntries(),
  ): String {
    val releaseDirectory = File(directory, "v$version").apply { mkdirs() }
    val archive = File(releaseDirectory, assetName)
    if (platform.isWindows) writeZip(archive, entries) else writeTarGz(archive, entries)
    return directory.toURI().toString()
  }

  /** A pnpm stub that logs its arguments next to itself, mirroring [PnpmStub]. */
  fun defaultEntries(): Map<String, String> =
    mapOf(
      "pnpm" to
        """
        #!/bin/sh
        echo "installed pnpm called with: ${'$'}@"
        """
          .trimIndent() + "\n",
      "pnpm.exe" to "not a real executable\n",
    )

  private fun writeTarGz(archive: File, entries: Map<String, String>) {
    TarArchiveOutputStream(GzipCompressorOutputStream(archive.outputStream().buffered())).use {
      output ->
      entries.forEach { (name, content) ->
        val bytes = content.toByteArray()
        val entry = TarArchiveEntry(name)
        entry.size = bytes.size.toLong()
        entry.mode = EXECUTABLE_MODE
        output.putArchiveEntry(entry)
        output.write(bytes)
        output.closeArchiveEntry()
      }
    }
  }

  private fun writeZip(archive: File, entries: Map<String, String>) {
    ZipOutputStream(archive.outputStream().buffered()).use { output ->
      entries.forEach { (name, content) ->
        output.putNextEntry(ZipEntry(name))
        output.write(content.toByteArray())
        output.closeEntry()
      }
    }
  }

  private const val EXECUTABLE_MODE = 0b111_101_101
}
