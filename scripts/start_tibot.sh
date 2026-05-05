#!/usr/bin/env bash
set -euo pipefail

java_args=(
  "-XX:MaxRAMPercentage=70.0"
  "-XX:InitialRAMPercentage=20.0"
  "-XX:+UseStringDeduplication"
)

if [[ -f "${JPROFILER_AGENT_PATH:-}" ]]; then
  java_args+=("-agentpath:${JPROFILER_AGENT_PATH}=port=${JPROFILER_PORT:-8849},nowait")
fi

exec java "${java_args[@]}" -jar /app/tibot.jar
