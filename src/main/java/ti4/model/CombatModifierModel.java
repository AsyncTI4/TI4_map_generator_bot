package ti4.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import ti4.map.Game;
import ti4.map.Player;
import ti4.service.combat.CombatRollType;

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
    private Boolean applyToOpponent = false;

    public boolean isValid() {
        return type != null && value != null && persistenceType != null && related != null;
    }

    public boolean isRelevantTo(String relatedType, String relatedAlias) {
        return related.stream()
                .anyMatch(related -> related.getAlias().equals(relatedAlias)
                        && related.getType().equals(relatedType));
    }

    public Boolean isInScopeForUnit(
            UnitModel unit, List<UnitModel> allUnits, CombatRollType rollType, Game game, Player player) {
        boolean isInScope = false;
        if (scopeExcept != null) {
            if (!scopeExcept.equals(unit.getAsyncId())) {
                isInScope = true;
            }
        } else {
            if (StringUtils.isBlank(scope) || "all".equals(scope) || scope.equals(unit.getAsyncId())) {
                isInScope = true;
            }
            if ("_best_".equals(scope)) {
                List<UnitModel> sortedAllUnits = new ArrayList<>(allUnits);
                sortedAllUnits.sort(
                        Comparator.comparingInt(a -> a.getCombatDieHitsOnForAbility(rollType, player, game)));
                isInScope = Objects.equals(sortedAllUnits.getFirst().getAsyncId(), unit.getAsyncId());
            }
            if ("_ship_".equals(scope)) {
                isInScope = unit.getIsShip();
            }
            if ("_ship_no_ff".equals(scope)) {
                isInScope = unit.getIsShip() && !unit.getBaseType().equalsIgnoreCase("fighter");
            }
            if ("_groundforce_".equals(scope)) {
                isInScope = unit.getIsGroundForce();
            }
        }
        return isInScope;
    }
}
