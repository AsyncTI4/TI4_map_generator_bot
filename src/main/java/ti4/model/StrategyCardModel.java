package ti4.model;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;

@Data
public class StrategyCardModel implements ModelInterface{
    private String name;
    private String alias;
    private int numberOfCards;
    private String description;

    @Override
    public boolean isValid() {
        return numberOfCards > 0 && StringUtils.isNotBlank(this.name) && StringUtils.isNotBlank(this.alias);
    }

    @Override
    public String getAlias() {
        return this.alias;
    }
}
