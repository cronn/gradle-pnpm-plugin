package de.cronn.pnpm.task

import org.gradle.work.DisableCachingByDefault

/** Runs Prettier through `pnpm exec prettier`, over the resolved [sources]. */
@DisableCachingByDefault(
  because = "Runs Prettier; its effects are not fully described by declared outputs."
)
public abstract class PrettierTask : PnpmToolTask() {

  init {
    command.convention("prettier")
  }
}
