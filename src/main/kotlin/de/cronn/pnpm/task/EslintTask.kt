package de.cronn.pnpm.task

import org.gradle.work.DisableCachingByDefault

/** Runs ESLint through `pnpm exec eslint`, over the resolved [sources]. */
@DisableCachingByDefault(
  because = "Runs ESLint; its effects are not fully described by declared outputs."
)
public abstract class EslintTask : PnpmToolTask() {

  init {
    command.convention("eslint")
  }
}
