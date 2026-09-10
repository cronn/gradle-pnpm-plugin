# Changelog

## Unreleased

- New `environment` on `PnpmTask`, to pass environment variables to the pnpm process; the entries
  are added to the environment of the build (Closes #23)
- **Breaking:** the tools are invoked with the `includes` and `excludes` patterns instead of the
  files they resolve to, which keeps a large source set from overrunning the command line length
  limit of Windows. The patterns now have to be understood by the tool as well, so they must be
  valid globs, and an exclude naming a directory needs a trailing `/**` (Closes #24)
- The plugin registers the repository serving the pnpm distribution itself, and steps aside when the
  build declares its repositories in `settings.gradle.kts`
- New `pnpm { repositoryUrl }`, to point the repository at a mirror or a proxy
- **Breaking:** removed `RepositoryHandler.pnpm()`; declare the repository with a plain Ivy
  declaration instead, which keeps the plugin off the settings classpath

## 0.1.0

- Support applying the plugin from a convention plugin
- Applying the plugin to a project that holds no pnpm files no longer fails
- A pnpm on `PATH` must not match the version pinned by `version`
- Remove `preferPnpmOnPath`; a pnpm on the `PATH` is always reused, whatever version it is
- Replace `setupTaskPath` and `installTaskPath` with `workspaceRootPath`
- Declare compatibility with configuration cache
- Fix: `pnpmSetup` task is not compatible with configuration cache when `preferPnpmOnPath = true`

## 0.0.2

- Configure DNS entry for Gradle verification

## 0.0.1

- Initial release
