package ti4.service.breakthrough;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.persistence.TestGameHarness;
import ti4.model.BorderAnomalyHolder;
import ti4.testUtils.BaseTi4Test;

class VoidTetherServiceTest extends BaseTi4Test {

    @Test
    void tethersFromALegacySaveAreFoundAfterLoading() {
        // The test map stores its two tethers with the old upper-case enum name "VOID_TETHER".
        try (var harness = TestGameHarness.fromSourceGame("game-with-border-anomalies")) {
            Game game = harness.load();
            Player empyrean = game.getPlayerFromColorOrFaction("empyrean");

            assertThat(game.getBorderAnomalies())
                    .extracting(BorderAnomalyHolder::getType)
                    .containsOnly("void_tether");
            assertThat(VoidTetherService.getRemoveVoidTetherButtons(game, empyrean, null))
                    .hasSize(2);
        }
    }

    // The cards-info "Void Tether Access" button only shows while a tether actually blocks something.
    @Test
    void accessButtonOnlyWhileATetherIsEnforced() {
        try (var harness = TestGameHarness.fromSourceGame("game-with-border-anomalies")) {
            Game game = harness.load();
            game.setOneWayAdjacency(false);
            assertThat(VoidTetherService.hasEnforcedTether(game)).isEqualTo(game.isFowMode());

            game.setOneWayAdjacency(true);
            assertThat(VoidTetherService.hasEnforcedTether(game)).isTrue();
        }
    }
}
