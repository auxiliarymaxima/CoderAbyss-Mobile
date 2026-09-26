# Google Sign-In verification

The existing Firebase project is `coder-abyss`; the existing Android app is
`com.coderabyss.mobile` (`1:342807159631:android:47964321c134b76bd59b3a`).
Do not register a replacement Android app to repair authentication.

## Configuration and signing

On September 25, 2026, Firebase Console showed Google Authentication enabled and
a configured support email. Both original debug fingerprints remain registered.
The existing production key is now registered with:

- SHA-1: `77:3D:BF:CB:A6:D9:C2:93:39:14:B8:A7:C1:9C:49:1A:DB:C8:53:61`
- SHA-256: `9F:CB:BC:66:C4:32:10:0B:6A:F4:37:6E:DB:30:28:A1:BF:70:07:3C:85:D0:6E:F0:56:A4:A8:9E:4D:02:DD:51`

`app/google-services.json` was copied unchanged from the refreshed Firebase download.
It includes the production Android OAuth registration and the original Web client.
Credential Manager uses the generated **Web** client resource as its server audience.
The Google token is exchanged through Firebase `GoogleAuthProvider`; the app never
creates a substitute user or session. Firebase owns persisted session credentials.

GitHub Actions keeps using the protected production signing secrets. After signing,
`scripts/verify-firebase-signing.py` compares the actual APK signer report with the
official JSON's Android OAuth certificate, package, app ID and Web client. It fails
if the certificate is missing. This checks configuration consistency; it does not
prove that a real Google account can sign in or that console settings remain enabled.

GitHub runners' temporary debug keys are not production identities and are not
registered in Firebase. Test real Google sign-in with the **signed release APK**.
If Google Play App Signing is enabled later, register its app-signing SHA-1 and
SHA-256 as additional fingerprints and download fresh configuration. Do not replace
the current GitHub production key or remove other valid fingerprints.

## Authentication behavior

Authentication operations belong to the repository, so the account screen disappearing
after a UID change does not cancel Firebase token verification or credential cleanup.
Firebase session state remains independent from subscription/GPU gateway availability.
After Google sign-in, the app explicitly retrieves a Firebase ID token and records only
whether retrieval succeeded. It never writes or displays token contents.

In Settings → Account, **Verify Firebase session** retries token retrieval without
calling the cloud gateway. **Sign out** signs out of Firebase and clears Credential
Manager state; failed cleanup has a separate retry action. Local Only blocks remote
sign-in and token verification, while local sign-out remains available.

Authentication diagnostics contain public app/certificate identifiers, initialization
and session booleans, and safe exception class/code information. They omit account
credentials, email, UID, ID/refresh tokens, exception messages and stack traces.
Play Services can report reauthentication/configuration errors as cancellation; those
known cases are separated, and ambiguous cancellation does not assert the user cancelled.

## Required real-device acceptance test

Automated configuration, failure-classification and lifecycle tests are not a substitute
for a Google account test. No successful real-user sign-in is claimed until this passes:

1. Install the signed release APK over the existing production-signed app. Do not uninstall
   merely to test authentication; that can remove local projects. A prior debug-signed
   install cannot be updated by a differently signed release; preserve projects first.
2. Disable Local Only. Tap **Continue with Google**, choose an account and complete Google's
   account consent on the phone. Open Settings → Account.
3. Confirm **Signed in with Google** and **Firebase session verified**. In Authentication
   diagnostics, confirm the production SHA values and token retrieval `true`.
4. Close/reopen Coder Abyss; verify the signed-in identity remains. Tap **Verify Firebase
   session** and confirm retrieval succeeds again.
5. Sign out. Confirm Firebase is signed out and Google credential state cleared. Sign in
   again, then test closing the Google account picker without completing sign-in.
6. With connectivity disabled, verify an authentication/network error is distinguished
   from a cloud gateway error. Restore connectivity and retry. Firebase may serve a cached
   valid ID token offline; successful cached retrieval is not proof of network access.
7. Copy diagnostics if any step fails. Never share token values or passwords.

Only the authentication account card may change. Navigation, shared theme, branding,
four service workspaces, projects, models, video editor, voice controls and reference
screenshots remain the Build #21 baseline.
