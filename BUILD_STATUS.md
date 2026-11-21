# LyricGlow build status

## 0.1.1 hotfix

- Restored the `Theme.mochy` resource name as a compatibility alias to `Theme.LyricGlow`.
- This resolves the reported AAPT resource-linking errors for the application, crash activity, and TV activity manifest theme references.

## Verification

- Source archive integrity checked after packaging.
- Android compilation has not been verified in this environment.
- The supplied CI excerpt reports a separate `:app:compileUniversalGmsDebugKotlin` failure but omits the Kotlin compiler diagnostics. The first actual `e: ...` compiler message (and nearby lines) is required to identify and safely fix that second failure; the generic Gradle summary is insufficient.
