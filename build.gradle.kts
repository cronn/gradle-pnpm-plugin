import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

// The plugins DSL resolves through the build script classpath, so locking it pins the plugin
// versions and their transitive dependencies as well.
buildscript { configurations.classpath { resolutionStrategy.activateDependencyLocking() } }

plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.plugin.publish)
  alias(libs.plugins.spotless)
}

description = "Gradle plugin that provisions pnpm and integrates pnpm workspaces into Gradle builds"

version = providers.environmentVariable("ARTIFACT_VERSION").getOrElse("0.0.0-SNAPSHOT")

// Pins every transitive dependency in gradle.lockfile so builds stay reproducible. Refresh with
// ./gradlew dependencies --write-locks and ./gradlew buildEnvironment --write-locks.
dependencyLocking { lockAllConfigurations() }

// Java 21 is the lowest supported bytecode level. The build itself runs on a newer JDK, so it
// cross-compiles instead of provisioning a second toolchain. sourceCompatibility and
// targetCompatibility are not redundant here: the Kotlin plugin validates its jvmTarget against the
// JavaCompile task's targetCompatibility.
java {
  sourceCompatibility = JavaVersion.VERSION_21
  targetCompatibility = JavaVersion.VERSION_21
}

kotlin {
  explicitApi()

  compilerOptions {
    jvmTarget = JvmTarget.JVM_21
    // Match the Kotlin runtime embedded in the oldest supported Gradle (9.0 ships stdlib 2.2).
    apiVersion = KotlinVersion.KOTLIN_2_2
    languageVersion = KotlinVersion.KOTLIN_2_2
    freeCompilerArgs.addAll(
      // Without this, javac-visible signatures come from the building JDK, not from Java 21.
      "-Xjdk-release=21",
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
  options.release = 21
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

val functionalTest: SourceSet = sourceSets.create("functionalTest")

configurations[functionalTest.implementationConfigurationName].extendsFrom(
  configurations.testImplementation.get()
)

configurations[functionalTest.runtimeOnlyConfigurationName].extendsFrom(
  configurations.testRuntimeOnly.get()
)

dependencies {
  // Needed to build tar.gz fixtures for the PnpmSetupTask tests; ArchiveOperations only reads.
  functionalTest.implementationConfigurationName(libs.commons.compress)
}

gradlePlugin {
  website = "https://github.com/cronn/gradle-pnpm-plugin"
  vcsUrl = "https://github.com/cronn/gradle-pnpm-plugin.git"

  testSourceSets(functionalTest)

  plugins {
    register("pnpm") {
      id = "de.cronn.gradle-pnpm-plugin"
      implementationClass = "de.cronn.pnpm.PnpmPlugin"
      displayName = "pnpm plugin"
      description =
        "Provisions the pnpm version pinned in package.json and integrates a pnpm workspace into " +
          "the Gradle build. Discovers the workspace root from pnpm-workspace.yaml and adds the " +
          "pnpm lifecycle tasks there, plus TypeScript, Prettier and ESLint tasks to every pnpm " +
          "package"
      tags =
        listOf(
          "pnpm",
          "node",
          "npm",
          "javascript",
          "typescript",
          "eslint",
          "prettier",
          "workspace",
          "monorepo",
        )
    }
  }
}

val functionalTestTask =
  tasks.register<Test>("functionalTest") {
    description = "Runs the Gradle TestKit tests."
    group = LifecycleBasePlugin.VERIFICATION_GROUP
    testClassesDirs = functionalTest.output.classesDirs
    classpath = functionalTest.runtimeClasspath
    // TestKit spawns real Gradle builds, which spend most of their time waiting on I/O.
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
    // Opt-in tier that additionally runs against other Gradle versions; see README.
    systemProperty(
      "pnpm.test.gradleVersions",
      providers.gradleProperty("pnpmTestGradleVersions").getOrElse(""),
    )
    shouldRunAfter(tasks.test)
  }

tasks.withType<Test>().configureEach {
  useJUnitPlatform()
  testLogging { exceptionFormat = TestExceptionFormat.FULL }
}

tasks.check { dependsOn(functionalTestTask) }

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
