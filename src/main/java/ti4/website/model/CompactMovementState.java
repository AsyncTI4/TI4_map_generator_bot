package ti4.website.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitState;
import ti4.json.JsonMapperManager;

/** Deterministic positional JSON describing the units committed by a tactical-action displacement. */
@UtilityClass
public class CompactMovementState {

    private static final int FORMAT_VERSION = 2;

    /**
     * Format: {@code [version,targetPosition,targetHolder,[[sourcePosition,sourceHolder,units],...]]}, where each unit
     * is {@code [colorId,asyncUnitId,healthy,damaged,galvanized,damagedGalvanized,neutral?]}. The final {@code 1} is
     * emitted only for neutral units because their dynamically assigned plastic color is intentionally absent from the
     * website's normal player-color map.
     */
    public static String serialize(
            Game game, String targetPosition, Map<String, Map<UnitKey, List<Integer>>> displacement) {
        List<Object> state = new ArrayList<>(4);
        state.add(FORMAT_VERSION);
        state.add(targetPosition);
        state.add("space");
        state.add(displacement.entrySet().stream()
                .map(entry -> serializeSource(game, entry))
                .filter(source -> !((List<?>) source.get(2)).isEmpty())
                .sorted(Comparator.comparing(source -> JsonMapperManager.basic().writeValueAsString(source)))
                .map(source -> (Object) source)
                .toList());
        return JsonMapperManager.basic().writeValueAsString(state);
    }

    private static List<Object> serializeSource(Game game, Map.Entry<String, Map<UnitKey, List<Integer>>> entry) {
        String key = entry.getKey();
        int separator = key == null ? -1 : key.indexOf('-');
        String position = separator < 0 ? key : key.substring(0, separator);
        String holder = separator < 0 ? "" : key.substring(separator + 1);

        List<Object> units = entry.getValue().entrySet().stream()
                .map(unit -> serializeUnit(game, unit))
                .filter(unit -> unit.subList(2, 6).stream()
                                .mapToInt(value -> (Integer) value)
                                .sum()
                        > 0)
                .sorted(Comparator.<List<Object>, String>comparing(unit -> (String) unit.get(0))
                        .thenComparing(unit -> (String) unit.get(1)))
                .map(unit -> (Object) unit)
                .toList();
        return List.of(position, holder, units);
    }

    private static List<Object> serializeUnit(Game game, Map.Entry<UnitKey, List<Integer>> entry) {
        List<Object> unit = new ArrayList<>(7);
        unit.add(entry.getKey().colorID());
        unit.add(entry.getKey().asyncID());
        List<Integer> states = entry.getValue();
        for (int i = 0; i < UnitState.values().length; i++) {
            Integer count = states != null && i < states.size() ? states.get(i) : null;
            unit.add(count == null ? 0 : count);
        }
        Player owner = game.getPlayerFromColorOrFaction(entry.getKey().colorID());
        if (owner != null && "neutral".equals(owner.getFaction())) unit.add(1);
        return unit;
    }
}
