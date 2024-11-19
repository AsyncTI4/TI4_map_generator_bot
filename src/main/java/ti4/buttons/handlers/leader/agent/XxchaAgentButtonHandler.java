package ti4.buttons.handlers.leader.agent;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.commands.units.RemoveUnits;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Helper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

@UtilityClass
class XxchaAgentButtonHandler {

    @ButtonHandler("xxchaAgentRemoveInfantry_")
    public static void resolveXxchaAgentInfantryRemoval(Player player, Game game, ButtonInteractionEvent event, String buttonID) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        String planet = buttonID.split("_")[2];
        String planetRep = Helper.getPlanetRepresentation(planet, game);
        ButtonHelper.deleteMessage(event);
        new RemoveUnits().unitParsing(event, p2.getColor(), game.getTileFromPlanet(planet), "1 infantry " + planet, game);
        MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentationUnfogged() + " you removed 1 infantry from " + planetRep);
        MessageHelper.sendMessageToChannel(p2.getCorrectChannel(), p2.getRepresentationUnfogged() + " 1 infantry of yours on " + planetRep + " was removed via the Ggrocuto Rinn, the Xxcha agent.");
    }
}
