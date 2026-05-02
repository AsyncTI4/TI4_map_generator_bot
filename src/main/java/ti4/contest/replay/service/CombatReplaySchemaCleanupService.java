package ti4.contest.replay.service;

import jakarta.annotation.PostConstruct;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ti4.logging.BotLogger;

/**
 * Temporary idempotent startup migration for replay columns removed from existing SQLite databases.
 */
@Service
@RequiredArgsConstructor
public class CombatReplaySchemaCleanupService {

    private static final String COMBAT_OBSERVATION_COLUMNS = "id, started_at, game_name, tile_position, "
            + "attacker_faction, defender_faction, attacker_strength, defender_strength, attacker_hp, defender_hp, "
            + "attacker_expected_hits, defender_expected_hits, fairness_ratio, joint_score";

    private final DataSource dataSource;

    @PostConstruct
    public void cleanSchema() {
        try (Connection connection = dataSource.getConnection()) {
            normalizeCombatCandidateEventTypes(connection);
            normalizeCombatContestReplayStatuses(connection);
            rebuildCombatObservationIfNeeded(connection);
            dropColumns(connection, "combat_candidate", "combat_type");
            dropColumns(
                    connection, "combat_contest_side_bets", "candidate_id", "points_spent", "won", "profit_awarded");
            dropColumns(connection, "combat_replay_house_ability_use", "contest_id", "ability_key");
            dropColumns(connection, "combat_replay_house_ability_vote", "contest_id", "ability_key");
            dropColumns(connection, "combat_replay_hacan_trade_convoys", "candidate_id");
            dropColumns(connection, "combat_replay_hacan_trade_convoys_vote", "candidate_id");
            dropColumns(connection, "combat_replay_hacan_market_compact_decision", "decision_type", "vote_count");
        } catch (SQLException e) {
            BotLogger.error("Failed to clean obsolete combat replay schema columns.", e);
        }
    }

    private void normalizeCombatContestReplayStatuses(Connection connection) throws SQLException {
        if (tableColumns(connection, "combat_contest").isEmpty()) return;
        try (Statement statement = connection.createStatement()) {
            int updated = statement.executeUpdate(
                    "UPDATE combat_contest SET replay_status = 'REPLAYING' WHERE replay_status = 'FAILED'");
            if (updated > 0) {
                BotLogger.info("Normalized " + updated + " obsolete combat replay FAILED statuses.");
            }
        }
    }

    private void normalizeCombatCandidateEventTypes(Connection connection) throws SQLException {
        if (tableColumns(connection, "combat_candidate_event").isEmpty()) return;
        try (Statement statement = connection.createStatement()) {
            int updated = statement.executeUpdate("UPDATE combat_candidate_event "
                    + "SET event_type = 'INFO' "
                    + "WHERE event_type IN ('CARD', 'AGENT', 'LEADER', 'RETREAT', 'TECH_USE', 'ASSAULT', 'GRAVITON')");
            if (updated > 0) {
                BotLogger.info("Normalized " + updated + " obsolete combat replay event types.");
            }
        }
    }

    private void rebuildCombatObservationIfNeeded(Connection connection) throws SQLException {
        Set<String> columns = tableColumns(connection, "combat_observation");
        if (!columns.contains("combat_type")
                && !columns.contains("candidate_id")
                && !columns.contains("eligible_as_candidate")) return;

        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("DROP TABLE IF EXISTS combat_observation_replay_migration");
            statement.executeUpdate("CREATE TABLE combat_observation_replay_migration ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "started_at DATETIME NOT NULL, "
                    + "game_name VARCHAR(255) NOT NULL, "
                    + "tile_position VARCHAR(255) NOT NULL, "
                    + "attacker_faction VARCHAR(255) NOT NULL, "
                    + "defender_faction VARCHAR(255) NOT NULL, "
                    + "attacker_strength DOUBLE NOT NULL, "
                    + "defender_strength DOUBLE NOT NULL, "
                    + "attacker_hp DOUBLE NOT NULL, "
                    + "defender_hp DOUBLE NOT NULL, "
                    + "attacker_expected_hits DOUBLE NOT NULL, "
                    + "defender_expected_hits DOUBLE NOT NULL, "
                    + "fairness_ratio DOUBLE NOT NULL, "
                    + "joint_score DOUBLE NOT NULL)");
            statement.executeUpdate("INSERT INTO combat_observation_replay_migration ("
                    + COMBAT_OBSERVATION_COLUMNS
                    + ") SELECT "
                    + COMBAT_OBSERVATION_COLUMNS
                    + " FROM combat_observation");
            statement.executeUpdate("DROP TABLE combat_observation");
            statement.executeUpdate("ALTER TABLE combat_observation_replay_migration RENAME TO combat_observation");
            statement.executeUpdate(
                    "CREATE INDEX IF NOT EXISTS idx_combat_observation_started_at ON combat_observation(started_at)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_combat_observation_game_tile_started_at "
                    + "ON combat_observation(game_name, tile_position, started_at)");
            BotLogger.info("Rebuilt combat_observation without obsolete replay columns.");
        }
    }

    private void dropColumns(Connection connection, String tableName, String... columnNames) throws SQLException {
        Set<String> existingColumns = tableColumns(connection, tableName);
        if (existingColumns.isEmpty()) return;
        dropIndexesReferencingColumns(connection, tableName, Set.of(columnNames));

        for (String columnName : columnNames) {
            if (!existingColumns.contains(columnName)) continue;
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("ALTER TABLE " + tableName + " DROP COLUMN " + columnName);
                BotLogger.info("Dropped obsolete combat replay column " + tableName + "." + columnName + ".");
            }
        }
    }

    private void dropIndexesReferencingColumns(Connection connection, String tableName, Set<String> columnNames)
            throws SQLException {
        Set<String> indexesToDrop = new HashSet<>();
        try (Statement statement = connection.createStatement();
                ResultSet indexes = statement.executeQuery("PRAGMA index_list(" + tableName + ")")) {
            while (indexes.next()) {
                if (!"c".equals(indexes.getString("origin"))) continue;
                String indexName = indexes.getString("name");
                if (indexReferencesAnyColumn(connection, indexName, columnNames)) {
                    indexesToDrop.add(indexName);
                }
            }
        }

        for (String indexName : indexesToDrop) {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("DROP INDEX IF EXISTS " + indexName);
                BotLogger.info("Dropped obsolete combat replay index " + indexName + ".");
            }
        }
    }

    private boolean indexReferencesAnyColumn(Connection connection, String indexName, Set<String> columnNames)
            throws SQLException {
        try (Statement statement = connection.createStatement();
                ResultSet indexColumns = statement.executeQuery("PRAGMA index_info(" + indexName + ")")) {
            while (indexColumns.next()) {
                if (columnNames.contains(indexColumns.getString("name"))) {
                    return true;
                }
            }
        }
        return false;
    }

    private Set<String> tableColumns(Connection connection, String tableName) throws SQLException {
        Set<String> columns = new HashSet<>();
        try (Statement statement = connection.createStatement();
                ResultSet resultSet = statement.executeQuery("PRAGMA table_info(" + tableName + ")")) {
            while (resultSet.next()) {
                columns.add(resultSet.getString("name"));
            }
        }
        return columns;
    }
}
