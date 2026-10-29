package de.cronn.pnpm.fixture

import java.io.File
import java.util.Properties
import org.gradle.testkit.runner.GradleRunner

/** A Gradle build on disk below [rootDirectory], run through TestKit with the plugin under test. */
abstract class TestKitBuild(val rootDirectory: File) {

  fun write(path: String, content: String) {
    ProjectFiles.writeFile(directory(path), content)
  }

  fun directory(path: String): File = File(rootDirectory, path)

  /**
   * A runner for [arguments] with the configuration cache switched on, where every configuration
   * cache problem fails the build instead of producing a warning.
   *
   * Set [injectPluginClasspath] to `false` for a build that gets the plugin through `buildSrc`:
   * injecting it into the outer build's script classpath as well would load the plugin's classes a
   * second time, so the extension a convention plugin created would not be the type the outer build
   * script sees.
   */
  protected fun gradleRunner(
    arguments: List<String>,
    environment: Map<String, String>? = null,
    injectPluginClasspath: Boolean = true,
  ): GradleRunner =
    GradleRunner.create()
      .withProjectDir(rootDirectory)
      .apply { if (injectPluginClasspath) withPluginClasspath() }
      .apply { if (environment != null) withEnvironment(environment) }
      .withArguments(
        arguments +
          listOf(
            "--configuration-cache",
            "-Dorg.gradle.configuration-cache.problems=fail",
            "--stacktrace",
          ),
      )
      .forwardOutput()

  /** Kotlin string literals of the plugin classpath, for an `implementation(files(...))` call. */
  protected fun pluginClasspathLiterals(): String =
    pluginClasspath().joinToString(", ") { quoted(it) }

  /** Kotlin string literal for [file], safe on Windows where paths contain backslashes. */
  protected fun quoted(file: File): String = "\"${file.invariantSeparatorsPath}\""

  /**
   * The classpath of the plugin under test, read from the metadata the `java-gradle-plugin` plugin
   * generates and puts on the runtime classpath of every TestKit source set.
   */
  private fun pluginClasspath(): List<File> {
    val stream =
      checkNotNull(javaClass.classLoader.getResourceAsStream(PLUGIN_METADATA)) {
        "$PLUGIN_METADATA is not on the classpath"
      }
    val properties = Properties().apply { stream.use { load(it) } }
    // Properties.load has already unescaped the entry, so it splits on the plain path separator.
    return checkNotNull(properties.getProperty("implementation-classpath")) {
        "$PLUGIN_METADATA declares no implementation-classpath"
      }
      .split(File.pathSeparator)
      .map(::File)
  }

  private companion object {
    const val PLUGIN_METADATA = "plugin-under-test-metadata.properties"
  }
}
