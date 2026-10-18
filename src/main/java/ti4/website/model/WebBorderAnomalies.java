package ti4.website.model;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import ti4.game.Game;
import ti4.model.BorderAnomalyHolder;

@Data
public class WebBorderAnomalies {

    /**
     * @param type e.g., "void_tether", "spatial_tear", etc.
     */
    public record BorderAnomalyInfo(String tile, int direction, String type) {}

    private List<BorderAnomalyInfo> borderAnomalies;

    public static WebBorderAnomalies fromGame(Game game) {
        WebBorderAnomalies web = new WebBorderAnomalies();
        List<BorderAnomalyHolder> anomalies = game.getBorderAnomalies();

        web.borderAnomalies = new ArrayList<>();
        for (BorderAnomalyHolder anomaly : anomalies) {
            if (anomaly == null || anomaly.getModel() == null) continue;
            web.borderAnomalies.add(
                    new BorderAnomalyInfo(anomaly.getTile(), anomaly.getDirection(), anomaly.getType()));
        }

        return web;
    }
}
