#!/usr/bin/env bash
set -euo pipefail

java_args=(
  "-XX:MaxRAMPercentage=70.0"
  "-XX:InitialRAMPercentage=20.0"
  "-XX:+UseStringDeduplication"
)

agent_path="${JPROFILER_AGENT_PATH:-}"
if [[ -n "$agent_path" && "$agent_path" == /opt/jprofiler/* && -f "$agent_path" ]]; then
  java_args+=("-agentpath:${agent_path}=port=${JPROFILER_PORT:-8849},nowait")
fi

exec java "${java_args[@]}" -jar /app/tibot.jar
