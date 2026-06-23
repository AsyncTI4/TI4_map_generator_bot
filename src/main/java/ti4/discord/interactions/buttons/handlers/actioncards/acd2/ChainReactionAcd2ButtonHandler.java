package ti4.discord.interactions.buttons.handlers.actioncards.acd2;

import java.util.ArrayList;
import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.helpers.DiceHelper;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;

@UtilityClass
class ChainReactionAcd2ButtonHandler {

    @ButtonHandler("resolveChainReaction")
    public static void resolveChainReaction(Player player, ButtonInteractionEvent event) {
        List<Button> combatValueButtons = new ArrayList<>();
        for (int combatValue = 1; combatValue <= 10; combatValue++) {
            combatValueButtons.add(
                    Buttons.gray("resolveChainReactionAt_" + combatValue, Integer.toString(combatValue)));
        }
        MessageHelper.sendMessageToChannelWithButtons(
                event.getChannel(),
                player.getRepresentation() + " choose the destroyed ship's combat value for _Chain Reaction_.",
                combatValueButtons);
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
    }

    @ButtonHandler("resolveChainReactionAt_")
    public static void resolveChainReactionAt(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        String combatValueText = buttonID.replace("resolveChainReactionAt_", "");
        int combatValue;
        try {
            combatValue = Integer.parseInt(combatValueText);
        } catch (NumberFormatException e) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "Could not resolve _Chain Reaction_ due to an invalid combat value.");
            ButtonHelper.deleteMessage(event);
            return;
        }
        if (combatValue < 1 || combatValue > 10) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "Could not resolve _Chain Reaction_ due to an invalid combat value.");
            ButtonHelper.deleteMessage(event);
            return;
        }

        int hits = 0;
        int pendingRolls = 3;
        List<DiceHelper.Die> rolls = new ArrayList<>();
        while (pendingRolls > 0 && hits < 3) {
            pendingRolls--;
            DiceHelper.Die roll = new DiceHelper.Die(combatValue);
            rolls.add(roll);
            if (roll.isSuccess()) {
                hits++;
                if (hits < 3) {
                    pendingRolls++;
                }
            }
        }

        StringBuilder message = new StringBuilder(player.getRepresentation())
                .append(" rolled for _Chain Reaction_ (combat value ")
                .append(combatValue)
                .append(").\n")
                .append(DiceHelper.formatDiceOutput(rolls));
        if (hits == 3) {
            message.append("\nMaximum hits reached.");
        }
        if (hits > 0) {
            String activeSystem = game.getActiveSystem();
            if (activeSystem == null || activeSystem.isEmpty()) {
                message.append("\nCould not find the active system, so assign the hits manually.");
            } else {
                List<Button> buttons = List.of(Buttons.red(
                        "getDamageButtons_" + activeSystem + "_spacecombat", "Assign Hit" + (hits == 1 ? "" : "s")));
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message.toString(), buttons);
                ButtonHelper.deleteMessage(event);
                return;
            }
        }

        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message.toString());
        ButtonHelper.deleteMessage(event);
    }
}
