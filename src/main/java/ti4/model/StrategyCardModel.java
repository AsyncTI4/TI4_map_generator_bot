package ti4.model;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import java.util.Map;

@Data
public class StrategyCardModel implements ModelInterface{
    private String name;
    private String alias;
    private Map<Integer, String> cardValues;
    private String description;

    @Override
    public boolean isValid() {
        return cardValues.size() > 0 && StringUtils.isNotBlank(this.name) && StringUtils.isNotBlank(this.alias);
    }

    @Override
    public String getAlias() {
        return this.alias;
    }

    public String getSCName(int scNumber) {
        return cardValues.get(scNumber);
    }
}
