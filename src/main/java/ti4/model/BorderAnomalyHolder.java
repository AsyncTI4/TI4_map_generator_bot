package ti4.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ti4.game.Player;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BorderAnomalyHolder {
    private String tile;
    private int direction;
    private String type;

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

    // TODO: let the exempt player grant access to other colours by command (Void Tether); check them here.
    public boolean exempts(Player player) {
        if (player == null) return false;
        BorderAnomalyModel model = getModel();
        String breakthrough = model == null ? null : model.getExemptHolderOf();
        return breakthrough != null && player.hasBreakthrough(breakthrough);
    }
}
