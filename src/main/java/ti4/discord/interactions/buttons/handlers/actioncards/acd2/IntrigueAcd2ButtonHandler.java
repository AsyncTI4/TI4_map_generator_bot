package ti4.discord.interactions.buttons.handlers.actioncards.acd2;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.AgendaHelper;
import ti4.logging.BotLogger;

@UtilityClass
class IntrigueAcd2ButtonHandler {

    @ButtonHandler("resolveIntrigue")
    public static void resolveIntrigue(Player player, Game game, ButtonInteractionEvent event) {
        AgendaHelper.drawAgenda(2, true, game, player);
        AgendaHelper.drawAgenda(2, game, player);
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
    }
}
