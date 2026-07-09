package ti4.service.combat;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import ti4.discord.interactions.buttons.handlers.faction.homebrew.beans.netrunners.NetrunnersLeadersHandler;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.BombardmentAssignment;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitType;
import ti4.helpers.thundersedge.TeHelperUnits;
import ti4.image.Mapper;
import ti4.json.JsonMapperManager;
import ti4.model.PlanetModel;
import ti4.model.UnitModel;
import ti4.service.combat.CombatV2RollData.Request;
import tools.jackson.core.type.TypeReference;

/** Selects the units that participate in each kind of combat roll. */
@UtilityClass
public class CombatV2UnitService {

    public boolean checkIfUnitsOfType(Request request, CombatRollType rollType) {
        UnitHolder holder = request.unitHolder();
        return holder != null && !select(request, holder, rollType).units().isEmpty();
    }

    static UnitSelection select(Request request, UnitHolder holder, CombatRollType rollType) {
        return switch (rollType) {
            case combatround -> selectCombatRound(request, holder);
            case AFB -> selectAntiFighterBarrage(request, holder);
            case bombardment -> selectBombardment(request);
            case SpaceCannonOffence -> selectSpaceCannonOffense(request);
            case SpaceCannonDefence -> selectSpaceCannonDefense(request, holder);
        };
    }

    private static UnitSelection selectCombatRound(Request request, UnitHolder holder) {
        UnitSelection selected = selectCombatRoundUnits(request, holder, request.player());
        Map<Pair<UnitModel, UnitHolder>, Integer> units = new LinkedHashMap<>(selected.units());
        List<String> notices = new ArrayList<>(selected.notices());
        addCombatRoundVirtualUnits(request, holder, units);
        removeUnitsDisabledByArticlesOfWar(request, CombatRollType.combatround, units, notices);
        return new UnitSelection(units, notices);
    }

    private static UnitSelection selectAntiFighterBarrage(Request request, UnitHolder holder) {
        UnitSelection selected = selectAfbUnits(request, request.player());
        Map<Pair<UnitModel, UnitHolder>, Integer> units = new LinkedHashMap<>(selected.units());
        addAbilityUnits(request, holder, units);
        return new UnitSelection(units, selected.notices());
    }

    private static UnitSelection selectBombardment(Request request) {
        UnitSelection selected = selectBombardmentUnits(request, request.player());
        Map<Pair<UnitModel, UnitHolder>, Integer> units = new LinkedHashMap<>(selected.units());
        List<String> notices = new ArrayList<>(selected.notices());
        filterAssignedBombardmentUnits(request, units);
        removeUnitsDisabledByArticlesOfWar(request, CombatRollType.bombardment, units, notices);
        return new UnitSelection(units, notices);
    }

    private static UnitSelection selectSpaceCannonOffense(Request request) {
        UnitSelection selected = selectSpaceCannonOffenseUnits(request, request.player());
        return removeSpaceCannonUnitsDisabledByArticlesOfWar(request, selected);
    }

    private static UnitSelection selectSpaceCannonDefense(Request request, UnitHolder holder) {
        UnitSelection selected = selectSpaceCannonDefenseUnits(request, holder, request.player());
        return removeSpaceCannonUnitsDisabledByArticlesOfWar(request, selected);
    }

    private static UnitSelection removeSpaceCannonUnitsDisabledByArticlesOfWar(
            Request request, UnitSelection selected) {
        Map<Pair<UnitModel, UnitHolder>, Integer> units = new LinkedHashMap<>(selected.units());
        List<String> notices = new ArrayList<>(selected.notices());
        removeUnitsDisabledByArticlesOfWar(request, CombatRollType.SpaceCannonOffence, units, notices);
        return new UnitSelection(units, notices);
    }

    static Map<UnitModel, Integer> getUnitsInCombat(
            Request request, UnitHolder holder, Player player, CombatRollType rollType) {
        return selectBase(request, holder, player, rollType).flatUnits();
    }

    static Map<Pair<UnitModel, UnitHolder>, Integer> getUnitsInBombardment(Request request) {
        return selectBombardmentUnits(request, request.player()).units();
    }

    private static UnitSelection selectBase(
            Request request, UnitHolder holder, Player player, CombatRollType rollType) {
        return switch (rollType) {
            case combatround -> selectCombatRoundUnits(request, holder, player);
            case AFB -> selectAfbUnits(request, player);
            case bombardment -> selectBombardmentUnits(request, player);
            case SpaceCannonOffence -> selectSpaceCannonOffenseUnits(request, player);
            case SpaceCannonDefence -> selectSpaceCannonDefenseUnits(request, holder, player);
        };
    }

    static Player getOpponent(Request request, List<UnitHolder> holders) {
        Player player = request.player();
        Game game = request.game();
        String playerColorId = request.getColorId();
        List<Player> opponents = holders.stream()
                .flatMap(holder -> holder.getUnitColorsOnHolder().stream())
                .filter(color -> !color.equals(playerColorId))
                .map(game::getPlayerByColorID)
                .flatMap(Optional::stream)
                .distinct()
                .toList();
        if (opponents.isEmpty()) return null;

        Player opponent = opponents.getFirst();
        if (opponents.size() == 1) return opponent;
        opponent = preferActiveOpponent(game, opponents, opponent);
        opponent = preferHiredGunsOpponent(game, player, opponents, opponent);
        return preferNonAllianceOpponent(player, opponents, opponent);
    }

    private static Player preferActiveOpponent(Game game, List<Player> opponents, Player fallback) {
        for (Player opponent : opponents) {
            if (opponent.getUserID().equals(game.getActivePlayerID())) return opponent;
        }
        return fallback;
    }

    private static Player preferHiredGunsOpponent(Game game, Player player, List<Player> opponents, Player fallback) {
        String[] factions = game.getStoredValue("hiredGunsInPlay").split("_");
        if (factions.length < 2) return fallback;
        Player nokar = game.getPlayerFromColorOrFaction(factions[0]);
        Player activePlayer = game.getPlayerFromColorOrFaction(factions[1]);
        if (player != nokar && player != activePlayer) return fallback;
        for (Player opponent : opponents) {
            if (opponent != nokar && opponent != activePlayer) fallback = opponent;
        }
        return fallback;
    }

    private static Player preferNonAllianceOpponent(Player player, List<Player> opponents, Player fallback) {
        String allies = player.getAllianceMembers();
        if (allies.isEmpty() || fallback == null || !allies.contains(fallback.getFaction())) return fallback;
        for (Player opponent : opponents) {
            if (opponent != player && !allies.contains(opponent.getFaction())) fallback = opponent;
        }
        return fallback;
    }

    private static UnitSelection selectCombatRoundUnits(Request request, UnitHolder holder, Player player) {
        Tile tile = request.tile();
        UnitScan holderScan = scanUnits(player, List.of(holder), (ignored, asyncId) -> true);
        Map<Pair<UnitModel, UnitHolder>, Integer> units;
        if (!Constants.SPACE.equals(holder.getName())) {
            units = filter(holderScan.units(), unit -> unit.getIsGroundForce() || unit.getIsShip());
            return selection(List.of(holderScan), units, List.of());
        }

        units = filter(holderScan.units(), UnitModel::getIsShip);
        Collection<UnitHolder> systemHolders = tile.getUnitHolders().values();
        if (hasNekroFlagship(holderScan, player)) {
            UnitScan systemScan = scanUnits(player, systemHolders, (ignored, id) -> true);
            units = filter(systemScan.units(), unit -> unit.getIsGroundForce() || unit.getIsShip());
        } else if (player.hasUnit("purpletf_mech") || player.hasUnit("naaz_voltron")) {
            UnitScan systemScan = scanUnits(player, systemHolders, (ignored, id) -> true);
            units = filter(systemScan.units(), unit -> unit.getUnitType() == UnitType.Mech || unit.getIsShip());
        }

        Game game = request.game();
        if (game.isCosmicPhenomenaeMode() && tile.isAsteroidField() && !player.hasFF2Tech()) {
            units = filter(units, unit -> unit.getUnitType() != UnitType.Fighter);
        }
        return selection(List.of(holderScan), units, List.of());
    }

    private static boolean hasNekroFlagship(UnitScan scan, Player player) {
        return scan.rawAsyncIds().contains("fs")
                && (player.hasUnit("nekro_flagship") || player.hasUnit("sigma_nekro_flagship_2"));
    }

    private static UnitSelection selectAfbUnits(Request request, Player player) {
        Tile tile = request.tile();
        UnitScan scan = scanUnits(player, tile.getUnitHolders().values(), (ignored, asyncId) -> true);
        Map<Pair<UnitModel, UnitHolder>, Integer> units = filter(scan.units(), unit -> unit.getAfbDieCount(player) > 0);
        if (player.hasUnit("iron_flagship")) {
            addIronFlagshipMechs(player, tile, units);
        }
        return selection(List.of(scan), units, List.of());
    }

    private static UnitSelection selectBombardmentUnits(Request request, Player player) {
        Tile tile = request.tile();
        UnitScan scan = scanUnits(player, tile.getUnitHolders().values(), (ignored, asyncId) -> true);
        Map<Pair<UnitModel, UnitHolder>, Integer> units = localBombardmentUnits(player, tile, scan);
        Game game = request.game();
        if (game != null && game.playerHasLeaderUnlockedOrAlliance(player, "kaloracommander")) {
            addKaloraCommanderBombardmentUnits(game, player, tile, units);
        }
        return selection(List.of(scan), units, List.of());
    }

    private static Map<Pair<UnitModel, UnitHolder>, Integer> localBombardmentUnits(
            Player player, Tile tile, UnitScan scan) {
        return rehome(scan.units(), tile.getSpaceUnitHolder(), unit -> unit.getBombardDieCount(player) > 0);
    }

    private static void addKaloraCommanderBombardmentUnits(
            Game game, Player player, Tile target, Map<Pair<UnitModel, UnitHolder>, Integer> units) {
        for (String position : FoWHelper.getAdjacentTiles(game, target.getPosition(), player, false, false)) {
            Tile adjacent = game.getTileByPosition(position);
            if (adjacent == null) continue;
            UnitScan scan = scanUnits(player, adjacent.getUnitHolders().values(), (ignored, asyncId) -> true);
            localBombardmentUnits(player, adjacent, scan)
                    .forEach((unit, count) -> units.merge(unit, count, Integer::sum));
        }
    }

    private static void addIronFlagshipMechs(
            Player player, Tile tile, Map<Pair<UnitModel, UnitHolder>, Integer> units) {
        if (!ButtonHelper.doesPlayerHaveFSHere("iron_flagship", player, tile)) return;
        int count = tile.getSpaceUnitHolder().getUnitCount(UnitType.Mech, player);
        UnitModel mech = player.getUnitByType(UnitType.Mech);
        if (count < 1 || mech == null) return;

        UnitModel afbMech = new UnitModel();
        afbMech.setId(mech.getId() + "_flagship_afb");
        afbMech.setBaseType(mech.getBaseType());
        afbMech.setAsyncId(mech.getAsyncId());
        afbMech.setName(mech.getName());
        afbMech.setFaction(player.getFaction());
        afbMech.setIsGroundForce(mech.getIsGroundForce());
        afbMech.setAfbHitsOn(7);
        afbMech.setAfbDieCount(2);
        units.put(Pair.of(afbMech, tile.getSpaceUnitHolder()), count);
    }

    private static UnitSelection selectSpaceCannonDefenseUnits(Request request, UnitHolder holder, Player player) {
        if (!(holder instanceof Planet planet)) return UnitSelection.empty();
        UnitScan scan = scanUnits(player, List.of(holder), (ignored, asyncId) -> true);
        Map<Pair<UnitModel, UnitHolder>, Integer> units = new LinkedHashMap<>(scan.units());
        addControlledPlanetCannon(request.game(), player, planet, units);
        units = filter(units, unit -> unit.getSpaceCannonDieCount(player) > 0);
        return selection(List.of(scan), units, List.of());
    }

    private static UnitSelection selectSpaceCannonOffenseUnits(Request request, Player player) {
        Tile tile = request.tile();
        Game game = request.game();
        UnitHolder space = tile.getSpaceUnitHolder();
        UnitScan localScan = scanUnits(
                player,
                tile.getUnitHolders().values(),
                (holder, asyncId) -> includeSpaceCannonUnit(player, holder, asyncId));
        UnitScan adjacentScan = scanUnits(
                player,
                adjacentHolders(game, player, tile),
                (holder, asyncId) -> includeSpaceCannonUnit(player, holder, asyncId));

        Map<Pair<UnitModel, UnitHolder>, Integer> units = new LinkedHashMap<>(localScan.units());
        addPlanetSpaceCannons(game, player, tile, units);
        List<String> notices = addStarfallCannons(request, player, units);
        units = filter(units, unit -> unit.getSpaceCannonDieCount(player) > 0);
        addAdjacentSpaceCannons(game, player, adjacentScan.units(), units);
        if (game.playerHasLeaderUnlockedOrAlliance(player, "netrunnerscommander")) {
            addUnits(units, NetrunnersLeadersHandler.getCommanderSpaceCannonUnits(game, player, tile), space);
        }
        return selection(List.of(localScan, adjacentScan), units, notices);
    }

    private static boolean includeSpaceCannonUnit(Player player, UnitHolder holder, String asyncId) {
        if (!player.hasUnit("ralnel_destroyer2") || !Constants.SPACE.equalsIgnoreCase(holder.getName())) return true;
        if ("pd".equalsIgnoreCase(asyncId) || "sd".equalsIgnoreCase(asyncId)) return false;
        return !"dd".equalsIgnoreCase(asyncId) || holder.getUnitCount(UnitType.Pds, player) > 0;
    }

    private static List<UnitHolder> adjacentHolders(Game game, Player player, Tile tile) {
        List<UnitHolder> holders = new ArrayList<>();
        Set<String> positions = FoWHelper.getAdjacentTiles(game, tile.getPosition(), player, false);
        for (String position : positions) {
            if (position.equals(tile.getPosition())) continue;
            Tile adjacent = game.getTileByPosition(position);
            if (adjacent == null || TeHelperUnits.affectedByQuietus(game, player, adjacent) || adjacent.isScar(game)) {
                continue;
            }
            holders.addAll(adjacent.getUnitHolders().values());
        }
        return holders;
    }

    private static void addPlanetSpaceCannons(
            Game game, Player player, Tile tile, Map<Pair<UnitModel, UnitHolder>, Integer> units) {
        for (Planet planet : tile.getPlanetUnitHolders()) {
            addControlledPlanetCannon(game, player, planet, units);
            addSpaceStationCannons(game, player, planet, units);
        }
    }

    private static void addControlledPlanetCannon(
            Game game, Player player, Planet planet, Map<Pair<UnitModel, UnitHolder>, Integer> units) {
        String controlId = Mapper.getControlID(player.getColor());
        if (!planet.getControlList().contains(controlId)) return;

        int dice = planet.getSpaceCannonDieCount();
        int hitsOn = planet.getSpaceCannonHitsOn();
        if (player.controlsMecatol(true) && game.mecatols().contains(planet.getName()) && player.hasIIHQ()) {
            PlanetModel custodia = Mapper.getPlanet("custodiavigilia");
            dice = custodia.getSpaceCannonDieCount();
            hitsOn = custodia.getSpaceCannonHitsOn();
        }
        if (dice < 1) return;
        addVirtualUnit(units, planet, planetSpaceCannon(player, planet, dice, hitsOn), 1);
    }

    private static void addSpaceStationCannons(
            Game game, Player player, Planet planet, Map<Pair<UnitModel, UnitHolder>, Integer> units) {
        boolean tokenStation = (player.hasUnlockedBreakthrough("gledgebt") || player.hasTech("tf-mantlecracking"))
                && planet.getTokenList().contains(Constants.GLEDGE_CORE_PNG);
        boolean controlledStation = (planet.isSpaceStation(game) || tokenStation)
                && player.getPlanets().contains(planet.getName());
        if (!controlledStation) return;
        if (player.hasUnlockedBreakthrough("gledgebt")) {
            addVirtualUnit(units, planet, planetSpaceCannon(player, planet, 1, 5), 1);
        }
        if (player.hasTech("tf-deepinstallations")) {
            addVirtualUnit(units, planet, planetSpaceCannon(player, planet, 2, 5), 1);
        }
    }

    private static UnitModel planetSpaceCannon(Player player, Planet planet, int dice, int hitsOn) {
        PlanetModel planetModel = Mapper.getPlanet(planet.getName());
        String name = Helper.getPlanetRepresentationPlusEmoji(planetModel.getId()) + " space cannon";
        return virtualSpaceCannon(player, planet.getName() + "pds", name, dice, hitsOn);
    }

    private static List<String> addStarfallCannons(
            Request request, Player player, Map<Pair<UnitModel, UnitHolder>, Integer> units) {
        Tile tile = request.tile();
        Game game = request.game();
        UnitHolder space = tile.getSpaceUnitHolder();
        List<String> notices = new ArrayList<>();
        if (player.hasAbility("starfall_gunnery")) {
            if (player == game.getActivePlayer()) {
                int count = Math.min(3, ButtonHelper.checkNumberNonFighterShipsWithoutSpaceCannon(player, tile));
                if (count > 0) {
                    UnitModel starfall =
                            virtualSpaceCannon(player, "starfallpds", "Starfall Gunnery space cannon", 1, 8);
                    addVirtualUnit(units, space, starfall, count);
                }
            } else {
                notices.add(CombatV2Messages.starfallReminder(player));
            }
        }
        if (player.hasTech("tf-kinematicstarfall") && player == game.getActivePlayer()) {
            int count = Math.min(2, ButtonHelper.checkNumberNonFighterShipsWithoutSpaceCannon(player, tile));
            if (count > 0) {
                UnitModel starfall = virtualSpaceCannon(player, "starfallpds", "Starfall Gunnery space cannon", 1, 9);
                addVirtualUnit(units, space, starfall, count);
            }
        }
        return notices;
    }

    private static UnitModel virtualSpaceCannon(Player player, String id, String name, int dice, int hitsOn) {
        UnitModel unit = new UnitModel();
        unit.setSpaceCannonHitsOn(hitsOn);
        unit.setSpaceCannonDieCount(dice);
        unit.setName(name);
        unit.setAsyncId(id);
        unit.setId(id);
        unit.setBaseType("pds");
        unit.setFaction(player.getFaction());
        return unit;
    }

    private static void addAdjacentSpaceCannons(
            Game game,
            Player player,
            Map<Pair<UnitModel, UnitHolder>, Integer> adjacent,
            Map<Pair<UnitModel, UnitHolder>, Integer> output) {
        List<Map.Entry<Pair<UnitModel, UnitHolder>, Integer>> limited = new ArrayList<>();
        boolean mirveda = game.playerHasLeaderUnlockedOrAlliance(player, "mirvedacommander");
        for (var entry : adjacent.entrySet()) {
            UnitModel unit = entry.getKey().getLeft();
            if (unit == null || unit.getSpaceCannonDieCount(player) < 1) continue;
            if (unit.getDeepSpaceCannon(player)) {
                output.merge(entry.getKey(), entry.getValue(), Integer::sum);
            } else if (mirveda || "spacedock".equalsIgnoreCase(unit.getBaseType())) {
                limited.add(entry);
            }
        }
        if (limited.isEmpty()) return;
        limited.sort(adjacentCannonOrder(player));
        Map.Entry<Pair<UnitModel, UnitHolder>, Integer> best = limited.getFirst();
        output.merge(best.getKey(), 1, Integer::sum);
    }

    private static Comparator<Map.Entry<Pair<UnitModel, UnitHolder>, Integer>> adjacentCannonOrder(Player player) {
        return Comparator.comparingInt((Map.Entry<Pair<UnitModel, UnitHolder>, Integer> entry) ->
                        unitOf(entry).getSpaceCannonHitsOn(player))
                .thenComparing(Comparator.comparingInt((Map.Entry<Pair<UnitModel, UnitHolder>, Integer> entry) ->
                                unitOf(entry).getSpaceCannonDieCount(player))
                        .reversed())
                .thenComparing(entry -> holderOf(entry).getName())
                .thenComparing(entry -> unitOf(entry).getId());
    }

    private static UnitModel unitOf(Map.Entry<Pair<UnitModel, UnitHolder>, Integer> entry) {
        return entry.getKey().getLeft();
    }

    private static UnitHolder holderOf(Map.Entry<Pair<UnitModel, UnitHolder>, Integer> entry) {
        return entry.getKey().getRight();
    }

    private static UnitScan scanUnits(
            Player player, Collection<UnitHolder> holders, BiPredicate<UnitHolder, String> includeRawUnit) {
        Map<Pair<UnitModel, UnitHolder>, Integer> units = new LinkedHashMap<>();
        Set<String> rawAsyncIds = new LinkedHashSet<>();
        Set<String> missingAsyncIds = new LinkedHashSet<>();
        String colorId = Mapper.getColorID(player.getColor());
        for (UnitHolder holder : holders) {
            Map<String, Integer> holderUnits = holder.getUnitAsyncIdsOnHolder(colorId);
            for (var entry : holderUnits.entrySet()) {
                String asyncId = entry.getKey();
                if (!includeRawUnit.test(holder, asyncId)) continue;
                rawAsyncIds.add(asyncId);
                UnitModel unit = player.getPriorityUnitByAsyncID(asyncId, holder);
                if (unit == null) {
                    if (player.getUnitsByAsyncID(asyncId.toLowerCase()).isEmpty()) missingAsyncIds.add(asyncId);
                    continue;
                }
                units.merge(new ImmutablePair<>(unit, holder), entry.getValue(), Integer::sum);
            }
        }
        return new UnitScan(units, rawAsyncIds, missingAsyncIds);
    }

    private static void filterAssignedBombardmentUnits(
            Request request, Map<Pair<UnitModel, UnitHolder>, Integer> rollingUnits) {
        String target = request.storedValue("bombardmentTarget" + request.getFaction());
        if (target.isBlank()) return;

        List<BombardmentAssignment> assignments = JsonMapperManager.basic()
                .readValue(
                        request.storedValue("assignedBombardment" + request.getFaction()),
                        new TypeReference<List<BombardmentAssignment>>() {});
        Map<String, Integer> remainingByAsyncId = new HashMap<>();
        assignments.stream()
                .filter(assignment -> target.equals(assignment.planet()))
                .filter(assignment -> assignment.sourceId() != null)
                .forEach(assignment -> remainingByAsyncId.merge(assignment.sourceId(), 1, Integer::sum));

        for (Pair<UnitModel, UnitHolder> key : new ArrayList<>(rollingUnits.keySet())) {
            String asyncId = key.getLeft().getAsyncId();
            int available = remainingByAsyncId.getOrDefault(asyncId, 0);
            int count = Math.min(available, rollingUnits.get(key));
            if (count > 0) {
                rollingUnits.put(key, count);
                remainingByAsyncId.put(asyncId, available - count);
            } else {
                rollingUnits.remove(key);
            }
        }
    }

    private static void addAbilityUnits(
            Request request, UnitHolder holder, Map<Pair<UnitModel, UnitHolder>, Integer> rollingUnits) {
        Player player = request.player();
        if (player.hasRelic("metalivoidarmaments")) {
            rollingUnits.put(new ImmutablePair<>(metaliArmaments(player), holder), 1);
        }
        if (player.hasTech("tf-projectionofpow")) {
            rollingUnits.put(new ImmutablePair<>(projectionOfPower(player, 2), holder), 1);
        }
        if (player.hasAbility("projection_of_power")) {
            boolean adjacentDock =
                    ButtonHelper.getTilesOfPlayersSpecificUnits(request.game(), player, UnitType.Spacedock).stream()
                            .anyMatch(dockTile -> FoWHelper.getAdjacentTiles(
                                            request.game(), dockTile.getPosition(), player, false, true)
                                    .contains(request.getTilePosition()));
            if (adjacentDock) {
                rollingUnits.put(new ImmutablePair<>(projectionOfPower(player, 1), holder), 1);
            }
        }
    }

    private static void addCombatRoundVirtualUnits(
            Request request, UnitHolder holder, Map<Pair<UnitModel, UnitHolder>, Integer> rollingUnits) {
        Player player = request.player();
        if (request.playerHasActiveBreakthrough("zelianbt")) {
            for (UnitHolder planet : request.planetHolders()) {
                if (request.playerControlsPlanet(planet.getName())
                        && (Constants.SPACE.equalsIgnoreCase(request.unitHolderName())
                                || planet.getName().equalsIgnoreCase(request.unitHolderName()))) {
                    addPlanetUnit(request, holder, rollingUnits, planet);
                }
            }
        }
        if (player.hasTech("tf-hostileplanetoids") && Constants.SPACE.equalsIgnoreCase(request.unitHolderName())) {
            for (UnitHolder planet : request.planetHolders()) {
                if (request.playerControlsPlanet(planet.getName())) {
                    addPlanetUnit(request, holder, rollingUnits, planet);
                }
            }
        }
    }

    private static void addPlanetUnit(
            Request request,
            UnitHolder holder,
            Map<Pair<UnitModel, UnitHolder>, Integer> rollingUnits,
            UnitHolder planet) {
        int resources = Helper.getPlanetResources(planet.getName(), request.game());
        UnitModel unit = zelianPlanet(request.player(), Helper.getPlanetName(planet.getName()), 10 - resources);
        rollingUnits.put(new ImmutablePair<>(unit, holder), 1);
    }

    private static UnitModel metaliArmaments(Player player) {
        UnitModel unit = virtualUnit(player, "Metali Void Armaments", "MetaliAFB");
        unit.setAfbDieCount(3);
        unit.setAfbHitsOn(6);
        return unit;
    }

    private static UnitModel projectionOfPower(Player player, int dice) {
        UnitModel unit = virtualUnit(player, "Projection of Power", "projectionafb");
        unit.setAfbDieCount(dice);
        unit.setAfbHitsOn(6);
        return unit;
    }

    private static UnitModel zelianPlanet(Player player, String planetName, int hitsOn) {
        UnitModel unit = virtualUnit(player, "Zelian Planet " + planetName, "zelianplanet");
        unit.setCombatDieCount(1);
        unit.setCombatHitsOn(hitsOn);
        return unit;
    }

    private static UnitModel virtualUnit(Player player, String name, String id) {
        UnitModel unit = new UnitModel();
        unit.setName(name);
        unit.setAsyncId(id);
        unit.setId(id);
        unit.setBaseType("dd");
        unit.setFaction(player.getFaction());
        return unit;
    }

    private static void removeUnitsDisabledByArticlesOfWar(
            Request request,
            CombatRollType rollType,
            Map<Pair<UnitModel, UnitHolder>, Integer> rollingUnits,
            List<String> notices) {
        if (!ButtonHelper.isLawInPlay(request.game(), "articles_war")) return;
        boolean removedNaazMech = rollingUnits.keySet().stream()
                .anyMatch(pair -> "naaz_mech_space".equals(pair.getLeft().getAlias()));
        boolean removedXxchaMech = rollingUnits.keySet().stream()
                .anyMatch(pair -> "xxcha_mech".equals(pair.getLeft().getAlias()));
        Map<Pair<UnitModel, UnitHolder>, Integer> allowed = rollingUnits.entrySet().stream()
                .filter(entry ->
                        !disabledByArticlesOfWar(rollType, entry.getKey().getLeft()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        rollingUnits.clear();
        rollingUnits.putAll(allowed);
        if (removedNaazMech) {
            notices.add(CombatV2Messages.articlesOfWarNaazRokha());
        }
        if ((rollType == CombatRollType.SpaceCannonDefence || rollType == CombatRollType.SpaceCannonOffence)
                && removedXxchaMech) {
            notices.add(CombatV2Messages.articlesOfWarXxcha());
        }
    }

    private static boolean disabledByArticlesOfWar(CombatRollType rollType, UnitModel unit) {
        if (rollType == CombatRollType.combatround) {
            return "naaz_mech_space".equals(unit.getAlias());
        }
        if (rollType == CombatRollType.SpaceCannonDefence || rollType == CombatRollType.SpaceCannonOffence) {
            return "xxcha_mech".equals(unit.getAlias());
        }
        return rollType == CombatRollType.bombardment && "l1z1x_mech".equals(unit.getAlias());
    }

    private static Map<Pair<UnitModel, UnitHolder>, Integer> filter(
            Map<Pair<UnitModel, UnitHolder>, Integer> units, Predicate<UnitModel> eligible) {
        Map<Pair<UnitModel, UnitHolder>, Integer> selected = new LinkedHashMap<>();
        for (var entry : units.entrySet()) {
            UnitModel unit = entry.getKey().getLeft();
            if (unit != null && eligible.test(unit)) selected.put(entry.getKey(), entry.getValue());
        }
        return selected;
    }

    private static Map<Pair<UnitModel, UnitHolder>, Integer> rehome(
            Map<Pair<UnitModel, UnitHolder>, Integer> units, UnitHolder holder, Predicate<UnitModel> eligible) {
        Map<Pair<UnitModel, UnitHolder>, Integer> selected = new LinkedHashMap<>();
        for (var entry : units.entrySet()) {
            UnitModel unit = entry.getKey().getLeft();
            if (unit != null && eligible.test(unit)) {
                selected.merge(new ImmutablePair<>(unit, holder), entry.getValue(), Integer::sum);
            }
        }
        return selected;
    }

    private static void addUnits(
            Map<Pair<UnitModel, UnitHolder>, Integer> target, Map<UnitModel, Integer> additions, UnitHolder holder) {
        additions.forEach((unit, count) -> target.merge(new ImmutablePair<>(unit, holder), count, Integer::sum));
    }

    private static void addVirtualUnit(
            Map<Pair<UnitModel, UnitHolder>, Integer> units, UnitHolder holder, UnitModel unit, int count) {
        units.merge(new ImmutablePair<>(unit, holder), count, Integer::sum);
    }

    private static UnitSelection selection(
            List<UnitScan> scans, Map<Pair<UnitModel, UnitHolder>, Integer> units, List<String> extraNotices) {
        Set<String> missing = new LinkedHashSet<>();
        for (UnitScan scan : scans) missing.addAll(scan.missingAsyncIds());
        Set<UnitModel> distinctModels = new LinkedHashSet<>();
        for (Pair<UnitModel, UnitHolder> key : units.keySet()) distinctModels.add(key.getLeft());
        Set<String> seen = new LinkedHashSet<>();
        Set<String> duplicateTypes = new LinkedHashSet<>();
        for (UnitModel unit : distinctModels) {
            if (!seen.add(unit.getAsyncId())) duplicateTypes.add(unit.getBaseType());
        }

        List<String> notices = new ArrayList<>(extraNotices);
        if (!duplicateTypes.isEmpty()) {
            notices.add(CombatV2Messages.duplicateUnits(new ArrayList<>(duplicateTypes)));
        }
        if (!missing.isEmpty()) notices.add(CombatV2Messages.missingUnits(new ArrayList<>(missing)));
        return new UnitSelection(units, notices);
    }

    record UnitSelection(Map<Pair<UnitModel, UnitHolder>, Integer> units, List<String> notices) {
        UnitSelection {
            units = Map.copyOf(units);
            notices = List.copyOf(notices);
        }

        static UnitSelection empty() {
            return new UnitSelection(Map.of(), List.of());
        }

        Map<UnitModel, Integer> flatUnits() {
            Map<UnitModel, Integer> flattened = new LinkedHashMap<>();
            units.forEach((key, count) -> flattened.merge(key.getLeft(), count, Integer::sum));
            return flattened;
        }
    }

    private record UnitScan(
            Map<Pair<UnitModel, UnitHolder>, Integer> units, Set<String> rawAsyncIds, Set<String> missingAsyncIds) {}
}
