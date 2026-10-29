import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.plugin.compatibility.compatibility
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

// The plugins DSL resolves through the build script classpath, so locking it pins the plugin
// versions and their transitive dependencies as well.
buildscript { configurations.classpath { resolutionStrategy.activateDependencyLocking() } }

plugins {
  `java-test-fixtures`
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.plugin.publish)
  alias(libs.plugins.spotless)
}

description = "Gradle plugin that provisions pnpm and integrates pnpm workspaces into Gradle builds"

version = providers.environmentVariable("ARTIFACT_VERSION").getOrElse("0.0.0-SNAPSHOT")

// Pins every transitive dependency in gradle.lockfile so builds stay reproducible. Refresh with
// ./gradlew dependencies --write-locks and ./gradlew buildEnvironment --write-locks.
dependencyLocking { lockAllConfigurations() }

// Java 25 is the lowest supported bytecode level. The build itself may run on a newer JDK, so it
// cross-compiles instead of provisioning a second toolchain. sourceCompatibility and
// targetCompatibility are not redundant here: the Kotlin plugin validates its jvmTarget against the
// JavaCompile task's targetCompatibility.
java {
  sourceCompatibility = JavaVersion.VERSION_25
  targetCompatibility = JavaVersion.VERSION_25
}

kotlin {
  explicitApi()

  compilerOptions {
    jvmTarget = JvmTarget.JVM_25
    // Match the Kotlin runtime embedded in the oldest supported Gradle (9.1 ships stdlib 2.2).
    apiVersion = KotlinVersion.KOTLIN_2_2
    languageVersion = KotlinVersion.KOTLIN_2_2
    freeCompilerArgs.addAll(
      // Without this, javac-visible signatures come from the building JDK, not from Java 25.
      "-Xjdk-release=25",
      // The configuration cache serializes Action/Spec instances (for example the onlyIf spec of
      // pnpmSetup) by reflecting over their fields. Invokedynamic lambdas have no stable class name
      // and cannot be restored, so SAM conversions must produce real classes.
      "-Xsam-conversions=class",
      "-Xjsr305=strict",
      "-java-parameters",
    )
  }
}

tasks.withType<JavaCompile>().configureEach {
  options.release = 25
  options.encoding = "UTF-8"
}

dependencies {
  // Provided by every Gradle distribution, which also shadows it at runtime. Pinned to the stdlib
  // of the oldest supported Gradle so newer stdlib APIs cannot be used by accident.
  compileOnly(libs.kotlin.stdlib)

  // gradleApi() is added to compileOnlyApi by java-gradle-plugin, and the stdlib is compileOnly
  // above, so both have to be put on the test runtime classpath explicitly.
  testImplementation(gradleApi())
  testImplementation(libs.kotlin.stdlib)
  testImplementation(platform(libs.junit.bom))
  testImplementation(libs.junit.jupiter)
  testImplementation(libs.junit.jupiter.params)
  testImplementation(libs.assertj.core)
  testRuntimeOnly(libs.junit.platform.launcher)
}

dependencies {
  // gradleTestKit() is only added to the test source sets of java-gradle-plugin, and the fixtures
  // shared by them build on it.
  testFixturesImplementation(gradleTestKit())
  testFixturesImplementation(libs.kotlin.stdlib)
  testImplementation(testFixtures(project))
}

// The test fixtures are no part of the published plugin.
listOf(configurations.testFixturesApiElements, configurations.testFixturesRuntimeElements).forEach {
  (components["java"] as AdhocComponentWithVariants).withVariantsFromConfiguration(it.get()) {
    skip()
  }
}

/** A TestKit source set whose tests see the internals of the plugin and the shared fixtures. */
fun testKitSourceSet(name: String): SourceSet {
  val sourceSet = sourceSets.create(name)
  configurations[sourceSet.implementationConfigurationName].extendsFrom(
    configurations.testImplementation.get(),
  )
  configurations[sourceSet.runtimeOnlyConfigurationName].extendsFrom(
    configurations.testRuntimeOnly.get(),
  )
  kotlin.target.compilations.named(name) {
    associateWith(kotlin.target.compilations.getByName("main"))
  }
  return sourceSet
}

val functionalTest: SourceSet = testKitSourceSet("functionalTest")

val integrationTest: SourceSet = testKitSourceSet("integrationTest")

dependencies {
  // Needed to build tar.gz fixtures for the PnpmSetupTask tests; ArchiveOperations only reads.
  functionalTest.implementationConfigurationName(libs.commons.compress)
}

gradlePlugin {
  website = "https://github.com/cronn/gradle-pnpm-plugin"
  vcsUrl = "https://github.com/cronn/gradle-pnpm-plugin.git"

  testSourceSets(functionalTest, integrationTest)

  plugins {
    register("pnpm") {
      id = "de.cronn.gradle-pnpm-plugin"
      implementationClass = "de.cronn.pnpm.PnpmPlugin"
      displayName = "pnpm plugin"
      description =
        "A Gradle plugin that provisions pnpm and integrates a pnpm workspace into a Gradle build."
      compatibility {
        features {
          configurationCache = true
        }
      }
      tags =
        listOf(
          "pnpm",
          "node",
          "workspace",
          "monorepo",
        )
    }
  }
}

// A TestKit daemon outlives the build it ran and keeps file handles on the project's .gradle
// directory open. Windows refuses to delete an open file, so deleting the @TempDir of a test fails
// whenever the daemon has not let go yet. The leftovers are temporary directories of a throwaway
// build, so a failure to delete them is logged instead of failing the test.
fun Test.ignoreTempDirDeletionFailures() {
  systemProperty(
    "junit.jupiter.tempdir.deletion.strategy.default",
    "org.junit.jupiter.api.io.TempDirDeletionStrategy${'$'}IgnoreFailures",
  )
}

val functionalTestTask =
  tasks.register<Test>("functionalTest") {
    description = "Runs the Gradle TestKit tests against a pnpm stub."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    testClassesDirs = functionalTest.output.classesDirs
    classpath = functionalTest.runtimeClasspath
    // TestKit spawns real Gradle builds, which spend most of their time waiting on I/O.
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
    ignoreTempDirDeletionFailures()
    shouldRunAfter(tasks.test)
  }

val integrationTestTask =
  tasks.register<Test>("integrationTest") {
    description = "Runs the Gradle TestKit tests against the real pnpm and Node.js."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    testClassesDirs = integrationTest.output.classesDirs
    classpath = integrationTest.runtimeClasspath
    // The workspace every test copies, without what a local pnpm install leaves behind in it.
    val workspaceTemplate = layout.projectDirectory.dir("src/integrationTest/workspace")
    systemProperty("pnpm.test.workspaceTemplate", workspaceTemplate.asFile.absolutePath)
    inputs
      .files(fileTree(workspaceTemplate) { exclude("**/node_modules/**", "**/build/**") })
      .withPropertyName("workspaceTemplate")
      .withPathSensitivity(PathSensitivity.RELATIVE)
    ignoreTempDirDeletionFailures()
    shouldRunAfter(functionalTestTask)
  }

tasks.withType<Test>().configureEach {
  useJUnitPlatform()
  testLogging { exceptionFormat = TestExceptionFormat.FULL }
}

tasks.check { dependsOn(functionalTestTask, integrationTestTask) }

spotless {
  isEnforceCheck = true

  kotlin {
    target("src/*/kotlin/**/*.kt")
    ktfmt().googleStyle()
    trimTrailingWhitespace()
    endWithNewline()
  }

  kotlinGradle {
    target("*.gradle.kts")
    ktfmt().googleStyle()
    trimTrailingWhitespace()
    endWithNewline()
  }

  flexmark {
    target("*.md", "docs/*.md")
    flexmark()
    trimTrailingWhitespace()
    endWithNewline()
  }

  format("misc") {
    target("*.properties", ".gitignore", ".github/**/*.yml", "gradle/*.toml")
    trimTrailingWhitespace()
    endWithNewline()
  }
}

// CAUTION: run ./gradlew wrapper (twice) after changes to this task!
// See https://docs.gradle.org/current/userguide/gradle_wrapper.html#sec:upgrading_wrapper
tasks.wrapper {
  gradleVersion = "9.7.1"
  distributionType = Wrapper.DistributionType.ALL
}
