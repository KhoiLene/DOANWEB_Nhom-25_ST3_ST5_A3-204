"""Create one OpenAI vector store per Techtra role and upload its starter guide."""

import json
import os
import sys
import uuid
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


ROOT = Path(__file__).resolve().parents[1]
KNOWLEDGE = ROOT / "docs" / "assistant-knowledge"
API_BASE = os.environ.get("OPENAI_BASE_URL", "https://api.openai.com/v1").rstrip("/")
ROLES = {
    "CUSTOMER": "customer.md",
    "STORE_STAFF": "store-staff.md",
    "WAREHOUSE": "warehouse.md",
    "ADMIN": "admin.md",
}


def request_json(method, path, payload=None):
    key = os.environ.get("OPENAI_API_KEY", "").strip()
    if not key:
        raise RuntimeError("Set OPENAI_API_KEY in the environment before provisioning.")
    body = json.dumps(payload).encode("utf-8") if payload is not None else None
    headers = {"Authorization": f"Bearer {key}", "Accept": "application/json"}
    if body is not None:
        headers["Content-Type"] = "application/json"
    return send(Request(f"{API_BASE}{path}", data=body, headers=headers, method=method))


def upload_file(file_path):
    key = os.environ["OPENAI_API_KEY"].strip()
    boundary = f"----Techtra{uuid.uuid4().hex}"
    filename = file_path.name
    content = file_path.read_bytes()
    body = b"".join([
        f"--{boundary}\r\nContent-Disposition: form-data; name=\"purpose\"\r\n\r\nassistants\r\n".encode(),
        f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"{filename}\"\r\nContent-Type: text/markdown\r\n\r\n".encode(),
        content,
        f"\r\n--{boundary}--\r\n".encode(),
    ])
    headers = {
        "Authorization": f"Bearer {key}",
        "Accept": "application/json",
        "Content-Type": f"multipart/form-data; boundary={boundary}",
    }
    return send(Request(f"{API_BASE}/files", data=body, headers=headers, method="POST"))


def send(request):
    try:
        with urlopen(request, timeout=45) as response:
            return json.loads(response.read().decode("utf-8"))
    except HTTPError as error:
        detail = error.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"OpenAI API returned HTTP {error.code}: {detail}") from None
    except URLError as error:
        raise RuntimeError(f"Could not reach OpenAI API: {error.reason}") from None


def main():
    if not os.environ.get("OPENAI_API_KEY", "").strip():
        print("Set OPENAI_API_KEY in your local environment, then run this script again.", file=sys.stderr)
        return 2
    created = []
    for role, filename in ROLES.items():
        source = KNOWLEDGE / filename
        store = request_json("POST", "/vector_stores", {"name": f"Techtra assistant - {role.lower().replace('_', ' ')}"})
        store_id = store["id"]
        file_obj = upload_file(source)
        attached = request_json("POST", f"/vector_stores/{store_id}/files", {"file_id": file_obj["id"]})
        created.append((role, store_id, attached.get("status", "processing")))
        print(f"OPENAI_VECTOR_STORE_{role}={store_id} status={attached.get('status', 'processing')}")
    print("Wait until all four files are indexed in the OpenAI dashboard, then set the IDs in the Spring environment.")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (KeyError, OSError, RuntimeError) as error:
        print(str(error), file=sys.stderr)
        raise SystemExit(1)
