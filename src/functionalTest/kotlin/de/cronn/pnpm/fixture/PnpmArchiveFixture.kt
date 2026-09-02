package de.cronn.pnpm.fixture

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream

/**
 * Creates local pnpm "release" archives, so that [de.cronn.pnpm.task.PnpmSetupTask] can be tested
 * against a `file:` URL instead of downloading a real pnpm distribution.
 */
object PnpmArchiveFixture {

  /** Every pnpm release asset name the plugin may ask for. */
  private val PLATFORMS =
    listOf("linux-x64", "linux-arm64", "darwin-x64", "darwin-arm64", "win32-x64", "win32-arm64")

  /**
   * Writes a release directory for [version] containing an archive for every platform, so the test
   * does not have to know which one the plugin resolves.
   *
   * @param entries file name to content; defaults to a recording pnpm stub.
   * @return the base URL to configure as `pnpm.downloadBaseUrl`.
   */
  fun writeRelease(
    directory: File,
    version: String,
    entries: Map<String, String> = defaultEntries(),
  ): String {
    val releaseDirectory = File(directory, "v$version").apply { mkdirs() }
    PLATFORMS.forEach { platform ->
      if (platform.startsWith("win32")) {
        writeZip(File(releaseDirectory, "pnpm-$platform.zip"), entries)
      } else {
        writeTarGz(File(releaseDirectory, "pnpm-$platform.tar.gz"), entries)
      }
    }
    return directory.toURI().toString().removeSuffix("/")
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
