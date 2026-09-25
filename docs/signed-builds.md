# Signed Coder Abyss builds

Production signing was configured with the owner's explicit approval on
25 September 2026. Release APKs and AABs use one persistent RSA-4096 certificate.
The existing local debug certificate is retained for installation/Firebase testing.

## Certificate choice

The production APK uses a different certificate from previously installed debug
builds. It cannot update an installation signed with that debug certificate.
Use the separately published debug APK to update that installation and preserve
its app data. Back up/export projects before switching signing identities.

Register these production certificate fingerprints in the Firebase Android app
for `com.coderabyss.mobile` before testing Google sign-in in the release APK:

```
SHA-1:   77:3D:BF:CB:A6:D9:C2:93:39:14:B8:A7:C1:9C:49:1A:DB:C8:53:61
SHA-256: 9F:CB:BC:66:C4:32:10:0B:6A:F4:37:6E:DB:30:28:A1:BF:70:07:3C:85:D0:6E:F0:56:A4:A8:9E:4D:02:DD:51
```

The existing local debug certificate (already registered by the owner):

```
SHA-1:   45:FC:10:F5:7C:FC:4F:23:3A:B8:8E:08:2C:13:A6:01:F7:C4:B9:AB
SHA-256: DD:92:DD:BD:DB:E9:D9:53:F9:F1:C8:85:7D:27:E0:88:A6:55:E9:5C:86:B3:97:87:C7:99:EC:45:C0:74:3E:F8
```

For future Google Play distribution, also register the Play **app signing**
certificate from Play Console if Google signs delivered APKs with a different
key. A signed AAB is not a declaration of Play approval or completed store setup.
See [Android's signing guidance](https://developer.android.com/studio/publish/app-signing).

## Secure storage and repeat builds

No private key or password is committed or embedded in the APK/AAB. The local
key is outside Git in `%USERPROFILE%\.android\coder-abyss-signing\release-upload.p12`.
Its directory ACL permits the current Windows account and SYSTEM. The password is
stored using Windows DPAPI in `release-signing.clixml` in the same directory.
The exported `release-upload-certificate.pem` contains only the public certificate.
Retain a secure recovery backup of the key and password: DPAPI's local credential
file is tied to the Windows identity/machine and is not itself a portable backup.

GitHub Actions uses these encrypted repository Secrets (names only):

- `CODER_ABYSS_KEYSTORE_BASE64`
- `CODER_ABYSS_KEYSTORE_PASSWORD`
- `CODER_ABYSS_KEY_ALIAS`
- `CODER_ABYSS_KEY_PASSWORD`

The workflow restores the key into the runner's temporary directory with restricted
permissions, builds release APK/AAB, verifies signatures, publishes the signed
artifacts and removes the temporary key. It never uploads the key as an artifact.
The existing local debug private key has **not** been uploaded to GitHub.

Local Gradle signing accepts the password/alias variables above plus
`CODER_ABYSS_KEYSTORE_PATH`, pointing to the local key. Supply these through the
secure environment, not `gradle.properties` or source files. Without that environment,
local release builds are unsigned; the publishing workflow requires the signing
secrets and rejects unsigned release artifacts.

CI debug builds remain internal test builds; downloadable CI artifacts are the
production-signed release APK and AAB. The Firebase-compatible debug APK in the
GitHub Release is the locally verified build, not a random CI debug certificate.


## Verified downloadable build

GitHub release: [v0.7.1 UI redesign](https://github.com/auxiliarymaxima/CoderAbyss-Mobile/releases/tag/v0.7.1-ui-redesign).

| Asset | Bytes | SHA-256 |
|---|---:|---|
| `Coder-Abyss-v0.7.1-ui-release.apk` | 80,486,325 | `d6a15e8f5931133601356e70f5143307390bf3e09141aa6fcddb9187e5edff3e` |
| `Coder-Abyss-v0.7.1-ui-release.aab` | 68,971,486 | `4fad54200cd312c8ebf6c71508437ce237dade3eb43343242e9f907d250643b7` |
| `Coder-Abyss-v0.7.1-ui-debug.apk` | 105,015,611 | `bd94a5650658a4c49dfd26c7eb0ec0932d1b6c8d08cbbf15cd10bc54901d3ad9` |

Verified with Gradle `signingReport`, actual APK certificate inspection, AAB JAR
signature verification and Google's bundletool 1.18.3 validation. Release APK and
AAB certificate fingerprints match. Both APKs pass 16 KB ZIP/ELF alignment and the
repository's native/JNI and credential-pattern checks. The four UI render tests,
26 existing tests, debug lint, debug APK, release APK and release bundle builds pass.

JAR verification uses Android's expected self-signed developer certificate; generic
JDK trust-chain/timestamp warnings do not indicate an unsigned AAB. Bundletool
validation passed for the final signed file. No Play Console submission is claimed.
