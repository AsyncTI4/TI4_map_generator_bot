package ti4.discord.interactions.buttons.handlers.faction.homebrew.beans;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.message.MessageHelper;

@UtilityClass
public class BeansButtonHandler {

    @ButtonHandler("beans_dream_remove_nexus_")
    public static void removeNexusToken(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        DreamButtonHandler.removeNexusToken(event, game, player, buttonID);
    }

    @ButtonHandler("beans_dream_add_nexus_")
    public static void addNexusToken(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
        DreamButtonHandler.addNexusToken(event, game, player, buttonID);
    }

    @ButtonHandler("beans_not_implemented")
    public static void beansNotImplemented(ButtonInteractionEvent event) {
        MessageHelper.sendMessageToEventChannel(event, "This Beans automation button is not implemented yet.");
    }
}