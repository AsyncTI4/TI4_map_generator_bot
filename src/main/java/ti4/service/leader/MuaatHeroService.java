package ti4.service.leader;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.helpers.DisasterWatchHelper;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Leader;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;

@UtilityClass
public class MuaatHeroService {

    public static void secondHalfOfNovaSeed(Player player, GenericInteractionCreateEvent event, Tile tile, Game game) {
        String message1 = "Moments before disaster in game " + game.getName() + ".";
        DisasterWatchHelper.postTileInDisasterWatch(game, event, tile, 1, message1);

        //Remove all other players units from the tile in question
        for (Player player_ : game.getPlayers().values()) {
            if (player_ != player) {
                tile.removeAllUnits(player_.getColor());
                tile.removeAllUnitDamage(player_.getColor());
            }
        }

        UnitHolder space = tile.getUnitHolders().get(Constants.SPACE);
        space.removeAllTokens();
        game.removeTile(tile.getPosition());

        //Add the muaat supernova to the map and copy over the space unitholder
        Tile novaTile = new Tile(AliasHandler.resolveTile("81"), tile.getPosition(), space);
        game.setTile(novaTile);

        String message2 = tile.getRepresentation() +
            " has been _Nova Seed_'d by " +
            player.getRepresentation() + ".";
        DisasterWatchHelper.postTileInDisasterWatch(game, event, novaTile, 1, message2);

        if (player.hasLeaderUnlocked("muaathero")) {
            Leader playerLeader = player.getLeader("muaathero").orElse(null);
            StringBuilder message = new StringBuilder(player.getRepresentation()).append(" played ").append(Helper.getLeaderFullRepresentation(playerLeader));
            boolean purged = player.removeLeader(playerLeader);
            if (purged) {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), message + " - Adjudicator Ba'al, the Muaat hero, has been purged.");
            } else {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Adjudicator Ba'al, the Muaat hero, was not purged - something went wrong.");
            }
        }
    }
}
