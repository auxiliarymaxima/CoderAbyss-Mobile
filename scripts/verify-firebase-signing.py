"""Validate public Firebase configuration against the actual release APK signer report."""
import argparse
import json
import re
from pathlib import Path

PACKAGE = "com.coderabyss.mobile"
PROJECT = "coder-abyss"
APP_ID = "1:342807159631:android:47964321c134b76bd59b3a"
WEB_CLIENT = "342807159631-be34s9osrjomhvbv67mcgk7b1us6jcam.apps.googleusercontent.com"
PRODUCTION_SHA1 = "773dbfcba6d9c2933914b8a7c19c491adbc85361"
PRODUCTION_SHA256 = "9fcbbc66c432100b6af4376edb3028a1bf70073c85d06ef056a4a89e4d02dd51"


def verify(config, signer_report):
    if config.get("project_info", {}).get("project_id") != PROJECT:
        raise ValueError("Wrong Firebase project")
    clients = [c for c in config.get("client", []) if c.get("client_info", {}).get("android_client_info", {}).get("package_name") == PACKAGE]
    if len(clients) != 1 or clients[0]["client_info"].get("mobilesdk_app_id") != APP_ID:
        raise ValueError("Configuration must describe the existing Firebase Android app")
    oauth = clients[0].get("oauth_client", [])
    if not any(c.get("client_type") == 3 and c.get("client_id") == WEB_CLIENT for c in oauth):
        raise ValueError("Expected Web OAuth client is missing")
    registered = {c.get("android_info", {}).get("certificate_hash", "").replace(":", "").lower() for c in oauth if c.get("client_type") == 1 and c.get("android_info", {}).get("package_name") == PACKAGE}
    signers = re.findall(r"Signer #\d+ certificate SHA-1 digest:\s*([0-9a-fA-F:]+)", signer_report)
    if not signers or any(len(s.replace(":", "")) != 40 or s.replace(":", "").lower() not in registered for s in signers):
        raise ValueError("Actual APK signing SHA-1 is missing from the official Firebase Android OAuth configuration. Register the certificate and download a refreshed google-services.json.")
    sha256 = re.findall(r"Signer #\d+ certificate SHA-256 digest:\s*([0-9a-fA-F:]+)", signer_report)
    if [s.replace(":", "").lower() for s in signers] != [PRODUCTION_SHA1] or [s.replace(":", "").lower() for s in sha256] != [PRODUCTION_SHA256]:
        raise ValueError("Release signing identity changed: expected the protected Build #21 production certificate, not a debug or replacement key")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("config", type=Path)
    parser.add_argument("signer_report", type=Path)
    args = parser.parse_args()
    verify(json.loads(args.config.read_text(encoding="utf-8-sig")), args.signer_report.read_text(encoding="utf-8-sig"))
    print("Firebase project, existing Android app, Web OAuth audience and actual APK signer match.")
