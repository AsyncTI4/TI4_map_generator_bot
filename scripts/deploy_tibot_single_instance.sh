#!/usr/bin/env bash
set -euo pipefail

compose_file="${COMPOSE_FILE:-docker-compose.production.yml}"
service="${SERVICE:-tibot}"
rollout_timeout_seconds="${ROLLOUT_TIMEOUT_SECONDS:-540}"
wait_after_healthy_seconds="${WAIT_AFTER_HEALTHY_SECONDS:-2}"
stop_timeout_seconds="${STOP_TIMEOUT_SECONDS:-600}"
log_tail_lines="${ROLLBACK_LOG_TAIL_LINES:-200}"

old_ids_file="$(mktemp)"
new_ids_file="$(mktemp)"
new_container_id=""

cleanup_tmp_files() {
  rm -f "$old_ids_file" "$new_ids_file"
}

cleanup_new_container() {
  if [ -n "$new_container_id" ]; then
    echo "Cleaning up new tibot container $new_container_id"
    docker stop "$new_container_id" || true
    docker rm "$new_container_id" || true
  fi
}

rollback_and_exit() {
  cleanup_new_container
  cleanup_tmp_files
  exit 1
}

handle_signal() {
  echo "Rollout interrupted; rolling back new tibot container" >&2
  cleanup_new_container
  cleanup_tmp_files
  exit 130
}

compose() {
  docker compose -f "$compose_file" "$@"
}

count_lines() {
  sed '/^$/d' | wc -l | tr -d ' '
}

health_status() {
  docker inspect --format='{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$1"
}

trap 'cleanup_tmp_files' EXIT

old_container_ids="$(compose ps -q "$service" || true)"
printf '%s\n' "$old_container_ids" | sed '/^$/d' > "$old_ids_file"
old_container_count="$(count_lines < "$old_ids_file")"
target_container_count=$((old_container_count + 1))

echo "Found $old_container_count existing $service container(s)."
echo "Scaling '$service' to $target_container_count total container(s) to create exactly one rollout candidate."
compose up --detach --scale "$service=$target_container_count" --no-recreate "$service"

all_container_ids_after_scale="$(compose ps -q "$service" || true)"
printf '%s\n' "$all_container_ids_after_scale" \
  | sed '/^$/d' \
  | grep -vxF -f "$old_ids_file" > "$new_ids_file" || true

new_container_count="$(count_lines < "$new_ids_file")"
if [ "$new_container_count" -ne 1 ]; then
  echo "Expected exactly one new $service container, found $new_container_count." >&2
  echo "Existing container ids before scale:" >&2
  cat "$old_ids_file" >&2
  echo "New candidate ids after scale:" >&2
  cat "$new_ids_file" >&2

  while IFS= read -r candidate_id; do
    [ -z "$candidate_id" ] && continue
    docker stop "$candidate_id" || true
    docker rm "$candidate_id" || true
  done < "$new_ids_file"

  exit 1
fi

new_container_id="$(cat "$new_ids_file")"
trap 'handle_signal' INT TERM HUP

echo "Waiting for new $service container to become healthy: $new_container_id"
for second in $(seq 1 "$rollout_timeout_seconds"); do
  status="$(health_status "$new_container_id")"
  if [ "$status" = "healthy" ]; then
    break
  fi

  if [ "$second" -eq 1 ] || [ $((second % 30)) -eq 0 ]; then
    echo "Still waiting for $new_container_id health status: $status (${second}s elapsed)"
  fi

  sleep 1
done

final_status="$(health_status "$new_container_id")"
if [ "$final_status" != "healthy" ]; then
  echo "New $service container did not become healthy; rolling back." >&2
  echo "Final health status: $final_status" >&2
  docker inspect --format='{{json .State.Health}}' "$new_container_id" || true
  docker logs --tail "$log_tail_lines" "$new_container_id" || true
  rollback_and_exit
fi

if [ "$wait_after_healthy_seconds" != "0" ]; then
  echo "New $service container is healthy. Waiting ${wait_after_healthy_seconds}s before draining old containers."
  sleep "$wait_after_healthy_seconds"
fi

trap - INT TERM HUP

while IFS= read -r old_container_id; do
  [ -z "$old_container_id" ] && continue

  echo "Draining old $service container $old_container_id"
  docker exec "$old_container_id" sh -c 'wget -qO- --post-data="" http://127.0.0.1:8081/api/public/deploy/drain || true; for i in $(seq 1 15); do wget -q -O - http://127.0.0.1:8081/api/public/ready >/dev/null 2>&1 || break; sleep 1; done' || true

  echo "Stopping and removing old $service container $old_container_id"
  docker stop --time "$stop_timeout_seconds" "$old_container_id" || true
  docker rm "$old_container_id" || true
done < "$old_ids_file"

active_container_ids="$(compose ps -q "$service" || true)"
active_container_count="$(printf '%s\n' "$active_container_ids" | count_lines)"
if [ "$active_container_count" -ne 1 ]; then
  echo "Expected exactly one active $service container after rollout, found $active_container_count." >&2
  compose ps "$service" || true
  exit 1
fi

echo "Rollout complete. Active $service container: $new_container_id"
