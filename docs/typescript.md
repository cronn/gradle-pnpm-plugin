# TypeScript

Enabled by default when the project directory contains a `tsconfig.json`.

|        Task         | Contributes to |           Default includes            |
|---------------------|----------------|---------------------------------------|
| `compileTypescript` | `check`        | `*.ts`, `src/**/*.ts`, `src/**/*.tsx` |

The task runs `tsc --noEmit`, which takes the files it type checks from the `tsconfig.json`. Passing
them on the command line would make `tsc` ignore that file, so this is the one tool that is not
handed its sources: for TypeScript the patterns only describe the Gradle inputs of
`compileTypescript`, which is what decides when it is `UP-TO-DATE`.
