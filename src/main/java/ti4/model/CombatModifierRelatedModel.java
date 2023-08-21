package ti4.model;

import java.util.List;

import lombok.Data;
import ti4.map.Player;

@Data
public class CombatModifierRelatedModel implements ModelInterface {
    private String type;
    private String alias;

    public CombatModifierRelatedModel() {
    }

    public boolean isValid() {
        return type != null
                && alias != null;
    }
}