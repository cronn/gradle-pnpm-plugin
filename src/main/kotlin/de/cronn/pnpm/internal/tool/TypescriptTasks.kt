package de.cronn.pnpm.internal.tool

import de.cronn.pnpm.TypescriptExtension
import de.cronn.pnpm.task.TypescriptTask
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider

/** The TypeScript tasks of a pnpm package. */
internal class TypescriptTasks(target: Project, extension: TypescriptExtension) :
  ToolTasks<TypescriptTask>(target, extension, TypescriptTask::class.java, INCLUDES) {

  override fun registerCheckTask(): TaskProvider<TypescriptTask> =
    registerToolTask(
      name = "compileTypescript",
      description = "Checks the TypeScript sources with tsc",
      arguments = listOf("--noEmit"),
    )

  companion object {
    val INCLUDES: List<String> = listOf(*BASE_INCLUDES)
  }
}
