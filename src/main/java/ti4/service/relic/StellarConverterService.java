package ti4.service.relic;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.DisasterWatchHelper;
import ti4.helpers.RandomHelper;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.service.unit.DestroyUnitService;

@UtilityClass
public class StellarConverterService {

    public static void resolveStellar(Game game, ButtonInteractionEvent event, String buttonID, Player player) {
        secondHalfOfStellar(game, buttonID.split("_")[1], event, player);
        ButtonHelper.deleteMessage(event);
    }

    public static void secondHalfOfStellar(
            Game game, String planetName, GenericInteractionCreateEvent event, Player player) {
        Tile tile = game.getTileContainingPlanet(planetName);
        if (tile == null) {
            MessageHelper.replyToMessage(event, "System not found that contains planet.");
            return;
        }
        UnitHolder unitHolder = tile.getPlanet(planetName);
        if (unitHolder == null) {
            MessageHelper.replyToMessage(event, "System not found that contains planet.");
            return;
        }

        String message1 = (RandomHelper.isOneInX(20)
                ? "# _Hey, Stellar!_"
                : "# There is a great disturbance in the Force, as if millions of voices suddenly cried out in terror and were suddenly silenced.");
        DisasterWatchHelper.postTileInDisasterWatch(
                game, event, tile, 1, "Moments before disaster in game " + game.getName() + ".");
        MessageHelper.sendMessageToChannel(game.getActionsChannel(), message1);
        Player p = null;
        for (Player p2 : game.getRealPlayers()) {
            if (p2.containsPlanet(planetName)) {
                p = p2;
                MessageHelper.sendMessageToChannel(
                        p2.getCorrectChannel(),
                        p2.getRepresentationUnfogged() + " we regret to inform you but "
                                + Mapper.getPlanet(planetName).getName() + " has been _Stellar Converter_'d.");
                DestroyUnitService.destroyAllUnits(event, tile, game, unitHolder, false);
            }
        }
        game.removePlanet(unitHolder);
        unitHolder.removeAllTokens();
        unitHolder.addToken(Constants.WORLD_DESTROYED_PNG);

        StringBuilder message2 = new StringBuilder();
        message2.append(Mapper.getPlanet(planetName).getName());
        message2.append(" has been _Stellar Converter_'d");
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), message2 + ".");

        message2.append(" by ");
        if (player == null) {
            player = game.getPlayer(event.getUser().getId());
        }
        message2.append(player.toString());
        DisasterWatchHelper.postTileInDisasterWatch(game, event, tile, 0, message2 + ".");
        if (game.isConventionsOfWarAbandonedMode() && tile.isHomeSystem(game) && !tile.hasPlanets() && p != null) {
            if (event instanceof ButtonInteractionEvent buttonEvent) {
                ButtonHelper.eliminatePlayer(game, buttonEvent, "eliminatePlayer_" + p.getFaction());
            }
        }
    }
}
