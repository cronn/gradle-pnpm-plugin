package de.cronn.pnpm

import de.cronn.pnpm.internal.PackageJson
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.gradle.api.GradleException
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class PackageJsonTest {

  @Test
  fun `reads the pinned pnpm version`() {
    val content =
      """
      {
        "name": "root",
        "private": true,
        "devEngines": {
          "packageManager": { "name": "pnpm", "version": "11.23.0", "onFail": "download" },
          "runtime": { "name": "node", "version": "24.19.0" }
        }
      }
      """
        .trimIndent()

    assertThat(PackageJson.pnpmVersion(content, ORIGIN)).isEqualTo("11.23.0")
  }

  @Test
  fun `fails when devEngines is missing`() {
    assertThatThrownBy { PackageJson.pnpmVersion("""{ "name": "root" }""", ORIGIN) }
      .isInstanceOf(GradleException::class.java)
      .hasMessage("Missing 'devEngines.packageManager' in $ORIGIN")
  }

  @Test
  fun `fails when packageManager is missing`() {
    val content = """{ "devEngines": { "runtime": { "name": "node" } } }"""

    assertThatThrownBy { PackageJson.pnpmVersion(content, ORIGIN) }
      .isInstanceOf(GradleException::class.java)
      .hasMessage("Missing 'devEngines.packageManager' in $ORIGIN")
  }

  @Test
  fun `fails when another package manager is pinned`() {
    val content = """{ "devEngines": { "packageManager": { "name": "yarn", "version": "4" } } }"""

    assertThatThrownBy { PackageJson.pnpmVersion(content, ORIGIN) }
      .isInstanceOf(GradleException::class.java)
      .hasMessage(
        "Expected 'devEngines.packageManager.name' to be 'pnpm' but was 'yarn' in $ORIGIN"
      )
  }

  @Test
  fun `fails when the version is missing`() {
    val content = """{ "devEngines": { "packageManager": { "name": "pnpm" } } }"""

    assertThatThrownBy { PackageJson.pnpmVersion(content, ORIGIN) }
      .isInstanceOf(GradleException::class.java)
      .hasMessage("Missing 'devEngines.packageManager.version' in $ORIGIN")
  }

  @Test
  fun `fails when the version is not a string`() {
    val content = """{ "devEngines": { "packageManager": { "name": "pnpm", "version": 11 } } }"""

    assertThatThrownBy { PackageJson.pnpmVersion(content, ORIGIN) }
      .isInstanceOf(GradleException::class.java)
      .hasMessage("Missing 'devEngines.packageManager.version' in $ORIGIN")
  }

  @ParameterizedTest
  @ValueSource(strings = ["11.23.0", "11.23.0-alpha.1", "11.23.0+build.5", "0.0.1"])
  fun `accepts fixed versions`(version: String) {
    val content =
      """{ "devEngines": { "packageManager": { "name": "pnpm", "version": "$version" } } }"""

    assertThat(PackageJson.pnpmVersion(content, ORIGIN)).isEqualTo(version)
  }

  @ParameterizedTest
  @ValueSource(
    strings =
      [
        "^11.23.0",
        "~11.23.0",
        ">=11.23.0",
        "11.23.x",
        "11.23",
        "11",
        "*",
        "latest",
        "11.23.0 || 12.0.0",
        " 11.23.0",
      ]
  )
  fun `fails when the version is not fixed`(version: String) {
    val content =
      """{ "devEngines": { "packageManager": { "name": "pnpm", "version": "$version" } } }"""

    assertThatThrownBy { PackageJson.pnpmVersion(content, ORIGIN) }
      .isInstanceOf(GradleException::class.java)
      .hasMessage(
        "Expected 'devEngines.packageManager.version' to be a fixed version but was " +
          "'$version' in $ORIGIN. Version ranges are not supported."
      )
  }

  @ParameterizedTest
  @ValueSource(strings = ["", "   ", "not json", "{", "[]", "\"a string\""])
  fun `fails for content that is not a json object`(content: String) {
    assertThatThrownBy { PackageJson.pnpmVersion(content, ORIGIN) }
      .isInstanceOf(GradleException::class.java)
      .hasMessageStartingWith("Failed to parse $ORIGIN")
  }

  private companion object {
    const val ORIGIN = "/workspace/package.json"
  }
}
