package ti4.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import ti4.game.Game;
import ti4.game.Player;

@Data
@NoArgsConstructor
public class BorderAnomalyHolder {
    private String tile;
    private int direction;
    private String type;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<String> allowedColors = new ArrayList<>();

    public BorderAnomalyHolder(String tile, int direction, String type) {
        this.tile = tile;
        this.direction = direction;
        this.type = type;
    }

    @JsonIgnore
    public BorderAnomalyModel getModel() {
        return Mapper.getBorderAnomaly(type);
    }

    public boolean isType(String typeId) {
        return type != null && type.equals(typeId);
    }

    public boolean blocksAdjacencyIn() {
        BorderAnomalyModel model = getModel();
        return model != null && model.blocksAdjacencyIn();
    }

    public boolean blocksAdjacencyOut() {
        BorderAnomalyModel model = getModel();
        return model != null && model.blocksAdjacencyOut();
    }

    // TODO: suggestion: enforce the Void Tether in normal games too (drop oneWayAdjacencyOnly in
    // border_anomalies.json).
    public boolean isEnforced(Game game) {
        BorderAnomalyModel model = getModel();
        return model == null || !model.isOneWayAdjacencyOnly() || game.usesOneWayAdjacency();
    }

    public boolean exempts(Player player) {
        if (player == null) return false;
        BorderAnomalyModel model = getModel();
        String breakthrough = model == null ? null : model.getExemptHolderOf();
        if (breakthrough == null) return false;
        return player.hasBreakthrough(breakthrough) || isAllowed(player.getColor());
    }

    public boolean isAllowed(String color) {
        return color != null && allowedColors.contains(color);
    }

    public void allow(String color) {
        if (!isAllowed(color)) allowedColors.add(color);
    }

    public void revoke(String color) {
        allowedColors.remove(color);
    }
}
