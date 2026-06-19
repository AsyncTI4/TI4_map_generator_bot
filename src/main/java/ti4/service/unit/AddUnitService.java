package ti4.service.unit;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.netrunners.NetrunnersAbilitiesHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.netrunners.NetrunnersLeadersHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.netrunners.NetrunnersUnitsHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.ta.TaUnitHandler;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitState;
import ti4.helpers.Units.UnitType;
import ti4.message.MessageHelper;
import ti4.model.UnitModel;
import ti4.service.emoji.ColorEmojis;
import ti4.service.planet.AddPlanetToPlayAreaService;
import ti4.service.planet.FlipTileService;
import ti4.service.unit.RemoveUnitService.RemovedUnit;

@UtilityClass
public class AddUnitService {

    public static void addUnits(GenericInteractionCreateEvent event, Game game, List<RemovedUnit> removedUnits) {
        for (RemovedUnit unit : removedUnits) {
            unit.uh().addUnitsWithStates(unit.unitKey(), unit.states());

            Tile tile = unit.tile();
            tile = FlipTileService.flipTileIfNeeded(tile, game);
            AddPlanetToPlayAreaService.addPlanetToPlayArea(
                    event, tile, unit.uh().getName(), game);
            if (game.getRealPlayers().stream().anyMatch(player -> player.hasAbility("system_breach"))) {
                NetrunnersAbilitiesHandler.resolveSystemBreach(game, unit.unitKey(), unit.getTotalRemoved());
            }
            if (game.getRealPlayers().stream().anyMatch(player -> player.hasUnit("netrunners_mech"))) {
                NetrunnersUnitsHandler.offerDeployMechWithStructure(
                        event, game, tile, unit.unitKey(), unit.uh().getName(), unit.getTotalRemoved());
            }
            if (game.getRealPlayers().stream().anyMatch(player -> player.hasLeader("netrunnerscommander"))) {
                NetrunnersLeadersHandler.checkCommanderUnlock(game, unit.unitKey());
            }
            Player player = game.getPlayerFromColorOrFaction(unit.unitKey().colorID());
            if (player != null && player.hasUnit("ta_flagship") && unit.unitKey().unitType() == UnitType.Flagship) {
                TaUnitHandler.offerWorldshaperOnFlagshipPlacement(
                        event, game, unit.unitKey(), unit.uh().getName(), tile);
            }

            String color = unit.unitKey().colorID();
            handleFogOfWar(tile, color, game, unit.unitKey() + " " + unit.getTotalRemoved());
            checkFleetCapacity(tile, color, game);
        }
    }

    public static void addUnits(
            GenericInteractionCreateEvent event,
            Tile tile,
            Game game,
            String color,
            String unitList,
            List<RemovedUnit> removed) {
        List<ParsedUnit> parsedUnits = ParseUnitService.getParsedUnits(event, color, tile, unitList);
        for (ParsedUnit parsedUnit : parsedUnits) {
            List<Integer> states = pickStatesForAddedUnit(parsedUnit, removed);
            tile.getUnitHolders().get(parsedUnit.location()).addUnitsWithStates(parsedUnit.unitKey(), states);
            tile = FlipTileService.flipTileIfNeeded(tile, game);
            AddPlanetToPlayAreaService.addPlanetToPlayArea(event, tile, parsedUnit.location(), game);
            if (game.getRealPlayers().stream().anyMatch(player -> player.hasAbility("system_breach"))) {
                NetrunnersAbilitiesHandler.resolveSystemBreach(game, parsedUnit.unitKey(), states.size());
            }
            if (game.getRealPlayers().stream().anyMatch(player -> player.hasUnit("netrunners_mech"))) {
                NetrunnersUnitsHandler.offerDeployMechWithStructure(
                        event, game, tile, parsedUnit.unitKey(), parsedUnit.location(), states.size());
            }
            if (game.getRealPlayers().stream().anyMatch(player -> player.hasLeader("netrunnerscommander"))) {
                NetrunnersLeadersHandler.checkCommanderUnlock(game, parsedUnit.unitKey());
            }
            Player player = game.getPlayerFromColorOrFaction(parsedUnit.unitKey().colorID());
            if (player != null
                    && player.hasUnit("ta_flagship")
                    && parsedUnit.unitKey().unitType() == UnitType.Flagship) {
                TaUnitHandler.offerWorldshaperOnFlagshipPlacement(
                        event, game, parsedUnit.unitKey(), parsedUnit.location(), tile);
            }
        }

        handleFogOfWar(tile, color, game, unitList);
        checkFleetCapacity(tile, color, game);
    }

    public static void addUnits(
            GenericInteractionCreateEvent event, Tile tile, Game game, String color, String unitList) {
        List<ParsedUnit> parsedUnits = ParseUnitService.getParsedUnits(event, color, tile, unitList);
        for (ParsedUnit parsedUnit : parsedUnits) {
            tile.addUnit(parsedUnit.location(), parsedUnit.unitKey(), parsedUnit.count());
            tile = FlipTileService.flipTileIfNeeded(tile, game);
            AddPlanetToPlayAreaService.addPlanetToPlayArea(event, tile, parsedUnit.location(), game);
            if (game.getRealPlayers().stream().anyMatch(player -> player.hasAbility("system_breach"))) {
                NetrunnersAbilitiesHandler.resolveSystemBreach(game, parsedUnit.unitKey(), parsedUnit.count());
            }
            if (game.getRealPlayers().stream().anyMatch(player -> player.hasUnit("netrunners_mech"))) {
                NetrunnersUnitsHandler.offerDeployMechWithStructure(
                        event, game, tile, parsedUnit.unitKey(), parsedUnit.location(), parsedUnit.count());
            }
            if (game.getRealPlayers().stream().anyMatch(player -> player.hasLeader("netrunnerscommander"))) {
                NetrunnersLeadersHandler.checkCommanderUnlock(game, parsedUnit.unitKey());
            }
            Player player = game.getPlayerFromColorOrFaction(parsedUnit.unitKey().colorID());
            if (player != null
                    && player.hasUnit("ta_flagship")
                    && parsedUnit.unitKey().unitType() == UnitType.Flagship) {
                TaUnitHandler.offerWorldshaperOnFlagshipPlacement(
                        event, game, parsedUnit.unitKey(), parsedUnit.location(), tile);
            }
        }

        handleFogOfWar(tile, color, game, unitList);
        checkFleetCapacity(tile, color, game);
    }

    /**
     * Ignore any locations in the provided unit list, and place them all in "default" locations.
     * This is useful when the unitList is for a different tile than what you're placing.
     * Ex. You draft a starting fleet, and place it on a different home system.
     */
    public static void addUnitsToDefaultLocations(
            GenericInteractionCreateEvent event, Tile tile, Game game, String color, String unitList) {
        Player player = game.getPlayerFromColorOrFaction(color);
        if (player == null) {
            throw new IllegalArgumentException("No player found for color/faction: " + color);
        }
        // Combine units of the same type, ignoring location
        Map<UnitType, Integer> unitCounts = Helper.getUnitList(unitList);
        // Get the planet names, ordered by resources descending
        List<String> planetNames = tile.getPlanetUnitHolders().stream()
                .sorted((ph1, ph2) -> Integer.compare(
                        ph2.getPlanetModel().getResources(),
                        ph1.getPlanetModel().getResources()))
                .map(UnitHolder::getName)
                .toList();

        // Distribute non-space units amongst planets evenly, and dump ships into space
        List<ParsedUnit> assignedUnits = new ArrayList<>();
        for (Entry<UnitType, Integer> entry : unitCounts.entrySet()) {
            UnitType unitType = entry.getKey();
            Integer totalAmt = entry.getValue();
            String asyncId = unitType.getValue().toLowerCase();
            if (player.getUnitsByAsyncID(asyncId).isEmpty()) {
                MessageHelper.sendMessageToChannel(
                        game.getActionsChannel(),
                        "Player " + player.getFactionEmojiOrColor() + " does not have any units of type "
                                + unitType.humanReadableName() + ". Skipping.");
                continue;
            }
            UnitModel mod = player.getUnitsByAsyncID(asyncId).getFirst();
            // Ships go to space
            if (mod.getIsShip()
                    || (unitType == UnitType.Spacedock
                            && (player.hasUnit("saar_spacedock") || player.hasUnit("tf-floatingfactory")))) {
                assignedUnits.add(new ParsedUnit(Units.getUnitKey(unitType, color), totalAmt, Constants.SPACE));
                continue;
            }

            // Non-ships get distributed to planets, prioritizing high-resource planets
            if (planetNames.isEmpty()) {
                MessageHelper.sendMessageToChannel(
                        game.getActionsChannel(),
                        "Could not find any planets for this unit list " + unitList + ". Let Fin know!");
                continue;
            }
            int minUnitsPerPlanet = totalAmt / planetNames.size();
            int remainder = totalAmt % planetNames.size();
            for (int i = 0; i < planetNames.size(); i++) {
                int amtForThisPlanet = minUnitsPerPlanet + (i < remainder ? 1 : 0);
                if (amtForThisPlanet > 0) {
                    assignedUnits.add(
                            new ParsedUnit(Units.getUnitKey(unitType, color), amtForThisPlanet, planetNames.get(i)));
                }
            }
        }

        // Now actually add the units
        StringBuilder unitListBuilder = new StringBuilder();
        boolean first = true;
        for (ParsedUnit parsedUnit : assignedUnits) {
            tile.addUnit(parsedUnit.location(), parsedUnit.unitKey(), parsedUnit.count());
            tile = FlipTileService.flipTileIfNeeded(tile, game);
            AddPlanetToPlayAreaService.addPlanetToPlayArea(event, tile, parsedUnit.location(), game);
            if (game.getRealPlayers().stream().anyMatch(player_ -> player_.hasAbility("system_breach"))) {
                NetrunnersAbilitiesHandler.resolveSystemBreach(game, parsedUnit.unitKey(), parsedUnit.count());
            }
            if (game.getRealPlayers().stream().anyMatch(player_ -> player_.hasUnit("netrunners_mech"))) {
                NetrunnersUnitsHandler.offerDeployMechWithStructure(
                        event, game, tile, parsedUnit.unitKey(), parsedUnit.location(), parsedUnit.count());
            }
            if (game.getRealPlayers().stream().anyMatch(player_ -> player_.hasLeader("netrunnerscommander"))) {
                NetrunnersLeadersHandler.checkCommanderUnlock(game, parsedUnit.unitKey());
            }
            if (player.hasUnit("ta_flagship") && parsedUnit.unitKey().unitType() == UnitType.Flagship) {
                TaUnitHandler.offerWorldshaperOnFlagshipPlacement(
                        event, game, parsedUnit.unitKey(), parsedUnit.location(), tile);
            }
            if (!first) {
                unitListBuilder.append(", ");
            }
            unitListBuilder
                    .append(parsedUnit.count())
                    .append(' ')
                    .append(parsedUnit.unitKey().asyncID())
                    .append(' ')
                    .append(parsedUnit.location());
            first = false;
        }

        handleFogOfWar(tile, color, game, unitListBuilder.toString());
        checkFleetCapacity(tile, color, game);
    }

    private static void handleFogOfWar(Tile tile, String color, Game game, String unitList) {
        if (!game.isFowMode()) return;

        if (isTileAlreadyPinged(game, tile)) return;

        FoWHelper.pingSystem(
                game,
                tile.getPosition(),
                ColorEmojis.getColorEmojiWithName(color) + " has modified units in the system: " + unitList);

        markTileAsPinged(game, tile);
    }

    private static boolean isTileAlreadyPinged(Game game, Tile tile) {
        return game.getListOfTilesPinged().contains(tile.getPosition());
    }

    private static void markTileAsPinged(Game game, Tile tile) {
        game.setTileAsPinged(tile.getPosition());
    }

    private static void checkFleetCapacity(Tile tile, String color, Game game) {
        Player player = game.getPlayerFromColorOrFaction(color);
        if (player != null) {
            ButtonHelper.checkFleetAndCapacity(player, game, tile);
        }
    }

    private static List<Integer> pickStatesForAddedUnit(ParsedUnit unit, List<RemovedUnit> removed) {
        int amtRemaining = unit.count();
        List<Integer> states = UnitState.emptyList();
        for (UnitState state : UnitState.defaultAddStatusOrder()) {
            for (RemovedUnit rm : removed) {
                if (!rm.unitKey().equals(unit.unitKey())) continue;
                if (rm.getTotalRemoved() == 0) continue; // depleted

                int amtRemoved = rm.states().get(state.ordinal());
                if (amtRemoved > 0 && amtRemaining > 0) {
                    int take = Math.min(amtRemaining, amtRemoved);
                    rm.states().set(state.ordinal(), amtRemoved - take);
                    states.set(state.ordinal(), states.get(state.ordinal()) + take);
                    amtRemaining -= take;
                }
            }
        }
        if (amtRemaining > 0) {
            states.set(0, states.getFirst() + amtRemaining);
        }
        return states;
    }
}
