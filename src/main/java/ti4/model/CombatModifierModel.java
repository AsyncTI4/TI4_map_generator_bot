package ti4.model;

import java.util.List;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Data;

@Data
public class CombatModifierModel implements ModelInterface {
    private String type;
    private Integer value;
    private String valueScalingType;
    private Double valueScalingMultipler = 1.0;

    private String persistanceType;

    @JsonProperty("related")
    private List<CombatModifierRelatedModel> related;
    private String alias;
    private String scope;
    private String scopeExcept;
    private String condition;

    public boolean isValid() {
        return type != null
                && value != null
                && persistanceType != null
                && related != null;
    }

    public boolean isRelevantTo(String relatedType, String relatedAlias) {
        return related.stream().anyMatch(related -> related.getAlias().equals(relatedAlias)
            && related.getType().equals(relatedType));
    }

    public Boolean isInScopeForUnit(UnitModel unit) {
        boolean isInScope = false;
        if (getScopeExcept() != null) {
            if (!getScopeExcept().equals(unit.getAsyncId())) {
                isInScope = true;
            }
        } else {
            if (StringUtils.isBlank(getScope())
                    || "all".equals(getScope())
                    || getScope().equals(unit.getAsyncId())) {
                isInScope = true;
            }
        }
        return isInScope;
    }
}