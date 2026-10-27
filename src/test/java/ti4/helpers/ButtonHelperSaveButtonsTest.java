package ti4.helpers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.components.tree.MessageComponentTree;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.testUtils.BaseTi4Test;

/**
 * Pins a real production bug: a message containing both a tracked button and a select menu (as
 * Objective Council's purge-menu messages do - a Confirm button alongside Stage I/II/Secrets select
 * menus) used to crash saveButtons with a ClassCastException, since it blindly cast every
 * ActionRowChildComponent to Button. Because ButtonContext.save() calls saveButtons() before the
 * actual game save, that crash silently skipped saving the game entirely for that interaction -
 * losing whatever state the button handler itself had just set (e.g. a player's confirmed flag).
 */
class ButtonHelperSaveButtonsTest extends BaseTi4Test {

    @Test
    void saveButtonsIgnoresSelectMenusSharingAMessageWithATrackedButton() {
        Game game = new Game();
        Player player = new Player("101", "testUser", game);
        player.setFaction("arborec");

        // Discord doesn't allow mixing a button and a select menu in the same ActionRow (AGENTS.md's
        // own mixing note), so the real failure shape is two separate rows in one message - one for
        // the select menu, one for the button - exactly how Objective Council's purge-menu messages
        // are built (one container+select-menu row per category, plus a trailing Confirm button row).
        ActionRow selectRow = ActionRow.of(StringSelectMenu.create("pick_101")
                .addOption("Option", "optionValue")
                .build());
        ActionRow buttonRow = ActionRow.of(Button.primary("confirm_101", "Confirm Choices"));
        MessageComponentTree tree = MessageComponentTree.of(selectRow, buttonRow);

        Message message = mock(Message.class);
        when(message.isEphemeral()).thenReturn(false);
        when(message.getContentRaw()).thenReturn("Choose your objectives");
        when(message.getComponentTree()).thenReturn(tree);

        TextChannel channel = mock(TextChannel.class);
        when(channel.getId()).thenReturn("chan-1");

        ButtonInteractionEvent event = mock(ButtonInteractionEvent.class);
        when(event.getMessage()).thenReturn(message);
        when(event.getMessageChannel()).thenReturn(channel);

        ButtonHelper.saveButtons(event, game, player);

        assertThat(game.getSavedButtons()).hasSize(1);
        assertThat(game.getSavedButtons().getFirst()).contains("Confirm Choices");
    }
}
