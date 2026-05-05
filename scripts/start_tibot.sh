#!/usr/bin/env bash
set -euo pipefail

java_args=(
  "-XX:MaxRAMPercentage=70.0"
  "-XX:InitialRAMPercentage=20.0"
  "-XX:+UseStringDeduplication"
)

requested_agent_path="${JPROFILER_AGENT_PATH:-}"
agent_path=""
if [[ -n "$requested_agent_path" && "$requested_agent_path" == /opt/jprofiler/* ]]; then
  agent_path="$(realpath -e -- "$requested_agent_path" 2>/dev/null || true)"
fi
if [[ -n "$agent_path" && "$agent_path" == /opt/jprofiler/* ]]; then
  java_args+=("-agentpath:${agent_path}=port=${JPROFILER_PORT:-8849},nowait")
fi

exec java "${java_args[@]}" -jar /app/tibot.jar
