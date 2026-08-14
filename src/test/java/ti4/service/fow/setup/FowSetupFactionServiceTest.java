package ti4.service.fow.setup;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.interactions.modals.ModalMapping;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.testUtils.BaseTi4Test;

/**
 * {@link PlayerSetupService} assigns faction/color and clears planets/techs/leaders before it checks
 * home-position validity, so a bad position must never be allowed to reach it - otherwise the player is
 * left half-configured. {@link FowSetupFactionService#resolvePositionModal} guards against this by
 * validating the typed position before touching any pending wizard state.
 */
class FowSetupFactionServiceTest extends BaseTi4Test {

    @Test
    void invalidCustomPositionIsRejectedBeforeAnyStateIsMutated() {
        Game game = new Game();
        game.setName("fow-position-validation-test");
        game.addPlayer("test-user-id", "test-user");

        FowSetupWizardState state = FowSetupWizardService.loadState(game);
        state.getPendingFactionByUserId().put("test-user-id", "winnu");
        FowSetupWizardService.saveState(game, state);

        ModalInteractionEvent event = mock(ModalInteractionEvent.class);
        when(event.getModalId()).thenReturn("fowSetupPositionResolve_test-user-id");
        ModalMapping positionValue = mock(ModalMapping.class);
        when(positionValue.getAsString()).thenReturn("not-a-real-position");
        when(event.getValue("position")).thenReturn(positionValue);

        FowSetupFactionService.resolvePositionModal(event, game);

        FowSetupWizardState after = FowSetupWizardService.loadState(game);
        assertTrue(
                after.getPendingPositionByUserId().isEmpty(),
                "an invalid position must never be recorded as pending (that only happens once "
                        + "startColorPick/setupPlayer would run)");
        assertTrue(
                after.getPendingFactionByUserId().containsKey("test-user-id"),
                "the player's pending faction pick must be untouched when position validation fails");
    }
}
