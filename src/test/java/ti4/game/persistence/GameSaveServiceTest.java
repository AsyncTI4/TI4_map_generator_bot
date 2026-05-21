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
            game.incrementSpecificSlashCommandCount("/statistics2 action_card_stats");
            game.setSpecificActionCardSaboCount("Sabotage", 4);
            game.setOverruleCount("hacan", 5, 2);

            boolean saved = GameSaveService.save(game, "test");
            assertThat(saved).isTrue();

            Game reloaded = harness.load();
            assertThat(reloaded).isNotNull();
            assertThat(reloaded.getLatestOutcomeVotedFor()).isEqualTo("testOutcome");
            assertThat(reloaded.getAllSlashCommandsUsed()).containsEntry("/statistics2 action_card_stats", 1);
            assertThat(reloaded.getAllActionCardsSabod()).containsEntry("Sabotage", 4);
            assertThat(reloaded.getAllOverruleCounts()).containsEntry("hacan|5", 2);
        }
    }
}
