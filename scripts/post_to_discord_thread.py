#!/usr/bin/env python3
"""Post a video file to a Discord thread."""

import argparse
import mimetypes
import os
import sys
import uuid
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

DISCORD_API_BASE = "https://discord.com/api/v10"


def _auth_header(token: str, auth_type: str) -> str:
    if auth_type == "bot":
        return f"Bot {token}"
    return token


def post_file_to_channel(
    channel_id: str,
    file_path: Path,
    content: str,
    token: str,
    auth_type: str,
) -> None:
    """Post a file to a Discord channel/thread using multipart form data."""
    boundary = uuid.uuid4().hex
    content_type_header = f"multipart/form-data; boundary={boundary}"

    filename = file_path.name
    mime_type, _ = mimetypes.guess_type(filename)
    if mime_type is None:
        mime_type = "application/octet-stream"

    file_data = file_path.read_bytes()

    body_parts: list[bytes] = []
    body_parts.append(f"--{boundary}\r\n".encode())
    body_parts.append(b'Content-Disposition: form-data; name="content"\r\n\r\n')
    body_parts.append(content.encode("utf-8") + b"\r\n")
    body_parts.append(f"--{boundary}\r\n".encode())
    body_parts.append(
        f'Content-Disposition: form-data; name="file"; filename="{filename}"\r\n'.encode()
    )
    body_parts.append(f"Content-Type: {mime_type}\r\n\r\n".encode())
    body_parts.append(file_data)
    body_parts.append(f"\r\n--{boundary}--\r\n".encode())

    body = b"".join(body_parts)

    url = f"{DISCORD_API_BASE}/channels/{channel_id}/messages"
    request = Request(
        url,
        data=body,
        headers={
            "Authorization": _auth_header(token, auth_type),
            "Content-Type": content_type_header,
            "User-Agent": "ti4-map-video-poster/1.0",
        },
        method="POST",
    )
    try:
        with urlopen(request, timeout=120) as response:
            result = response.read().decode("utf-8")
            print(f"Posted successfully to thread {channel_id}: {result[:200]}")
    except HTTPError as exc:
        resp_body = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(
            f"Discord API request failed ({exc.code}): {resp_body}"
        ) from exc
    except URLError as exc:
        raise RuntimeError(f"Discord API request failed: {exc.reason}") from exc


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Post a video file to a Discord thread"
    )
    parser.add_argument(
        "--thread-id", required=True, help="Discord thread ID to post to"
    )
    parser.add_argument(
        "--file", required=True, help="Path to the video file to post"
    )
    parser.add_argument(
        "--content",
        default="Here is the game replay video!",
        help="Message content to include with the file",
    )
    parser.add_argument(
        "--token-env",
        default="DISCORD_TOKEN",
        help="Environment variable containing Discord token (default: DISCORD_TOKEN)",
    )
    parser.add_argument(
        "--auth-type",
        choices=["bot", "user"],
        default="bot",
        help="Authorization mode: 'bot' prefixes token with 'Bot ', 'user' uses raw token (default: bot)",
    )
    args = parser.parse_args()

    token = os.getenv(args.token_env)
    if not token:
        raise RuntimeError(f"Missing Discord token env var: {args.token_env}")

    file_path = Path(args.file).resolve()
    if not file_path.exists():
        raise RuntimeError(f"File not found: {file_path}")

    post_file_to_channel(args.thread_id, file_path, args.content, token, args.auth_type)
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception as exc:
        print(f"Error: {exc}", file=sys.stderr)
        raise SystemExit(1)
