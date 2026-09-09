# Changelog

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
