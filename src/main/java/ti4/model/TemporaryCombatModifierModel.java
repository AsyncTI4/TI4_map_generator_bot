package ti4.model;

import lombok.Data;
import ti4.image.Mapper;

/// Internal model for combat modifier
@Data
public class TemporaryCombatModifierModel {

    private Integer useInTurn;
    private String relatedType;
    private String relatedID;
    private CombatModifierModel modifier;
    private String useInSystem;
    private String useInUnitHolder;

    public TemporaryCombatModifierModel(
            String relatedType, String relatedID, CombatModifierModel modifier, Integer useInTurn) {
        this.relatedType = relatedType;
        this.relatedID = relatedID;
        this.modifier = modifier;
        this.useInTurn = useInTurn;
    }

    public TemporaryCombatModifierModel(String loadString) {
        String[] items = loadString.split(",");
        relatedID = items[0];
        relatedType = items[1];
        useInTurn = Integer.parseInt(items[2]);
        useInSystem = items[3];
        useInUnitHolder = items[4];
        String modifierAlias = items[5];
        modifier = Mapper.getCombatModifiers().get(modifierAlias);
    }

    public String getSaveString() {
        if (getModifier() != null) {
            return relatedID + "," + relatedType + "," + useInTurn + "," + useInSystem + "," + useInUnitHolder + ","
                    + getModifier().getAlias();
        } else {
            return relatedID + "," + relatedType + "," + useInTurn + "," + useInSystem + "," + useInUnitHolder
                    + ",bleh";
        }
    }
}
