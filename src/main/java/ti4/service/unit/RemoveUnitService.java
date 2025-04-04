package ti4.service.unit;

import java.util.Collections;
import java.util.List;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import ti4.helpers.AliasHandler;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Units;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.planet.AddPlanetToPlayAreaService;

@UtilityClass
public class RemoveUnitService {

    public static void removeUnits(GenericInteractionCreateEvent event, Tile tile, Game game, String color, String unitList) {
        removeUnits(event, tile, game, color, unitList, true);
    }

    public static void removeUnits(GenericInteractionCreateEvent event, Tile tile, Game game, String color, String unitList, boolean prioritizeDamagedUnits) {
        List<ParsedUnit> parsedUnits = ParseUnitService.getParsedUnits(event, color, tile, unitList);
        for (ParsedUnit parsedUnit : parsedUnits) {
            removeUnit(event, tile, game, parsedUnit, prioritizeDamagedUnits);
        }
    }

    public static void removeUnit(GenericInteractionCreateEvent event, Tile tile, Game game, ParsedUnit parsedUnit) {
        removeUnit(event, tile, game, parsedUnit, true);
    }

    public static void removeUnit(GenericInteractionCreateEvent event, Tile tile, Game game, ParsedUnit parsedUnit, boolean prioritizeDamagedUnits) {
        List<UnitHolder> unitHoldersToRemoveFrom = getUnitHoldersToRemoveFrom(tile, parsedUnit);

        if (unitHoldersToRemoveFrom.isEmpty()) {
            handleEmptyUnitHolders(event, tile, parsedUnit);
            return;
        }

        int toRemoveCount = parsedUnit.getCount();
        for (UnitHolder unitHolder : unitHoldersToRemoveFrom) {
            int oldUnitCount = unitHolder.getUnitCount(parsedUnit.getUnitKey());
            int unitsRemovedCount = unitHolder.removeUnit(parsedUnit.getUnitKey(), toRemoveCount);

            int damagedToRemove = getNumberOfDamagedUnitsToRemove(unitHolder, parsedUnit.getUnitKey(), prioritizeDamagedUnits, oldUnitCount, unitsRemovedCount);
            unitHolder.removeDamagedUnit(parsedUnit.getUnitKey(), damagedToRemove);

            toRemoveCount -= unitsRemovedCount;
            if (toRemoveCount == 0) {
                break;
            }
        }

        if (toRemoveCount > 0) {
            MessageHelper.replyToMessage(event, "Did not find enough units to remove, " + toRemoveCount + " missing.");
        }
        Player player = game.getPlayerFromColorOrFaction(parsedUnit.getUnitKey().getColor());
        if (player != null) {
            if (player.hasAbility("necrophage") && player.getCommoditiesTotal() < 5 && !player.getFaction().contains("franken")) {
                player.setCommoditiesTotal(1 + ButtonHelper.getNumberOfUnitsOnTheBoard(game,
                    Mapper.getUnitKey(AliasHandler.resolveUnit("spacedock"), player.getColor())));
            }
        }

        tile.getUnitHolders().values().forEach(unitHolder -> AddPlanetToPlayAreaService.addPlanetToPlayArea(event, tile, unitHolder.getName(), game));
    }

    private static int getNumberOfDamagedUnitsToRemove(UnitHolder unitHolder, Units.UnitKey unitKey, boolean prioritizeDamagedUnits, int oldUnitCount, int unitsRemoved) {
        if (prioritizeDamagedUnits) {
            return unitsRemoved;
        }
        int damagedUnitCount = unitHolder.getDamagedUnitCount(unitKey);
        int undamagedUnitCount = oldUnitCount - damagedUnitCount;
        return Math.max(0, unitsRemoved - undamagedUnitCount);
    }

    private static List<UnitHolder> getUnitHoldersToRemoveFrom(Tile tile, ParsedUnit parsedUnit) {
        if (!parsedUnit.getLocation().equals(Constants.SPACE)) { // We are removing from a specific planet.
            var planet = tile.getUnitHolders().get(parsedUnit.getLocation());
            return planet == null ? Collections.emptyList() : List.of(planet);
        }
        // Otherwise, the location was not specified, so we check everywhere
        return tile.getUnitHolders().values().stream()
            .filter(unitHolderTemp -> countUnitsInHolder(unitHolderTemp, parsedUnit.getUnitKey()) > 0)
            .toList();
    }

    private static int countUnitsInHolder(UnitHolder holder, Units.UnitKey unitKey) {
        int unitCount = holder.getUnits().getOrDefault(unitKey, 0);
        int damageCount = holder.getUnitDamage().getOrDefault(unitKey, 0);
        return unitCount + damageCount;
    }

    private static void handleEmptyUnitHolders(GenericInteractionCreateEvent event, Tile tile, ParsedUnit parsedUnit) {
        if (event != null && event instanceof ButtonInteractionEvent) {
            BotLogger.warning(new BotLogger.LogMessageOrigin(event), event.getId() + " found a null UnitHolder with the following info: " + tile.getRepresentation() + " " + parsedUnit.getLocation());
        } else {
            MessageHelper.replyToMessage(event, "Unable to determine where the units are being removed from.");
        }
    }
}
