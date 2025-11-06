package ti4.model;

import lombok.Data;

@Data
public class CombatModifierRelatedModel implements ModelInterface {
    private String type;
    private String alias;
    private String message;

    public boolean isValid() {
        return type != null && alias != null;
    }
}
