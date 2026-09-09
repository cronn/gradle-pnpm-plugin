package de.cronn.pnpm

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property

/**
 * Configuration of the pnpm installation, added by [PnpmPlugin] to every project as the `pnpm`
 * extension.
 *
 * [version], [installDirectory] and [executable] describe the one pnpm installation the whole
 * workspace shares, so configure them once, in the build script of the workspace root -- the
 * project whose directory contains the `pnpm-workspace.yaml`. Every package inherits its values
 * from there, and keeps inheriting them however late in the configuration phase the workspace root
 * is configured. Setting one of them on a package overrides it for that project's own pnpm
 * invocations only; pnpm is still provisioned by the workspace root, so overriding
 * [installDirectory] on a package merely points that project at a directory nothing fills.
 *
 * [workspaceRootPath] is the one property that is genuinely per project: it says which project
 * provisions pnpm for this one.
 *
 * The Node tools are configured separately, per project, through the `typescript`, `prettier` and
 * `eslint` extensions.
 */
public abstract class PnpmExtension {

  /**
   * The pnpm version to download when neither [executable] is set nor a pnpm is found on the
   * `PATH`. Defaults to the version pinned by the plugin.
   */
  public abstract val version: Property<String>

  /**
   * Directory a downloaded pnpm distribution is installed into. Defaults to
   * `<workspaceRootDir>/.gradle/pnpm/<version>`.
   */
  public abstract val installDirectory: DirectoryProperty

  /**
   * The pnpm executable to use. When set, no pnpm is downloaded and the `PATH` is not consulted.
   */
  public abstract val executable: Property<String>

  /**
   * Base URL the pnpm distribution archives are downloaded from. Defaults to the pnpm releases on
   * GitHub; set it to an internal mirror of them, or to whatever a proxy serves them under.
   *
   * The plugin registers the repository over this URL in the workspace root. It registers none when
   * the repositories of the build are declared in `settings.gradle.kts`, or when the build already
   * declares a repository named `pnpm` itself.
   */
  public abstract val repositoryUrl: Property<String>

  /**
   * Gradle path of the project that is the pnpm workspace root, for example `:` or `:frontend`.
   * Defaults to the project whose directory contains the `pnpm-workspace.yaml`.
   *
   * The pnpm lifecycle tasks of that project (`pnpmSetup`, `pnpmInstall`) are the tasks every pnpm
   * task of the workspace depends on.
   *
   * Set it to point a project at a workspace root the plugin cannot discover on its own, because it
   * is not one of the project's Gradle ancestors. It has no default in a project that takes no part
   * in the pnpm build, so running a pnpm task there reports the missing workspace root.
   */
  public abstract val workspaceRootPath: Property<String>
}
