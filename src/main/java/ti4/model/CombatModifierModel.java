package ti4.model;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import lombok.Data;
import ti4.helpers.CombatRollType;

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
    private Boolean applyEachForQuantity = false;

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

    public Boolean isInScopeForUnit(UnitModel unit, List<UnitModel> allUnits, CombatRollType rollType) {
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
            if ("_best_".equals(scope)) {
                List<UnitModel> sortedAllUnits = new ArrayList<>(allUnits);
                sortedAllUnits.sort(
                        (a, b) -> a.getCombatDieHitsOnForAbility(rollType) - b.getCombatDieHitsOnForAbility(rollType));
                isInScope = sortedAllUnits.get(0).getAsyncId() == unit.getAsyncId();
            }
            if ("_ship_".equals(scope)) {
                isInScope = unit.getIsShip() != null && unit.getIsShip();
            }
        }
        return isInScope;
    }
}