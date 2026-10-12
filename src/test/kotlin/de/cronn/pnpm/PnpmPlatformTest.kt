package de.cronn.pnpm

import de.cronn.pnpm.internal.PnpmPlatform
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.gradle.api.GradleException
import org.junit.jupiter.api.Test
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
  }

  @Test
  fun `uses a zip archive and the windows executable on windows`() {
    val platform = PnpmPlatform("Windows 11", "amd64")

    assertThat(platform.isWindows).isTrue()
    assertThat(platform.archiveExtension).isEqualTo("zip")
    assertThat(platform.executableName).isEqualTo("pnpm.exe")
  }
}
