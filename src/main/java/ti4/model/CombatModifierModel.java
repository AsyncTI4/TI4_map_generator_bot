package ti4.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonFormat;

import lombok.Data;

@Data
public class CombatModifierModel implements ModelInterface {
    private String type;
    private String value;
    private String persistanceType;
    @JsonFormat(with = JsonFormat.Feature.)
    private List<CombatModifierRelatedModel> related;
    private String alias;
    private String scope;

    public CombatModifierModel() {
    }

    public boolean isValid() {
        return type != null
                && value != null
                && persistanceType != null
                && related != null;
    }

    public boolean isRelevantTo(String relatedType, String relatedAlias) {
        return related.stream()
                .filter(related -> related.getAlias().equals(relatedAlias)
                        && related.getType().equals(relatedType))
                .count() > 0;
    }
}