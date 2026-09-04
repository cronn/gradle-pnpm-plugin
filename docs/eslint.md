# ESLint

Enabled by default when the project directory contains an `eslint.config.*` flat config file. The
legacy `.eslintrc.*` format is not detected.

|     Task      | Contributes to |           Default includes            |
|---------------|----------------|---------------------------------------|
| `eslintCheck` | `check`        | `*.ts`, `src/**/*.ts`, `src/**/*.tsx` |
| `eslintFix`   | `fix`          | `*.ts`, `src/**/*.ts`, `src/**/*.tsx` |

Both tasks depend on `compileTypescript`, and both are handed exactly the files the patterns resolve
to: `eslint <files> --max-warnings=0`, with `--fix` appended for `eslintFix`.
