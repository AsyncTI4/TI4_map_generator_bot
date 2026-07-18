package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Xytheris;

import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.CheckDistanceHelper;
import ti4.helpers.FoWHelper;
import ti4.helpers.thundersedge.TeHelperUnits;
import ti4.image.Mapper;
import ti4.model.UnitModel;
import ti4.service.combat.CombatRollType;

@UtilityClass
public class XytherisAbilityHandler {
    public Optional<Pair<UnitModel, UnitHolder>> getBestHiveEchoUnit(
            Tile tile, Player player, CombatRollType rollType) {
        Game game = player.getGame();
        if (game == null
                || !player.hasAbility("hive_echo")
                || (rollType != CombatRollType.SpaceCannonOffence
                        && !FoWHelper.playerHasActualShipsInSystem(player, tile))) {
            return Optional.empty();
        }

        return CheckDistanceHelper.getTileDistances(game, player, tile.getPosition(), 2, true).entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue() >= 1 && entry.getValue() <= 2)
                .map(Map.Entry::getKey)
                .map(game::getTileByPosition)
                .filter(remoteTile -> remoteTile != null
                        && !TeHelperUnits.affectedByQuietus(game, player, remoteTile)
                        && !remoteTile.isScar(game))
                .flatMap(remoteTile -> remoteTile.getUnitHolders().values().stream())
                .flatMap(remoteHolder ->
                        remoteHolder.getUnitAsyncIdsOnHolder(Mapper.getColorID(player.getColor())).entrySet().stream()
                                .filter(entry -> entry.getValue() > 0)
                                .<Pair<UnitModel, UnitHolder>>map(entry -> new ImmutablePair<>(
                                        player.getPriorityUnitByAsyncID(entry.getKey(), null), remoteHolder)))
                .filter(unit ->
                        unit.getLeft() != null && unit.getLeft().getCombatDieCountForAbility(rollType, player) > 0)
                .max(Comparator.comparingDouble(unit -> unit.getLeft().getCombatDieCountForAbility(rollType, player)
                        * (10 - unit.getLeft().getCombatDieHitsOnForAbility(rollType, player))
                        / 10.0d));
    }
}
