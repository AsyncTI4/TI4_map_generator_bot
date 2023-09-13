package ti4.model;

import lombok.Data;

/// Internal model for combat modifier
@Data
public class TemporaryCombatModifierModel {

    private Integer useInTurn;
    private String relatedType;
    private String relatedID;
    private CombatModifierModel modifier;
    private String useInSystem;
    private String useInUnitHolder;

    public TemporaryCombatModifierModel(String relatedType, String relatedID, CombatModifierModel modifier) {
        this.relatedType = relatedType;
        this.relatedID = relatedID;
        this.modifier = modifier;
    }
}
//store in map against player, like <mod-id>,<mod-related-type>,<mod-related-id>,<useInTurn>,<useInSystem>,<useInHolder>
