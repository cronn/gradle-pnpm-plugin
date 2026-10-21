package de.cronn.pnpm.internal.check

import de.cronn.pnpm.TypescriptExtension
import de.cronn.pnpm.internal.ToolConfigFiles
import de.cronn.pnpm.task.TypescriptTask
import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider

/** The TypeScript tasks of a pnpm package. */
internal class TypescriptTasks(target: Project, private val typescript: TypescriptExtension) :
  CheckTasks<TypescriptTask>(
    target,
    typescript,
    TypescriptTask::class.java,
    ToolConfigFiles.TYPESCRIPT,
    INCLUDES,
  ) {

  override fun configureTask(task: TypescriptTask) {
    task.build.convention(typescript.build)
    task.noEmit.convention(typescript.noEmit)
    task.outDir.convention(typescript.outDir)
  }

  override fun registerCheckTask(): TaskProvider<TypescriptTask> =
    registerTask(
      name = "compileTypescript",
      description = "Checks the TypeScript sources with tsc",
    )

  companion object {
    val INCLUDES: List<String> = listOf(*BASE_INCLUDES)
  }
}
