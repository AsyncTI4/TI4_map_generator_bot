package ti4.discord.interactions.buttons.handlers.actioncards.acd2;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Player;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.logging.BotLogger;

@UtilityClass
class SideProjectAcd2ButtonHandler {

    @ButtonHandler("sideProject")
    public static void resolveSideProject(Player player, ButtonInteractionEvent event) {
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
        ButtonHelperFactionSpecific.offerWinnuStartingTech(player);
    }
}
