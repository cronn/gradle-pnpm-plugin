package de.cronn.pnpm

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
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
   * The `package.json` the pnpm [version] is read from. Defaults to the `package.json` in the
   * directory of the workspace root.
   */
  public abstract val packageJson: RegularFileProperty

  /** The pnpm version to use. Defaults to `devEngines.packageManager.version` of [packageJson]. */
  public abstract val version: Property<String>

  /**
   * Directory a downloaded pnpm distribution is installed into. Defaults to
   * `<workspaceRootDir>/.gradle/pnpm/<version>`.
   */
  public abstract val installDirectory: DirectoryProperty

  /**
   * Base URL pnpm release archives are downloaded from. Defaults to
   * `https://github.com/pnpm/pnpm/releases/download`.
   */
  public abstract val downloadBaseUrl: Property<String>

  /** URL of the pnpm release archive. Defaults to a URL derived from [downloadBaseUrl]. */
  public abstract val archiveUrl: Property<String>

  /** Expected SHA-256 checksum of the pnpm release archive. Not verified when absent. */
  public abstract val archiveSha256: Property<String>

  /**
   * The pnpm executable to use. When set, no pnpm is downloaded and the `PATH` is not consulted.
   */
  public abstract val executable: Property<String>

  /**
   * Whether a pnpm found on the `PATH` is reused when its version matches [version]. Defaults to
   * `true`.
   *
   * Reusing a matching pnpm avoids a download, at the cost of one `pnpm --version` call per build,
   * which is a configuration cache input. Set to `false` for a fully hermetic build that always
   * uses the pnpm version pinned in `package.json`.
   */
  public abstract val preferPnpmOnPath: Property<Boolean>

  /**
   * Path of the task that provisions pnpm. Defaults to the `pnpmSetup` task of the workspace root,
   * for example `:pnpmSetup` or `:frontend:pnpmSetup`.
   */
  public abstract val setupTaskPath: Property<String>

  /**
   * Path of the task that installs the workspace dependencies. Defaults to the `pnpmInstall` task
   * of the workspace root, for example `:pnpmInstall` or `:frontend:pnpmInstall`.
   */
  public abstract val installTaskPath: Property<String>
}
