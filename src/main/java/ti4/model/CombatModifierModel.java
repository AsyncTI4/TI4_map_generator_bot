package ti4.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import ti4.helpers.ButtonHelper;
import ti4.helpers.FoWHelper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
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
    private CombatRollType forCombatAbility;
    private Boolean singleUnitMod = false;
    private Boolean applyEachForQuantity = false;
    private Boolean applyToOpponent = false;

    public boolean isValid() {
        return type != null && value != null && persistenceType != null && related != null && forCombatAbility != null;
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
                sortedAllUnits.sort(Comparator.comparingInt(a -> a.getCombatDieHitsOnForAbility(rollType, player)));
                UnitModel best = sortedAllUnits.getFirst();
                if (sortedAllUnits.size() > 1) {
                    UnitModel secondBest = sortedAllUnits.get(1);
                    if (best.getCombatDieHitsOnForAbility(rollType, player)
                            == secondBest.getCombatDieHitsOnForAbility(rollType, player)) {
                        if (secondBest.getCombatDieCount() > best.getCombatDieCount()) {
                            best = secondBest;
                        }
                    }
                }
                isInScope = Objects.equals(best.getAsyncId(), unit.getAsyncId());
            }
            if ("_bestCap_".equals(scope)) {
                List<UnitModel> sortedAllUnits = new ArrayList<>(allUnits);
                sortedAllUnits = sortedAllUnits.stream()
                        .filter(u -> u.getCapacityValue() > 0)
                        .toList();
                List<UnitModel> sortedAllUnits2 = new ArrayList<>(sortedAllUnits);
                if (!sortedAllUnits2.isEmpty()) {
                    sortedAllUnits2.sort(
                            Comparator.comparingInt(a -> a.getCombatDieHitsOnForAbility(rollType, player)));
                    isInScope = Objects.equals(sortedAllUnits2.getFirst().getAsyncId(), unit.getAsyncId());
                } else {
                    isInScope = false;
                }
            }
            if (scope.contains("_mostdice_")) {
                List<UnitModel> sortedAllUnits = new ArrayList<>(allUnits);
                sortedAllUnits.sort(Comparator.comparingInt(a -> a.getCombatDieCountForAbility(rollType, player)));
                isInScope = Objects.equals(sortedAllUnits.getFirst().getAsyncId(), unit.getAsyncId());
            }
            if ("_ship_".equals(scope)) {
                isInScope = unit.getIsShip();
                if (game.getTileByPosition(game.getActiveSystem()) != null) {
                    Tile tile = game.getTileByPosition(game.getActiveSystem());
                    if ("purpletf_mech".equalsIgnoreCase(unit.getAlias())) {
                        if (FoWHelper.playerHasShipsInSystem(player, tile)
                                && FoWHelper.otherPlayersHaveShipsInSystem(player, tile, game)) {
                            isInScope = true;
                        }
                    }
                    if (ButtonHelper.doesPlayerHaveFSHere("nekro_flagship", player, tile)
                            && FoWHelper.otherPlayersHaveShipsInSystem(player, tile, game)) {
                        isInScope = true;
                    }
                }
            }
            if ("_ship_no_ff".equals(scope)) {
                isInScope = unit.getIsShip() && !"fighter".equalsIgnoreCase(unit.getBaseType());
            }
            if ("_groundforce_".equals(scope)) {
                isInScope = unit.getIsGroundForce();
            }
        }
        return isInScope;
    }
}
