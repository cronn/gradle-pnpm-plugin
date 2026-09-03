package de.cronn.pnpm.fixture

import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream

/**
 * Creates a local pnpm "release" archive, so that [de.cronn.pnpm.task.PnpmSetupTask] can be tested
 * against a `file:` URL instead of downloading a real pnpm distribution.
 */
object PnpmArchiveFixture {

  /**
   * Writes an archive for [version] in the archive format the plugin expects on this platform.
   *
   * @param entries file name to content; defaults to a recording pnpm stub.
   * @return the URL to configure as the `archiveUrl` of the `pnpmSetup` task.
   */
  fun writeRelease(
    directory: File,
    version: String,
    entries: Map<String, String> = defaultEntries(),
  ): String {
    val releaseDirectory = File(directory, "v$version").apply { mkdirs() }
    val archive =
      if (PnpmStub.isWindows) {
        File(releaseDirectory, "pnpm-win32.zip").also { writeZip(it, entries) }
      } else {
        File(releaseDirectory, "pnpm-linux.tar.gz").also { writeTarGz(it, entries) }
      }
    return archive.toURI().toString()
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
