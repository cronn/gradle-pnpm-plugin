package de.cronn.pnpm.task

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.work.DisableCachingByDefault

/**
 * Downloads the browsers Playwright drives, through `pnpm exec playwright install`.
 *
 * The browsers land in a cache outside the project -- `~/.cache/ms-playwright` and its equivalents
 * -- which is nothing Gradle can compare, so the task declares what decides the download instead:
 * the [lockfile] that pins the Playwright version and the [browsers] asked for. A [stampFile] gives
 * it the output that makes it skippable, the way `pnpmInstall` does it.
 */
@DisableCachingByDefault(
  because =
    "Downloads browsers into a cache outside the project; nothing worth transporting through a " +
      "build cache."
)
public abstract class PlaywrightInstallTask : PnpmExecTask() {

  /** The browsers to download. Empty installs the ones the Playwright configuration asks for. */
  @get:Input public abstract val browsers: ListProperty<String>

  /** Whether the system libraries the browsers need are installed as well, as `--with-deps`. */
  @get:Input public abstract val withDependencies: Property<Boolean>

  /**
   * The lockfile of the workspace, which pins the Playwright version and so decides which browsers
   * are downloaded. Absent for a project that is no part of a pnpm workspace.
   */
  @get:InputFile
  @get:Optional
  @get:PathSensitive(PathSensitivity.NONE)
  public abstract val lockfile: RegularFileProperty

  /**
   * Records that the browsers were installed, so the task has an output to be up to date about. The
   * plugin writes it in an action added to every task of this type.
   */
  @get:OutputFile public abstract val stampFile: RegularFileProperty

  init {
    command.convention("playwright")
    withDependencies.convention(false)
  }

  override fun commandArguments(): List<String> = buildList {
    add("install")
    if (withDependencies.get()) add("--with-deps")
    addAll(browsers.get())
    addAll(arguments.get())
  }
}
