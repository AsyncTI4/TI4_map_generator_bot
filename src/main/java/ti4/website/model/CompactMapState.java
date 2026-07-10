package ti4.website.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.json.JsonMapperManager;

/**
 * Deterministic, compact serialization of the map data sent to the website.
 *
 * <p>The format is versioned positional JSON rather than a JSON object: field names make up a surprisingly large
 * fraction of a normal {@link WebTileUnitData} payload. String values are still JSON encoded, so homebrew identifiers
 * cannot collide with a hand-picked delimiter. Every otherwise unordered collection is sorted here so the serialized
 * value can be compared directly when deciding whether a new event needs a snapshot.</p>
 */
@UtilityClass
public class CompactMapState {

    private static final int FORMAT_VERSION = 1;

    public static String serialize(Game game) {
        List<Object> state = new ArrayList<>();
        state.add(FORMAT_VERSION);
        WebTileUnitData.fromGame(game).entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(CompactMapState::serializeTile)
                .forEach(state::add);
        return JsonMapperManager.basic().writeValueAsString(state);
    }

    private static List<Object> serializeTile(Map.Entry<String, WebTileUnitData> entry) {
        WebTileUnitData tile = entry.getValue();
        return List.of(
                entry.getKey(),
                tile.isAnomaly() ? 1 : 0,
                serializeEntityGroups(tile.getSpace()),
                serializePlanets(tile.getPlanets()),
                sortedStrings(tile.getCcs()),
                serializeIntegerMap(tile.getProduction()),
                serializePds(tile.getPds()));
    }

    private static List<Object> serializePlanets(Map<String, WebTilePlanet> planets) {
        return planets.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    WebTilePlanet planet = entry.getValue();
                    List<Object> data = new ArrayList<>(8);
                    data.add(entry.getKey());
                    data.add(planet.getControlledBy());
                    data.add(planet.getCommodities());
                    data.add(planet.isPlanetaryShield() ? 1 : 0);
                    data.add(planet.isExhausted() ? 1 : 0);
                    data.add(planet.getResources());
                    data.add(planet.getInfluence());
                    data.add(serializeEntityGroups(planet.getEntities()));
                    return (Object) data;
                })
                .toList();
    }

    private static List<Object> serializeEntityGroups(Map<String, List<WebEntityData>> groups) {
        return groups.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> List.of(entry.getKey(), serializeEntities(entry.getValue())))
                .map(value -> (Object) value)
                .toList();
    }

    private static List<Object> serializeEntities(List<WebEntityData> entities) {
        return entities.stream()
                .filter(Objects::nonNull)
                .map(CompactMapState::serializeEntity)
                .sorted(Comparator.comparing(JsonMapperManager.basic()::writeValueAsString))
                .map(value -> (Object) value)
                .toList();
    }

    private static List<Object> serializeEntity(WebEntityData entity) {
        String type =
                switch (entity.getEntityType()) {
                    case "unit" -> "u";
                    case "token" -> "t";
                    case "attachment" -> "a";
                    case "actioncard" -> "c";
                    default ->
                        throw new IllegalArgumentException(
                                "Unsupported web map entity type: " + entity.getEntityType());
                };

        if (!"u".equals(type)) {
            return List.of(type, entity.getEntityId(), entity.getCount());
        }

        List<Integer> states = entity.getUnitStates();
        if (states == null || states.size() != 4) {
            throw new IllegalArgumentException("A web map unit must have exactly four unit-state counts");
        }
        List<Object> data = new ArrayList<>(6);
        data.add(type);
        data.add(entity.getEntityId());
        data.addAll(states);
        return data;
    }

    private static List<Object> serializeIntegerMap(Map<String, Integer> values) {
        return values.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> (Object) List.of(entry.getKey(), entry.getValue()))
                .toList();
    }

    private static List<Object> serializePds(Map<String, ti4.website.WebPdsCoverage> pds) {
        if (pds == null) return List.of();
        return pds.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> (Object) List.of(
                        entry.getKey(),
                        entry.getValue().getCount(),
                        entry.getValue().getExpected()))
                .toList();
    }

    private static List<String> sortedStrings(List<String> values) {
        return values.stream().filter(Objects::nonNull).sorted().toList();
    }
}
