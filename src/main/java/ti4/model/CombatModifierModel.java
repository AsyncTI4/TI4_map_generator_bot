package ti4.model;

import java.util.List;

import org.apache.commons.lang3.StringUtils;

import lombok.Data;

@Data
public class CombatModifierModel implements ModelInterface {

    private String type;
    private Integer value;
    private String valueScalingType;
    private Double valueScalingMultiplier = 1.0;
    private String persistenceType;
    private List<CombatModifierRelatedModel> related;
    private String alias;
    private String scope;
    private String scopeExcept;
    private String condition;
    private String forCombatAbility;

    public boolean isValid() {
        return type != null
                && value != null
                && persistenceType != null
                && related != null;
    }

    public boolean isRelevantTo(String relatedType, String relatedAlias) {
        return related.stream().anyMatch(related -> related.getAlias().equals(relatedAlias)
            && related.getType().equals(relatedType));
    }

    public Boolean isInScopeForUnit(UnitModel unit) {
        boolean isInScope = false;
        if (scopeExcept != null) {
            if (!scopeExcept.equals(unit.getAsyncId())) {
                isInScope = true;
            }
        } else {
            if (StringUtils.isBlank(scope)
                    || "all".equals(scope)
                    || scope.equals(unit.getAsyncId())) {
                isInScope = true;
            }
        }
        return isInScope;
    }
}