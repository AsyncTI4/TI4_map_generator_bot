package ti4.discord.interactions.buttons.handlers.combat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.tree.MessageComponentTree;
import org.junit.jupiter.api.Test;
import ti4.discord.interactions.buttons.Buttons;
import ti4.service.combat.StartCombatService;

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

    @Test
    void clickedDeclineButtonIsRemovedFromUpdatedRows() {
        MessageComponentTree componentTree = MessageComponentTree.of(List.of(ActionRow.of(
                Buttons.gray("combatRoll_1_space_spacecannonoffence", "Roll SPACE CANNON Offence"),
                Buttons.red(StartCombatService.DECLINE_PDS_BUTTON_PREFIX + "18_hacan", "Decline SPACE CANNON"),
                Buttons.red(StartCombatService.DECLINE_PDS_BUTTON_PREFIX + "18_sol", "Decline SPACE CANNON"))));

        List<ActionRow> updatedRows = CombatButtonHandler.buildUpdatedSpaceCannonDeclineRows(
                componentTree, StartCombatService.DECLINE_PDS_BUTTON_PREFIX + "18_hacan");

        assertEquals(2, updatedRows.getFirst().getButtons().size());
        assertFalse(updatedRows.getFirst().getButtons().stream()
                .anyMatch(button -> StartCombatService.DECLINE_PDS_BUTTON_PREFIX
                        .concat("18_hacan")
                        .equals(button.getCustomId())));
        assertTrue(updatedRows.getFirst().getButtons().stream()
                .anyMatch(button -> StartCombatService.DECLINE_PDS_BUTTON_PREFIX
                        .concat("18_sol")
                        .equals(button.getCustomId())));
    }
}
