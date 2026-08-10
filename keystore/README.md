# Signing boundary

`debug.keystore.base64` is a repository-pinned, development-only keystore. Its
credentials are intentionally non-secret Android debug defaults. The stable
certificate fingerprint verified by CI is:

```text
SHA-256: f805690fd2b6a9e925d6da491fbbb2839df7df581db580f58eb7f26742804c7a
```

The fixed development identity is intentional: Android only permits an
in-place update when the new APK has the same application ID and a compatible
signing certificate. It is useful for repeatedly installing CI debug builds
over the development installation, but anyone who can read this repository
can reproduce the key. It must not protect user data or sign a production
release.

CI builds `assembleRelease` with minification enabled and runs release lint and
unit tests. Because no release secret is available in the repository, that
validation output is intentionally unsigned. CI verifies that fact and uploads
reports only; it does not upload an APK.

The final private-sideload APK requires a separately generated and protected
release keystore. The private distribution model does not change Android's
signature rules: a release-signed APK can update an existing installation only
when its application ID and signing identity are compatible. Changing the
certificate normally requires uninstalling the old app (which removes its
data) or using Android's supported key-rotation/signing-lineage process.

The release key must therefore be kept outside the repository and supplied
only through an explicitly controlled signing step. The user may still need to
approve installation from the chosen source, and device/OEM security settings
can block or warn about sideloaded packages.
