package ti4.discord.interactions.buttons.handlers.actioncards.acd2;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ButtonHelper;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;

@UtilityClass
class ShrapnelTurretsAcd2ButtonHandler {

    @ButtonHandler("resolveShrapnelTurrets_")
    public static void resolveShrapnelTurrets(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        game.setStoredValue("ShrapnelTurretsFaction", player.getFaction());
        if (buttonID.contains("_")) {
            ButtonHelper.resolveCombatRoll(player, game, event, "combatRoll_" + buttonID.split("_")[1] + "_space_afb");
        } else {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "Could not find active system. You will need to roll using `/roll`.");
        }
        game.setStoredValue("ShrapnelTurretsFaction", "");
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
    }
}
