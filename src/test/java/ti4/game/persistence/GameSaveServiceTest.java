package ti4.game.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.GameStats;
import ti4.testUtils.BaseTi4Test;

class GameSaveServiceTest extends BaseTi4Test {

    @Test
    void shouldSaveAndReloadGame() {
        try (var harness = TestGameHarness.forDefaultMap()) {
            Game game = harness.load();
            var player = game.getRealPlayers().getFirst();
            game.setLatestOutcomeVotedFor("testOutcome");
            game.getGameStats().recordAcPlay("Divert Funding", player);
            game.getGameStats().markLatestPlayCanceled("Divert Funding");
            game.getGameStats().recordAcPlay(GameStats.OVERRULE, player);

            boolean saved = GameSaveService.save(game, "test");
            assertThat(saved).isTrue();

            Game reloaded = harness.load();
            assertThat(reloaded).isNotNull();
            assertThat(reloaded.getLatestOutcomeVotedFor()).isEqualTo("testOutcome");
            assertThat(reloaded.getGameStats().getTotalPlays("Divert Funding")).isEqualTo(1);
            assertThat(reloaded.getGameStats().getTotalPlays(GameStats.OVERRULE))
                    .isEqualTo(1);
            assertThat(reloaded.getGameStats().getActionCardPlays())
                    .extracting(GameStats.ActionCardPlay::isCanceled)
                    .containsExactly(true, false);
            assertThat(reloaded.getGameStats().getActionCardPlays())
                    .extracting(GameStats.ActionCardPlay::getPlayerId)
                    .containsOnly(player.getStatsTrackedUserID());
        }
    }
}
