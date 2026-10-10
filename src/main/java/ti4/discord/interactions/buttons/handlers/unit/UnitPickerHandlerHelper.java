package ti4.discord.interactions.buttons.handlers.unit;

import lombok.experimental.UtilityClass;
import ti4.discord.interactions.buttons.ids.UnitPickButtonIds;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.Constants;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.service.unit.ParsedUnit;

@UtilityClass
class UnitPickerHandlerHelper {

    public ParsedUnit ownParsedUnit(Player player, UnitPickButtonIds.Parsed picked) {
        UnitKey key = Units.getUnitKey(picked.unitType(), player.getColorID());
        String location = picked.onPlanet() ? picked.planetName() : Constants.SPACE;
        return new ParsedUnit(key, picked.amount(), location);
    }

    public UnitHolder pickedUnitHolder(Tile tile, UnitPickButtonIds.Parsed picked) {
        return picked.onPlanet() ? tile.getUnitHolderFromPlanet(picked.planetName()) : tile.getSpaceUnitHolder();
    }

    public String pickedLocation(
            Game game, Player player, Tile tile, UnitHolder holder, UnitPickButtonIds.Parsed picked) {
        return picked.onPlanet() && holder != null
                ? " on " + holder.getRepresentation(game)
                : " in tile " + tile.getRepresentationForButtons(game, player);
    }
}
