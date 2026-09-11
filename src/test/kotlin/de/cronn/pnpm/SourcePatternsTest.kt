package de.cronn.pnpm

import de.cronn.pnpm.internal.SourcePatterns
import de.cronn.pnpm.internal.check.EslintTasks
import de.cronn.pnpm.internal.check.PrettierTasks
import de.cronn.pnpm.internal.check.TypescriptTasks
import de.cronn.pnpm.internal.test.PlaywrightTasks
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.gradle.api.GradleException
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class SourcePatternsTest {

  @ParameterizedTest
  @ValueSource(
    strings =
      [
        "*.ts",
        "src/**/*.ts",
        "src/**/*.tsx",
        "*.json",
        "*.md",
        "**/*.snap",
        "src/generated/**",
        "types/**",
        "generated.ts",
        "docs/**/*.md",
        // A Windows separator is normalized for the tool rather than rejected.
        "src\\**\\*.ts",
        // Parentheses and dots that are part of a file name, not a pattern construct.
        "src/main (copy).ts",
        "*..ts",
        "a..b/**/*.ts",
        // The Ant matcher reads this as the directory and everything in it, which is the point.
        "src/generated/",
      ]
  )
  fun `accepts a pattern the Ant matcher of Gradle resolves`(pattern: String) {
    assertThat(SourcePatterns.reasonUnresolvable(pattern)).isNull()
  }

  @Test
  fun `accepts every pattern the plugin defaults to`() {
    val defaults =
      TypescriptTasks.INCLUDES +
        PrettierTasks.INCLUDES +
        EslintTasks.INCLUDES +
        PlaywrightTasks.INCLUDES +
        PlaywrightTasks.EXCLUDES

    assertThat(defaults).allSatisfy { pattern ->
      assertThat(SourcePatterns.reasonUnresolvable(pattern)).isNull()
      assertThat(SourcePatterns.reasonUnsupportedByTool(pattern)).isNull()
    }
  }

  @ParameterizedTest
  @CsvSource(
    "'src/**/*.{ts,tsx}', brace expansion",
    "'src/**/*.ts}', brace expansion",
    "'src/**/*.[jt]s', a character class",
    "'src/[abc.ts', a character class",
    "'!(vendor)/**', an extglob",
    "'@(main|index).ts', an extglob",
    "'+(main).ts', an extglob",
    "'*(main).ts', an extglob",
    "'?(main).ts', an extglob",
    "'!src/generated/**', a negation",
    "'/src/**/*.ts', an absolute pattern",
    "'C:/src/**/*.ts', an absolute pattern",
    "'C:\\src\\**\\*.ts', an absolute pattern",
    "'../shared/**/*.ts', leaves the directory",
    "'src/../lib/**/*.ts', leaves the directory",
  )
  fun `rejects a pattern the Ant matcher of Gradle cannot resolve`(
    pattern: String,
    reason: String,
  ) {
    assertThat(SourcePatterns.reasonUnresolvable(pattern)).contains(reason)
  }

  @ParameterizedTest
  @ValueSource(strings = ["src/generated/", "src\\generated\\"])
  fun `rejects a pattern a tool reads differently than Gradle`(pattern: String) {
    assertThat(SourcePatterns.reasonUnsupportedByTool(pattern))
      .contains("a trailing \"/\"")
      .contains("Write \"src/generated/**\"")
  }

  @ParameterizedTest
  @ValueSource(strings = ["*.ts", "src/**/*.ts", "src/generated/**", "src/main (copy).ts"])
  fun `accepts a pattern a tool reads the same way as Gradle`(pattern: String) {
    assertThat(SourcePatterns.reasonUnsupportedByTool(pattern)).isNull()
  }

  @Test
  fun `reports the offending pattern, the property and the task it belongs to`() {
    assertThatThrownBy {
        SourcePatterns.requireResolvable(
          listOf("*.ts", "src/**/*.{ts,tsx}"),
          "includes",
          ":frontend:eslintCheck",
        )
      }
      .isInstanceOf(GradleException::class.java)
      .hasMessageContaining("The includes pattern \"src/**/*.{ts,tsx}\" of :frontend:eslintCheck")
      .hasMessageContaining("brace expansion")
      .hasMessageContaining("Write one pattern per alternative")
      .hasMessageNotContaining("\"*.ts\"")
  }

  @Test
  fun `accepts a list without an unsupported pattern`() {
    SourcePatterns.requireResolvable(
      listOf("*.ts", "src/**/*.ts"),
      "includes",
      ":frontend:eslintFix",
    )
    SourcePatterns.requireSupportedByTool(
      listOf("*.ts", "src/**/*.ts"),
      "includes",
      ":frontend:eslintFix",
    )
  }
}
