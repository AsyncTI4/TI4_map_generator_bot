package ti4.service.tigl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.persistence.TestGameHarness;
import ti4.testUtils.BaseTi4Test;

/**
 * Runs the validator against a real saved game rather than a hand-built one. The synthetic fixtures in
 * TiglSetupServiceTest are all in setup state; this one is mid-game, which is the state /tigl enable sees when a
 * bothelper corrects a ladder in a later round.
 */
class TiglSetupServiceRealGameTest extends BaseTi4Test {

    @Test
    void doesNotInventObjectiveViolationsForAGameAlreadyUnderway() {
        try (var harness = TestGameHarness.forDefaultMap()) {
            Game game = harness.load();

            // Revealing consumes the peekable list, so counting it mid-game would report "0 stage 1 objectives".
            assertThat(game.getRevealedPublicObjectives()).isNotEmpty();
            assertThat(game.getPublicObjectives1Peekable()).isEmpty();

            assertThat(TiglSetupService.validateStandardLadder(game))
                    .noneMatch(violation -> violation.contains("stage 1 objectives"))
                    .noneMatch(violation -> violation.contains("stage 2 objectives"));
        }
    }

    @Test
    void stillReportsTheViolationsThatAreRealForThatGame() {
        try (var harness = TestGameHarness.forDefaultMap()) {
            Game game = harness.load();
            List<String> violations = TiglSetupService.validateStandardLadder(game);

            assertThat(violations).anyMatch(violation -> violation.startsWith("5 players"));
            // The game carries Thunder's Edge Demo, which is homebrew - but plain Thunder's Edge is the
            // Standard ladder itself and must never be reported as missing.
            assertThat(violations).anyMatch(violation -> violation.contains("homebrew"));
            assertThat(violations).noneMatch(violation -> violation.contains("Thunder's Edge is off"));
        }
    }

    @Test
    void doesNotTreatAGameWithoutTiglInItsNameAsALeagueGame() {
        try (var harness = TestGameHarness.forDefaultMap()) {
            Game game = harness.load();
            assertThat(game.getCustomName()).doesNotContainIgnoringCase("tigl");
            assertThat(TiglSetupService.looksLikeTiglGame(game)).isFalse();
        }
    }
}
