package ti4.ai.eval;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import lombok.experimental.UtilityClass;
import ti4.ai.scoring.Wallet;
import ti4.game.Game;
import ti4.game.Planet;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitState;
import ti4.helpers.Units.UnitType;
import ti4.model.UnitModel;

@UtilityClass
public class BoardView {

    public static final String SPACE = "space";
    public static final Set<UnitType> MOVING_SHIPS = EnumSet.of(
            UnitType.Destroyer,
            UnitType.Cruiser,
            UnitType.Carrier,
            UnitType.Dreadnought,
            UnitType.Flagship,
            UnitType.Warsun);
    public static final Set<UnitType> GROUND_FORCES = EnumSet.of(UnitType.Infantry, UnitType.Mech);
    public static final int DOCK_FIGHTER_ALLOWANCE = 3;
    private static final String GRAVITY_DRIVE = "gd";

    public static int count(@Nullable UnitHolder holder, Player player, UnitType type) {
        return holder == null ? 0 : holder.getUnitCount(type, player.getColor());
    }

    public static Map<UnitType, Integer> ships(@Nullable UnitHolder holder, Player player) {
        Map<UnitType, Integer> ships = new EnumMap<>(UnitType.class);
        if (holder == null) return ships;
        for (UnitKey key : holder.getUnitKeysForPlayer(player)) {
            UnitModel model = player.getUnitFromUnitKey(key);
            if (model != null && model.getIsShip()) ships.merge(key.unitType(), holder.getUnitCount(key), Integer::sum);
        }
        return ships;
    }

    public static int nonFighterShips(@Nullable UnitHolder holder, Player player) {
        return ships(holder, player).entrySet().stream()
                .filter(entry -> entry.getKey() != UnitType.Fighter)
                .mapToInt(Map.Entry::getValue)
                .sum();
    }

    public static int undamaged(@Nullable UnitHolder holder, Player player, UnitType type) {
        return holder == null ? 0 : holder.getUnitCountForState(type, player, UnitState.none);
    }

    public static int damaged(@Nullable UnitHolder holder, Player player, UnitType type) {
        if (holder == null) return 0;
        return holder.getUnitCountForState(type, player, UnitState.dmg)
                + holder.getUnitCountForState(type, player, UnitState.dmg_glv);
    }

    public static int countInTile(Tile tile, Player player, UnitType type) {
        return tile.getUnitHolders().values().stream()
                .mapToInt(holder -> count(holder, player, type))
                .sum();
    }

    public static UnitHolder space(Tile tile) {
        return tile.getUnitHolders().get(SPACE);
    }

    public static Optional<UnitModel> model(Player player, UnitType type) {
        return Optional.ofNullable(player.getUnitByType(type));
    }

    public static int moveValue(Player player, UnitType type) {
        return model(player, type).map(UnitModel::getMoveValue).orElse(0);
    }

    public static int moveValueWithGravityDrive(Player player, UnitType type) {
        return moveValue(player, type) + (player.hasTech(GRAVITY_DRIVE) ? 1 : 0);
    }

    public static int capacity(Player player, UnitType type) {
        return model(player, type).map(UnitModel::getCapacityValue).orElse(0);
    }

    public static boolean hasEnemyShips(Game game, Player player, Tile tile) {
        return FoWHelper.otherPlayersHaveShipsInSystem(player, tile, game);
    }

    public static boolean hasEnemyUnits(Game game, Player player, Tile tile) {
        return FoWHelper.otherPlayersHaveUnitsInSystem(player, tile, game);
    }

    public static boolean hasOwnShips(Player player, Tile tile) {
        return FoWHelper.playerHasActualShipsInSystem(player, tile);
    }

    public static boolean enemyGroundForcesOn(Game game, Player player, UnitHolder planet) {
        return game.getRealPlayers().stream()
                .filter(other -> other != player)
                .anyMatch(other -> GROUND_FORCES.stream().anyMatch(type -> count(planet, other, type) > 0));
    }

    public static boolean enemyStructuresOn(Game game, Player player, UnitHolder planet) {
        return game.getRealPlayers().stream()
                .filter(other -> other != player)
                .anyMatch(other ->
                        count(planet, other, UnitType.Pds) > 0 || count(planet, other, UnitType.Spacedock) > 0);
    }

    public static int groundForces(UnitHolder holder, Player player) {
        return count(holder, player, UnitType.Infantry) + count(holder, player, UnitType.Mech);
    }

    public static List<Planet> planets(Tile tile) {
        return tile.getPlanetUnitHolders();
    }

    @Nullable
    public static Player controller(Game game, String planet) {
        return game.getPlayerThatControlsPlanet(planet);
    }

    public static boolean hasCustodians(Planet planet) {
        return planet.getTokenList().contains(Constants.CUSTODIAN_TOKEN_PNG);
    }

    public static double planetValue(Planet planet) {
        double value = planet.getResources() + 0.6 * planet.getInfluence();
        if (planet.isLegendary()) value += 2;
        if (!planet.getTechSpecialities().isEmpty()) value += 0.3;
        return value;
    }

    public static int availableResources(Game game, Player player) {
        Integer planets = Helper.getPlayerResourcesAvailable(player, game);
        return (planets == null ? 0 : planets) + player.getTg() * Wallet.tradeGoodValue(player);
    }

    public static int planetResources(Game game, String planet) {
        return Helper.getPlanetResources(planet, game);
    }

    public static int planetInfluence(Game game, String planet) {
        return Helper.getPlanetInfluence(planet, game);
    }
}
