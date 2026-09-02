package de.cronn.pnpm

import de.cronn.pnpm.internal.PnpmPlatform
import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.gradle.api.GradleException
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class PnpmPlatformTest {

  @ParameterizedTest
  @CsvSource(
    "Linux, amd64, linux-x64",
    "Linux, x86_64, linux-x64",
    "Linux, aarch64, linux-arm64",
    "Linux, arm64, linux-arm64",
    "Mac OS X, x86_64, darwin-x64",
    "Mac OS X, aarch64, darwin-arm64",
    "Darwin, arm64, darwin-arm64",
    "Windows 11, amd64, win32-x64",
    "Windows Server 2025, aarch64, win32-arm64",
  )
  fun `maps operating system and architecture to the pnpm release identifier`(
    osName: String,
    osArch: String,
    expected: String,
  ) {
    assertThat(PnpmPlatform(osName, osArch).identifier).isEqualTo(expected)
  }

  @ParameterizedTest
  @ValueSource(strings = ["SunOS", "AIX", "FreeBSD", ""])
  fun `rejects unsupported operating systems`(osName: String) {
    assertThatThrownBy { PnpmPlatform(osName, "amd64") }
      .isInstanceOf(GradleException::class.java)
      .hasMessage("Unsupported operating system for pnpm: $osName")
  }

  @ParameterizedTest
  @ValueSource(strings = ["sparc", "ppc64le", "riscv64", ""])
  fun `rejects unsupported architectures`(osArch: String) {
    assertThatThrownBy { PnpmPlatform("Linux", osArch) }
      .isInstanceOf(GradleException::class.java)
      .hasMessage("Unsupported architecture for pnpm: $osArch")
  }

  @Test
  fun `uses a tar archive and an unsuffixed executable on posix platforms`() {
    val platform = PnpmPlatform("Linux", "amd64")

    assertThat(platform.isWindows).isFalse()
    assertThat(platform.archiveExtension).isEqualTo("tar.gz")
    assertThat(platform.executableName).isEqualTo("pnpm")
    assertThat(platform.executableNamesOnPath).containsExactly("pnpm")
  }

  @Test
  fun `uses a zip archive and the windows executable names on windows`() {
    val platform = PnpmPlatform("Windows 11", "amd64")

    assertThat(platform.isWindows).isTrue()
    assertThat(platform.archiveExtension).isEqualTo("zip")
    assertThat(platform.executableName).isEqualTo("pnpm.exe")
    assertThat(platform.executableNamesOnPath).containsExactly("pnpm.exe", "pnpm.cmd", "pnpm.bat")
  }

  @ParameterizedTest
  @CsvSource(
    "Linux, amd64, https://example.test/download/v11.23.0/pnpm-linux-x64.tar.gz",
    "Mac OS X, aarch64, https://example.test/download/v11.23.0/pnpm-darwin-arm64.tar.gz",
    "Windows 11, amd64, https://example.test/download/v11.23.0/pnpm-win32-x64.zip",
  )
  fun `builds the release archive url`(osName: String, osArch: String, expected: String) {
    val url =
      PnpmPlatform.archiveUrl(
        "https://example.test/download",
        "11.23.0",
        PnpmPlatform(osName, osArch),
      )

    assertThat(url).isEqualTo(expected)
  }

  @Test
  fun `tolerates a trailing slash in the download base url`() {
    val url =
      PnpmPlatform.archiveUrl(
        "https://example.test/download/",
        "1.2.3",
        PnpmPlatform("Linux", "amd64"),
      )

    assertThat(url).isEqualTo("https://example.test/download/v1.2.3/pnpm-linux-x64.tar.gz")
  }

  @Test
  @DisabledOnOs(OS.WINDOWS)
  fun `finds an executable pnpm on the path`(@TempDir directory: File) {
    val other = File(directory, "other").apply { mkdirs() }
    val binary = File(directory, "bin").apply { mkdirs() }
    val pnpm = File(binary, "pnpm").apply { writeText("#!/bin/sh\n") }
    pnpm.setExecutable(true)

    val found =
      PnpmPlatform("Linux", "amd64")
        .findPnpmOnPath(listOf(other.path, binary.path).joinToString(File.pathSeparator))

    assertThat(found).isEqualTo(pnpm)
  }

  @Test
  @DisabledOnOs(OS.WINDOWS)
  fun `ignores a pnpm on the path that is not executable`(@TempDir directory: File) {
    val pnpm = File(directory, "pnpm").apply { writeText("#!/bin/sh\n") }
    pnpm.setExecutable(false)

    assertThat(PnpmPlatform("Linux", "amd64").findPnpmOnPath(directory.path)).isNull()
  }

  @Test
  fun `returns no pnpm for an empty or blank path`() {
    val platform = PnpmPlatform("Linux", "amd64")

    assertThat(platform.findPnpmOnPath(null)).isNull()
    assertThat(platform.findPnpmOnPath("")).isNull()
    assertThat(platform.findPnpmOnPath("${File.pathSeparator}${File.pathSeparator}")).isNull()
  }
}
