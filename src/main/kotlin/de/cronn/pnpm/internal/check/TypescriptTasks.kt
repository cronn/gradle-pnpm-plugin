package de.cronn.pnpm.internal.check

import de.cronn.pnpm.TypescriptExtension
import de.cronn.pnpm.internal.ToolConfigFiles
import de.cronn.pnpm.task.TypescriptTask
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider

/** The TypeScript tasks of a pnpm package. */
internal class TypescriptTasks(target: Project, typescript: TypescriptExtension) :
  CheckTasks<TypescriptTask, TypescriptExtension>(
    target,
    typescript,
    TypescriptTask::class.java,
    ToolConfigFiles.TYPESCRIPT,
    INCLUDES,
  ) {

  override fun configureTask(task: TypescriptTask) {
    task.build.convention(extension.build)
    task.noEmit.convention(extension.noEmit)
    task.outDir.convention(extension.outDir)
  }

  override fun registerCheckTask(): TaskProvider<TypescriptTask> =
    registerPredefinedTask(
      name = "compileTypescript",
      description = "Checks the TypeScript sources with tsc",
    )

  companion object {
    val INCLUDES: List<String> = listOf(*BASE_INCLUDES)
  }
}
