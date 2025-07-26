package ti4.map.manager;

import java.util.ArrayList;
import java.util.List;

import ti4.model.BorderAnomalyHolder;
import ti4.model.BorderAnomalyModel;

/**
 * Responsible for tracking border anomalies present on a map.
 */
public class BorderAnomalyManager {
    private final List<BorderAnomalyHolder> anomalies = new ArrayList<>();

    public List<BorderAnomalyHolder> getAnomalies() {
        return anomalies;
    }

    public void setAnomalies(List<BorderAnomalyHolder> newAnomalies) {
        anomalies.clear();
        if (newAnomalies != null) {
            anomalies.addAll(newAnomalies);
        }
    }

    public boolean hasAnomaly(String tile, Integer direction) {
        return anomalies.stream()
            .filter(anomaly -> anomaly.getType() != BorderAnomalyModel.BorderAnomalyType.ARROW)
            .anyMatch(anomaly -> anomaly.getTile().equals(tile) && anomaly.getDirection() == direction);
    }

    public void addAnomaly(String tile, Integer direction, BorderAnomalyModel.BorderAnomalyType anomalyType) {
        anomalies.add(new BorderAnomalyHolder(tile, direction, anomalyType));
    }

    public void removeAnomaly(String tile, Integer direction) {
        anomalies.removeIf(anom -> anom.getTile().equals(tile) && anom.getDirection() == direction);
    }
}
