package ti4.discord.interactions.buttons.handlers.faction.homebrew.theodisi.Kryxos;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import org.apache.commons.lang3.tuple.Pair;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.Units.UnitKey;
import ti4.image.Mapper;
import ti4.model.UnitModel;
import ti4.service.combat.CombatRollType;

@UtilityClass
public class KryxosBreakthroughHandler {
    private static final String PROTOTYPE_INNOVATORS_STATE = "kryxosbtGalvanization_";

    // Automatically applies Prototype Innovators to the best eligible units participating in this roll.
    // The effect is intentionally replaced for every roll, rather than left on the map for the whole action.
    public static void refreshPrototypeInnovators(
            Game game,
            Player player,
            Tile tile,
            Map<Pair<UnitModel, UnitHolder>, Integer> unitsByQuantity,
            CombatRollType rollType) {
        if (game == null || player == null) return;

        clearPrototypeInnovators(game, player);
        if (!player.hasUnlockedBreakthrough("kryxosbt") || tile == null || unitsByQuantity == null) return;

        int remaining = 3;
        List<String> state = new ArrayList<>();
        List<Map.Entry<Pair<UnitModel, UnitHolder>, Integer>> eligibleUnits = unitsByQuantity.entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue() > 0)
                .filter(entry ->
                        isPrototypeInnovatorsEligible(player, entry.getKey().getLeft(), rollType))
                .sorted(Comparator.<Map.Entry<Pair<UnitModel, UnitHolder>, Integer>>comparingInt(
                                entry -> prototypeInnovatorsValue(entry.getKey().getLeft(), rollType, player))
                        .reversed())
                .toList();

        for (Map.Entry<Pair<UnitModel, UnitHolder>, Integer> entry : eligibleUnits) {
            if (remaining == 0) break;

            UnitModel unit = entry.getKey().getLeft();
            UnitHolder holder = entry.getKey().getRight();
            UnitKey unitKey = holder.getUnitKeys().stream()
                    .filter(player::unitBelongsToPlayer)
                    .filter(key -> unit.getAsyncId().equals(key.asyncID()))
                    .findFirst()
                    .orElse(null);
            if (unitKey == null) continue;

            int ungalvanizedCopies = holder.getUnitCount(unitKey) - holder.getGalvanizedUnitCount(unitKey);
            int toGalvanize = Math.min(remaining, Math.min(entry.getValue(), ungalvanizedCopies));
            if (toGalvanize <= 0) continue;

            int baseline = holder.getGalvanizedUnitCount(unitKey);
            int added = holder.addGalvanizedUnit(unitKey, toGalvanize);
            if (added <= 0) continue;

            state.add(String.join(
                    ";",
                    tile.getPosition(),
                    holder.getName(),
                    unitKey.asyncID(),
                    Integer.toString(baseline),
                    Integer.toString(added)));
            remaining -= added;
        }

        if (!state.isEmpty()) {
            game.setStoredValue(prototypeInnovatorsStateKey(player), String.join(",", state));
        }
    }

    public static void clearPrototypeInnovators(Game game) {
        if (game == null) return;
        for (Player player : game.getRealPlayers()) {
            clearPrototypeInnovators(game, player);
        }
    }

    private static void clearPrototypeInnovators(Game game, Player player) {
        String state = game.getStoredValue(prototypeInnovatorsStateKey(player));
        if (state.isBlank()) return;

        for (String entry : state.split(",")) {
            String[] values = entry.split(";", 5);
            if (values.length != 5) continue;

            Tile tile = game.getTileByPosition(values[0]);
            UnitHolder holder = tile == null ? null : tile.getUnitHolders().get(values[1]);
            if (holder == null) continue;

            UnitKey unitKey = holder.getUnitKeys().stream()
                    .filter(player::unitBelongsToPlayer)
                    .filter(key -> values[2].equals(key.asyncID()))
                    .findFirst()
                    .orElse(null);
            if (unitKey == null) continue;

            try {
                int baseline = Integer.parseInt(values[3]);
                int added = Integer.parseInt(values[4]);
                int remove = Math.min(added, Math.max(0, holder.getGalvanizedUnitCount(unitKey) - baseline));
                if (remove > 0) holder.removeGalvanizedUnit(unitKey, remove);
            } catch (NumberFormatException ignored) {
                // Malformed state cannot safely be reversed; still remove it below so it cannot leak further.
            }
        }
        game.removeStoredValue(prototypeInnovatorsStateKey(player));
    }

    private static boolean isPrototypeInnovatorsEligible(Player player, UnitModel unit, CombatRollType rollType) {
        return unit != null
                && player.getTechs().stream()
                        .map(Mapper::getUnitModelByTechUpgrade)
                        .filter(java.util.Objects::nonNull)
                        .anyMatch(upgrade -> upgrade.getAsyncId().equals(unit.getAsyncId()))
                && unit.getCombatDieCountForAbility(rollType, player) > 0;
    }

    private static int prototypeInnovatorsValue(UnitModel unit, CombatRollType rollType, Player player) {
        return unit.getCombatDieCountForAbility(rollType, player)
                * (11 - unit.getCombatDieHitsOnForAbility(rollType, player));
    }

    private static String prototypeInnovatorsStateKey(Player player) {
        return PROTOTYPE_INNOVATORS_STATE + player.getFaction();
    }
}
