package ti4.model;

import lombok.Data;

@Data
public class CombatModifierRelatedModel implements ModelInterface {
    private String type;
    private String alias;

  public boolean isValid() {
        return type != null
                && alias != null;
    }
}