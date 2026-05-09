#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

SQLITE_BIN="${SQLITE_BIN:-/usr/bin/sqlite3}"
PGLOADER_BIN="${PGLOADER_BIN:-pgloader}"
PSQL_BIN="${PSQL_BIN:-psql}"

SQLITE_DB="${SQLITE_DB:-}"
POSTGRES_URL="${POSTGRES_URL:-${DATABASE_URL:-}}"

if [[ -z "$SQLITE_DB" ]]; then
  echo "SQLITE_DB must point at the source tibot.db file." >&2
  exit 2
fi

if [[ -z "$POSTGRES_URL" && "${SPRING_DATASOURCE_URL:-}" == jdbc:postgresql://* ]]; then
  if [[ -z "${SPRING_DATASOURCE_USERNAME:-}" || -z "${SPRING_DATASOURCE_PASSWORD:-}" ]]; then
    echo "Set POSTGRES_URL, or set SPRING_DATASOURCE_URL plus SPRING_DATASOURCE_USERNAME/PASSWORD." >&2
    exit 2
  fi
  POSTGRES_URL="postgresql://${SPRING_DATASOURCE_USERNAME}:${SPRING_DATASOURCE_PASSWORD}@${SPRING_DATASOURCE_URL#jdbc:postgresql://}"
fi

if [[ -z "$POSTGRES_URL" ]]; then
  echo "POSTGRES_URL must point at the target Postgres database, e.g. postgresql://tibot:tibot@localhost:5432/tibot." >&2
  exit 2
fi

if [[ ! -f "$SQLITE_DB" ]]; then
  echo "SQLite database not found: $SQLITE_DB" >&2
  exit 2
fi

if [[ "$SQLITE_DB" == *"'"* ]]; then
  echo "SQLITE_DB paths containing single quotes are not supported by this migration script." >&2
  exit 2
fi

if ! command -v "$SQLITE_BIN" >/dev/null; then
  echo "sqlite3 not found: $SQLITE_BIN" >&2
  exit 2
fi

if ! command -v "$PGLOADER_BIN" >/dev/null; then
  echo "pgloader not found: $PGLOADER_BIN" >&2
  exit 2
fi

if ! command -v "$PSQL_BIN" >/dev/null; then
  echo "psql not found: $PSQL_BIN" >&2
  exit 2
fi

TABLES=(
  bot_discord_message
  combat_candidate
  combat_candidate_event
  combat_contest
  combat_contest_side_bets
  combat_observation
  combat_replay_hacan_market_compact_decision
  combat_replay_hacan_subsidy
  combat_replay_hacan_subsidy_vote
  combat_replay_hacan_trade_convoys
  combat_replay_hacan_trade_convoys_vote
  combat_replay_house
  combat_replay_house_ability_use
  combat_replay_house_ability_vote
  combat_replay_house_opt_out
  combat_replay_house_score
  combat_replay_leaderboard_entry
  combat_replay_prediction
  discord_user
  game
  game_round_player_stats
  game_round_player_stats_snapshot
  map_image_data
  player
  player_aggregates
  player_map_image_data
  standalone_title
  title
  tournament_winner
)

FKS=(
  "player fk_player_user foreign key (user_id) references discord_user (id)"
  "player fk_player_game foreign key (game_name) references game (game_name)"
  "title fk_title_user foreign key (user_id) references discord_user (id)"
  "title fk_title_game foreign key (game_name) references game (game_name)"
  "standalone_title fk_standalone_title_user foreign key (user_id) references discord_user (id)"
)

WORKDIR="$(mktemp -d "${TMPDIR:-/tmp}/ti4-pgloader.XXXXXX")"
SANITIZED_DB="$WORKDIR/tibot.pgloader.sqlite"
LOAD_FILE="$WORKDIR/sqlite-to-postgres.load"
SOURCE_COUNTS="$WORKDIR/source-counts.tsv"
TARGET_COUNTS="$WORKDIR/target-counts.tsv"
FKS_DROPPED=0

cleanup() {
  if [[ "${TI4_MIGRATION_KEEP_WORKDIR:-0}" == "1" ]]; then
    echo "Keeping migration workdir: $WORKDIR"
  else
    rm -rf "$WORKDIR"
  fi
}
trap cleanup EXIT

restore_foreign_keys_on_error() {
  if [[ "$FKS_DROPPED" == "1" ]]; then
    echo "Migration failed after foreign keys were dropped; attempting to restore them..." >&2
    add_foreign_keys || true
  fi
}
trap restore_foreign_keys_on_error ERR

quote_csv_tables() {
  local first=1
  for table in "${TABLES[@]}"; do
    if [[ "$first" == 1 ]]; then
      printf "  '%s'" "$table"
      first=0
    else
      printf ",\n  '%s'" "$table"
    fi
  done
  printf "\n"
}

run_psql() {
  "$PSQL_BIN" "$POSTGRES_URL" -v ON_ERROR_STOP=1 "$@"
}

drop_foreign_keys() {
  for fk in "${FKS[@]}"; do
    read -r table name _ <<<"$fk"
    run_psql -c "alter table if exists ${table} drop constraint if exists ${name};"
  done
}

add_foreign_keys() {
  for fk in "${FKS[@]}"; do
    read -r table name definition <<<"$fk"
    run_psql -c "alter table ${table} add constraint ${name} ${definition};"
  done
}

echo "Checking SQLite source integrity..."
"$SQLITE_BIN" "file:$SQLITE_DB?mode=ro" "pragma integrity_check;"

echo "Checking required target tables..."
for table in "${TABLES[@]}"; do
  exists="$(run_psql -At -c "select to_regclass('public.${table}') is not null;")"
  if [[ "$exists" != "t" ]]; then
    echo "Missing target table public.${table}. Apply V1__baseline_postgresql_schema.sql first." >&2
    exit 1
  fi
done

echo "Creating sanitized SQLite copy for pgloader..."
"$SQLITE_BIN" "$SQLITE_DB" "vacuum into '$SANITIZED_DB';"

if "$SQLITE_BIN" "$SANITIZED_DB" "select 1 from pragma_table_info('player') where name = 'username';" | grep -q 1; then
  "$SQLITE_BIN" "$SANITIZED_DB" "alter table player drop column username;"
fi

if "$SQLITE_BIN" "$SANITIZED_DB" "select count(*) from sqlite_master where type = 'table' and name = 'standalone_title';" | grep -q 1; then
  "$SQLITE_BIN" "$SANITIZED_DB" <<'SQL'
create table standalone_title_dedup (
    id integer,
    source varchar(255) not null,
    title varchar(255) not null,
    user_id varchar(255) not null
);
insert into standalone_title_dedup (id, source, title, user_id)
select min(id), source, title, user_id
  from standalone_title
 group by user_id, title, source;
drop table standalone_title;
alter table standalone_title_dedup rename to standalone_title;
SQL
fi

for table in "${TABLES[@]}"; do
  exists="$("$SQLITE_BIN" "$SANITIZED_DB" "select count(*) from sqlite_master where type = 'table' and name = '$table';")"
  if [[ "$exists" != "1" ]]; then
    echo "Missing source table ${table} in $SQLITE_DB." >&2
    exit 1
  fi
done

{
  echo "load database"
  echo "     from sqlite://$SANITIZED_DB"
  echo "     into $POSTGRES_URL"
  echo
  echo "with data only, reset sequences"
  echo
  echo "cast"
  cat <<'CASTS'
  column combat_candidate.started_at to timestamp using ti4-sqlite-millis-or-timestamp-to-timestamp,
  column combat_candidate.resolved_at to timestamp using ti4-sqlite-millis-or-timestamp-to-timestamp,
  column combat_candidate.promoted_at to timestamp using ti4-sqlite-millis-or-timestamp-to-timestamp,
  column combat_candidate.pending_resolution_started_at to timestamp using ti4-sqlite-millis-or-timestamp-to-timestamp,
  column combat_candidate.mentak_preview_posted_at to timestamp using ti4-sqlite-millis-or-timestamp-to-timestamp,
  column combat_candidate_event.occurred_at to timestamp using ti4-sqlite-millis-or-timestamp-to-timestamp,
  column combat_contest.posted_at to timestamp using ti4-sqlite-millis-or-timestamp-to-timestamp,
  column combat_contest.side_bet_market_posted_at to timestamp using ti4-sqlite-millis-or-timestamp-to-timestamp,
  column combat_contest.replay_start_at to timestamp using ti4-sqlite-millis-or-timestamp-to-timestamp,
  column combat_contest.next_replay_at to timestamp using ti4-sqlite-millis-or-timestamp-to-timestamp,
  column combat_contest.replay_completed_at to timestamp using ti4-sqlite-millis-or-timestamp-to-timestamp,
  column combat_contest.pre_replay_context_posted_at to timestamp using ti4-sqlite-millis-or-timestamp-to-timestamp,
  column combat_contest.leaderboard_posted_at to timestamp using ti4-sqlite-millis-or-timestamp-to-timestamp,
  column combat_contest_side_bets.placed_at to timestamp using ti4-sqlite-millis-or-timestamp-to-timestamp,
  column combat_contest_side_bets.resolved_at to timestamp using ti4-sqlite-millis-or-timestamp-to-timestamp,
  column combat_observation.started_at to timestamp using ti4-sqlite-millis-or-timestamp-to-timestamp,
  column combat_replay_hacan_market_compact_decision.decided_at to timestamp using ti4-sqlite-millis-or-timestamp-to-timestamp,
  column combat_replay_hacan_subsidy.selected_at to timestamp using ti4-sqlite-millis-or-timestamp-to-timestamp,
  column combat_replay_hacan_subsidy_vote.voted_at to timestamp using ti4-sqlite-millis-or-timestamp-to-timestamp,
  column combat_replay_hacan_trade_convoys.selected_at to timestamp using ti4-sqlite-millis-or-timestamp-to-timestamp,
  column combat_replay_hacan_trade_convoys_vote.voted_at to timestamp using ti4-sqlite-millis-or-timestamp-to-timestamp,
  column combat_replay_house.assigned_at to timestamp using ti4-sqlite-millis-or-timestamp-to-timestamp,
  column combat_replay_house.updated_at to timestamp using ti4-sqlite-millis-or-timestamp-to-timestamp,
  column combat_replay_house_ability_use.used_at to timestamp using ti4-sqlite-millis-or-timestamp-to-timestamp,
  column combat_replay_house_ability_vote.voted_at to timestamp using ti4-sqlite-millis-or-timestamp-to-timestamp,
  column combat_replay_house_opt_out.assigned_at to timestamp using ti4-sqlite-millis-or-timestamp-to-timestamp,
  column combat_replay_house_opt_out.opted_out_at to timestamp using ti4-sqlite-millis-or-timestamp-to-timestamp,
  column combat_replay_house_score.scored_at to timestamp using ti4-sqlite-millis-or-timestamp-to-timestamp,
  column combat_replay_leaderboard_entry.updated_at to timestamp using ti4-sqlite-millis-or-timestamp-to-timestamp,
  column combat_replay_prediction.announced_at to timestamp using ti4-sqlite-millis-or-timestamp-to-timestamp,
  column combat_replay_prediction.locked_at to timestamp using ti4-sqlite-millis-or-timestamp-to-timestamp,
  column combat_replay_prediction.scored_at to timestamp using ti4-sqlite-millis-or-timestamp-to-timestamp,
  column player_map_image_data.updated_at to timestamp using ti4-sqlite-millis-or-timestamp-to-timestamp
CASTS
  echo
  echo "including only table names like"
  quote_csv_tables
  echo ";"
} > "$LOAD_FILE"

echo "Recording source row counts..."
for table in "${TABLES[@]}"; do
  count="$("$SQLITE_BIN" "$SANITIZED_DB" "select count(*) from \"$table\";")"
  printf "%s\t%s\n" "$table" "$count" >> "$SOURCE_COUNTS"
done

if [[ "${TI4_MIGRATION_TRUNCATE_TARGET:-1}" == "1" ]]; then
  echo "Truncating target tables..."
  table_csv="$(printf ", public.%s" "${TABLES[@]}")"
  run_psql -c "truncate table ${table_csv#, } restart identity cascade;"
fi

echo "Dropping app foreign keys during bulk load..."
drop_foreign_keys
FKS_DROPPED=1

echo "Running pgloader..."
"$PGLOADER_BIN" -l "$ROOT_DIR/scripts/pgloader-ti4-transforms.lisp" "$LOAD_FILE"

echo "Re-adding app foreign keys..."
add_foreign_keys
FKS_DROPPED=0

echo "Recording target row counts..."
for table in "${TABLES[@]}"; do
  count="$(run_psql -At -c "select count(*) from public.${table};")"
  printf "%s\t%s\n" "$table" "$count" >> "$TARGET_COUNTS"
done

if ! diff -u "$SOURCE_COUNTS" "$TARGET_COUNTS"; then
  echo "Row count mismatch after migration." >&2
  echo "Source counts: $SOURCE_COUNTS" >&2
  echo "Target counts: $TARGET_COUNTS" >&2
  exit 1
fi

echo "Migration complete. Row counts match for ${#TABLES[@]} tables."
