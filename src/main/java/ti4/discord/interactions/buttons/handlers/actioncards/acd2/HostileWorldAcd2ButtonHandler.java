package ti4.discord.interactions.buttons.handlers.actioncards.acd2;

import java.util.List;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.DiceHelper;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;

@UtilityClass
class HostileWorldAcd2ButtonHandler {

    @ButtonHandler("resolveHostileWorld")
    public static void resolveHostileWorld(Player player, Game game, ButtonInteractionEvent event) {
        List<DiceHelper.Die> rolls = DiceHelper.rollDice(6, 3);
        int hits = DiceHelper.countSuccesses(rolls);
        StringBuilder message = new StringBuilder(player.getRepresentation())
                .append(" rolled for _Hostile World_.\n")
                .append(DiceHelper.formatDiceOutput(rolls));
        String loreQuip = getHostileWorldLoreQuip(hits);
        if (loreQuip != null) {
            message.append("\n").append(loreQuip);
        }
        if (hits > 0) {
            String activeSystem = game.getActiveSystem();
            if (activeSystem == null || activeSystem.isEmpty()) {
                message.append("\nCould not find the active system, so assign the hits manually.");
            } else {
                List<Button> buttons = List.of(Buttons.red(
                        "getDamageButtons_" + activeSystem + "_groundcombat", "Assign Hit" + (hits == 1 ? "" : "s")));
                MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), message.toString(), buttons);
                event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
                return;
            }
        }
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message.toString());
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
    }

    private static String getHostileWorldLoreQuip(int hits) {
        return switch (hits) {
            case 0 -> "\"Must've been the wind.\" - Guard, _Skyrim_.";
            case 1 -> "\"Get off my lawn.\" — Walt Kowalski, _Gran Torino_";
            case 2 -> "\"Watch out for that first step, it's a doozy!\" - Ned Ryerson, _Groundhog Day_";
            case 3 ->
                "\"One does not simply walk into Mordor.\" - Boromir, _The Lord of the Rings: The Fellowship of the Ring_";
            default -> null;
        };
    }
}
