package ti4.discord.interactions.buttons.handlers.faction.homebrew.goose.jotunn;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.helpers.adjacency.Adjacency;
import ti4.model.UnitModel;
import ti4.service.combat.CombatRollType;

@UtilityClass
public class JotunnAbilityHandler {
    public static boolean hasJotunnStructure(Player player, Game game, Tile tile) {
        return getJotunnStructureCount(player, tile) > 0;
    }

    public static int getJotunnStructureCount(Player player, Tile tile) {
        int structureCount = tile.getUnitHolders().values().stream()
                .flatMap(unitHolder -> unitHolder.getUnitKeysForPlayer(player).stream()
                        .filter(unitKey -> {
                            UnitModel unit = player.getUnitFromUnitKey(unitKey);
                            return unit != null && unit.getIsStructure();
                        })
                        .mapToInt(unitHolder::getUnitCount)
                        .boxed())
                .mapToInt(Integer::intValue)
                .sum();
        if (ButtonHelper.doesPlayerHaveFSHere("jotunn_flagship", player, tile)) {
            structureCount++;
        }
        if (player.hasUnit("jotunn_mech")) {
            structureCount += tile.getUnitHolders().values().stream()
                    .mapToInt(unitHolder -> unitHolder.getUnitCount(UnitType.Mech, player))
                    .sum();
        }
        return structureCount;
    }

    public static boolean isRimeShieldActive(Game game, Player roller, Tile tile, CombatRollType rollType) {
        if (rollType != CombatRollType.combatround) {
            return false;
        }

        return game.getRealPlayersExcludingThis(roller).stream()
                .filter(player -> player.hasAbility("rime_shields"))
                .anyMatch(player -> hasJotunnStructure(player, game, tile));
    }

    public static boolean hasWardstoneDoctrineCoverage(Game game, Player player, Tile activeTile) {
        return getWardstoneDoctrinePds(game, player, activeTile).isPresent();
    }

    public static Optional<Pair<UnitModel, UnitHolder>> getWardstoneDoctrinePds(
            Game game, Player player, Tile activeTile) {
        if (!player.hasAbility("wardstone_doctrine")) {
            return Optional.empty();
        }
        List<String> positions =
                connectedPdsSystems(game, player, activeTile).stream().sorted().toList();
        for (String position : positions) {
            Tile tile = game.getTileByPosition(position);
            if (tile == null) {
                continue;
            }
            if ((tile.isScar(game) && !player.hasTech("dsxinystar"))
                    || ButtonHelper.isTileSmothered(game, tile, player)) {
                continue;
            }
            for (UnitHolder unitHolder : tile.getUnitHolders().values()) {
                for (UnitKey unitKey : unitHolder.getUnitKeysForPlayer(player)) {
                    UnitModel unit = player.getUnitFromUnitKey(unitKey);
                    if (unitKey.unitType() == UnitType.Pds
                            && unit != null
                            && unit.getSpaceCannonDieCount(player) > 0
                            && !hasNormalPdsCoverage(game, player, activeTile, position, unit)) {
                        return Optional.of(new ImmutablePair<>(unit, unitHolder));
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static Set<String> connectedPdsSystems(Game game, Player player, Tile activeTile) {
        Adjacency adjacency = Adjacency.of(game, player).forEffects();
        Set<String> connected = new LinkedHashSet<>();
        Set<String> visited = new LinkedHashSet<>();
        Deque<String> systemsToCheck = new ArrayDeque<>();
        Set<String> adjacentToActive = adjacency.either(activeTile.getPosition());
        for (String position : adjacentToActive) {
            if (hasPds(game, player, position)) {
                visited.add(position);
                systemsToCheck.add(position);
                connected.add(position);
            }
        }
        while (!systemsToCheck.isEmpty()) {
            String position = systemsToCheck.removeFirst();
            for (String adjacentPosition : adjacency.either(position)) {
                if (!adjacentPosition.equals(activeTile.getPosition())
                        && hasPds(game, player, adjacentPosition)
                        && visited.add(adjacentPosition)) {
                    systemsToCheck.addLast(adjacentPosition);
                    if (!adjacentToActive.contains(adjacentPosition)) {
                        connected.add(adjacentPosition);
                    }
                }
            }
        }
        return connected;
    }

    private static boolean hasNormalPdsCoverage(
            Game game, Player player, Tile activeTile, String position, UnitModel unit) {
        return position.equals(activeTile.getPosition())
                || (Adjacency.of(game, player)
                                .forEffects()
                                .either(activeTile.getPosition())
                                .contains(position)
                        && (unit.getDeepSpaceCannon(player)
                                || game.playerHasLeaderUnlockedOrAlliance(player, "mirvedacommander")));
    }

    private static boolean hasPds(Game game, Player player, String position) {
        Tile tile = game.getTileByPosition(position);
        return tile != null
                && tile.getUnitHolders().values().stream()
                        .anyMatch(unitHolder -> unitHolder.getUnitCount(UnitType.Pds, player) > 0);
    }
}
