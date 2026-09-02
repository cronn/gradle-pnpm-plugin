package de.cronn.pnpm.task

import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.work.DisableCachingByDefault

/** Runs `pnpm run <script>`, which executes a script declared in a `package.json`. */
@DisableCachingByDefault(
  because =
    "Runs an arbitrary package.json script; its effects are not fully described by declared outputs."
)
public abstract class PnpmRunTask : PnpmTask() {

  /** The `package.json` script to run, for example `build`. */
  @get:Input public abstract val script: Property<String>

  override fun buildArguments(): List<String> = listOf("run", script.get()) + arguments.get()
}
