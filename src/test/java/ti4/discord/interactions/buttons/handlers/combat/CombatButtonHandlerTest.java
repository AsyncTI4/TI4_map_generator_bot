package ti4.discord.interactions.buttons.handlers.combat;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import org.junit.jupiter.api.Test;
import ti4.discord.interactions.buttons.Buttons;

class CombatButtonHandlerTest {

    @Test
    void remainingSpaceCannonDeclineButtonsAreDetected() {
        List<ActionRow> rows = List.of(ActionRow.of(
                Buttons.gray("combatRoll_1_space_spacecannonoffence", "Roll SPACE CANNON Offence"),
                Buttons.red("declinePDS_18_hacan", "Decline SPACE CANNON"),
                Buttons.red("declinePDS_18_sol", "Decline SPACE CANNON")));

        assertTrue(CombatButtonHandler.hasRemainingSpaceCannonDeclineButtons(rows));
    }

    @Test
    void noRemainingSpaceCannonDeclineButtonsMeansPromptCanBeDeleted() {
        List<ActionRow> rows = List.of(ActionRow.of(
                Buttons.gray("combatRoll_1_space_spacecannonoffence", "Roll SPACE CANNON Offence"),
                Buttons.gray("exhaustTech_gls", "Exhaust Graviton Laser System")));

        assertFalse(CombatButtonHandler.hasRemainingSpaceCannonDeclineButtons(rows));
    }
}
