plugins { id("de.cronn.gradle-pnpm-plugin") }

tasks.register<de.cronn.pnpm.task.NodeTask>("greet") {
  entryPoint = layout.projectDirectory.file("scripts/greet.mjs")
  arguments = listOf("Gradle")
}
