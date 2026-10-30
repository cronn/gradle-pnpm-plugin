package de.cronn.pnpm

import de.cronn.pnpm.task.CommandLine
import java.io.File
import org.assertj.core.api.Assertions.assertThat
import org.gradle.api.model.ObjectFactory
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class CommandLineTest {

  private val objects: ObjectFactory = ProjectBuilder.builder().build().objects

  @Test
  fun `puts the head first and the tail last, whatever the order they are added in`() {
    val commandLine = CommandLine()
    commandLine.tail(listOf("--extra"))
    commandLine.add("--first")
    commandLine.head("exec", "eslint")
    commandLine.add("--second")

    assertThat(commandLine.build())
      .containsExactly("exec", "eslint", "--first", "--second", "--extra")
  }

  @Test
  fun `adds a flag only when it is enabled`() {
    val commandLine = CommandLine()
    commandLine.flag("--on", objects.property(Boolean::class.java).value(true))
    commandLine.flag("--off", objects.property(Boolean::class.java).value(false))
    commandLine.flag("--unset", objects.property(Boolean::class.java))

    assertThat(commandLine.build()).containsExactly("--on")
  }

  @Test
  fun `adds an option separate from or joined to its value`() {
    val commandLine = CommandLine()
    commandLine.option("--max-warnings", objects.property(Int::class.java).value(0))
    commandLine.option("--bail", objects.property(Int::class.java).value(1), joined = true)
    commandLine.option("--unset", objects.property(String::class.java))

    assertThat(commandLine.build()).containsExactly("--max-warnings", "0", "--bail=1")
  }

  @Test
  fun `repeats an option for each of its values`() {
    val commandLine = CommandLine()
    val rules = objects.listProperty(String::class.java).value(listOf("a", "b"))
    commandLine.repeated("--suppress-rule", rules)
    commandLine.repeated("--project", rules, joined = true)

    assertThat(commandLine.build())
      .containsExactly("--suppress-rule", "a", "--suppress-rule", "b", "--project=a", "--project=b")
  }

  @Test
  fun `passes a file location as its absolute path`(@TempDir directory: File) {
    val file = File(directory, "eslint.config.js")
    val commandLine = CommandLine()
    commandLine.head("--config", file)
    commandLine.option("--cache-location", objects.fileProperty().fileValue(file))

    assertThat(commandLine.build())
      .containsExactly("--config", file.absolutePath, "--cache-location", file.absolutePath)
  }
}
