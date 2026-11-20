package ti4.game;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ti4.testUtils.BaseTi4Test;

class GameSetupDetectionTest extends BaseTi4Test {

    private static Game freshGame() {
        Game game = new Game();
        game.setName("setup-detection");
        game.newGameSetup();
        return game;
    }

    @Test
    void aFreshGameIsStillInSetup() {
        assertThat(freshGame().hasSetupEnded()).isFalse();
    }

    @Test
    void playerSetupPhaseIsStillSetup() {
        Game game = freshGame();
        game.setPhaseOfGame("playerSetup");

        assertThat(game.hasSetupEnded()).isFalse();
    }

    @Test
    void threeRevealedObjectivesEndSetup() {
        // Custodians is added by newGameSetup, so two more objectives make three.
        Game game = freshGame();
        game.addCustomPO("Marker A", 0);
        game.addCustomPO("Marker B", 0);

        assertThat(game.hasSetupEnded()).isTrue();
    }

    @Test
    void roundOneStrategyPhaseEndsSetupWithoutPublicObjectives() {
        // Secrets Only never reveals public objectives, so the phase alone has to end setup.
        Game game = freshGame();
        game.setPhaseOfGame("strategy");

        assertThat(game.hasSetupEnded()).isTrue();
    }

    @Test
    void laterRoundPhasesEndSetupWithoutPublicObjectives() {
        Game game = freshGame();
        for (String phase : new String[] {"action", "statusScoring", "statusHomework", "agendaVoting"}) {
            game.setPhaseOfGame(phase);
            assertThat(game.hasSetupEnded()).as(phase).isTrue();
        }
    }

    @Test
    void secretObjectiveScoringIsClosedInAFreshGame() {
        // Only Custodians is revealed, so the score button stays hidden during setup.
        assertThat(freshGame().isSecretObjectiveScoringOpen()).isFalse();
    }

    @Test
    void secretObjectiveScoringOpensWithASecondRevealedObjective() {
        Game game = freshGame();
        game.addCustomPO("Marker A", 0);

        assertThat(game.isSecretObjectiveScoringOpen()).isTrue();
    }

    @Test
    void secretObjectiveScoringOpensInRoundOneStrategyWithoutPublicObjectives() {
        // Secrets Only never reveals public objectives, so the score button must not depend on them.
        Game game = freshGame();
        game.setPhaseOfGame("strategy");

        assertThat(game.isSecretObjectiveScoringOpen()).isTrue();
    }

    @Test
    void aRoundPastOneEndsSetupWhateverThePhase() {
        Game game = freshGame();
        game.setPhaseOfGame("");
        game.setRound(2);

        assertThat(game.hasSetupEnded()).isTrue();
    }
}
