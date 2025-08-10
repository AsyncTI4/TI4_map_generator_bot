package ti4.map_ttpg;

import com.fasterxml.jackson.annotation.JsonAnyGetter;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.util.LinkedHashMap;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"tactics", "fleet", "strategy"})
public class TTPGCommandTokens {

    @JsonProperty("tactics")
    private Integer tactics;

    @JsonProperty("fleet")
    private Integer fleet;

    @JsonProperty("strategy")
    private Integer strategy;

    @JsonIgnore
    private final Map<String, Object> additionalProperties = new LinkedHashMap<>();

    @JsonProperty("tactics")
    public Integer getTactics() {
        return tactics;
    }

    @JsonProperty("tactics")
    public void setTactics(Integer tactics) {
        this.tactics = tactics;
    }

    @JsonProperty("fleet")
    public Integer getFleet() {
        return fleet;
    }

    @JsonProperty("fleet")
    public void setFleet(Integer fleet) {
        this.fleet = fleet;
    }

    @JsonProperty("strategy")
    public Integer getStrategy() {
        return strategy;
    }

    @JsonProperty("strategy")
    public void setStrategy(Integer strategy) {
        this.strategy = strategy;
    }

    @JsonAnyGetter
    public Map<String, Object> getAdditionalProperties() {
        return additionalProperties;
    }

    @JsonAnySetter
    public void setAdditionalProperty(String name, Object value) {
        additionalProperties.put(name, value);
    }
}
