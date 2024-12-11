package ti4.service.leader;

import java.util.List;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperAgents;
import ti4.helpers.Constants;
import ti4.helpers.DisasterWatchHelper;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Leader;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;

@UtilityClass
public class ZelianHeroService {

    public static void secondHalfOfCelestialImpact(Player player, GenericInteractionCreateEvent event, Tile tile, Game game) {
        String message1 = "Moments before disaster in game " + game.getName();
        DisasterWatchHelper.postTileInDisasterWatch(game, event, tile, 1, message1);

        //Remove all other players ground force units from the tile in question
        for (Player player_ : game.getPlayers().values()) {
            if (player_ != player) {
                for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                    if (!unitHolder.getName().equals(Constants.SPACE)) {
                        unitHolder.removeAllUnits(player_.getColor());
                    }
                }
            }
        }

        //Gain TGs equal to the sum of the resource values of the planets in the system
        int resourcesSum = 0;
        List<Planet> planetsInSystem = tile.getPlanetUnitHolders().stream().toList();
        for (Planet p : planetsInSystem) {
            resourcesSum += p.getResources();
        }
        String tgGainMsg = player.getFactionEmoji() + " gained " + resourcesSum + "TG" + (resourcesSum == 1 ? "" : "s") + " from Celestial Impact (" +
            player.getTg() + "->" + (player.getTg() + resourcesSum) + ").";
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), tgGainMsg);
        player.gainTG(resourcesSum);
        ButtonHelperAbilities.pillageCheck(player, game);
        ButtonHelperAgents.resolveArtunoCheck(player, resourcesSum);

        //Add the zelian asteroid field to the map and copy over the space unitholder
        UnitHolder space = tile.getUnitHolders().get(Constants.SPACE);
        game.removeTile(tile.getPosition());
        Tile asteroidTile = new Tile(AliasHandler.resolveTile("D36"), tile.getPosition(), space);
        game.setTile(asteroidTile);

        //After shot to disaster channel
        String message2 = tile.getRepresentation() +
            " has been celestially impacted by " +
            player.getRepresentation();
        DisasterWatchHelper.postTileInDisasterWatch(game, event, asteroidTile, 1, message2);

        if (player.hasLeaderUnlocked("zelianhero")) {
            Leader playerLeader = player.getLeader("zelianhero").orElse(null);
            StringBuilder message = new StringBuilder(player.getRepresentation()).append(" played ").append(Helper.getLeaderFullRepresentation(playerLeader));
            boolean purged = player.removeLeader(playerLeader);
            if (purged) {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), message + " - Zelian R, the Zelian heRo, has been purged");
            } else {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Zelian R, the Zelian heRo, was not purged - something went wrong");
            }
        }
    }
}
