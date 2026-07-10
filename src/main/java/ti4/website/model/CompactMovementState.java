package ti4.website.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.experimental.UtilityClass;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitState;
import ti4.json.JsonMapperManager;

/** Deterministic positional JSON describing the units committed by a tactical-action displacement. */
@UtilityClass
public class CompactMovementState {

    private static final int FORMAT_VERSION = 1;

    /**
     * Format: {@code [version,targetPosition,targetHolder,[[sourcePosition,sourceHolder,units],...]]}, where each unit
     * is {@code [colorId,asyncUnitId,healthy,damaged,galvanized,damagedGalvanized]}.
     */
    public static String serialize(String targetPosition, Map<String, Map<UnitKey, List<Integer>>> displacement) {
        List<Object> state = new ArrayList<>(4);
        state.add(FORMAT_VERSION);
        state.add(targetPosition);
        state.add("space");
        state.add(displacement.entrySet().stream()
                .map(CompactMovementState::serializeSource)
                .filter(source -> !((List<?>) source.get(2)).isEmpty())
                .sorted(Comparator.comparing(source -> JsonMapperManager.basic().writeValueAsString(source)))
                .map(source -> (Object) source)
                .toList());
        return JsonMapperManager.basic().writeValueAsString(state);
    }

    private static List<Object> serializeSource(Map.Entry<String, Map<UnitKey, List<Integer>>> entry) {
        String key = entry.getKey();
        int separator = key == null ? -1 : key.indexOf('-');
        String position = separator < 0 ? key : key.substring(0, separator);
        String holder = separator < 0 ? "" : key.substring(separator + 1);

        List<Object> units = entry.getValue().entrySet().stream()
                .map(CompactMovementState::serializeUnit)
                .filter(unit -> unit.subList(2, unit.size()).stream()
                                .mapToInt(value -> (Integer) value)
                                .sum()
                        > 0)
                .sorted(Comparator.<List<Object>, String>comparing(unit -> (String) unit.get(0))
                        .thenComparing(unit -> (String) unit.get(1)))
                .map(unit -> (Object) unit)
                .toList();
        return List.of(position, holder, units);
    }

    private static List<Object> serializeUnit(Map.Entry<UnitKey, List<Integer>> entry) {
        List<Object> unit = new ArrayList<>(6);
        unit.add(entry.getKey().colorID());
        unit.add(entry.getKey().asyncID());
        List<Integer> states = entry.getValue();
        for (int i = 0; i < UnitState.values().length; i++) {
            Integer count = states != null && i < states.size() ? states.get(i) : null;
            unit.add(count == null ? 0 : count);
        }
        return unit;
    }
}
