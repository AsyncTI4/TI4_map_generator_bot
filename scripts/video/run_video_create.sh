#!/usr/bin/env bash

set -euo pipefail

MAP_ID="${1:-}"
CHANNEL_ID="${2:-}"
THREAD_ID="${3:-}"
MAX_MESSAGES="${4:-500}"
DISCORD_AUTH_TYPE="${5:-${DISCORD_AUTH_TYPE:-bot}}"
GUILD_ID="${6:-${GUILD_ID:-}}"

if [[ -z "$MAP_ID" ]]; then
  echo "Error: map_id is required. Usage: scripts/video/run_video_create.sh <map_id> <channel_id> <thread_id> [max_messages] [discord_auth_type] [guild_id]" >&2
  exit 1
fi

if [[ -z "$GUILD_ID" ]]; then
  echo "Error: guild_id is required. Pass it as the 6th argument or set the GUILD_ID environment variable." >&2
  exit 1
fi

if [[ ! "$MAP_ID" =~ ^[A-Za-z0-9_-]+$ ]]; then
  echo "Error: invalid map_id '$MAP_ID'. Allowed characters: letters, numbers, underscore, hyphen." >&2
  exit 1
fi

if [[ -z "$CHANNEL_ID" && -z "$THREAD_ID" ]]; then
  echo "Error: provide either channel_id or thread_id." >&2
  exit 1
fi

if [[ -n "$CHANNEL_ID" && -n "$THREAD_ID" ]]; then
  echo "Error: provide only one source: channel_id or thread_id, not both." >&2
  exit 1
fi

DISCORD_TOKEN_VALUE="${DISCORD_TOKEN:-${DISCORD_BOT_TOKEN:-}}"
if [[ -z "$DISCORD_TOKEN_VALUE" ]]; then
  echo "Error: Discord token is missing. Set DISCORD_TOKEN (or DISCORD_BOT_TOKEN for backward compatibility)." >&2
  exit 1
fi

if [[ "$DISCORD_AUTH_TYPE" != "bot" && "$DISCORD_AUTH_TYPE" != "user" ]]; then
  echo "Error: DISCORD_AUTH_TYPE must be 'bot' or 'user'." >&2
  exit 1
fi

export DISCORD_TOKEN="$DISCORD_TOKEN_VALUE"

if [[ ! "$MAX_MESSAGES" =~ ^[0-9]+$ ]] || [[ "$MAX_MESSAGES" -le 0 ]]; then
  echo "Error: max_messages must be a positive integer." >&2
  exit 1
fi

SOURCE_ID="$CHANNEL_ID"
if [[ -n "$THREAD_ID" ]]; then
  SOURCE_ID="$THREAD_ID"
fi

FRAMES_DIR="tmp/video_frames/$MAP_ID"
OUTPUT_DIR="tmp/video_output"
TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"
ARTIFACT_NAME="${MAP_ID}_${TIMESTAMP}.mp4"
OUTPUT_FILE="${OUTPUT_DIR}/${ARTIFACT_NAME}"

mkdir -p "$FRAMES_DIR" "$OUTPUT_DIR"
find "$FRAMES_DIR" -maxdepth 1 -type f -delete

echo "Downloading frames for map_id '$MAP_ID' from Discord source '$SOURCE_ID'..." >&2
python3 scripts/video/fetch_discord_frames.py \
  --guild-id "$GUILD_ID" \
  --channel-id "$SOURCE_ID" \
  --output-dir "$FRAMES_DIR" \
  --max-messages "$MAX_MESSAGES" \
  --token-env "DISCORD_TOKEN" \
  --auth-type "$DISCORD_AUTH_TYPE" >&2

initial_frame_count="$(find "$FRAMES_DIR" -maxdepth 1 -type f | wc -l | tr -d ' ')"
if [[ "$initial_frame_count" -eq 0 ]]; then
  echo "Error: no image frames found in Discord source '$SOURCE_ID'." >&2
  exit 1
fi

echo "Downloaded $initial_frame_count frame(s). Detecting player-area cutoff, cropping, and normalising frames..." >&2
python3 scripts/video/crop_frames.py "$FRAMES_DIR" >&2
find "$FRAMES_DIR" -maxdepth 1 -type f ! -name '*.png' -delete

echo "Sanitizing frame filenames..." >&2
python3 scripts/video/conv.py "$FRAMES_DIR" >&2

final_frame_count="$(find "$FRAMES_DIR" -maxdepth 1 -type f -name '*.png' | wc -l | tr -d ' ')"
if [[ "$final_frame_count" -eq 0 ]]; then
  echo "Error: frame processing resulted in zero PNG files for map_id '$MAP_ID'." >&2
  exit 1
fi

echo "Creating MP4 from $final_frame_count frame(s)..." >&2
ffmpeg -y -framerate 5 -pattern_type glob -i "$FRAMES_DIR/*.png" -vf "scale=trunc(iw/2)*2:trunc(ih/2)*2" -c:v libx264 -pix_fmt yuv420p "$OUTPUT_FILE" >&2

if [[ ! -s "$OUTPUT_FILE" ]]; then
  echo "Error: video output was not created or is empty: $OUTPUT_FILE" >&2
  exit 1
fi

echo "Video created successfully: $OUTPUT_FILE" >&2
echo "artifact_name=${MAP_ID}_${TIMESTAMP}"
echo "output_file=$OUTPUT_FILE"