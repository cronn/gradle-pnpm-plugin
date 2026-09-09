package de.cronn.pnpm

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property

/**
 * Configuration of the pnpm installation, added by [PnpmPlugin] to the pnpm workspace root as the
 * `pnpm` extension.
 *
 * The workspace root is the project whose directory contains the `pnpm-workspace.yaml`. Exactly one
 * of these extensions exists per workspace, and the packages of the workspace read it, so that one
 * pnpm installation is shared by the whole workspace.
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
   * Gradle path of the project that is the pnpm workspace root, for example `:` or `:frontend`.
   * Defaults to the project whose directory contains the `pnpm-workspace.yaml`.
   *
   * The pnpm lifecycle tasks of that project (`pnpmSetup`, `pnpmInstall`) are the tasks every pnpm
   * task of the workspace depends on.
   */
  public abstract val workspaceRootPath: Property<String>
}
