package ti4.service.unit;

import java.util.List;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.helpers.ButtonHelper;
import ti4.helpers.FoWHelper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.service.emoji.ColorEmojis;
import ti4.service.leader.CommanderUnlockCheckService;
import ti4.service.planet.AddPlanetToPlayAreaService;
import ti4.service.planet.FlipTileService;

@UtilityClass
public class AddUnitService {

    public static void addUnits(GenericInteractionCreateEvent event, Tile tile, Game game, String color, String unitList) {
        List<ParsedUnit> parsedUnits = ParseUnitService.getParsedUnits(event, color, tile, unitList);
        for (ParsedUnit parsedUnit : parsedUnits) {
            tile.addUnit(parsedUnit.getLocation(), parsedUnit.getUnitKey(), parsedUnit.getCount());
            tile = FlipTileService.flipTileIfNeeded(tile, game);
            AddPlanetToPlayAreaService.addPlanetToPlayArea(event, tile, parsedUnit.getLocation(), game);
        }

        handleFogOfWar(event, tile, color, game, unitList);
        checkFleetCapacity(event, tile, color, game);
    }

    private static void handleFogOfWar(GenericInteractionCreateEvent event, Tile tile, String color, Game game, String unitList) {
        if (!game.isFowMode()) return;

        if (isTileAlreadyPinged(game, tile)) return;

        String message = ColorEmojis.getColorEmojiWithName(color) + " has modified units in the system.";
        message += " Specific units modified: " + unitList;
        message += " Refresh map to see changes.";
        FoWHelper.pingSystem(game, event, tile.getPosition(), message);

        markTileAsPinged(game, tile);
    }

    private static boolean isTileAlreadyPinged(Game game, Tile tile) {
        for (int i = 0; i < 10; i++) {
            String tilePinged = game.getListOfTilesPinged()[i];
            if (tilePinged != null && tilePinged.equalsIgnoreCase(tile.getPosition())) {
                return true;
            }
        }
        return false;
    }

    private static void markTileAsPinged(Game game, Tile tile) {
        for (int i = 0; i < 10; i++) {
            if (game.getListOfTilesPinged()[i] == null) {
                game.setTileAsPinged(i, tile.getPosition());
                break;
            }
        }
    }

    private static void checkFleetCapacity(GenericInteractionCreateEvent event, Tile tile, String color, Game game) {
        Player player = game.getPlayerFromColorOrFaction(color);
        if (player != null) {
            ButtonHelper.checkFleetAndCapacity(player, game, tile, event);
            CommanderUnlockCheckService.checkPlayer(player, "naalu", "cabal");
        }
    }
}
