package ti4.discord.interactions.commands.developer;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.testUtils.BaseTi4Test;

class RunAgainstAllGamesTest extends BaseTi4Test {

    @Test
    void shouldOnlyCleanUpGamesThatRecordAPlayerOnEveryPlay() {
        assertThat(RunAgainstAllGames.startedAfterPlayerTracking(gameCreatedOn("2026.05.24")))
                .isTrue();
    }

    @Test
    void shouldSkipGamesFromBeforePlayerTrackingBegan() {
        // Every play in these games is player-less, so a player-less cancel is not evidence of
        // anything and the cleanup has no way to tell a fabricated one from a real one.
        assertThat(RunAgainstAllGames.startedAfterPlayerTracking(gameCreatedOn("2026.05.23")))
                .isFalse();
        assertThat(RunAgainstAllGames.startedAfterPlayerTracking(gameCreatedOn("2026.01.01")))
                .isFalse();
    }

    @Test
    void shouldSkipGamesItCannotDate() {
        assertThat(RunAgainstAllGames.startedAfterPlayerTracking(gameCreatedOn("not a date")))
                .isFalse();
        assertThat(RunAgainstAllGames.startedAfterPlayerTracking(gameCreatedOn("")))
                .isFalse();
    }

    private static Game gameCreatedOn(String creationDate) {
        Game game = new Game();
        game.setCreationDate(creationDate);
        return game;
    }
}
