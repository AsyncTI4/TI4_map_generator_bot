package ti4.discord.interactions.buttons.handlers.actioncards.acd2;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.apache.commons.lang3.function.Consumers;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.ButtonHelper;
import ti4.helpers.FoWHelper;
import ti4.logging.BotLogger;
import ti4.message.MessageHelper;

@UtilityClass
class SisterShipAcd2ButtonHandler {

    @ButtonHandler("resolveSisterShip")
    public static void resolveSisterShip(Player player, Game game, ButtonInteractionEvent event) {
        event.getMessage().delete().queue(Consumers.nop(), BotLogger::catchRestError);
        List<Button> buttons = new ArrayList<>();
        Set<Tile> tiles = ButtonHelper.getTilesOfUnitsWithProduction(player, game);

        for (Tile tile : tiles) {
            if (!FoWHelper.otherPlayersHaveShipsInSystem(player, tile, game)) {
                String buttonID = "produceOneUnitInTile_" + tile.getPosition() + "_sling";
                Button tileButton = Buttons.green(buttonID, tile.getRepresentationForButtons(game, player));
                buttons.add(tileButton);
            }
        }
        MessageHelper.sendMessageToChannelWithButtons(
                event.getMessageChannel(),
                "Please choose which system you wish to produce a ship in. The bot will not know that it is reduced cost and limited to a specific ship type, but you know that. ",
                buttons);
    }
}
