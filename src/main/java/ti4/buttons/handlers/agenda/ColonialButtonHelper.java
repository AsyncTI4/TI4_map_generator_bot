package ti4.buttons.handlers.agenda;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.commands.units.AddUnits;
import ti4.helpers.ButtonHelper;
import ti4.listeners.annotations.ButtonHandler;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;

@UtilityClass
class ColonialButtonHelper {

    @ButtonHandler("colonialRedTarget_")
    public static void resolveColonialRedTarget(Game game, String buttonID, ButtonInteractionEvent event) {
        Player p2 = game.getPlayerFromColorOrFaction(buttonID.split("_")[1]);
        if (p2 == null)
            return;
        String planet = buttonID.split("_")[2];
        Tile tile = game.getTileFromPlanet(planet);
        if (tile != null) {
            new AddUnits().unitParsing(event, p2.getColor(), tile, "1 inf " + planet, game);
        }
        MessageHelper.sendMessageToChannel(game.getMainGameChannel(),
            "1 " + p2.getColor() + " infantry was added to " + planet);
        ButtonHelper.deleteMessage(event);
    }
}
