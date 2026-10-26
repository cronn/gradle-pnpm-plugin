package de.cronn.pnpm

import de.cronn.pnpm.internal.PackageJsonPnpmVersion
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class PackageJsonPnpmVersionTest {

  @ParameterizedTest
  @ValueSource(strings = ["11.28.5", "12.0.0-rc.1", "11.0.0-alpha.2-beta"])
  fun `accepts a fixed version`(version: String) {
    assertThat(PackageJsonPnpmVersion.parse(packageJson(version)))
      .isEqualTo(PackageJsonPnpmVersion.Fixed(version))
  }

  @ParameterizedTest
  @ValueSource(strings = ["^11.28.5", "~11.28.5", ">=11.0.0", "11.x", "11", "latest", "=11.28.5"])
  fun `treats anything but a fixed version as a range`(spec: String) {
    assertThat(PackageJsonPnpmVersion.parse(packageJson(spec)))
      .isEqualTo(PackageJsonPnpmVersion.Range(spec))
  }

  @Test
  fun `rejects a fixed version the plugin cannot provision`() {
    assertThat(PackageJsonPnpmVersion.parse(packageJson("10.33.0")))
      .isEqualTo(PackageJsonPnpmVersion.Unsupported("10.33.0"))
  }

  @Test
  fun `picks the pnpm entry from a list of package managers`() {
    val packageJson =
      """
      {
        "devEngines": {
          "packageManager": [
            { "name": "npm", "version": "11.6.0" },
            { "name": "pnpm", "version": "11.28.5" }
          ]
        }
      }
      """

    assertThat(PackageJsonPnpmVersion.parse(packageJson))
      .isEqualTo(PackageJsonPnpmVersion.Fixed("11.28.5"))
  }

  @Test
  fun `ignores another package manager`() {
    val packageJson =
      """{ "devEngines": { "packageManager": { "name": "npm", "version": "11.6.0" } } }"""

    assertThat(PackageJsonPnpmVersion.parse(packageJson))
      .isInstanceOf(PackageJsonPnpmVersion.Absent::class.java)
  }

  @Test
  fun `finds nothing without devEngines`() {
    assertThat(PackageJsonPnpmVersion.parse("""{ "name": "root" }"""))
      .isInstanceOf(PackageJsonPnpmVersion.Absent::class.java)
  }

  @Test
  fun `finds nothing in a pnpm entry without a version`() {
    val packageJson = """{ "devEngines": { "packageManager": { "name": "pnpm" } } }"""

    assertThat(PackageJsonPnpmVersion.parse(packageJson))
      .isInstanceOf(PackageJsonPnpmVersion.Absent::class.java)
  }

  @ParameterizedTest
  @ValueSource(strings = ["", "{", "[1, 2", "not json"])
  fun `finds nothing in a malformed package json`(packageJson: String) {
    assertThat(PackageJsonPnpmVersion.parse(packageJson))
      .isInstanceOf(PackageJsonPnpmVersion.Absent::class.java)
  }

  private fun packageJson(version: String): String =
    """
    {
      "devEngines": {
        "packageManager": { "name": "pnpm", "version": "$version", "onFail": "download" }
      }
    }
    """
}
