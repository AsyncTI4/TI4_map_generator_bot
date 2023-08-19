package ti4.model;

import lombok.Data;

/// Internal model for combat modifier
@Data
public class NamedCombatModifierModel {

    private CombatModifierModel modifier;
    private String name;

    public NamedCombatModifierModel(CombatModifierModel modifier, String name) {
        this.modifier = modifier;
        this.name = name;
    }
}
