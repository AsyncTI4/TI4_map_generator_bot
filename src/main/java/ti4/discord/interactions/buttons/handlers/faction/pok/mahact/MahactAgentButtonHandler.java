package ti4.discord.interactions.buttons.handlers.faction.pok.mahact;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.discord.interactions.routing.ButtonHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.CommandCounterHelper;
import ti4.helpers.Helper;
import ti4.image.Mapper;
import ti4.message.MessageHelper;

@UtilityClass
class MahactAgentButtonHandler {

    @ButtonHandler("placeHolderOfConInSystem_")
    public static void placeHolderOfConInSystem(
            GenericInteractionCreateEvent event, Game game, Player player, String buttonID) {
        String planet = buttonID.replace("placeHolderOfConInSystem_", "");
        String tileID = AliasHandler.resolveTile(planet.toLowerCase());
        Tile tile = game.getTile(tileID);
        if (tile == null) {
            tile = game.getTileByPosition(tileID);
        }
        if (tile == null) {
            MessageHelper.sendMessageToChannel(
                    event.getMessageChannel(), "Could not resolve tileID:  `" + tileID + "`. Tile not found");
            return;
        }
        Player constructionPlayer = player;
        for (Player p2 : game.getRealPlayers()) {
            if (p2.hasStrategyCard(4)) {
                constructionPlayer = p2;
            }
        }
        CommandCounterHelper.addCC(event, constructionPlayer, tile);

        String colorName = Mapper.getColor(constructionPlayer.getColor()).getDisplayName();
        String message = player.toString() + " placed 1 " + colorName + " command token in the "
                + Helper.getPlanetRepresentation(planet, game)
                + " system due to use of " + (player.hasUnexhaustedLeader("yssarilagent") ? "Clever Clever " : "")
                + "Jae Mir Kan, the Mahact" + (player.hasUnexhaustedLeader("yssarilagent") ? "/Yssaril" : "")
                + " agent on **Construction**.";
        ButtonHelper.sendMessageToRightStratThread(player, game, message, "construction");
        if (!game.isFowMode()) {
            ButtonHelper.updateMap(game, event);
        }
        ButtonHelper.deleteMessage(event);
    }
}
