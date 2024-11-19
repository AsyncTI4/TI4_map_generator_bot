package ti4.service.explore;

import java.util.Collection;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.commands.tokens.AddToken;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Tile;
import ti4.map.UnitHolder;

@UtilityClass
public class AddFrontierTokensService {

    public static void addFrontierTokens(GenericInteractionCreateEvent event, Game game) {
        Collection<Tile> tileList = game.getTileMap().values();
        for (Tile tile : tileList) {
            if (((tile.getPlanetUnitHolders().isEmpty() && tile.getUnitHolders().size() == 2) || Mapper.getFrontierTileIds().contains(tile.getTileID())) && !game.isBaseGameMode()) {
                boolean hasMirage = false;
                for (UnitHolder unitholder : tile.getUnitHolders().values()) {
                    if (unitholder.getName().equals(Constants.MIRAGE)) {
                        hasMirage = true;
                        break;
                    }
                }
                if (!hasMirage) AddToken.addToken(event, tile, Constants.FRONTIER, game);
            }
        }
    }
}
