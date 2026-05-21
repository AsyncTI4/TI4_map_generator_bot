package ti4.game.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.testUtils.BaseTi4Test;

class GameSaveServiceTest extends BaseTi4Test {

    @Test
    void shouldSaveAndReloadGame() {
        try (var harness = TestGameHarness.forDefaultMap()) {
            Game game = harness.load();
            game.setLatestOutcomeVotedFor("testOutcome");
            game.getGameStats().incrementSpecificSlashCommandCount("/statistics2 action_card_stats");
            game.getGameStats().setSpecificActionCardSaboCount("Sabotage", 4);
            game.getGameStats().setOverruleCount("hacan", 5, 2);

            boolean saved = GameSaveService.save(game, "test");
            assertThat(saved).isTrue();

            Game reloaded = harness.load();
            assertThat(reloaded).isNotNull();
            assertThat(reloaded.getLatestOutcomeVotedFor()).isEqualTo("testOutcome");
            assertThat(reloaded.getGameStats().getSlashCommandsUsed()).containsEntry("/statistics2 action_card_stats", 1);
            assertThat(reloaded.getGameStats().getActionCardsSabotaged()).containsEntry("Sabotage", 4);
            assertThat(reloaded.getGameStats().getFlattenedOverruleCounts()).containsEntry("hacan|5", 2);
        }
    }
}
