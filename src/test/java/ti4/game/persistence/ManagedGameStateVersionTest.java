package ti4.game.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.spring.service.persistence.GameEntityMapper;

class ManagedGameStateVersionTest {

    // Warmup only trusts a database row whose mapping_version equals MAPPING_VERSION; any other row is rebuilt
    // from its game file and resynced. If ManagedGameState changes shape, stored rows hold defaults for the new
    // parts, so MAPPING_VERSION must be raised to force that rebuild. Update this snapshot together with it.
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

    @Test
    void changingManagedGameStateRequiresRaisingTheMappingVersion() {
        boolean shapeUnchanged = componentNames(ManagedGameState.class).equals(SNAPSHOT_STATE_COMPONENTS)
                && componentNames(ManagedGameState.Participant.class).equals(SNAPSHOT_PARTICIPANT_COMPONENTS);

        assertThat(shapeUnchanged || GameEntityMapper.MAPPING_VERSION > SNAPSHOT_MAPPING_VERSION)
                .as("ManagedGameState changed shape: raise GameEntityMapper.MAPPING_VERSION so stored rows are"
                        + " rebuilt from game files, then update this test's snapshot")
                .isTrue();
        assertThat(GameEntityMapper.MAPPING_VERSION)
                .as("Update SNAPSHOT_MAPPING_VERSION and the component snapshots to the new version")
                .isEqualTo(SNAPSHOT_MAPPING_VERSION);
    }

    private static List<String> componentNames(Class<? extends Record> recordClass) {
        return Arrays.stream(recordClass.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
    }
}
