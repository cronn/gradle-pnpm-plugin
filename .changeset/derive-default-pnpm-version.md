---
"gradle-pnpm-plugin": minor
---

Bootstrap the pnpm version declared in `devEngines.packageManager` of `package.json`

When the `package.json` of the workspace root declares a fixed version, the plugin downloads exactly that version instead of `pnpm.version`, so the version no longer has to be maintained in two places:

```json5
// package.json
{
  "devEngines": {
    "packageManager": {
      "name": "pnpm",
      "version": "11.28.5",
      "onFail": "download"
    }
  }
}
```

A SemVer range like `^11.0.0` still falls back to `pnpm.version`, or the version pinned by the plugin. Builds that mirror the pnpm distribution, lock dependencies or verify them now need to serve or pin the version declared in `package.json`.
