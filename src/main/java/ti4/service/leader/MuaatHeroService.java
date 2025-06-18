package ti4.service.leader;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.helpers.DisasterWatchHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitKey;
import ti4.map.Game;
import ti4.map.Leader;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.service.unit.DestroyUnitService;

@UtilityClass
public class MuaatHeroService {

    public static void secondHalfOfNovaSeed(Player muaat, GenericInteractionCreateEvent event, Tile tile, Game game) {
        String message1 = "Moments before disaster in game " + game.getName() + ".";
        DisasterWatchHelper.postTileInDisasterWatch(game, event, tile, 1, message1);

        // Destroy all other players units from the tile in question
        for (UnitHolder uh : tile.getUnitHolders().values()) {
            for (Player player_ : game.getPlayers().values()) {
                if (player_ == muaat)
                    continue; // skip muaat
                DestroyUnitService.destroyAllPlayerUnits(event, game, player_, tile, uh, false);
            }
        }

        //Add the muaat supernova to the map and copy over the space unitholder
        UnitHolder space = tile.getUnitHolders().get(Constants.SPACE);
        Tile novaTile = new Tile(AliasHandler.resolveTile("81"), tile.getPosition(), space);

        game.removeTile(tile.getPosition());
        game.setTile(novaTile);

        String message2 = tile.getRepresentation() + " has been _Nova Seed_'d by " + muaat.getRepresentation() + ".";
        DisasterWatchHelper.postTileInDisasterWatch(game, event, novaTile, 1, message2);

        if (muaat.hasLeaderUnlocked("muaathero")) {
            Leader playerLeader = muaat.getLeader("muaathero").orElse(null);
            StringBuilder message = new StringBuilder(muaat.getRepresentation()).append(" played ").append(Helper.getLeaderFullRepresentation(playerLeader));
            boolean purged = muaat.removeLeader(playerLeader);
            if (purged) {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), message + " - Adjudicator Ba'al, the Muaat hero, has been purged.");
            } else {
                MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Adjudicator Ba'al, the Muaat hero, was not purged - something went wrong.");
            }
        }
    }
}
