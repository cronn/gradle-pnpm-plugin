package de.cronn.pnpm.fixture

import de.cronn.pnpm.internal.PackageJsonPnpmVersion
import de.cronn.pnpm.internal.PnpmPlatform
import java.io.File
import org.gradle.testkit.runner.GradleRunner

/**
 * A copy of the workspace in `src/integrationTest/workspace`, built with the real pnpm the plugin
 * provisions and the Node.js the workspace pins.
 *
 * Nothing is stubbed, so the tests need network access the first time: the pnpm distribution comes
 * from GitHub and Node.js from nodejs.org. Both land in the Gradle and pnpm caches afterwards.
 */
class IntegrationProjectFixture(rootDirectory: File) : TestKitBuild(rootDirectory) {

  init {
    template
      .walkTopDown()
      .onEnter { it.name !in LOCAL_OUTPUTS }
      .filter { it.isFile }
      .forEach { it.copyTo(File(rootDirectory, it.relativeTo(template).path)) }
  }

  /** Runs Gradle in the environment of the test JVM. */
  fun runner(vararg arguments: String): GradleRunner = gradleRunner(arguments.toList())

  /** The pnpm executable the plugin provisions for the version the workspace pins. */
  fun installedPnpm(): File {
    val pinned = PackageJsonPnpmVersion.parse(directory("package.json").readText())
    val version = (pinned as PackageJsonPnpmVersion.Fixed).version
    return directory(".gradle/pnpm/$version/${PnpmPlatform.current().executableName}")
  }

  /** The file of the workspace template at [path], as it is checked in. */
  fun templateFile(path: String): File = File(template, path)

  private companion object {
    val template: File =
      File(
        checkNotNull(System.getProperty("pnpm.test.workspaceTemplate")) {
          "pnpm.test.workspaceTemplate is not set; run the tests through ./gradlew integrationTest"
        },
      )

    /** What running pnpm or Gradle in the template itself leaves behind. */
    val LOCAL_OUTPUTS: Set<String> = setOf("node_modules", "build", ".gradle", "coverage")
  }
}
