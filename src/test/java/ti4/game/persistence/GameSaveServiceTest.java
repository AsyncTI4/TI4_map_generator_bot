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
            game.setLatestOutcomeVotedFor("testOutcome");
            game.getGameStats().incrementSpecificSlashCommandCount("/statistics2 action_card_stats");
            game.getGameStats().recordAcPlayWithTarget(GameStats.SABOTAGE, "Divert Funding");
            game.getGameStats().recordAcPlayWithTarget(GameStats.OVERRULE, "leadership");

            boolean saved = GameSaveService.save(game, "test");
            assertThat(saved).isTrue();

            Game reloaded = harness.load();
            assertThat(reloaded).isNotNull();
            assertThat(reloaded.getLatestOutcomeVotedFor()).isEqualTo("testOutcome");
            assertThat(reloaded.getGameStats().getSlashCommandsUsed()).containsEntry("/statistics2 action_card_stats", 1);
            assertThat(reloaded.getGameStats().getCountPerTarget(GameStats.SABOTAGE)).containsEntry("Divert Funding", 1);
            assertThat(reloaded.getGameStats().getTotalPlays(GameStats.SABOTAGE)).isEqualTo(1);
            assertThat(reloaded.getGameStats().getCountPerTarget(GameStats.OVERRULE)).containsEntry("leadership", 1);
            assertThat(reloaded.getGameStats().getTotalPlays(GameStats.OVERRULE)).isEqualTo(1);
        }
    }
}
