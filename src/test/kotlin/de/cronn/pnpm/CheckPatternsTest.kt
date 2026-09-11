package de.cronn.pnpm

import de.cronn.pnpm.internal.check.CheckPatterns
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

class CheckPatternsTest {

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
      ]
  )
  fun `accepts a pattern Gradle and the tool both understand`(pattern: String) {
    assertThat(CheckPatterns.reasonUnsupported(pattern)).isNull()
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
      assertThat(CheckPatterns.reasonUnsupported(pattern)).isNull()
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
    "'src/', a trailing",
    "'src\\', a trailing",
    "'/src/**/*.ts', an absolute pattern",
    "'C:/src/**/*.ts', an absolute pattern",
    "'C:\\src\\**\\*.ts', an absolute pattern",
    "'../shared/**/*.ts', leaves the directory",
    "'src/../lib/**/*.ts', leaves the directory",
  )
  fun `rejects a pattern only one of the two understands`(pattern: String, reason: String) {
    assertThat(CheckPatterns.reasonUnsupported(pattern)).contains(reason)
  }

  @Test
  fun `reports the offending pattern, the property and the task it belongs to`() {
    assertThatThrownBy {
        CheckPatterns.requireSupported(
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
    CheckPatterns.requireSupported(listOf("*.ts", "src/**/*.ts"), "includes", ":frontend:eslintFix")
  }
}
