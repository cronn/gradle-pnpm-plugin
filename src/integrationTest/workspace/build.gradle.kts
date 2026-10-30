plugins { id("de.cronn.gradle-pnpm-plugin") }

// An outdated lockfile fails the install instead of being rewritten, the way it does on CI.
tasks.pnpmInstall { arguments.add("--frozen-lockfile") }
