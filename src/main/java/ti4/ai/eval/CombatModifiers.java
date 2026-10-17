package ti4.ai.eval;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.game.UnitHolder;
import ti4.helpers.CombatModHelper;
import ti4.helpers.Constants;
import ti4.helpers.Units.UnitType;
import ti4.image.TileHelper;
import ti4.model.NamedCombatModifierModel;
import ti4.model.UnitModel;
import ti4.service.combat.CombatRollType;

@UtilityClass
public class CombatModifiers {

    private static final Set<String> STANDING_PERSISTENCE = Set.of("ALWAYS", "CONDITIONAL");

    public record Side(Player player, Map<UnitType, Integer> units) {}

    public static Map<UnitType, Integer> hitModifiers(
            Game game, Tile tile, UnitHolder holder, Side side, Side opponent) {
        Map<UnitType, Integer> modifiers = new HashMap<>();
        Map<UnitModel, Integer> mine = models(side);
        Map<UnitModel, Integer> theirs = models(opponent);
        if (mine.isEmpty()) return modifiers;
        try {
            List<NamedCombatModifierModel> standing = CombatModHelper.getModifiers(
                            side.player(),
                            opponent.player(),
                            mine,
                            theirs,
                            TileHelper.getTileById(tile.getTileID()),
                            game,
                            CombatRollType.combatround,
                            holder,
                            Constants.COMBAT_MODIFIERS)
                    .stream()
                    .filter(modifier ->
                            STANDING_PERSISTENCE.contains(modifier.getModifier().getPersistenceType()))
                    .toList();
            List<UnitModel> units = new ArrayList<>(mine.keySet());
            mine.forEach((model, count) -> modifiers.put(
                    model.getUnitType(),
                    CombatModHelper.getCombinedModifierForUnit(
                            model,
                            count,
                            standing,
                            side.player(),
                            opponent.player(),
                            game,
                            units,
                            CombatRollType.combatround,
                            tile,
                            holder)));
        } catch (RuntimeException e) {
            modifiers.clear();
        }
        return modifiers;
    }

    private static Map<UnitModel, Integer> models(Side side) {
        Map<UnitModel, Integer> models = new HashMap<>();
        side.units().forEach((type, count) -> {
            UnitModel model = side.player().getUnitByType(type);
            if (model != null && count > 0) models.merge(model, count, Integer::sum);
        });
        return models;
    }
}
