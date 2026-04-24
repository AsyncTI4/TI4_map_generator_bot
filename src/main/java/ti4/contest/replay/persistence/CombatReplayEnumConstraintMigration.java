package ti4.contest.replay.persistence;

import jakarta.annotation.PostConstruct;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ti4.logging.BotLogger;

@Component
@RequiredArgsConstructor
public class CombatReplayEnumConstraintMigration {

    private final DataSource dataSource;

    @PostConstruct
    void migrate() {
        try (Connection connection = dataSource.getConnection()) {
            if (!isSqlite(connection) || !needsMigration(connection)) {
                return;
            }

            boolean originalAutoCommit = connection.getAutoCommit();
            connection.setAutoCommit(false);
            try {
                execute(connection, "PRAGMA foreign_keys = OFF");
                migrateCombatCandidate(connection);
                migrateCombatObservation(connection);
                migrateCombatContest(connection);
                migrateCombatCandidateEvent(connection);
                migrateCombatPredictorContest(connection);
                connection.commit();
                BotLogger.info("Combat replay enum constraint migration completed.");
            } catch (SQLException e) {
                connection.rollback();
                throw new IllegalStateException("Failed to migrate replay enum constraints.", e);
            } finally {
                try {
                    execute(connection, "PRAGMA foreign_keys = ON");
                } catch (SQLException e) {
                    BotLogger.warning("Failed to re-enable SQLite foreign keys after replay enum migration.", e);
                }
                connection.setAutoCommit(originalAutoCommit);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to inspect replay schema for enum constraint migration.", e);
        }
    }

    private boolean isSqlite(Connection connection) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        String url = metadata.getURL();
        return url != null && url.startsWith("jdbc:sqlite:");
    }

    private boolean needsMigration(Connection connection) throws SQLException {
        return tableNeedsMigration(connection, "combat_candidate")
                || tableNeedsMigration(connection, "combat_observation")
                || tableNeedsMigration(connection, "combat_contest")
                || tableNeedsMigration(connection, "combat_candidate_event")
                || tableNeedsMigration(connection, "combat_predictor_contest");
    }

    private boolean tableNeedsMigration(Connection connection, String tableName) throws SQLException {
        String tableSql = getTableSql(connection, tableName);
        return tableSql != null && tableSql.toLowerCase().contains("check");
    }

    private String getTableSql(Connection connection, String tableName) throws SQLException {
        try (var preparedStatement =
                connection.prepareStatement("SELECT sql FROM sqlite_master WHERE type = 'table' AND name = ?")) {
            preparedStatement.setString(1, tableName);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                if (!resultSet.next()) {
                    return null;
                }
                return resultSet.getString(1);
            }
        }
    }

    private Set<String> getColumnNames(Connection connection, String tableName) throws SQLException {
        Set<String> columnNames = new HashSet<>();
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("PRAGMA table_info(" + tableName + ")")) {
            while (resultSet.next()) {
                columnNames.add(resultSet.getString("name"));
            }
        }
        return columnNames;
    }

    private void migrateCombatCandidate(Connection connection) throws SQLException {
        if (!tableNeedsMigration(connection, "combat_candidate")) {
            return;
        }

        execute(connection, "DROP TABLE IF EXISTS combat_candidate_new");
        execute(connection, """
                CREATE TABLE combat_candidate_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    observation_id BIGINT NOT NULL UNIQUE,
                    status VARCHAR(255) NOT NULL,
                    promotion_status VARCHAR(255) NOT NULL,
                    next_event_sequence INTEGER NOT NULL,
                    started_at DATETIME NOT NULL,
                    resolved_at DATETIME,
                    promoted_at DATETIME,
                    game_name VARCHAR(255) NOT NULL,
                    tile_position VARCHAR(255) NOT NULL,
                    combat_type VARCHAR(255) NOT NULL,
                    attacker_faction VARCHAR(255) NOT NULL,
                    defender_faction VARCHAR(255) NOT NULL,
                    winner_faction VARCHAR(255),
                    loser_faction VARCHAR(255),
                    resolution_reason TEXT,
                    cancellation_reason TEXT,
                    pre_replay_context_text TEXT,
                    initial_render_snapshot_json TEXT,
                    promotion_score DOUBLE
                )
                """);
        execute(connection, """
                INSERT INTO combat_candidate_new (
                    id, observation_id, status, promotion_status, next_event_sequence,
                    started_at, resolved_at, promoted_at, game_name, tile_position, combat_type,
                    attacker_faction, defender_faction, winner_faction, loser_faction,
                    resolution_reason, cancellation_reason, pre_replay_context_text,
                    initial_render_snapshot_json, promotion_score
                )
                SELECT
                    id, observation_id, status, promotion_status, next_event_sequence,
                    started_at, resolved_at, promoted_at, game_name, tile_position, combat_type,
                    attacker_faction, defender_faction, winner_faction, loser_faction,
                    resolution_reason, cancellation_reason, pre_replay_context_text,
                    initial_render_snapshot_json, promotion_score
                FROM combat_candidate
                """);
        execute(connection, "DROP TABLE combat_candidate");
        execute(connection, "ALTER TABLE combat_candidate_new RENAME TO combat_candidate");
        execute(
                connection,
                "CREATE INDEX idx_combat_candidate_status_started_at ON combat_candidate(status, started_at)");
        execute(
                connection,
                "CREATE INDEX idx_combat_candidate_promotion_resolved_at ON combat_candidate(promotion_status, resolved_at)");
        execute(
                connection,
                "CREATE INDEX idx_combat_candidate_game_tile_status ON combat_candidate(game_name, tile_position, status)");
        execute(connection, "CREATE INDEX idx_combat_candidate_promoted_at ON combat_candidate(promoted_at)");
    }

    private void migrateCombatObservation(Connection connection) throws SQLException {
        if (!tableNeedsMigration(connection, "combat_observation")) {
            return;
        }

        execute(connection, "DROP TABLE IF EXISTS combat_observation_new");
        execute(connection, """
                CREATE TABLE combat_observation_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    started_at DATETIME NOT NULL,
                    game_name VARCHAR(255) NOT NULL,
                    tile_position VARCHAR(255) NOT NULL,
                    combat_type VARCHAR(255) NOT NULL,
                    attacker_faction VARCHAR(255) NOT NULL,
                    defender_faction VARCHAR(255) NOT NULL,
                    attacker_strength DOUBLE NOT NULL,
                    defender_strength DOUBLE NOT NULL,
                    attacker_hp DOUBLE NOT NULL,
                    defender_hp DOUBLE NOT NULL,
                    attacker_expected_hits DOUBLE NOT NULL,
                    defender_expected_hits DOUBLE NOT NULL,
                    fairness_ratio DOUBLE NOT NULL,
                    joint_score DOUBLE NOT NULL,
                    eligible_as_candidate BOOLEAN NOT NULL,
                    candidate_id BIGINT UNIQUE
                )
                """);
        execute(connection, """
                INSERT INTO combat_observation_new (
                    id, started_at, game_name, tile_position, combat_type,
                    attacker_faction, defender_faction, attacker_strength, defender_strength,
                    attacker_hp, defender_hp, attacker_expected_hits, defender_expected_hits,
                    fairness_ratio, joint_score, eligible_as_candidate, candidate_id
                )
                SELECT
                    id, started_at, game_name, tile_position, combat_type,
                    attacker_faction, defender_faction, attacker_strength, defender_strength,
                    attacker_hp, defender_hp, attacker_expected_hits, defender_expected_hits,
                    fairness_ratio, joint_score, eligible_as_candidate, candidate_id
                FROM combat_observation
                """);
        execute(connection, "DROP TABLE combat_observation");
        execute(connection, "ALTER TABLE combat_observation_new RENAME TO combat_observation");
        execute(connection, "CREATE INDEX idx_combat_observation_started_at ON combat_observation(started_at)");
        execute(
                connection,
                "CREATE INDEX idx_combat_observation_eligible_started_at ON combat_observation(eligible_as_candidate, started_at)");
        execute(
                connection,
                "CREATE INDEX idx_combat_observation_game_tile_started_at ON combat_observation(game_name, tile_position, started_at)");
    }

    private void migrateCombatContest(Connection connection) throws SQLException {
        if (!tableNeedsMigration(connection, "combat_contest")) {
            return;
        }

        execute(connection, "DROP TABLE IF EXISTS combat_contest_new");
        execute(connection, """
                CREATE TABLE combat_contest_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    candidate_id BIGINT NOT NULL UNIQUE,
                    posted_at DATETIME NOT NULL,
                    public_channel_id BIGINT NOT NULL,
                    public_message_id BIGINT NOT NULL,
                    public_thread_id BIGINT,
                    replay_status VARCHAR(255) NOT NULL,
                    replay_start_at DATETIME NOT NULL,
                    next_replay_at DATETIME NOT NULL,
                    next_event_sequence INTEGER NOT NULL,
                    replay_completed_at DATETIME,
                    pre_replay_context_posted_at DATETIME,
                    leaderboard_posted_at DATETIME,
                    replay_error TEXT
                )
                """);
        execute(connection, """
                INSERT INTO combat_contest_new (
                    id, candidate_id, posted_at, public_channel_id, public_message_id,
                    public_thread_id, replay_status, replay_start_at, next_replay_at,
                    next_event_sequence, replay_completed_at, pre_replay_context_posted_at,
                    leaderboard_posted_at, replay_error
                )
                SELECT
                    id, candidate_id, posted_at, public_channel_id, public_message_id,
                    public_thread_id, replay_status, replay_start_at, next_replay_at,
                    next_event_sequence, replay_completed_at, pre_replay_context_posted_at,
                    leaderboard_posted_at, replay_error
                FROM combat_contest
                """);
        execute(connection, "DROP TABLE combat_contest");
        execute(connection, "ALTER TABLE combat_contest_new RENAME TO combat_contest");
        execute(
                connection,
                "CREATE INDEX idx_replay_contest_replay_status_due ON combat_contest(replay_status, next_replay_at)");
        execute(connection, "CREATE INDEX idx_replay_contest_posted_at ON combat_contest(posted_at)");
    }

    private void migrateCombatCandidateEvent(Connection connection) throws SQLException {
        if (!tableNeedsMigration(connection, "combat_candidate_event")) {
            return;
        }

        execute(connection, "DROP TABLE IF EXISTS combat_candidate_event_new");
        execute(connection, """
                CREATE TABLE combat_candidate_event_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    candidate_id BIGINT NOT NULL,
                    occurred_at DATETIME NOT NULL,
                    sequence_number INTEGER NOT NULL,
                    event_type VARCHAR(255) NOT NULL,
                    round_number INTEGER,
                    actor_faction VARCHAR(255),
                    summary_text TEXT NOT NULL,
                    payload_json TEXT
                )
                """);
        execute(connection, """
                INSERT INTO combat_candidate_event_new (
                    id, candidate_id, occurred_at, sequence_number, event_type,
                    round_number, actor_faction, summary_text, payload_json
                )
                SELECT
                    id, candidate_id, occurred_at, sequence_number, event_type,
                    round_number, actor_faction, summary_text, payload_json
                FROM combat_candidate_event
                """);
        execute(connection, "DROP TABLE combat_candidate_event");
        execute(connection, "ALTER TABLE combat_candidate_event_new RENAME TO combat_candidate_event");
        execute(
                connection,
                "CREATE UNIQUE INDEX idx_combat_candidate_event_candidate_sequence ON combat_candidate_event(candidate_id, sequence_number)");
        execute(
                connection,
                "CREATE INDEX idx_combat_candidate_event_candidate_occurred_at ON combat_candidate_event(candidate_id, occurred_at)");
    }

    private void migrateCombatPredictorContest(Connection connection) throws SQLException {
        if (!tableNeedsMigration(connection, "combat_predictor_contest")) {
            return;
        }

        String combatTypeSourceColumn =
                getColumnNames(connection, "combat_predictor_contest").contains("combat_type")
                        ? "combat_type"
                        : "combatType";

        execute(connection, "DROP TABLE IF EXISTS combat_predictor_contest_new");
        execute(connection, """
                CREATE TABLE combat_predictor_contest_new (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    status VARCHAR(255) NOT NULL,
                    combat_type VARCHAR(255) NOT NULL,
                    game_name VARCHAR(255) NOT NULL,
                    tile_position VARCHAR(255) NOT NULL,
                    tile_representation TEXT NOT NULL,
                    attacker_faction VARCHAR(255) NOT NULL,
                    defender_faction VARCHAR(255) NOT NULL,
                    attacker_color VARCHAR(255) NOT NULL,
                    defender_color VARCHAR(255) NOT NULL,
                    public_channel_id BIGINT,
                    public_message_id BIGINT,
                    public_thread_id BIGINT,
                    source_combat_thread_id BIGINT,
                    posted_at DATETIME NOT NULL,
                    resolved_at DATETIME,
                    leaderboard_posted_at DATETIME,
                    winner_faction VARCHAR(255),
                    loser_faction VARCHAR(255),
                    initial_summary_text TEXT NOT NULL,
                    active_player_summary TEXT,
                    initial_strength_attacker DOUBLE NOT NULL,
                    initial_strength_defender DOUBLE NOT NULL,
                    initial_hp_attacker DOUBLE,
                    initial_hp_defender DOUBLE,
                    dice_rolled BOOLEAN,
                    attacker_hit_assigned_round INTEGER,
                    defender_hit_assigned_round INTEGER,
                    last_posted_hit_image_round INTEGER
                )
                """);
        execute(connection, """
                INSERT INTO combat_predictor_contest_new (
                    id, status, combat_type, game_name, tile_position, tile_representation,
                    attacker_faction, defender_faction, attacker_color, defender_color,
                    public_channel_id, public_message_id, public_thread_id, source_combat_thread_id,
                    posted_at, resolved_at, leaderboard_posted_at, winner_faction, loser_faction,
                    initial_summary_text, active_player_summary, initial_strength_attacker,
                    initial_strength_defender, initial_hp_attacker, initial_hp_defender, dice_rolled,
                    attacker_hit_assigned_round, defender_hit_assigned_round, last_posted_hit_image_round
                )
                SELECT
                    id, status, %s, game_name, tile_position, tile_representation,
                    attacker_faction, defender_faction, attacker_color, defender_color,
                    public_channel_id, public_message_id, public_thread_id, source_combat_thread_id,
                    posted_at, resolved_at, leaderboard_posted_at, winner_faction, loser_faction,
                    initial_summary_text, active_player_summary, initial_strength_attacker,
                    initial_strength_defender, initial_hp_attacker, initial_hp_defender, dice_rolled,
                    attacker_hit_assigned_round, defender_hit_assigned_round, last_posted_hit_image_round
                FROM combat_predictor_contest
                """.formatted(combatTypeSourceColumn));
        execute(connection, "DROP TABLE combat_predictor_contest");
        execute(connection, "ALTER TABLE combat_predictor_contest_new RENAME TO combat_predictor_contest");
        execute(connection, "CREATE INDEX idx_combat_contest_posted_at ON combat_predictor_contest(posted_at)");
        execute(
                connection,
                "CREATE INDEX idx_combat_contest_game_status ON combat_predictor_contest(game_name, status)");
        execute(
                connection,
                "CREATE INDEX idx_combat_contest_lookup ON combat_predictor_contest(game_name, tile_position, combat_type, status, posted_at)");
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }
}
