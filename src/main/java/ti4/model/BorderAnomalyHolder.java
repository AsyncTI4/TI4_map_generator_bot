package ti4.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BorderAnomalyHolder {
    private String tile;
    private int direction;
    private BorderAnomalyModel.BorderAnomalyType type;

    public boolean blocksAdjacencyIn() {
        return switch (type) {
            case SPATIAL_TEAR -> true;
            case GRAVITY_WAVE -> true;
            case null, default -> false;
        };
    }

    public boolean blocksAdjacencyOut() {
        return switch (type) {
            case SPATIAL_TEAR -> true;
            case GRAVITY_WAVE -> false;
            case null, default -> false;
        };
    }
}
