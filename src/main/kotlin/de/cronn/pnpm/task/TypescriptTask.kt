package de.cronn.pnpm.task

import org.gradle.work.DisableCachingByDefault

/**
 * Runs the TypeScript compiler through `pnpm exec tsc`.
 *
 * Unlike a [PnpmCheckTask], `tsc` is not handed its sources: it takes the files it compiles from
 * the `tsconfig.json`, and naming them on the command line would make it ignore that file. The
 * [includes] and [excludes] of the task therefore only describe its Gradle inputs, which is what
 * decides when it is up to date -- the same as for a [PnpmTestTask], and why this is a plain
 * [PnpmSourceTask].
 */
@DisableCachingByDefault(
  because = "Runs tsc; its effects are not fully described by declared outputs."
)
public abstract class TypescriptTask : PnpmSourceTask() {

  init {
    command.convention("tsc")
  }
}
