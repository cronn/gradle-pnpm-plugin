# Prettier

Enabled by default when the project directory contains a `prettier.config.*` or a `.prettierrc*`
file.

|      Task       | Contributes to |                    Default includes                     |
|-----------------|----------------|---------------------------------------------------------|
| `prettierCheck` | `check`        | `*.ts`, `src/**/*.ts`, `src/**/*.tsx`, `*.json`, `*.md` |
| `prettierFix`   | `fix`          | `*.ts`, `src/**/*.ts`, `src/**/*.tsx`, `*.json`, `*.md` |

Both tasks are handed exactly the files the patterns resolve to: `prettier <files> --check` for
`prettierCheck`, `prettier <files> --write --list-different` for `prettierFix`. `prettierFix` runs
after `eslintFix`, so that formatting has the final say over ESLint's automatic fixes.
