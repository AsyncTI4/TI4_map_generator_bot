package ti4.discord.interactions.buttons.handlers.tech.specific;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.message.MessageHelper;
import ti4.service.unit.AddUnitService;

@UtilityClass
class DacxiveButtonHandler {

    @ButtonHandler("dacxive_")
    public static void daxcive(ButtonInteractionEvent event, Player player, String buttonID, Game game) {
        String planet = buttonID.replace("dacxive_", "");
        AddUnitService.addUnits(
                event, game.getTile(AliasHandler.resolveTile(planet)), game, player.getColor(), "infantry " + planet);
        MessageHelper.sendMessageToChannel(
                event.getChannel(),
                player.getFactionEmojiOrColor() + " placed 1 infantry on "
                        + Helper.getPlanetRepresentation(planet, game) + " via _Dacxive Animators_.");
        ButtonHelper.deleteMessage(event);
    }
}
