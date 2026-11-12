# Signed APK releases

The current release is signed with a dedicated 3072-bit RSA key. The private PKCS#12 keystore and password are stored only in the ignored local `.signing/` directory used for this build; they are not committed to Git. Back up this key privately. Future APK updates need the same signing key to install over this release.

For automated signed GitHub releases, configure these repository Actions secrets:

- `ROADSCOPE_KEYSTORE_BASE64`: base64-encoded bytes of the PKCS#12 keystore.
- `ROADSCOPE_STORE_PASSWORD`: keystore password.
- `ROADSCOPE_KEY_ALIAS`: `roadscope-release`.
- `ROADSCOPE_KEY_PASSWORD`: private-key password.

Then push a `v*` tag. The workflow creates a signed release APK and SHA-256 file. Without the secrets, it intentionally produces a debug APK artifact and does not publish a release. The current GitHub connection rejected secret configuration with HTTP 403, so this first signed release is uploaded directly after the local release build.
