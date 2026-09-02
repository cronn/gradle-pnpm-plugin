package de.cronn.pnpm

import javax.inject.Inject
import org.gradle.api.Action
import org.gradle.api.model.ObjectFactory
import org.gradle.api.tasks.Nested

/**
 * Configuration of the Node tools of a single workspace package, added by [PnpmPackagePlugin] as
 * the `pnpmPackage` extension.
 */
public abstract class PnpmPackageExtension @Inject constructor(objects: ObjectFactory) {

  /** Configuration of the TypeScript compiler check. */
  @get:Nested
  public val typescript: PnpmToolExtension = objects.newInstance(PnpmToolExtension::class.java)

  /** Configuration of the Prettier check and fix tasks. */
  @get:Nested
  public val prettier: PnpmToolExtension = objects.newInstance(PnpmToolExtension::class.java)

  /** Configuration of the ESLint check and fix tasks. */
  @get:Nested
  public val eslint: PnpmToolExtension = objects.newInstance(PnpmToolExtension::class.java)

  init {
    typescript.enabled.convention(true)
    prettier.enabled.convention(true)
    eslint.enabled.convention(true)
  }

  /** Configures [typescript]. */
  public fun typescript(action: Action<in PnpmToolExtension>) {
    action.execute(typescript)
  }

  /** Configures [prettier]. */
  public fun prettier(action: Action<in PnpmToolExtension>) {
    action.execute(prettier)
  }

  /** Configures [eslint]. */
  public fun eslint(action: Action<in PnpmToolExtension>) {
    action.execute(eslint)
  }
}
