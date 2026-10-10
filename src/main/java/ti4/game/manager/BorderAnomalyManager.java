package ti4.game.manager;

import java.util.ArrayList;
import java.util.List;
import ti4.image.Mapper;
import ti4.logging.BotLogger;
import ti4.model.BorderAnomalyHolder;
import ti4.model.BorderAnomalyModel;

public class BorderAnomalyManager {

    private static final String RETIRED_ARROW_TYPE = "arrow";

    private final List<BorderAnomalyHolder> anomalies = new ArrayList<>();

    public List<BorderAnomalyHolder> get() {
        return anomalies;
    }

    public void set(List<BorderAnomalyHolder> newAnomalies) {
        List<BorderAnomalyHolder> incoming = newAnomalies == null ? List.of() : new ArrayList<>(newAnomalies);
        anomalies.clear();
        for (BorderAnomalyHolder holder : incoming) {
            if (holder == null || isRetiredArrow(holder.getTile(), holder.getType())) continue;
            holder.setType(normalizeType(holder.getType()));
            anomalies.add(holder);
        }
    }

    public boolean has(String tile, Integer direction) {
        return anomalies.stream()
                .anyMatch(anomaly -> anomaly.getTile().equals(tile) && anomaly.getDirection() == direction);
    }

    public void add(String tile, Integer direction, String typeId) {
        if (isRetiredArrow(tile, typeId)) return;
        anomalies.add(new BorderAnomalyHolder(tile, direction, normalizeType(typeId)));
    }

    private static boolean isRetiredArrow(String tile, String typeId) {
        if (!RETIRED_ARROW_TYPE.equalsIgnoreCase(typeId)) return false;
        BotLogger.warning("Dropped retired border anomaly type `arrow` on tile " + tile);
        return true;
    }

    public void remove(String tile, Integer direction) {
        anomalies.removeIf(anom -> anom.getTile().equals(tile) && anom.getDirection() == direction);
    }

    private static String normalizeType(String typeId) {
        if (typeId == null) return null;
        BorderAnomalyModel model = Mapper.resolveBorderAnomaly(typeId);
        if (model == null) {
            BotLogger.warning("Unknown border anomaly type `" + typeId + "`; kept as is but not rendered or applied.");
            return typeId;
        }
        return model.getId();
    }
}
