"""Reject recognizable private credential payloads without printing their values.

This is a defense-in-depth artifact scan, not proof that every possible secret
encoding is absent. Public Firebase application API identifiers are not secrets.
"""
import re
import sys
import zipfile

patterns = [rb"hf_[A-Za-z0-9]{25,}", rb"-----BEGIN (?:RSA |EC )?PRIVATE KEY-----",
            rb'"type"\s*:\s*"service_account"']
path = sys.argv[1] if len(sys.argv) > 1 else "app/build/outputs/apk/debug/app-debug.apk"
with zipfile.ZipFile(path) as apk:
    for entry in apk.infolist():
        if entry.is_dir():
            continue
        data = apk.read(entry)
        if any(re.search(pattern, data) for pattern in patterns):
            raise SystemExit("Credential-shaped content found in APK; values withheld")
print("APK credential-pattern scan passed (HF token/private-key/service-account payloads)")
