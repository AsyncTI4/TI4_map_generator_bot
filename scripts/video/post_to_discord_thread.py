#!/usr/bin/env python3
"""Post a video file as a message attachment to a Discord thread."""

import argparse
import json
import os
import sys
import time
import uuid
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

DISCORD_API_BASE = "https://discord.com/api/v10"
_MAX_RETRIES = 5
_MAX_FILE_SIZE_BYTES = 25 * 1024 * 1024  # 25 MB Discord default limit
_NITRO_MAX_FILE_SIZE_BYTES = 500 * 1024 * 1024  # 500 MB Discord Nitro limit


def _auth_header(token: str, auth_type: str) -> str:
    if auth_type == "bot":
        return f"Bot {token}"
    return token


def _build_multipart(boundary: str, filename: str, file_bytes: bytes) -> bytes:
    parts: list[bytes] = []
    sep = f"--{boundary}\r\n".encode()

    # payload_json part
    parts.append(sep)
    parts.append(b'Content-Disposition: form-data; name="payload_json"\r\n')
    parts.append(b"Content-Type: application/json\r\n\r\n")
    parts.append(json.dumps({"attachments": [{"id": 0, "filename": filename}]}).encode())
    parts.append(b"\r\n")

    # file part
    parts.append(sep)
    parts.append(f'Content-Disposition: form-data; name="files[0]"; filename="{filename}"\r\n'.encode())
    parts.append(b"Content-Type: video/mp4\r\n\r\n")
    parts.append(file_bytes)
    parts.append(b"\r\n")

    parts.append(f"--{boundary}--\r\n".encode())
    return b"".join(parts)


def _request_upload_url(
    channel_id: str,
    filename: str,
    file_size: int,
    token: str,
    auth_type: str,
) -> tuple[str, str]:
    """Request a pre-signed upload URL from Discord. Returns (upload_url, upload_filename)."""
    url = f"{DISCORD_API_BASE}/channels/{channel_id}/attachments"
    body = json.dumps({"files": [{"id": 0, "filename": filename, "file_size": file_size}]}).encode()
    request = Request(
        url,
        data=body,
        headers={
            "Authorization": _auth_header(token, auth_type),
            "Content-Type": "application/json",
            "User-Agent": "ti4-map-video-poster/1.0",
        },
        method="POST",
    )
    for attempt in range(_MAX_RETRIES):
        try:
            with urlopen(request, timeout=120) as response:
                result = json.loads(response.read().decode())
            attachment = result["attachments"][0]
            return attachment["upload_url"], attachment["upload_filename"]
        except HTTPError as exc:
            body_text = exc.read().decode("utf-8", errors="replace")
            if exc.code == 429:
                try:
                    retry_after = float(json.loads(body_text).get("retry_after", 1))
                except Exception:
                    retry_after = 1.0
                if attempt < _MAX_RETRIES - 1:
                    print(
                        f"Rate limited by Discord API; retrying in {retry_after}s (attempt {attempt + 1}/{_MAX_RETRIES})",
                        file=sys.stderr,
                    )
                    time.sleep(retry_after)
                    continue
            raise RuntimeError(f"Discord API request failed ({exc.code}): {body_text}") from exc
        except URLError as exc:
            raise RuntimeError(f"Discord API request failed: {exc.reason}") from exc
    raise RuntimeError(f"Discord API request failed: exceeded {_MAX_RETRIES} retries due to rate limiting")


def _upload_file_to_url(upload_url: str, file_bytes: bytes) -> None:
    """Upload file bytes to a pre-signed Discord upload URL via HTTP PUT."""
    # Allow at least 60 s per 10 MB (assumes ~170 KB/s minimum), with a 120 s floor.
    timeout = max(120, len(file_bytes) // (10 * 1024 * 1024) * 60)
    request = Request(
        upload_url,
        data=file_bytes,
        headers={
            "Content-Type": "video/mp4",
            "Content-Length": str(len(file_bytes)),
        },
        method="PUT",
    )
    try:
        with urlopen(request, timeout=timeout) as response:
            response.read()
    except HTTPError as exc:
        body_text = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"File upload to pre-signed URL failed ({exc.code}): {body_text}") from exc
    except URLError as exc:
        raise RuntimeError(f"File upload to pre-signed URL failed: {exc.reason}") from exc


def _post_uploaded_attachment(
    thread_id: str,
    filename: str,
    upload_filename: str,
    token: str,
    auth_type: str,
) -> None:
    """Post a message that references an already-uploaded file to a Discord thread."""
    url = f"{DISCORD_API_BASE}/channels/{thread_id}/messages"
    body = json.dumps(
        {"attachments": [{"id": 0, "filename": filename, "uploaded_filename": upload_filename}]}
    ).encode()
    request = Request(
        url,
        data=body,
        headers={
            "Authorization": _auth_header(token, auth_type),
            "Content-Type": "application/json",
            "User-Agent": "ti4-map-video-poster/1.0",
        },
        method="POST",
    )
    for attempt in range(_MAX_RETRIES):
        try:
            with urlopen(request, timeout=120) as response:
                response.read()
            return
        except HTTPError as exc:
            body_text = exc.read().decode("utf-8", errors="replace")
            if exc.code == 429:
                try:
                    retry_after = float(json.loads(body_text).get("retry_after", 1))
                except Exception:
                    retry_after = 1.0
                if attempt < _MAX_RETRIES - 1:
                    print(
                        f"Rate limited by Discord API; retrying in {retry_after}s (attempt {attempt + 1}/{_MAX_RETRIES})",
                        file=sys.stderr,
                    )
                    time.sleep(retry_after)
                    continue
            raise RuntimeError(f"Discord API request failed ({exc.code}): {body_text}") from exc
        except URLError as exc:
            raise RuntimeError(f"Discord API request failed: {exc.reason}") from exc
    raise RuntimeError(f"Discord API request failed: exceeded {_MAX_RETRIES} retries due to rate limiting")


def post_message_to_thread(
    thread_id: str,
    content: str,
    token: str,
    auth_type: str,
) -> None:
    url = f"{DISCORD_API_BASE}/channels/{thread_id}/messages"
    body = json.dumps({"content": content}).encode()
    request = Request(
        url,
        data=body,
        headers={
            "Authorization": _auth_header(token, auth_type),
            "Content-Type": "application/json",
            "User-Agent": "ti4-map-video-poster/1.0",
        },
        method="POST",
    )
    for attempt in range(_MAX_RETRIES):
        try:
            with urlopen(request, timeout=120) as response:
                response.read()
            return
        except HTTPError as exc:
            body_text = exc.read().decode("utf-8", errors="replace")
            if exc.code == 429:
                try:
                    retry_after = float(json.loads(body_text).get("retry_after", 1))
                except Exception:
                    retry_after = 1.0
                if attempt < _MAX_RETRIES - 1:
                    print(
                        f"Rate limited by Discord API; retrying in {retry_after}s (attempt {attempt + 1}/{_MAX_RETRIES})",
                        file=sys.stderr,
                    )
                    time.sleep(retry_after)
                    continue
            raise RuntimeError(f"Discord API request failed ({exc.code}): {body_text}") from exc
        except URLError as exc:
            raise RuntimeError(f"Discord API request failed: {exc.reason}") from exc
    raise RuntimeError(f"Discord API request failed: exceeded {_MAX_RETRIES} retries due to rate limiting")


def post_video_to_thread(
    thread_id: str,
    file_path: Path,
    token: str,
    auth_type: str,
    artifact_url: str | None = None,
) -> None:
    file_bytes = file_path.read_bytes()
    file_size = len(file_bytes)

    if file_size > _NITRO_MAX_FILE_SIZE_BYTES:
        if artifact_url:
            msg = (
                f"The video for `{file_path.name}` is too large to upload directly to Discord "
                f"({file_size:,} bytes, limit is {_NITRO_MAX_FILE_SIZE_BYTES:,} bytes).\n"
                f"Download it from the GitHub Actions artifact instead:\n{artifact_url}"
            )
            post_message_to_thread(thread_id, msg, token, auth_type)
            return
        raise ValueError(
            f"File '{file_path}' is {file_size} bytes, exceeding the {_NITRO_MAX_FILE_SIZE_BYTES}-byte Discord Nitro limit."
        )

    if file_size > _MAX_FILE_SIZE_BYTES:
        # Use Discord's resumable upload API for large files (25 MB – 500 MB)
        upload_url, upload_filename = _request_upload_url(
            thread_id, file_path.name, file_size, token, auth_type
        )
        _upload_file_to_url(upload_url, file_bytes)
        _post_uploaded_attachment(thread_id, file_path.name, upload_filename, token, auth_type)
        return

    boundary = uuid.uuid4().hex
    body = _build_multipart(boundary, file_path.name, file_bytes)
    url = f"{DISCORD_API_BASE}/channels/{thread_id}/messages"

    request = Request(
        url,
        data=body,
        headers={
            "Authorization": _auth_header(token, auth_type),
            "Content-Type": f"multipart/form-data; boundary={boundary}",
            "User-Agent": "ti4-map-video-poster/1.0",
        },
        method="POST",
    )

    for attempt in range(_MAX_RETRIES):
        try:
            with urlopen(request, timeout=120) as response:
                response.read()
            return
        except HTTPError as exc:
            body_text = exc.read().decode("utf-8", errors="replace")
            if exc.code == 429:
                try:
                    retry_after = float(json.loads(body_text).get("retry_after", 1))
                except Exception:
                    retry_after = 1.0
                if attempt < _MAX_RETRIES - 1:
                    print(
                        f"Rate limited by Discord API; retrying in {retry_after}s (attempt {attempt + 1}/{_MAX_RETRIES})",
                        file=sys.stderr,
                    )
                    time.sleep(retry_after)
                    continue
            raise RuntimeError(f"Discord API request failed ({exc.code}): {body_text}") from exc
        except URLError as exc:
            raise RuntimeError(f"Discord API request failed: {exc.reason}") from exc

    raise RuntimeError(f"Discord API request failed: exceeded {_MAX_RETRIES} retries due to rate limiting")


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Post a video file as an attachment to a Discord thread"
    )
    parser.add_argument("--thread-id", required=True, help="Discord thread ID to post the video into")
    parser.add_argument("--file", required=True, help="Path to the video file to upload")
    parser.add_argument(
        "--token-env",
        default="DISCORD_TOKEN",
        help="Environment variable name containing Discord token (default: DISCORD_TOKEN)",
    )
    parser.add_argument(
        "--auth-type",
        choices=["bot", "user"],
        default="bot",
        help="Authorization mode: 'bot' prefixes token with 'Bot ', 'user' uses raw token (default: bot)",
    )
    parser.add_argument(
        "--artifact-url",
        default=None,
        help="GitHub Actions artifact URL to include in Discord message when the file is too large to upload directly",
    )
    args = parser.parse_args()

    token = os.getenv(args.token_env)
    if not token:
        raise RuntimeError(f"Missing Discord token env var: {args.token_env}")

    file_path = Path(args.file).resolve()
    if not file_path.is_file():
        raise RuntimeError(f"File not found: {file_path}")

    post_video_to_thread(args.thread_id, file_path, token, args.auth_type, args.artifact_url)
    print(f"Posted '{file_path.name}' to Discord thread {args.thread_id}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"Error: {exc}", file=sys.stderr)
        raise SystemExit(1)
