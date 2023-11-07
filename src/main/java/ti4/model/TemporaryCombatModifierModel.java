package ti4.model;

import lombok.Data;
import ti4.generator.Mapper;

/// Internal model for combat modifier
@Data
public class TemporaryCombatModifierModel {

    private Integer useInTurn;
    private String relatedType;
    private String relatedID;
    private CombatModifierModel modifier;
    private String useInSystem;
    private String useInUnitHolder;

    public TemporaryCombatModifierModel(String relatedType, String relatedID, CombatModifierModel modifier,
            Integer useInTurn) {
        this.relatedType = relatedType;
        this.relatedID = relatedID;
        this.modifier = modifier;
        this.useInTurn = useInTurn;
    }

    public TemporaryCombatModifierModel(String loadString) {
        String[] items = loadString.split(",");
        this.relatedID = items[0];
        this.relatedType = items[1];
        this.useInTurn = Integer.parseInt(items[2]);
        this.useInSystem = items[3];
        this.useInUnitHolder = items[4];
        String modifierAlias = items[5];
        this.modifier = Mapper.getCombatModifiers().get(modifierAlias);
    }

    public String getSaveString() {
        return this.relatedID + "," + this.relatedType + "," + this.useInTurn + "," + this.useInSystem + ","
                + this.useInUnitHolder + "," + this.getModifier().getAlias();
    }
}
