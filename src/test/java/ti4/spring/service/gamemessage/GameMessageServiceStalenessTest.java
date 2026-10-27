package ti4.spring.service.gamemessage;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.message.GameMessage;
import ti4.message.GameMessageType;

class GameMessageServiceStalenessTest {

    private static final long STALE_BEFORE = 1_000L;

    @Test
    void recentMessageStillWaitingOnReactionsIsKept() {
        assertThat(GameMessageService.isStale(message(5_000L, "sol"), 3, STALE_BEFORE))
                .isFalse();
    }

    @Test
    void messageEveryPlayerReactedToIsStale() {
        assertThat(GameMessageService.isStale(message(5_000L, "sol", "argent", "xxcha"), 3, STALE_BEFORE))
                .isTrue();
    }

    @Test
    void messageOlderThanTheCutoffIsStaleEvenWithoutReactions() {
        assertThat(GameMessageService.isStale(message(STALE_BEFORE), 3, STALE_BEFORE))
                .isTrue();
    }

    @Test
    void gameWithNoRealPlayersNeverCountsAsFullyReacted() {
        assertThat(GameMessageService.isStale(message(5_000L), 0, STALE_BEFORE)).isFalse();
    }

    private static GameMessage message(long gameSaveTime, String... factions) {
        return new GameMessage(
                "123", GameMessageType.ACTION_CARD, new LinkedHashSet<>(List.of(factions)), gameSaveTime, null);
    }
}
