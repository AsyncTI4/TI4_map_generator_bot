package ti4.game.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import ti4.spring.service.persistence.GameEntity;
import ti4.spring.service.persistence.GameEntityMapper;
import ti4.spring.service.persistence.GameParticipantEntity;
import ti4.spring.service.persistence.PlayerEntity;
import ti4.spring.service.persistence.TitleEntity;

class ManagedGameStateVersionTest {

    // Warmup only trusts a database row whose mapping_version equals MAPPING_VERSION, and the nightly reconcile
    // skips games whose revision and file stamp still match their row. Rows written under an older mapping are
    // therefore only rebuilt from their game files when MAPPING_VERSION is raised. That applies to any change in
    // what a row holds: a ManagedGameState component, a persisted column, or the logic that derives a value
    // (ManagedGameState.of, GameEntityMapper, GameStatisticsFilterer). This test catches the shape changes; logic
    // changes need the same bump by hand. Update the snapshots together with the version.
    private static final int SNAPSHOT_MAPPING_VERSION = 2;
    private static final List<String> SNAPSHOT_STATE_COMPONENTS = List.of(
            "name",
            "revisionKey",
            "hasEnded",
            "competitiveExportEligible",
            "hasWinner",
            "vpGoalReached",
            "fowMode",
            "factionReactMode",
            "twilightsFallMode",
            "colorReactMode",
            "stratReactMode",
            "fastScFollowMode",
            "fogQol01",
            "injectRules",
            "autoPingEnabled",
            "temporaryPingDisable",
            "creationDateTime",
            "lastModifiedDate",
            "activePlayerId",
            "lastActivePlayerChange",
            "endedDate",
            "round",
            "guildId",
            "mainGameChannelId",
            "tableTalkChannelId",
            "launchPostThreadId",
            "participants",
            "teamMateToPrimaryId");
    private static final List<String> SNAPSHOT_PARTICIPANT_COMPONENTS =
            List.of("userId", "userName", "realPlayer", "realOrEliminated");
    private static final Map<String, Set<String>> SNAPSHOT_ENTITY_COLUMNS = Map.of(
            "GameEntity",
            Set.of(
                    "gameName",
                    "revisionKey",
                    "mappingVersion",
                    "creationEpochMilliseconds",
                    "endedEpochMilliseconds",
                    "round",
                    "victoryPointGoal",
                    "completed",
                    "prophecyOfKings",
                    "thundersEdge",
                    "twilightsFall",
                    "fractureInPlay",
                    "homebrew",
                    "discordantStarsMode",
                    "absolMode",
                    "frankenMode",
                    "allianceMode",
                    "twilightImperiumGlobalLeague",
                    "twilightImperiumGlobalLeagueFractured",
                    "twilightImperiumGlobalLeagueRank",
                    "playerCount",
                    "statisticsIgnored",
                    "ended",
                    "winner",
                    "victoryPointGoalReached",
                    "fogOfWarMode",
                    "fogQol01",
                    "factionReactMode",
                    "colorReactMode",
                    "strategyCardReactMode",
                    "fastStrategyCardFollowMode",
                    "injectRulesLinks",
                    "lastModifiedEpochMilliseconds",
                    "gameFileModifiedEpochMilliseconds",
                    "gameFileSizeBytes",
                    "activePlayerUserId",
                    "lastActivePlayerChangeEpochMilliseconds",
                    "guildId",
                    "mainGameChannelId",
                    "tableTalkChannelId",
                    "launchPostThreadId",
                    "competitiveExportEligible",
                    "autoPingEnabled",
                    "temporaryPingDisabled",
                    "teamMatePrimaryUserIds"),
            "PlayerEntity",
            Set.of(
                    "id",
                    "user",
                    "game",
                    "factionName",
                    "score",
                    "totalNumberOfTurns",
                    "totalTurnTime",
                    "expectedHits",
                    "actualHits",
                    "eliminated",
                    "winner",
                    "replaced"),
            "TitleEntity",
            Set.of("id", "game", "user", "title"),
            "GameParticipantEntity",
            Set.of("id", "game", "userId", "userName", "realPlayer", "realOrEliminated"));

    @Test
    void changingManagedGameStateRequiresRaisingTheMappingVersion() {
        boolean shapeUnchanged = SNAPSHOT_STATE_COMPONENTS.equals(componentNames(ManagedGameState.class))
                && SNAPSHOT_PARTICIPANT_COMPONENTS.equals(componentNames(ManagedGameState.Participant.class))
                && sorted(SNAPSHOT_ENTITY_COLUMNS).equals(persistedColumns());

        assertThat(shapeUnchanged || GameEntityMapper.MAPPING_VERSION > SNAPSHOT_MAPPING_VERSION)
                .as("ManagedGameState or a persisted entity changed shape: raise GameEntityMapper.MAPPING_VERSION so"
                        + " stored rows are"
                        + " rebuilt from game files, then update this test's snapshot")
                .isTrue();
        assertThat(GameEntityMapper.MAPPING_VERSION)
                .as("Update SNAPSHOT_MAPPING_VERSION and the component snapshots to the new version")
                .isEqualTo(SNAPSHOT_MAPPING_VERSION);
    }

    private static Map<String, Set<String>> persistedColumns() {
        Map<String, Set<String>> columns = new TreeMap<>();
        for (Class<?> entity :
                List.of(GameEntity.class, PlayerEntity.class, TitleEntity.class, GameParticipantEntity.class)) {
            columns.put(entity.getSimpleName(), columnFields(entity));
        }
        return columns;
    }

    private static Set<String> columnFields(Class<?> entity) {
        Set<String> names = new TreeSet<>();
        for (Field field : entity.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())) continue;
            if (Collection.class.isAssignableFrom(field.getType())) continue;
            names.add(field.getName());
        }
        return names;
    }

    private static Map<String, Set<String>> sorted(Map<String, Set<String>> columns) {
        Map<String, Set<String>> sorted = new TreeMap<>();
        columns.forEach((entity, names) -> sorted.put(entity, new TreeSet<>(names)));
        return sorted;
    }

    private static List<String> componentNames(Class<? extends Record> recordClass) {
        return Arrays.stream(recordClass.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
    }
}
