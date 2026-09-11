package de.cronn.pnpm.internal.check

import de.cronn.pnpm.TypescriptExtension
import de.cronn.pnpm.task.TypescriptTask
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider

/** The TypeScript tasks of a pnpm package. */
internal class TypescriptTasks(target: Project, extension: TypescriptExtension) :
  CheckTasks<TypescriptTask>(target, extension, TypescriptTask::class.java, INCLUDES) {

  override fun registerCheckTask(): TaskProvider<TypescriptTask> =
    registerTask(
      name = "compileTypescript",
      description = "Checks the TypeScript sources with tsc",
    )

  companion object {
    val INCLUDES: List<String> = listOf(*BASE_INCLUDES)
  }
}
