package ti4.service.unit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import org.jetbrains.annotations.NotNull;
import ti4.helpers.Constants;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitState;
import ti4.helpers.Units.UnitType;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.service.planet.AddPlanetToPlayAreaService;

@UtilityClass
public class RemoveUnitService {

    public record RemovedUnit(UnitKey unitKey, Tile tile, UnitHolder uh, List<Integer> states) {
        public int getTotalRemoved() {
            return states.stream()
                    .filter(Objects::nonNull)
                    .mapToInt(Integer::intValue)
                    .sum();
        }

        public RemovedUnit onUnitHolder(UnitHolder uh2) {
            return new RemovedUnit(unitKey, tile, uh2, states);
        }

        public RemovedUnit onUnitHolder(Tile t2, String uh2) {
            return new RemovedUnit(unitKey, t2, t2.getUnitHolders().get(uh2), states);
        }

        public RemovedUnit withColorID(String colorID) {
            return new RemovedUnit(Units.getUnitKey(unitKey.unitType(), colorID), tile, uh, states);
        }

        public Player getPlayer(Game game) {
            return game.getPlayerFromColorOrFaction(unitKey().colorID());
        }
    }

    @NotNull
    public List<RemovedUnit> removeAllUnits(
            GenericInteractionCreateEvent event, Tile tile, Game game, UnitHolder unitHolder) {
        List<RemovedUnit> removed = new ArrayList<>();
        for (UnitKey uk : Set.copyOf(unitHolder.getUnitsByState().keySet())) {
            ParsedUnit u = new ParsedUnit(uk, unitHolder.getUnitCount(uk), unitHolder.getName());
            removed.addAll(removeUnit(event, tile, game, u));
        }
        return removed;
    }

    @NotNull
    public List<RemovedUnit> removeAllPlayerUnits(
            GenericInteractionCreateEvent event, Game game, Player player, Tile tile, UnitHolder unitHolder) {
        List<RemovedUnit> removed = new ArrayList<>();
        for (UnitKey uk : Set.copyOf(unitHolder.getUnitsByStateForPlayer(player).keySet())) {
            ParsedUnit u = new ParsedUnit(uk, unitHolder.getUnitCount(uk), unitHolder.getName());
            removed.addAll(removeUnit(event, tile, game, u));
        }
        return removed;
    }

    @NotNull
    public List<RemovedUnit> removeAllPlayerNonStructureUnits(
            GenericInteractionCreateEvent event, Game game, Player player, Tile tile, UnitHolder unitHolder) {
        List<RemovedUnit> removed = new ArrayList<>();
        for (UnitKey uk : Set.copyOf(unitHolder.getUnitsByStateForPlayer(player).keySet())) {
            if (uk.unitType() == UnitType.Pds || uk.unitType() == UnitType.Spacedock) {
                continue;
            }
            ParsedUnit u = new ParsedUnit(uk, unitHolder.getUnitCount(uk), unitHolder.getName());
            removed.addAll(removeUnit(event, tile, game, u));
        }
        return removed;
    }

    public static List<RemovedUnit> removeUnit(
            GenericInteractionCreateEvent event,
            Tile tile,
            Game game,
            Player player,
            UnitHolder uh,
            UnitType unit,
            int amt) {
        ParsedUnit pu = ParseUnitService.simpleParsedUnit(player, unit, uh, amt);
        return removeUnit(event, tile, game, pu);
    }

    public static List<RemovedUnit> removeUnit(
            GenericInteractionCreateEvent event,
            Tile tile,
            Game game,
            Player player,
            UnitHolder uh,
            UnitType unit,
            int amt,
            boolean prioDamage) {
        ParsedUnit pu = ParseUnitService.simpleParsedUnit(player, unit, uh, amt);
        return removeUnit(event, tile, game, pu, prioDamage);
    }

    public static List<RemovedUnit> removeUnit(
            GenericInteractionCreateEvent event,
            Tile tile,
            Game game,
            Player player,
            UnitHolder uh,
            UnitType unit,
            int amt,
            UnitState state) {
        ParsedUnit pu = ParseUnitService.simpleParsedUnit(player, unit, uh, amt);
        return removeUnit(event, tile, game, pu, state);
    }

    @NotNull
    public static List<RemovedUnit> removeUnits(
            GenericInteractionCreateEvent event, Tile tile, Game game, String color, String unitList) {
        return removeUnits(event, tile, game, color, unitList, true);
    }

    @NotNull
    public static List<RemovedUnit> removeUnits(
            GenericInteractionCreateEvent event,
            Tile tile,
            Game game,
            String color,
            String unitList,
            boolean prioritizeDamagedUnits) {
        List<ParsedUnit> parsedUnits = ParseUnitService.getParsedUnits(event, color, tile, unitList);

        List<RemovedUnit> unitsRemoved = new ArrayList<>();
        for (ParsedUnit parsedUnit : parsedUnits) {
            var removedUnit = removeUnit(event, tile, game, parsedUnit, prioritizeDamagedUnits);
            unitsRemoved.addAll(removedUnit);
        }
        return unitsRemoved;
    }

    @NotNull
    public static List<RemovedUnit> removeUnit(
            GenericInteractionCreateEvent event, Tile tile, Game game, ParsedUnit parsedUnit) {
        return removeUnit(event, tile, game, parsedUnit, true);
    }

    @NotNull
    public static List<RemovedUnit> removeUnit(
            GenericInteractionCreateEvent event,
            Tile tile,
            Game game,
            ParsedUnit parsedUnit,
            boolean prioritizeDamagedUnits) {
        return removeUnit(event, tile, game, parsedUnit, UnitState.dmg);
    }

    public static List<RemovedUnit> removeUnit(
            GenericInteractionCreateEvent event,
            Tile tile,
            Game game,
            ParsedUnit parsedUnit,
            UnitState preferredState) {
        List<UnitHolder> unitHoldersToRemoveFrom = getUnitHoldersToRemoveFrom(tile, parsedUnit);

        if (unitHoldersToRemoveFrom.isEmpty()) {
            handleEmptyUnitHolders(event, tile, parsedUnit);
            return List.of();
        }

        int toRemoveCount = parsedUnit.getCount();
        List<RemovedUnit> allUnitsRemoved = new ArrayList<>();
        for (UnitHolder unitHolder : unitHoldersToRemoveFrom) {
            List<Integer> unitsRemovedCount =
                    unitHolder.removeUnit(parsedUnit.getUnitKey(), toRemoveCount, preferredState);

            int tot = unitsRemovedCount.stream().mapToInt(i -> i).sum();
            if (tot > 0) {
                allUnitsRemoved.add(new RemovedUnit(parsedUnit.getUnitKey(), tile, unitHolder, unitsRemovedCount));
                toRemoveCount -= tot;
            }

            if (toRemoveCount == 0) {
                break;
            }
        }

        if (toRemoveCount > 0 && event != null) {
            MessageHelper.replyToMessage(event, "Did not find enough units to remove, " + toRemoveCount + " missing.");
        }

        tile.getUnitHolders()
                .values()
                .forEach(unitHolder ->
                        AddPlanetToPlayAreaService.addPlanetToPlayArea(event, tile, unitHolder.getName(), game));
        return allUnitsRemoved;
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

    private static int countUnitsInHolder(UnitHolder holder, UnitKey unitKey) {
        return holder.getUnitCount(unitKey);
    }

    private static void handleEmptyUnitHolders(GenericInteractionCreateEvent event, Tile tile, ParsedUnit parsedUnit) {
        if (event instanceof ButtonInteractionEvent) {
            BotLogger.warning(
                    new BotLogger.LogMessageOrigin(event),
                    event.getId() + " found a null UnitHolder with the following info: " + tile.getRepresentation()
                            + " " + parsedUnit.getLocation());
        } else if (event != null) {
            MessageHelper.replyToMessage(event, "Unable to determine where the units are being removed from.");
        }
    }
}
