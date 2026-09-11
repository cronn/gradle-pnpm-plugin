---
"gradle-pnpm-plugin": minor
---

`includes` and `excludes` of a check task are now validated: a pattern that only Gradle's Ant
matcher or only the tool understands fails the build naming the pattern and the property.
