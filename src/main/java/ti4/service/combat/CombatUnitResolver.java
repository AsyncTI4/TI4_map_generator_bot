package ti4.service.combat;

import static org.apache.commons.lang3.StringUtils.*;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.Iron.IronUnitsHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.netrunners.NetrunnersLeadersHandler;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.whispers.kalora.KaloraLeaderHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.CombatMessageHelper;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitType;
import ti4.helpers.thundersedge.TeHelperUnits;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.PlanetModel;
import ti4.model.UnitModel;

@UtilityClass
public class CombatUnitResolver {

    public UnitModel getMetaliAFBUnit(Player player) {
        return buildSyntheticUnit(player, "Metali Void Armaments", "MetaliAFB", "dd", unit -> {
            unit.setAfbDieCount(3);
            unit.setAfbHitsOn(6);
        });
    }

    public UnitModel getProjectionUnit(Player player, boolean twilightsFall) {
        return buildSyntheticUnit(player, "Projection of Power", "projectionafb", "dd", unit -> {
            unit.setAfbDieCount(twilightsFall ? 2 : 1);
            unit.setAfbHitsOn(6);
        });
    }

    public UnitModel getZelianPlanetUnit(Player player, String planetName, int planetCombat) {
        return buildSyntheticUnit(player, "Zelian Planet " + planetName, "zelianplanet", "dd", unit -> {
            unit.setCombatDieCount(1);
            unit.setCombatHitsOn(planetCombat);
        });
    }

    private UnitModel buildSyntheticUnit(
            Player player, String name, String id, String baseType, Consumer<UnitModel> configuration) {
        UnitModel unit = new UnitModel();
        configuration.accept(unit);
        unit.setName(name);
        unit.setAsyncId(id);
        unit.setId(id);
        unit.setBaseType(baseType);
        unit.setFaction(player.getFaction());
        return unit;
    }

    private UnitModel buildSyntheticSpaceCannon(Player player, String name, String id, int dice, int hitsOn) {
        return buildSyntheticUnit(player, name, id, "pds", unit -> {
            unit.setSpaceCannonDieCount(dice);
            unit.setSpaceCannonHitsOn(hitsOn);
        });
    }

    public Player getOpponent(Player player, List<UnitHolder> unitHolders, Game game) {
        Player opponent = null;
        String playerColorID = Mapper.getColorID(player.getColor());
        List<Player> opponents = unitHolders.stream()
                .flatMap(holder -> holder.getUnitColorsOnHolder().stream())
                .filter(color -> !color.equals(playerColorID))
                .map(game::getPlayerByColorID)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .toList();
        if (!opponents.isEmpty()) opponent = opponents.getFirst();
        if (opponents.size() < 2) return opponent;

        opponent = preferActiveOpponent(game, opponents, opponent);
        opponent = excludeHiredGunsPartners(player, game, opponents, opponent);
        return excludeAllianceMembers(player, opponents, opponent);
    }

    private Player preferActiveOpponent(Game game, List<Player> opponents, Player fallback) {
        return opponents.stream()
                .filter(opponent -> opponent.getUserID().equals(game.getActivePlayerID()))
                .findAny()
                .orElse(fallback);
    }

    private Player excludeHiredGunsPartners(Player player, Game game, List<Player> opponents, Player fallback) {
        if (game.getStoredValue("hiredGunsInPlay").isEmpty()) return fallback;
        String[] hiredGuns = game.getStoredValue("hiredGunsInPlay").split("_");
        Player nokar = game.getPlayerFromColorOrFaction(hiredGuns[0]);
        Player activePlayer = game.getPlayerFromColorOrFaction(hiredGuns[1]);
        if (player != nokar && player != activePlayer) return fallback;
        return opponents.stream()
                .filter(opponent -> opponent != nokar && opponent != activePlayer)
                .reduce((first, second) -> second)
                .orElse(fallback);
    }

    private Player excludeAllianceMembers(Player player, List<Player> opponents, Player fallback) {
        if (player.getAllianceMembers().isEmpty()
                || fallback == null
                || !player.getAllianceMembers().contains(fallback.getFaction())) return fallback;
        return opponents.stream()
                .filter(opponent -> opponent != player)
                .filter(opponent -> !player.getAllianceMembers().contains(opponent.getFaction()))
                .reduce((first, second) -> second)
                .orElse(fallback);
    }

    public Map<UnitModel, Integer> getProximaBombardUnit(Player player) {
        UnitModel proxima =
                buildSyntheticUnit(player, Mapper.getTech("proxima").getName(), "ProximaBombard", "dn", unit -> {
                    unit.setBombardDieCount(3);
                    unit.setBombardHitsOn(player.hasTech("tf-proxima") ? 7 : 8);
                });
        return Map.of(proxima, 1);
    }

    public Map<UnitModel, Integer> flattenUnitMap(Map<Pair<UnitModel, UnitHolder>, Integer> units) {
        Map<UnitModel, Integer> flattened = new HashMap<>();
        units.forEach((key, count) -> flattened.merge(key.getLeft(), count, Integer::sum));
        return flattened;
    }

    private static Map<UnitModel, Integer> getCombatRoundUnits(
            Tile tile, UnitHolder unitHolder, Player player, GenericInteractionCreateEvent event) {
        String colorID = Mapper.getColorID(player.getColor());
        Map<String, Integer> unitsByAsyncId = unitHolder.getUnitAsyncIdsOnHolder(colorID);
        Map<UnitModel, Integer> output = CombatUnitSelectionHelper.collectCombatRoundUnits(tile, unitHolder, player);
        checkBadUnits(player, event, unitsByAsyncId, output);
        return output;
    }

    static Map<Pair<UnitModel, UnitHolder>, Integer> getUnitsInAFB(
            Tile tile, Player player, GenericInteractionCreateEvent event) {
        String colorID = Mapper.getColorID(player.getColor());
        UnitHolder spaceHolder = tile.getUnitHolders().get("space");

        Map<String, Integer> unitsByAsyncId = new HashMap<>();
        Map<Pair<UnitModel, UnitHolder>, Integer> output = new HashMap<>();
        for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
            Map<String, Integer> holderUnits = new HashMap<>();
            getUnitsOnHolderByAsyncId(colorID, holderUnits, unitHolder);
            holderUnits.forEach((k, v) -> unitsByAsyncId.merge(k, v, Integer::sum));
            for (var entry : holderUnits.entrySet()) {
                UnitModel model = player.getPriorityUnitByAsyncID(entry.getKey(), null);
                if (model != null && model.getAfbDieCount(player) > 0) {
                    output.merge(new ImmutablePair<>(model, unitHolder), entry.getValue(), Integer::sum);
                }
            }
        }
        if (player.hasUnit("iron_flagship")) {
            IronUnitsHandler.getIronFlagshipAfbUnits(player, tile)
                    .forEach((model, count) -> output.put(new ImmutablePair<>(model, spaceHolder), count));
        }
        Map<UnitModel, Integer> flatOutput = new HashMap<>();
        output.forEach((k, v) -> flatOutput.merge(k.getLeft(), v, Integer::sum));
        checkBadUnits(player, event, unitsByAsyncId, flatOutput);

        return output;
    }

    private static Map<UnitModel, Integer> getUnitsInCombat(Player player, Map<String, Integer> unitsByAsyncId) {
        return unitsByAsyncId.entrySet().stream()
                .map(entry ->
                        new ImmutablePair<>(player.getPriorityUnitByAsyncID(entry.getKey(), null), entry.getValue()))
                .collect(Collectors.toMap(Pair::getLeft, Pair::getRight));
    }

    private static void getUnitsOnHolderByAsyncId(
            String colorID, Map<String, Integer> unitsByAsyncId, UnitHolder unitHolder) {
        Map<String, Integer> unitsOnHolderByAsyncId = unitHolder.getUnitAsyncIdsOnHolder(colorID);
        for (Map.Entry<String, Integer> unitEntry : unitsOnHolderByAsyncId.entrySet()) {
            Integer existingCount = 0;
            if (unitsByAsyncId.containsKey(unitEntry.getKey())) {
                existingCount = unitsByAsyncId.get(unitEntry.getKey());
            }
            unitsByAsyncId.put(unitEntry.getKey(), existingCount + unitEntry.getValue());
        }
    }

    private static void getUnitsOnHolderByAsyncIdForSpaceCannon(
            String colorID, Map<String, Integer> unitsByAsyncId, UnitHolder unitHolder, Player player) {
        Map<String, Integer> unitsOnHolderByAsyncId = unitHolder.getUnitAsyncIdsOnHolder(colorID);
        for (Map.Entry<String, Integer> unitEntry : unitsOnHolderByAsyncId.entrySet()) {

            if (player.hasUnit("ralnel_destroyer2") && "space".equalsIgnoreCase(unitHolder.getName())) {
                if ("pd".equalsIgnoreCase(unitEntry.getKey()) || "sd".equalsIgnoreCase(unitEntry.getKey())) {
                    continue;
                }
                if ("dd".equalsIgnoreCase(unitEntry.getKey()) && (unitHolder.getUnitCount(UnitType.Pds, player) < 1)) {
                    continue;
                }
            }
            Integer existingCount = 0;
            if (unitsByAsyncId.containsKey(unitEntry.getKey())) {
                existingCount = unitsByAsyncId.get(unitEntry.getKey());
            }
            unitsByAsyncId.put(unitEntry.getKey(), existingCount + unitEntry.getValue());
        }
    }

    private static Map<UnitModel, Integer> getUnitsInSpaceCannonDefence(
            Planet planet, Player player, GenericInteractionCreateEvent event) {
        Game game = player.getGame();
        String colorID = Mapper.getColorID(player.getColor());

        Map<String, Integer> unitsByAsyncId = new HashMap<>();
        if (planet == null) {
            return new HashMap<>();
        }

        Map<String, Integer> unitsOnHolderByAsyncId = planet.getUnitAsyncIdsOnHolder(colorID);
        for (Map.Entry<String, Integer> unitEntry : unitsOnHolderByAsyncId.entrySet()) {
            Integer existingCount = 0;
            if (unitsByAsyncId.containsKey(unitEntry.getKey())) {
                existingCount = unitsByAsyncId.get(unitEntry.getKey());
            }
            unitsByAsyncId.put(unitEntry.getKey(), existingCount + unitEntry.getValue());
        }

        Map<UnitModel, Integer> unitsOnPlanet = unitsByAsyncId.entrySet().stream()
                .map(entry ->
                        new ImmutablePair<>(player.getPriorityUnitByAsyncID(entry.getKey(), null), entry.getValue()))
                .collect(Collectors.toMap(Pair::getLeft, Pair::getRight));

        // Check for space cannon die on planet
        PlanetModel planetModel = Mapper.getPlanet(planet.getName());
        String ccID = Mapper.getControlID(player.getColor());
        if (player.controlsMecatol(true) && game.mecatols().contains(planet.getName()) && player.hasIIHQ()) {
            PlanetModel custodiaVigilia = Mapper.getPlanet("custodiavigilia");
            planet.setSpaceCannonDieCount(custodiaVigilia.getSpaceCannonDieCount());
            planet.setSpaceCannonHitsOn(custodiaVigilia.getSpaceCannonHitsOn());
        }
        if (planet.getControlList().contains(ccID) && planet.getSpaceCannonDieCount() > 0) {
            UnitModel planetFakeUnit = buildSyntheticSpaceCannon(
                    player,
                    Helper.getPlanetRepresentationPlusEmoji(planetModel.getId()) + " space cannon",
                    planet.getName() + "pds",
                    planet.getSpaceCannonDieCount(),
                    planet.getSpaceCannonHitsOn());
            unitsOnPlanet.put(planetFakeUnit, 1);
        }

        Map<UnitModel, Integer> output = new HashMap<>(unitsOnPlanet.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getKey().getSpaceCannonDieCount(player) > 0)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));

        checkBadUnits(player, event, unitsByAsyncId, output);

        return output;
    }

    static Map<Pair<UnitModel, UnitHolder>, Integer> getUnitsInSpaceCannonOffense(
            Tile tile, Player player, GenericInteractionCreateEvent event, Game game) {
        String colorID = Mapper.getColorID(player.getColor());
        UnitHolder spaceHolder = tile.getUnitHolders().get("space");

        Map<String, Integer> unitsByAsyncId = new HashMap<>();
        Map<Pair<UnitModel, UnitHolder>, Integer> unitsOnTile = new HashMap<>();

        Collection<UnitHolder> unitHolders = tile.getUnitHolders().values();
        for (UnitHolder unitHolder : unitHolders) {
            Map<String, Integer> holderUnits = new HashMap<>();
            getUnitsOnHolderByAsyncIdForSpaceCannon(colorID, holderUnits, unitHolder, player);
            holderUnits.forEach((k, v) -> unitsByAsyncId.merge(k, v, Integer::sum));
            for (var entry : holderUnits.entrySet()) {
                UnitModel model = player.getPriorityUnitByAsyncID(entry.getKey(), null);
                if (model != null)
                    unitsOnTile.merge(new ImmutablePair<>(model, unitHolder), entry.getValue(), Integer::sum);
            }
        }

        Map<String, Integer> adjacentUnitsByAsyncId = new HashMap<>();
        Map<Pair<UnitModel, UnitHolder>, Integer> unitsOnAdjacentTiles = new HashMap<>();
        Set<String> adjTiles = FoWHelper.getAdjacentTiles(game, tile.getPosition(), player, false);
        for (String adjacentTilePosition : adjTiles) {
            if (adjacentTilePosition.equals(tile.getPosition())) {
                continue;
            }
            Tile adjTile = game.getTileByPosition(adjacentTilePosition);
            if (TeHelperUnits.affectedByQuietus(game, player, adjTile) || adjTile.isScar(game)) {
                continue;
            }
            for (UnitHolder unitHolder : adjTile.getUnitHolders().values()) {
                Map<String, Integer> holderUnits = new HashMap<>();
                getUnitsOnHolderByAsyncIdForSpaceCannon(colorID, holderUnits, unitHolder, player);
                holderUnits.forEach((k, v) -> adjacentUnitsByAsyncId.merge(k, v, Integer::sum));
                for (var entry : holderUnits.entrySet()) {
                    UnitModel model = player.getPriorityUnitByAsyncID(entry.getKey(), null);
                    if (model != null)
                        unitsOnAdjacentTiles.merge(
                                new ImmutablePair<>(model, unitHolder), entry.getValue(), Integer::sum);
                }
            }
        }

        // Check for space cannon die on planets

        for (UnitHolder unitHolder : unitHolders) {
            if (unitHolder instanceof Planet planet) {
                if (player.controlsMecatol(true) && game.mecatols().contains(planet.getName()) && player.hasIIHQ()) {
                    PlanetModel custodiaVigilia = Mapper.getPlanet("custodiavigilia");
                    planet.setSpaceCannonDieCount(custodiaVigilia.getSpaceCannonDieCount());
                    planet.setSpaceCannonHitsOn(custodiaVigilia.getSpaceCannonHitsOn());
                }
                PlanetModel planetModel = Mapper.getPlanet(planet.getName());
                String ccID = Mapper.getControlID(player.getColor());
                if (planet.getControlList().contains(ccID) && planet.getSpaceCannonDieCount() > 0) {
                    UnitModel planetFakeUnit = buildSyntheticSpaceCannon(
                            player,
                            Helper.getPlanetRepresentationPlusEmoji(planetModel.getId()) + " space cannon",
                            planet.getName() + "pds",
                            planet.getSpaceCannonDieCount(),
                            planet.getSpaceCannonHitsOn());
                    unitsOnTile.put(new ImmutablePair<>(planetFakeUnit, unitHolder), 1);
                }
                boolean spaceStation =
                        (player.hasUnlockedBreakthrough("gledgebt") || player.hasTech("tf-mantlecracking"))
                                && planet.getTokenList().contains(Constants.GLEDGE_CORE_PNG);
                if ((planet.isSpaceStation(game) || spaceStation)
                        && player.getPlanets().contains(planet.getName())) {
                    if (player.hasUnlockedBreakthrough("gledgebt")) {
                        UnitModel planetFakeUnit = buildSyntheticSpaceCannon(
                                player,
                                Helper.getPlanetRepresentationPlusEmoji(planetModel.getId()) + " space cannon",
                                planet.getName() + "pds",
                                1,
                                5);
                        unitsOnTile.put(new ImmutablePair<>(planetFakeUnit, unitHolder), 1);
                    }
                    if (player.hasTech("tf-deepinstallations")) {
                        UnitModel planetFakeUnit = buildSyntheticSpaceCannon(
                                player,
                                Helper.getPlanetRepresentationPlusEmoji(planetModel.getId()) + " space cannon",
                                planet.getName() + "pds",
                                2,
                                5);
                        unitsOnTile.put(new ImmutablePair<>(planetFakeUnit, unitHolder), 1);
                    }
                }
            }
        }
        if (player.hasAbility("starfall_gunnery")) {
            if (player == game.getActivePlayer()) {
                int count = Math.min(3, ButtonHelper.checkNumberNonFighterShipsWithoutSpaceCannon(player, tile));
                if (count > 0) {
                    UnitModel starfallFakeUnit =
                            buildSyntheticSpaceCannon(player, "Starfall Gunnery space cannon", "starfallpds", 1, 8);
                    unitsOnTile.put(new ImmutablePair<>(starfallFakeUnit, spaceHolder), count);
                }
            } else {
                MessageHelper.sendMessageToChannel(
                        event.getMessageChannel(),
                        player.getFactionEmoji()
                                + ", a reminder that due to the **Starfall Gunnery** ability, the SPACE CANNON of only 1 unit should be counted at this point."
                                + " Hopefully you declared beforehand what that unit was, but by default it's probably the best one. Only look at/count the rolls of that one unit.");
            }
        }

        if (player.hasTech("tf-kinematicstarfall")) {
            if (player == game.getActivePlayer()) {
                int count = Math.min(2, ButtonHelper.checkNumberNonFighterShipsWithoutSpaceCannon(player, tile));
                if (count > 0) {
                    UnitModel starfallFakeUnit =
                            buildSyntheticSpaceCannon(player, "Starfall Gunnery space cannon", "starfallpds", 1, 9);
                    unitsOnTile.put(new ImmutablePair<>(starfallFakeUnit, spaceHolder), count);
                }
            }
        }

        Map<Pair<UnitModel, UnitHolder>, Integer> output = new HashMap<>(unitsOnTile.entrySet().stream()
                .filter(entry -> entry.getKey().getLeft() != null
                        && entry.getKey().getLeft().getSpaceCannonDieCount(player) > 0)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));

        Map<Pair<UnitModel, UnitHolder>, Integer> adjacentOutput =
                new HashMap<>(unitsOnAdjacentTiles.entrySet().stream()
                        .filter(entry -> entry.getKey().getLeft() != null
                                && entry.getKey().getLeft().getSpaceCannonDieCount(player) > 0
                                && (entry.getKey().getLeft().getDeepSpaceCannon(player)
                                        || game.playerHasLeaderUnlockedOrAlliance(player, "mirvedacommander")
                                        || "spacedock"
                                                .equalsIgnoreCase(
                                                        entry.getKey().getLeft().getBaseType())))
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
        int limit = 0;
        for (var entry : adjacentOutput.entrySet()) {
            if (entry.getKey().getLeft().getDeepSpaceCannon(player)) {
                output.merge(entry.getKey(), entry.getValue(), Integer::sum);
            } else {
                if (limit < 1) {
                    limit = 1;
                    output.merge(entry.getKey(), 1, Integer::sum);
                }
            }
        }
        if (game.playerHasLeaderUnlockedOrAlliance(player, "netrunnerscommander")) {
            NetrunnersLeadersHandler.getCommanderSpaceCannonUnits(game, player, tile)
                    .forEach((model, count) ->
                            output.merge(new ImmutablePair<>(model, spaceHolder), count, Integer::sum));
        }

        Map<UnitModel, Integer> flatOutput = new HashMap<>();
        output.forEach((k, v) -> flatOutput.merge(k.getLeft(), v, Integer::sum));
        checkBadUnits(player, event, unitsByAsyncId, flatOutput);

        return output;
    }

    private static void checkBadUnits(
            Player player,
            GenericInteractionCreateEvent event,
            Map<String, Integer> unitsByAsyncId,
            Map<UnitModel, Integer> output) {
        Set<String> duplicates = new HashSet<>();
        List<String> dupes = output.keySet().stream()
                .filter(unit -> !duplicates.add(unit.getAsyncId()))
                .map(UnitModel::getBaseType)
                .toList();
        List<String> missing = unitsByAsyncId.keySet().stream()
                .filter(unit -> player.getUnitsByAsyncID(unit.toLowerCase()).isEmpty())
                .collect(Collectors.toList());

        if (!dupes.isEmpty()) {
            CombatMessageHelper.displayDuplicateUnits(event, missing);
        }
        if (!missing.isEmpty()) {
            CombatMessageHelper.displayMissingUnits(event, missing);
        }
    }

    public static Map<Pair<UnitModel, UnitHolder>, Integer> getUnitsInCombatByHolder(
            Tile tile,
            UnitHolder unitHolder,
            Player player,
            GenericInteractionCreateEvent event,
            CombatRollType roleType,
            Game game) {
        Planet unitHolderPlanet = unitHolder instanceof Planet p ? p : null;
        return switch (roleType) {
            case combatround -> {
                Map<Pair<UnitModel, UnitHolder>, Integer> result = new HashMap<>();
                getCombatRoundUnits(tile, unitHolder, player, event)
                        .forEach((model, count) -> result.put(new ImmutablePair<>(model, unitHolder), count));
                yield result;
            }
            case SpaceCannonDefence -> {
                Map<Pair<UnitModel, UnitHolder>, Integer> result = new HashMap<>();
                getUnitsInSpaceCannonDefence(unitHolderPlanet, player, event)
                        .forEach((model, count) -> result.put(new ImmutablePair<>(model, unitHolder), count));
                yield result;
            }
            case AFB -> getUnitsInAFB(tile, player, event);
            case bombardment -> getUnitsInBombardment(tile, player, event);
            case SpaceCannonOffence -> getUnitsInSpaceCannonOffense(tile, player, event, game);
        };
    }

    public static Map<UnitModel, Integer> getUnitsInCombat(
            Tile tile,
            UnitHolder unitHolder,
            Player player,
            GenericInteractionCreateEvent event,
            CombatRollType roleType,
            Game game) {
        Map<UnitModel, Integer> result = new HashMap<>();
        getUnitsInCombatByHolder(tile, unitHolder, player, event, roleType, game)
                .forEach((key, value) -> result.merge(key.getLeft(), value, Integer::sum));
        return result;
    }

    public static Map<Pair<UnitModel, UnitHolder>, Integer> getUnitsInBombardment(
            Tile tile, Player player, GenericInteractionCreateEvent event) {
        String colorID = Mapper.getColorID(player.getColor());
        UnitHolder spaceHolder = tile.getUnitHolders().get("space");
        Map<String, Integer> unitsByAsyncId = new HashMap<>();
        for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
            getUnitsOnHolderByAsyncId(colorID, unitsByAsyncId, unitHolder);
        }
        Map<UnitModel, Integer> unitsInCombat = getUnitsInCombat(player, unitsByAsyncId);

        Map<Pair<UnitModel, UnitHolder>, Integer> output = new HashMap<>(unitsInCombat.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getKey().getBombardDieCount(player) > 0)
                .collect(Collectors.toMap(
                        entry -> new ImmutablePair<>(entry.getKey(), spaceHolder), Map.Entry::getValue)));
        Map<UnitModel, Integer> flatOutput = new HashMap<>();
        output.forEach((k, v) -> flatOutput.merge(k.getLeft(), v, Integer::sum));
        checkBadUnits(player, event, unitsByAsyncId, flatOutput);
        if (player.getGame() != null && player.getGame().playerHasLeaderUnlockedOrAlliance(player, "kaloracommander")) {
            KaloraLeaderHandler.addCommanderBombardmentUnits(player, tile, output);
        }
        return output;
    }
}
