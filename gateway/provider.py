"""Private Gradio adapter. Credentials exist only in server memory/Secret Manager."""
import json
import re
from urllib.parse import urlparse
import requests


def endpoint(space):
    if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9-]{0,95}/[A-Za-z0-9][A-Za-z0-9-]{0,95}", space):
        raise ValueError("Invalid Space identifier")
    return "https://" + space.replace("/", "-").lower() + ".hf.space"


class Provider:
    def __init__(self, space, token):
        self.base = endpoint(space)
        self.headers = {"Authorization": "Bearer " + token}

    def call(self, name, *args):
        if name not in {"capabilities", "submit_job", "get_job_status", "find_job", "cancel_job", "retry_job", "get_result"}:
            raise ValueError("Unsupported operation")
        base = self.base + "/gradio_api/call/" + name
        response = requests.post(base, headers=self.headers, json={"data": list(args)}, timeout=45, allow_redirects=False)
        if response.status_code != 200:
            raise RuntimeError("Provider unavailable")
        event_id = response.json()["event_id"]
        if not re.fullmatch(r"[A-Za-z0-9-]{1,128}", event_id):
            raise RuntimeError("Invalid provider event")
        with requests.get(base + "/" + event_id, headers=self.headers, stream=True, timeout=60, allow_redirects=False) as result:
            if result.status_code != 200:
                raise RuntimeError("Provider unavailable")
            event = ""
            for line in result.iter_lines(decode_unicode=True):
                if line.startswith("event: "):
                    event = line[7:]
                if event == "error":
                    raise RuntimeError("Provider rejected request")
                if event == "complete" and line.startswith("data: "):
                    return json.loads(line[6:])
        raise RuntimeError("Provider response interrupted")

    def validate(self):
        # Both identity and private Space access must succeed before activating a secret.
        result = requests.get("https://huggingface.co/api/whoami-v2", headers=self.headers, timeout=20, allow_redirects=False)
        if result.status_code != 200:
            raise ValueError("Credential validation failed")
        self.call("capabilities")

    def content(self, url, byte_range=None):
        target = urlparse(url)
        origin = urlparse(self.base)
        if target.scheme != "https" or target.hostname != origin.hostname or target.port not in (None, 443) or target.username:
            raise ValueError("Invalid result origin")
        headers = dict(self.headers)
        if byte_range:
            if not re.fullmatch(r"bytes=\d+-", byte_range):
                raise ValueError("Invalid range")
            headers["Range"] = byte_range
        result = requests.get(url, headers=headers, stream=True, timeout=60, allow_redirects=False)
        if result.status_code not in (200, 206):
            result.close()
            raise RuntimeError("Result unavailable")
        return result
