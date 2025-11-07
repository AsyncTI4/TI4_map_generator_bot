package ti4.website.model;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import ti4.map.Game;
import ti4.model.BorderAnomalyHolder;
import ti4.model.BorderAnomalyModel.BorderAnomalyType;

@Data
public class WebBorderAnomalies {

    @Data
    public static class BorderAnomalyInfo {
        private final String tile;
        private final int direction;
        private final String type; // e.g., "VOID_TETHER", "SPATIAL_TEAR", etc.
    }

    private List<BorderAnomalyInfo> borderAnomalies;

    public static WebBorderAnomalies fromGame(Game game) {
        WebBorderAnomalies web = new WebBorderAnomalies();
        List<BorderAnomalyHolder> anomalies = game.getBorderAnomalies();

        web.borderAnomalies = new ArrayList<>();
        for (BorderAnomalyHolder anomaly : anomalies) {
            if (anomaly == null) continue;
            // Only include non-ARROW anomalies (ARROW is used for custom adjacency and shouldn't be displayed)
            if (anomaly.getType() != BorderAnomalyType.ARROW) {
                web.borderAnomalies.add(new BorderAnomalyInfo(
                        anomaly.getTile(),
                        anomaly.getDirection(),
                        anomaly.getType().toString()));
            }
        }

        return web;
    }
}
