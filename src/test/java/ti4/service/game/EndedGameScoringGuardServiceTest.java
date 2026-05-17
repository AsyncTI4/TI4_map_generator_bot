package ti4.service.game;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ti4.game.Game;

class EndedGameScoringGuardServiceTest {

    @Test
    void getButtonsIncludesContinueAndDeleteOptions() {
        var buttons = EndedGameScoringGuardService.getButtons();

        assertThat(buttons).hasSize(2);
        assertThat(buttons.getFirst().getCustomId()).isEqualTo(EndedGameScoringGuardService.CONTINUE_PLAYING_BUTTON_ID);
        assertThat(buttons.getFirst().getLabel()).isEqualTo("Continue Playing");
        assertThat(buttons.get(1).getCustomId()).isEqualTo("deleteButtons");
        assertThat(buttons.get(1).getLabel()).isEqualTo("Delete These Buttons");
    }

    @Test
    void sendPromptIfGameEndedReturnsFalseForActiveGames() {
        var game = new Game();

        boolean blocked = EndedGameScoringGuardService.sendPromptIfGameEnded(game, null);

        assertThat(blocked).isFalse();
    }

    @Test
    void sendPromptIfGameEndedReturnsTrueForEndedGamesEvenWithoutChannel() {
        var game = new Game();
        game.setHasEnded(true);
        game.setVp(0);

        boolean blocked = EndedGameScoringGuardService.sendPromptIfGameEnded(game, null);

        assertThat(blocked).isTrue();
    }

    @Test
    void sendPromptIfGameEndedAutoClearsEndedFlagWhenNoOneHasReachedPointGoal() {
        var game = new Game();
        game.setHasEnded(true);

        boolean blocked = EndedGameScoringGuardService.sendPromptIfGameEnded(game, null);

        assertThat(blocked).isFalse();
        assertThat(game.isHasEnded()).isFalse();
    }
}
