#!/usr/bin/env python3

import argparse
import json
import os
import re
import sys
from pathlib import Path
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.parse import urlencode
from urllib.request import Request, urlopen

DISCORD_API_BASE = "https://discord.com/api/v10"
IMAGE_EXTENSIONS = {".png", ".jpg", ".jpeg", ".webp", ".gif"}


def _auth_header(token: str, auth_type: str) -> str:
    if auth_type == "bot":
        return f"Bot {token}"
    return token


def _api_get_json(url: str, token: str, auth_type: str) -> list[dict[str, Any]]:
    request = Request(
        url,
        headers={
            "Authorization": _auth_header(token, auth_type),
            "User-Agent": "ti4-map-video-fetcher/1.0",
        },
        method="GET",
    )
    try:
        with urlopen(request, timeout=60) as response:
            return json.loads(response.read().decode("utf-8"))
    except HTTPError as exc:
        body = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"Discord API request failed ({exc.code}): {body}") from exc
    except URLError as exc:
        raise RuntimeError(f"Discord API request failed: {exc.reason}") from exc


def _download_file(url: str, token: str, auth_type: str, target_path: Path) -> None:
    request = Request(
        url,
        headers={
            "Authorization": _auth_header(token, auth_type),
            "User-Agent": "ti4-map-video-fetcher/1.0",
        },
        method="GET",
    )
    try:
        with urlopen(request, timeout=120) as response:
            target_path.write_bytes(response.read())
    except HTTPError as exc:
        body = exc.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"Attachment download failed ({exc.code}) for {url}: {body}") from exc
    except URLError as exc:
        raise RuntimeError(f"Attachment download failed for {url}: {exc.reason}") from exc


def _is_image_attachment(attachment: dict[str, Any]) -> bool:
    content_type = str(attachment.get("content_type") or "").lower()
    if content_type.startswith("image/"):
        return True

    filename = str(attachment.get("filename") or "").lower()
    return Path(filename).suffix in IMAGE_EXTENSIONS


def _safe_extension(filename: str) -> str:
    ext = Path(filename).suffix.lower()
    if ext in IMAGE_EXTENSIONS:
        return ext
    return ".img"


def _normalize_name(value: str) -> str:
    return re.sub(r"[^A-Za-z0-9._-]", "_", value)


def collect_image_attachments(
    channel_id: str,
    token: str,
    max_messages: int,
    auth_type: str,
) -> list[tuple[str, str, str, str]]:
    before: str | None = None
    scanned = 0
    collected: list[tuple[str, str, str, str]] = []

    while scanned < max_messages:
        remaining = max_messages - scanned
        limit = 100 if remaining > 100 else remaining
        query = {"limit": str(limit)}
        if before:
            query["before"] = before

        url = f"{DISCORD_API_BASE}/channels/{channel_id}/messages?{urlencode(query)}"
        messages = _api_get_json(url, token, auth_type)
        if not messages:
            break

        scanned += len(messages)
        for message in messages:
            message_id = str(message.get("id") or "")
            attachments = message.get("attachments") or []
            for attachment in attachments:
                if not _is_image_attachment(attachment):
                    continue
                attachment_id = str(attachment.get("id") or "")
                attachment_url = str(attachment.get("url") or "")
                filename = str(attachment.get("filename") or "")
                if not (message_id and attachment_id and attachment_url):
                    continue
                collected.append((message_id, attachment_id, filename, attachment_url))

        oldest_message_id = str(messages[-1].get("id") or "")
        if not oldest_message_id:
            break
        before = oldest_message_id

    collected.sort(key=lambda entry: (int(entry[0]), int(entry[1])))
    return collected


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Download image attachments from a Discord channel/thread for map video rendering"
    )
    parser.add_argument("--channel-id", required=True, help="Discord channel or thread ID to scan")
    parser.add_argument("--output-dir", required=True, help="Directory to write downloaded frames")
    parser.add_argument(
        "--max-messages",
        type=int,
        default=500,
        help="Maximum number of messages to scan from newest backward (default: 500)",
    )
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
    args = parser.parse_args()

    if args.max_messages <= 0:
        raise ValueError("--max-messages must be > 0")

    token = os.getenv(args.token_env)
    if not token:
        raise RuntimeError(f"Missing Discord token env var: {args.token_env}")

    output_dir = Path(args.output_dir).resolve()
    output_dir.mkdir(parents=True, exist_ok=True)

    attachments = collect_image_attachments(args.channel_id, token, args.max_messages, args.auth_type)
    if not attachments:
        raise RuntimeError(
            f"No image attachments found in channel/thread '{args.channel_id}' within {args.max_messages} message(s)."
        )

    for index, (message_id, attachment_id, filename, attachment_url) in enumerate(attachments, start=1):
        ext = _safe_extension(filename)
        safe_message = _normalize_name(message_id)
        safe_attachment = _normalize_name(attachment_id)
        target_name = f"{index:06d}_{safe_message}_{safe_attachment}{ext}"
        target_path = output_dir / target_name
        _download_file(attachment_url, token, args.auth_type, target_path)

    print(f"Downloaded {len(attachments)} image attachment(s) to {output_dir}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as exc:
        print(f"Error: {exc}", file=sys.stderr)
        raise SystemExit(1)